plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
    alias(libs.plugins.shadow)
}

group = "com.example.smartfoodup"
version = "1.0.0"

application {
    // Declaramos el punto de entrada de tu servidor Ktor
    mainClass.set("com.example.smartfoodup.ApplicationKt")
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

// Configuración de la tarea ShadowJar para inyectar correctamente el Manifiesto Principal
tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    manifest {
        attributes["Main-Class"] = "com.example.smartfoodup.ApplicationKt"
    }
}