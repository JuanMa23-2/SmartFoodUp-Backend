package com.example.smartfoodup

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
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
import javax.imageio.ImageIO

@Serializable
data class PredictionResult(
    val fruta: String,
    val estado: String,
    val porcentajeFrescura: Double,
    val sugerencias: String,
    val esSaludable: Boolean,
    val claseDetectada: String = ""
)

object PredictionService {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    // Función crítica: Intenta obtener la clave de todas las formas posibles
    fun findApiKey(): String {
        val envKey = System.getenv("GEMINI_API_KEY")
        val propKey = System.getProperty("GEMINI_API_KEY")
        
        val key = when {
            !envKey.isNullOrBlank() && envKey.length > 20 -> envKey.trim()
            !propKey.isNullOrBlank() && propKey.length > 20 -> propKey.trim()
            else -> "FALTA_KEY"
        }
        return key
    }

    private var modelBundle: SavedModelBundle? = null
    
    init {
        try {
            val paths = listOf("server/smartfoodup_model", "smartfoodup_model", "/app/server/smartfoodup_model")
            for (path in paths) {
                val modelDir = File(path)
                if (modelDir.exists() && modelDir.isDirectory && File(modelDir, "saved_model.pb").exists()) {
                    modelBundle = SavedModelBundle.load(path, "serve")
                    println("Modelo TensorFlow cargado")
                    break
                }
            }
        } catch (e: Exception) {
            println("❌ Error modelo: ${e.message}")
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
        "Apple" to "Manzana", "Banana" to "Plátano", "Bellpepper" to "Pimiento",
        "Carrot" to "Zanahoria", "Cucumber" to "Pepino", "Grape" to "Uva",
        "Guava" to "Guayaba", "Jujube" to "Azufaifa", "Lemon" to "Limón",
        "Lulo" to "Lulo", "Mango" to "Mango", "Okra" to "Okra",
        "Orange" to "Naranja", "Pomegranate" to "Granada", "Potato" to "Papa",
        "Strawberry" to "Fresa", "Tamarillo" to "Tomate de árbol", "Tomato" to "Tomate"
    )

    suspend fun predecirImagen(base64: String): PredictionResult {
        val apiKey = findApiKey()
        
        if (apiKey == "FALTA_KEY") {
            return PredictionResult("Error", "Falta Configuración", 0.0, "La variable GEMINI_API_KEY no se detectó. Por favor, usa el 'Raw Editor' en Railway para guardarla.", false)
        }

        val cleanB64 = base64.substringAfter(",").replace("\n", "").replace("\r", "").replace(" ", "")
        val mimeType = if (base64.contains("webp")) "image/webp" else "image/jpeg"
        
        return if (modelBundle != null) {
            try {
                val classIndex = realizarInferenciaReal(cleanB64)
                mapearPrediccion(classIndex, apiKey)
            } catch (e: Exception) {
                predecirConGeminiTotal(cleanB64, mimeType, apiKey)
            }
        } else {
            predecirConGeminiTotal(cleanB64, mimeType, apiKey)
        }
    }

    private fun realizarInferenciaReal(cleanB64: String): Int {
        val bundle = modelBundle ?: throw Exception("No Model")
        val imageBytes = Base64.getDecoder().decode(cleanB64)
        val image = ImageIO.read(ByteArrayInputStream(imageBytes)) ?: throw Exception("Img Error")
        val resized = BufferedImage(224, 224, BufferedImage.TYPE_INT_RGB)
        resized.createGraphics().drawImage(image, 0, 0, 224, 224, null)

        val input = NdArrays.ofFloats(Shape.of(1, 224, 224, 3))
        for (y in 0 until 224) {
            for (x in 0 until 224) {
                val p = resized.getRGB(x, y)
                input.setFloat(((p shr 16) and 0xFF) / 255.0f, 0, y.toLong(), x.toLong(), 0)
                input.setFloat(((p shr 8) and 0xFF) / 255.0f, 0, y.toLong(), x.toLong(), 1)
                input.setFloat((p and 0xFF) / 255.0f, 0, y.toLong(), x.toLong(), 2)
            }
        }

        TFloat32.tensorOf(input).use { t ->
            val res = bundle.session().runner().feed("serving_default_input_1", t).fetch("StatefulPartitionedCall").run()
            res[0].use { out ->
                val probs = out as TFloat32
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
        return obtenerInfoExtraGemini(nombre, if(esSaludable) "Fresco" else "Podrido", esSaludable, raw, key)
    }

    private suspend fun obtenerInfoExtraGemini(fruta: String, estado: String, saludable: Boolean, raw: String, key: String): PredictionResult {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$key"
        val prompt = "Alimento: $fruta ($estado). JSON plano: {\"porcentaje\": n, \"dias\": \"X días\", \"comer\": \"recetas\"}"
        
        return try {
            val response: String = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(mapOf("contents" to listOf(mapOf("parts" to listOf(mapOf("text" to prompt))))))
            }.body()
            
            val jsonResponse = Json.parseToJsonElement(response).jsonObject
            if (jsonResponse.containsKey("error")) {
                return PredictionResult(fruta, estado, 75.0, "API Error: ${jsonResponse["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content}", saludable, raw)
            }

            val text = jsonResponse["candidates"]?.jsonArray?.get(0)?.jsonObject
                ?.get("content")?.jsonObject?.get("parts")?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
            
            val cleanJsonStr = text.trim().removePrefix("```json").removeSuffix("```").trim()
            val json = Json.parseToJsonElement(cleanJsonStr).jsonObject
            
            PredictionResult(fruta, if (saludable) "Fresco/Saludable" else "Podrido", json["porcentaje"]?.jsonPrimitive?.doubleOrNull ?: 80.0,
                "Vida útil: ${json["dias"]?.jsonPrimitive?.content}. Sugerencias: ${json["comer"]?.jsonPrimitive?.content}", saludable, raw)
        } catch (e: Exception) {
            PredictionResult(fruta, estado, 75.0, "Detalle error: ${e.message}", saludable, raw)
        }
    }

    private suspend fun predecirConGeminiTotal(cleanB64: String, mime: String, key: String): PredictionResult {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$key"
        
        return try {
            val response: String = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(mapOf("contents" to listOf(mapOf("parts" to listOf(
                    mapOf("text" to "Analiza la imagen y devuelve JSON plano con: 'fruta' (español), 'estado' (Fresco/Podrido), 'porcentaje' (0-100), 'dias', 'comer'."),
                    mapOf("inline_data" to mapOf("mime_type" to mime, "data" to cleanB64))
                )))))
            }.body()
            
            println("🤖 Gemini Response: $response")
            
            val jsonResponse = Json.parseToJsonElement(response).jsonObject
            
            // Si Google devuelve un error en el JSON
            if (jsonResponse.containsKey("error")) {
                val errorMsg = jsonResponse["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: "Error de Google"
                return PredictionResult("Error", "Google API Error", 0.0, errorMsg, false)
            }

            val text = jsonResponse["candidates"]?.jsonArray?.get(0)?.jsonObject
                ?.get("content")?.jsonObject?.get("parts")?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
            
            // Limpieza de JSON de Markdown
            val cleanJsonStr = text.trim()
                .removePrefix("```json")
                .removeSuffix("```")
                .trim()
            
            val json = Json.parseToJsonElement(cleanJsonStr).jsonObject
            
            val clase = json["fruta"]?.jsonPrimitive?.content ?: "Desconocido"
            val esSaludable = !(json["estado"]?.jsonPrimitive?.content?.contains("Podrido", true) ?: false)
            
            PredictionResult(
                fruta = clase,
                estado = json["estado"]?.jsonPrimitive?.content ?: "Detectado",
                porcentajeFrescura = json["porcentaje"]?.jsonPrimitive?.doubleOrNull ?: 80.0,
                sugerencias = "Vida útil: ${json["dias"]?.jsonPrimitive?.content}. Sugerencia: ${json["comer"]?.jsonPrimitive?.content}",
                esSaludable = esSaludable,
                claseDetectada = "IA_DETECTION"
            )
        } catch (e: Exception) {
            println("❌ Error en predecirConGeminiTotal: ${e.message}")
            e.printStackTrace()
            PredictionResult("Error", "Error IA", 0.0, "Detalle: ${e.localizedMessage ?: "Fallo al procesar"}. Verifica el tamaño de imagen o la clave.", false)
        }
    }
}
