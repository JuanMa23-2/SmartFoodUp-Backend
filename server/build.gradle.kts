plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
}

group = "com.example.prueba_multiplataforma"
version = "1.0.0"
application {
    mainClass = "com.example.prueba_multiplataforma.ApplicationKt"
}

dependencies {
    api(projects.core)
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
    // 1. El Conector oficial para comunicarnos con MySQL de Railway
    implementation("mysql:mysql-connector-java:8.0.33")
    // 2. El framework 'Exposed' de JetBrains (ORM para manejar la BD con código Kotlin limpio)
    implementation("org.jetbrains.exposed:exposed-core:0.42.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.42.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.42.0")
    // 3. Herramienta ligera para proteger las contraseñas en el Login (BCrypt)
    implementation("org.mindrot:jbcrypt:0.4")

}