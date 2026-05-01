package mentat.music.com.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import mentat.music.com.model.Borrador
import mentat.music.com.model.MemoriaConversacion

// 1. LA ENTIDAD (La tabla de datos de Caza)
@Entity(tableName = "tabla_caza")
data class PostEntity(
    @PrimaryKey val id: String,
    val tipo: String,
    val textoIA: String,
    val postOriginal: String,
    val fecha: String,
    val uriOriginal: String,
    val cidOriginal: String,
    val imagenUrl: String,
    val estado: String = "pendiente"
) {
    fun toBorrador(): Borrador {
        return Borrador(
            id = id,
            opcion = 0,
            tipo = tipo,
            texto = textoIA,
            postOriginal = postOriginal,
            estado = estado,
            fecha = fecha,
            uriOriginal = uriOriginal,
            cidOriginal = cidOriginal,
            imagenUrl = imagenUrl
        )
    }
}

// LA ENTIDAD TUMBA (Para los Zombis)
@Entity(tableName = "rejected_posts")
data class RejectedPost(
    @PrimaryKey val uri: String, // El ID del post que queremos "matar"
    val timestamp: Long = System.currentTimeMillis()
)

// 2. LOS DAOS (Las herramientas)

@Dao
interface PostDao {
    @Query("SELECT * FROM tabla_caza WHERE estado = 'pendiente' ORDER BY fecha DESC")
    fun obtenerPendientes(): Flow<List<PostEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(post: PostEntity)

    @Query("DELETE FROM tabla_caza WHERE id = :id")
    suspend fun borrar(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM tabla_caza WHERE id = :id)")
    suspend fun existe(id: String): Boolean

    @Query("DELETE FROM tabla_caza")
    suspend fun borrarTodo()
}

// --- NUEVO: El DAO para los posts rechazados ---
@Dao
interface RejectedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rejectedPost: RejectedPost)

    @Query("SELECT uri FROM rejected_posts")
    suspend fun getAllRejectedUris(): List<String>
}

// 3. LA BASE DE DATOS (El cerebro actualizado)
@Database(
    entities = [
        PostEntity::class,
        MemoriaConversacion::class,
        RejectedPost::class,
        ObraMaestraEntity::class // <--- NUEVO: La tabla del JSONL
    ],
    version = 5, // <--- NUEVO: Subimos versión a 4 para aplicar cambios
    exportSchema = false
)
abstract class MentatDatabase : RoomDatabase() {

    abstract fun postDao(): PostDao
    abstract fun memoriaDao(): MemoriaDao
    abstract fun rejectedDao(): RejectedDao
    abstract fun obraMaestraDao(): ObraMaestraDao // <--- NUEVO: Acceso al DAO del JSONL

    companion object {
        @Volatile
        private var INSTANCE: MentatDatabase? = null

        fun getDatabase(context: Context): MentatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MentatDatabase::class.java,
                    "mentat_database_v1"
                )
                    .fallbackToDestructiveMigration() // Esto borra la BD antigua y crea la nueva limpia (v4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}