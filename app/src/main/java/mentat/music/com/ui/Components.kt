package mentat.music.com.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import mentat.music.com.R
import mentat.music.com.model.Borrador
import java.time.Instant
import java.time.temporal.ChronoUnit

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TarjetaBorrador(
    borrador: Borrador,
    onArchivar: () -> Unit,
    onPublicar: (String) -> Unit,
    onCitar: (String) -> Unit,
    onRepost: () -> Unit,
    // onSeguir ELIMINADO
    onIgnorar: () -> Unit,
    onMover: () -> Unit,
    onRegenerar: () -> Unit,
    onLike: () -> Unit
) {
    val context = LocalContext.current
    var textState by remember(borrador.texto) { mutableStateOf(borrador.texto) }

    val partes = borrador.postOriginal.split("|")
    val autorHandle = partes.getOrNull(0)?.removePrefix("@") ?: "Desconocido"
    val postTexto = partes.getOrNull(1) ?: ""
    val postUrl = partes.getOrNull(2) ?: ""
    val avatarUrl = partes.getOrNull(3) ?: ""

    // CÁLCULO DEL TIEMPO (Nuevo)
    val tiempoHace = remember(borrador.fecha) { calcularTiempoHace(borrador.fecha) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // --- CABECERA ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. ICONO BSK (Izquierda) - Azul
                IconButton(
                    onClick = {
                        if (postUrl.isNotEmpty()) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(postUrl))
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_menu_social),
                        contentDescription = "Ir a Bluesky",
                        tint = Color.Unspecified
                    )
                }

                // 2. DATOS AUTOR + TIEMPO (Centro)
                Row(
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (avatarUrl.isNotEmpty()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // AQUI ESTÁ EL CAMBIO: Ponemos Nombre y Tiempo juntos
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "@$autorHandle",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false) // Para que no empuje demasiado
                            )

                            // Separador y Tiempo
                            if (tiempoHace.isNotEmpty()) {
                                Text(
                                    text = " • $tiempoHace",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.Gray,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // 3. CERRAR / ARCHIVAR (Derecha)
                IconButton(onClick = onArchivar, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Archivar", tint = Color.Gray)
                }
            }

            // --- CONTENIDO ORIGINAL ---
            Text(
                text = postTexto,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            )

            // --- EDITOR ---
            OutlinedTextField(
                value = textState,
                onValueChange = { textState = it },
                label = { Text("Respuesta Mentat") },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge,
                maxLines = 6
            )

            Spacer(modifier = Modifier.height(8.dp))

            // --- BARRA DE ACCIONES (ICONOS) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // GRUPO SOCIAL (Izquierda)
                Row {
                    // LIKE
                    IconButton(onClick = onLike) {
                        Icon(Icons.Outlined.FavoriteBorder, contentDescription = "Like", tint = Color.Red)
                    }
                    // REPOST (Flechas girando)
                    IconButton(onClick = onRepost) {
                        Icon(Icons.Default.Repeat, contentDescription = "Repost", tint = Color.Green)
                    }
                    // CITAR (Comillas)
                    IconButton(onClick = { onCitar(textState) }) {
                        Icon(Icons.Default.FormatQuote, contentDescription = "Citar", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                // GRUPO GESTIÓN (Derecha)
                Row {
                    // REGENERAR IA (Estrellitas Mágicas)
                    IconButton(onClick = onRegenerar) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Regenerar IA", tint = Color.Magenta)
                    }
                    // MOVER A BUZÓN (Caja de entrada)
                    IconButton(onClick = onMover) {
                        Icon(Icons.Default.Inbox, contentDescription = "Mover a Buzón", tint = Color.Gray)
                    }
                }
            }

            // --- BOTÓN ENVIAR ---
            Button(
                onClick = { onPublicar(textState) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Responder")
            }
        }
    }
}

// --- FUNCIÓN AUXILIAR PARA EL TIEMPO ---
@RequiresApi(Build.VERSION_CODES.O)
fun calcularTiempoHace(fechaIso: String): String {
    if (fechaIso.isEmpty()) return ""
    return try {
        val fechaPost = Instant.parse(fechaIso)
        val ahora = Instant.now()

        val segundos = ChronoUnit.SECONDS.between(fechaPost, ahora)
        val minutos = ChronoUnit.MINUTES.between(fechaPost, ahora)
        val horas = ChronoUnit.HOURS.between(fechaPost, ahora)
        val dias = ChronoUnit.DAYS.between(fechaPost, ahora)

        when {
            segundos < 60 -> "ahora"
            minutos < 60 -> "${minutos}m"
            horas < 24 -> "${horas}h"
            dias < 7 -> "${dias}d"
            else -> "+1sem"
        }
    } catch (e: Exception) {
        ""
    }
}