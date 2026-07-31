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
import java.util.concurrent.ConcurrentHashMap
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
    val mensajeError: String? = null
)

// Motor de inteligencia hibrida: TensorFlow Local + OpenAI GPT-4o-mini.
object PredictionService {
    private val iaClient = HttpClient(CIO)
    
    // Cache de seguridad para optimizar el saldo de la cuenta.
    private var lastResult: PredictionResult? = null
    private var lastRequestTime: Long = 0
    private val sugerenciasCache = ConcurrentHashMap<String, String>()

    // Obtencion de la clave de OpenAI desde el entorno de Railway.
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
            println("Error inicializando motor local.")
        }
    }

    private val classNames = listOf(
        "Apple__Healthy", "Apple__Rotten", "Banana__Healthy", "Banana__Rotten",
        "Bellpepper__Healthy", "Bellpepper__Rotten", "Carrot__Healthy", "Carrot__Rotten",
        "Cucumber__Healthy", "Cucumber__Rotten", "Grape__Healthy", "Grape__Rotten",
        "Guava__Healthy", "Guava__Rotten", "Jujube__Healthy", "Jujube__Rotten",
        "Lemon__Healthy", "Lemon__Rotten", "Lulo__Healthy", "Lulo__Rotten",
        "Mango__Healthy", "Mango__Rotten", "Okra__Healty", "Okra__Rotten",
        "Orange__Healthy", "Orange__Rotten", "Pomegranate__Healthy", "Pomegranate__Rotten",
        "Potato__Healthy", "Potato__Rotten", "Strawberry__Healthy", "Strawberry__Rotten",
        "Tamarillo__Healthy", "Tamarillo__Rotten", "Tomato__Healthy", "Tomato__Rotten"
    )

    private val traducciones = mapOf(
        "Apple" to "Manzana", "Banana" to "Platano", "Bellpepper" to "Pimiento",
        "Carrot" to "Zanahoria", "Cucumber" to "Pepino", "Grape" to "Uva",
        "Guava" to "Guayaba", "Jujube" to "Azufaifa", "Lemon" to "Limon",
        "Lulo" to "Lulo", "Mango" to "Mango", "Okra" to "Okra",
        "Orange" to "Naranja", "Pomegranate" to "Granada", "Potato" to "Papa",
        "Strawberry" to "Fresa", "Tamarillo" to "Tomate de arbol", "Tomato" to "Tomate"
    )

    // Logica principal de prediccion con control de cooldown para Realidad Aumentada.
    suspend fun predecirImagen(base64: String): PredictionResult {
        val currentTime = System.currentTimeMillis()
        
        if (lastResult != null && (currentTime - lastRequestTime) < 5000) {
            return lastResult!!
        }

        val apiKey = findApiKey()
        if (apiKey == "FALTA_KEY") {
            return PredictionResult(null, null, 0.0, "Configuracion OpenAI pendiente.", null, false, errorOcurrido = true)
        }

        val cleanB64 = base64.substringAfter(",").replace("\n", "").replace("\r", "").replace(" ", "")
        
        val result = if (modelBundle != null) {
            try {
                val classIndex = realizarInferenciaReal(cleanB64)
                mapearPrediccion(classIndex, apiKey)
            } catch (e: Exception) {
                predecirConOpenAITotal(cleanB64, apiKey)
            }
        } else {
            predecirConOpenAITotal(cleanB64, apiKey)
        }

        lastRequestTime = currentTime
        lastResult = result
        return result
    }

    private fun realizarInferenciaReal(cleanB64: String): Int {
        val bundle = modelBundle ?: throw Exception("No Model")
        val imageBytes = Base64.getDecoder().decode(cleanB64)
        val image = ImageIO.read(ByteArrayInputStream(imageBytes)) ?: throw Exception("Img Invalida")
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

    suspend fun mapearPrediccion(idx: Int, key: String = findApiKey()): PredictionResult {
        val raw = classNames.getOrElse(idx) { "Apple__Healthy" }
        val partes = raw.split("__")
        val nombre = traducciones[partes[0]] ?: partes[0]
        val esSaludable = !raw.contains("Rotten", true)
        return obtenerSugerenciasOpenAI(nombre, if(esSaludable) "Fresco" else "Deteriorado", esSaludable, raw, key)
    }

    // Consulta a GPT-4o-mini para obtener informacion detallada y formateada.
    private suspend fun obtenerSugerenciasOpenAI(fruta: String, estado: String, saludable: Boolean, raw: String, key: String): PredictionResult {
        val cacheKey = "${fruta}_$estado"
        val cached = sugerenciasCache[cacheKey]
        if (cached != null) {
            return PredictionResult(fruta, estado, 90.0, "Consultar visualmente.", cached, saludable, raw)
        }

        val prompt = "Alimento: $fruta ($estado). Responde SOLO en JSON plano: {\"porcentaje\": 90, \"dias\": \"X dias aprox\", \"comer\": \"Dar 3 sugerencias detalladas separadas por saltos de linea \\n\"}"
        
        return try {
            val response: HttpResponse = iaClient.post("https://api.openai.com/v1/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $key")
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("model", "gpt-4o-mini")
                    put("messages", buildJsonArray {
                        add(buildJsonObject { put("role", "user"); put("content", prompt) })
                    })
                    put("response_format", buildJsonObject { put("type", "json_object") })
                }.toString())
            }
            
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val content = json["choices"]?.jsonArray?.get(0)?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content ?: ""
            val res = Json.parseToJsonElement(content).jsonObject
            
            val recetas = res["comer"]?.jsonPrimitive?.content ?: "Lavar y consumir."
            sugerenciasCache[cacheKey] = recetas

            PredictionResult(fruta, estado, res["porcentaje"]?.jsonPrimitive?.double ?: 85.0,
                "Vida util estimada: ${res["dias"]?.jsonPrimitive?.content}", recetas, saludable, raw)
        } catch (e: Exception) {
            PredictionResult(fruta, estado, 75.0, "Revisar estado visual.", "Lavar antes de ingerir.", saludable, raw)
        }
    }

    // Analisis integral de imagen mediante GPT-4o-mini Vision.
    private suspend fun predecirConOpenAITotal(cleanB64: String, key: String): PredictionResult {
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
                                add(buildJsonObject { put("type", "text"); put("text", "Analiza frescura del alimento. Devuelve JSON: 'fruta' (español), 'estado', 'porcentaje', 'dias', 'comer' (3 sugerencias numeradas con saltos de linea \\n).") })
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
            
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val content = json["choices"]?.jsonArray?.get(0)?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content ?: ""
            val res = Json.parseToJsonElement(content).jsonObject
            
            PredictionResult(
                fruta = res["fruta"]?.jsonPrimitive?.content,
                estado = res["estado"]?.jsonPrimitive?.content,
                porcentajeFrescura = res["porcentaje"]?.jsonPrimitive?.doubleOrNull ?: 80.0,
                sugerencias = "Vida util estimada: ${res["dias"]?.jsonPrimitive?.content}",
                recetas = res["comer"]?.jsonPrimitive?.content,
                esSaludable = !(res["estado"]?.jsonPrimitive?.content?.contains("Deteriorado", true) ?: false),
                claseDetectada = "OPENAI_VISION"
            )
        } catch (e: Exception) {
            PredictionResult(null, null, 0.0, null, null, false, "ERROR", true, e.message)
        }
    }
}
