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
}

// Forzamos a que el manifiesto apunte correctamente usando la tarea nativa de Ktor
tasks.named<org.gradle.api.tasks.bundling.Jar>("jar") {
    manifest {
        attributes["Main-Class"] = "io.ktor.server.netty.EngineMain"
    }
}