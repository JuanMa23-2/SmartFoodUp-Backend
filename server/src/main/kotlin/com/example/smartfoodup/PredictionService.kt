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

    private val API_KEY = System.getenv("GEMINI_API_KEY") ?: "TU_API_KEY_AQUI"
    private var modelBundle: SavedModelBundle? = null
    
    init {
        val currentDir = File(".").absolutePath
        println("📂 Directorio actual de ejecución: $currentDir")
        
        try {
            // Rutas extendidas para Railway
            val paths = listOf(
                "server/smartfoodup_model",
                "smartfoodup_model",
                "/app/server/smartfoodup_model",
                "/app/smartfoodup_model"
            )
            for (path in paths) {
                val modelDir = File(path)
                println("🔍 Buscando modelo en: ${modelDir.absolutePath}")
                if (modelDir.exists() && modelDir.isDirectory && File(modelDir, "saved_model.pb").exists()) {
                    modelBundle = SavedModelBundle.load(path, "serve")
                    println("✅ ¡ÉXITO! Modelo TensorFlow cargado desde: $path")
                    break
                }
            }
            if (modelBundle == null) println("⚠️ No se encontró la carpeta smartfoodup_model en ninguna ruta.")
        } catch (e: Exception) {
            println("❌ Error fatal cargando modelo: ${e.message}")
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
        val mimeType = if (base64.contains("webp")) "image/webp" else "image/jpeg"
        val cleanB64 = base64.substringAfter(",").replace("\n", "").replace("\r", "").replace(" ", "")
        
        return if (modelBundle != null) {
            try {
                val classIndex = realizarInferenciaReal(cleanB64)
                mapearPrediccion(classIndex)
            } catch (e: Exception) {
                predecirConGeminiTotal(cleanB64, mimeType)
            }
        } else {
            predecirConGeminiTotal(cleanB64, mimeType)
        }
    }

    private fun realizarInferenciaReal(cleanB64: String): Int {
        val bundle = modelBundle ?: throw Exception("No Model")
        val imageBytes = Base64.getDecoder().decode(cleanB64)
        val image = ImageIO.read(ByteArrayInputStream(imageBytes)) ?: throw Exception("Formato inválido")
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

    suspend fun mapearPrediccion(idx: Int): PredictionResult {
        val raw = classNames.getOrElse(idx) { "Apple__Healthy" }
        val partes = raw.split("__")
        val nombre = traducciones[partes[0]] ?: partes[0]
        val esSaludable = !raw.contains("Rotten", true)
        return obtenerInfoExtraGemini(nombre, if(esSaludable) "Fresco" else "Podrido", esSaludable, raw)
    }

    private suspend fun obtenerInfoExtraGemini(fruta: String, estado: String, saludable: Boolean, raw: String): PredictionResult {
        if (API_KEY == "TU_API_KEY_AQUI") return PredictionResult(fruta, estado, 85.0, "Verifica GEMINI_API_KEY en Railway", saludable, raw)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$API_KEY"
        
        val prompt = """
            Alimento detectado: $fruta ($estado). 
            Responde SOLO en formato JSON plano:
            {
              "porcentaje": número 0-100,
              "dias": "cuántos días aprox aguanta",
              "comer": "formas de comerlo o recetas sugeridas"
            }
        """.trimIndent()
        
        return try {
            val response: String = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(mapOf("contents" to listOf(mapOf("parts" to listOf(mapOf("text" to prompt))))))
            }.body()
            val text = Json.parseToJsonElement(response).jsonObject["candidates"]?.jsonArray?.get(0)?.jsonObject
                ?.get("content")?.jsonObject?.get("parts")?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
            val json = Json.parseToJsonElement(text.trim().removeSurrounding("```json", "```").trim()).jsonObject
            
            PredictionResult(
                fruta = fruta,
                estado = if (saludable) "Fresco/Saludable" else "Podrido/No saludable",
                porcentajeFrescura = json["porcentaje"]?.jsonPrimitive?.doubleOrNull ?: 80.0,
                sugerencias = "Vida útil: ${json["dias"]?.jsonPrimitive?.content}. Sugerencias: ${json["comer"]?.jsonPrimitive?.content}",
                esSaludable = saludable,
                claseDetectada = raw
            )
        } catch (e: Exception) {
            PredictionResult(fruta, estado, 75.0, "Consumir pronto.", saludable, raw)
        }
    }

    private suspend fun predecirConGeminiTotal(cleanB64: String, mime: String): PredictionResult {
        if (API_KEY == "TU_API_KEY_AQUI" || API_KEY.isBlank()) {
            return PredictionResult("Error", "Falta API Key", 0.0, "La variable GEMINI_API_KEY no se guardó en Railway", false)
        }
        
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$API_KEY"
        
        return try {
            val response: String = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(mapOf("contents" to listOf(mapOf("parts" to listOf(
                    mapOf("text" to "Analiza la imagen y devuelve JSON con 'fruta', 'estado', 'porcentaje', 'dias', 'comer'."),
                    mapOf("inline_data" to mapOf("mime_type" to mime, "data" to cleanB64))
                )))))
            }.body()
            
            if (response.contains("error")) {
                val errorMsg = Json.parseToJsonElement(response).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: "Error desconocido"
                return PredictionResult("Error", "Error Google", 0.0, "Google dice: $errorMsg", false)
            }

            val text = Json.parseToJsonElement(response).jsonObject["candidates"]?.jsonArray?.get(0)?.jsonObject
                ?.get("content")?.jsonObject?.get("parts")?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
            val json = Json.parseToJsonElement(text.trim().removeSurrounding("```json", "```").trim()).jsonObject
            
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
            PredictionResult("Error", "Error IA", 0.0, "Detalle: ${e.localizedMessage}", false)
        }
    }
}
