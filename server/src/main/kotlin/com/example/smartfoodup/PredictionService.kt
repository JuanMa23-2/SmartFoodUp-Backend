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
    val alertaRiesgo: String? = null,
    val infoHardware: String? = null
)

// Motor de diagnostico integral mediante hardware y OpenAI Vision.
object PredictionService {
    private val iaClient = HttpClient(CIO)

    fun findApiKey(): String {
        val envKey = System.getenv("OPENAI_API_KEY")
        return envKey?.trim() ?: "FALTA_KEY"
    }

    private var modelBundle: SavedModelBundle? = null
    
    init {
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
            println("Error motor local: \${e.message}")
        }
    }

    private suspend fun obtenerContextoFisico(id: Int?): String {
        if (id == null) return "Sin datos de sensores."
        return try {
            newSuspendedTransaction {
                MedicionesSensores.select { MedicionesSensores.dispositivoId eq id }
                    .orderBy(MedicionesSensores.id to SortOrder.DESC)
                    .limit(1)
                    .map { 
                        "Hardware: Peso \${it[MedicionesSensores.pesoGramos]}g, Gas \${it[MedicionesSensores.gasPorcentaje]}%, Temp \${it[MedicionesSensores.temperatura]}C, Hum \${it[MedicionesSensores.humedad]}%."
                    }.singleOrNull() ?: "Sin telemetria reciente."
            }
        } catch (e: Exception) { "Error hardware." }
    }

    suspend fun predecirImagen(base64: String, idDispositivo: Int? = null): PredictionResult {
        val apiKey = findApiKey()
        if (apiKey == "FALTA_KEY") return PredictionResult("Falta Clave", null, 0.0, "N/A", null, false, errorOcurrido = true)
        
        val contextoHardware = obtenerContextoFisico(idDispositivo ?: 1)
        val cleanB64 = base64.substringAfter(",").replace("\n", "").replace("\r", "").replace(" ", "")
        
        return predecirConOpenAITotal(cleanB64, apiKey, contextoHardware)
    }

    private suspend fun predecirConOpenAITotal(cleanB64: String, key: String, hardware: String): PredictionResult {
        val prompt = "Analiza el alimento. Sensores: \$hardware. Responde JSON plano: 'fruta', 'estado' (Fresco/Maduro/Deteriorado), 'porcentaje' (0-100), 'dias', 'comer' (3 sugerencias numeradas con \\n), 'riesgo', 'inventario'."
        
        return try {
            val response: HttpResponse = iaClient.post("https://api.openai.com/v1/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer \$key")
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("model", "gpt-4o-mini")
                    put("messages", buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", buildJsonArray { add(buildJsonObject { put("type", "text"); put("text", prompt) }); add(buildJsonObject { put("type", "image_url"); put("image_url", buildJsonObject { put("url", "data:image/jpeg;base64,\$cleanB64") }) }) }) }) })
                    put("response_format", buildJsonObject { put("type", "json_object") })
                }.toString())
            }
            val content = Json.parseToJsonElement(response.bodyAsText()).jsonObject["choices"]?.jsonArray?.get(0)?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content ?: ""
            val res = Json.parseToJsonElement(content).jsonObject
            
            PredictionResult(
                fruta = res["fruta"]?.jsonPrimitive?.content,
                estado = res["estado"]?.jsonPrimitive?.content,
                porcentajeFrescura = res["porcentaje"]?.jsonPrimitive?.doubleOrNull ?: 85.0,
                sugerencias = res["dias"]?.jsonPrimitive?.content,
                recetas = res["comer"]?.jsonPrimitive?.content,
                esSaludable = true,
                alertaRiesgo = res["riesgo"]?.jsonPrimitive?.content,
                infoHardware = hardware
            )
        } catch (e: Exception) { PredictionResult("Error Vision", "N/A", 0.0, "N/A", "N/A", false) }
    }

    suspend fun mapearPrediccion(idx: Int): PredictionResult = predecirImagen("") // Redireccion para evitar errores de firma
}
