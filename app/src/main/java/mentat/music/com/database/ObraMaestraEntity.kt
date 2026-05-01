package mentat.music.com.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "obras_maestras")
data class ObraMaestraEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val artista: String,
    val obraExcluida: String,
    val procesado: Boolean = false,
    val descartado: Boolean = false,
    val ultimaVezPublicado: Long? = null  // Timestamp Unix en milisegundos
)