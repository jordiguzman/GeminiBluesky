package mentat.music.com.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import mentat.music.com.database.MentatDatabase
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Clases exclusivas para la ruta de Obras Maestras / Wikimedia
data class ObraWikimedia(
    val titulo: String,
    val url: String
)

data class PostWikimedia(
    val artista: String,
    val textoPost: String,
    val obras: List<ObraWikimedia>
)

object BuscadorWikimedia {

    // API de Wikimedia Commons (Búsqueda de archivos de imagen)
    private const val BASE_URL = "https://commons.wikimedia.org/w/api.php"

    suspend fun buscarTesoro(context: Context): PostWikimedia? = withContext(Dispatchers.IO) {
        Log.d("WIKI_API", "🚀 1. Entrando en buscarTesoro")

        try {
            Log.d("WIKI_API", "🚀 2. Conectando con MentatDatabase...")
            val db = MentatDatabase.getDatabase(context.applicationContext)
            val dao = db.obraMaestraDao()
            Log.d("WIKI_API", "🚀 3. DAO obtenido. Todo listo para buscar.")
            val hace15Dias = System.currentTimeMillis() - (15L * 24 * 60 * 60 * 1000)

            var intentos = 0
            val maxIntentos = 40

            while (intentos < maxIntentos) {
                intentos++
                Log.d("WIKI_API", "--------------------------------------------------")

                val obraPendiente = dao.getSiguientePendiente(hace15Dias) ?: return@withContext null
                val idRoom = obraPendiente.id
                val artistaCrudo = obraPendiente.artista
                val obraProhibida = obraPendiente.obraExcluida

                Log.d("WIKI_API", "Intento #$intentos: Analizando '$artistaCrudo'")

                // --- FASE 1: EL FILTRO TRITURADORA ---

                if (artistaCrudo.contains("/") || artistaCrudo.contains("\"")) {
                    Log.d("WIKI_API", "❌ Contiene caracteres inválidos. Descartado.")
                    dao.marcarDescartado(idRoom)
                    continue
                }

                var artistaLimpio = artistaCrudo.trim()
                val indexBy = artistaLimpio.indexOf(" by ", ignoreCase = true)
                if (indexBy != -1) {
                    artistaLimpio = artistaLimpio.substring(indexBy + 4).trim()
                }

                if (artistaLimpio.split("\\s+".toRegex()).size > 5) {
                    Log.d("WIKI_API", "❌ Demasiadas palabras (posible biografía). Descartado.")
                    dao.marcarDescartado(idRoom)
                    continue
                }

                val obraLower = obraProhibida.lowercase()
                val terminosTecnicos = listOf("oil", "acrylic", "gouache", "tempera", "canvas", "cm", "mm", "x")
                val contieneNumeros = obraLower.any { it.isDigit() }
                val esAmnistia = terminosTecnicos.any { obraLower.contains(it) } || contieneNumeros

                val prohibicionActiva = !esAmnistia
                if (esAmnistia) {
                    Log.d("WIKI_API", "⚠️ Amnistía técnica aplicada. Se ignorará la prohibición: $obraProhibida")
                }

                // --- FASE 2: BÚSQUEDA EN WIKIMEDIA COMMONS ---

                val queryTerm = "\"$artistaLimpio\" painting"
                val queryUrl = "$BASE_URL?action=query&format=json&generator=search&gsrnamespace=6&gsrsearch=${URLEncoder.encode(queryTerm, "UTF-8")}&gsrlimit=15&prop=imageinfo&iiprop=url&iiurlwidth=800"
                val jsonRespuesta = descargarJson(queryUrl)
                if (jsonRespuesta == null) {
                    dao.marcarDescartado(idRoom)
                    delay(500)
                    continue
                }

                val jsonObject = JSONObject(jsonRespuesta)
                val queryObj = jsonObject.optJSONObject("query")

                if (queryObj == null) {
                    Log.d("WIKI_API", "❌ Sin resultados en Wikimedia para $artistaLimpio.")
                    dao.marcarDescartado(idRoom)
                    delay(500)
                    continue
                }

                val pagesObj = queryObj.optJSONObject("pages") ?: JSONObject()
                val candidatos = mutableListOf<ObraWikimedia>()

                pagesObj.keys().forEach { key ->
                    val page = pagesObj.getJSONObject(key)
                    val titleCrudo = page.optString("title", "")
                    val imageInfoArray = page.optJSONArray("imageinfo")

                    // ✅ FILTRO DE FORMATO: lo primero, antes de procesar nada más
                    val titleLower = titleCrudo.lowercase()
                    if (!titleLower.endsWith(".jpg") && !titleLower.endsWith(".jpeg") && !titleLower.endsWith(".png")) {
                        Log.d("WIKI_API", "Descartando formato no soportado: $titleCrudo")
                        return@forEach
                    }

                    if (imageInfoArray != null && imageInfoArray.length() > 0) {
                        val url = imageInfoArray.getJSONObject(0).optString("thumburl", imageInfoArray.getJSONObject(0).optString("url", ""))

                        val tituloLimpio = titleCrudo
                            .replace("File:", "")
                            .replace(".jpg", "", ignoreCase = true)
                            .replace(".jpeg", "", ignoreCase = true)
                            .replace(".png", "", ignoreCase = true)
                            .replace("_", " ")

                        val tituloLower = tituloLimpio.lowercase()
                        if (prohibicionActiva && (tituloLower.contains(obraLower) || obraLower.contains(tituloLower))) {
                            Log.d("WIKI_API", "🚫 Obra prohibida detectada y bloqueada: $tituloLimpio")
                        } else {
                            if (url.isNotEmpty()) {
                                val urlSegura = if (url.startsWith("//")) "https:$url" else url
                                // ⚡ Conectamos directamente a Wikimedia Commons sin intermediarios que nos corten la conexión
                                candidatos.add(ObraWikimedia(titulo = tituloLimpio, url = urlSegura))
                            }
                        }
                    }
                }

                if (candidatos.size >= 2) {
                    val seleccion = candidatos.shuffled().take(3)
                    Log.d("WIKI_API", "✅ ¡ÉXITO! Encontradas ${seleccion.size} obras en Wikimedia para $artistaLimpio")
                    dao.marcarProcesado(idRoom, System.currentTimeMillis())

                    val textoPost = "$artistaLimpio\n\n#Arte #Pintura #Wikimedia"
                    val nombreEnriquecido = enriquecerConWikipedia(artistaLimpio)
                    return@withContext PostWikimedia(artista = artistaLimpio, textoPost = nombreEnriquecido, obras = seleccion)
                } else {
                    Log.d("WIKI_API", "❌ Descartado. Obras válidas que pasaron el filtro: ${candidatos.size}.")
                    dao.marcarDescartado(idRoom)
                    delay(500)
                }
            }

            Log.e("WIKI_API", "Se alcanzó el límite de $maxIntentos intentos.")
            return@withContext null

        } catch (e: Exception) {
            Log.e("WIKI_API", "🔥 Error crítico", e)
            null
        }
    }

