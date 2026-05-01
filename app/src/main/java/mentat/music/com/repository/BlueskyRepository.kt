package mentat.music.com.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import mentat.music.com.constans.AppConfig
import mentat.music.com.model.*
import mentat.music.com.network.RetrofitClient
import mentat.music.com.database.MentatDatabase
import mentat.music.com.database.RejectedPost
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class BlueskyRepository(private val context: Context? = null) {

    var jwtToken: String? = null
    val api = RetrofitClient.api

    // --- CONFIGURACIÓN DEL RELOJ (TIEMPO HUMANO) ---
    private val MINUTOS_MINIMOS = 5
    private val MINUTOS_MAXIMOS = 90

    // --- FUNCIÓN NUEVA: EL ENTERRADOR (MATA ZOMBIS) ---
    suspend fun matarPost(uri: String) {
        if (context == null) return
        try {
            val db = MentatDatabase.getDatabase(context)
            db.rejectedDao().insert(RejectedPost(uri = uri))
            Log.d("BLUESKY_REPO", "⚰️ Post enterrado: $uri")
        } catch (e: Exception) {
            Log.e("BLUESKY_REPO", "Error enterrando post", e)
        }
    }

    // --- FUNCIÓN NUEVA: ¿ES BUEN MOMENTO? (FILTRO DE TIEMPO) ---
    private fun esMomentoAdecuado(fechaIso: String): Boolean {
        return try {
            // Formato ISO 8601 que usa Bluesky (ej: 2023-10-27T10:00:00.000Z)
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")

            val fechaPost = sdf.parse(fechaIso) ?: return false
            val ahora = Date()

            val diferenciaMs = ahora.time - fechaPost.time
            val minutos = diferenciaMs / (1000 * 60)

            // La regla de oro: Entre 5 y 90 minutos
            val esValido = minutos in MINUTOS_MINIMOS..MINUTOS_MAXIMOS

            // Log para depurar (puedes borrarlo luego si hace mucho ruido)
            if (!esValido) {
                // Log.d("FILTRO_TIEMPO", "⏳ Descartado por tiempo: ${minutos}m (Post: $fechaIso)")
            }

            esValido
        } catch (e: Exception) {
            // Si falla el parseo de fecha, por seguridad lo dejamos pasar (o lo bloqueamos, según prefieras)
            true
        }
    }

    private fun obtenerFechaIso(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private suspend fun asegurarLogin(): Boolean {
        if (jwtToken != null) return true

        return try {
            Log.d("BLUESKY_REPO", "🔐 Iniciando sesión...")
            val loginResponse = api.login(
                LoginRequest(AppConfig.BLUESKY_HANDLE, AppConfig.BLUESKY_APP_PASSWORD)
            )
            jwtToken = "Bearer ${loginResponse.accessJwt}"
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("BLUESKY_REPO", "❌ Error Login: ${e.message}")
            false
        }
    }

    // --- LECTURA CON DOBLE FILTRO (ZOMBIS + TIEMPO) ---
    suspend fun traerTimeline(): List<FeedViewPost> {
        if (!asegurarLogin()) return emptyList()
        return try {
            // 1. Descargamos todo de Bluesky
            val postsCrudos = api.getTimeline(jwtToken!!, limit = 100).feed

            // 2. Preparamos la lista negra (si hay base de datos)
            var listaNegra: List<String> = emptyList()
            if (context != null) {
                val db = MentatDatabase.getDatabase(context)
                listaNegra = db.rejectedDao().getAllRejectedUris()
            }

            // 3. APLICAMOS LOS DOS FILTROS A LA VEZ
            val postsFiltrados = postsCrudos.filter { postView ->
                val uri = postView.post.uri
                val fecha = postView.post.indexedAt

                // A. ¿Está muerto?
                val esZombi = listaNegra.contains(uri)

                // B. ¿Es el momento adecuado? (Entre 5 y 90 min)
                val esHora = esMomentoAdecuado(fecha)

                // Solo pasa si NO es zombi Y SI es hora
                !esZombi && esHora
            }

            Log.d("BLUESKY_REPO", "🧹 Filtros: ${postsCrudos.size} total -> ${postsFiltrados.size} válidos (Zombis fuera + Rango 5-90min)")

            postsFiltrados

        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("BLUESKY_REPO", "Error timeline", e)
            emptyList()
        }
    }

    suspend fun traerInteracciones(): List<NotificationView> {
        if (!asegurarLogin()) return emptyList()
        return try {
            val response = api.listNotifications(jwtToken!!, limit = 100)
            response.notifications
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("BLUESKY_REPO", "❌ Error notificaciones", e)
            emptyList()
        }
    }

    // --- INVESTIGACIÓN Y UTILIDADES ---

    suspend fun traerTextoPost(uri: String): String {
        return try {
            val response = api.getPostThread(jwtToken!!, uri)
            val postObjetivo = response.thread?.post
            val recordMap = postObjetivo?.record as? Map<*, *>
            recordMap?.get("text")?.toString() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun traerImagenPost(uri: String): String {
        return try {
            val response = api.getPostThread("Bearer $jwtToken", uri)
            val post = response.thread?.post
            val embedMap = post?.embed as? Map<*, *>
            val thumb = embedMap?.get("thumb")?.toString() ?: ""
            thumb
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun traerPerfilCompleto(didOrHandle: String): ProfileViewDetailed? {
        if (!asegurarLogin()) return null
        return try {
            Log.d("DEBUG_INVESTIGATOR", "🔍 Investigando perfil de: $didOrHandle")
            api.getProfile(jwtToken!!, didOrHandle)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("DEBUG_INVESTIGATOR", "❌ Fallo al traer perfil", e)
            null
        }
    }

    suspend fun traerHistorialUsuario(didOrHandle: String): List<FeedViewPost> {
        if (!asegurarLogin()) return emptyList()
        return try {
            Log.d("DEBUG_INVESTIGATOR", "📜 Leyendo historial reciente de: $didOrHandle")
            api.getAuthorFeed(jwtToken!!, didOrHandle, limit = 10).feed
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("DEBUG_INVESTIGATOR", "❌ Fallo al traer historial", e)
            emptyList()
        }
    }

    // --- ACCIÓN ---
    suspend fun publicarRespuesta(texto: String, postOriginalUri: String, postOriginalCid: String): Boolean {
        if (!asegurarLogin()) return false

        return try {
            Log.d("BLUESKY_REPO", "🚀 Analizando hilo para responder...")
            val cleanToken = jwtToken!!.removePrefix("Bearer ")
            val session = api.getSession("Bearer $cleanToken")
            val myDid = session.did

            val parentRef = Reference(uri = postOriginalUri, cid = postOriginalCid)
            var rootRef = parentRef

            try {
                val response = api.getPosts("Bearer $cleanToken", listOf(postOriginalUri))
                if (response.isSuccessful && response.body() != null) {
                    val posts = response.body()!!["posts"] as? List<Map<String, Any>>
                    val postData = posts?.firstOrNull()
                    if (postData != null) {
                        val record = postData["record"] as? Map<String, Any>
                        val replyObj = record?.get("reply") as? Map<String, Any>
                        if (replyObj != null) {
                            val rootData = replyObj["root"] as? Map<String, Any>
                            val rootUri = rootData?.get("uri") as? String
                            val rootCid = rootData?.get("cid") as? String
                            if (rootUri != null && rootCid != null) {
                                rootRef = Reference(uri = rootUri, cid = rootCid)
                                Log.d("BLUESKY_REPO", "🔗 Hilo encontrado. Root: $rootUri")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("BLUESKY_REPO", "⚠️ Fallo al buscar root, usando método simple.", e)
            }

            val replyRef = ReplyRef(root = rootRef, parent = parentRef)
            val now = obtenerFechaIso()

            val request = CreateRecordRequest(
                repo = myDid,
                collection = "app.bsky.feed.post",
                record = PostStructure(text = texto, createdAt = now, reply = replyRef)
            )

            api.createRecord("Bearer $cleanToken", request)
            Log.d("BLUESKY_REPO", "✅ Respuesta publicada DENTRO del hilo.")
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("BLUESKY_REPO", "❌ Error al publicar respuesta", e)
            false
        }
    }

    suspend fun darLike(uriPost: String, cidPost: String): Boolean {
        if (!asegurarLogin()) return false
        return try {
            Log.d("BLUESKY_REPO", "❤️ Enviando Like...")
            val session = api.getSession("Bearer ${jwtToken!!.removePrefix("Bearer ")}")
            val myDid = session.did
            val now = obtenerFechaIso()

            val request = CreateRecordRequest(
                repo = myDid, collection = "app.bsky.feed.like",
                record = LikeRecord(subject = Reference(uri = uriPost, cid = cidPost), createdAt = now)
            )
            api.createRecord(jwtToken!!, request)
            Log.d("BLUESKY_REPO", "✅ ¡Like enviado!")
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("BLUESKY_REPO", "❌ Error al dar Like", e)
            false
        }
    }

    suspend fun crearPost(texto: String): Boolean {
        if (!asegurarLogin()) return false

        return try {
            Log.d("BLUESKY_REPO", "🚀 Enviando post nuevo...")
            val session = api.getSession("Bearer ${jwtToken!!.removePrefix("Bearer ")}")
            val myDid = session.did
            val now = obtenerFechaIso()

            val request = CreateRecordRequest(
                repo = myDid,
                collection = "app.bsky.feed.post",
                record = PostStructure(
                    text = texto,
                    createdAt = now,
                    reply = null
                )
            )

            api.createRecord(jwtToken!!, request)
            Log.d("BLUESKY_REPO", "✅ ¡Post enviado con éxito!")
            true

        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("BLUESKY_REPO", "❌ Error al crear post", e)
            false
        }
    }

    suspend fun publicarPostConImagenes(
        texto: String,
        bitmaps: List<Bitmap>,
        altText: String = "Imagen compartida desde Mentat"
    ): Boolean {
        if (!asegurarLogin()) return false
        if (bitmaps.isEmpty()) return crearPost(texto)

        return try {
            Log.d("BLUESKY_REPO", "📸 Iniciando subida de ${bitmaps.size} imágenes...")
            val cleanToken = jwtToken!!.removePrefix("Bearer ")
            val session = api.getSession("Bearer $cleanToken")
            val myDid = session.did
            val uploadedBlobs = mutableListOf<BlobRef>()

            for ((index, bitmap) in bitmaps.take(4).withIndex()) {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                val byteArray = stream.toByteArray()
                val mediaType = "image/jpeg".toMediaTypeOrNull()
                val requestBody = byteArray.toRequestBody(mediaType)
                val response = api.uploadBlob("Bearer $cleanToken", "image/jpeg", requestBody)
                uploadedBlobs.add(response.blob)
            }

            val imageAspects = uploadedBlobs.map { blobRef ->
                ImageAspect(image = blobRef, alt = altText)
            }
            val embedStruct = ImagesEmbed(images = imageAspects)
            val now = obtenerFechaIso()
            val postStructure = PostStructure(
                text = texto,
                createdAt = now,
                embed = embedStruct
            )
            val request = CreateRecordRequest(
                repo = myDid,
                collection = "app.bsky.feed.post",
                record = postStructure
            )
            api.createRecord("Bearer $cleanToken", request)
            Log.d("BLUESKY_REPO", "🎉 ¡Post con imágenes publicado con éxito!")
            true

        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e("BLUESKY_REPO", "❌ Error al publicar post con imágenes", e)
            false
        }
    }
    /**
     * Sube una galería de arte respetando el Alt Text individual de cada obra.
     * @param galeria Lista de parejas (Imagen Bitmap, Texto descriptivo individual).
     */
    suspend fun publicarGaleriaArte(
        texto: String,
        galeria: List<Pair<Bitmap, String>>
    ): Boolean {
        if (!asegurarLogin()) return false
        if (galeria.isEmpty()) return crearPost(texto)

        return try {
            Log.d("BLUESKY_REPO", "🎨 Iniciando subida de galería de arte (${galeria.size} obras)...")

            // 1. Preparación de sesión (Igual que tu función original)
            val cleanToken = jwtToken!!.removePrefix("Bearer ")
            val session = api.getSession("Bearer $cleanToken") // Necesario para obtener el DID si no lo tienes guardado
            val myDid = session.did

            val imagesForEmbed = mutableListOf<ImageAspect>()

            // 2. Iteramos sobre las PAREJAS (Bitmap + Texto)
            for ((bitmap, altSpecifico) in galeria.take(4)) {

                // A. Compresión y preparación del archivo
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                val byteArray = stream.toByteArray()
                val mediaType = "image/jpeg".toMediaTypeOrNull()
                val requestBody = byteArray.toRequestBody(mediaType)

                // B. Subida del Blob (Igual que antes)
                val response = api.uploadBlob("Bearer $cleanToken", "image/jpeg", requestBody)
                val blobRef = response.blob

                // C. AQUÍ ESTÁ EL CAMBIO CLAVE:
                // Creamos el 'ImageAspect' uniendo este Blob concreto con SU texto descriptivo
                imagesForEmbed.add(ImageAspect(image = blobRef, alt = altSpecifico))
            }

            // 3. Construcción del Embed y el Post (Usando tus clases existentes)
            val embedStruct = ImagesEmbed(images = imagesForEmbed)
            val now = obtenerFechaIso()

            val postStructure = PostStructure(
                text = texto,
                createdAt = now,
                embed = embedStruct
            )

            val request = CreateRecordRequest(
                repo = myDid,
                collection = "app.bsky.feed.post",
                record = postStructure
            )

            // 4. Envío final
            api.createRecord("Bearer $cleanToken", request)

            Log.d("BLUESKY_REPO", "✅ Galería de arte publicada con éxito.")
            true

        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e("BLUESKY_REPO", "❌ Error al publicar galería", e)
            false
        }
    }

    suspend fun crearRepost(uriOriginal: String, cidOriginal: String): Boolean {
        if (!asegurarLogin()) return false
        return try {
            Log.d("BLUESKY_REPO", "🔄 Enviando Repost...")
            val session = api.getSession("Bearer ${jwtToken!!.removePrefix("Bearer ")}")
            val myDid = session.did
            val now = obtenerFechaIso()

            val request = CreateRecordRequest(
                repo = myDid,
                collection = "app.bsky.feed.repost",
                record = RepostRecord(
                    subject = Reference(uri = uriOriginal, cid = cidOriginal),
                    createdAt = now
                )
            )
            api.createRecord(jwtToken!!, request)
            Log.d("BLUESKY_REPO", "✅ Repost realizado.")
            true
        } catch (e: Exception) {
            Log.e("BLUESKY_REPO", "❌ Error al repostear", e)
            false
        }
    }

    suspend fun publicarCita(texto: String, uriOriginal: String, cidOriginal: String): Boolean {
        if (!asegurarLogin()) return false
        return try {
            Log.d("BLUESKY_REPO", "💬 Publicando Cita (Quote Post)...")
            val session = api.getSession("Bearer ${jwtToken!!.removePrefix("Bearer ")}")
            val myDid = session.did
            val now = obtenerFechaIso()

            val embedRecord = EmbedRecord(
                record = Reference(uri = uriOriginal, cid = cidOriginal)
            )

            val postStructure = PostStructure(
                text = texto,
                createdAt = now,
                embed = embedRecord
            )

            val request = CreateRecordRequest(
                repo = myDid,
                collection = "app.bsky.feed.post",
                record = postStructure
            )
            api.createRecord(jwtToken!!, request)
            Log.d("BLUESKY_REPO", "✅ Cita publicada correctamente.")
            true
        } catch (e: Exception) {
            Log.e("BLUESKY_REPO", "❌ Error al publicar cita", e)
            false
        }
    }

    suspend fun seguirUsuario(didDestino: String): Boolean {
        if (!asegurarLogin()) return false
        return try {
            Log.d("BLUESKY_REPO", "➕ Siguiendo a usuario...")
            val session = api.getSession("Bearer ${jwtToken!!.removePrefix("Bearer ")}")
            val myDid = session.did
            val now = obtenerFechaIso()

            val request = CreateRecordRequest(
                repo = myDid,
                collection = "app.bsky.graph.follow",
                record = FollowRecord(
                    subject = didDestino,
                    createdAt = now
                )
            )
            api.createRecord(jwtToken!!, request)
            Log.d("BLUESKY_REPO", "✅ Ahora sigues a $didDestino")
            true
        } catch (e: Exception) {
            Log.e("BLUESKY_REPO", "❌ Error al seguir usuario", e)
            false
        }
    }

}