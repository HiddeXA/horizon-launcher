package nl.thuis.launcher.tv

import androidx.compose.runtime.Immutable

/** Een live-zender uit je Xtream-aanbod. */
@Immutable
data class LiveChannel(
    val id: Int,
    val num: Int,
    val name: String,          // schoon: "NPO 1 HD"
    val region: String?,       // "NL", "BE", "UK" … uit het voorvoegsel, of null
    val logo: String?,
    val epgId: String?,
    val categoryId: String,
    val archiveDays: Int,      // terugkijken: hoeveel dagen (0 = niet)
    val radio: Boolean,
    val adult: Boolean,
)

@Immutable
data class LiveCategory(val id: String, val name: String, val region: String?, val adult: Boolean)

/** Eén programma in de gids. Tijden in seconden sinds 1970 (UTC). */
@Immutable
data class Programme(val start: Long, val end: Long, val title: String) {
    fun isNow(nowSec: Long) = nowSec in start until end
    fun progress(nowSec: Long): Float = if (end > start) ((nowSec - start).toFloat() / (end - start)).coerceIn(0f, 1f) else 0f
}

/**
 * Namen van zenders en categorieën opschonen:
 * "┃NL┃ NPO 1 HD ⏺ʳᵃʷ" → regio "NL", naam "NPO 1 HD"; "┃NL┃ VIAPLAY PREMIER LEAGUE" → "Viaplay Premier League".
 * Pure Kotlin, met unit tests.
 */
object ChannelNames {
    private const val BARS = "|\u2502\u2503\u2506\u2507\u250a\u250b\u2551\uff5c\u00a6\u2758\u2759\u275a\u258f\u258e\u258d\u258c\u2590\u2595\u01c0\u2223"
    private val prefixColon = Regex("""^\s*([A-Z]{2,3})\s*[:|]\s+""")
    private val spaces = Regex("\\s{2,}")

    /** Kort woord dat altijd in hoofdletters blijft. */
    private val keepUpper = setOf("HD", "FHD", "UHD", "SD", "TV", "4K", "8K", "NPO", "RTL", "SBS", "VRT", "VTM", "BBC", "ITV", "UFC", "PPV", "F1", "NBA", "NFL",
        "NHL", "MLB", "ESPN", "DAZN", "UK", "US", "USA", "NL", "BE", "DE", "FR", "EU", "MTV", "CNN", "TLC", "AT5", "RTV", "PDC", "UCL", "BVN", "HBO", "AMC", "FOX",
        "ID", "XL", "MAX", "TV+", "MXGP", "GP", "AVROTROS", "KRO", "NCRV", "EO", "VPRO", "BNN", "WWE", "AEW")

    fun split(raw: String): Pair<String?, String> {
        var s = raw.trim()
        var region: String? = null
        // "┃NL┃ …" of "[NL] …"
        if (s.isNotEmpty() && (s[0] in BARS || s[0] == '[')) {
            val close = (1 until minOf(s.length, 14)).firstOrNull { s[it] in BARS || s[it] == ']' }
            if (close != null) {
                val inner = s.substring(1, close).trim()
                if (inner.isNotEmpty() && inner.all { it.isLetterOrDigit() || it == '+' || it == ' ' }) region = inner.uppercase()
                s = s.substring(close + 1)
            }
        }
        if (region == null) prefixColon.find(s)?.let { m -> region = m.groupValues[1]; s = s.substring(m.range.last + 1) }
        return region to tidy(s)
    }

    /** Rare tekens (superscript-labels als ʳᵃʷ, symbolen als ⏺ ★) weg, spaties netjes. */
    fun tidy(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            val modifier = Character.getType(ch) == Character.MODIFIER_LETTER.toInt() || ch.code in 0x1D00..0x1DBF || ch.code in 0x2070..0x209F
            when {
                (Character.isLetterOrDigit(ch) && !modifier) || ch in " +-&'.:!()/," -> sb.append(ch)
                ch == '|' -> sb.append(" \u00B7 ")
                else -> sb.append(' ')
            }
        }
        return sb.toString().replace(spaces, " ").trim().trim('-', '|', ':', ' ', '.', '\u00B7')
    }

    /** "NEDERLAND HD" → "Nederland HD", maar "NPO 1" blijft "NPO 1". Gemengde namen blijven zoals ze zijn. */
    fun titleCase(s: String): String {
        if (s.any { it.isLowerCase() }) return s
        return s.split(' ').joinToString(" ") { w ->
            when {
                w.isEmpty() -> w
                w.uppercase() in keepUpper || w.length <= 2 || w.any { it.isDigit() } -> w
                else -> w.lowercase().replaceFirstChar { it.uppercase() }
            }
        }
    }

    fun category(raw: String): Pair<String?, String> = split(raw).let { (r, n) -> r to titleCase(n) }
}