    private suspend fun enriquecerConWikipedia(nombre: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val idiomas = listOf("es", "en")
                var extracto = ""

                for (lang in idiomas) {
                    val wikiUrl = "https://$lang.wikipedia.org/api/rest_v1/page/summary/${URLEncoder.encode(nombre.replace(" ", "_"), "UTF-8")}"
                    val respuesta = descargarJson(wikiUrl)
                    if (respuesta != null) {
                        extracto = JSONObject(respuesta).optString("extract", "")
                        if (extracto.isNotEmpty()) break
                    }
                }

                if (extracto.isEmpty()) return@withContext nombre

                val regexAnos = "\\((\\d{4}[–\\-]\\d{4}|n\\.\\s\\d{4})\\)".toRegex()
                val matchAnos = regexAnos.find(extracto)
                val anos = matchAnos?.value ?: ""

                val estilos = listOf(
                    "impresionista", "surrealista", "barroco", "renacentista",
                    "expresionista", "cubista", "neoclásico", "romántico",
                    "realista", "abstracto", "fauvista", "postimpresionista"
                )
                val estiloEncontrado = estilos.firstOrNull { extracto.contains(it, ignoreCase = true) }
                    ?.replaceFirstChar { it.uppercase() } ?: ""

                val infoExtra = listOf(anos, estiloEncontrado).filter { it.isNotEmpty() }.joinToString(". ")

                if (infoExtra.isNotEmpty()) "$nombre $infoExtra" else nombre

            } catch (e: Exception) {
                nombre
            }
        }
    }

    suspend fun buscarMasDelMismo(nombreArtista: String, titulosYaVistos: List<String> = emptyList()): PostWikimedia? = withContext(Dispatchers.IO) {
        try {
            Log.d("WIKI_API", "🔄 Buscando más obras de: $nombreArtista")

            val queryTerm = "\"$nombreArtista\" painting"
            val queryUrl = "$BASE_URL?action=query&format=json&generator=search&gsrnamespace=6&gsrsearch=${URLEncoder.encode(queryTerm, "UTF-8")}&gsrlimit=20&prop=imageinfo&iiprop=url&iiurlwidth=800"

            val jsonRespuesta = descargarJson(queryUrl) ?: return@withContext null
            val jsonObject = JSONObject(jsonRespuesta)
            val queryObj = jsonObject.optJSONObject("query") ?: return@withContext null
            val pagesObj = queryObj.optJSONObject("pages") ?: JSONObject()

            val candidatos = mutableListOf<ObraWikimedia>()

            pagesObj.keys().forEach { key ->
                val page = pagesObj.getJSONObject(key)
                val titleCrudo = page.optString("title", "")
                val imageInfoArray = page.optJSONArray("imageinfo")

                // ✅ FILTRO DE FORMATO: igual que en buscarTesoro
                val titleLower = titleCrudo.lowercase()
                if (!titleLower.endsWith(".jpg") && !titleLower.endsWith(".jpeg") && !titleLower.endsWith(".png")) {
                    Log.d("WIKI_API", "Descartando formato no soportado: $titleCrudo")
                    return@forEach
                }

                if (imageInfoArray != null && imageInfoArray.length() > 0) {
                    val rawUrl = imageInfoArray.getJSONObject(0).optString("thumburl", imageInfoArray.getJSONObject(0).optString("url", ""))
                    val url = if (rawUrl.startsWith("http://")) rawUrl.replace("http://", "https://") else rawUrl

                    val tituloLimpio = titleCrudo
                        .replace("File:", "")
                        .replace(".jpg", "", ignoreCase = true)
                        .replace(".jpeg", "", ignoreCase = true)
                        .replace(".png", "", ignoreCase = true)
                        .replace("_", " ")

                    val yaLaEstaViendo = titulosYaVistos.any { tituloVisto ->
                        tituloLimpio.contains(tituloVisto, ignoreCase = true) || tituloVisto.contains(tituloLimpio, ignoreCase = true)
                    }

                    if (yaLaEstaViendo) {
                        Log.d("WIKI_API", "Descartando repetida en pantalla: $tituloLimpio")
                        return@forEach
                    }

                    if (url.isNotEmpty()) {
                        val urlSegura = if (url.startsWith("//")) "https:$url" else url
                        // ⚡ Conectamos directamente a Wikimedia Commons sin intermediarios que nos corten la conexión
                        candidatos.add(ObraWikimedia(titulo = tituloLimpio, url = urlSegura))
                    }
                }
            }

            if (candidatos.size >= 2) {
                val seleccion = candidatos.shuffled().take(3)
                val nombreEnriquecido = enriquecerConWikipedia(nombreArtista)
                return@withContext PostWikimedia(artista = nombreArtista, textoPost = nombreEnriquecido, obras = seleccion)
            }
            null
        } catch (e: Exception) {
            Log.e("WIKI_API", "Error en búsqueda ligera", e)
            null
        }
    }

    private suspend fun descargarJson(urlUrl: String): String? {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(urlUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "MentatArtApp/1.0 (mentat.music.com)")
                    connectTimeout = 10000
                    readTimeout = 10000
                    doInput = true
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    return@withContext connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    Log.e("WIKI_API", "Error HTTP: $responseCode en la URL: $urlUrl")
                    null
                }
            } catch (e: Exception) {
                Log.e("WIKI_API", "Fallo de conexión en el móvil: ${e.message}")
                delay(1500)
                null
            } finally {
                connection?.disconnect()
            }
        }
    }
}