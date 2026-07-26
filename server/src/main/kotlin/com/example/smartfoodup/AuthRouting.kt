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

fun Route.authRouting() {

    route("/auth") {
        post("/registro") {
            val request = call.receive<RegistroRequest>()
            
            val result = newSuspendedTransaction {
                val existe = Usuarios.select { Usuarios.email eq request.email }.singleOrNull()
                if (existe != null) {
                    "Existe"
                } else {
                    val passwordHasheada = BCrypt.hashpw(request.contrasena, BCrypt.gensalt())
                    Usuarios.insert {
                        it[nombre] = request.nombre
                        it[email] = request.email
                        it[passwordHash] = passwordHasheada
                        it[rol] = "CLIENTE"
                    }
                    "Ok"
                }
            }

            when (result) {
                "Existe" -> call.respond(HttpStatusCode.Conflict, AuthResponse(exitoso = false, mensaje = "El correo ya está registrado"))
                "Ok" -> call.respond(HttpStatusCode.Created, AuthResponse(exitoso = true, mensaje = "Usuario registrado"))
                else -> call.respond(HttpStatusCode.InternalServerError, AuthResponse(exitoso = false, mensaje = "Error desconocido"))
            }
        }

        post("/login") {
            val request = call.receive<LoginRequest>()
            val emailLimpio = request.email.trim()

            val response = newSuspendedTransaction {
                val usuario = Usuarios.select { Usuarios.email eq emailLimpio }.singleOrNull()
                if (usuario == null) {
                    AuthResponse(exitoso = false, mensaje = "Usuario no encontrado")
                } else {
                    val passwordEnBd = usuario[Usuarios.passwordHash]
                    if (BCrypt.checkpw(request.contrasena, passwordEnBd)) {
                        AuthResponse(
                            exitoso = true,
                            mensaje = "Bienvenido",
                            nombre = usuario[Usuarios.nombre],
                            rol = usuario[Usuarios.rol]
                        )
                    } else {
                        AuthResponse(exitoso = false, mensaje = "Contraseña incorrecta")
                    }
                }
            }
            call.respond(if (response.exitoso) HttpStatusCode.OK else HttpStatusCode.Unauthorized, response)
        }
    }

    route("/api") {
        post("/alimentos") {
            try {
                val request = call.receive<AlimentoRequest>()
                
                if (!request.imagenBytesBase64.isNullOrBlank()) {
                    val resultadoIa = PredictionService.predecirImagen(request.imagenBytesBase64)
                    call.respond(
                        HttpStatusCode.OK,
                        AlimentoResponse(
                            exitoso = resultadoIa.fruta != "Error",
                            mensaje = if (resultadoIa.fruta == "Error") resultadoIa.sugerencias ?: "Error IA" else "Detección: ${resultadoIa.fruta}",
                            alimento = resultadoIa.fruta,
                            estado = resultadoIa.estado,
                            porcentajeFrescura = resultadoIa.porcentajeFrescura,
                            sugerencia = resultadoIa.sugerencias
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
                call.respond(HttpStatusCode.Created, AlimentoResponse(exitoso = true, mensaje = "Registrado"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, AlimentoResponse(exitoso = false, mensaje = e.message ?: "Error"))
            }
        }
    }
}
