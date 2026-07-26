package com.example.smartfoodup

import kotlinx.serialization.Serializable

@Serializable
data class RegistroRequest(
    val nombre: String,
    val email: String,
    val contrasena: String
)

@Serializable
data class LoginRequest(
    val email: String,
    val contrasena: String
)

@Serializable
data class AdminRegistroRequest(
    val nombre: String,
    val email: String,
    val contrasena: String,
    val rol: String
)

@Serializable
data class AuthResponse(
    val exitoso: Boolean,
    val mensaje: String,
    val nombre: String? = null,
    val rol: String? = null
)

@Serializable
data class AlimentoRequest(
    val nombre: String,
    val categoria: String,
    val cantidad: Int,
    val imagenBytesBase64: String? = null
)

@Serializable
data class AlimentoResponse(
    val exitoso: Boolean,
    val mensaje: String,
    val alimento: String? = null,    // Cambiado de 'fruta' a 'alimento' para el Frontend
    val estado: String? = null,
    val porcentajeFrescura: Double? = null,
    val sugerencia: String? = null   // Cambiado a singular para el Frontend
)
