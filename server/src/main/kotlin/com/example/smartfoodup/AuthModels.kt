package com.example.smartfoodup

import kotlinx.serialization.Serializable

// Este objeto representa los datos exactos que el Frontend (Celular) enviará al Backend
@Serializable
data class RegistroRequest(
    val nombre: String,
    val email: String,
    val contrasena: String
)

// Este objeto representa la respuesta que el Servidor le devolverá al celular con el nombre dinámico y su rol
@Serializable
data class AuthResponse(
    val exitoso: Boolean,
    val mensaje: String,
    val nombre: String? = null, // transportar el nombre real
    val rol: String? = null    //  Transporta el rol real (ADMIN / CLIENTE) desde la base de datos
)