package com.example.prueba_multiplataforma

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

// ==========================================
// 1. TABLA DE USUARIOS (Cuentas de la App)
// ==========================================
object UsuariosTable : Table("usuarios") {
    val id = integer("id").autoIncrement()
    val nombre = varchar("nombre", 100)
    val email = varchar("email", 100).uniqueIndex()          // Unique evita correos repetidos
    val passwordHash = varchar("password_hash", 255)         // Contraseña encriptada
    val fechaCreacion = datetime("fecha_creacion").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}

// ==========================================
// 2. TABLA DE DISPOSITIVOS (Hardware IoT)
// ==========================================
object DispositivosTable : Table("dispositivos") {
    val id = integer("id").autoIncrement()
    val tokenDispositivo = varchar("token_dispositivo", 50).uniqueIndex() // Código único de la Pico
    val nombre = varchar("nombre", 100)                      // Nombre personalizado (Ej: Contenedor 1)
    val usuarioId = integer("usuario_id").references(UsuariosTable.id) // Vinculado al dueño real
    val fechaRegistro = datetime("fecha_registro").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}

// ==========================================
// 3. TABLA DE TELEMETRÍA (Histórico de Sensores)
// ==========================================
object MedicionesTable : Table("mediciones_sensores") {
    val id = bigint("id").autoIncrement()
    val dispositivoToken = varchar("dispositivo_token", 50).references(DispositivosTable.tokenDispositivo)
    val peso = double("peso")                                // Lecturas reales enviadas por la Pico
    val temperatura = double("temperatura")
    val humedad = double("humedad")
    val gasPorcentaje = integer("gas_porcentaje")
    val estadoSemaforo = varchar("estado_semaforo", 20)      // OPTIMO, PREVENTIVO, CRITICO
    val fechaHora = datetime("fecha_hora").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}

// ==========================================
// 4. TABLA DE ANÁLISIS IA (Escáner de la App)
// ==========================================
object AnalisisIaTable : Table("analisis_ia") {
    val id = bigint("id").autoIncrement()
    val usuarioId = integer("usuario_id").references(UsuariosTable.id) // Quién tomó la foto
    val dispositivoToken = varchar("dispositivo_token", 50).references(DispositivosTable.tokenDispositivo).nullable() // Nullable por si usa "Solo Foto"
    val urlImagen = varchar("url_imagen", 255)               // Link de la foto guardada en el servidor
    val alimentoDetectado = varchar("alimento_detectado", 50)// Ej: Manzana, Plátano...
    val esSaludable = bool("es_saludable")                   // True o False
    val porcentajeFrescura = integer("porcentaje_frescura")  // Ej: 85%
    val fechaAnalisis = datetime("fecha_analisis").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}

// ==========================================
// 5. TABLA MAESTRA DE RECOMENDACIONES (Recetas/Consejos)
// ==========================================
object RecomendacionesTable : Table("recomendaciones_consumo") {
    val id = integer("id").autoIncrement()
    val alimento = varchar("alimento", 50)                   // Ej: Plátano
    val rangoMadurez = varchar("rango_madurez", 20)           // OPTIMO, PREVENTIVO, CRITICO
    val consejoConservacion = text("consejo_conservacion")   // Consejos fijos de conservación
    val sugerenciaUso = text("sugerencia_uso")               // Recetas fijas de contingencia (Fallback)

    override val primaryKey = PrimaryKey(id)
}