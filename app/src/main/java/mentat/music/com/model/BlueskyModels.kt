package mentat.music.com.model

import com.google.gson.annotations.SerializedName

// 1. Login
data class LoginRequest(val identifier: String, val password: String)
data class LoginResponse(val accessJwt: String, val did: String, val handle: String)

// 2. Timeline
data class FeedResponse(val feed: List<FeedViewPost>)

data class FeedViewPost(
    val post: PostView,
    val reply: ReplyRef? = null,
    val reason: Reason? = null
)

data class Reason(
    val by: ProfileView,
    val indexedAt: String
)

// 3. Notificaciones
data class NotificationListResponse(
    val notifications: List<NotificationView>
)

data class NotificationView(
    val uri: String,
    val cid: String,
    val author: ProfileView,
    val reason: String,
    val reasonSubject: String? = null,
    val record: Any?,
    val isRead: Boolean,
    val indexedAt: String
)

// 4. Post (Lectura)
data class PostView(
    val uri: String,
    val cid: String,
    val author: ProfileView,
    val record: Any?,
    val embed: Any? = null,
    val likeCount: Int = 0,
    val replyCount: Int = 0,
    val indexedAt: String
)

// 5. Perfiles
data class ProfileView(
    val did: String,
    val displayName: String?,
    val handle: String,
    val avatar: String? = null,
    val viewer: ViewerState? = null
)

data class ProfileViewDetailed(
    val did: String,
    val handle: String,
    val displayName: String?,
    val description: String?,
    val avatar: String? = null,
    val followersCount: Int = 0,
    val followsCount: Int = 0,
    val postsCount: Int = 0,
    val viewer: ViewerState? = null
)

data class ViewerState(
    val following: String?, // URI del follow si le sigues
    val followedBy: String? // URI del follow si te sigue
)

// 6. Publicación (Escritura)

// La petición genérica para crear cualquier cosa
data class CreateRecordRequest(val repo: String, val collection: String, val record: Any)

// La estructura del POST
data class PostStructure(
    val text: String,
    val createdAt: String,
    val reply: ReplyRef? = null,
    val embed: Any? = null,

    // IMPORTANTE: Forzamos el nombre con $ para que el servidor no se queje
    @SerializedName("\$type")
    val type: String = "app.bsky.feed.post"
)

// La estructura del LIKE (Corregida)
data class LikeRecord(
    val subject: Reference,
    val createdAt: String,

    // FALTABA ESTO: Sin el tipo, el like da error 400
    @SerializedName("\$type")
    val type: String = "app.bsky.feed.like"
)

data class ReplyRef(val root: Reference, val parent: Reference)
data class Reference(val uri: String, val cid: String)
data class CreateRecordResponse(val uri: String, val cid: String)

// 7. Hilos
data class ThreadResponse(
    val thread: ThreadView
)

data class ThreadView(
    val post: PostView,
    val parent: ThreadView? = null
)

// --- IMÁGENES (Corregido para evitar error 400) ---

data class BlobUploadResponse(
    val blob: BlobRef
)

data class BlobRef(
    // Usamos Map para 'ref' porque dentro tiene la clave "$link"
    // y Gson a veces se lía si creamos una clase con variables que empiezan por $.
    val ref: Map<String, String>,
    val mimeType: String,
    val size: Long,

    // AÑADIDO: Aseguramos que se identifique como blob al enviarlo
    @SerializedName("\$type")
    val type: String = "blob"
)

data class ImagesEmbed(
    @SerializedName("\$type")
    val type: String = "app.bsky.embed.images",

    val images: List<ImageAspect>
)

data class ImageAspect(
    val image: BlobRef,
    val alt: String = "Imagen publicada desde Mentat"
)
// --- MODELOS PARA ACCIONES SOCIALES ---


data class RepostRecord(
    val subject: Reference,
    val createdAt: String,
    @SerializedName("\$type")
    val type: String = "app.bsky.feed.repost"
)

data class FollowRecord(
    val subject: String,
    val createdAt: String,
    @SerializedName("\$type")
    val type: String = "app.bsky.graph.follow"
)

data class EmbedRecord(
    @SerializedName("\$type")
    val type: String = "app.bsky.embed.record",
    val record: Reference
)