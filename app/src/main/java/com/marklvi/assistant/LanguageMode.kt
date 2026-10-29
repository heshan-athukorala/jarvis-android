package com.marklvi.assistant

/** Lightweight language detection for choosing response style and TTS voice.
 * Speech recognition itself is provided by Android and may have device-specific limits. */
object LanguageMode {
    private val sinhalaScript = Regex("[\\u0D80-\\u0DFF]")
    private val singlishHints = listOf(
        "karanna", "karapan", "karanna puluwanda", "eka", "meka", "mokakda", "kohomada",
        "kiyanna", "open karanna", "close karanna", "wena", "mata", "oyata", "hari",
        "nathnam", "thiyenne", "puluwan", "ba", "epa", "denna", "ganna", "yanna",
        "hadanna", "balanna", "wada", "machan", "bro", "aiye", "ane"
    )

    /** Returns si, en, or mixed. */
    fun detect(text: String): String {
        if (text.isBlank()) return "en"
        val hasSinhala = sinhalaScript.containsMatchIn(text)
        val latin = text.count { it in 'A'..'Z' || it in 'a'..'z' }
        val words = text.lowercase().replace(Regex("[^a-z\\s]"), " ").split(Regex("\\s+")).filter { it.isNotBlank() }
        val hasSinglish = singlishHints.any { hint ->
            val h = hint.lowercase()
            if (h.contains(' ')) text.lowercase().contains(h) else words.contains(h)
        }
        return when {
            hasSinhala && (latin > 0 || hasSinglish) -> "mixed"
            hasSinhala -> "si"
            hasSinglish -> "mixed"
            else -> "en"
        }
    }

    /** Use Sinhala TTS for Sinhala-script replies; English/system TTS for Latin-script replies. */
    fun ttsLanguageFor(reply: String, inputLanguage: String): String {
        return when {
            sinhalaScript.containsMatchIn(reply) -> "si"
            inputLanguage == "si" && reply.isNotBlank() -> "si"
            inputLanguage == "mixed" -> "mixed"
            else -> "en"
        }
    }
}
