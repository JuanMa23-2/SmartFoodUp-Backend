package com.example.smartfoodup

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

// Clase que se devolverá automáticamente en formato JSON
@Serializable
data class IaPredictionResponse(
    val exitoso: Boolean,
    val mensaje: String,
    val alimento: String? = null,
    val claseDetectada: String? = null,
    val esSaludable: Boolean? = null,
    val sugerencia: String? = null
)

fun Route.iaRouting() {

    route("/ia") {

        // ENDPOINT: POST /ia/predict (Recibe la imagen Multipart desde la App/Frontend)
        post("/predict") {
            try {
                val multipart = call.receiveMultipart()
                var resultadoPrediccion: PredictionResult? = null

                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        // Leemos la imagen subida en bytes
                        val imageBytes = part.streamProvider().readBytes()

                        // TODO: Aquí se conecta el modelo descargado de Drive
                        // Por ejemplo, simulamos que el modelo retorna el índice 2 (ej. Banana__Healthy)
                        val predictedIndex = 2

                        // Usamos el servicio que creaste en el Paso 3 para traducir el índice con clases.json
                        resultadoPrediccion = PredictionService.mapearPrediccion(predictedIndex)
                    }
                    part.dispose()
                }

                if (resultadoPrediccion != null) {
                    call.respond(
                        HttpStatusCode.OK,
                        IaPredictionResponse(
                            exitoso = true,
                            mensaje = "Predicción realizada con éxito",
                            alimento = resultadoPrediccion!!.alimento,
                            claseDetectada = resultadoPrediccion!!.claseDetectada,
                            esSaludable = resultadoPrediccion!!.esSaludable,
                            sugerencia = resultadoPrediccion!!.sugerencia
                        )
                    )
                } else {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        IaPredictionResponse(
                            exitoso = false,
                            mensaje = "No se recibió ninguna imagen válida en la petición"
                        )
                    )
                }

            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    IaPredictionResponse(
                        exitoso = false,
                        mensaje = "Error al procesar la imagen con el modelo: ${e.localizedMessage}"
                    )
                )
            }
        }
    }
}