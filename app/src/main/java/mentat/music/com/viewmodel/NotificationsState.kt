package mentat.music.com.viewmodel

import mentat.music.com.model.Borrador

// Esta clase define CÓMO se ve tu pantalla en cualquier momento
data class NotificationsUiState(
    val borradores: List<Borrador> = emptyList(), // La lista de datos
    val isLoading: Boolean = false,               // ¿Está cargando?
    val mensajeError: String? = null,             // ¿Hay error?
    val mensajeInfo: String? = null,              // Para Snackbars (ej: "Like enviado")

    // Filtros de pestañas (Badge counts)
    val countConversacion: Int = 0,
    val countReacciones: Int = 0,
    val countAudiencia: Int = 0
)