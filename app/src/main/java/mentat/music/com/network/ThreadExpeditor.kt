package mentat.music.com.network

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import mentat.music.com.ai.MentatBrain
import mentat.music.com.repository.BlueskyRepository
import mentat.music.com.model.Borrador // Asegúrate de tener el import

class ThreadExpeditor(private val repository: BlueskyRepository,
                      private val db: FirebaseFirestore,
                      private val brain: MentatBrain) {

    private val COLECCION_CAZA = "data_caza"
    private val hilosEnProceso = mutableSetOf<String>()

    suspend fun procesarHilo(rootUri: String, postCazadoUri: String, postCazadoCid: String) {
        if (hilosEnProceso.contains(rootUri)) return
        hilosEnProceso.add(rootUri)

        try {
            val cronologia = recolectarCronologia(postCazadoUri, 6)
            if (cronologia.isEmpty()) return

            val cronologiaOrdenada = cronologia.reversed()

            // 👇 CAMBIO AQUÍ: Usamos una línea separadora elegante y saltos de línea
            val textoCompletoDelHilo = cronologiaOrdenada.joinToString("\n\n──────────────────────────────\n\n")

            val resultadoIA = brain.evaluarCaza(
                autor = "Varios",
                textoPost = textoCompletoDelHilo,
                perfilContexto = "Conversación en hilo de Bluesky",
                imagen = null,
                idioma = "auto"
            )

            if (resultadoIA == "SKIP") return

            val docId = "HILO_" + rootUri.replace("/", "_").replace(":", "_")
            val primerAutor = cronologiaOrdenada.firstOrNull()?.substringBefore(":") ?: "@usuario"

            val datosHILO = mapOf(
                "id" to docId,
                "tipo" to "CAZA_HILO",
                "texto" to resultadoIA,
                "autorPost" to "🧵 Hilo de $primerAutor y otros",
                "postOriginal" to textoCompletoDelHilo,
                "estado" to "pendiente",
                "uriOriginal" to postCazadoUri,
                "cidOriginal" to postCazadoCid,
                "fecha" to System.currentTimeMillis().toString()
            )

            db.collection(COLECCION_CAZA).document(docId).set(datosHILO)
                .addOnSuccessListener {
                    Log.d("THREAD_EXPEDITOR", "✅ Hilo guardado: $docId")
                }

        } catch (e: Exception) {
            Log.e("THREAD_EXPEDITOR", "❌ Error: ${e.message}")
        } finally {
            hilosEnProceso.remove(rootUri)
        }
    }
    private suspend fun recolectarCronologia(inicioUri: String, maxNiveles: Int): List<String> {
        val resultado = mutableListOf<String>()
        var uriActual: String? = inicioUri
        var nivel = 0

        while (uriActual != null && nivel < maxNiveles) {
            try {
                val tokenBase = repository.jwtToken?.removePrefix("Bearer ")?.trim() ?: ""
                val authHeader = "Bearer $tokenBase"

                Log.d("THREAD_EXPEDITOR", "⬇️ Solicitando nivel $nivel: $uriActual")

                val respuesta = repository.api.getPostThread(token = authHeader, uri = uriActual!!)
                val thread = respuesta.thread

                // 1. Extraer datos del post actual
                val post = thread.post
                val autor = post.author.handle

                // Acceso directo al texto según la estructura de la clase Post
                val texto = (post.record as? Map<*, *>)?.get("text")?.toString()
                    ?: "[Sin texto]"

                resultado.add("@$autor: \"$texto\"")

                // 2. BUSCAR AL PADRE (Acceso directo por objeto, no por Mapa)
                // En la librería, 'thread.parent' suele ser un objeto 'ThreadViewPost'
                // que a su vez tiene un objeto 'post' dentro.

                val parentView = thread.parent

                // Intentamos obtener la URI del padre de la forma más directa posible
                // Si tu librería usa 'parent.post.uri', esto es lo que buscamos:
                val siguienteUri = parentView?.post?.uri

                if (siguienteUri != null) {
                    uriActual = siguienteUri
                    Log.d("THREAD_EXPEDITOR", "⬆️ NIVEL $nivel OK. Padre: $uriActual")
                } else {
                    Log.d("THREAD_EXPEDITOR", "⏹️ Fin del hilo en nivel $nivel (No hay más padres).")
                    uriActual = null
                }

                nivel++
            } catch (e: Exception) {
                Log.e("THREAD_EXPEDITOR", "❌ Error en nivel $nivel: ${e.message}")
                uriActual = null
            }
        }
        return resultado
    }

}