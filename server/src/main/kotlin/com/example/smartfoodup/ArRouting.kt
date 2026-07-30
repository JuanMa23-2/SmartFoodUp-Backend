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
        // Recibe la imagen en Base64 y devuelve el diagnostico detallado para superposicion en pantalla.
        post("/analisis") {
            try {
                val request = call.receive<AlimentoRequest>()
                
                if (request.imagenBytesBase64.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, AlimentoResponse(false, "No se recibio flujo de video"))
                    return@post
                }

                // Se utiliza el motor de prediccion hibrido para procesar el fotograma.
                val resultado = PredictionService.predecirImagen(request.imagenBytesBase64)

                // Se retorna la estructura completa para que el frontend renderice la etiqueta de RA.
                call.respond(
                    HttpStatusCode.OK,
                    AlimentoResponse(
                        exitoso = resultado.fruta != "Error",
                        mensaje = "Analisis de RA completado",
                        alimento = resultado.fruta,
                        estado = resultado.estado,
                        porcentajeFrescura = resultado.porcentajeFrescura,
                        sugerencia = resultado.sugerencias,
                        recetas = resultado.recetas
                    )
                )
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, AlimentoResponse(false, "Error en el flujo de RA"))
            }
        }
    }
}
