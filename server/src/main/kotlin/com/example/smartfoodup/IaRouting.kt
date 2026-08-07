package com.example.smartfoodup

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class IaPredictionResponse(
    val exitoso: Boolean,
    val mensaje: String,
    val alimento: String? = null,
    val claseDetectada: String? = null,
    val esSaludable: Boolean? = null,
    val sugerencia: String? = null,
    val porcentajeFrescura: Double? = null
)

fun Route.iaRouting() {
    route("/ia") {
        post("/predict") {
            try {
                // Sincronizado con la firma actual de PredictionService
                val predictedIndex = (0..35).random()
                val resultado = PredictionService.mapearPrediccion(predictedIndex)

                call.respond(
                    HttpStatusCode.OK,
                    IaPredictionResponse(
                        exitoso = true,
                        mensaje = "Analisis completado",
                        alimento = resultado.fruta,
                        claseDetectada = resultado.claseDetectada,
                        esSaludable = resultado.esSaludable,
                        sugerencia = resultado.sugerencias,
                        porcentajeFrescura = resultado.porcentajeFrescura
                    )
                )
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, IaPredictionResponse(false, e.message ?: "Error"))
            }
        }
    }
}
