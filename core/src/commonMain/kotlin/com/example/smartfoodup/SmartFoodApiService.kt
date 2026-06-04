package com.example.smartfoodup

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class SmartFoodApiService {

    // Configuramos el cliente HTTP Multiplataforma con soporte de traducción JSON
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true // Evita que la app truene si el servidor manda datos de más
                prettyPrint = true
            })
        }
    }

    /**
     * Petición asíncrona no bloqueante al servidor (Fase 2 del diagrama)
     * Usamos la IP 10.0.2.2 porque es el puente que usa el emulador de Android
     * para comunicarse con el localhost de tu misma computadora.
     */
    suspend fun checkServerStatus(): String {
        return try {
            val response: HttpResponse = httpClient.get("http://10.0.2.2:8080/")
            response.bodyAsText() // Retorna el texto de éxito que configuramos en Ktor
        } catch (e: Exception) {
            "Error de conexión con el backend: ${e.message}"
        }
    }
}