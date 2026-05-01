package mentat.music.com.ui

import android.util.Log
import android.widget.Toast
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import mentat.music.com.network.BuscadorWikimedia
import mentat.music.com.network.ObraVisual
import mentat.music.com.network.PostWikimedia
import mentat.music.com.ui.theme.*
import coil.ImageLoader
import okhttp3.OkHttpClient
import okhttp3.Dispatcher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtMaestroScreen(
    onClose: () -> Unit,
    onPublicar: (String, List<ObraVisual>) -> Unit
) {
    val context = LocalContext.current

    // ⚡ EL CORTAFUEGOS ANTIBANEOS:
    // Obligamos a Coil a hacer las peticiones de 1 en 1 para no saturar a Wikipedia
    val imageLoaderSecuencial = remember {
        ImageLoader.Builder(context)
            .okHttpClient {
                OkHttpClient.Builder()
                    .dispatcher(Dispatcher().apply {
                        maxRequestsPerHost = 1 // Peticiones en fila india
                    })
                    .build()
            }
            .build()
    }

    val scope = rememberCoroutineScope()
    var cargando by remember { mutableStateOf(true) }
    var postActual by remember { mutableStateOf<PostWikimedia?>(null) }
    var textoEditable by remember { mutableStateOf("") }

    fun cargarNuevo() {
        scope.launch {
            cargando = true
            postActual = BuscadorWikimedia.buscarTesoro(context)
            postActual?.let {
                // Quitamos el tag de Wikimedia aquí también
                textoEditable = it.textoPost
                    .replace("\n\n#Arte #Pintura #Wikimedia", "")
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
                title = { Text("Obras Maestras", color = Color.White, fontWeight = FontWeight.Bold) },
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
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = AzulMentat)
            } else if (postActual == null) {
                Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No quedan más obras maestras...", color = Color.Gray)
                    Button(onClick = { cargarNuevo() }, colors = ButtonDefaults.buttonColors(containerColor = AzulMentat)) {
                        Text("Reintentar")
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // ⚡ AQUÍ ESTABA EL ERROR: Bucle único y limpio
                    postActual!!.obras.forEach { obra ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(obra.url)
                                // ⚡ IMPORTANTE: Cambia "tu@email.com" por tu correo real. Wikipedia lo exige.
                                .addHeader("User-Agent", "MentatArtApp/2.0 (jordiguz@gmail.com)")
                                .crossfade(true)
                                .build(),
                            imageLoader = imageLoaderSecuencial,
                            contentDescription = obra.titulo,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(350.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF111111)),
                            onSuccess = {
                                Log.d("COIL_DEBUG", "✅ Imagen cargada: ${obra.url}")
                            },
                            onError = { error ->
                                Log.e("COIL_DEBUG", "❌ Error al cargar: ${obra.url}")
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    OutlinedTextField(
                        value = textoEditable,
                        onValueChange = { textoEditable = it },
                        label = { Text("Título / Comentario") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF1A1A1A), unfocusedContainerColor = Color(0xFF1A1A1A),
                            focusedBorderColor = AzulMentat, unfocusedBorderColor = Color.Gray
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(30.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 90.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    cargando = true
                                    postActual?.let { post ->
                                        val titulosEnPantalla = post.obras.map { it.titulo }
                                        val nuevas = BuscadorWikimedia.buscarMasDelMismo(post.artista, titulosEnPantalla)

                                        if (nuevas != null) {
                                            postActual = nuevas
                                            textoEditable = nuevas.textoPost.replace("\n\n#Arte #Pintura #Wikimedia", "")
                                        } else {
                                            Toast.makeText(context, "El museo no tiene más obras distintas de este artista", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    cargando = false
                                }
                            },
                            modifier = Modifier.weight(0.3f).height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.Gray)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                        }

                        Button(
                            onClick = { cargarNuevo() },
                            modifier = Modifier.weight(0.3f).height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                        ) {
                            Icon(Icons.Default.ArrowBack, modifier = Modifier.graphicsLayer(rotationZ = 180f), contentDescription = null)
                        }

                        Button(
                            onClick = {
                                val obrasAdaptadas = postActual!!.obras.map {
                                    ObraVisual(titulo = it.titulo, url = it.url)
                                }
                                onPublicar(textoEditable, obrasAdaptadas)
                            },
                            modifier = Modifier.weight(0.4f).height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AzulMentat)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Text("Ok", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        }
    }
}