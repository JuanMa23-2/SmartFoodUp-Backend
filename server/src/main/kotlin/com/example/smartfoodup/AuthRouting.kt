package com.example.smartfoodup

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.select

fun Route.authRouting() {
    route("/auth") {

        // 1. ENDPOINT: POST /auth/register
        post("/register") {
            try {
                val request = call.receive<RegistroRequest>()
                if (request.nombre.isBlank() || request.email.isBlank() || request.password.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, AuthResponse(false, "Todos los campos son obligatorios"))
                    return@post
                }

                var usuarioIdGenerado: Int? = null
                var emailYaExiste = false

                transaction {
                    val existe = Usuarios.select { Usuarios.email eq request.email }.count() > 0
                    if (existe) {
                        emailYaExiste = true
                    } else {
                        val insertStatement = Usuarios.insert {
                            it[nombre] = request.nombre
                            it[email] = request.email
                            it[passwordHash] = request.password
                        }
                        usuarioIdGenerado = insertStatement[Usuarios.id]
                    }
                }

                if (emailYaExiste) {
                    call.respond(HttpStatusCode.Conflict, AuthResponse(false, "El correo electrónico ya está registrado"))
                } else {
                    call.respond(HttpStatusCode.Created, AuthResponse(true, "¡Usuario creado exitosamente!", usuarioIdGenerado))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, AuthResponse(false, "Error en el servidor: ${e.localizedMessage}"))
            }
        }

        // ENDPOINT: POST /auth/login
        post("/login") {
            try {
                // El login solo necesita email y password, reutilizamos el mismo mapeo (ignora el nombre)
                val request = call.receive<RegistroRequest>()

                if (request.email.isBlank() || request.password.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, AuthResponse(false, "Correo y contraseña requeridos"))
                    return@post
                }

                var loginExitoso = false
                var usuarioIdEncontrado: Int? = null
                var mensajeRespuesta = "Usuario no encontrado"

                transaction {
                    // Buscamos el usuario por su email
                    val usuarioRow = Usuarios.select { Usuarios.email eq request.email }.singleOrNull()

                    if (usuarioRow != null) {
                        val passwordEnBd = usuarioRow[Usuarios.passwordHash]
                        // Comparamos contraseñas (por ahora texto plano)
                        if (passwordEnBd == request.password) {
                            loginExitoso = true
                            usuarioIdEncontrado = usuarioRow[Usuarios.id]
                            mensajeRespuesta = "¡Inicio de sesión exitoso!"
                        } else {
                            mensajeRespuesta = "Contraseña incorrecta"
                        }
                    }
                }

                if (loginExitoso) {
                    call.respond(HttpStatusCode.OK, AuthResponse(true, mensajeRespuesta, usuarioIdEncontrado))
                } else {
                    call.respond(HttpStatusCode.Unauthorized, AuthResponse(false, mensajeRespuesta))
                }

            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, AuthResponse(false, "Error en el servidor: ${e.localizedMessage}"))
            }
        }
    }
}