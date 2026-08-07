package com.example.smartfoodup

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.tensorflow.SavedModelBundle
import org.tensorflow.ndarray.NdArrays
import org.tensorflow.ndarray.Shape
import org.tensorflow.types.TFloat32
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.util.*
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.SortOrder
import javax.imageio.ImageIO

@Serializable
data class PredictionResult(
    val fruta: String?,
    val estado: String?,
    val porcentajeFrescura: Double,
    val sugerencias: String?,
    val recetas: String?,
    val esSaludable: Boolean,
    val claseDetectada: String = "",
    val errorOcurrido: Boolean = false,
    val mensajeError: String? = null,
    // Datos de la estacion inteligente
    val alertaRiesgo: String? = null,
    val infoHardware: String? = null
)

// Gestión de diagnóstico mediante IA híbrida enriquecida con datos de sensores.
object PredictionService {
    private val iaClient = HttpClient(CIO)

    fun findApiKey(): String {
        val envKey = System.getenv("OPENAI_API_KEY")
        return envKey?.trim() ?: "FALTA_KEY"
    }

    private var modelBundle: SavedModelBundle? = null
    
    init {
        ImageIO.scanForPlugins()
        cargarModeloLocal()
    }

    private fun cargarModeloLocal() {
        try {
            val paths = listOf("server/smartfoodup_model", "smartfoodup_model", "/app/server/smartfoodup_model")
            for (path in paths) {
                val modelDir = File(path)
                if (modelDir.exists() && modelDir.isDirectory && File(modelDir, "saved_model.pb").exists()) {
                    modelBundle = SavedModelBundle.load(path, "serve")
                    println("Modelo TensorFlow cargado correctamente.")
                    break
                }
            }
        } catch (e: Exception) {
            println("Aviso: Motor local inactivo.")
        }
    }

    // Consulta la telemetría más reciente del dispositivo vinculado.
    private suspend fun obtenerContextoFisico(id: Int?): String {
        if (id == null) return "Sin conexion a sensores."
        return try {
            newSuspendedTransaction {
                MedicionesSensores.select { MedicionesSensores.dispositivoId eq id }
                    .orderBy(MedicionesSensores.id to SortOrder.DESC)
                    .limit(1)
                    .map { 
                        "Sensores: Peso ${it[MedicionesSensores.pesoGramos]}g, Gas ${it[MedicionesSensores.gasPorcentaje]}%, Temp ${it[MedicionesSensores.temperatura]}C, Hum ${it[MedicionesSensores.humedad]}%."
                    }.singleOrNull() ?: "Sin mediciones recientes."
            }
        } catch (e: Exception) { "Error al leer hardware." }
    }

    suspend fun predecirImagen(base64: String, idDispositivo: Int? = null): PredictionResult {
        val apiKey = findApiKey()
        if (apiKey == "FALTA_KEY") {
            return PredictionResult("Falta Clave", null, 0.0, "N/A", null, false, errorOcurrido = true)
        }

        // Si hay un ID de dispositivo, traemos los datos de la Raspberry para la IA.
        val contextoHardware = obtenerContextoFisico(idDispositivo)
        val cleanB64 = base64.substringAfter(",").replace("\n", "").replace("\r", "").replace(" ", "")
        
        return predecirConOpenAITotal(cleanB64, apiKey, contextoHardware)
    }

    private fun realizarInferenciaReal(cleanB64: String): Int {
        val bundle = modelBundle ?: throw Exception("Sin modelo")
        val imageBytes = Base64.getDecoder().decode(cleanB64)
        val image = ImageIO.read(ByteArrayInputStream(imageBytes)) ?: throw Exception("Imagen invalida")
        val resized = BufferedImage(224, 224, BufferedImage.TYPE_INT_RGB)
        resized.createGraphics().drawImage(image, 0, 0, 224, 224, null)

        val inputData = NdArrays.ofFloats(Shape.of(1, 224, 224, 3))
        for (y in 0 until 224) {
            for (x in 0 until 224) {
                val p = resized.getRGB(x, y)
                inputData.setFloat(((p shr 16) and 0xFF) / 255.0f, 0, y.toLong(), x.toLong(), 0)
                inputData.setFloat(((p shr 8) and 0xFF) / 255.0f, 0, y.toLong(), x.toLong(), 1)
                inputData.setFloat((p and 0xFF) / 255.0f, 0, y.toLong(), x.toLong(), 2)
            }
        }

        val signature = bundle.metaGraphDef().getSignatureDefOrThrow("serving_default")
        val inputName = signature.getInputsMap().values.first().getName().substringBefore(":")
        val outputName = signature.getOutputsMap().values.first().getName().substringBefore(":")

        TFloat32.tensorOf(inputData).use { t ->
            val result = bundle.session().runner().feed(inputName, t).fetch(outputName).run()
            result.use { res ->
                val probs = res[0] as TFloat32
                var max = 0; var maxP = -1.0f
                for (i in 0 until 36) {
                    val p = probs.getFloat(0, i.toLong())
                    if (p > maxP) { maxP = p; max = i }
                }
                return max
            }
        }
    }

    private suspend fun predecirConOpenAITotal(cleanB64: String, key: String, hardware: String): PredictionResult {
        val prompt = """
            Analiza la imagen y los datos de sensores: $hardware.
            Responde JSON plano: 'fruta', 'estado', 'porcentaje', 'dias', 'comer' (3 sugerencias con \n), 'riesgo' (Alerta segun clima/gas), 'inventario' (Sugerencia segun el peso).
            Si no hay fruta responde 'NULO' en fruta.
        """.trimIndent()

        return try {
            val response: HttpResponse = iaClient.post("https://api.openai.com/v1/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $key")
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("model", "gpt-4o-mini")
                    put("messages", buildJsonArray {
                        add(buildJsonObject {
                            put("role", "user")
                            put("content", buildJsonArray {
                                add(buildJsonObject { put("type", "text"); put("text", prompt) })
                                add(buildJsonObject { 
                                    put("type", "image_url")
                                    put("image_url", buildJsonObject { put("url", "data:image/jpeg;base64,$cleanB64") })
                                })
                            })
                        })
                    })
                    put("response_format", buildJsonObject { put("type", "json_object") })
                }.toString())
            }
            
            val content = Json.parseToJsonElement(response.bodyAsText()).jsonObject["choices"]?.jsonArray?.get(0)?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content ?: ""
            val res = Json.parseToJsonElement(content).jsonObject
            
            val nombre = res["fruta"]?.jsonPrimitive?.content ?: "NULO"
            if (nombre.contains("NULO", true)) {
                return PredictionResult("No se detecto alimento", "N/A", 0.0, "N/A", "N/A", false)
            }

            PredictionResult(
                fruta = nombre,
                estado = res["estado"]?.jsonPrimitive?.content,
                porcentajeFrescura = res["porcentaje"]?.jsonPrimitive?.doubleOrNull ?: 85.0,
                sugerencias = res["dias"]?.jsonPrimitive?.content,
                recetas = res["comer"]?.jsonPrimitive?.content,
                esSaludable = true,
                alertaRiesgo = res["riesgo"]?.jsonPrimitive?.content,
                infoHardware = hardware
            )
        } catch (e: Exception) {
            PredictionResult("Error Smart Station", "N/A", 0.0, "N/A", "N/A", false, "ERROR")
        }
    }
}
