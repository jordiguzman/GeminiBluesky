package mentat.music.com.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import mentat.music.com.model.UserProfile

class ProfileRepository(private val db: FirebaseFirestore) {

    private val COLECCION_PERFILES = "user_profiles"

    // 1. BUSCAR EN ARCHIVO 🗄️
    // Devuelve el perfil si ya lo tenemos fichado, o null si es nuevo.
    suspend fun obtenerPerfil(did: String): UserProfile? {
        return try {
            val doc = db.collection(COLECCION_PERFILES).document(did).get().await()
            if (doc.exists()) {
                doc.toObject(UserProfile::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("PROFILE_REPO", "❌ Error al buscar perfil $did", e)
            null
        }
    }

    // 2. GUARDAR FICHA 💾
    // Guarda el análisis generado por la IA para no tener que gastar tokens la próxima vez.
    suspend fun guardarPerfil(perfil: UserProfile) {
        try {
            db.collection(COLECCION_PERFILES)
                .document(perfil.did)
                .set(perfil)
                .await()
            Log.d("PROFILE_REPO", "✅ Perfil guardado/actualizado: ${perfil.handle}")
        } catch (e: Exception) {
            Log.e("PROFILE_REPO", "❌ Error al guardar perfil ${perfil.handle}", e)
        }
    }
    // ⚡ HERRAMIENTA DE PURGA MASIVA
    // Borra todos los perfiles que fueron rechazados injustamente para re-evaluarlos
    suspend fun purgarPerfilesNegativos() {
        Log.d("PROFILE_REPO", "💀 Iniciando PURGA de perfiles negativos...")

        try {
            val snapshot = db.collection(COLECCION_PERFILES).get().await()
            val batch = db.batch()
            var cont = 0

            for (doc in snapshot.documents) {
                val perfil = doc.toObject(UserProfile::class.java)

                // Si el análisis dice "No", lo marcamos para morir
                if (perfil != null && perfil.analisisIA.contains("Verdict: No", ignoreCase = true)) {
                    batch.delete(doc.reference)
                    cont++
                }
            }

            if (cont > 0) {
                batch.commit().await()
                Log.d("PROFILE_REPO", "✅ PURGA COMPLETADA: Se han eliminado $cont perfiles negativos.")
            } else {
                Log.d("PROFILE_REPO", "🤷‍♂️ No se encontraron perfiles negativos para borrar.")
            }

        } catch (e: Exception) {
            Log.e("PROFILE_REPO", "❌ Error en la purga", e)
        }
    }
}