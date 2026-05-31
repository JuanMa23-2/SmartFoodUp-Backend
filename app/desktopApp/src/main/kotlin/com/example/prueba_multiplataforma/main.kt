package com.example.prueba_multiplataforma

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Pruebamultiplataforma",
    ) {
        App()
    }
}