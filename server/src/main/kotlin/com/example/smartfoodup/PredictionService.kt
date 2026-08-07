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

// Motor de diagnóstico experto con lógica de seguridad alimentaria.
object PredictionService {
    private val iaClient = HttpClient(CIO)

    fun findApiKey(): String {
        return System.getenv("OPENAI_API_KEY")?.trim() ?: "FALTA_KEY"
    }

    private fun extraerTexto(element: JsonElement?): String {
        return when (element) {
            is JsonPrimitive -> element.content
            is JsonArray -> element.joinToString("\n") { if (it is JsonPrimitive) it.content else it.toString() }
            else -> element?.toString() ?: "N/A"
        }
    }

    private suspend fun obtenerEstadoHardware(id: Int?): Pair<Boolean, String> {
        if (id == null) return Pair(false, "Modo Cámara Directa")
        return try {
            newSuspendedTransaction {
                val registro = MedicionesSensores.select { MedicionesSensores.dispositivoId eq id }
                    .orderBy(MedicionesSensores.id to SortOrder.DESC)
                    .limit(1).singleOrNull()

                if (registro == null) Pair(false, "Estación no detectada.")
                else {
                    val fecha = registro[MedicionesSensores.fechaMedicion]
                    val diff = Duration.between(fecha, LocalDateTime.now()).seconds
                    if (diff < 60) {
                        val p = registro[MedicionesSensores.pesoGramos]
                        val g = registro[MedicionesSensores.gasPorcentaje]
                        Pair(true, "Hardware OK. Peso: ${p}g | Gas: ${g}%")
                    } else Pair(false, "Estación Offline.")
                }
            }
        } catch (e: Exception) { Pair(false, "Error de enlace.") }
    }

    suspend fun predecirImagen(base64: String, idDispositivo: Int? = null): PredictionResult {
        val apiKey = findApiKey()
        if (apiKey == "FALTA_KEY") return PredictionResult("Error Clave", null, 0.0, null, null, false, errorOcurrido = true)
        
        val (online, mensajeHardware) = obtenerEstadoHardware(idDispositivo)
        
        // Limpiador de Base64 ultra-robusto para evitar errores de galeria.
        val cleanB64 = base64.substringAfter("base64,").replace("\n", "").replace("\r", "").replace(" ", "").trim()
        
        val prompt = """
            Eres un experto en seguridad alimentaria. Analiza la imagen [y estos datos: $mensajeHardware].
            Responde estrictamente en JSON plano:
            1. 'fruta': nombre en español (o 'NULO' si no hay comida).
            2. 'estado': Fresco, Maduro, Muy Maduro o Deteriorado.
            3. 'porcentaje': 0 a 100. SE RIGUROSO: Si hay moho, pudrición o deterioro visible, el valor DEBE ser entre 0 y 15.
            4. 'dias': Vida útil. Si está deteriorado pon '0 días'.
            5. 'comer': 3 sugerencias con \n. REGLA: Si porcentaje < 30, SOLO sugerir desecho/compostaje.
            6. 'riesgo': (Solo si hay hardware) veredicto ambiental.
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
                                    put("image_url", buildJsonObject { put("url", "data:image/jpeg;base64,$cleanB64") })
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
            println("Error IA: ${e.message}")
            PredictionResult("Error de analisis", null, 0.0, null, null, false, errorOcurrido = true, mensajeError = e.message)
        }
    }

    suspend fun mapearPrediccion(idx: Int): PredictionResult = predecirImagen("")
}
