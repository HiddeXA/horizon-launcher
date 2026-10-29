package nl.thuis.launcher.bios

import androidx.compose.runtime.Immutable

@Immutable
data class VodItem(
    val id: Int,
    val rawName: String,
    val title: String,
    val year: String?,
    val poster: String?,
    val rating: Double?,
    val added: Long,
    val categoryId: String,
    val ext: String,
    val tmdbId: Int?,
    /** Zoek-/matchsleutel; uit de opslag op het kastje, of één keer berekend. */
    val key: String = TitleMatch.key(title),
)

@Immutable
data class VodCategory(val id: String, val name: String)

/**
 * Doorzoekbare catalogus. Wordt één keer opgebouwd na het laden (op een achtergrondthread).
 * Pure Kotlin: matching en zoeken zijn met unit tests te controleren.
 */
class VodIndex(val items: List<VodItem>, val categories: List<VodCategory>) {

    val byId: Map<Int, VodItem> = items.associateBy { it.id }
    private val byTmdb: Map<Int, VodItem> = items.filter { it.tmdbId != null }
        .groupBy { it.tmdbId!! }.mapValues { (_, v) -> v.maxBy { it.added } }
    private val byKey: Map<String, List<VodItem>> = items.groupBy { it.key }

    /** Per categorie, nieuwste eerst. */
    val byCategory: Map<String, List<VodItem>> = items.groupBy { it.categoryId }
        .mapValues { (_, v) -> v.sortedByDescending { it.added } }

    val newest: List<VodItem> by lazy {
        items.sortedByDescending { it.added }.distinctBy { it.key + (it.year ?: "") }.take(40)
    }

    /**
     * Zoekt de film uit een TMDB-aanbeveling in je aanbod.
     * Eerst op TMDB-id (als de aanbieder die meegeeft), anders op titel + jaar (±1).
     */
    fun find(tmdbId: Int?, titles: List<String>, year: String?): VodItem? {
        tmdbId?.let { byTmdb[it] }?.let { return it }
        val y = year?.toIntOrNull()
        for (t in titles.filter { it.isNotBlank() }.distinct()) {
            val list = byKey[TitleMatch.key(t)] ?: continue
            val sameYear = list.filter { item -> item.year?.toIntOrNull()?.let { y != null && kotlin.math.abs(it - y) <= 1 } == true }
            if (sameYear.isNotEmpty()) return sameYear.maxBy { it.added }
            // Geen jaar bij de aanbieder: alleen accepteren als de titel eenduidig is.
            val noYear = list.filter { it.year == null }
            if (noYear.size == 1 && list.size == 1) return noYear.first()
        }
        return null
    }

    fun search(query: String, limit: Int = 60): List<VodItem> {
        val q = TitleMatch.key(query)
        if (q.isBlank()) return emptyList()
        return items.asSequence()
            .map { it to TitleMatch.searchScore(it.key, q) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<VodItem, Int>> { it.second }.thenByDescending { it.first.added })
            .map { it.first }
            .distinctBy { it.key + (it.year ?: "") }
            .take(limit)
            .toList()
    }
}
