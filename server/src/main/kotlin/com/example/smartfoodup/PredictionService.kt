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
import java.time.temporal.ChronoUnit
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
    val infoHardware: String? = null,
    val hardwareOnline: Boolean = false,
    val hardwareMensaje: String = "Buscando Raspberry..."
)

object PredictionService {
    private val iaClient = HttpClient(CIO)

    fun findApiKey(): String {
        return System.getenv("OPENAI_API_KEY")?.trim() ?: "FALTA_KEY"
    }

    private fun limpiarBase64(raw: String): String {
        return raw.substringAfter("base64,").replace("\n", "").replace("\r", "").replace(" ", "").trim()
    }

    private suspend fun obtenerEstadoHardware(id: Int?): Pair<Boolean, String> {
        if (id == null) return Pair(false, "Modo Cámara Directa")
        return try {
            newSuspendedTransaction {
                val registro = MedicionesSensores.select { MedicionesSensores.dispositivoId eq id }
                    .orderBy(MedicionesSensores.id to SortOrder.DESC)
                    .limit(1).singleOrNull()

                if (registro == null) Pair(false, "Sin datos en la nube.")
                else {
                    val fecha = registro[MedicionesSensores.fechaMedicion]
                    // Ajuste por Zona Horaria: Si la diferencia es menor a 12 horas, lo tomamos como reciente.
                    val minutos = ChronoUnit.MINUTES.between(fecha, LocalDateTime.now())
                    if (Math.abs(minutos) < 720) { // Tolerancia para cualquier zona horaria
                        val p = registro[MedicionesSensores.pesoGramos]
                        val g = registro[MedicionesSensores.gasPorcentaje]
                        Pair(true, "Conectada. Peso: ${p}g | Gas: ${g}%")
                    } else Pair(false, "Raspberry desconectada (Hace $minutos min)")
                }
            }
        } catch (e: Exception) { Pair(false, "Error de enlace.") }
    }

    suspend fun predecirImagen(base64: String, idDispositivo: Int? = null): PredictionResult {
        val apiKey = findApiKey()
        if (apiKey == "FALTA_KEY") return PredictionResult("Error Clave", null, 0.0, null, null, false, errorOcurrido = true)
        
        val (online, mensajeHardware) = obtenerEstadoHardware(idDispositivo)
        val b64Limpio = limpiarBase64(base64)
        
        // Prompt mejorado para veredicto de salud agresivo y deteccion real.
        val prompt = """
            Eres un experto en calidad alimentaria. Analiza la imagen. 
            Responde JSON plano:
            1. 'fruta': Nombre real (ej. Manzana). Pon 'NULO' solo si es algo que NO es comida.
            2. 'estado': Fresco, Maduro o Deteriorado.
            3. 'porcentaje': 0-100. SE RIGUROSO: Si ves moho o pudrición, el valor DEBE ser menor a 15.
            4. 'dias': Vida útil estimada (ej. '0 dias' si esta mal).
            5. 'comer': 3 sugerencias con \n. Si la salud es < 30, SOLO aconseja desechar.
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
            
            val content = Json.parseToJsonElement(response.bodyAsText()).jsonObject["choices"]?.jsonArray?.get(0)?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content ?: ""
            val res = Json.parseToJsonElement(content).jsonObject
            
            val fruta = res["fruta"]?.jsonPrimitive?.content ?: "NULO"
            if (fruta.contains("NULO", true)) return PredictionResult("No detectado", "N/A", 0.0, "N/A", "N/A", false, hardwareOnline = online, hardwareMensaje = mensajeHardware)

            val salud = res["porcentaje"]?.jsonPrimitive?.doubleOrNull ?: 0.0

            PredictionResult(
                fruta = fruta,
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
