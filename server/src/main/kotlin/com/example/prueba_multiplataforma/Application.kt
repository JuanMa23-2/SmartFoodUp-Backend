package com.example.prueba_multiplataforma

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

// Estructura de datos para recibir la telemetría del circuito (Raspberry Pi Pico)
@Serializable
data class TelemetriaSensor(
    val dispositivoToken: String,
    val peso: Double,
    val temperatura: Double,
    val humedad: Double,
    val gasPorcentaje: Int,
    val estadoSemaforo: String
)

@Serializable
data class RespuestaServidor(val success: Boolean, val message: String)

fun main() {
    // 🛢️ CONFIGURACIÓN DE CONEXIÓN A LA BASE DE DATOS
    // En local usará los valores por defecto (puedes cambiar 'root' y 'tu_contraseña' según tu PC).
    // En Railway, leerá automáticamente las variables de entorno sin modificar el código.
    val dbUrl = System.getenv("JDBC_DATABASE_URL") ?: "jdbc:mysql://localhost:3306/smartfood_db?useSSL=false&allowPublicKeyRetrieval=true"
    val dbUser = System.getenv("DB_USER") ?: "root"
    val dbPassword = System.getenv("DB_PASSWORD") ?: "tu_contraseña"

    try {
        Database.connect(
            url = dbUrl,
            driver = "com.mysql.cj.jdbc.Driver",
            user = dbUser,
            password = dbPassword
        )

        // Ejecuta la creación automática de las 5 tablas de SmartFood Up si no existen en MySQL
        transaction {
            SchemaUtils.create(
                UsuariosTable,
                DispositivosTable,
                MedicionesTable,
                AnalisisIaTable,
                RecomendacionesTable
            )
        }
        println("[DATABASE] Conexión y sincronización de las 5 tablas completada con éxito.")
    } catch (e: Exception) {
        println("[DATABASE ERROR] No se pudo conectar a MySQL: ${e.message}")
        println("[DATABASE INFO] Recuerda crear la base de datos 'smartfood_db' en tu gestor local.")
    }

    // 🌐 ARRANQUE DEL SERVIDOR KTOR
    // Railway asigna el puerto dinámicamente con la variable PORT, en local usará el 8080.
    val port = System.getenv("PORT")?.toInt() ?: 8080

    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        // Habilitar conversión automática de JSON
        install(ContentNegotiation) {
            json()
        }

        // Configuración de las rutas de la API
        routing {
            // Test básico para el navegador
            get("/") {
                call.respondText("Servidor backend de SmartFood Up listo y conectado a la BD.")
            }

            // Endpoint para la Raspberry Pi Pico
            post("/api/telemetria") {
                try {
                    val datos = call.receive<TelemetriaSensor>()

                    // Esto imprimirá en la consola interna de Android Studio al recibir datos
                    println("\n[IoT Hub] Datos recibidos del contenedor: ${datos.dispositivoToken}")
                    println("Peso: ${datos.peso}kg | Clima: ${datos.temperatura}°C / ${datos.humedad}% | Gas: ${datos.gasPorcentaje}% | Semáforo: ${datos.estadoSemaforo}")

                    // TODO: En el siguiente paso del Integrante 1, insertaremos aquí los datos en la tabla 'MedicionesTable'

                    call.respond(HttpStatusCode.Created, RespuestaServidor(true, "Datos recibidos con éxito."))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, RespuestaServidor(false, "Error: ${e.localizedMessage}"))
                }
            }
        }
    }.start(wait = true)
}