package com.example.smartfoodup

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.SortOrder
import java.time.LocalDateTime
import java.time.Duration

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
    val infoHardware: String? = null,
    val hardwareOnline: Boolean = false,
    val hardwareMensaje: String = "Esperando hardware..."
)

// Motor de diagnóstico experto optimizado para reconocimiento de frutas dañadas.
object PredictionService {
    private val iaClient = HttpClient(CIO)

    fun findApiKey(): String {
        return System.getenv("OPENAI_API_KEY")?.trim() ?: "FALTA_KEY"
    }

    private fun limpiarBase64(raw: String): String {
        return if (raw.contains(",")) raw.substringAfter(",") 
               else raw.replace("\n", "").replace("\r", "").trim()
    }

    private suspend fun obtenerEstadoHardware(id: Int?): Pair<Boolean, String> {
        if (id == null) return Pair(false, "Cámara")
        return try {
            newSuspendedTransaction {
                val registro = MedicionesSensores.select { MedicionesSensores.dispositivoId eq id }
                    .orderBy(MedicionesSensores.id to SortOrder.DESC)
                    .limit(1).singleOrNull()

                if (registro == null) Pair(false, "Sin estación.")
                else {
                    val fecha = registro[MedicionesSensores.fechaMedicion]
                    val diff = Duration.between(fecha, LocalDateTime.now()).seconds
                    if (diff < 60) Pair(true, "Online. Peso: ${registro[MedicionesSensores.pesoGramos]}g")
                    else Pair(false, "Offline.")
                }
            }
        } catch (e: Exception) { Pair(false, "Error hardware.") }
    }

    suspend fun predecirImagen(base64: String, idDispositivo: Int? = null): PredictionResult {
        val apiKey = findApiKey()
        if (apiKey == "FALTA_KEY") return PredictionResult("Falta Clave", null, 0.0, null, null, false, errorOcurrido = true)
        
        val (online, mensajeHardware) = obtenerEstadoHardware(idDispositivo)
        val b64Limpio = limpiarBase64(base64)
        
        val prompt = """
            Eres un inspector de calidad de alimentos. Analiza la imagen. 
            Responde JSON plano:
            1. 'fruta': Nombre común (ej: Manzana, Plátano). Solo pon 'NULO' si no hay nada de comida.
            2. 'estado': Fresco, Maduro o Deteriorado.
            3. 'porcentaje': 0-100. SIEMPRE asigna un valor real. Si está podrida, pon entre 0 y 15.
            4. 'dias': Vida útil (ej: 0 días si está podrida).
            5. 'comer': 3 sugerencias con \n. (Si está mal, aconseja no comer).
            Contexto hardware: $mensajeHardware.
        """.trimIndent()
        
        return try {
            val response: HttpResponse = iaClient.post("https://api.openai.com/v1/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
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
                                    put("image_url", buildJsonObject { put("url", "data:image/jpeg;base64,$b64Limpio") })
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
            
            val nombre = res["fruta"]?.jsonPrimitive?.content ?: "NULO"
            if (nombre.contains("NULO", true)) {
                return PredictionResult("No detectado", "N/A", 0.0, "N/A", "N/A", false, hardwareOnline = online, hardwareMensaje = mensajeHardware)
            }

            val salud = res["porcentaje"]?.jsonPrimitive?.doubleOrNull ?: 0.0

            PredictionResult(
                fruta = nombre,
                estado = res["estado"]?.jsonPrimitive?.content,
                porcentajeFrescura = salud,
                sugerencias = res["dias"]?.jsonPrimitive?.content,
                recetas = res["comer"]?.jsonPrimitive?.content,
                esSaludable = salud > 30.0,
                alertaRiesgo = res["riesgo"]?.jsonPrimitive?.content,
                infoHardware = if (online) mensajeHardware else null,
                hardwareOnline = online,
                hardwareMensaje = mensajeHardware
            )
        } catch (e: Exception) {
            PredictionResult("Error", null, 0.0, null, null, false, errorOcurrido = true, mensajeError = e.message)
        }
    }

    suspend fun mapearPrediccion(idx: Int): PredictionResult = predecirImagen("")
}
