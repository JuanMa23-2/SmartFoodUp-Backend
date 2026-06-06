package com.example.smartfoodup

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

// ==========================================
// CONFIGURACIÓN DE CONEXIÓN HÍBRIDA (LOCAL / NUBE)
// ==========================================
object DatabaseFactory {
    fun init() {
        // 1. Intenta leer las variables de entorno que te da Railway en la nube.
        val host = System.getenv("MYSQLHOST") ?: "localhost"
        val port = System.getenv("MYSQLPORT") ?: "3306"
        val database = System.getenv("MYSQLDATABASE") ?: "smartfoodup"
        val user = System.getenv("MYSQLUSER") ?: "root"
        val password = System.getenv("MYSQLPASSWORD") ?: ""
        // 2. Armamos la URL de conexión JDBC de forma dinámica
        val url = "jdbc:mysql://$host:$port/$database?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
        // 3. Inicializa el motor de Exposed con los datos calculados
        Database.connect(
            url = url,
            driver = "com.mysql.cj.jdbc.Driver",
            user = user,
            password = password
        )

        // 4. Bloque de transacción segura que crea las tablas automáticamente si no existen
        transaction {
            SchemaUtils.create(
                Usuarios,
                Dispositivos,
                MedicionesSensores,
                AnalisisIa,
                RecomendacionesConsumo
            )
        }

        // Mensaje de confirmación en la consola del servidor
        if (host == "localhost") {
            println("¡Exposed conectado a XAMPP local con éxito!")
        } else {
            println("¡Exposed conectado a la Base de Datos en la Nube de Railway!")
        }
    }
}

// ==========================================
// DEFINICIÓN DE TABLAS RELACIONALES (EXPOSED ORM)
// ==========================================

// 1. TABLA USUARIOS
object Usuarios : Table("usuarios") {
    val id = integer("id").autoIncrement()
    val nombre = varchar("nombre", 100)
    val email = varchar("email", 150).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val fechaRegistro = datetime("fecha_registro").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}

// 2. TABLA DISPOSITIVOS (Raspberry Pi Pico W)
object Dispositivos : Table("dispositivos") {
    val id = integer("id").autoIncrement()
    val usuarioId = integer("usuario_id").references(Usuarios.id)
    val picoMacAddress = varchar("pico_mac_address", 50).uniqueIndex()
    val nombreDispositivo = varchar("nombre_dispositivo", 100).default("Mi Pico W")
    val fechaVinculacion = datetime("fecha_vinculacion").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}

// 3. TABLA MEDICIONES SENSORES (Peso, Gas, Temperatura, Humedad)
object MedicionesSensores : Table("mediciones_sensores") {
    val id = integer("id").autoIncrement()
    val dispositivoId = integer("dispositivo_id").references(Dispositivos.id)
    val pesoGramos = double("peso_gramos")
    val gasAdc = integer("gas_adc")
    val gasPorcentaje = double("gas_porcentaje")
    val temperatura = double("temperatura")
    val humedad = double("humedad")
    val fechaMedicion = datetime("fecha_medicion").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}

// 4. TABLA ANALISIS IA (Visión Computacional / Frescura)
object AnalisisIa : Table("analisis_ia") {
    val id = integer("id").autoIncrement()
    val usuarioId = integer("usuario_id").references(Usuarios.id)
    val urlFoto = varchar("url_foto", 255).nullable()
    val frutaDetectada = varchar("fruta_detectada", 100)
    val porcentajeFrescura = double("porcentaje_frescura")
    val esSaludable = bool("es_saludable") // Corregido: 'bool' nativo de Exposed
    val fechaAnalisis = datetime("fecha_analisis").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}

// 5. TABLA RECOMENDACIONES CONSUMO (Tabla Maestra)
object RecomendacionesConsumo : Table("recomendaciones_consumo") {
    val id = integer("id").autoIncrement()
    val rangoMadurez = varchar("rango_madurez", 30) // "Óptimo", "Preventivo", "Crítico"
    val consejoConservacion = text("consejo_conservacion")
    val recetaSugerida = text("receta_sugerida")

    override val primaryKey = PrimaryKey(id)
}