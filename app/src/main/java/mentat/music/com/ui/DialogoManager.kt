package mentat.music.com.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DialogoVelocidad(
    velocidadActual: Long,    // Ejemplo: 4000
    cantidadActual: Int,      // Ejemplo: 30
    onDismiss: () -> Unit,
    onGuardar: (Long, Int) -> Unit
) {
    // ESTADO LOCAL: Usamos Float para que el Slider funcione suave
    var sliderDelay by remember { mutableFloatStateOf(velocidadActual.toFloat()) }
    var sliderCantidad by remember { mutableFloatStateOf(cantidadActual.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⚙️ Configurar Motor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(25.dp)) {

                // --- BARRA 1: DELAY (Saltos de 100 en 100) ---
                Column {
                    // El número se ve GRANDE y CLARO
                    Text(
                        text = "⏱️ Espera: ${sliderDelay.toLong()} ms",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = sliderDelay,
                        onValueChange = { sliderDelay = it },
                        valueRange = 1000f..10000f, // De 1 seg a 10 seg
                        // (10000 - 1000) / 100 = 90 tramos. Restamos 1 = 89 steps.
                        // Esto obliga a la barra a saltar de 100 en 100 exactos.
                        steps = 89
                    )
                }

                // --- BARRA 2: CANTIDAD (Saltos de 1 en 1) ---
                Column {
                    // El número se ve GRANDE y CLARO
                    Text(
                        text = "📦 Cantidad: ${sliderCantidad.toInt()} posts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = sliderCantidad,
                        onValueChange = { sliderCantidad = it },
                        valueRange = 1f..100f, // De 1 a 100 posts
                        // (100 - 1) / 1 = 99 tramos. Restamos 1 = 98 steps.
                        // Esto obliga a la barra a saltar de 1 en 1.
                        steps = 98
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                // Al guardar, enviamos los datos limpios (Long e Int)
                onGuardar(sliderDelay.toLong(), sliderCantidad.toInt())
            }) {
                Text("Guardar y Aplicar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}