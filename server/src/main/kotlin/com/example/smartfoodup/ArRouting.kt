package com.example.smartfoodup

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

// Modulo de rutas para la gestion de analisis en tiempo real para Realidad Aumentada.
fun Route.arRouting() {

    route("/api/ar") {
        // Endpoint optimizado para el analisis de fotogramas de RA.
        post("/analisis") {
            try {
                val request = call.receive<AlimentoRequest>()
                
                if (request.imagenBytesBase64.isNullOrBlank()) {
                    call.respond(HttpStatusCode.OK, AlimentoResponse(false, "Camara en espera"))
                    return@post
                }

                val resultado = PredictionService.predecirImagen(request.imagenBytesBase64)

                // En RA somos mas estrictos: solo enviamos exitoso: true si hay datos claros.
                if (resultado.errorOcurrido || resultado.fruta == null) {
                    call.respond(
                        HttpStatusCode.OK,
                        AlimentoResponse(
                            exitoso = false,
                            mensaje = "Identificando alimento..."
                        )
                    )
                } else {
                    call.respond(
                        HttpStatusCode.OK,
                        AlimentoResponse(
                            exitoso = true,
                            mensaje = "Deteccion exitosa",
                            alimento = resultado.fruta,
                            estado = resultado.estado,
                            porcentajeFrescura = resultado.porcentajeFrescura,
                            sugerencia = resultado.sugerencias,
                            recetas = resultado.recetas
                        )
                    )
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.OK, AlimentoResponse(false, "Analizando entorno..."))
            }
        }
    }
}
