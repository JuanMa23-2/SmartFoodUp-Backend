package com.example.smartfoodup

package com.example.smartfoodup

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.core.*
import java.io.File

object `ModelServices.kt` {
    // ⚠️ Reemplaza con el ID público de tu modelo en Google Drive
    private const val DRIVE_FILE_ID = "1TxFYdQKSIaVomFVxlgrMUNB_QQo09T-d"
    private const val MODEL_FILENAME = "modelo_smartfoodup_36clases(1).h5"

    suspend fun obtenerModeloFile(): File {
        val file = File(MODEL_FILENAME)

        // Si el modelo ya fue descargado en el contenedor de Railway, no lo descarga de nuevo
        if (file.exists() && file.length() > 0) {
            println("Modelo cargado desde el disco local de Railway.")
            return file
        }

        println("Descargando modelo desde Google Drive a Railway...")
        val client = HttpClient(CIO)
        val downloadUrl = "https://drive.google.com/uc?export=download&id=$DRIVE_FILE_ID"

        try {
            val response: HttpResponse = client.get(downloadUrl)
            val bytes = response.readBytes()
            file.writeBytes(bytes)
            println("🎉 Modelo descargado con éxito en Railway: ${file.absolutePath}")
        } catch (e: Exception) {
            println("❌ Error al descargar el modelo desde Drive: ${e.message}")
        } finally {
            client.close()
        }

        return file
    }
}