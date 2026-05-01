package mentat.music.com

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mentat.music.com.constans.AppConfig
import mentat.music.com.ui.AppNavigation
import mentat.music.com.ui.theme.MentatMusicComTheme // <--- IMPORTANTE: Importamos tu tema
import mentat.music.com.database.MentatDatabase
import mentat.music.com.utils.FirebaseCleaner

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("DEBUG_MENTAT", "🏁 MainActivity: Iniciando onCreate")

        var db: FirebaseFirestore? = null
        var model: GenerativeModel? = null
        var errorInit: String? = null
        val database = MentatDatabase.getDatabase(this)

        // 1. INTENTO DE INICIALIZACIÓN
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
            db = FirebaseFirestore.getInstance()

            model = GenerativeModel(
                modelName = AppConfig.GEMINI_MODEL,
                apiKey = AppConfig.GEMINI_API_KEY,
                generationConfig = generationConfig {
                    temperature = 0.2f
                    topK = 40
                    topP = 0.95f
                }
            )

        } catch (e: Exception) {
            Log.e("DEBUG_MENTAT", "❌ CRASH EN INICIALIZACIÓN: ${e.message}", e)
            errorInit = e.message
        }

        // 2. LANZAR LA UI
        setContent {
            MentatMusicComTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (errorInit != null) {
                        Text(text = "ERROR CRÍTICO AL INICIAR:\n$errorInit")
                    } else if (db != null && model != null) {

                        // 2. PASAMOS LA BASE DE DATOS A LA NAVEGACIÓN (NUEVO)
                        AppNavigation(
                            db = db!!,
                            model = model!!,
                            database = database // <--- AÑADIDO
                        )

                    } else {
                        Text(text = "Cargando servicios...")
                    }
                }
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            FirebaseCleaner.verificarYLimpiar("data_buzon")
            FirebaseCleaner.verificarYLimpiar("data_caza")
            // ⚡ AQUÍ ESTÁ LA MAGIA: Arrancamos el motor de la base de datos
            Log.d("JSONL", "Llamando al Sincronizador desde MainActivity...")
            mentat.music.com.network.SincronizadorObras.sincronizarSiEsNecesario(this@MainActivity)
        }
    }
}