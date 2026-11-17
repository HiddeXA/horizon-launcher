package nl.thuis.launcher.bios

/*
 * Rekenwerk voor series, los van Android en netwerk zodat het met unit tests te controleren is:
 * volgorde van afleveringen, waar je was, gezien-vinkjes.
 */

/** Wat we per aflevering onthouden. */
data class EpState(val positionMs: Long, val durationMs: Long, val watched: Boolean, val updated: Long) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    /** Er middenin: nog niet gezien, en ver genoeg om te bewaren. */
    val inProgress: Boolean get() = !watched && positionMs >= Watched.MIN_SAVE_MS
}

object EpisodeOrder {
    /** Gewone seizoenen op volgorde, Specials (seizoen 0) achteraan; afleveringen op nummer. */
    fun seasons(episodes: List<Episode>): List<Season> =
        episodes.groupBy { it.season }
            .map { (n, eps) -> Season(n, eps.sortedWith(compareBy<Episode> { it.number }.thenBy { it.id })) }
            .sortedWith(compareBy<Season> { it.number == 0 }.thenBy { it.number })

    /**
     * De aflevering na [episodeId]: de eerstvolgende die er is (gaten in de nummering tellen niet).
     * Na het laatste gewone seizoen komt niets: doorkijken gaat niet vanzelf naar Specials.
     */
    fun next(seasons: List<Season>, episodeId: Int): Episode? {
        val si = seasons.indexOfFirst { s -> s.episodes.any { it.id == episodeId } }
        if (si < 0) return null
        val season = seasons[si]
        val ei = season.episodes.indexOfFirst { it.id == episodeId }
        season.episodes.getOrNull(ei + 1)?.let { return it }
        if (season.number == 0) return null
        return seasons.drop(si + 1).firstOrNull { it.number != 0 && it.episodes.isNotEmpty() }?.episodes?.first()
    }

    /** Alle afleveringen in kijkvolgorde. */
    fun flat(seasons: List<Season>): List<Episode> = seasons.flatMap { it.episodes }

    fun find(seasons: List<Season>, episodeId: Int): Episode? = flat(seasons).firstOrNull { it.id == episodeId }
}

object Watched {
    /** Korter dan dit gekeken: niet bewaren (zelfde grens als bij films). */
    const val MIN_SAVE_MS = 30_000L

    /** Meer dan 95% gekeken = gezien. */
    fun reached(positionMs: Long, durationMs: Long): Boolean = durationMs > 0 && positionMs > durationMs * 0.95

    fun count(season: Season, states: Map<Int, EpState>): Int = season.episodes.count { states[it.id]?.watched == true }

    fun seasonDone(season: Season, states: Map<Int, EpState>): Boolean =
        season.episodes.isNotEmpty() && count(season, states) == season.episodes.size
}

/** "Waar je was" bij een serie: welke aflevering de afspeelknop start, en wat erop staat. */
data class NextUp(val episode: Episode, val kind: Kind, val resumeMs: Long, val finished: Boolean = false) {
    enum class Kind { START, RESUME, NEXT }

    val buttonLabel: String
        get() = when (kind) {
            Kind.START -> "Afspelen ${episode.label}"
            Kind.RESUME -> "Verder kijken ${episode.label}"
            Kind.NEXT -> "Volgende aflevering ${episode.label}"
        }

