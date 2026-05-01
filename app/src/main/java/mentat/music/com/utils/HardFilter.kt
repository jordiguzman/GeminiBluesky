package mentat.music.com.utils

import java.text.Normalizer
import java.util.regex.Pattern

/**
 * HARD FILTER 🛡️
 * Este objeto es el "Portero de Discoteca".
 * Su trabajo es detectar palabras prohibidas antes de gastar ni un céntimo en IA.
 */
object HardFilter {

    // 1. LISTA MAESTRA DE PALABRAS PROHIBIDAS
    // Al estar aquí en código, es rapidísimo.
    private val blackList = setOf(
        // --- POLÍTICA USA (Tu lista original + extras) ---
        "trump", "biden", "kamala", "harris", "vance", "desantis",
        "democrat", "republican", "gop", "maga", "white house",
        "senate", "congress", "supreme court", "potus", "election",
        "gun control", "abortion", "border crisis", "immigrants",
        "woke", "antifa", "fascist", "nazi", "communist",

        // --- POLÍTICA ESPAÑA (El punto ciego que tenías) ---
        "sanchez", "feijoo", "abascal", "yolanda diaz", "puigdemont",
        "psoe", "pp", "vox", "sumar", "podemos", "junts", "erc", "bildu",
        "cup", "pnv", "independencia", "amnistia", "referendum",
        "gobierno", "ministro", "parlamento", "congreso", "senado",
        "generalitat", "moncloa", "zarzuela", "rey felipe",

        // --- TEMAS SENSIBLES / TÓXICOS ---
        "war", "guerra", "ukraine", "ucrania", "israel", "palestine", "gaza",
        "hamas", "russia", "putin", "zelensky", "nato", "otan",
        "kill", "death", "suicide", "murder", "rape", "assault",
        "muerte", "asesinato", "violacion", "suicidio",

        // --- PERSONAJES POLÉMICOS (De tu JSON original) ---
        "elon", "musk", "tesla", "twitter", "x.com", // Si quieres evitar el tema X/Twitter
        "kanye", "tate"
    )

    private var compiledRegex: Pattern? = null

    init {
        construirMuro()
    }

    /**
     * Compila la lista en una expresión regular gigante optimizada.
     * Se ejecuta una sola vez al iniciar la App.
     */
    private fun construirMuro() {
        if (blackList.isEmpty()) return

        // Creamos un patrón tipo: \b(trump|biden|psoe)\b
        // \b asegura que sea palabra completa (evita borrar "class" por contener "ass")
        val patternString = blackList.joinToString("|") { Regex.escape(it) }

        // CASE_INSENSITIVE: Da igual mayúsculas/minúsculas
        // UNICODE_CASE: Maneja mejor caracteres internacionales
        compiledRegex = Pattern.compile("\\b($patternString)\\b", Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
    }

    /**
     * FUNCIÓN PRINCIPAL
     * Devuelve TRUE si el texto contiene mierda.
     */
    fun esContenidoProhibido(textoOriginal: String): Boolean {
        if (textoOriginal.isBlank()) return true
        if (compiledRegex == null) return false

        // 1. Limpieza previa: Quitamos acentos para asegurar el tiro
        // (Así "Sánchez" se convierte en "sanchez" y coincide con la lista)
        val textoLimpio = normalizarTexto(textoOriginal)

        // 2. Disparo
        return compiledRegex!!.matcher(textoLimpio).find()
    }

    /**
     * Elimina acentos y diacríticos.
     * Ej: "El camión de Sánchez" -> "el camion de sanchez"
     */
    private fun normalizarTexto(input: String): String {
        val nfd = Normalizer.normalize(input, Normalizer.Form.NFD)
        val pattern = "\\p{InCombiningDiacriticalMarks}+".toRegex()
        return pattern.replace(nfd, "").lowercase()
    }
}