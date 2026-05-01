package mentat.music.com.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import mentat.music.com.utils.BrainLoader
import mentat.music.com.utils.HardFilter

class MentatBrain(
    private val context: Context,
    private val model: GenerativeModel
) {
    private var jsonPersonalidad: String = "{}"

    // ⚡ NUEVO: Aquí guardaremos el "ticket" de pensamiento que exige Gemini 3.1
    private var firmaPensamientoActual: String? = null

    init {
        inicializarCerebro()
    }

    private fun inicializarCerebro() {
        try {
            jsonPersonalidad = BrainLoader.cargarJSON(context)
        } catch (e: Exception) {
            Log.e("MENTAT_BRAIN", "❌ Error cargando personalidad", e)
            jsonPersonalidad = "{ \"error\": \"Default mode\" }"
        }
    }

    /**
     * MODO CAZA (TIMELINE)
     */
    suspend fun evaluarCaza(
        autor: String,
        textoPost: String,
        perfilContexto: String = "",
        imagen: Bitmap? = null,
        idioma: String = "auto"
    ): String {

        // 🛡️ 1. Hard Filter
        if (HardFilter.esContenidoProhibido(textoPost)) {
            Log.d("MENTAT_BRAIN", "⛔ Bloqueado por HardFilter local.")
            return "SKIP"
        }

        // ⚡ LIMPIEZA DE MEMORIA: Al analizar un post nuevo del timeline,
        // borramos cualquier "pensamiento" anterior para no mezclar contextos.
        firmaPensamientoActual = null

        // 🚀 PROMPT DINÁMICO
        val prompt = construirPromptDinamico(autor, textoPost, perfilContexto, idioma)

        // Debug Logs
        Log.d("VER_PROMPT", "\n⚠️⚠️⚠️ --- PROMPT DINÁMICO --- ⚠️⚠️⚠️")
        Log.d("VER_PROMPT", prompt)
        Log.d("VER_PROMPT", "⚠️⚠️⚠️ --------------------------- ⚠️⚠️⚠️\n")

        val logInput = """
        📥 --- INPUT PARA GEMINI (@$autor) ---
        $textoPost
        ---------------------------------------
        """.trimIndent()
        Log.d("DEBUG_INPUT_IA", logInput)

        return try {
            val response = model.generateContent(
                content {
                    // ⚡ ENVÍO DE FIRMA (Descomentar al actualizar a Gemini 3.1)
                    // firmaPensamientoActual?.let { firma ->
                    //     text("THOUGHT_SIGNATURE: $firma")
                    // }

                    if (imagen != null) image(imagen)
                    text(prompt)
                }
            )

            // ⚡ CAPTURA DE FIRMA (Descomentar al actualizar a Gemini 3.1)
            // firmaPensamientoActual = response.thoughtSignature

            val rawText = response.text?.trim() ?: "SKIP"
            Log.d("DEBUG_OUTPUT_IA", "🤖 SALIDA GEMINI: $rawText")

            val textoLimpio = rawText.replaceFirstChar { it.uppercase() }

            if (textoLimpio.uppercase().contains("SKIP")) "SKIP" else textoLimpio
        } catch (e: Exception) {
            Log.e("MENTAT_BRAIN", "Error IA en Caza", e)
            "SKIP"
        }
    }

    // 🧠 VERSIÓN 7.0: ECONOMY MODE (ANTI-LORO + ANTI-GASTO)
    private fun construirPromptDinamico(autor: String, textoPost: String, perfilContexto: String, idiomaForzado: String): String {

        // 1. MODOS (Detectores)
        val esMusica = textoPost.contains("[CONTEXTO EXTERNO -") // Simplificado para pillar todas las plataformas
        val esNoticia = textoPost.contains("WEB ARTICLE")

        // 2. IDIOMA (Comprimido: 50 tokens -> 15 tokens)
        val reglaIdioma = "LANG: ES if input is ES/CAT/GAL/EUS. EN otherwise."

        // 3. SISTEMA CENTRAL (Comprimido: Estilo Telegráfico)
        val sistema = """
            ROLE: Juan Mentat (Musician/Coder/Observer, BCN). Tone: Warm, casual, diplomatic.
            
            STRICT RULES:
            1. NO REPETITION: Never repeat names/titles/content. Use pronouns (it/that/him).
            2. NO DESCRIPTION: Assume shared context. Don't describe text/image.
            3. FORMAT: Under 20 words. No slang. No exclamations (!).
        """.trimIndent()

        // 4. MODO ESPECÍFICO (Comprimido)
        val instruccionModo = when {
            esMusica -> "CTX: MUSIC. Focus on atmosphere/vibe. Open-minded. Don't name the song."
            esNoticia -> "CTX: NEWS. Give specific thought. NO summary. SKIP if US Politics/Hate."
            else -> "CTX: SOCIAL. Add wit/value. Don't just agree. SKIP if Hate/Spam."
        }

        // 5. CONTEXTO DE PERFIL (CORTAFUEGOS DE TOKENS)
        val infoUsuario = if (perfilContexto.isNotEmpty()) {
            "USER VIBE: ${perfilContexto.take(100)}..."
        } else ""

        // 6. ENSAMBLAJE FINAL (Minimalista)
        return """
            $sistema
            $instruccionModo
            $infoUsuario
            $reglaIdioma
            
            INPUT (@$autor): $textoPost
            
            REPLY:
        """.trimIndent()
    }
}

// 🕵️‍♂️ Detector de idioma
fun detectarIdioma(texto: String): String {
    val palabras = texto.lowercase().split(" ", "\n", ".", ",").filter { it.isNotEmpty() }

    val englishTriggers = setOf("the", "and", "is", "to", "of", "in", "it", "you", "my", "for")
    val spanishTriggers = setOf("el", "la", "y", "es", "de", "en", "que", "un", "los", "por")

    var scoreEn = 0
    var scoreEs = 0

    palabras.forEach { p ->
        if (englishTriggers.contains(p)) scoreEn++
        if (spanishTriggers.contains(p)) scoreEs++
    }

    return when {
        scoreEn > scoreEs -> "en"
        scoreEs > scoreEn -> "es"
        else -> "auto"
    }
}