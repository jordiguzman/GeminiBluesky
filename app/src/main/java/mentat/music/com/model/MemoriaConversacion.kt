package mentat.music.com.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memoria_conversacion")
data class MemoriaConversacion(
    // Usaremos la URI del post original (Root) como clave.
    // Así, si alguien contesta en ese hilo, sabremos qué dijimos nosotros ahí.
    @PrimaryKey val rootUri: String,

    val textoIa: String,             // Lo que contestó Mentat
    val fecha: Long = System.currentTimeMillis() // Para saber cuándo fue
)