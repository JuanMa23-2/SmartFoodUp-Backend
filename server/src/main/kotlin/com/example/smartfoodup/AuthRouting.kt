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

// Modulo de rutas para la gestion de autenticacion y operaciones de catalogo.
fun Route.authRouting() {

    route("/auth") {
        post("/registro") { handleRegistro(call) }
        post("/register") { handleRegistro(call) }

        post("/login") {
            val request = call.receive<LoginRequest>()
            val emailLimpio = request.email.trim()

            val response = newSuspendedTransaction {
                val usuario = Usuarios.select { Usuarios.email eq emailLimpio }.singleOrNull()
                if (usuario == null) {
                    AuthResponse(exitoso = false, mensaje = "Credenciales no validas")
                } else {
                    val passwordEnBd = usuario[Usuarios.passwordHash]
                    if (BCrypt.checkpw(request.contrasena, passwordEnBd)) {
                        AuthResponse(
                            exitoso = true,
                            mensaje = "Bienvenido al sistema",
                            nombre = usuario[Usuarios.nombre],
                            rol = usuario[Usuarios.rol]
                        )
                    } else {
                        AuthResponse(exitoso = false, mensaje = "La contrasena es incorrecta")
                    }
                }
            }
            call.respond(if (response.exitoso) HttpStatusCode.OK else HttpStatusCode.Unauthorized, response)
        }
    }

    route("/api") {
        // Obtiene la ultima medicion de los sensores para mostrarla en el dashboard.
        get("/sensores/ultimo") {
            try {
                val medicion = newSuspendedTransaction {
                    MedicionesSensores
                        .select { MedicionesSensores.dispositivoId eq 1 }
                        .orderBy(MedicionesSensores.id to org.jetbrains.exposed.sql.SortOrder.DESC)
                        .limit(1)
                        .map {
                            SensorDataRequest(
                                deviceId = it[MedicionesSensores.dispositivoId],
                                peso = it[MedicionesSensores.pesoGramos] / 1000.0,
                                humedad = it[MedicionesSensores.humedad],
                                temperatura = it[MedicionesSensores.temperatura],
                                gas = it[MedicionesSensores.gasPorcentaje]
                            )
                        }.singleOrNull()
                }
                if (medicion != null) call.respond(HttpStatusCode.OK, medicion)
                else call.respond(HttpStatusCode.NotFound, "No hay mediciones aun")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error al consultar sensores")
            }
        }

        // Procesamiento de alimentos: Analisis por IA enriquecido con hardware.
        post("/alimentos") {
            try {
                val request = call.receive<AlimentoRequest>()
                
                if (!request.imagenBytesBase64.isNullOrBlank()) {
                    // Llamada al motor de IA enviando el ID del dispositivo si existe.
                    val resultadoIa = PredictionService.predecirImagen(request.imagenBytesBase64, request.dispositivoId)
                    
                    call.respond(
                        HttpStatusCode.OK,
                        AlimentoResponse(
                            exitoso = !resultadoIa.errorOcurrido,
                            mensaje = if (resultadoIa.errorOcurrido) "Error en el procesamiento" else "Detección finalizada",
                            alimento = resultadoIa.fruta,
                            estado = resultadoIa.estado,
                            porcentajeFrescura = resultadoIa.porcentajeFrescura,
                            sugerencia = resultadoIa.sugerencias,
                            recetas = resultadoIa.recetas,
                            // Los sensores solo vendrán si idDispositivo no es null
                            datosSensores = resultadoIa.infoHardware,
                            alertaRiesgo = resultadoIa.alertaRiesgo
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
                call.respond(HttpStatusCode.Created, AlimentoResponse(exitoso = true, mensaje = "Alimento registrado"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, AlimentoResponse(exitoso = false, mensaje = "Fallo interno del servidor"))
            }
        }
    }
}

private suspend fun handleRegistro(call: ApplicationCall) {
    val request = call.receive<RegistroRequest>()
    val response = newSuspendedTransaction {
        val existe = Usuarios.select { Usuarios.email eq request.email }.singleOrNull()
        if (existe != null) {
            AuthResponse(exitoso = false, mensaje = "El correo electronico ya se encuentra registrado")
        } else {
            val passwordHasheada = BCrypt.hashpw(request.contrasena, BCrypt.gensalt())
            Usuarios.insert {
                it[nombre] = request.nombre
                it[email] = request.email
                it[passwordHash] = passwordHasheada
                it[rol] = "CLIENTE"
            }
            AuthResponse(exitoso = true, mensaje = "Usuario creado exitosamente")
        }
    }
    call.respond(if (response.exitoso) HttpStatusCode.Created else HttpStatusCode.Conflict, response)
}
