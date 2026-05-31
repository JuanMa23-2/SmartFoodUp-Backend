package com.example.prueba_multiplataforma

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform