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

        // Endpoint: POST https://.../auth/register
        post("/register") {
            try {
                // 1. Recibe el JSON enviado por el celular y lo transforma al objeto Kotlin
                val request = call.receive<RegistroRequest>()

                // Validación básica de campos vacíos
                if (request.nombre.isBlank() || request.email.isBlank() || request.password.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, AuthResponse(false, "Todos los campos son obligatorios"))
                    return@post
                }

                var usuarioIdGenerado: Int? = null
                var emailYaExiste = false

                // 2. Operación segura en la Base de Datos mediante Exposed ORM
                transaction {
                    // Verificamos si el email ya está registrado en la tabla Usuarios
                    val existe = Usuarios.select { Usuarios.email eq request.email }.count() > 0
                    if (existe) {
                        emailYaExiste = true
                    } else {
                        // Insertamos el nuevo registro en MySQL usando tu objeto Usuarios
                        val insertStatement = Usuarios.insert {
                            it[nombre] = request.nombre
                            it[email] = request.email
                            // Guardamos la contraseña (recuerda que en el futuro le pondremos hash)
                            it[passwordHash] = request.password
                        }
                        usuarioIdGenerado = insertStatement[Usuarios.id]
                    }
                }

                // 3. Responder al Frontend según el resultado de la transacción
                if (emailYaExiste) {
                    call.respond(HttpStatusCode.Conflict, AuthResponse(false, "El correo electrónico ya está registrado"))
                } else {
                    call.respond(HttpStatusCode.Created, AuthResponse(true, "¡Usuario creado exitosamente!", usuarioIdGenerado))
                }

            } catch (e: Exception) {
                // Manejo de errores en caso de fallas de red o de parseo JSON
                call.respond(HttpStatusCode.InternalServerError, AuthResponse(false, "Error en el servidor: ${e.localizedMessage}"))
            }
        }
    }
}