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
import com.example.smartfoodup.*

// ==========================================
// 1. ARRANQUE DEL SERVIDOR (Puerto Dinámico)
// ==========================================
fun main() {
    // Railway asigna un puerto aleatorio en la variable PORT. Si no existe, usa el 8080 por defecto.
    val port = System.getenv("PORT")?.toInt() ?: 8080

    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
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
            call.respondText("¡Servidor Ktor de SmartFoodUp conectado exitosamente a MySQL en la Nube!")
        }
    }
}

// ==========================================
// 3. CONFIGURACIÓN DE CONEXIÓN A MYSQL HÍBRIDA
// ==========================================
fun Application.configureDatabase() {
    // 1. Intentar leer las variables de entorno de Railway
    val host = System.getenv("MYSQLHOST")
    val port = System.getenv("MYSQLPORT") ?: "3306"
    val database = System.getenv("MYSQLDATABASE")
    val user = System.getenv("MYSQLUSER")
    val password = System.getenv("MYSQLPASSWORD")

    val jdbcUrl = if (host != null) {
        // URL de Producción para Railway
        "jdbc:mysql://$host:$port/$database?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
    } else {
        // URL Local de respaldo para XAMPP
        "jdbc:mysql://localhost:3306/smartfoodup?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
    }

    val dbUser = user ?: "root"
    val dbPassword = password ?: ""

    // Conexión final usando los datos calculados
    Database.connect(
        url = jdbcUrl,
        driver = "com.mysql.cj.jdbc.Driver",
        user = dbUser,
        password = dbPassword
    )

    // Bloque transaccional que crea las 5 tablas en Railway o XAMPP si no existen
    transaction {
        SchemaUtils.create(Usuarios, Dispositivos, MedicionesSensores, AnalisisIa, RecomendacionesConsumo)
    }
}