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

// Configuracion de serializacion JSON con soporte para campos desconocidos.
fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = true
        })
    }
}

// Registro de los modulos de rutas principales del sistema.
fun Application.configureRouting() {
    routing {
        authRouting()
        iaRouting()
        arRouting()
    }
}

// Configuracion y conexion a la base de datos MySQL con logica de reintento.
fun Application.configureDatabase() {
    val host = System.getenv("MYSQLHOST")?.takeIf { it.isNotBlank() } ?: "mysql.railway.internal"
    val port = System.getenv("MYSQLPORT")?.takeIf { it.isNotBlank() } ?: "3306"
    val database = System.getenv("MYSQLDATABASE")?.takeIf { it.isNotBlank() } ?: "railway"
    val user = System.getenv("MYSQLUSER")?.takeIf { it.isNotBlank() } ?: "root"
    val password = System.getenv("MYSQLPASSWORD")?.takeIf { it.isNotBlank() } ?: ""

    val jdbcUrl = "jdbc:mysql://$host:$port/$database?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"

    println("Iniciando conexion a base de datos: $jdbcUrl")

    var connected = false
    var attempts = 0
    while (!connected && attempts < 3) {
        try {
            Database.connect(jdbcUrl, "com.mysql.cj.jdbc.Driver", user, password)
            transaction {
                SchemaUtils.create(Usuarios, Dispositivos, MedicionesSensores, AnalisisIa, RecomendacionesConsumo, AlimentosLocales)
            }
            println("Conexion establecida exitosamente.")
            connected = true
        } catch (e: Exception) {
            attempts++
            println("Intento de conexion $attempts fallido: ${e.message}")
            if (attempts < 3) Thread.sleep(2000)
        }
    }
}
