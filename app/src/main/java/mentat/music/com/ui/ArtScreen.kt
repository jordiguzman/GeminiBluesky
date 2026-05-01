package mentat.music.com.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import mentat.music.com.network.ArtPost
import mentat.music.com.network.BlogScraper
import mentat.music.com.network.ObraVisual // ⚡ Importante: Importamos la nueva clase
import mentat.music.com.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtScreen(
    onClose: () -> Unit,
    // ⚡ CAMBIO 1: Ahora pasamos la lista de objetos ObraVisual (que tiene URL + Título)
    onPublicar: (String, List<ObraVisual>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cargando by remember { mutableStateOf(true) }
    var postActual by remember { mutableStateOf<ArtPost?>(null) }
    var textoEditable by remember { mutableStateOf("") }

    fun cargarNuevo() {
        scope.launch {
            cargando = true
            postActual = BlogScraper.buscarTesoro(context)
            postActual?.let {
                // ⚡ CAMBIO 2: Usamos 'textoPost' que ya viene limpio desde el Scraper
                textoEditable = it.textoPost
                    .replace("&#8211;", "-")
                    .replace("&#038;", "&")
            }
            cargando = false
        }
    }

    LaunchedEffect(Unit) { cargarNuevo() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Museo Random", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (cargando) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Purple40)
            } else if (postActual == null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No se encontró arte...", color = Color.Gray)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { cargarNuevo() }, colors = ButtonDefaults.buttonColors(containerColor = Purple40)) {
                        Text("Reintentar")
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ⚡ CAMBIO 3: Iteramos sobre objetos 'obra', no strings
                    postActual!!.obras.forEach { obra ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(obra.url) // Sacamos la URL del objeto
                                .crossfade(true)
                                .build(),
                            // ⚡ CAMBIO 4: Aquí ponemos el título individual de ESTE cuadro
                            contentDescription = obra.titulo,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(350.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF111111))
                        )
                    }

                    OutlinedTextField(
                        value = textoEditable,
                        onValueChange = { textoEditable = it },
                        label = { Text("Título / Comentario") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF1A1A1A), unfocusedContainerColor = Color(0xFF1A1A1A),
                            focusedBorderColor = Purple40, unfocusedBorderColor = Color.Gray
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 90.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { cargarNuevo() },
                            modifier = Modifier.weight(0.4f).height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color.Gray)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Otro")
                        }

                        Button(
                            // ⚡ CAMBIO 5: Enviamos la lista completa de obras (con sus títulos)
                            onClick = {
                                onPublicar(textoEditable, postActual!!.obras)
                            },
                            modifier = Modifier.weight(0.6f).height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AzulMentat)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Publicar")
                        }
                    }
                }
            }
        }
    }
}