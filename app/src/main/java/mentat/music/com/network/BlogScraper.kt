package mentat.music.com.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import mentat.music.com.utils.HistorialStorage
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.regex.Pattern

data class ObraVisual(
    val url: String,
    val titulo: String
)

data class ArtPost(
    val textoPost: String,
    val obras: List<ObraVisual>,
    val urlOriginal: String
)

object BlogScraper {

    private const val BASE_SEARCH = "https://api.artic.edu/api/v1/artworks/search"
    private const val WIKI_API = "https://en.wikipedia.org/api/rest_v1/page/summary/"

    suspend fun buscarTesoro(context: Context): ArtPost? = withContext(Dispatchers.IO) {
        try {
            val memoria = HistorialStorage(context)

            repeat(5) { intento ->
                Log.d("ART_API", "--------------------------------------------------")
                val paginaAleatoria = (1..20).random()
                Log.d("ART_API", "Attempt #$intento: 1. Buscando semilla en página $paginaAleatoria...")

                val urlSemilla = "$BASE_SEARCH?q=painting&query[term][is_public_domain]=true&limit=50&page=$paginaAleatoria&fields=artist_id"

                val jsonSemilla = descargarJson(urlSemilla)
                if (jsonSemilla == null) {
                    Log.d("ART_API", "Attempt #$intento: Falló la descarga de la semilla. Saltando intento.")
                    delay(2000)
                    return@repeat
                }

                val dataSemilla = JSONObject(jsonSemilla).optJSONArray("data")

                if (dataSemilla != null && dataSemilla.length() > 0) {
                    val artistasIds = mutableSetOf<Int>()
                    for (i in 0 until dataSemilla.length()) {
                        val id = dataSemilla.getJSONObject(i).optInt("artist_id", 0)
                        if (id > 0) artistasIds.add(id)
                    }

                    Log.d("ART_API", "Attempt #$intento: 2. Semilla extraída. Encontrados ${artistasIds.size} artistas únicos.")

                    for (artistId in artistasIds.shuffled()) {
                        Log.d("ART_API", "Investigando al artista ID: $artistId...")

                        // Freno de mano. 2 segundos de pausa por artista para no saturar la API.
                        delay(2000)

                        // Arreglo del Error 400. Eliminamos la doble condición y pedimos el dato is_public_domain para filtrarlo aquí.
                        val urlObras = "$BASE_SEARCH?query[term][artist_id]=$artistId&limit=15&fields=id,title,image_id,artist_title,style_title,artist_birth_year,artist_death_year,classification_title,is_public_domain"

                        val jsonObras = descargarJson(urlObras)
                        if (jsonObras == null) {
                            Log.d("ART_API", "Fallo al descargar obras del artista $artistId. Pasando al siguiente.")
                            continue
                        }

                        val dataObras = JSONObject(jsonObras).optJSONArray("data")
                        val cantidadObras = dataObras?.length() ?: 0
                        Log.d("ART_API", "   - El museo devuelve $cantidadObras obras para el artista $artistId.")

                        if (dataObras != null && cantidadObras >= 2) {
                            val candidatos = mutableListOf<CandidatoArte>()

                            for (j in 0 until cantidadObras) {
                                val item = dataObras.getJSONObject(j)
                                val imgId = item.optString("image_id")
                                val id = item.optInt("id")
                                val urlWeb = "https://www.artic.edu/artworks/$id"
                                val clasificacion = item.optString("classification_title", "").lowercase()
                                val artistaNombre = item.optString("artist_title")
                                val esPublico = item.optBoolean("is_public_domain", false)

                                if (!esPublico) {
                                    Log.d("ART_API", "   - Descartada obra $id. No es de dominio público.")
                                    continue
                                }
                                if (imgId.isNullOrEmpty() || imgId == "null") {
                                    Log.d("ART_API", "   - Descartada obra $id. Sin imagen.")
                                    continue
                                }
                                if (artistaNombre.isNullOrEmpty() || artistaNombre == "null" || artistaNombre.contains("Unknown")) {
                                    Log.d("ART_API", "   - Descartada obra $id. Autor desconocido o nulo.")
                                    continue
                                }
                                if (!clasificacion.contains("painting") && !clasificacion.contains("drawing") && !clasificacion.contains("print") && !clasificacion.contains("pastel")) {
                                    Log.d("ART_API", "   - Descartada obra $id. Clasificación incorrecta ($clasificacion).")
                                    continue
                                }
                                if (memoria.getUrlsVistas().contains(urlWeb)) {
                                    Log.d("ART_API", "   - Descartada obra $id. Ya vista anteriormente.")
                                    continue
                                }

                                candidatos.add(
                                    CandidatoArte(
                                        id = id,
                                        artista = artistaNombre,
                                        titulo = item.optString("title"),
                                        imgId = imgId,
                                        estiloReal = item.optString("style_title"),
                                        nacio = item.optInt("artist_birth_year", 0),
                                        murio = item.optInt("artist_death_year", 0),
                                        urlWeb = urlWeb
                                    )
                                )
                            }

                            Log.d("ART_API", "   - Tras los filtros, quedan ${candidatos.size} obras limpias.")

                            if (candidatos.size >= 2) {
                                val seleccion = candidatos.shuffled().take(3)
                                Log.d("ART_API", "Exito. Galería lista para ${seleccion[0].artista}")
                                return@withContext crearPostFinal(seleccion, memoria)
                            } else {
                                Log.d("ART_API", "No hay suficientes obras limpias para montar la galería.")
                            }
                        }
                    }
                } else {
                    Log.d("ART_API", "Attempt #$intento: La semilla devolvió 0 resultados.")
                }
            }
            Log.d("ART_API", "Agotados los 5 intentos sin éxito.")
            return@withContext null

        } catch (e: Exception) {
            Log.e("ART_API", "Error crítico en el proceso de búsqueda", e)
            null
        }
    }

