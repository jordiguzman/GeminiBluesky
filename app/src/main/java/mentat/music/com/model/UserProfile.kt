package mentat.music.com.model

/**
 * USER PROFILE 🕵️‍♂️
 * Ficha persistente de un usuario analizado.
 * Se guarda en la colección 'user_profiles'.
 */
data class UserProfile(
    val did: String = "",          // DNI único (Clave del documento)
    val handle: String = "",       // @usuario
    val bio: String = "",          // Lo que dice de sí mismo
    val analisisIA: String = "",   // El resumen psicológico de Mentat
    val fechaAnalisis: String = "" // Para saber cuándo lo investigamos (ISO Date)
)