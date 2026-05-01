package mentat.music.com.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

object ImageUtils {

    /**
     * Descarga una imagen desde una URL y la convierte en Bitmap.
     * Vital para que Gemini pueda "verla".
     */
    suspend fun descargarImagen(context: Context, url: String): Bitmap? {
        if (url.isBlank()) return null

        return try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false) // Importante para Gemini: necesita bitmap de software/copiable
                .size(1024)
                .build()

            val result = loader.execute(request)

            if (result is SuccessResult) {
                Log.d("IMAGE_UTILS", "✅ Imagen descargada: $url")
                (result.drawable as BitmapDrawable).bitmap
            } else {
                Log.e("IMAGE_UTILS", "⚠️ Fallo al descargar imagen (No Success)")
                null
            }
        } catch (e: Exception) {
            Log.e("IMAGE_UTILS", "❌ Error descargando imagen", e)
            null
        }
    }
}