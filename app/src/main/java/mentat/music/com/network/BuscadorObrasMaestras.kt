package mentat.music.com.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import mentat.music.com.database.MentatDatabase
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.regex.Pattern

object BuscadorObrasMaestras {

    private const val BASE_SEARCH = "https://api.artic.edu/api/v1/artworks/search"
    private const val WIKI_API = "https://en.wikipedia.org/api/rest_v1/page/summary/"

    suspend fun buscarTesoro(context: Context): ArtPost? = withContext(Dispatchers.IO) {
        try {
            val db = MentatDatabase.getDatabase(context)
            val dao = db.obraMaestraDao()

            SincronizadorObras.sincronizarSiEsNecesario(context)

            // ✅ Se calculan aquí, dentro de la función, donde dao ya existe
            val hace15Dias = System.currentTimeMillis() - (15L * 24 * 60 * 60 * 1000)

            var intentos = 0
            val maxIntentos = 40

            while (intentos < maxIntentos) {
                intentos++
                Log.d("JSONL_API", "--------------------------------------------------")

                // ✅ getSiguientePendiente ahora recibe el umbral de 15 días
                val obraPendiente = dao.getSiguientePendiente(hace15Dias)
                if (obraPendiente == null) {
                    Log.e("JSONL_API", "¡Nos hemos quedado sin obras en la base de datos!")
                    return@withContext null
                }

                val artistaIdRoom = obraPendiente.id
                val artistaNombreCrudo = obraPendiente.artista

                val artistaNombre = limpiarNombreArtista(artistaNombreCrudo)
                val obraProhibida = obraPendiente.obraExcluida.lowercase()

                Log.d("JSONL_API", "Intento #$intentos: Investigando a '$artistaNombre' (Crudo: '$artistaNombreCrudo')...")

                if (artistaNombre.length < 3) {
                    Log.d("JSONL_API", "Nombre inválido tras limpiar. Descartando.")
                    dao.marcarDescartado(artistaIdRoom)
                    continue
                }

                val queryUrl = "$BASE_SEARCH?q=${URLEncoder.encode(artistaNombre, "UTF-8")}&limit=15&fields=id,title,image_id,artist_title,style_title,artist_birth_year,artist_death_year,classification_title,is_public_domain"

                val jsonObras = descargarJson(queryUrl)
                if (jsonObras == null) {
                    dao.marcarDescartado(artistaIdRoom)
                    delay(500)
                    continue
                }

                val dataObras = JSONObject(jsonObras).optJSONArray("data") ?: org.json.JSONArray()
                val cantidadObras = dataObras.length()
                val candidatos = mutableListOf<BlogScraper.CandidatoArte>()

                for (j in 0 until cantidadObras) {
                    val item = dataObras.getJSONObject(j)
                    val imgId = item.optString("image_id")
                    val id = item.optInt("id")
                    val tituloObra = item.optString("title", "")
                    val autorMuseo = item.optString("artist_title", "")
                    val esPublico = item.optBoolean("is_public_domain", false)
                    val clasificacion = item.optString("classification_title", "").lowercase()

                    if (!esPublico) continue
                    if (imgId.isNullOrEmpty() || imgId == "null") continue
                    if (!autorMuseo.contains(artistaNombre, ignoreCase = true) && !artistaNombre.contains(autorMuseo, ignoreCase = true)) continue
                    if (!clasificacion.contains("painting") && !clasificacion.contains("drawing") && !clasificacion.contains("print") && !clasificacion.contains("pastel")) continue
                    if (tituloObra.lowercase().contains(obraProhibida) || obraProhibida.contains(tituloObra.lowercase())) continue

                    candidatos.add(
                        BlogScraper.CandidatoArte(
                            id = id, artista = autorMuseo, titulo = tituloObra, imgId = imgId,
                            estiloReal = item.optString("style_title"),
                            nacio = item.optInt("artist_birth_year", 0), murio = item.optInt("artist_death_year", 0),
                            urlWeb = "https://www.artic.edu/artworks/$id"
                        )
                    )
                }

                if (candidatos.size >= 2) {
                    val seleccion = candidatos.shuffled().take(3)
                    Log.d("JSONL_API", "✅ ¡BINGO! Galería lista para $artistaNombre")
                    // ✅ marcarProcesado ahora guarda también el timestamp actual
                    dao.marcarProcesado(artistaIdRoom, System.currentTimeMillis())
                    return@withContext crearPostFinal(seleccion)
                } else {
                    Log.d("JSONL_API", "❌ Descartado. Obras válidas: ${candidatos.size}.")
                    dao.marcarDescartado(artistaIdRoom)
                    delay(500)
                }
            }

            Log.e("JSONL_API", "Se alcanzó el límite de $maxIntentos intentos. Abortando para no colgar la app.")
            return@withContext null

        } catch (e: Exception) {
            Log.e("JSONL_API", "🔥 Error crítico", e)
            null
        }
    }

    private fun limpiarNombreArtista(crudo: String): String {
        var limpio = crudo.replace("\"", "").trim()
        val indexBy = limpio.indexOf(" by ", ignoreCase = true)
        if (indexBy != -1) {
            limpio = limpio.substring(indexBy + 4).trim()
        }
        limpio = limpio.removeSuffix(",").trim()
        return limpio
    }

