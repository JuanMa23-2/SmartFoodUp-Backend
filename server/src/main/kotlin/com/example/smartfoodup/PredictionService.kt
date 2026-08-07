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
    val alertaRiesgo: String? = null,
    val infoHardware: String? = null,
    val hardwareOnline: Boolean = false,
    val hardwareMensaje: String = "Esperando conexion de la Raspberry..."
)

// Motor de diagnostico avanzado con validacion de pulso de hardware.
object PredictionService {
    private val iaClient = HttpClient(CIO)

    fun findApiKey(): String {
        return System.getenv("OPENAI_API_KEY") ?: "FALTA_KEY"
    }

    // Verifica si la Raspberry ha mandado datos en los ultimos 60 segundos.
    private suspend fun obtenerEstadoHardware(id: Int?): Pair<Boolean, String> {
        if (id == null) return Pair(false, "Modo Manual (Sin sensores)")
        return try {
            newSuspendedTransaction {
                val registro = MedicionesSensores.select { MedicionesSensores.dispositivoId eq id }
                    .orderBy(MedicionesSensores.id to SortOrder.DESC)
                    .limit(1)
                    .singleOrNull()

                if (registro == null) {
                    Pair(false, "Estacion no vinculada. Encienda la Raspberry.")
                } else {
                    val fecha = registro[MedicionesSensores.fechaMedicion]
                    val segundosTranscurridos = Duration.between(fecha, LocalDateTime.now()).seconds
                    
                    if (segundosTranscurridos < 60) {
                        val peso = registro[MedicionesSensores.pesoGramos]
                        val gas = registro[MedicionesSensores.gasPorcentaje]
                        val temp = registro[MedicionesSensores.temperatura]
                        val hum = registro[MedicionesSensores.humedad]
                        Pair(true, "Estacion Online. Peso: ${peso}g | Gas: ${gas}% | Temp: ${temp}C | Hum: ${hum}%")
                    } else {
                        Pair(false, "Estacion fuera de linea. Ultima conexion: $segundosTranscurridos seg. atras.")
                    }
                }
            }
        } catch (e: Exception) { Pair(false, "Error al consultar hardware.") }
    }

    suspend fun predecirImagen(base64: String, idDispositivo: Int? = null): PredictionResult {
        val apiKey = findApiKey()
        if (apiKey == "FALTA_KEY") return PredictionResult("Falta Clave", null, 0.0, "N/A", null, false, errorOcurrido = true)
        
        val (online, mensajeHardware) = obtenerEstadoHardware(idDispositivo)
        
        // Si el usuario presiono el boton de estacion pero la Raspberry no esta conectada:
        if (idDispositivo != null && !online) {
            return PredictionResult(
                fruta = "Estacion desconectada",
                estado = "N/A",
                porcentajeFrescura = 0.0,
                sugerencias = "N/A",
                recetas = "N/A",
                esSaludable = false,
                hardwareOnline = false,
                hardwareMensaje = mensajeHardware
            )
        }

        val cleanB64 = base64.substringAfter(",").replace("\n", "").replace("\r", "").replace(" ", "")
        
        val prompt = if (online) {
            """
            MODO ESTACION INTELIGENTE. Datos: $mensajeHardware.
            Analiza imagen y sensores. Responde JSON: 'fruta', 'estado', 'porcentaje' (0-100), 'dias', 'comer', 'riesgo'.
            REGLA DE SEGURIDAD: Si el porcentaje de salud es menor a 40, NO des recetas de comida, solo indica como desechar o compostar el producto.
            """.trimIndent()
        } else {
            "Analiza el alimento visualmente. Responde JSON plano con: fruta, estado, porcentaje (0-100), dias, comer (3 sugerencias numeradas con \\n)."
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
            
            val frutaDetectada = res["fruta"]?.jsonPrimitive?.content ?: "No detectado"
            
            if (frutaDetectada.contains("NULO", true) || frutaDetectada.contains("No detectado", true)) {
                return PredictionResult("No se detecto alimento", "N/A", 0.0, "N/A", "N/A", false, hardwareOnline = online, hardwareMensaje = mensajeHardware)
            }

            PredictionResult(
                fruta = frutaDetectada,
                estado = res["estado"]?.jsonPrimitive?.content,
                porcentajeFrescura = res["porcentaje"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                sugerencias = res["dias"]?.jsonPrimitive?.content,
                recetas = res["comer"]?.jsonPrimitive?.content,
                esSaludable = (res["porcentaje"]?.jsonPrimitive?.doubleOrNull ?: 0.0) > 40.0,
                alertaRiesgo = res["riesgo"]?.jsonPrimitive?.content,
                infoHardware = if (online) mensajeHardware else null,
                hardwareOnline = online,
                hardwareMensaje = mensajeHardware
            )
        } catch (e: Exception) {
            PredictionResult("Error de procesamiento", null, 0.0, null, null, false, errorOcurrido = true)
        }
    }

    suspend fun mapearPrediccion(idx: Int): PredictionResult = predecirImagen("")
}
