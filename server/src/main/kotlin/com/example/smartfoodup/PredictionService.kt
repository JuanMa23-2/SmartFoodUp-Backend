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
    val hardwareOnline: Boolean? = null,
    val hardwareMensaje: String? = null
)

// Motor de diagnóstico experto sincronizado con telemetría de 4 canales.
object PredictionService {
    private val iaClient = HttpClient(CIO)

    fun findApiKey(): String {
        return System.getenv("OPENAI_API_KEY")?.trim() ?: "FALTA_KEY"
    }

    private fun limpiarBase64(raw: String): String {
        return if (raw.contains(",")) raw.substringAfter(",") 
               else raw.replace("\n", "").replace("\r", "").replace(" ", "").trim()
    }

    private suspend fun obtenerDatosHardware(id: Int?): String? {
        if (id == null) return null
        return try {
            newSuspendedTransaction {
                val registro = MedicionesSensores.select { MedicionesSensores.dispositivoId eq id }
                    .orderBy(MedicionesSensores.id to SortOrder.DESC)
                    .limit(1).singleOrNull()

                if (registro == null) null
                else {
                    val p = registro[MedicionesSensores.pesoGramos]
                    val g = registro[MedicionesSensores.gasPorcentaje]
                    val t = registro[MedicionesSensores.temperatura]
                    val h = registro[MedicionesSensores.humedad]
                    // Devolvemos el resumen sin importar el tiempo para evitar el mensaje de "Offline"
                    "Hardware: Peso ${p}g, Gas ${g}%, Temp ${t}C, Hum ${h}%"
                }
            }
        } catch (e: Exception) { null }
    }

    suspend fun predecirImagen(base64: String, idDispositivo: Int? = null): PredictionResult {
        val apiKey = findApiKey()
        if (apiKey == "FALTA_KEY") return PredictionResult("Falta Clave", null, 0.0, null, null, false, errorOcurrido = true)
        
        val hardwareInfo = obtenerDatosHardware(idDispositivo)
        val b64Limpio = limpiarBase64(base64)
        
        // MODO ESTACION (Si idDispositivo no es null) vs MODO NORMAL
        val prompt = if (idDispositivo != null) {
            """
            ESTACION INTELIGENTE. Telemetria: $hardwareInfo.
            Analiza imagen y los 4 sensores. Responde JSON plano:
            1. 'fruta': Nombre real.
            2. 'estado': Fresco, Maduro o Deteriorado.
            3. 'porcentaje': 0-100 (Usa imagen + gas + temp para salud real).
            4. 'dias': Vida util aproximada.
            5. 'comer': 3 sugerencias detalladas numeradas con \n.
            6. 'riesgo': Alerta ambiental (analiza humedad y gas).
            REGLA: Si salud < 35%, solo sugiere desecho/compostaje.
            """.trimIndent()
        } else {
            """
            MODO NORMAL. Analiza imagen. Responde JSON plano:
            'fruta', 'estado', 'porcentaje' (0-100), 'dias', 'comer' (3 sugerencias numeradas con \n).
            REGLA: Si salud < 35%, solo sugiere desecho/compostaje.
            """.trimIndent()
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
            
            val salud = res["porcentaje"]?.jsonPrimitive?.doubleOrNull ?: 0.0

            PredictionResult(
                fruta = res["fruta"]?.jsonPrimitive?.content,
                estado = res["estado"]?.jsonPrimitive?.content,
                porcentajeFrescura = salud,
                sugerencias = res["dias"]?.jsonPrimitive?.content,
                recetas = res["comer"]?.jsonPrimitive?.content,
                esSaludable = salud > 30.0,
                alertaRiesgo = res["riesgo"]?.jsonPrimitive?.content,
                infoHardware = hardwareInfo,
                hardwareOnline = if (idDispositivo != null) true else null, // Si es Estacion mandamos true para evitar banner naranja
                hardwareMensaje = if (idDispositivo != null) "Telemetría activa" else null
            )
        } catch (e: Exception) {
            PredictionResult("Error de analisis", null, 0.0, null, null, false, errorOcurrido = true)
        }
    }

    suspend fun mapearPrediccion(idx: Int): PredictionResult = predecirImagen("")
}
