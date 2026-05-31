package com.example.smartfoodup

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

// ==========================================
// 🛠️ IMPORTACIÓN DE TUS TABLAS NATIVAS
// ==========================================
// Esto enlaza Usuarios, Restaurantes, Categorias, Productos y Pedidos automáticamente
import com.example.smartfoodup.*

// ==========================================
// 1. ARRANQUE DEL SERVIDOR (Netty)
// ==========================================
fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

// ==========================================
// 2. MÓDULO PRINCIPAL DE LA APLICACIÓN
// ==========================================
fun Application.module() {
    // Inicializar y conectar la Base de Datos al arrancar
    configureDatabase()

    // Configuración de las rutas de consulta (Endpoints)
    routing {
        get("/") {
            call.respondText("¡Servidor Ktor de SmartFoodUp conectado exitosamente a MySQL!")
        }
    }
}

// ==========================================
// 3. CONFIGURACIÓN DE CONEXIÓN A MYSQL
// ==========================================
fun Application.configureDatabase() {
    // Conexión segura al contenedor local de XAMPP
    Database.connect(
        url = "jdbc:mysql://localhost:3306/smartfoodup?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
        driver = "com.mysql.cj.jdbc.Driver",
        user = "root",      // Usuario por defecto en XAMPP
        password = ""       // Contraseña por defecto en XAMPP (vacía)
    )

    // Bloque transaccional que crea las 5 tablas en phpMyAdmin si no existen
    transaction {
        SchemaUtils.create(
            Usuarios,
            Restaurantes,
            Categorias,
            Productos,
            Pedidos
        )
    }
}