package com.example.smartfoodup

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

// 1. El arranque nativo se delega al EngineMain de Ktor
fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

// 2. MÓDULO PRINCIPAL DE LA APLICACIÓN
fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
        })
    }

    // Inicializar y conectar la Base de Datos al arrancar
    configureDatabase()

    // Configuración de las rutas de consulta (Endpoints)
    routing {
        get("/") {
            call.respondText("¡Servidor Ktor de SmartFoodUp conectado exitosamente a MySQL en la Nube!")
        }

        // CONEXIÓN DEL ENDPOINT DE AUTENTICACIÓN (Registro/Login)
        authRouting()
    }
}

// 3. CONFIGURACIÓN DE CONEXIÓN A MYSQL HÍBRIDA
fun Application.configureDatabase() {
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


    transaction {
        SchemaUtils.create(Usuarios, Dispositivos, MedicionesSensores, AnalisisIa, RecomendacionesConsumo)
    }
}