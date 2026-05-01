package mentat.music.com.utils

import android.content.Context

class HistorialStorage(context: Context) {

    // Nombre del archivo de preferencias
    private val prefs = context.getSharedPreferences("historial_arte_prefs", Context.MODE_PRIVATE)
    private val KEY_URLS = "urls_vistas"

    // 1. Obtener la lista de lo que ya hemos visto
    fun getUrlsVistas(): List<String> {
        // SharedPreferences guarda Sets, lo convertimos a List para que sea fácil de usar
        val set = prefs.getStringSet(KEY_URLS, emptySet()) ?: emptySet()
        return set.toList()
    }

    // 2. Guardar una nueva URL en la memoria
    fun guardarUrl(url: String) {
        // Recuperamos lo que había
        val setActual = prefs.getStringSet(KEY_URLS, emptySet()) ?: emptySet()

        // Creamos una copia mutable y añadimos la nueva
        val nuevoSet = setActual.toMutableSet()
        nuevoSet.add(url)

        // Guardamos de vuelta
        prefs.edit().putStringSet(KEY_URLS, nuevoSet).apply()
    }

    // Opcional: Si quieres borrar la memoria algún día
    fun borrarHistorial() {
        prefs.edit().remove(KEY_URLS).apply()
    }
}