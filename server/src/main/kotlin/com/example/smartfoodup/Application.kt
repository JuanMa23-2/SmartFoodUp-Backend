fun Application.configureDatabase() {
    val host = System.getenv("MYSQLHOST")
    val port = System.getenv("MYSQLPORT") ?: "3306"
    val database = System.getenv("MYSQLDATABASE")
    val user = System.getenv("MYSQLUSER")
    val password = System.getenv("MYSQLPASSWORD")

    val jdbcUrl = if (host != null) {
        "jdbc:mysql://$host:$port/$database?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
    } else {
        "jdbc:mysql://localhost:3306/smartfoodup?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
    }

    val dbUser = user ?: "root"
    val dbPassword = password ?: ""

    Database.connect(
        url = jdbcUrl,
        driver = "com.mysql.cj.jdbc.Driver",
        user = dbUser,
        password = dbPassword
    )

    // OPERACIÓN FORZADA: Borramostodo y creamos el esquema completo de 6 tablas
    transaction {
        SchemaUtils.drop(Usuarios, Dispositivos, MedicionesSensores, AnalisisIa, RecomendacionesConsumo, AlimentosLocales)
        SchemaUtils.create(Usuarios, Dispositivos, MedicionesSensores, AnalisisIa, RecomendacionesConsumo, AlimentosLocales)
    }
}