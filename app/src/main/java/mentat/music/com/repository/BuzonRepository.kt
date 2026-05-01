package mentat.music.com.repository

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import mentat.music.com.model.Borrador

class BuzonRepository(private val db: FirebaseFirestore) {

    val blueskyRepo = BlueskyRepository()

    // 1. ARCHIVAR (Botón Rojo / Ignorar)
    // Marca el documento como "rechazado" para que desaparezca de la lista pero la app recuerde no volver a procesarlo.
    fun archivarBorrador(coleccion: String, id: String, onSuccess: () -> Unit) {
        db.collection(coleccion).document(id)
            .update("estado", "rechazado")
            .addOnSuccessListener {
                Log.d("BUZON_REPO", "🗑️ Archivado: $id en $coleccion")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e("BUZON_REPO", "❌ Error al archivar", e)
            }
    }

    // 2. APROBAR Y PUBLICAR (Botón Morado / Rosa)
    // Llama a la API real de Bluesky y si funciona, marca como "aprobado" en Firebase.
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun aprobarBorrador(coleccion: String, borrador: Borrador, textoFinal: String, onSuccess: () -> Unit) {
        val bskyRepo = BlueskyRepository() // Instanciamos el repo de conexión
        var exito = false

        // DECISIÓN: ¿Es un Like o una Respuesta?
        if (textoFinal == "ACTION_LIKE") {
            // MODO LIKE REAL ❤️
            // Usamos las llaves guardadas (uriOriginal, cidOriginal)
            if (borrador.uriOriginal.isNotEmpty() && borrador.cidOriginal.isNotEmpty()) {
                exito = bskyRepo.darLike(borrador.uriOriginal, borrador.cidOriginal)
            } else {
                Log.e("BUZON_REPO", "❌ Faltan datos (URI/CID) para dar Like. ¿Es un borrador antiguo?")
            }
        } else {
            // MODO RESPUESTA DE TEXTO 💬
            if (borrador.uriOriginal.isNotEmpty() && borrador.cidOriginal.isNotEmpty()) {
                exito = bskyRepo.publicarRespuesta(textoFinal, borrador.uriOriginal, borrador.cidOriginal)
            } else {
                Log.e("BUZON_REPO", "❌ Faltan datos (URI/CID) para responder. ¿Es un borrador antiguo?")
            }
        }

        if (exito) {
            // Si la API de Bluesky dijo OK, actualizamos Firebase para cerrar la tarea
            db.collection(coleccion).document(borrador.id)
                .update(
                    mapOf(
                        "estado" to "aprobado",
                        "texto" to textoFinal
                    )
                )
                .addOnSuccessListener {
                    Log.d("BUZON_REPO", "✅ Tarea completada y registrada en DB")
                    onSuccess()
                }
        } else {
            Log.e("BUZON_REPO", "❌ Fallo al conectar con Bluesky API. No se marca como aprobado.")
        }
    }

    // 3. MOVER (Escalar / Responder más tarde)
    // Mueve un borrador de una colección a otra (ej: de Buzón a Caza/Pendientes)
    fun moverBorrador(
        borrador: Borrador,
        origen: String,
        destino: String,
        onSuccess: () -> Unit
    ) {
        // Preparamos el borrador para su nuevo destino
        val borradorMovido = borrador.copy(
            tipo = "${borrador.tipo} (MANUAL)",
            // Si era un "ACTION_LIKE", borramos el texto para que la IA de Caza lo rellene o quede limpio para escribir
            texto = if (borrador.texto.contains("ACTION_LIKE")) "" else borrador.texto,
            estado = "pendiente" // Reseteamos estado para que aparezca en la lista nueva
        )

        // Usamos una transacción por lotes (Batch) para que sea atómico
        val batch = db.batch()

        // Paso A: Crear en destino
        val docDestino = db.collection(destino).document(borrador.id)
        batch.set(docDestino, borradorMovido)

        // Paso B: Borrar de origen
        val docOrigen = db.collection(origen).document(borrador.id)
        batch.delete(docOrigen)

        // Ejecutar
        batch.commit()
            .addOnSuccessListener {
                Log.d("BUZON_REPO", "🔀 Movido de $origen a $destino: ${borrador.id}")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e("BUZON_REPO", "❌ Error al mover borrador", e)
            }
    }
    // 4. DAR LIKE DIRECTO (Botón Corazón en Timeline)
    suspend fun enviarLike(uri: String, cid: String): Boolean {
        val bskyRepo = BlueskyRepository() // Instanciamos la conexión
        return bskyRepo.darLike(uri, cid)  // Usamos la función que ya existe
    }
    // ACCIÓN 1: CITAR (Quote Post)
    suspend fun publicarCita(coleccion: String, borrador: Borrador, textoFinal: String, onSuccess: () -> Unit) {
        // Llamamos a Bluesky
        val exito = blueskyRepo.publicarCita(textoFinal, borrador.uriOriginal, borrador.cidOriginal)

        if (exito) {
            // Si sale bien, borramos el borrador de Firestore
            try {
                db.collection(coleccion).document(borrador.id).delete().await()
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                // Log de error si quieres, pero la acción social ya se hizo
            }
        }
    }

    // ACCIÓN 2: REPOSTEAR (Flechas)
    suspend fun repostear(coleccion: String, borrador: Borrador, onSuccess: () -> Unit) {
        val exito = blueskyRepo.crearRepost(borrador.uriOriginal, borrador.cidOriginal)

        if (exito) {
            try {
                db.collection(coleccion).document(borrador.id).delete().await()
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {}
        }
    }

    // ACCIÓN 3: SEGUIR (Follow Back)
    suspend fun seguirUsuario(coleccion: String, borrador: Borrador, onSuccess: () -> Unit) {
        // Sacamos el DID de la URI (truco sucio pero rápido: at://did:plc:xxxx/...)
        val did = try { borrador.uriOriginal.split("/")[2] } catch (e: Exception) { "" }

        if (did.isNotEmpty()) {
            val exito = blueskyRepo.seguirUsuario(did)
            if (exito) {
                try {
                    db.collection(coleccion).document(borrador.id).delete().await()
                    withContext(Dispatchers.Main) { onSuccess() }
                } catch (e: Exception) {}
            }
        }
    }
}
