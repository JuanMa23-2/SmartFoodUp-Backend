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
    val host = System.getenv("MYSQLHOST") ?: "localhost"
    val port = System.getenv("MYSQLPORT") ?: "3306"
    val database = System.getenv("MYSQLDATABASE") ?: "smartfoodup"
    val user = System.getenv("MYSQLUSER") ?: "root"
    val password = System.getenv("MYSQLPASSWORD") ?: ""

    val jdbcUrl = "jdbc:mysql://$host:$port/$database?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"

    println("🚀 Intentando conectar a la base de datos en: $jdbcUrl")

    try {
        Database.connect(
            url = jdbcUrl,
            driver = "com.mysql.cj.jdbc.Driver",
            user = user,
            password = password
        )

        transaction {
            SchemaUtils.create(
                Usuarios,
                Dispositivos,
                MedicionesSensores,
                AnalisisIa,
                RecomendacionesConsumo,
                AlimentosLocales
            )
            println("✅ Esquema de base de datos verificado/creado con éxito.")
        }
    } catch (e: Exception) {
        println("❌ ERROR CRÍTICO EN BASE DE DATOS: ${e.message}")
        // No lanzamos excepción aquí para permitir que el servidor arranque 
        // y puedas ver los logs de error en la app si falla una ruta.
    }
}
