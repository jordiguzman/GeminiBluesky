package mentat.music.com.utils

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

suspend fun contarDocumentos(nombreColeccion: String): Long {
    val db = Firebase.firestore
    return try {
        // 👇 Esto es la magia: .count()
        val snapshot = db.collection(nombreColeccion)
            .count()
            .get(AggregateSource.SERVER) // Obliga a contar en el servidor
            .await() // Espera el resultado sin bloquear

        val total = snapshot.count
        Log.d("MENTAT_DB", "📊 Total en $nombreColeccion: $total")
        total
    } catch (e: Exception) {
        Log.e("MENTAT_DB", "❌ Error contando: ${e.message}")
        0L
    }
}