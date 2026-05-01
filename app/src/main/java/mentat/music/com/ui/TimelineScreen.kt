package mentat.music.com.ui

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.* // Importamos todo layout para statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import mentat.music.com.ai.MentatBrain
import mentat.music.com.ai.detectarIdioma
import mentat.music.com.constans.AppConfig
import mentat.music.com.database.MentatDatabase
import mentat.music.com.database.PostEntity
import mentat.music.com.enrichment.ContentEnricher
import mentat.music.com.model.MemoriaConversacion
import mentat.music.com.network.ThreadExpeditor
import mentat.music.com.repository.BlueskyRepository
import mentat.music.com.repository.BuzonRepository
import mentat.music.com.utils.ImageUtils
import mentat.music.com.utils.simpleVerticalScrollbar

private const val COLECCION_CAZA = "data_caza"

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    db: FirebaseFirestore,
    model: GenerativeModel,
    database: MentatDatabase
) {
    val context = LocalContext.current
    val brain = remember { MentatBrain(context, model) }

    // LEER DE ROOM
    val postsEntities by database.postDao().obtenerPendientes().collectAsState(initial = emptyList())
    val borradoresCaza = remember(postsEntities) { postsEntities.map { it.toBorrador() } }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val actionRepo = remember { BuzonRepository(db) }
    val bskyRepoForInvestigator = remember { BlueskyRepository() }
    var isRefreshing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var snackbarJob by remember { mutableStateOf<Job?>(null) }

    val threadExpeditor = remember {
        ThreadExpeditor(
            repository = bskyRepoForInvestigator,
            db = db,
            brain = brain
        )
    }

    val idsProcesadosLocal = remember { mutableSetOf<String>() }
    val prefs = remember { context.getSharedPreferences("mentat_config", android.content.Context.MODE_PRIVATE) }
    var delayVariable by remember { mutableLongStateOf(prefs.getLong("saved_delay", 4000L)) }
    var cantidadVariable by remember { mutableIntStateOf(prefs.getInt("saved_cantidad", 30)) }
    var mostrarAjustes by remember { mutableStateOf(false) }

    fun mostrarSnack(mensaje: String) {
        snackbarJob?.cancel()
        snackbarJob = scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message = mensaje, duration = SnackbarDuration.Short, withDismissAction = true)
        }
    }

    suspend fun procesarInteraccionIA(
        autorHandle: String,
        textoBase: String,
        imagenUrl: String,
        contextoSistema: String = "",
        idioma: String = "auto"
    ): String {
        return withContext(Dispatchers.IO) {
            var bitmapIA: Bitmap? = null
            if (imagenUrl.isNotEmpty()) {
                bitmapIA = ImageUtils.descargarImagen(context, imagenUrl)
            }

            val datosExtra = ContentEnricher.analizarContenido(textoBase)
            var contextoPerfilLocal = ""
            try {
                val query = db.collection("user_profiles")
                    .whereEqualTo("handle", autorHandle)
                    .limit(1)
                    .get()
                    .await()
                if (!query.isEmpty) {
                    contextoPerfilLocal = query.documents[0].getString("analisisIA") ?: ""
                }
            } catch (e: Exception) {
                Log.w("MENTAT_PROFILE", "Error buscando perfil: ${e.message}")
            }

            val textoConContexto = buildString {
                append(textoBase)
                if (datosExtra.isNotEmpty()) append("\n\n$datosExtra")
                if (contextoSistema.isNotEmpty()) append("\n\n$contextoSistema")
                if (contextoPerfilLocal.isNotEmpty()) {
                    append("\n\n[INTERNAL DATA - AUTHOR PROFILE]:\n$contextoPerfilLocal")
                }
            }

            val rawResponse = brain.evaluarCaza(
                autor = autorHandle,
                textoPost = textoConContexto,
                perfilContexto = contextoPerfilLocal,
                imagen = bitmapIA,
                idioma = idioma
            )
            formatearRespuestaIA(rawResponse)
        }
    }

    suspend fun escanearTimeline(manual: Boolean = false) {
        if (manual) isRefreshing = true
        Log.d("DEBUG_MENTAT", "🔄 Escaneando Timeline con ROOM...")
        try {
            val posts = bskyRepoForInvestigator.traerTimeline()
            if (manual) isRefreshing = false

            if (posts.isNotEmpty()) {
                for (item in posts.take(cantidadVariable)) {
                    try {
                        val post = item.post
                        val docId = post.uri.replace("/", "_").replace(":", "_")
                        if (idsProcesadosLocal.contains(docId)) continue
                        if (database.postDao().existe(docId)) {
                            idsProcesadosLocal.add(docId)
                            continue
                        }

                        val autor = post.author
                        if (autor.handle == AppConfig.BLUESKY_HANDLE) continue
                        if (autor.viewer?.following == null) {
                            idsProcesadosLocal.add(docId)
                            continue
                        }

                        val esReply = item.reply != null
                        val rootUri = item.reply?.root?.uri
                        if (esReply && rootUri != null) {
                            scope.launch { threadExpeditor.procesarHilo(rootUri, item.post.uri, item.post.cid) }
                            idsProcesadosLocal.add(docId)
                            continue
                        }

                        val rawRecord = post.record.toString()
                        val rawEmbed = post.embed?.toString() ?: ""
                        val todoJunto = "$rawRecord $rawEmbed"
                        val textoLimpio = limpiarTextoCaza(rawRecord)
                        val imagenEncontrada = extraerImagenCaza(todoJunto)
                        val urlReal = extraerMejorUrl(rawRecord, rawEmbed)

                        if (imagenEncontrada.isEmpty() && urlReal.isEmpty() && textoLimpio.length < 40 && !esReply) {
                            idsProcesadosLocal.add(docId)
                            continue
                        }

                        val textoParaIA = buildString {
                            append(textoLimpio)
                            if (urlReal.isNotEmpty()) append("\n$urlReal")
                        }

                        val resultadoIA = procesarInteraccionIA(
                            autorHandle = autor.handle,
                            textoBase = textoParaIA,
                            imagenUrl = imagenEncontrada,
                            idioma = detectarIdioma(textoLimpio)
                        )

                        if (resultadoIA.contains("SKIP", ignoreCase = true)) {
                            idsProcesadosLocal.add(docId)
                        } else {
                            val urlWeb = "https://bsky.app/profile/${autor.handle}/post/${post.uri.substringAfterLast("/")}"
                            val avatarUser = autor.avatar ?: ""

                            val entidad = PostEntity(
                                id = docId,
                                tipo = "CAZA_POST",
                                textoIA = resultadoIA,
                                postOriginal = "@${autor.handle}|$textoLimpio|$urlWeb|$avatarUser",
                                fecha = post.indexedAt,
                                uriOriginal = post.uri,
                                cidOriginal = post.cid,
                                imagenUrl = imagenEncontrada,
                                estado = "pendiente"
                            )
                            database.postDao().insertar(entidad)
                            val borradorFirebase = entidad.toBorrador()
                            val docRef = db.collection(COLECCION_CAZA).document(docId)
                            docRef.get().addOnSuccessListener { documento ->
                                if (!documento.exists()) docRef.set(borradorFirebase)
                            }
                            idsProcesadosLocal.add(docId)
                        }
                        if (idsProcesadosLocal.size > 500) idsProcesadosLocal.clear()
                        delay(delayVariable)
                    } catch (e: Exception) { Log.e("DEBUG_MENTAT", "Error procesando item", e) }
                }
            }
        } catch (e: Exception) { Log.e("DEBUG_MENTAT", "Error general", e) }
        finally { if (manual) isRefreshing = false }
    }

    LaunchedEffect(Unit) {
        while (true) {
            escanearTimeline(manual = false)
            delay(25 * 60 * 1000)
        }
    }

    // --- AQUÍ EMPIEZA LA CAJA CONTENEDORA GLOBAL ---
    Box(modifier = Modifier.fillMaxSize()) {

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Timeline", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    actions = {
                        IconButton(onClick = { mostrarAjustes = true }) {
                            Icon(androidx.compose.material.icons.Icons.Default.Settings, contentDescription = "Ajustes", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { scope.launch { escanearTimeline(manual = true) } },
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (borradoresCaza.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Escaneando horizonte (DB vacía)...", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().simpleVerticalScrollbar(listState),
                            contentPadding = PaddingValues(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 100.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(borradoresCaza, key = { it.id }) { borrador ->
                                val esHilo = borrador.tipo == "CAZA_HILO"
                                val visualData = if (esHilo) {
                                    val titulo = borrador.autorPost ?: "🧵 Hilo de conversación"
                                    val contenido = borrador.postOriginal
                                    val rkey = borrador.uriOriginal.substringAfterLast("/")
                                    val did = borrador.uriOriginal.substringAfter("at://").substringBefore("/")
                                    val urlWeb = "https://bsky.app/profile/$did/post/$rkey"
                                    "$titulo|$contenido|$urlWeb"
                                } else {
                                    borrador.postOriginal
                                }

                                TarjetaBorrador(
                                    borrador = borrador.copy(postOriginal = visualData),
                                    onArchivar = {
                                        scope.launch {
                                            database.postDao().borrar(borrador.id)
                                            actionRepo.archivarBorrador(COLECCION_CAZA, borrador.id) { mostrarSnack("🗑️ Archivado") }
                                        }
                                    },
                                    onPublicar = { textoFinal ->
                                        scope.launch {
                                            actionRepo.aprobarBorrador(COLECCION_CAZA, borrador, textoFinal) { mostrarSnack("🚀 Respuesta enviada") }
                                            try {
                                                database.memoriaDao().guardar(MemoriaConversacion(borrador.uriOriginal, textoFinal))
                                            } catch (e: Exception) {}
                                            database.postDao().borrar(borrador.id)
                                        }
                                    },
                                    onCitar = { textoCita ->
                                        scope.launch {
                                            val exito = bskyRepoForInvestigator.publicarCita(textoCita, borrador.uriOriginal, borrador.cidOriginal)
                                            if (exito) {
                                                mostrarSnack("💬 Cita publicada")
                                                try {
                                                    database.memoriaDao().guardar(MemoriaConversacion(borrador.uriOriginal, textoCita))
                                                } catch (e: Exception) {}
                                                database.postDao().borrar(borrador.id)
                                                db.collection(COLECCION_CAZA).document(borrador.id).delete()
                                            } else { mostrarSnack("❌ Error al citar") }
                                        }
                                    },
                                    onRepost = {
                                        scope.launch {
                                            val exito = bskyRepoForInvestigator.crearRepost(borrador.uriOriginal, borrador.cidOriginal)
                                            if (exito) mostrarSnack("🔄 Repost enviado") else mostrarSnack("❌ Error al hacer Repost")
                                        }
                                    },
                                    onIgnorar = {
                                        scope.launch {
                                            database.postDao().borrar(borrador.id)
                                            actionRepo.archivarBorrador(COLECCION_CAZA, borrador.id) { mostrarSnack("Ignorado") }
                                        }
                                    },
                                    onMover = {
                                        scope.launch {
                                            db.collection("data_buzon").document(borrador.id).set(borrador)
                                                .addOnSuccessListener {
                                                    scope.launch {
                                                        db.collection(COLECCION_CAZA).document(borrador.id).delete()
                                                        database.postDao().borrar(borrador.id)
                                                        mostrarSnack("📦 Movido a Buzón")
                                                    }
                                                }
                                        }
                                    },
                                    onRegenerar = {
                                        scope.launch {
                                            mostrarSnack("🔄 Regenerando...")
                                            val partes = visualData.split("|")
                                            val autor = partes.getOrNull(0)?.removePrefix("@") ?: "Usuario"
                                            val textoOriginal = partes.getOrNull(1) ?: ""
                                            val avatarRegen = partes.getOrNull(3) ?: ""

                                            val nuevoTexto = procesarInteraccionIA(autor, textoOriginal, borrador.imagenUrl, "[SYSTEM: FORCED MODE. Reply creatively.]")
                                            val nuevoPostOriginal = partes.take(3).joinToString("|") + "|$avatarRegen"

                                            val entidadActualizada = PostEntity(
                                                id = borrador.id, tipo = borrador.tipo, textoIA = nuevoTexto, postOriginal = nuevoPostOriginal,
                                                fecha = borrador.fecha, uriOriginal = borrador.uriOriginal, cidOriginal = borrador.cidOriginal,
                                                imagenUrl = borrador.imagenUrl, estado = "pendiente"
                                            )
                                            database.postDao().insertar(entidadActualizada)
                                            db.collection(COLECCION_CAZA).document(borrador.id).update("texto", nuevoTexto)
                                        }
                                    },
                                    onLike = {
                                        scope.launch {
                                            if (actionRepo.enviarLike(borrador.uriOriginal, borrador.cidOriginal)) mostrarSnack("❤️ Like enviado") else mostrarSnack("❌ Error al dar Like")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- SNACKBAR FLOTANTE (FUERA DEL SCAFFOLD, SOBRE TODO) ---
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter) // Arriba del todo
                .statusBarsPadding()        // Respeta la barra de estado (batería, hora)
                .padding(top = 8.dp)        // Un poquito de margen
        )
    } // FIN BOX GLOBAL

    if (mostrarAjustes) {
        DialogoVelocidad(
            velocidadActual = delayVariable,
            cantidadActual = cantidadVariable,
            onDismiss = { mostrarAjustes = false },
            onGuardar = { nuevoDelay, nuevaCantidad ->
                delayVariable = nuevoDelay
                cantidadVariable = nuevaCantidad
                prefs.edit().apply {
                    putLong("saved_delay", nuevoDelay)
                    putInt("saved_cantidad", nuevaCantidad)
                    apply()
                }
                mostrarAjustes = false
                mostrarSnack("✅ Guardado: $nuevaCantidad posts / $nuevoDelay ms")
            }
        )
    }
}

// Helpers
private fun limpiarTextoCaza(raw: String): String {
    try {
        if (!raw.contains("text=")) return ""
        val regex = """text=(?s)(.*?)(?:, [a-zA-Z]+=|\})""".toRegex()
        return regex.find(raw)?.groupValues?.get(1) ?: ""
    } catch (e: Exception) { return raw }
}

private fun extraerImagenCaza(raw: String): String {
    try {
        val regex = """(thumb|fullsize)=(https?://[^,\}\s]+)""".toRegex()
        return regex.find(raw)?.groupValues?.get(2) ?: ""
    } catch (e: Exception) { return "" }
}

private fun extraerMejorUrl(rawRecord: String, rawEmbed: String): String {
    try {
        val embedUrlRegex = """uri=(https?://[^,\}\s]+)""".toRegex()
        val matchEmbed = embedUrlRegex.find(rawEmbed)
        if (matchEmbed != null) {
            val url = matchEmbed.groupValues[1]
            if (!url.contains("avatar") && !url.contains("banner")) return url
        }
    } catch (e: Exception) {}
    try {
        val textUrlRegex = """(https?://[^\s]+)""".toRegex()
        val matchText = textUrlRegex.find(rawRecord)
        if (matchText != null) return matchText.groupValues[1].removeSuffix("...")
    } catch (e: Exception) {}
    return ""
}

private fun formatearRespuestaIA(texto: String): String {
    if (texto.isBlank()) return ""
    var t = texto.trim().removeSurrounding("\"")
    return t.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}