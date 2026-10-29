package com.marklvi.assistant

import java.util.Locale

/** Sinhala-first intent normalizer for common phone actions. Unknown phrases are preserved
 * verbatim so the configured desktop model can receive natural Sinhala input. */
object SinhalaLanguage {
    private val rules = listOf(
        Regex("^(?:මුල් පිටුවට යන්න|ගෙදරට යන්න|හෝම් එකට යන්න|හෝම් යන්න|මුල් තිරයට යන්න)$") to "go home",
        Regex("^(?:ආපසු යන්න|පසුපසට යන්න|ආපහු යන්න|බැක් යන්න)$") to "go back",
        Regex("^(?:අලුත්ම ඇප් පෙන්වන්න|විවෘත කර ඇති ඇප් පෙන්වන්න|රීසන්ට් ඇප් පෙන්වන්න)$") to "show recent apps",
        Regex("^(?:දැනුම්දීම් කියවන්න|නොටිෆිකේෂන් කියවන්න|දැනුම්දීම් පෙන්වන්න)$") to "read notifications",
        Regex("^(?:ටෝච් එක දල්වන්න|ෆ්ලෑෂ් ලයිට් එක දල්වන්න|ෆ්ලෑෂ් ලයිට් දල්වන්න)$") to "flashlight on",
        Regex("^(?:ටෝච් එක නිවන්න|ෆ්ලෑෂ් ලයිට් එක නිවන්න|ෆ්ලෑෂ් ලයිට් නිවන්න)$") to "flashlight off",
        Regex("^(?:ටෝච් එක|ෆ්ලෑෂ් ලයිට් එක) (?:ඔන් කරන්න|දල්වන්න)$") to "flashlight on",
        Regex("^(?:ටෝච් එක|ෆ්ලෑෂ් ලයිට් එක) (?:ඕෆ් කරන්න|නිවන්න)$") to "flashlight off",
        Regex("^(?:වොලියුම් එක වැඩි කරන්න|ශබ්දය වැඩි කරන්න|සද්දෙ වැඩි කරන්න)$") to "volume up",
        Regex("^(?:වොලියුම් එක අඩු කරන්න|ශබ්දය අඩු කරන්න|සද්දෙ අඩු කරන්න)$") to "volume down",
        Regex("^(?:ශබ්දය නිහඬ කරන්න|සද්දෙ නවත්වන්න|මියුට් කරන්න)$") to "mute",
        Regex("^(?:වයිෆයි සැකසුම් අරින්න|වයි ෆයි සැකසුම් අරින්න)$") to "wifi settings",
        Regex("^(?:බ්ලූටූත් සැකසුම් අරින්න|බ්ලූටූත් අරින්න)$") to "bluetooth settings",
        Regex("^(?:ඩිස්ප්ලේ සැකසුම් අරින්න|තිර සැකසුම් අරින්න)$") to "display settings",
        Regex("^(?:දුරකථන සැකසුම් අරින්න|සැකසුම් අරින්න)$") to "phone settings",
        Regex("^(?:කැමරාව අරින්න|කැමරා එක අරින්න|කැමරාව විවෘත කරන්න)$") to "open camera",
        Regex("^(?:යූටියුබ්|යූ ටියුබ්) (?:අරින්න|විවෘත කරන්න)$") to "open YouTube",
        Regex("^(?:වට්ස්ඇප්|වට්ස් ඇප්) (?:අරින්න|විවෘත කරන්න)$") to "open WhatsApp",
        Regex("^(?:ගූගල් ක්‍රෝම්|ක්‍රෝම්) (?:අරින්න|විවෘත කරන්න)$") to "open Chrome",
        Regex("^(.+?) (?:අරින්න|විවෘත කරන්න)$") to "open $1",
        Regex("^(?:ගූගල් එකේ සොයන්න|අන්තර්ජාලයේ සොයන්න) (.+)$") to "search $1",
        Regex("^(.+?) යන්න මාර්ගය පෙන්වන්න$") to "navigate to $1",
        Regex("^(.+?) වෙත යන්න මාර්ගය පෙන්වන්න$") to "navigate to $1"
    )

    fun toCommand(input: String): String {
        val trimmed = input.trim().replace(Regex("\\s+"), " ")
        if (trimmed.isEmpty()) return trimmed
        val noWake = trimmed.replace(Regex("(?i)^\\s*jarvis[ ,:]*"), "").trim()
        val normalized = noWake.lowercase(Locale.ROOT)
        for ((pattern, replacement) in rules) {
            if (pattern.matches(normalized)) return pattern.replace(normalized, replacement)
        }
        return noWake
    }

