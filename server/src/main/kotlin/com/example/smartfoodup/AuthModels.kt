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
    val imagenBytesBase64: String? = null,
    val dispositivoId: Int? = null 
)

@Serializable
data class AlimentoResponse(
    val exitoso: Boolean,
    val mensaje: String,
    val alimento: String? = null,
    val estado: String? = null,
    val porcentajeFrescura: Double? = null,
    val sugerencia: String? = null,
    val recetas: String? = null,
    val datosSensores: String? = null,
    val alertaRiesgo: String? = null,
    val hardwareOnline: Boolean? = null,
    val hardwareMensaje: String? = null
)

@Serializable
data class SensorDataRequest(
    val deviceId: Int,
    val peso: Double,
    val humedad: Double,
    val temperatura: Double,
    val gas: Double
)