    data class CandidatoArte(
        val id: Int,
        val artista: String,
        val titulo: String,
        val imgId: String,
        val estiloReal: String?,
        val nacio: Int,
        val murio: Int,
        val urlWeb: String
    )

    private fun crearPostFinal(
        seleccion: List<CandidatoArte>,
        memoria: HistorialStorage
    ): ArtPost {
        Log.d("ART_API", "Preparando textos finales y Wikipedia...")
        val lider = seleccion[0]

        val estiloMostrar = if (!lider.estiloReal.isNullOrEmpty() && lider.estiloReal != "null") {
            lider.estiloReal
        } else {
            ""
        }

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
            if (textoFechas.isEmpty()) {
                textoFechas = fechaEncontrada
            }
            estiloFinal = estiloFinal.replace(fechaEncontrada, "").trim()
        }

        estiloFinal = estiloFinal.replace(Regex("\\(\\s*\\)"), "").trim()
        if (estiloFinal.endsWith(",")) estiloFinal = estiloFinal.dropLast(1).trim()

        val galeriaVisual = seleccion.map { item ->
            memoria.guardarUrl(item.urlWeb)
            ObraVisual(
                url = construirUrlImagen(item.imgId),
                titulo = item.titulo
            )
        }

        val encabezado = "${lider.artista} $textoFechas".replace("  ", " ").trim()

        val textoPost = if (estiloFinal.isNotEmpty()) {
            "$encabezado\n$estiloFinal"
        } else {
            encabezado
        }

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
        } catch (_: Exception) {
            Pair("", "")
        }
    }

    private fun descargarJson(url: String): String? {
        return try {
            Jsoup.connect(url)
                .ignoreContentType(true)
                .header("User-Agent", "MentatApp/2.0 (Android)")
                .timeout(10000)
                .execute()
                .body()
        } catch (e: org.jsoup.HttpStatusException) {
            Log.e("ART_API", "Error HTTP ${e.statusCode} al conectar con la API.")
            null
        } catch (e: Exception) {
            Log.e("ART_API", "Error general de red. ${e.message}")
            null
        }
    }

    private fun construirUrlImagen(imageId: String): String {
        return "https://www.artic.edu/iiif/2/$imageId/full/843,/0/default.jpg"
    }
}