package mentat.music.com.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "https://bsky.social/"

    // ⚡ CREAMOS EL MOTOR CON PACIENCIA AMPLIADA
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(40, TimeUnit.SECONDS) // Tiempo máximo para conectar con Bluesky
        .writeTimeout(40, TimeUnit.SECONDS)   // Tiempo máximo para enviar la foto
        .readTimeout(40, TimeUnit.SECONDS)    // Tiempo máximo para esperar el "OK"
        .build()

    val api: BlueskyApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient) // ⚡ Le pasamos nuestro motor personalizado aquí
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BlueskyApi::class.java)
    }
}