    fun localizeResponse(text: String, language: String): String {
        if (language == "mixed") {
            val singlish = mapOf(
                "Please say or type a command." to "Command ekak kiyanna hari type karanna.",
                "Going home." to "Hari, home screen ekata yanawa.",
                "Going back." to "Hari, back yanawa.",
                "Showing recent apps." to "Recent apps pennanawa.",
                "Opening camera." to "Hari, camera eka open karanawa.",
                "Opening web search." to "Hari, web search eka open karanawa.",
                "Opening maps." to "Hari, maps open karanawa.",
                "Volume command sent." to "Hari, volume eka change kala.",
                "Flashlight on." to "Hari, torch eka on kala.",
                "Flashlight off." to "Hari, torch eka off kala.",
                "Command sent to desktop MARK LVI." to "Hari, command eka desktop MARK LVI ekata yawwa.",
                "Configure the desktop AI bridge first." to "Mulin desktop AI bridge eka set karanna.",
                "Speech recognition is not available on this phone." to "Me phone eke speech recognition support naha.",
                "Microphone permission was not granted. You can still type commands." to "Microphone permission dunne naha. Commands type karanna puluwan."
            )
            singlish[text]?.let { return it }
            Regex("^Opening (.+)\\.$", RegexOption.IGNORE_CASE).find(text)?.let { return "Hari, ${it.groupValues[1]} open karanawa." }
            return text
        }
        if (language != "si") return text
        val translations = mapOf(
            "Please say or type a command." to "කරුණාකර විධානයක් කියන්න හෝ ටයිප් කරන්න.",
            "Going home." to "මුල් තිරයට යනවා.",
            "Going back." to "පෙර තිරයට යනවා.",
            "Showing recent apps." to "මෑතකදී භාවිත කළ යෙදුම් පෙන්වනවා.",
            "Opening Wi-Fi settings." to "වයිෆයි සැකසුම් විවෘත කරනවා.",
            "Opening Bluetooth settings." to "බ්ලූටූත් සැකසුම් විවෘත කරනවා.",
            "Opening display settings." to "තිර සැකසුම් විවෘත කරනවා.",
            "Opening phone settings." to "දුරකථන සැකසුම් විවෘත කරනවා.",
            "Opening camera." to "කැමරාව විවෘත කරනවා.",
            "Opening web search." to "අන්තර්ජාල සෙවුම විවෘත කරනවා.",
            "Opening maps." to "සිතියම විවෘත කරනවා.",
            "Volume command sent." to "ශබ්ද මට්ටම වෙනස් කළා.",
            "Flashlight on." to "ෆ්ලෑෂ් ලයිට් එක දැල්වුවා.",
            "Flashlight off." to "ෆ්ලෑෂ් ලයිට් එක නිවා දැමුවා.",
            "Command sent to desktop MARK LVI." to "විධානය පරිගණකයේ MARK LVI වෙත යැව්වා.",
            "Configure the desktop AI bridge first." to "පළමුව පරිගණකයේ AI සම්බන්ධතාව සකසන්න.",
            "Speech recognition is not available on this phone." to "මෙම දුරකථනයේ හඬ හඳුනාගැනීම ලබාගත නොහැක.",
            "Microphone permission was not granted. You can still type commands." to "මයික්‍රෆෝන අවසරය ලබා දී නැහැ. ඔබට තවමත් විධාන ටයිප් කළ හැකියි."
        )
        translations[text]?.let { return it }
        Regex("^Opening (.+)\\.$", RegexOption.IGNORE_CASE).find(text)?.let { return "${it.groupValues[1]} විවෘත කරනවා." }
        Regex("^Opening (.+) settings\\.$", RegexOption.IGNORE_CASE).find(text)?.let { return "${it.groupValues[1]} සැකසුම් විවෘත කරනවා." }
        Regex("^I couldn't find an app matching (.+)\\.$", RegexOption.IGNORE_CASE).find(text)?.let { return "${it.groupValues[1]} නමින් යෙදුමක් හමු වුණේ නැහැ." }
        Regex("^Review the (.+) in your (.+) app\\.$", RegexOption.IGNORE_CASE).find(text)?.let { return "${it.groupValues[2]} යෙදුමේ ${it.groupValues[1]} පරීක්ෂා කර තහවුරු කරන්න." }
        return text
    }
}
