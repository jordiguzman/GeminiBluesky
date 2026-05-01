package mentat.music.com.network // O el paquete que uses

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mentat.music.com.database.MentatDatabase
import mentat.music.com.database.ObraMaestraEntity
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL

object SincronizadorObras {

    private const val JSONL_URL = "https://mentat-music.com/gemini_app/obras_maestras.jsonl"

    suspend fun sincronizarSiEsNecesario(context: Context) = withContext(Dispatchers.IO) {
        val db = MentatDatabase.getDatabase(context)
        val dao = db.obraMaestraDao()

        // 1. Comprobamos si ya tenemos los datos
        val cantidadActual = dao.contarObras()

        if (cantidadActual == 0) {
            Log.d("JSONL", "La base de datos está vacía. Iniciando descarga desde el servidor...")
            try {
                val url = URL(JSONL_URL)
                val conexion = url.openConnection()
                val inputStream = conexion.getInputStream()
                val lector = BufferedReader(InputStreamReader(inputStream))

                val listaObras = mutableListOf<ObraMaestraEntity>()

                // 2. Leemos en streaming, línea a línea, para no saturar la RAM
                lector.forEachLine { linea ->
                    if (linea.isNotBlank()) {
                        val json = JSONObject(linea)
                        val artista = json.optString("artista", "")
                        val obraExcluida = json.optString("obra_excluida", "")

                        if (artista.isNotEmpty()) {
                            listaObras.add(
                                ObraMaestraEntity(
                                    artista = artista,
                                    obraExcluida = obraExcluida
                                )
                            )
                        }
                    }
                }
                lector.close()

                Log.d("JSONL", "Descarga completada. Insertando ${listaObras.size} registros en Room...")

                // 3. Inserción masiva en la base de datos local
                dao.insertAll(listaObras)

                Log.d("JSONL", "¡Éxito! Base de datos sincronizada y lista para cazar.")

            } catch (e: Exception) {
                Log.e("JSONL", "Error crítico al descargar o procesar el archivo: ${e.message}")
            }
        } else {
            Log.d("JSONL", "La base de datos ya contiene $cantidadActual obras. Saltando descarga.")
        }
    }
}