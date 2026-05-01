package mentat.music.com.enrichment

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URL

object ContentEnricher {

    private const val TAG = "DEBUG_ENRICHER"
    // Regex mejorada para capturar URLs incluso sin http al principio si son dominios conocidos
    private val urlRegex = "(https?://\\S+|www\\.\\S+|[a-zA-Z0-9.-]+\\.(com|org|net|io)/\\S+)".toRegex()

    // User Agent moderno para evitar bloqueos (Chrome 120)
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    suspend fun analizarContenido(textoPost: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Encontrar URL candidata
                val match = urlRegex.find(textoPost)
                val rawUrl = match?.value ?: return@withContext ""
                var url = limpiarUrl(rawUrl)

                // Asegurar protocolo
                if (!url.startsWith("http")) url = "https://$url"

                // 2. FILTRO DE BASURA (Archivos binarios, CDNs)
                val lowerUrl = url.lowercase()
                if (lowerUrl.matches(".*\\.(jpg|jpeg|png|gif|mp4|mp3|webp|svg)$".toRegex()) ||
                    lowerUrl.contains("video.") ||
                    lowerUrl.contains("media.")) {
                    return@withContext ""
                }

                Log.d(TAG, "🔍 Analizando: $url")

                // 3. ESTRATEGIAS ESPECÍFICAS
                return@withContext when {
                    url.contains("bandcamp.com") -> scrapearBandcamp(url)
                    url.contains("youtube.com") || url.contains("youtu.be") -> scrapearYoutube(url)
                    // 4. ESTRATEGIA GENÉRICA (Para artículos de New Atlas, NYT, Blogs, etc.)
                    else -> scrapearGenerico(url)
                }

            } catch (e: Exception) {
                Log.e(TAG, "💥 Error fatal en Enricher: ${e.message}")
                return@withContext ""
            }
        }
    }

    private fun limpiarUrl(raw: String): String {
        return raw.trimEnd(',', '.', ')', ']', '}', '"', '\'', ';', '>', '!')
    }

    // --- BANDCAMP ---
    private fun scrapearBandcamp(url: String): String {
        try {
            val doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(10000).get()

            var artist = doc.select("meta[property=og:site_name]").attr("content")
            if (artist.isEmpty()) artist = doc.select("span[itemprop=byArtist]").text()

            var title = doc.select("meta[property=og:title]").attr("content")
            if (title.isEmpty()) title = doc.select("h2.trackTitle").text()

            var richText = doc.select(".tralbum-about").text()
            if (richText.isEmpty()) richText = doc.select("meta[property=og:description]").attr("content")

            // Limpieza
            richText = richText.replace("Stream .*? by .*? on desktop and mobile.".toRegex(), "")
            richText = richText.replace("Includes unlimited streaming via the free Bandcamp app.*".toRegex(), "")

            val tags = doc.select(".tralbum-tags .tag").joinToString(", ") { it.text() }

            val sb = StringBuilder()
            sb.append("[CONTEXTO EXTERNO - BANDCAMP]\n")
            sb.append("Artist: $artist\n")
            sb.append("Album/Track: $title\n")
            if (tags.isNotEmpty()) sb.append("Genre: $tags\n")
            if (richText.isNotEmpty()) sb.append("Info: ${richText.take(500)}...\n")

            return sb.toString().also { Log.d(TAG, "📦 Bandcamp OK") }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Bandcamp falló: ${e.message}")
            return ""
        }
    }

    // --- YOUTUBE ---
    private fun scrapearYoutube(url: String): String {
        try {
            val doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Accept-Language", "en-US")
                .referrer("https://www.google.com")
                .timeout(10000)
                .get()

            var title = doc.select("meta[property=og:title]").attr("content")
            if (title.isEmpty()) title = doc.title().replace(" - YouTube", "")

            var desc = doc.select("meta[property=og:description]").attr("content")
            if (desc.isEmpty()) desc = doc.select("meta[name=description]").attr("content")

            val channel = doc.select("link[itemprop=name]").attr("content")

            if (title.isEmpty() && url.contains("v=")) {
                title = "Video: " + url.substringAfter("v=").take(11)
            }

            val sb = StringBuilder()
            sb.append("[CONTEXTO EXTERNO - YOUTUBE]\n")
            if (title.isNotEmpty()) sb.append("Title: $title\n")
            if (channel.isNotEmpty()) sb.append("Channel: $channel\n")
            if (desc.isNotEmpty()) sb.append("Description: ${desc.take(400)}...\n")

            return sb.toString().also { Log.d(TAG, "📦 YouTube OK") }

        } catch (e: Exception) {
            Log.e(TAG, "❌ YouTube falló: ${e.message}")
            return ""
        }
    }

    // --- LECTURA GENÉRICA DE WEBS ---
    private fun scrapearGenerico(url: String): String {
        val WEB_TAG = "MENTAT_WEB_READER" // 🏷️ Etiqueta exclusiva para filtrar

        return try {
            Log.d(WEB_TAG, "🌐 CONNECTING... -> $url")

            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.instanceFollowRedirects = true
            // Disfraz de navegador móvil Android
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36")

            if (conn.responseCode == 200) {
                val html = conn.inputStream.bufferedReader().readText()

                // 1. Título
                val titleRegex = "<title[^>]*>(.*?)</title>".toRegex(RegexOption.IGNORE_CASE)
                val title = titleRegex.find(html)?.groupValues?.get(1)?.trim() ?: "Sin título"

                // 2. Descripción
                val descRegex = "<meta\\s+name=[\"']description[\"']\\s+content=[\"'](.*?)[\"']".toRegex(RegexOption.IGNORE_CASE)
                val desc = descRegex.find(html)?.groupValues?.get(1)?.trim() ?: ""

                // 3. Texto principal (Párrafos > 80 chars)
                val pRegex = "<p[^>]*>(.*?)</p>".toRegex(RegexOption.IGNORE_CASE)
                val parrafos = pRegex.findAll(html)
                    .map { it.groupValues[1].replace(Regex("<[^>]*>"), "").trim() }
                    .filter { it.length > 80 }
                    .take(5)
                    .joinToString("\n\n")

                if (parrafos.isNotEmpty() || desc.isNotEmpty()) {
                    // ✅ AÑADIDO: Log de éxito para que lo veas
                    Log.d(WEB_TAG, "✅ ÉXITO LEIDO: $title")

                    return """
                        [CONTEXTO EXTERNO - WEB ARTICLE]
                        Title: $title
                        Description: $desc
                        Content Excerpt:
                        $parrafos
                    """.trimIndent()
                } else {
                    // ⚠️ AÑADIDO: Aviso si entra pero no saca texto
                    Log.w(WEB_TAG, "⚠️ Web leída pero sin texto útil (<p> cortos o vacíos).")
                }
            } else {
                Log.e(WEB_TAG, "❌ Error HTTP: ${conn.responseCode}")
            }
            ""
        } catch (e: Exception) {
            // ❌ CORREGIDO: Usamos WEB_TAG en lugar de TAG para que salga en el filtro
            Log.e(WEB_TAG, "💥 Excepción leyendo web: ${e.message}")
            ""
        }
    }
}