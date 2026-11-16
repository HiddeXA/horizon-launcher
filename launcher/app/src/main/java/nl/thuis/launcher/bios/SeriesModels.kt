package nl.thuis.launcher.bios

import androidx.compose.runtime.Immutable

/** Een serie uit je aanbod. [modified] = wanneer de aanbieder hem het laatst bijwerkte (nieuwe aflevering). */
@Immutable
data class SeriesItem(
    val id: Int,
    val rawName: String,
    val title: String,
    val year: String?,
    val poster: String?,
    val rating: Double?,
    val modified: Long,
    val categoryId: String,
    val tmdbId: Int?,
    val key: String = TitleMatch.key(title),
)

@Immutable
data class Episode(
    val id: Int,
    val season: Int,
    val number: Int,
    val title: String,
    val ext: String,
    val durationSecs: Int?,
    val plot: String?,
    val image: String?,
    val added: Long,
) {
    /** "S2 · A4"; specials (seizoen 0) heten "Special 3". */
    val label: String get() = if (season == 0) "Special $number" else "S$season · A$number"

    companion object {
        /** Een aflevering zonder naam heet "Aflevering 4". */
        fun titleOr(title: String?, number: Int): String = title?.trim().takeUnless { it.isNullOrEmpty() } ?: "Aflevering $number"
    }
}

@Immutable
data class Season(val number: Int, val episodes: List<Episode>) {
    val label: String get() = if (number == 0) "Specials" else "Seizoen $number"
}

/** Alles voor het seriescherm: info plus seizoenen (gewone seizoenen op volgorde, Specials achteraan). */
@Immutable
data class SeriesDetail(
    val seasons: List<Season>,
    val plot: String?,
    val cast: String?,
    val genre: String?,
    val backdrop: String?,
    val trailerKey: String?,
    val tmdbId: Int?,
    val releaseDate: String?,
) {
    val episodeCount: Int get() = seasons.sumOf { it.episodes.size }
    val regularSeasons: Int get() = seasons.count { it.number != 0 }
}

/**
 * Doorzoekbare lijst met series. Pure Kotlin, zodat zoeken en matchen met unit tests te controleren zijn.
 * Zelfde regels als [VodIndex] voor films.
 */
class SeriesIndex(val items: List<SeriesItem>, val categories: List<VodCategory>) {

    val byId: Map<Int, SeriesItem> = items.associateBy { it.id }
    private val byTmdb: Map<Int, SeriesItem> = items.filter { it.tmdbId != null }
        .groupBy { it.tmdbId!! }.mapValues { (_, v) -> v.maxBy { it.modified } }
    private val byKey: Map<String, List<SeriesItem>> = items.groupBy { it.key }

    /** Per categorie, laatst bijgewerkt eerst. */
    val byCategory: Map<String, List<SeriesItem>> = items.groupBy { it.categoryId }
        .mapValues { (_, v) -> v.sortedByDescending { it.modified } }

    /** Laatst bijgewerkt eerst, elke serie één keer. */
    val newest: List<SeriesItem> by lazy {
        items.sortedByDescending { it.modified }.distinctBy { it.key + (it.year ?: "") }.take(40)
    }

    /** Zoekt een serie uit TMDB in je aanbod: eerst op TMDB-nummer, anders op titel + jaar (±1). */
    fun find(tmdbId: Int?, titles: List<String>, year: String?): SeriesItem? {
        tmdbId?.let { byTmdb[it] }?.let { return it }
        val y = year?.toIntOrNull()
        for (t in titles.filter { it.isNotBlank() }.distinct()) {
            val list = byKey[TitleMatch.key(t)] ?: continue
            val sameYear = list.filter { item -> item.year?.toIntOrNull()?.let { y != null && kotlin.math.abs(it - y) <= 1 } == true }
            if (sameYear.isNotEmpty()) return sameYear.maxBy { it.modified }
            // Geen jaar bij de aanbieder: alleen accepteren als de titel eenduidig is.
            val noYear = list.filter { it.year == null }
            if (noYear.size == 1 && list.size == 1) return noYear.first()
        }
        return null
    }

    fun search(query: String, limit: Int = 60): List<SeriesItem> = searchScored(query, limit).map { it.first }

    /** Zoekresultaten met score, om met films te kunnen mengen. */
    fun searchScored(query: String, limit: Int = 60): List<Pair<SeriesItem, Int>> {
        val q = TitleMatch.key(query)
        if (q.isBlank()) return emptyList()
        return items.asSequence()
            .map { it to TitleMatch.searchScore(it.key, q) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<SeriesItem, Int>> { it.second }.thenByDescending { it.first.modified })
            .distinctBy { it.first.key + (it.first.year ?: "") }
            .take(limit)
            .toList()
    }
}
