package com.example.smartfoodup

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
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
                val multipart = call.receiveMultipart()
                var resultadoPrediccion: PredictionResult? = null

                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        // Simulamos que el modelo retorna un índice para mantener tu flujo
                        val predictedIndex = (0..35).random()

                        // NOTA: No podemos llamar a mapearPrediccion (suspend) directamente aquí si no estamos en un coroutine scope
                        // Pero el bloque post { } de Ktor YA es un coroutine scope.
                    }
                    part.dispose()
                }
                
                // Para que el flujo sea real, necesitamos ejecutar la predicción.
                // Re-inicializamos para prueba
                val predictedIndex = (0..35).random()
                resultadoPrediccion = PredictionService.mapearPrediccion(predictedIndex)

                if (resultadoPrediccion != null) {
                    call.respond(
                        HttpStatusCode.OK,
                        IaPredictionResponse(
                            exitoso = true,
                            mensaje = "Predicción realizada",
                            alimento = resultadoPrediccion!!.fruta,
                            claseDetectada = resultadoPrediccion!!.claseDetectada,
                            esSaludable = resultadoPrediccion!!.esSaludable,
                            sugerencia = resultadoPrediccion!!.sugerencias,
                            porcentajeFrescura = resultadoPrediccion!!.porcentajeFrescura
                        )
                    )
                } else {
                    call.respond(HttpStatusCode.BadRequest, IaPredictionResponse(false, "Imagen no válida"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, IaPredictionResponse(false, e.message ?: "Error"))
            }
        }
    }
}
