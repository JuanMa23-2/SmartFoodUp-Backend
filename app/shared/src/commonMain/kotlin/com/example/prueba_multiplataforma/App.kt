package com.example.prueba_multiplataforma

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class DatosEntrega(
    val nombre: String,
    val matricula: String,
    val asignatura: String,
    val hora: String,
    val fecha: String
)

@Composable
fun App() {
    var nombre by remember { mutableStateOf("") }
    var matricula by remember { mutableStateOf("") }
    var asignatura by remember { mutableStateOf("") }
    var hora by remember { mutableStateOf("") }
    var fecha by remember { mutableStateOf("") }

    var errorNombre by remember { mutableStateOf<String?>(null) }
    var errorMatricula by remember { mutableStateOf<String?>(null) }
    var errorAsignatura by remember { mutableStateOf<String?>(null) }
    var errorHora by remember { mutableStateOf<String?>(null) }
    var errorFecha by remember { mutableStateOf<String?>(null) }

    var datosGuardados by remember { mutableStateOf<DatosEntrega?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Formulario",
                style = MaterialTheme.typography.titleLarge
            )
            Text(text = "Nombre")
            TextField(
                value = nombre,
                onValueChange = {
                    nombre = it
                    errorNombre = null
                },
                placeholder = { Text("Escribe aquí...") },
                isError = errorNombre != null,
                modifier = Modifier.fillMaxWidth()
            )
            errorNombre?.let { Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Text(text = "Matrícula")
            TextField(
                value = matricula,
                onValueChange = {
                    matricula = it
                    errorMatricula = null
                },
                placeholder = { Text("Escribe aquí...") },
                isError = errorMatricula != null,
                modifier = Modifier.fillMaxWidth()
            )
            errorMatricula?.let { Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Text(text = "Asignatura")
            TextField(
                value = asignatura,
                onValueChange = {
                    asignatura = it
                    errorAsignatura = null
                },
                placeholder = { Text("Escribe aquí...") },
                isError = errorAsignatura != null,
                modifier = Modifier.fillMaxWidth()
            )
            errorAsignatura?.let { Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Text(text = "Hora que se imparte")
            TextField(
                value = hora,
                onValueChange = { input ->
                    if (input.length <= 5) {
                        hora = input
                        errorHora = null
                    }
                },
                placeholder = { Text("Escribe aquí...") },
                isError = errorHora != null,
                modifier = Modifier.fillMaxWidth()
            )
            errorHora?.let { Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Text(text = "Fecha de entrega")
            TextField(
                value = fecha,
                onValueChange = { input ->
                    if (input.length <= 10) {
                        fecha = input
                        errorFecha = null
                    }
                },
                placeholder = { Text("Escribe aquí...") },
                isError = errorFecha != null,
                modifier = Modifier.fillMaxWidth()
            )
            errorFecha?.let { Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    var tieneErrores = false

                    if (nombre.isBlank()) {
                        errorNombre = "El nombre es obligatorio"
                        tieneErrores = true
                    } else if (nombre.any { it.isDigit() }) {
                        errorNombre = "El nombre no puede contener números"
                        tieneErrores = true
                    }

                    if (matricula.isBlank()) {
                        errorMatricula = "La matrícula es obligatoria"
                        tieneErrores = true
                    } else if (matricula.any { it.isLetter() }) {
                        errorMatricula = "La matrícula solo debe contener números"
                        tieneErrores = true
                    }

                    if (asignatura.isBlank()) {
                        errorAsignatura = "La asignatura es obligatoria"
                        tieneErrores = true
                    }

                    val horaPattern = "^[0-9]{2}:[0-9]{2}$".toRegex()
                    if (!hora.matches(horaPattern)) {
                        errorHora = "Formato requerido: XX:XX numérico"
                        tieneErrores = true
                    }

                    val fechaPattern = "^[0-9]{2}/[0-9]{2}/[0-9]{4}$".toRegex()
                    if (!fecha.matches(fechaPattern)) {
                        errorFecha = "Formato requerido: DD/MM/AAAA"
                        tieneErrores = true
                    }

                    if (!tieneErrores) {
                        datosGuardados = DatosEntrega(nombre, matricula, asignatura, hora, fecha)
                    } else {
                        datosGuardados = null
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guardar")
            }

            Spacer(modifier = Modifier.height(16.dp))

            datosGuardados?.let { datos ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Datos Capturados",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        HorizontalDivider()
                        Text(text = "Nombre: ${datos.nombre}")
                        Text(text = "Matrícula: ${datos.matricula}")
                        Text(text = "Asignatura: ${datos.asignatura}")
                        Text(text = "Hora: ${datos.hora}")
                        Text(text = "Fecha de Entrega: ${datos.fecha}")
                    }
                }
            }
        }
    }
}