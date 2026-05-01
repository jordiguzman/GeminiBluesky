package mentat.music.com.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object MentatTimeHelper {

    private const val FORMATO_ISO = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
    private const val FORMATO_ISO_SIMPLE = "yyyy-MM-dd'T'HH:mm:ss'Z'"

    // ⚡ CAMBIO: Formato corregido a dd/MM
    private const val FORMATO_SALIDA = "dd/MM / HH:mm"

    fun calcularTiempoRelativo(isoDate: String): String {
        if (isoDate.isBlank()) return ""

        try {
            var sdf = SimpleDateFormat(FORMATO_ISO, Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")

            var date: Date? = null
            try {
                date = sdf.parse(isoDate)
            } catch (e: Exception) {
                sdf = SimpleDateFormat(FORMATO_ISO_SIMPLE, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                date = sdf.parse(isoDate)
            }

            if (date == null) return "???"

            val now = Date()
            val diffMillis = now.time - date.time
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
            val hours = TimeUnit.MILLISECONDS.toHours(diffMillis)
            val days = TimeUnit.MILLISECONDS.toDays(diffMillis)

            return when {
                minutes < 1 -> "Ahora"
                minutes < 60 -> "${minutes}m"
                hours < 24 -> "${hours}h"
                days < 7 -> "${days}d"
                else -> {
                    // Usamos el formato fijo definido arriba
                    val formateadorSalida = SimpleDateFormat(FORMATO_SALIDA, Locale.getDefault())
                    formateadorSalida.format(date)
                }
            }

        } catch (e: Exception) {
            Log.e("MENTAT_TIME", "Error fecha: $isoDate", e)
            return ""
        }
    }
}