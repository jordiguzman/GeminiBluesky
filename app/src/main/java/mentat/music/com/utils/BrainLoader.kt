package mentat.music.com.utils

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

object BrainLoader {
    // Esta función necesita el 'context' para poder entrar en la carpeta assets
    fun cargarJSON(context: Context): String {
        return try {
            val inputStream = context.assets.open("cerebro_mentat.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.use { it.readText() }
        } catch (e: Exception) {
            "Error crítico cargando el cerebro: ${e.message}"
        }
    }
}