package mentat.music.com.network

import mentat.music.com.model.*
import retrofit2.Response // Importante para manejar errores manualmente si quieres
import retrofit2.http.*
import okhttp3.RequestBody

interface BlueskyApi {

    // --- AUTENTICACIÓN ---
    @POST("xrpc/com.atproto.server.createSession")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("xrpc/com.atproto.server.getSession")
    suspend fun getSession(@Header("Authorization") token: String): LoginResponse

    // --- TIMELINE Y FEEDS ---
    @GET("xrpc/app.bsky.feed.getTimeline")
    suspend fun getTimeline(
        @Header("Authorization") token: String,
        @Query("limit") limit: Int = 100
    ): FeedResponse

    // NUEVO: Para consultar un post concreto y saber su root (padre original)
    @GET("xrpc/app.bsky.feed.getPosts")
    suspend fun getPosts(
        @Header("Authorization") token: String,
        @Query("uris") uris: List<String>
    ): Response<Map<String, Any>>

    // --- NOTIFICACIONES ---
    @GET("xrpc/app.bsky.notification.listNotifications")
    suspend fun listNotifications(
        @Header("Authorization") token: String,
        @Query("limit") limit: Int = 50
    ): NotificationListResponse

    // --- PUBLICACIÓN ---
    @POST("xrpc/com.atproto.repo.createRecord")
    suspend fun createRecord(
        @Header("Authorization") token: String,
        @Body request: CreateRecordRequest
    ): CreateRecordResponse

    // --- UTILIDADES ---
    @GET("xrpc/app.bsky.feed.getPostThread")
    suspend fun getPostThread(
        @Header("Authorization") token: String,
        @Query("uri") uri: String
    ): ThreadResponse

    // ⚡ HERRAMIENTAS DEL INVESTIGADOR ⚡
    @GET("xrpc/app.bsky.actor.getProfile")
    suspend fun getProfile(
        @Header("Authorization") token: String,
        @Query("actor") actor: String
    ): ProfileViewDetailed

    @GET("xrpc/app.bsky.feed.getAuthorFeed")
    suspend fun getAuthorFeed(
        @Header("Authorization") token: String,
        @Query("actor") actor: String,
        @Query("limit") limit: Int = 10
    ): FeedResponse

    // --- SUBIDA DE IMÁGENES (FALTABA ESTO) ---
    @POST("xrpc/com.atproto.repo.uploadBlob")
    suspend fun uploadBlob(
        @Header("Authorization") token: String,
        @Header("Content-Type") contentType: String,
        @Body imageBytes: RequestBody
    ): BlobUploadResponse
}