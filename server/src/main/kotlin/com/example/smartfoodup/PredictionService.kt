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
import java.util.*

@Serializable
data class PredictionResult(
    val fruta: String,
    val estado: String,
    val porcentajeFrescura: Double,
    val sugerencias: String,
    val esSaludable: Boolean,
    val claseDetectada: String = "" // Para compatibilidad con IaRouting
)

object PredictionService {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private val API_KEY = System.getenv("GEMINI_API_KEY") ?: "TU_API_KEY_AQUI"

    private val classNames: List<String> by lazy {
        val jsonStream = object {}.javaClass.classLoader.getResourceAsStream("clases.json")
            ?: throw IllegalStateException("No se encontró el archivo clases.json")
        Json.decodeFromString<List<String>>(jsonStream.bufferedReader().readText())
    }

    private val traducciones = mapOf(
        "Apple" to "Manzana",
        "Banana" to "Plátano",
        "Bellpepper" to "Pimiento",
        "Carrot" to "Zanahoria",
        "Cucumber" to "Pepino",
        "Grape" to "Uva",
        "Guava" to "Guayaba",
        "Jujube" to "Azufaifa",
        "Lemon" to "Limón",
        "Lulo" to "Lulo",
        "Mango" to "Mango",
        "Okra" to "Okra",
        "Orange" to "Naranja",
        "Pomegranate" to "Granada",
        "Potato" to "Papa",
        "Strawberry" to "Fresa",
        "Tamarillo" to "Tomate de árbol",
        "Tomato" to "Tomate"
    )

    suspend fun predecirImagen(base64: String): PredictionResult {
        // En un entorno real con TensorFlow Java (requiere configuración de .pb/SavedModel)
        // aquí se llamaría al intérprete. Por ahora usamos la lógica de inferencia simulada.
        val classIndex = realizarInferenciaLocal(base64)
        return mapearPrediccion(classIndex)
    }

    suspend fun mapearPrediccion(claseIndex: Int): PredictionResult {
        val classNameRaw = classNames.getOrElse(claseIndex) { "Apple__Healthy" }
        
        val partes = classNameRaw.split("__")
        val nombreIngles = partes[0]
        val estadoIngles = partes.getOrNull(1) ?: "Healthy"
        
        val nombreEspanol = traducciones[nombreIngles] ?: nombreIngles
        val estadoEspanol = if (estadoIngles.contains("Healthy", true) || estadoIngles.contains("Healty", true)) "Fresco/Saludable" else "Podrido/No saludable"
        val esSaludable = !estadoEspanol.contains("Podrido")

        // Obtener sugerencias dinámicas de Gemini
        return obtenerSugerenciasGemini(nombreEspanol, estadoEspanol, esSaludable, classNameRaw)
    }

    private fun realizarInferenciaLocal(base64: String): Int {
        // Simulación: aquí iría interpreter.run() con el archivo .h5 (convertido a SavedModel)
        return (0 until classNames.size).random()
    }

    private suspend fun obtenerSugerenciasGemini(fruta: String, estado: String, esSaludable: Boolean, raw: String): PredictionResult {
        if (API_KEY == "TU_API_KEY_AQUI") {
            return PredictionResult(fruta, estado, 80.0, "Consume pronto. (Configura API Key para más)", esSaludable, raw)
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$API_KEY"
        
        val prompt = "El modelo detectó: $fruta ($estado). Responde SOLO un JSON con: {\"porcentaje\": número, \"sugerencias\": \"texto corto de qué hacer\"}"

        try {
            val response: String = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(mapOf("contents" to listOf(mapOf("parts" to listOf(mapOf("text" to prompt))))))
            }.body()

            val textResult = Json.parseToJsonElement(response).jsonObject["candidates"]?.jsonArray?.get(0)?.jsonObject
                ?.get("content")?.jsonObject?.get("parts")?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""

            val cleanJson = textResult.trim().removeSurrounding("```json", "```").trim()
            val extraData = Json.parseToJsonElement(cleanJson).jsonObject

            return PredictionResult(
                fruta = fruta,
                estado = estado,
                porcentajeFrescura = extraData["porcentaje"]?.jsonPrimitive?.double ?: 75.0,
                sugerencias = extraData["sugerencias"]?.jsonPrimitive?.content ?: "Sin sugerencias",
                esSaludable = esSaludable,
                claseDetectada = raw
            )
        } catch (e: Exception) {
            return PredictionResult(fruta, estado, 50.0, "Consumir con precaución.", esSaludable, raw)
        }
    }
}
