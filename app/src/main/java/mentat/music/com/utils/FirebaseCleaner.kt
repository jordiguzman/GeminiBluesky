package mentat.music.com.utils

import android.util.Log
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import kotlin.math.min

object FirebaseCleaner {
    // ELIMINAMOS la variable 'db' de aquí para evitar el memory leak

    private const val LIMITE_MAXIMO = 80
    private const val BORRADO_POR_LOTE = 20

    suspend fun verificarYLimpiar(coleccion: String) {
        try {
            // LA DECLARAMOS AQUÍ ADENTRO
            // Al estar dentro de la función, nace y muere con ella. No hay leak.
            val db = FirebaseFirestore.getInstance()

            val collectionRef = db.collection(coleccion)

            // 1. Contamos
            val snapshot = collectionRef.count().get(AggregateSource.SERVER).await()
            val totalDocs = snapshot.count

            Log.d("MENTAT_CLEAN", "Colección $coleccion tiene $totalDocs documentos.")

            if (totalDocs > LIMITE_MAXIMO) {
                val sobran = totalDocs - LIMITE_MAXIMO
                val aBorrar = min(sobran.toInt(), BORRADO_POR_LOTE)

                Log.d("MENTAT_CLEAN", "Sobran $sobran. Borrando los $aBorrar más antiguos...")

                // 2. Buscamos los viejos
                // OJO: Asegúrate que tu campo en Firebase se llame "fecha"
                val viejos = collectionRef
                    .orderBy("fecha", Query.Direction.ASCENDING)
                    .limit(aBorrar.toLong())
                    .get()
                    .await()

                // 3. Borramos
                if (!viejos.isEmpty) {
                    val batch = db.batch()
                    for (doc in viejos.documents) {
                        batch.delete(doc.reference)
                    }
                    batch.commit().await()
                    Log.d("MENTAT_CLEAN", "Limpieza completada en $coleccion. Se borraron $aBorrar docs.")
                }
            } else {
                Log.d("MENTAT_CLEAN", "No es necesario limpiar $coleccion.")
            }

        } catch (e: Exception) {
            Log.e("MENTAT_CLEAN", "Error intentando limpiar $coleccion: ${e.message}")
        }
    }
}