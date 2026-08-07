package com.example.smartfoodup

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

// Módulo de Realidad Aumentada con soporte para sensores externos.
fun Route.arRouting() {

    route("/api/ar") {
        post("/analisis") {
            try {
                val request = call.receive<AlimentoRequest>()
                
                if (request.imagenBytesBase64.isNullOrBlank()) {
                    call.respond(HttpStatusCode.OK, AlimentoResponse(false, "Flujo inactivo"))
                    return@post
                }

                // Si request.dispositivoId es null, no buscara sensores (RA Normal)
                // Si es 1 (o el ID enviado), activara la Estacion Inteligente en RA
                val resultado = PredictionService.predecirImagen(request.imagenBytesBase64, request.dispositivoId)

                if (resultado.errorOcurrido || resultado.fruta == "No detectado") {
                    call.respond(HttpStatusCode.OK, AlimentoResponse(false, "Identificando..."))
                } else {
                    call.respond(
                        HttpStatusCode.OK,
                        AlimentoResponse(
                            exitoso = true,
                            mensaje = "Detección activa",
                            alimento = resultado.fruta,
                            estado = resultado.estado,
                            porcentajeFrescura = resultado.porcentajeFrescura,
                            sugerencia = resultado.sugerencias,
                            recetas = resultado.recetas,
                            // Datos que solo se llenan en modo Estacion Inteligente
                            datosSensores = resultado.infoHardware,
                            alertaRiesgo = resultado.alertaRiesgo
                        )
                    )
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.OK, AlimentoResponse(false, "Procesando..."))
            }
        }
    }
}
