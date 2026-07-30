package com.example.smartfoodup

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

// Modulo de rutas especifico para el analisis en tiempo real orientado a Realidad Aumentada.
fun Route.arRouting() {

    route("/api/ar") {
        // Analisis continuo de fotogramas para superposicion de informacion visual.
        post("/analisis") {
            try {
                val request = call.receive<AlimentoRequest>()
                
                if (request.imagenBytesBase64.isNullOrBlank()) {
                    call.respond(HttpStatusCode.OK, AlimentoResponse(false, "Flujo de camara inactivo"))
                    return@post
                }

                // Ejecucion del motor de prediccion hibrido.
                val resultado = PredictionService.predecirImagen(request.imagenBytesBase64)

                // La respuesta se marca como no exitosa si la IA no identifica un objeto claro para evitar ruido en pantalla.
                if (resultado.errorOcurrido || resultado.fruta == null) {
                    call.respond(
                        HttpStatusCode.OK,
                        AlimentoResponse(
                            exitoso = false,
                            mensaje = "Identificando..."
                        )
                    )
                } else {
                    // Retorno de datos procesados para el renderizado de la etiqueta de RA.
                    call.respond(
                        HttpStatusCode.OK,
                        AlimentoResponse(
                            exitoso = true,
                            mensaje = "Deteccion activa",
                            alimento = resultado.fruta,
                            estado = resultado.estado,
                            porcentajeFrescura = resultado.porcentajeFrescura,
                            sugerencia = resultado.sugerencias,
                            recetas = resultado.recetas
                        )
                    )
                }
            } catch (e: Exception) {
                // Silenciamos errores tecnicos menores para mantener la estabilidad del visor.
                call.respond(HttpStatusCode.OK, AlimentoResponse(false, "Procesando..."))
            }
        }
    }
}
