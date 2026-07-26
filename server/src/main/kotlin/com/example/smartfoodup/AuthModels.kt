package com.example.smartfoodup

import kotlinx.serialization.Serializable

// Este objeto representa los datos exactos que el Frontend (Celular) enviará al Backend
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

// Este objeto representa la respuesta que el Servidor le devolverá al celular con el nombre dinámico y su rol
@Serializable
data class AuthResponse(
    val exitoso: Boolean,
    val mensaje: String,
    val nombre: String? = null, // transportar el nombre real
    val rol: String? = null    //  Transporta el rol real (ADMIN / CLIENTE) desde la base de datos
)

// Nuevos modelos de transferencia de datos para el modulo de alimentos
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
    val fruta: String? = null,
    val estado: String? = null,
    val porcentajeFrescura: Double? = null,
    val sugerencias: String? = null
)
