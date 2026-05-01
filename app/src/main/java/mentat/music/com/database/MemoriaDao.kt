package mentat.music.com.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import mentat.music.com.model.MemoriaConversacion

// 1. TU DAO ORIGINAL (No tocamos nada)
@Dao
interface MemoriaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(memoria: MemoriaConversacion)

    @Query("SELECT * FROM memoria_conversacion WHERE rootUri = :rootUri")
    suspend fun obtenerContexto(rootUri: String): MemoriaConversacion?

    @Query("DELETE FROM memoria_conversacion")
    suspend fun borrarTodo()
}

// 2. EL DAO DE OBRAS MAESTRAS
@Dao
interface ObraMaestraDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(obras: List<ObraMaestraEntity>)

    @Query("SELECT COUNT(id) FROM obras_maestras")
    suspend fun contarObras(): Int

    // Devuelve un artista pendiente que no haya sido publicado en los últimos 15 días.
    // ORDER BY RANDOM() evita que siempre salga el mismo cuando hay muchos duplicados.
    @Query("""
        SELECT * FROM obras_maestras 
        WHERE procesado = 0 AND descartado = 0 
        AND artista NOT IN (
            SELECT DISTINCT artista FROM obras_maestras 
            WHERE ultimaVezPublicado IS NOT NULL 
            AND ultimaVezPublicado > :hace15Dias
        )
        ORDER BY RANDOM() 
        LIMIT 1
    """)
    suspend fun getSiguientePendiente(hace15Dias: Long): ObraMaestraEntity?

    // Marca el registro como procesado y guarda el timestamp actual.
    @Query("UPDATE obras_maestras SET procesado = 1, ultimaVezPublicado = :ahora WHERE id = :id")
    suspend fun marcarProcesado(id: Int, ahora: Long)

    @Query("UPDATE obras_maestras SET descartado = 1 WHERE id = :id")
    suspend fun marcarDescartado(id: Int)
}
