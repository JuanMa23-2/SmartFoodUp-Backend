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
        val host = System.getenv("MYSQLHOST") ?: "localhost"
        val port = System.getenv("MYSQLPORT") ?: "3306"
        val database = System.getenv("MYSQLDATABASE") ?: "smartfoodup"
        val user = System.getenv("MYSQLUSER") ?: "root"
        val password = System.getenv("MYSQLPASSWORD") ?: ""

        val url = "jdbc:mysql://$host:$port/$database?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"

        Database.connect(
            url = url,
            driver = "com.mysql.cj.jdbc.Driver",
            user = user,
            password = password
        )

        // Se añade la nueva tabla al bloque de inicializacion automatica
        transaction {
            SchemaUtils.create(
                Usuarios,
                Dispositivos,
                MedicionesSensores,
                AnalisisIa,
                RecomendacionesConsumo,
                AlimentosLocales // Nueva tabla para el catalogo del administrador
            )
        }

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

object Usuarios : Table("usuarios") {
    val id = integer("id").autoIncrement()
    val nombre = varchar("nombre", 100)
    val email = varchar("email", 150).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val rol = varchar("rol", 20).default("CLIENTE")
    val fechaRegistro = datetime("fecha_registro").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}

object Dispositivos : Table("dispositivos") {
    val id = integer("id").autoIncrement()
    val usuarioId = integer("usuario_id").references(Usuarios.id).nullable()
    val picoMacAddress = varchar("pico_mac_address", 50).uniqueIndex()
    val nombreDispositivo = varchar("nombre_dispositivo", 100).default("Mi Pico W")
    val fechaVinculacion = datetime("fecha_vinculacion").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}

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

object AnalisisIa : Table("analisis_ia") {
    val id = integer("id").autoIncrement()
    val usuarioId = integer("usuario_id").references(Usuarios.id)
    val urlFoto = varchar("url_foto", 255).nullable()
    val frutaDetectada = varchar("fruta_detectada", 100)
    val porcentajeFrescura = double("porcentaje_frescura")
    val esSaludable = bool("es_saludable")
    val fechaAnalisis = datetime("fecha_analisis").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}

object RecomendacionesConsumo : Table("recomendaciones_consumo") {
    val id = integer("id").autoIncrement()
    val rangoMadurez = varchar("rango_madurez", 30)
    val consejoConservacion = text("consejo_conservacion")
    val recetaSugerida = text("receta_sugerida")

    override val primaryKey = PrimaryKey(id)
}

// NUEVA TABLA: Catalogo de altas de frutas y alimentos agregados por administradores
object AlimentosLocales : Table("alimentos_locales") {
    val id = integer("id").autoIncrement()
    val nombre = varchar("nombre", 100)
    val categoria = varchar("categoria", 100)
    val cantidad = integer("cantidad")
    val imagenBase64 = text("imagen_base64").nullable() // Almacena la cadena larga de la foto
    val fechaCreacion = datetime("fecha_creacion").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}