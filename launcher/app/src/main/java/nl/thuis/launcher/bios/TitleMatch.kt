package nl.thuis.launcher.bios

import java.text.Normalizer

/**
 * Titels van IPTV-aanbieders zijn rommelig: "NL | The Matrix (1999) 4K", "EN - Dune: Part Two - 2024".
 * Deze functies maken er een schone titel + jaar van, en een sleutel om op te zoeken en te matchen.
 * Pure Kotlin, zodat het met unit tests te controleren is.
 */
object TitleMatch {
    /** Ophogen als het opschonen verandert: opgeslagen titels worden dan opnieuw berekend. */
    const val VERSION = 2


    private val prefix = Regex("""^\s*(?:\[[^\]]{1,12}]|[\|│┃┆┇┊┋║｜¦❘❙❚▏▎▍▌▐▕ǀ∣]\s*[^\|│┃┆┇┊┋║｜¦❘❙❚▏▎▍▌▐▕ǀ∣]{1,12}?\s*[\|│┃┆┇┊┋║｜¦❘❙❚▏▎▍▌▐▕ǀ∣]|[A-Za-z0-9+]{2,6}\s*[\|│┃┆┇┊┋║｜¦❘❙❚▏▎▍▌▐▕ǀ∣]|[A-Z]{2,3}:\s|[A-Z0-9]{2,3}\s+-\s+|[A-Z]{2,3}-(?=\S))\s*""")
    private val yearRe = Regex("""(?<![\d])(19[2-9]\d|20[0-4]\d)(?![\d])""")
    private val tags = Regex(
        """[\[(]?\b(4k|uhd|fhd|hd|sd|1080p|720p|2160p|hevc|x265|h265|hdr|hdr10|dv|dolby vision|multi[- ]?sub|multisub|nl[- ]?subs?|subs?|dubbed|nl dub|vlaams|imax|remastered|extended|director'?s cut)\b[\])]?""",
        RegexOption.IGNORE_CASE,
    )
    // Eén keer aanmaken: per film opnieuw compileren kostte bij duizenden films tientallen seconden.
    private val yearInBrackets = Regex("""\s*[(\[]\s*(19|20)\d\d\s*[)\]]""")
    private val yearAfterDash = Regex("""\s*[-|:]\s*(19|20)\d\d\s*$""")
    private val yearAtEnd = Regex("""\s+(19|20)\d\d\s*$""")
    private val emptyBrackets = Regex("""\(\s*\)|\[\s*]""")
    private val multiSpace = Regex("""\s{2,}""")
    private val marks = Regex("""\p{Mn}+""")

    private val articles = setOf("the", "de", "het", "een", "a", "an", "la", "le", "les", "der", "die", "das")

    private const val BARS = "|│┃┆┇┊┋║｜¦❘❙❚▏▎▍▌▐▕ǀ∣"
    private val tagWords = setOf(
        "4k", "uhd", "fhd", "hd", "sd", "1080p", "720p", "2160p", "hevc", "x265", "h265", "hdr", "hdr10", "dv",
        "dolby", "multi", "multisub", "nl", "sub", "subs", "dubbed", "dub", "vlaams", "imax", "remastered", "extended", "director",
    )

    /*
     * Snelle voorcontroles: op het kastje kost elke regex-aanroep merkbaar tijd, en met 55.000 films
     * telt dat op tot minuten. De regex draait dus alleen als er iets te doen kan zijn.
     */
    private fun mayHavePrefix(s: String): Boolean {
        if (s.isEmpty()) return false
        if (s[0] == '[' || s[0] in BARS) return true
        val end = minOf(s.length, 12)
        for (i in 0 until end) { val c = s[i]; if (c in BARS || c == ':' || c == '-') return true }
        return false
    }

    private fun mayHaveTag(s: String): Boolean {
        var start = -1
        for (i in 0..s.length) {
            val c = if (i < s.length) s[i] else ' '
            if (c.isLetterOrDigit()) { if (start < 0) start = i }
            else if (start >= 0) {
                if (i - start <= 10 && s.substring(start, i).lowercase() in tagWords) return true
                start = -1
            }
        }
        return false
    }

    private fun mayHaveYear(s: String) = s.contains("19") || s.contains("20")

    /** Leesbare titel zonder taal-voorvoegsel, jaartal en kwaliteitslabels. */
    fun cleanName(raw: String): String {
        var s = raw.trim()
        repeat(3) { if (mayHavePrefix(s)) s = prefix.replace(s, "") }
        if (mayHaveTag(s)) s = tags.replace(s, " ")
        // "(1999)", "[1999]", "- 1999" aan het eind weg
        if (mayHaveYear(s)) {
            s = s.replace(yearInBrackets, " ")
            s = s.replace(yearAfterDash, " ")
            s = s.replace(yearAtEnd, " ")
        }
        if (s.indexOf('(') >= 0 || s.indexOf('[') >= 0) s = s.replace(emptyBrackets, " ")
        if (s.contains("  ") || s.contains('\t')) s = s.replace(multiSpace, " ")
        s = s.trim().trim('-', '|', ':', ' ')
        return s.ifBlank { raw.trim() }
    }

    /** Jaartal uit de titel (het laatste), of null. */
    fun year(raw: String): String? = if (mayHaveYear(raw)) yearRe.findAll(raw).lastOrNull()?.value else null

    /** Zoek-/matchsleutel: kleine letters, zonder accenten, leestekens en lidwoord vooraan. */
    fun key(title: String): String {
        val ascii = title.all { it.code < 128 }
        val noAccents = if (ascii) title else Normalizer.normalize(title, Normalizer.Form.NFD).replace(marks, "")
        // Zonder regex: alles wat geen a-z/0-9 is wordt een spatie.
        val sb = StringBuilder(noAccents.length + 8)
        for (ch in noAccents.lowercase()) {
            when {
                ch == '&' -> sb.append(" and ")
                ch in 'a'..'z' || ch in '0'..'9' -> sb.append(ch)
                else -> sb.append(' ')
            }
        }
        val words = sb.split(' ').filter { it.isNotBlank() }
        val trimmed = if (words.size > 1 && words.first() in articles) words.drop(1) else words
        return trimmed.joinToString(" ")
    }

    /** Alle zoekwoorden moeten in de titel voorkomen; titels die ermee beginnen eerst. */
    fun searchScore(itemKey: String, queryKey: String): Int {
        if (queryKey.isBlank()) return 0
        val tokens = queryKey.split(' ')
        if (!tokens.all { t -> itemKey.split(' ').any { it.startsWith(t) } }) return 0
        return when {
            itemKey == queryKey -> 100
            itemKey.startsWith(queryKey) -> 80
            itemKey.contains(queryKey) -> 60
            else -> 40
        }
    }
}
