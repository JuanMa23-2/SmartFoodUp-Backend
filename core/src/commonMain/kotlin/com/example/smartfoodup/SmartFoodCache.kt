package com.example.smartfoodup

/**
 * Almacenamiento local temporal en memoria compartida (Fase 8 del diagrama)
 * Almacena el último estado del servicio de forma persistente durante la sesión.
 */
object SmartFoodCache {
    var lastServerResponse: String = "Sin datos guardados localmente"
}