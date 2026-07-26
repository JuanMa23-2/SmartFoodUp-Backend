package com.example.smartfoodup

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

data class PredictionResult(
    val alimento: String,
    val claseDetectada: String,
    val esSaludable: Boolean,
    val sugerencia: String
)

object PredictionService {
    private val classNames: List<String> by lazy {
        val jsonStream = object {}.javaClass.classLoader.getResourceAsStream("clases.json")
            ?: throw IllegalStateException("No se encontró el archivo clases.json en resources")

        val mapper = jacksonObjectMapper()
        mapper.readValue<List<String>>(jsonStream)
    }

    fun mapearPrediccion(claseIndex: Int): PredictionResult {
        val claseNombre = classNames.getOrElse(claseIndex) { "Desconocido" }

        val esSaludable = claseNombre.contains("Healthy", ignoreCase = true) ||
                claseNombre.contains("Healty", ignoreCase = true)

        val partes = claseNombre.split("__")
        val alimento = partes.getOrNull(0) ?: "Alimento"

        val sugerencia = if (esSaludable) {
            "Apto para consumo o preparación de recetas."
        } else {
            "Alimento en estado de descomposición. Se recomienda desechar o usar en compostaje."
        }

        return PredictionResult(
            alimento = alimento,
            claseDetectada = claseNombre,
            esSaludable = esSaludable,
            sugerencia = sugerencia
        )
    }
}