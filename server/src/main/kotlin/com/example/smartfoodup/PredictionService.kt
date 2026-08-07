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

@Serializable
data class PredictionResult(
    val fruta: String?,
    val estado: String?,
    val porcentajeFrescura: Double,
    val sugerencias: String?,
    val recetas: String?,
    val esSaludable: Boolean,
    val errorOcurrido: Boolean = false,
    val alertaRiesgo: String? = null,
    val infoHardware: String? = null
)

// Motor de diagnostico integral mediante hardware y OpenAI Vision.
object PredictionService {
    private val iaClient = HttpClient(CIO)

    fun findApiKey(): String {
        return System.getenv("OPENAI_API_KEY") ?: "FALTA_KEY"
    }

    // Consulta la telemetria mas reciente de la Raspberry Pi en Railway.
    private suspend fun obtenerContextoHardware(id: Int?): String {
        if (id == null) return "Sin conexion a sensores."
        return try {
            newSuspendedTransaction {
                MedicionesSensores.select { MedicionesSensores.dispositivoId eq id }
                    .orderBy(MedicionesSensores.id to SortOrder.DESC)
                    .limit(1)
                    .map { 
                        "Hardware: Peso ${it[MedicionesSensores.pesoGramos]}g, Gas ${it[MedicionesSensores.gasPorcentaje]}%, Temp ${it[MedicionesSensores.temperatura]}C, Hum ${it[MedicionesSensores.humedad]}%."
                    }.singleOrNull() ?: "Sin telemetria reciente."
            }
        } catch (e: Exception) { "Error al leer hardware." }
    }

    suspend fun predecirImagen(base64: String, idDispositivo: Int? = null): PredictionResult {
        val apiKey = findApiKey()
        if (apiKey == "FALTA_KEY") return PredictionResult("Falta Clave", null, 0.0, "N/A", null, false, true)
        
        val contextoHardware = obtenerContextoHardware(idDispositivo ?: 1)
        val cleanB64 = base64.substringAfter(",").replace("\n", "").replace("\r", "").replace(" ", "")
        
        return predecirConOpenAITotal(cleanB64, apiKey, contextoHardware)
    }

    private suspend fun predecirConOpenAITotal(cleanB64: String, key: String, hardware: String): PredictionResult {
        val prompt = """
            Analiza el alimento. Datos de sensores: $hardware.
            Responde JSON: 'fruta', 'estado' (Fresco/Maduro/Deteriorado), 'porcentaje', 'dias', 'comer' (3 sugerencias numeradas con \n), 'riesgo' (Analiza si el gas o humedad son peligrosos), 'inventario' (Segun el peso).
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
        } catch (e: Exception) { PredictionResult("Error Vision", "N/A", 0.0, "N/A", "N/A", false, true) }
    }

    suspend fun mapearPrediccion(idx: Int): PredictionResult = predecirImagen("")
}
