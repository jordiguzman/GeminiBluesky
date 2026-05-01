package mentat.music.com.viewmodel

// --- IMPORTACIONES DE ANDROID Y KOTLIN ---
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Tasks

// --- IMPORTACIONES DE FIREBASE ---
import com.google.firebase.firestore.FirebaseFirestore
import com.google.protobuf.LazyStringArrayList.emptyList

// --- IMPORTACIONES DE CORRUTINAS ---
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

// --- IMPORTACIONES DE TU PROYECTO ---
import mentat.music.com.ai.MentatBrain
import mentat.music.com.model.Borrador
import mentat.music.com.model.NotificationView
import mentat.music.com.network.ArtPost
import mentat.music.com.network.BuscadorObrasMaestras
import mentat.music.com.repository.BlueskyRepository
import mentat.music.com.repository.BuzonRepository
import kotlin.collections.emptyList

private const val COLECCION_BUZON = "data_buzon"
private const val COLECCION_CAZA = "data_caza"

class NotificationsViewModel(
    private val db: FirebaseFirestore,
    private val brain: MentatBrain, // Se mantiene en el constructor para no romper tu UI, pero ya no se usa aquí dentro
    private val context: Context
) : ViewModel() {

    // ESTADO UI
    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    // REPOSITORIOS
    private val blueskyRepo = BlueskyRepository()
    private val actionRepo = BuzonRepository(db)

    var mostrarGaleriaMaestra by mutableStateOf(false)

    init {
        escucharBuzonFirebase()
    }

    // --- 1. ESCUCHAR FIREBASE ---
    private fun escucharBuzonFirebase() {
        db.collection(COLECCION_BUZON)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    _uiState.update { it.copy(mensajeError = "Error Firebase: ${e.message}") }
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val todos = snapshot.documents.mapNotNull { doc -> doc.toObject(Borrador::class.java) }

                    val listaLimpia = todos
                        .filter { it.estado != "rechazado" && it.estado != "aprobado" }
                        .sortedByDescending { it.fecha }

                    _uiState.update { current ->
                        current.copy(
                            borradores = listaLimpia,
                            // (Si te da error en countConversacion, puedes borrar esa variable en tu clase NotificationsUiState)
                            countReacciones = listaLimpia.count { it.tipo in listOf("LIKE", "REPOST") },
                            countAudiencia = listaLimpia.count { it.tipo == "FOLLOW" }
                        )
                    }
                }
            }
    }

    // --- 2. DESCARGAR DE BLUESKY ---
    fun buscarNovedades(manual: Boolean = false, context: Context) {
        viewModelScope.launch {
            if (manual) _uiState.update { it.copy(isLoading = true) }
            Log.d("MVVM_NOTIFS", "🔔 Buscando novedades...")

            try {
                val notificaciones = withContext(Dispatchers.IO) { blueskyRepo.traerInteracciones() }

                if (notificaciones.isEmpty() && manual) {
                    mostrarMensaje("No hay novedades en Bluesky")
                } else {
                    withContext(Dispatchers.IO) {
                        procesarListaCruda(notificaciones)
                    }
                }

            } catch (e: Exception) {
                Log.e("MVVM_NOTIFS", "Error buscando", e)
                mostrarMensaje("Error de conexión: ${e.message}")
            } finally {
                if (manual) _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    // --- LÓGICA DE PROCESAMIENTO (AHORA SÓLO REACCIONES Y SEGUIDORES) ---
    private suspend fun procesarListaCruda(notificaciones: List<NotificationView>) {
        for (item in notificaciones.take(50)) {
            try {
                val motivo = item.reason

                // ⚡ EL CORTAFUEGOS: Ignoramos cualquier mención, respuesta o cita. No entran en la app.
                if (motivo in listOf("reply", "quote", "mention")) {
                    continue
                }

                val uri = item.uri
                val docId = uri.replace("/", "_").replace(":", "_")

                // Chequeos de existencia en Firebase
                val existeEnBuzon = withContext(Dispatchers.IO) {
                    try { Tasks.await(db.collection(COLECCION_BUZON).document(docId).get()).exists() } catch (e: Exception) { false }
                }
                if (existeEnBuzon) continue

                val existeEnCaza = withContext(Dispatchers.IO) {
                    try { Tasks.await(db.collection(COLECCION_CAZA).document(docId).get()).exists() } catch (e: Exception) { false }
                }
                if (existeEnCaza) continue

                // Datos básicos
                val autor = item.author
                val yaLeSigo = autor.viewer?.following != null

                var tipoInteraccion = "LIKE"
                when (motivo) {
                    "like" -> tipoInteraccion = "LIKE"
                    "repost" -> tipoInteraccion = "REPOST"
                    "follow" -> tipoInteraccion = "FOLLOW"
                }

                var textoTuPost = ""
                var respuestaIA = ""

                // Url genérica
                val partesUri = item.uri.split("/")
                val didPropietario = partesUri.getOrNull(2) ?: autor.handle
                val idPost = partesUri.lastOrNull() ?: ""
                val urlWeb = "https://bsky.app/profile/$didPropietario/post/$idPost"

                // --- LÓGICA SIMPLIFICADA ---
                if (tipoInteraccion == "FOLLOW") {
                    respuestaIA = if (yaLeSigo) "✅ AMIGO" else "👤 NUEVO"
                } else {
                    val uriTarget = item.reasonSubject
                    if (uriTarget != null) {
                        textoTuPost = blueskyRepo.traerTextoPost(uriTarget)
                    }
                }

                // Preparación visual
                val textoVisual = when (tipoInteraccion) {
                    "LIKE", "REPOST" -> textoTuPost.ifEmpty { "Tu publicación" }
                    "FOLLOW" -> if(yaLeSigo) "Te ha seguido (Y tú ya le sigues)" else "¡Nuevo seguidor!"
                    else -> ""
                }

                val avatar = autor.avatar ?: ""
                val postFormateado = "@${autor.handle}|$textoVisual|$urlWeb|$avatar"

                val borrador = Borrador(
                    id = docId,
                    opcion = (1..99999).random(),
                    tipo = tipoInteraccion,
                    texto = respuestaIA,
                    postOriginal = postFormateado,
                    fecha = item.indexedAt,
                    uriOriginal = item.uri,
                    cidOriginal = item.cid,
                    imagenUrl = "" // Ya no extraemos imágenes de menciones
                )

                db.collection(COLECCION_BUZON).document(docId).set(borrador)
                delay(if (motivo == "like") 20L else 500L)

            } catch (e: Exception) { Log.e("MVVM_NOTIFS", "Error procesando item", e) }
        }
    }

    // --- 4. ACCIONES (USER INTENTS) ---

    fun archivar(borrador: Borrador) {
        actionRepo.archivarBorrador(COLECCION_BUZON, borrador.id) { mostrarMensaje("🗑️ Archivado") }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun enviarRespuesta(borrador: Borrador, texto: String) {
        viewModelScope.launch {
            // 1. Enviamos a la red (Bluesky/Firebase)
            actionRepo.aprobarBorrador(COLECCION_BUZON, borrador, texto) { mostrarMensaje("🚀 Enviado") }
            // (Memoria local eliminada)
        }
    }

    fun citar(borrador: Borrador, texto: String) {
        viewModelScope.launch {
            actionRepo.publicarCita(COLECCION_BUZON, borrador, texto) { mostrarMensaje("📣 Cita publicada") }
            // (Memoria local eliminada)
        }
    }

    fun repostear(borrador: Borrador) {
        viewModelScope.launch {
            actionRepo.repostear(COLECCION_BUZON, borrador) { mostrarMensaje("🔄 Repost enviado") }
        }
    }

    fun seguir(borrador: Borrador) {
        viewModelScope.launch {
            actionRepo.seguirUsuario(COLECCION_BUZON, borrador) { mostrarMensaje("➕ Siguiendo usuario") }
        }
    }

    fun moverACaza(borrador: Borrador) {
        actionRepo.moverBorrador(borrador, COLECCION_BUZON, COLECCION_CAZA) { mostrarMensaje("🔄 Movido a Caza") }
    }

    // Helpers UI
    fun mensajeMostrado() {
        _uiState.update { it.copy(mensajeInfo = null, mensajeError = null) }
    }

    private fun mostrarMensaje(msg: String) {
        _uiState.update { it.copy(mensajeInfo = msg) }
    }

    // JSONL Galería (Se mantiene intacto)
    var artPostJsonl by mutableStateOf<ArtPost?>(null)
        private set

    var cargandoJsonl by mutableStateOf(false)
        private set

    fun buscarMasDelMismoArtista(artista: String, obraExcluida: String) {
        viewModelScope.launch {
            cargandoJsonl = true

            // 1. Recopilamos los títulos que ya estás viendo usando el campo correcto: obras
            val titulosVistos = artPostJsonl?.obras?.map { it.titulo } ?: emptyList()

            // 2. Llamamos al motor con la lista de bloqueados
            val nuevasObras = BuscadorObrasMaestras.buscarMasDelMismoArtista(
                artistaNombre = artista,
                obraProhibida = obraExcluida,
                titulosYaVistos = titulosVistos
            )

            // 3. Si hay obras nuevas, actualizamos la pantalla. Si no, avisamos.
            if (nuevasObras != null) {
                artPostJsonl = nuevasObras
            } else {
                mostrarMensaje("El museo no tiene más obras de este artista.")
            }

            cargandoJsonl = false
        }
    }

    var artPostMaestro by mutableStateOf<ArtPost?>(null)
        private set

    var cargandoMaestro by mutableStateOf(false)
        private set

    fun iniciarBusquedaObrasMaestras(context: Context) {
        viewModelScope.launch {
            cargandoMaestro = true
            val resultado = BuscadorObrasMaestras.buscarTesoro(context)

            if (resultado != null) {
                artPostMaestro = resultado
                mostrarGaleriaMaestra = true
            }
            cargandoMaestro = false
        }
    }
}