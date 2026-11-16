package nl.thuis.launcher.bios

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf

/**
 * Wat we per serie bijhouden voor het startscherm, zonder de afleveringen opnieuw op te halen:
 * waar je was (label + voortgang), of je klaar bent, en tot wanneer we de serie "gezien" hebben
 * (voor het label Nieuwe aflevering).
 */
data class SeriesSummary(
    val updated: Long,
    val lastEpisodeId: Int,
    val nextLabel: String,
    val nextFraction: Float,
    val finished: Boolean,
    /** [SeriesItem.modified] op het moment dat je het seriescherm voor het laatst opende. */
    val seenModified: Long,
    /** Wanneer je het seriescherm voor het laatst opende (afleveringen die daarna zijn toegevoegd zijn "Nieuw"). */
    val seenAt: Long,
)

/**
 * Posities, gezien-vinkjes en Mijn lijst voor series. Alleen op het kastje.
 * De kaarten zijn Compose-state: schermen kleuren direct mee als er iets verandert.
 */
class SeriesStore(context: Context) {
    private val prefs = context.getSharedPreferences("series-progress", Context.MODE_PRIVATE)
    private val listPrefs = context.getSharedPreferences("series-mylist", Context.MODE_PRIVATE)

    /** Per aflevering: positie, duur, gezien. Sleutel in de opslag: "e<id>". */
    val episodes = mutableStateMapOf<Int, EpState>()

    /** Per serie: samenvatting voor het startscherm. Sleutel in de opslag: "s<id>". */
    val summaries = mutableStateMapOf<Int, SeriesSummary>()

    /** Mijn lijst (series), nieuwste bovenaan, met het moment van toevoegen. */
    val myList = mutableStateListOf<Pair<Int, Long>>()

    init {
        prefs.all.forEach { (k, v) ->
            val p = (v as? String)?.split('|') ?: return@forEach
            val id = k.drop(1).toIntOrNull() ?: return@forEach
            runCatching {
                when (k[0]) {
                    'e' -> if (p.size == 4) episodes[id] = EpState(p[0].toLong(), p[1].toLong(), p[2] == "1", p[3].toLong())
                    's' -> if (p.size == 7) summaries[id] = SeriesSummary(p[0].toLong(), p[1].toInt(), p[2], p[3].toFloat(), p[4] == "1", p[5].toLong(), p[6].toLong())
                }
            }
        }
        listPrefs.getString("ids", "")!!.split(',').forEach { e ->
            val id = e.substringBefore(':').toIntOrNull() ?: return@forEach
            myList += id to (e.substringAfter(':', "0").toLongOrNull() ?: 0L)
        }
    }

    private fun putEpisode(id: Int, st: EpState?) {
        if (st == null) { episodes.remove(id); prefs.edit().remove("e$id").apply(); return }
        episodes[id] = st
        prefs.edit().putString("e$id", "${st.positionMs}|${st.durationMs}|${if (st.watched) 1 else 0}|${st.updated}").apply()
    }

    private fun putSummary(seriesId: Int, s: SeriesSummary) {
        summaries[seriesId] = s
        prefs.edit().putString(
            "s$seriesId",
            "${s.updated}|${s.lastEpisodeId}|${s.nextLabel.replace('|', ' ')}|${s.nextFraction}|${if (s.finished) 1 else 0}|${s.seenModified}|${s.seenAt}",
        ).apply()
    }

    fun lastEpisode(seriesId: Int): Int? = summaries[seriesId]?.lastEpisodeId?.takeIf { it != 0 }

    fun resumeAt(episodeId: Int): Long = episodes[episodeId]?.takeIf { it.inProgress }?.positionMs ?: 0L

    /**
     * Bewaart waar je bent in een aflevering. Meer dan 95% = gezien; minder dan 30 s wordt niet bewaard.
     * Geeft terug of de aflevering nu als gezien telt.
     */
    fun savePosition(item: SeriesItem, detail: SeriesDetail, ep: Episode, pos: Long, dur: Long): Boolean {
        val now = System.currentTimeMillis()
        val done = Watched.reached(pos, dur)
        when {
            done -> putEpisode(ep.id, EpState(0, dur, true, now))
            pos < Watched.MIN_SAVE_MS -> { if (episodes[ep.id] == null) return false }
            // Opnieuw aan een geziene aflevering begonnen: dan zit je er weer middenin.
            else -> putEpisode(ep.id, EpState(pos, dur, false, now))
        }
        refreshSummary(item, detail, lastEpisodeId = ep.id, touched = true)
        return done
    }

    /** Zet afleveringen op gezien of niet gezien. Niet gezien wist ook de bewaarde positie. */
    fun setWatched(item: SeriesItem, detail: SeriesDetail, eps: List<Episode>, watched: Boolean) {
        val now = System.currentTimeMillis()
        eps.forEach { e ->
            if (watched) putEpisode(e.id, EpState(0, episodes[e.id]?.durationMs ?: 0, true, now)) else putEpisode(e.id, null)
        }
        refreshSummary(item, detail, touched = true)
    }

    /** Rekent opnieuw uit waar je was, en bewaart dat voor het startscherm. */
    fun refreshSummary(item: SeriesItem, detail: SeriesDetail, lastEpisodeId: Int? = null, touched: Boolean = false, seen: Boolean = false) {
        val old = summaries[item.id]
        val last = lastEpisodeId ?: old?.lastEpisodeId?.takeIf { it != 0 }
        val started = NextUp.inContinue(detail.seasons, episodes, last) || detail.seasons.any { s -> s.episodes.any { episodes[it.id] != null } }
        if (!started && old == null && !seen) return
        val next = NextUp.resolve(detail.seasons, episodes, last)
        val now = System.currentTimeMillis()
        putSummary(
            item.id,
            SeriesSummary(
                updated = if (touched || old == null) now else old.updated,
                lastEpisodeId = last ?: 0,
                nextLabel = next?.episode?.label.orEmpty(),
                nextFraction = next?.let { episodes[it.episode.id]?.takeIf { st -> st.inProgress }?.fraction } ?: 0f,
                finished = next == null || next.finished || !started,
                seenModified = if (seen || old == null) item.modified else old.seenModified,
                seenAt = if (seen || old == null) now else old.seenAt,
            ),
        )
    }

    // --- Mijn lijst ---

    fun inMyList(seriesId: Int) = myList.any { it.first == seriesId }

    fun toggleMyList(seriesId: Int) {
        if (!myList.removeAll { it.first == seriesId }) myList.add(0, seriesId to System.currentTimeMillis())
        listPrefs.edit().putString("ids", myList.joinToString(",") { "${it.first}:${it.second}" }).apply()
    }
}
