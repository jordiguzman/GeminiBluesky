package mentat.music.com.network

class ThreadFetcher(private val api: BlueskyApi) {

    suspend fun obtenerResumenHilo(token: String, postUri: String): String {
        return try {
            val response = api.getPostThread("Bearer $token", postUri)
            val mensajes = mutableListOf<String>()

            // 1. Empezamos por el post actual (el que ha disparado el escáner)
            val postActual = response.thread?.post
            if (postActual != null) {
                val handleActual = postActual.author.handle
                val recordActual = postActual.record as? Map<*, *>
                val textoActual = recordActual?.get("text")?.toString() ?: ""
                if (textoActual.isNotEmpty()) {
                    mensajes.add("@$handleActual: $textoActual")
                }
            }

            // 2. Ahora subimos por los padres (máximo 5 niveles más)
            var actual = response.thread?.parent

            while (actual != null && mensajes.size < 6) {
                val handle = actual.post.author.handle
                val recordMap = actual.post.record as? Map<*, *>
                val texto = recordMap?.get("text")?.toString() ?: ""

                if (texto.isNotEmpty()) {
                    mensajes.add("@$handle: $texto")
                }
                actual = actual.parent
            }

            // 3. Invertimos la lista para que el más viejo esté arriba y el más nuevo abajo
            if (mensajes.isEmpty()) ""
            else {
                // Quitamos el último (que es el post actual) para la cabecera del contexto
                // y dejamos que el TimelineScreen lo ponga como "POST ACTUAL"
                val actualParaMostrar = mensajes.first()
                val contextoPrevio = mensajes.drop(1).reversed()

                if (contextoPrevio.isEmpty()) ""
                else contextoPrevio.joinToString("\n⬇️\n") + "\n\n"
            }
        } catch (e: Exception) {
            ""
        }
    }
}