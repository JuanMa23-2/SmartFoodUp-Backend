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
        if (apiKey == "FALTA_KEY") {
            return PredictionResult(
                fruta = "Error: Falta Clave",
                estado = null,
                porcentajeFrescura = 0.0,
                sugerencias = "N/A",
                recetas = null,
                esSaludable = false,
                errorOcurrido = true
            )
        }
        
        val contextoHardware = obtenerContextoHardware(idDispositivo ?: 1)
        val cleanB64 = base64.substringAfter(",").replace("\n", "").replace("\r", "").replace(" ", "")
        
        return predecirConOpenAITotal(cleanB64, apiKey, contextoHardware)
    }

    private suspend fun predecirConOpenAITotal(cleanB64: String, key: String, hardware: String): PredictionResult {
        val prompt = """
            Analiza el alimento. Datos de sensores: $hardware.
            Responde JSON: 'fruta', 'estado' (Fresco/Maduro/Deteriorado), 'porcentaje', 'dias', 'comer' (3 sugerencias numeradas con \n), 'riesgo', 'inventario'.
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
                claseDetectada = "OPENAI_VISION",
                alertaRiesgo = res["riesgo"]?.jsonPrimitive?.content,
                infoHardware = hardware
            )
        } catch (e: Exception) { 
            PredictionResult(
                fruta = "Error Vision",
                estado = "N/A",
                porcentajeFrescura = 0.0,
                sugerencias = "N/A",
                recetas = "N/A",
                esSaludable = false,
                errorOcurrido = true
            )
        }
    }

    suspend fun mapearPrediccion(idx: Int): PredictionResult {
        // Mantenemos esta funcion para compatibilidad con IaRouting.kt
        val raw = classNames.getOrElse(idx) { "Apple__Healthy" }
        val partes = raw.split("__")
        val nombre = traducciones[partes[0]] ?: partes[0]
        val esSaludable = !raw.contains("Rotten", true)
        
        return PredictionResult(
            fruta = nombre,
            estado = if(esSaludable) "Fresco" else "Deteriorado",
            porcentajeFrescura = if(esSaludable) 95.0 else 20.0,
            sugerencias = "Analisis por catalogo local.",
            recetas = "Consumir segun preferencia.",
            esSaludable = esSaludable,
            claseDetectada = raw
        )
    }

    private val classNames = listOf("Apple__Healthy", "Apple__Rotten", "Banana__Healthy", "Banana__Rotten", "Bellpepper__Healthy", "Bellpepper__Rotten", "Carrot__Healthy", "Carrot__Rotten", "Cucumber__Healthy", "Cucumber__Rotten", "Grape__Healthy", "Grape__Rotten", "Guava__Healthy", "Guava__Rotten", "Jujube__Healthy", "Jujube__Rotten", "Lemon__Healthy", "Lemon__Rotten", "Lulo__Healthy", "Lulo__Rotten", "Mango__Healthy", "Mango__Rotten", "Okra__Healty", "Okra__Rotten", "Orange__Healthy", "Orange__Rotten", "Pomegranate__Healthy", "Pomegranate__Rotten", "Potato__Healthy", "Potato__Rotten", "Strawberry__Healthy", "Strawberry__Rotten", "Tamarillo__Healthy", "Tamarillo__Rotten", "Tomato__Healthy", "Tomato__Rotten")
    private val traducciones = mapOf("Apple" to "Manzana", "Banana" to "Platano", "Bellpepper" to "Pimiento", "Carrot" to "Zanahoria", "Cucumber" to "Pepino", "Grape" to "Uva", "Guava" to "Guayaba", "Jujube" to "Azufaifa", "Lemon" to "Limon", "Lulo" to "Lulo", "Mango" to "Mango", "Okra" to "Okra", "Orange" to "Naranja", "Pomegranate" to "Granada", "Potato" to "Papa", "Strawberry" to "Fresa", "Tamarillo" to "Tomate de arbol", "Tomato" to "Tomate")
}