    suspend fun buscarMasDelMismoArtista(
        artistaNombre: String,
        obraProhibida: String,
        titulosYaVistos: List<String>
    ): ArtPost? = withContext(Dispatchers.IO) {
        try {
            Log.d("JSONL_API", "Buscando alternativas de: $artistaNombre...")

            val queryUrl = "$BASE_SEARCH?q=${URLEncoder.encode(artistaNombre, "UTF-8")}&limit=30&fields=id,title,image_id,artist_title,style_title,artist_birth_year,artist_death_year,classification_title,is_public_domain"

            val jsonObras = descargarJson(queryUrl) ?: return@withContext null

            val dataObras = JSONObject(jsonObras).optJSONArray("data") ?: org.json.JSONArray()
            val candidatos = mutableListOf<BlogScraper.CandidatoArte>()

            for (j in 0 until dataObras.length()) {
                val item = dataObras.getJSONObject(j)
                val imgId = item.optString("image_id")
                val id = item.optInt("id")
                val tituloObra = item.optString("title", "")
                val autorMuseo = item.optString("artist_title", "")
                val esPublico = item.optBoolean("is_public_domain", false)
                val clasificacion = item.optString("classification_title", "").lowercase()

                if (!esPublico || imgId.isNullOrEmpty() || imgId == "null") continue
                if (!autorMuseo.contains(artistaNombre, ignoreCase = true)) continue
                if (!clasificacion.contains("painting") && !clasificacion.contains("drawing") && !clasificacion.contains("print") && !clasificacion.contains("pastel")) continue
                if (tituloObra.lowercase().contains(obraProhibida) || obraProhibida.contains(tituloObra.lowercase())) continue

                val yaLaEstaViendo = titulosYaVistos.any { tituloVisto ->
                    tituloObra.contains(tituloVisto, ignoreCase = true) || tituloVisto.contains(tituloObra, ignoreCase = true)
                }
                if (yaLaEstaViendo) {
                    Log.d("JSONL_API", "Descartando repetida: $tituloObra")
                    continue
                }

                candidatos.add(
                    BlogScraper.CandidatoArte(
                        id = id, artista = autorMuseo, titulo = tituloObra, imgId = imgId,
                        estiloReal = item.optString("style_title"),
                        nacio = item.optInt("artist_birth_year", 0), murio = item.optInt("artist_death_year", 0),
                        urlWeb = "https://www.artic.edu/artworks/$id"
                    )
                )
            }

            if (candidatos.size >= 2) {
                val seleccion = candidatos.shuffled().take(3)
                Log.d("JSONL_API", "✅ Alternativas FRESCAS encontradas para $artistaNombre")
                return@withContext crearPostFinal(seleccion)
            } else {
                Log.d("JSONL_API", "❌ El museo no tiene más obras distintas.")
                return@withContext null
            }

        } catch (e: Exception) { null }
    }

    private fun crearPostFinal(seleccion: List<BlogScraper.CandidatoArte>): ArtPost {
        Log.d("JSONL_API", "Preparando textos finales y Wikipedia...")
        val lider = seleccion[0]

        val estiloMostrar = if (!lider.estiloReal.isNullOrEmpty() && lider.estiloReal != "null") lider.estiloReal else ""
        var textoFechas = ""
        var extraWiki = ""

        if (lider.nacio > 0 && lider.murio > 0) {
            textoFechas = "(${lider.nacio}-${lider.murio})"
        } else {
            val wikiData = consultarWikipedia(lider.artista)
            textoFechas = wikiData.first
            extraWiki = wikiData.second
        }

        var estiloFinal = if (extraWiki.isNotEmpty() && estiloMostrar.isEmpty()) {
            extraWiki.replaceFirstChar { it.uppercase() }
        } else {
            estiloMostrar
        }

        val regexFechas = Regex("\\(\\s*\\d{3,4}\\s*[–\\-—]\\s*\\d{3,4}\\s*\\)")
        val matchFechas = regexFechas.find(estiloFinal)

        if (matchFechas != null) {
            val fechaEncontrada = matchFechas.value
            if (textoFechas.isEmpty()) textoFechas = fechaEncontrada
            estiloFinal = estiloFinal.replace(fechaEncontrada, "").trim()
        }

        estiloFinal = estiloFinal.replace(Regex("\\(\\s*\\)"), "").trim()
        if (estiloFinal.endsWith(",")) estiloFinal = estiloFinal.dropLast(1).trim()

        val galeriaVisual = seleccion.map { item ->
            ObraVisual(
                url = "https://wsrv.nl/?url=https://www.artic.edu/iiif/2/${item.imgId}/full/843,/0/default.jpg",
                titulo = item.titulo
            )
        }

        val encabezado = "${lider.artista} $textoFechas".replace("  ", " ").trim()
        val textoPost = if (estiloFinal.isNotEmpty()) "$encabezado\n$estiloFinal" else encabezado

        return ArtPost(textoPost, galeriaVisual, lider.urlWeb)
    }

    private fun consultarWikipedia(nombreArtista: String): Pair<String, String> {
        return try {
            val nombreCodificado = URLEncoder.encode(nombreArtista.replace(" ", "_"), "UTF-8")
            val url = "$WIKI_API$nombreCodificado"
            val json = descargarJson(url) ?: return Pair("", "")
            val jsonObject = JSONObject(json)

            val descripcion = jsonObject.optString("description", "")
            val extracto = jsonObject.optString("extract", "")

            val regex = Pattern.compile("\\(\\s*(\\d{3,4})\\s*[–\\-—]\\s*(\\d{3,4})\\s*\\)")
            var fechas = ""
            var matcher = regex.matcher(descripcion)

            if (matcher.find()) {
                fechas = matcher.group(0) ?: ""
            } else {
                matcher = regex.matcher(extracto)
                if (matcher.find()) fechas = matcher.group(0) ?: ""
            }

            Pair(fechas, descripcion)
        } catch (e: Exception) { Pair("", "") }
    }

    private fun descargarJson(url: String): String? {
        return try {
            Jsoup.connect(url)
                .ignoreContentType(true)
                .header("User-Agent", "MentatApp/2.0 (Android)")
                .timeout(10000)
                .execute()
                .body()
        } catch (e: Exception) { null }
    }
}