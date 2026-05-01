package mentat.music.com.model

// Esta clase define la estructura EXACTA de lo que subiremos a Firebase
data class Borrador(
    var id: String = "",
    val opcion: Int = 0,            // Ej: 1, 2, 3
    val tipo: String = "",          // Ej: "Sarcástica"
    val texto: String = "",         // La respuesta de la IA
    val postOriginal: String = "",
    val autorPost: String? = null,// Para saber a qué contestamos
    val estado: String = "pendiente", // pendiente, aprobado, descartado
    val fecha: String ="",
    val uriOriginal: String = "",
    val cidOriginal: String = "",
    // ⚡ NUEVO CAMPO: La foto
    val imagenUrl: String = ""
)
// Necesitamos valores por defecto (= "") para que Firebase pueda deserializar sin errores.