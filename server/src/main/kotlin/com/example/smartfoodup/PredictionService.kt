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
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private val API_KEY = System.getenv("GEMINI_API_KEY") ?: "TU_API_KEY_AQUI"
    private var modelBundle: SavedModelBundle? = null
    
    init {
        try {
            val paths = listOf("server/smartfoodup_model", "smartfoodup_model")
            for (path in paths) {
                val modelDir = File(path)
                if (modelDir.exists() && modelDir.isDirectory) {
                    modelBundle = SavedModelBundle.load(path, "serve")
                    println("✅ Modelo TensorFlow cargado desde: ${modelDir.absolutePath}")
                    break
                }
            }
        } catch (e: Exception) {
            println("⚠️ Error al cargar modelo local: ${e.message}")
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
        return if (modelBundle != null) {
            try {
                val classIndex = realizarInferenciaReal(base64)
                mapearPrediccion(classIndex)
            } catch (e: Exception) {
                println("⚠️ Fallo en inferencia local, usando Gemini: ${e.message}")
                predecirConGeminiTotal(base64)
            }
        } else {
            predecirConGeminiTotal(base64)
        }
    }

    private fun realizarInferenciaReal(base64: String): Int {
        val bundle = modelBundle ?: throw IllegalStateException("Modelo no cargado")
        
        val imageBytes = Base64.getDecoder().decode(base64.replace("\n", "").replace(" ", ""))
        val originalImage = ImageIO.read(ByteArrayInputStream(imageBytes)) ?: throw IllegalArgumentException("Imagen inválida")
        val resizedImage = BufferedImage(224, 224, BufferedImage.TYPE_INT_RGB)
        resizedImage.createGraphics().drawImage(originalImage, 0, 0, 224, 224, null)

        val inputData = NdArrays.ofFloats(Shape.of(1, 224, 224, 3))
        for (y in 0 until 224) {
            for (x in 0 until 224) {
                val pixel = resizedImage.getRGB(x, y)
                inputData.setFloat(((pixel shr 16) and 0xFF) / 255.0f, 0, y.toLong(), x.toLong(), 0) // R
                inputData.setFloat(((pixel shr 8) and 0xFF) / 255.0f, 0, y.toLong(), x.toLong(), 1)  // G
                inputData.setFloat((pixel and 0xFF) / 255.0f, 0, y.toLong(), x.toLong(), 2)         // B
            }
        }

        TFloat32.tensorOf(inputData).use { inputTensor ->
            val result = bundle.session().runner()
                .feed("serving_default_input_1", inputTensor)
                .fetch("StatefulPartitionedCall")
                .run()

            result[0].use { outputTensor ->
                val probabilities = outputTensor as TFloat32
                var maxIdx = 0
                var maxProb = -1.0f
                for (i in 0 until 36) {
                    val prob = probabilities.getFloat(0, i.toLong())
                    if (prob > maxProb) {
                        maxProb = prob
                        maxIdx = i
                    }
                }
                return maxIdx
            }
        }
    }

    suspend fun mapearPrediccion(claseIndex: Int): PredictionResult {
        val raw = classNames.getOrElse(claseIndex) { "Apple__Healthy" }
        val partes = raw.split("__")
        val nombre = traducciones[partes[0]] ?: partes[0]
        val esSaludable = !raw.contains("Rotten", true)
        
        return obtenerInfoExtraGemini(nombre, if(esSaludable) "Fresco" else "Podrido", esSaludable, raw)
    }

    private suspend fun obtenerInfoExtraGemini(fruta: String, estado: String, saludable: Boolean, raw: String): PredictionResult {
        if (API_KEY == "TU_API_KEY_AQUI") return PredictionResult(fruta, estado, 80.0, "Configura API Key", saludable, raw)
        
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$API_KEY"
        val prompt = "Alimento: $fruta ($estado). JSON con: {\"dias\": \"X días\", \"recetas\": \"breve\", \"porcentaje\": 0-100}"
        
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
                porcentajeFrescura = json["porcentaje"]?.jsonPrimitive?.double ?: 75.0,
                sugerencias = "Vida útil: ${json["dias"]?.jsonPrimitive?.content}. Consejos: ${json["recetas"]?.jsonPrimitive?.content}",
                esSaludable = saludable,
                claseDetectada = raw
            )
        } catch (e: Exception) {
            PredictionResult(fruta, estado, 50.0, "Consumir pronto.", saludable, raw)
        }
    }

    private suspend fun predecirConGeminiTotal(base64: String): PredictionResult {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$API_KEY"
        val prompt = "Analiza y devuelve JSON con 'fruta', 'estado', 'porcentaje', 'dias', 'sugerencias' usando esta lista: ${classNames.joinToString()}"
        
        return try {
            val response: String = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(mapOf("contents" to listOf(mapOf("parts" to listOf(
                    mapOf("text" to prompt),
                    mapOf("inline_data" to mapOf("mime_type" to "image/jpeg", "data" to base64.replace("\n", "").replace(" ", "")))
                )))))
            }.body()
            
            val text = Json.parseToJsonElement(response).jsonObject["candidates"]?.jsonArray?.get(0)?.jsonObject
                ?.get("content")?.jsonObject?.get("parts")?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
            val json = Json.parseToJsonElement(text.trim().removeSurrounding("```json", "```").trim()).jsonObject
            
            val clase = json["fruta"]?.jsonPrimitive?.content ?: "Apple__Healthy"
            val esSaludable = !clase.contains("Rotten", true)
            
            PredictionResult(
                fruta = traducciones[clase.split("__")[0]] ?: clase,
                estado = if (esSaludable) "Fresco/Saludable" else "No saludable",
                porcentajeFrescura = json["porcentaje"]?.jsonPrimitive?.double ?: 80.0,
                sugerencias = "Vida útil: ${json["dias"]?.jsonPrimitive?.content}. Sugerencia: ${json["sugerencias"]?.jsonPrimitive?.content}",
                esSaludable = esSaludable,
                claseDetectada = clase
            )
        } catch (e: Exception) {
            PredictionResult("Error", "Error IA", 0.0, "Reintente", false)
        }
    }
}
