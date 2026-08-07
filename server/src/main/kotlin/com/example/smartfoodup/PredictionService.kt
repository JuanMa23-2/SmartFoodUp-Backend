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
    val hardwareOnline: Boolean? = null, // Cambiado a opcional para filtrado
    val hardwareMensaje: String? = null
)

object PredictionService {
    private val iaClient = HttpClient(CIO)

    fun findApiKey(): String {
        return System.getenv("OPENAI_API_KEY")?.trim() ?: "FALTA_KEY"
    }

    private fun limpiarBase64(raw: String): String {
        return if (raw.contains(",")) raw.substringAfter(",") 
               else raw.replace("\n", "").replace("\r", "").replace(" ", "").trim()
    }

    private suspend fun obtenerDatosHardware(id: Int?): Pair<Boolean, String?> {
        if (id == null) return Pair(false, null)
        return try {
            newSuspendedTransaction {
                val registro = MedicionesSensores.select { MedicionesSensores.dispositivoId eq id }
                    .orderBy(MedicionesSensores.id to SortOrder.DESC)
                    .limit(1).singleOrNull()

                if (registro == null) Pair(false, "Estacion sin datos.")
                else {
                    val p = registro[MedicionesSensores.pesoGramos]
                    val g = registro[MedicionesSensores.gasPorcentaje]
                    val t = registro[MedicionesSensores.temperatura]
                    val h = registro[MedicionesSensores.humedad]
                    Pair(true, "Online. Peso: ${p}g | Gas: ${g}% | Temp: ${t}C | Hum: ${h}%")
                }
            }
        } catch (e: Exception) { Pair(false, "Error de enlace.") }
    }

    suspend fun predecirImagen(base64: String, idDispositivo: Int? = null): PredictionResult {
        val apiKey = findApiKey()
        if (apiKey == "FALTA_KEY") return PredictionResult("Falta Clave", null, 0.0, null, null, false, errorOcurrido = true)
        
        val (online, mensajeHardware) = obtenerDatosHardware(idDispositivo)
        val b64Limpio = limpiarBase64(base64)
        
        // Si el usuario eligio modo estacion y no hay datos, enviamos error de hardware
        if (idDispositivo != null && !online) {
            return PredictionResult("Estacion no detectada", "N/A", 0.0, "N/A", "N/A", false, hardwareOnline = false, hardwareMensaje = "Raspberry fuera de linea")
        }

        val prompt = if (idDispositivo != null) {
            """
            MODO ESTACION INTELIGENTE. Sensores: $mensajeHardware.
            Analiza la imagen. Aunque la fruta este muy negra o dañada, IDENTIFICALA.
            Responde JSON plano: 'fruta', 'estado' (Fresco/Maduro/Deteriorado), 'porcentaje' (0-100), 'dias', 'comer' (3 sugerencias con \n), 'riesgo' (analiza sensores).
            REGLA: Si ves moho o color negro intenso, el porcentaje DEBE ser menor a 15 y di que no es comestible.
            """.trimIndent()
        } else {
            "MODO NORMAL. Analiza la imagen. Responde JSON plano: 'fruta', 'estado', 'porcentaje' (0-100), 'dias', 'comer'. Si esta muy dañada, salud < 15."
        }
        
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
            
            val fruta = res["fruta"]?.jsonPrimitive?.content ?: "Desconocido"
            val salud = res["porcentaje"]?.jsonPrimitive?.doubleOrNull ?: 0.0

            PredictionResult(
                fruta = if (fruta.contains("NULO", true)) "No detectado" else fruta,
                estado = res["estado"]?.jsonPrimitive?.content,
                porcentajeFrescura = salud,
                sugerencias = res["dias"]?.jsonPrimitive?.content,
                recetas = res["comer"]?.jsonPrimitive?.content,
                esSaludable = salud > 30.0,
                alertaRiesgo = res["riesgo"]?.jsonPrimitive?.content,
                infoHardware = if (idDispositivo != null) mensajeHardware else null,
                hardwareOnline = if (idDispositivo != null) online else null, // Si es nulo, el front ocultara la barra
                hardwareMensaje = if (idDispositivo != null) mensajeHardware else null
            )
        } catch (e: Exception) {
            PredictionResult("Error de analisis", null, 0.0, null, null, false, errorOcurrido = true)
        }
    }

    suspend fun mapearPrediccion(idx: Int): PredictionResult = predecirImagen("")
}
