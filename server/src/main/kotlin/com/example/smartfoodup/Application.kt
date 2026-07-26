package com.example.smartfoodup

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    configureSerialization()
    configureDatabase()
    configureRouting()
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = true
        })
    }
}

fun Application.configureRouting() {
    routing {
        authRouting()
        iaRouting()
    }
}

fun Application.configureDatabase() {
    // Railway inyecta estas variables. takeIf asegura que no usemos textos vacíos.
    val host = System.getenv("MYSQLHOST")?.takeIf { it.isNotBlank() } ?: "mysql.railway.internal"
    val port = System.getenv("MYSQLPORT")?.takeIf { it.isNotBlank() } ?: "3306"
    val database = System.getenv("MYSQLDATABASE")?.takeIf { it.isNotBlank() } ?: "railway"
    val user = System.getenv("MYSQLUSER")?.takeIf { it.isNotBlank() } ?: "root"
    val password = System.getenv("MYSQLPASSWORD")?.takeIf { it.isNotBlank() } ?: ""

    val jdbcUrl = "jdbc:mysql://$host:$port/$database?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"

    println("🚀 Intentando conectar a: $jdbcUrl")

    try {
        Database.connect(
            url = jdbcUrl,
            driver = "com.mysql.cj.jdbc.Driver",
            user = user,
            password = password
        )

        transaction {
            SchemaUtils.create(Usuarios, Dispositivos, MedicionesSensores, AnalisisIa, RecomendacionesConsumo, AlimentosLocales)
        }
        println("✅ Base de datos conectada con éxito.")
    } catch (e: Exception) {
        println("❌ Error en conexión de BD: ${e.message}")
    }
}
