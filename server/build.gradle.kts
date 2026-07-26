plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
}

group = "com.example.smartfoodup"
version = "1.0.0"

application {
    // Definimos la clase principal nativa del framework
    mainClass.set("io.ktor.server.netty.EngineMain")
}

dependencies {
    // Logs del servidor
    implementation(libs.logback)
    implementation("org.mindrot:jbcrypt:0.4")

    // Servidor Ktor (Usando la configuración nativa de tu catálogo)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serializationKotlinxJson)

    // =======================================================
    // 🗄️ BASE DE DATOS: Exposed ORM & MySQL Driver
    // =======================================================
    implementation("org.jetbrains.exposed:exposed-core:0.41.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.41.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.41.1")
    implementation("org.jetbrains.exposed:exposed-java-time:0.41.1")
    implementation("mysql:mysql-connector-java:8.0.33")
    // =======================================================

    // Pruebas unitarias
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
    // Para hacer descargas HTTP desde Ktor (Cliente HTTP)
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-cio:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-client-serialization:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")

    // Para procesar JSON (Jackson o kotlinx.serialization)
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")

    // Dependencia de TensorFlow Java para cargar el modelo .h5 / .pb
    implementation("org.tensorflow:tensorflow-core-platform:0.5.0")
}

// Forzamos a que el manifiesto apunte correctamente usando la tarea nativa de Ktor
tasks.named<JavaExec>("run") {
    // Lee el puerto asignado dinámicamente por Railway o usa el 8080 por defecto
    val port = System.getenv("PORT") ?: "8080"
    args("--port=$port")
}