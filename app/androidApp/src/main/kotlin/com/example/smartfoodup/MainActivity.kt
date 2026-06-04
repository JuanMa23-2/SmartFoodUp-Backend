package com.example.smartfoodup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartfoodup.SmartFoodApiService
import com.example.smartfoodup.SmartFoodCache
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ServerStatusScreen()
                }
            }
        }
    }
}

@Composable
fun ServerStatusScreen() {
    // Instanciamos el servicio compartido que creaste (Fase 1 del diagrama)
    val apiService = remember { SmartFoodApiService() }
    val scope = rememberCoroutineScope()

    // Estado de la vista conectado directamente al almacenamiento local caché (Fase 8 del diagrama)
    var screenText by remember { mutableStateOf(SmartFoodCache.lastServerResponse) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "SmartFoodUp Móvil 🍏",
            fontSize = 28.sp,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Prueba de Endpoint & Comunicación KMP",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // BOTÓN INTERACTIVO: Dispara el flujo asíncrono no bloqueante
        Button(
            onClick = {
                isLoading = true
                // Disparamos la corrutina asíncrona (Fase 2 del diagrama)
                scope.launch {
                    val response = apiService.checkServerStatus() // Hace el GET al backend local
                    SmartFoodCache.lastServerResponse = response  // Almacenamiento local (Fase 8)
                    screenText = response                        // Actualiza la interfaz gráfica
                    isLoading = false
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
            } else {
                Text("Probar Conexión con Backend")
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // RECUADRO PARA DESPLEGAR LOS DATOS RECIBIDOS
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Resultado del Servidor:",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = screenText,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Start,
                    lineHeight = 22.sp
                )
            }
        }
    }
}