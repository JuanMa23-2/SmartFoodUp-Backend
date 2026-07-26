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
    // .takeIf { it.isNotBlank() } es la clave: ignora los textos vacíos que Railway está enviando
    val host = System.getenv("MYSQLHOST")?.takeIf { it.isNotBlank() }
    val port = System.getenv("MYSQLPORT")?.takeIf { it.isNotBlank() } ?: "3306"
    val database = System.getenv("MYSQLDATABASE")?.takeIf { it.isNotBlank() }
    val user = System.getenv("MYSQLUSER")?.takeIf { it.isNotBlank() }
    val password = System.getenv("MYSQLPASSWORD")?.takeIf { it.isNotBlank() }

    if (host == null || database == null) {
        println("❌ ERROR: Las variables MYSQLHOST o MYSQLDATABASE están VACÍAS en Railway.")
        println("Vuelve a enlazar el servicio MySQL al Servidor o escribe los valores manualmente.")
        return
    }

    val jdbcUrl = "jdbc:mysql://$host:$port/$database?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
    println("🚀 Intentando conectar a BD Real: $jdbcUrl")

    try {
        Database.connect(jdbcUrl, "com.mysql.cj.jdbc.Driver", user ?: "root", password ?: "")
        transaction {
            SchemaUtils.create(Usuarios, Dispositivos, MedicionesSensores, AnalisisIa, RecomendacionesConsumo, AlimentosLocales)
        }
        println("✅ ¡CONECTADO CON ÉXITO!")
    } catch (e: Exception) {
        println("❌ Error de conexión: ${e.message}")
    }
}
