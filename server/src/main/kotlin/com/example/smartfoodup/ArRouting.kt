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
                    call.respond(HttpStatusCode.OK, AlimentoResponse(false, "Camara en espera..."))
                    return@post
                }

                // Se ejecuta el proceso de prediccion.
                val resultado = PredictionService.predecirImagen(request.imagenBytesBase64)

                // Si ocurrio un error interno o la IA no devolvio datos validos, se marca como no exitoso.
                if (resultado.errorOcurrido || resultado.fruta == null) {
                    call.respond(
                        HttpStatusCode.OK,
                        AlimentoResponse(
                            exitoso = false,
                            mensaje = "No se detecto un alimento claro. Intente mejorar la iluminacion."
                        )
                    )
                } else {
                    // Respuesta exitosa: El Frontend renderizara la tarjeta de RA con los datos reales.
                    call.respond(
                        HttpStatusCode.OK,
                        AlimentoResponse(
                            exitoso = true,
                            mensaje = "Alimento identificado",
                            alimento = resultado.fruta,
                            estado = resultado.estado,
                            porcentajeFrescura = resultado.porcentajeFrescura,
                            sugerencia = resultado.sugerencias,
                            recetas = resultado.recetas
                        )
                    )
                }
            } catch (e: Exception) {
                // Manejo de fallos criticos en el flujo de datos.
                call.respond(HttpStatusCode.OK, AlimentoResponse(false, "Procesando flujo de video..."))
            }
        }
    }
}
