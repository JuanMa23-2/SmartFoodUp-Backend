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

// Servicio de analisis mediante modelos locales e IA generativa sincronizados.
object PredictionService {
    private val iaClient = HttpClient(CIO)

    fun findApiKey(): String {
        val envKey = System.getenv("GEMINI_API_KEY")
        val propKey = System.getProperty("GEMINI_API_KEY")
        return when {
            !envKey.isNullOrBlank() && envKey.length > 15 -> envKey.trim()
            !propKey.isNullOrBlank() -> propKey.trim()
            else -> "FALTA_KEY"
        }
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
            println("Error al inicializar modelo local: ${e.message}")
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

    suspend fun predecirImagen(base64: String): PredictionResult {
        val apiKey = findApiKey()
        if (apiKey == "FALTA_KEY") {
            return PredictionResult(null, null, 0.0, "API KEY no detectada.", null, false, errorOcurrido = true)
        }

        val cleanB64 = base64.substringAfter(",").replace("\n", "").replace("\r", "").replace(" ", "")
        val mimeType = if (base64.contains("webp")) "image/webp" else "image/jpeg"
        
        return if (modelBundle != null) {
            try {
                val classIndex = realizarInferenciaReal(cleanB64)
                mapearPrediccion(classIndex, apiKey)
            } catch (e: Exception) {
                // Registro de error en consola para depuracion en Railway.
                println("Fallo en motor local: ${e.message}. Recurriendo a Gemini.")
                predecirConGeminiTotal(cleanB64, mimeType, apiKey)
            }
        } else {
            predecirConGeminiTotal(cleanB64, mimeType, apiKey)
        }
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

        // Extraccion dinamica de los nombres de nodos para compatibilidad total con el archivo .pb.
        val signature = bundle.metaGraphDef().getSignatureDefOrThrow("serving_default")
        val inputName = signature.getInputsMap().values.first().getName().substringBefore(":")
        val outputName = signature.getOutputsMap().values.first().getName().substringBefore(":")

        TFloat32.tensorOf(inputData).use { t ->
            val result = bundle.session().runner().feed(inputName, t).fetch(outputName).run()
            result.use { res ->
                val probs = res[0] as TFloat32
                var max = 0
                var maxP = -1.0f
                for (i in 0 until 36) {
                    val p = probs.getFloat(0, i.toLong())
                    if (p > maxP) {
                        maxP = p
                        max = i
                    }
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
        return obtenerInfoExtraGemini(nombre, if(esSaludable) "Fresco" else "Deteriorado", esSaludable, raw, key)
    }

    private suspend fun obtenerInfoExtraGemini(fruta: String, estado: String, saludable: Boolean, raw: String, key: String): PredictionResult {
        // Uso del alias de produccion gemini-flash-latest confirmado en la lista tecnica.
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=$key"
        val prompt = "Alimento: $fruta ($estado). Responder estrictamente en JSON: {\"porcentaje\": 90, \"dias\": \"X dias aprox\", \"comer\": \"Dar 3 sugerencias detalladas y numeradas para comerlo\"}"
        
        return try {
            val response: HttpResponse = iaClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody("""{"contents":[{"parts":[{"text":"$prompt"}]}]}""")
            }
            val responseText = response.bodyAsText()
            val json = Json.parseToJsonElement(responseText).jsonObject
            
            if (json.containsKey("error")) {
                val errorMsg = json["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: "Error API"
                return PredictionResult(fruta, estado, 85.0, "Google dice: $errorMsg", "Lavar y procesar pronto.", saludable, raw, true, errorMsg)
            }

            val text = json["candidates"]?.jsonArray?.get(0)?.jsonObject
                ?.get("content")?.jsonObject?.get("parts")?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
            val res = Json.parseToJsonElement(text.trim().removePrefix("```json").removeSuffix("```").trim()).jsonObject
            
            PredictionResult(
                fruta = fruta,
                estado = estado,
                porcentajeFrescura = res["porcentaje"]?.jsonPrimitive?.double ?: 88.0,
                sugerencias = "Vida útil estimada: ${res["dias"]?.jsonPrimitive?.content}",
                recetas = res["comer"]?.jsonPrimitive?.content,
                esSaludable = saludable,
                claseDetectada = raw
            )
        } catch (e: Exception) {
            PredictionResult(fruta, estado, 75.0, "Consultar estado visual.", "Consumir segun preferencia.", saludable, raw, true, e.message)
        }
    }

    private suspend fun predecirConGeminiTotal(cleanB64: String, mime: String, key: String): PredictionResult {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=$key"
        val bodyStr = """
            {
              "contents": [{
                "parts": [
                  {"text": "Analiza la frescura. JSON plano: 'fruta' (español), 'estado', 'porcentaje', 'dias', 'comer' (3 sugerencias numeradas)."},
                  {"inline_data": {"mime_type": "$mime", "data": "$cleanB64"}}
                ]
              }]
            }
        """.trimIndent()

        return try {
            val response: HttpResponse = iaClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(bodyStr)
            }
            val responseText = response.bodyAsText()
            val json = Json.parseToJsonElement(responseText).jsonObject
            
            if (json.containsKey("error")) {
                val errorMsg = json["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: "Error"
                return PredictionResult(null, null, 0.0, "Google dice: $errorMsg", null, false, "ERROR", true, errorMsg)
            }

            val text = json["candidates"]?.jsonArray?.get(0)?.jsonObject
                ?.get("content")?.jsonObject?.get("parts")?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
            val res = Json.parseToJsonElement(text.trim().removePrefix("```json").removeSuffix("```").trim()).jsonObject
            
            PredictionResult(
                fruta = res["fruta"]?.jsonPrimitive?.content,
                estado = res["estado"]?.jsonPrimitive?.content,
                porcentajeFrescura = res["porcentaje"]?.jsonPrimitive?.doubleOrNull ?: 80.0,
                sugerencias = "Vida útil estimada: ${res["dias"]?.jsonPrimitive?.content}",
                recetas = res["comer"]?.jsonPrimitive?.content,
                esSaludable = !(res["estado"]?.jsonPrimitive?.content?.contains("Deteriorado", true) ?: false),
                claseDetectada = "IA_BACKUP"
            )
        } catch (e: Exception) {
            PredictionResult(null, null, 0.0, null, null, false, "ERROR", true, e.message)
        }
    }
}
