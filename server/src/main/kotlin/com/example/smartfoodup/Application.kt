package com.example.smartfoodup

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

// ==========================================
// 1. ARRANQUE DEL SERVIDOR (Fuerza Bruta de Puerto Dinámico)
// ==========================================
fun main() {
    // Leemos el puerto de Railway obligatoriamente. Si localmente no existe, usa el 8080.
    val puertoString = System.getenv("PORT") ?: "8080"
    val puertoInt = puertoString.toInt()

    // Configuramos el servidor con inicialización directa en bloque para no perder el puerto en el JAR
    embeddedServer(Netty, port = puertoInt, host = "0.0.0.0") {

        // 🚀 CRÍTICO: Configuración de serialización JSON real
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
            })
        }

        // Inicializar y conectar la Base de Datos nativamente
        configureDatabase()

        // Configuración de las rutas de consulta (Endpoints)
        routing {
            get("/") {
                call.respondText("¡Servidor Ktor de SmartFoodUp conectado exitosamente a MySQL en la Nube!")
            }

            // CONEXIÓN DEL ENDPOINT DE AUTENTICACIÓN (Registro/Login)
            authRouting()
        }
    }.start(wait = true)
}

// ==========================================
// 2. CONFIGURACIÓN DE CONEXIÓN A MYSQL HÍBRIDA
// ==========================================
fun configureDatabase() {
    val host = System.getenv("MYSQLHOST")
    val port = System.getenv("MYSQLPORT") ?: "3306"
    val database = System.getenv("MYSQLDATABASE")
    val user = System.getenv("MYSQLUSER")
    val password = System.getenv("MYSQLPASSWORD")

    val jdbcUrl = if (host != null) {
        "jdbc:mysql://$host:$port/$database?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
    } else {
        "jdbc:mysql://localhost:3306/smartfoodup?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
    }

    val dbUser = user ?: "root"
    val dbPassword = password ?: ""

    Database.connect(
        url = jdbcUrl,
        driver = "com.mysql.cj.jdbc.Driver",
        user = dbUser,
        password = dbPassword
    )

    // Bloque directo que creará las tablas al levantar exitosamente
    transaction {
        SchemaUtils.create(Usuarios, Dispositivos, MedicionesSensores, AnalisisIa, RecomendacionesConsumo)
    }
}