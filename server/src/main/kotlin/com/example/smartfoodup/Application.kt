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
    // Tomamos las variables estrictamente de Railway
    val host = System.getenv("MYSQLHOST")?.takeIf { it.isNotBlank() }
    val port = System.getenv("MYSQLPORT") ?: "3306"
    val database = System.getenv("MYSQLDATABASE")
    val user = System.getenv("MYSQLUSER")
    val password = System.getenv("MYSQLPASSWORD")

    if (host == null || database == null) {
        println("❌ ERROR CRÍTICO: No se encontraron variables de base de datos. El servidor no conectará a nada.")
        return
    }

    val jdbcUrl = "jdbc:mysql://$host:$port/$database?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
    println("🚀 Conectando a Base de Datos en: $host")

    try {
        Database.connect(jdbcUrl, "com.mysql.cj.jdbc.Driver", user ?: "root", password ?: "")
        transaction {
            // Esto creará las tablas en la base de datos VACÍA que ves en Railway
            SchemaUtils.create(Usuarios, Dispositivos, MedicionesSensores, AnalisisIa, RecomendacionesConsumo, AlimentosLocales)
        }
        println("✅ Base de datos nueva configurada y lista.")
    } catch (e: Exception) {
        println("❌ Error de conexión: ${e.message}")
    }
}
