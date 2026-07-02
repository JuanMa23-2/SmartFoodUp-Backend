package com.example.smartfoodup

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.select
import org.mindrot.jbcrypt.BCrypt
import kotlinx.serialization.Serializable

// Definición local de la petición del administrador para evitar conflictos de tipado
@Serializable
data class AdminRegistroRequest(
    val nombre: String,
    val email: String,
    val contrasena: String,
    val rol: String
)

fun Route.authRouting() {
    route("/auth") {

        // 1. ENDPOINT: POST /auth/register (Público, rol CLIENTE por defecto)
        post("/register") {
            try {
                val request = call.receive<RegistroRequest>()

                if (request.nombre.isBlank() || request.email.isBlank() || request.contrasena.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        AuthResponse(exitoso = false, mensaje = "Todos los campos son obligatorios")
                    )
                    return@post
                }

                var emailYaExiste = false
                val passwordHasheada = BCrypt.hashpw(request.contrasena, BCrypt.gensalt())

                transaction {
                    val existe = Usuarios.select { Usuarios.email eq request.email }.count() > 0
                    if (existe) {
                        emailYaExiste = true
                    } else {
                        Usuarios.insert {
                            it[nombre] = request.nombre
                            it[email] = request.email
                            it[passwordHash] = passwordHasheada
                            it[rol] = "CLIENTE"
                        }
                    }
                }

                if (emailYaExiste) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        AuthResponse(exitoso = false, mensaje = "El correo electrónico ya está registrado")
                    )
                } else {
                    call.respond(
                        HttpStatusCode.Created,
                        AuthResponse(
                            exitoso = true,
                            mensaje = "¡Usuario creado exitosamente!",
                            nombre = request.nombre,
                            rol = "CLIENTE"
                        )
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    AuthResponse(exitoso = false, mensaje = "Error en el servidor: ${e.localizedMessage}")
                )
            }
        }

        // 2. ENDPOINT: POST /auth/login
        post("/login") {
            try {
                val request = call.receive<RegistroRequest>()
                val emailLimpio = request.email.trim()

                if (emailLimpio.isBlank() || request.contrasena.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        AuthResponse(exitoso = false, mensaje = "Correo y contraseña requeridos")
                    )
                    return@post
                }

                var loginExitoso = false
                var nombreEnBd: String? = null
                var rolEnBd: String? = null
                var mensajeRespuesta = "Usuario no encontrado"

                transaction {
                    val usuarioRow = Usuarios.select { Usuarios.email eq emailLimpio }.singleOrNull()

                    if (usuarioRow != null) {
                        val passwordEnBd = usuarioRow[Usuarios.passwordHash]

                        if (BCrypt.checkpw(request.contrasena, passwordEnBd)) {
                            loginExitoso = true
                            nombreEnBd = usuarioRow[Usuarios.nombre]
                            rolEnBd = usuarioRow[Usuarios.rol]
                            mensajeRespuesta = "¡Inicio de sesión exitoso!"
                        } else {
                            mensajeRespuesta = "Contraseña incorrecta"
                        }
                    }
                }

                if (loginExitoso) {
                    call.respond(
                        HttpStatusCode.OK,
                        AuthResponse(
                            exitoso = true,
                            mensaje = mensajeRespuesta,
                            nombre = nombreEnBd,
                            rol = rolEnBd
                        )
                    )
                } else {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        AuthResponse(exitoso = false, mensaje = mensajeRespuesta)
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    AuthResponse(exitoso = false, mensaje = "Error en el servidor: ${e.localizedMessage}")
                )
            }
        }

        // 3. ENDPOINT EXCLUSIVO: POST /auth/admin-register (El administrador decide el Rol)
        post("/admin-register") {
            try {
                val request = call.receive<AdminRegistroRequest>()

                if (request.nombre.isBlank() || request.email.isBlank() || request.contrasena.isBlank() || request.rol.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        AuthResponse(exitoso = false, mensaje = "Todos los campos son obligatorios")
                    )
                    return@post
                }

                var emailYaExiste = false
                val passwordHasheada = BCrypt.hashpw(request.contrasena, BCrypt.gensalt())

                transaction {
                    val existe = Usuarios.select { Usuarios.email eq request.email }.count() > 0
                    if (existe) {
                        emailYaExiste = true
                    } else {
                        Usuarios.insert {
                            it[nombre] = request.nombre
                            it[email] = request.email
                            it[passwordHash] = passwordHasheada
                            it[rol] = request.rol // Guarda dinámicamente ADMIN o CLIENTE según la petición
                        }
                    }
                }

                if (emailYaExiste) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        AuthResponse(exitoso = false, mensaje = "El correo electrónico ya está registrado")
                    )
                } else {
                    call.respond(
                        HttpStatusCode.Created,
                        AuthResponse(
                            exitoso = true,
                            mensaje = "¡Usuario institucional creado exitosamente!",
                            nombre = request.nombre,
                            rol = request.rol
                        )
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    AuthResponse(exitoso = false, mensaje = "Error en el servidor: ${e.localizedMessage}")
                )
            }
        }
    }
}