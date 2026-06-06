plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
}

group = "com.example.smartfoodup"
version = "1.0.0"
application {
    mainClass = "com.example.smartfoodup.ApplicationKt"
}

dependencies {

    // Logs del servidor
    implementation(libs.logback)

    // Servidor Ktor (Usando la configuración nativa de tu catálogo)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)

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