package com.example.smartfoodup

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.mindrot.jbcrypt.BCrypt

// Modulo de rutas para autenticacion y gestion de alimentos.
fun Route.authRouting() {

    route("/auth") {
        // Soporte multilingue para compatibilidad con el frontend.
        post("/registro") { handleRegistro(call) }
        post("/register") { handleRegistro(call) }

        // Logica de inicio de sesion y validacion de credenciales de usuario.
        post("/login") {
            val request = call.receive<LoginRequest>()
            val emailLimpio = request.email.trim()

            val response = newSuspendedTransaction {
                val usuario = Usuarios.select { Usuarios.email eq emailLimpio }.singleOrNull()
                if (usuario == null) {
                    AuthResponse(exitoso = false, mensaje = "Credenciales invalidas")
                } else {
                    val passwordEnBd = usuario[Usuarios.passwordHash]
                    if (BCrypt.checkpw(request.contrasena, passwordEnBd)) {
                        AuthResponse(
                            exitoso = true,
                            mensaje = "Autenticacion exitosa",
                            nombre = usuario[Usuarios.nombre],
                            rol = usuario[Usuarios.rol]
                        )
                    } else {
                        AuthResponse(exitoso = false, mensaje = "Contrasena incorrecta")
                    }
                }
            }
            call.respond(if (response.exitoso) HttpStatusCode.OK else HttpStatusCode.Unauthorized, response)
        }
    }

    route("/api") {
        // Endpoint para procesamiento de alimentos mediante inteligencia artificial o registro manual.
        post("/alimentos") {
            try {
                val request = call.receive<AlimentoRequest>()
                
                if (!request.imagenBytesBase64.isNullOrBlank()) {
                    val resultadoIa = PredictionService.predecirImagen(request.imagenBytesBase64)
                    call.respond(
                        HttpStatusCode.OK,
                        AlimentoResponse(
                            exitoso = resultadoIa.fruta != "Error",
                            mensaje = if (resultadoIa.fruta == "Error") "Error en el analisis" else "Analisis finalizado",
                            alimento = resultadoIa.fruta,
                            estado = resultadoIa.estado,
                            porcentajeFrescura = resultadoIa.porcentajeFrescura,
                            sugerencia = resultadoIa.sugerencias,
                            recetas = resultadoIa.recetas
                        )
                    )
                    return@post
                }

                newSuspendedTransaction {
                    AlimentosLocales.insert {
                        it[nombre] = request.nombre
                        it[categoria] = request.categoria
                        it[cantidad] = request.cantidad
                        it[imagenBase64] = request.imagenBytesBase64
                    }
                }
                call.respond(HttpStatusCode.Created, AlimentoResponse(exitoso = true, mensaje = "Registro completado"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, AlimentoResponse(exitoso = false, mensaje = "Fallo interno del servidor"))
            }
        }
    }
}

// Procesamiento de registro de nuevos usuarios con cifrado de contraseñas.
private suspend fun handleRegistro(call: ApplicationCall) {
    val request = call.receive<RegistroRequest>()
    val response = newSuspendedTransaction {
        val existe = Usuarios.select { Usuarios.email eq request.email }.singleOrNull()
        if (existe != null) {
            AuthResponse(exitoso = false, mensaje = "El usuario ya existe en el sistema")
        } else {
            val passwordHasheada = BCrypt.hashpw(request.contrasena, BCrypt.gensalt())
            Usuarios.insert {
                it[nombre] = request.nombre
                it[email] = request.email
                it[passwordHash] = passwordHasheada
                it[rol] = "CLIENTE"
            }
            AuthResponse(exitoso = true, mensaje = "Cuenta creada correctamente")
        }
    }
    call.respond(if (response.exitoso) HttpStatusCode.Created else HttpStatusCode.Conflict, response)
}
