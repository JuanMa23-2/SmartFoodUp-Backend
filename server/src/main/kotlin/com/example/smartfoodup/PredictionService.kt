package com.example.smartfoodup

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.tensorflow.SavedModelBundle
import org.tensorflow.Tensor
import org.tensorflow.ndarray.NdArrays
import org.tensorflow.ndarray.Shape
import org.tensorflow.types.TFloat32
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.util.*
import javax.imageio.ImageIO

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

    // Nota: El cargado real del modelo .h5 de Keras en Java requiere convertirlo a SavedModel o usar una lib específica.
    // Para este ejemplo, simulamos la lógica de predicción basada en la imagen.
    fun predecirImagen(base64: String): PredictionResult {
        try {
            val imageBytes = Base64.getDecoder().decode(base64.replace("\n", ""))
            val image = ImageIO.read(ByteArrayInputStream(imageBytes))
            
            // Simulación: En un entorno real aquí cargarías el modelo con TensorFlow Java
            // y pasarías los tensores. Dado que es un modelo .h5, lo ideal es convertirlo
            // pero para que el flujo del frontend funcione, devolveremos una predicción basada en el catálogo.
            
            // Por ahora, simulamos una detección aleatoria o basada en metadatos para que el usuario vea resultados
            val indiceAleatorio = (0 until classNames.size).random()
            return mapearPrediccion(indiceAleatorio)
            
        } catch (e: Exception) {
            return PredictionResult("Error", "No detectado", false, "Error al procesar imagen: ${e.message}")
        }
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
