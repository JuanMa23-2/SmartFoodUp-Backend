package com.example.smartfoodup

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.SortOrder
import javax.imageio.ImageIO
import java.io.ByteArrayInputStream

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

// Motor de diagnóstico inteligente mediante hardware y OpenAI Vision.
object PredictionService {
    private val iaClient = HttpClient(CIO)

    fun findApiKey(): String {
        return System.getenv("OPENAI_API_KEY")?.trim() ?: "FALTA_KEY"
    }

    // Consulta la telemetría más reciente de la Raspberry Pi.
    private suspend fun obtenerContextoHardware(id: Int?): String {
        if (id == null) return "Análisis sin datos de hardware vinculados."
        return try {
            newSuspendedTransaction {
                MedicionesSensores.select { MedicionesSensores.dispositivoId eq id }
                    .orderBy(MedicionesSensores.id to SortOrder.DESC)
                    .limit(1)
                    .map { 
                        "Hardware: Peso ${it[MedicionesSensores.pesoGramos]}g, Gas ${it[MedicionesSensores.gasPorcentaje]}%, Temp ${it[MedicionesSensores.temperatura]}C, Hum ${it[MedicionesSensores.humedad]}%."
                    }.singleOrNull() ?: "Hardware vinculado pero sin mediciones recientes."
            }
        } catch (e: Exception) { "Hardware inaccesible en este momento." }
    }

    suspend fun predecirImagen(base64: String, idDispositivo: Int? = null): PredictionResult {
        val apiKey = findApiKey()
        if (apiKey == "FALTA_KEY") return PredictionResult("Error: Clave API", null, 0.0, "Configurar OPENAI_API_KEY en Railway.", null, false, errorOcurrido = true)
        
        val contextoHardware = obtenerContextoHardware(idDispositivo)
        val cleanB64 = base64.substringAfter(",").replace("\n", "").replace("\r", "").replace(" ", "")
        
        return predecirConOpenAITotal(cleanB64, apiKey, contextoHardware)
    }

    private suspend fun predecirConOpenAITotal(cleanB64: String, key: String, hardware: String): PredictionResult {
        val prompt = """
            Analiza el alimento mostrado en la imagen.
            Contexto del hardware: $hardware.
            Responde estrictamente en formato JSON plano con estos campos: 
            'fruta' (nombre en español), 
            'estado' (Fresco/Maduro/Deteriorado), 
            'porcentaje' (índice de frescura 0-100), 
            'dias' (vida útil estimada), 
            'comer' (3 sugerencias numeradas separadas por saltos de línea \n),
            'riesgo' (Analiza si el gas o humedad son peligrosos para este alimento), 
            'inventario' (Sugerencia de compra según el peso detectado).
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
            
            val responseText = response.bodyAsText()
            val json = Json.parseToJsonElement(responseText).jsonObject
            val content = json["choices"]?.jsonArray?.get(0)?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content ?: ""
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
        } catch (e: Exception) { 
            PredictionResult("Error en el Análisis", "Error", 0.0, "Fallo en la comunicación con OpenAI.", null, false, errorOcurrido = true, mensajeError = e.message) 
        }
    }

    suspend fun mapearPrediccion(idx: Int): PredictionResult {
        // Mantener por compatibilidad con análisis manual de catálogo.
        return PredictionResult("Analizando...", null, 0.0, null, null, false, errorOcurrido = true)
    }
}
