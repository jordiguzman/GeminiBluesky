package mentat.music.com.utils

import android.content.Context
import android.util.Log
import mentat.music.com.model.ProfileView
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

object SocialUtils {

    enum class NivelRelacion {
        DESCONOCIDO,
        CONOCIDO_SEGUIDO,
        SEGUIDOR,
        AMIGO_MUTUO,
        VIP_CONFIRMADO // <--- ¡NUEVO NIVEL SUPREMO!
    }

    // Cache en memoria para no leer el archivo 50 veces
    private var listaVipsCache: Map<String, Int>? = null

    // Carga el JSON de assets y lo convierte en un Mapa rápido: "usuario" -> puntos
    fun cargarVips(context: Context) {
        if (listaVipsCache != null) return // Ya estaba cargado

        try {
            val inputStream = context.assets.open("mis_vips.json")
            val jsonString = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
            val jsonArray = JSONArray(jsonString)

            val mapaTemp = mutableMapOf<String, Int>()

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val handle = item.getString("handle")
                val score = item.getInt("score")
                mapaTemp[handle] = score
            }

            listaVipsCache = mapaTemp
            Log.d("SOCIAL_UTILS", "✅ Lista VIP cargada: ${mapaTemp.size} amigos.")

        } catch (e: Exception) {
            Log.e("SOCIAL_UTILS", "⚠️ No se pudo cargar mis_vips.json (¿Quizás no lo has copiado aún?)", e)
            listaVipsCache = emptyMap()
        }
    }

    fun calcularRelacion(autor: ProfileView, context: Context): NivelRelacion {
        // Aseguramos que la lista esté cargada
        cargarVips(context)

        val handle = autor.handle
        val leSigo = autor.viewer?.following != null
        val meSigue = autor.viewer?.followedBy != null

        // 1. CHEQUEO VIP (PRIORIDAD MÁXIMA)
        // Si está en tu lista del script PHP, es VIP automáticamente
        if (listaVipsCache?.containsKey(handle) == true) {
            return NivelRelacion.VIP_CONFIRMADO
        }

        // 2. CHEQUEO ESTÁNDAR
        return when {
            leSigo && meSigue -> NivelRelacion.AMIGO_MUTUO
            leSigo -> NivelRelacion.CONOCIDO_SEGUIDO
            meSigue -> NivelRelacion.SEGUIDOR
            else -> NivelRelacion.DESCONOCIDO
        }
    }

    fun obtenerInstruccionTono(nivel: NivelRelacion): String {
        return when (nivel) {
            NivelRelacion.VIP_CONFIRMADO ->
                "USER IS A VIP (High affinity history). Tone: Warm, highly respectful, prioritize this interaction. Use inside jokes if possible."

            NivelRelacion.AMIGO_MUTUO ->
                "USER IS A MUTUAL FRIEND. Tone: Familiar, complicity allowed."

            NivelRelacion.SEGUIDOR ->
                "USER IS A FOLLOWER. Tone: Grateful and welcoming."

            NivelRelacion.CONOCIDO_SEGUIDO ->
                "USER IS SOMEONE I FOLLOW. Tone: Friendly and interested."

            NivelRelacion.DESCONOCIDO ->
                "USER IS A STRANGER. Tone: Polite, distant, cautious. NEVER condescending."
        }
    }
}