    companion object {
        /**
         * 1. de aflevering waar je middenin zit (de laatst bekeken);
         * 2. anders de eerste niet-geziene aflevering ná de laatst geziene;
         * 3. anders de eerste aflevering.
         * Alles gezien: [finished], met de eerste aflevering om opnieuw te beginnen.
         */
        fun resolve(seasons: List<Season>, states: Map<Int, EpState>, lastEpisodeId: Int?): NextUp? {
            val all = EpisodeOrder.flat(seasons)
            val first = all.firstOrNull() ?: return null
            fun seen(e: Episode) = states[e.id]?.watched == true

            val last = lastEpisodeId?.let { id -> all.firstOrNull { it.id == id } }
            if (last != null) states[last.id]?.let { st -> if (st.inProgress) return NextUp(last, Kind.RESUME, st.positionMs) }

            // Vanaf waar zoeken we verder? De laatst bekeken aflevering, anders de verste die gezien is.
            val anchor = last?.takeIf { seen(it) } ?: all.lastOrNull { seen(it) }
            if (anchor == null) {
                // Niets gezien. Bestaat de laatst bekeken aflevering niet meer, dan de eerste niet-geziene.
                return NextUp(first, Kind.START, 0)
            }
            var e = EpisodeOrder.next(seasons, anchor.id)
            while (e != null && seen(e)) e = EpisodeOrder.next(seasons, e.id)
            if (e != null) return NextUp(e, Kind.NEXT, states[e.id]?.takeIf { it.inProgress }?.positionMs ?: 0)

            // Na de laatst geziene komt niets meer. Staat er eerder nog iets open (gewone seizoenen)?
            val open = all.firstOrNull { it.season != 0 && !seen(it) }
            if (open != null) return NextUp(open, Kind.NEXT, states[open.id]?.takeIf { it.inProgress }?.positionMs ?: 0)
            return NextUp(first, Kind.START, 0, finished = true)
        }

        /** Hoort de serie in de rij Verder kijken? Begonnen (30 s of een aflevering gezien) en nog niet klaar. */
        fun inContinue(seasons: List<Season>, states: Map<Int, EpState>, lastEpisodeId: Int?): Boolean {
            val ids = EpisodeOrder.flat(seasons).mapTo(HashSet()) { it.id }
            val started = states.any { (id, st) -> id in ids && (st.watched || st.inProgress) }
            if (!started) return false
            return resolve(seasons, states, lastEpisodeId)?.finished == false
        }
    }
}

/** Titels van afleveringen zijn vaak "Serienaam - S02E04 - Titel": alleen de titel blijft over. */
object EpisodeTitles {
    private val code = Regex("""[Ss]\d{1,2}\s*[EeXx]\s*\d{1,3}""")
    private val generic = Regex("""^(episode|aflevering|afl|ep)\.?\s*\d+$""", RegexOption.IGNORE_CASE)
    private const val SEPARATORS = " -:|.–—"

    fun clean(raw: String?, seriesTitle: String, number: Int): String {
        var s = TitleMatch.cleanName(raw.orEmpty()).trim()
        if (s.isEmpty()) return Episode.titleOr(null, number)
        val m = code.find(s)
        if (m != null) {
            s = s.substring(m.range.last + 1)
        } else if (s.length > seriesTitle.length && s.startsWith(seriesTitle, ignoreCase = true) && s[seriesTitle.length] in SEPARATORS) {
            s = s.substring(seriesTitle.length)
        }
        s = s.trim { it in SEPARATORS }
        if (s.isEmpty() || generic.matches(s)) return Episode.titleOr(null, number)
        return s
    }
}

/**
 * De kaart "Volgende aflevering" in de speler: zichtbaar in de laatste 10 s als er een volgende is.
 * De aftelling volgt de positie in de aflevering, dus pauzeren houdt hem vanzelf stil.
 * Annuleren verbergt de kaart tot je terugspoelt tot vóór de laatste 10 s.
 * Is de duur onbekend, dan verschijnt de kaart pas als de aflevering is afgelopen en telt hij 10 s op de klok af.
 */
class NextCard(private val windowMs: Long = WINDOW_MS) {
    var dismissed = false
        private set
    private var endedAt: Long? = null

    /** Seconden op de kaart, of null als de kaart niet in beeld hoort. */
    fun secondsLeft(positionMs: Long, durationMs: Long, hasNext: Boolean, nowMs: Long): Int? {
        if (!hasNext) return null
        endedAt?.let { e ->
            if (dismissed) return null
            return ceilSeconds(windowMs - (nowMs - e))
        }
        if (durationMs <= 0) return null
        val left = durationMs - positionMs
        if (left > windowMs) { dismissed = false; return null }
        if (dismissed) return null
        return ceilSeconds(left)
    }

    fun dismiss() { dismissed = true }

    fun endedWithoutDuration(nowMs: Long) { endedAt = nowMs }

    /** Na afloop zonder bekende duur: is de aftelling voorbij? */
    fun expired(nowMs: Long): Boolean = endedAt?.let { nowMs - it >= windowMs } == true

    fun reset() { dismissed = false; endedAt = null }

    private fun ceilSeconds(ms: Long): Int = ((ms.coerceAtLeast(0) + 999) / 1000).toInt()

    companion object {
        const val WINDOW_MS = 10_000L
        /** Hoe ver de balk op de kaart gevuld is (0 bij 10 s, 1 bij 0 s). */
        fun fraction(secondsLeft: Int): Float = ((WINDOW_MS / 1000 - secondsLeft).toFloat() / (WINDOW_MS / 1000)).coerceIn(0f, 1f)
    }
}
