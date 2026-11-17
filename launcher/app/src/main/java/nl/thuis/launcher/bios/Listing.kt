package nl.thuis.launcher.bios

import androidx.compose.runtime.Immutable

/** Een film of een serie, voor schermen waar ze samen staan (Zoeken, Mijn lijst). */
@Immutable
sealed interface Listing {
    val id: Int
    val title: String
    val poster: String?

    @Immutable
    data class Film(val item: VodItem) : Listing {
        override val id get() = item.id
        override val title get() = item.title
        override val poster get() = item.poster
    }

    @Immutable
    data class Show(val item: SeriesItem) : Listing {
        override val id get() = item.id
        override val title get() = item.title
        override val poster get() = item.poster
    }

    /** Uniek binnen een lijst met beide soorten (film- en serienummers kunnen gelijk zijn). */
    val listKey: String get() = if (this is Show) "s$id" else "f$id"
}

object Listings {
    /** Films en series samen, beste overeenkomst eerst; bij gelijke score het laatst toegevoegd eerst. */
    fun search(films: VodIndex, series: SeriesIndex?, query: String, limit: Int = 80): List<Listing> {
        val q = TitleMatch.key(query)
        if (q.isBlank()) return emptyList()
        val f = films.items.asSequence()
            .map { it to TitleMatch.searchScore(it.key, q) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<VodItem, Int>> { it.second }.thenByDescending { it.first.added })
            .distinctBy { it.first.key + (it.first.year ?: "") }
            .take(limit)
            .map { Triple<Listing, Int, Long>(Listing.Film(it.first), it.second, it.first.added) }
        val s = series?.searchScored(query, limit).orEmpty().asSequence()
            .map { Triple<Listing, Int, Long>(Listing.Show(it.first), it.second, it.first.modified) }
        return (f + s)
            .sortedWith(compareByDescending<Triple<Listing, Int, Long>> { it.second }.thenByDescending { it.third })
            .take(limit)
            .map { it.first }
            .toList()
    }

    /**
     * Mijn lijst met films en series, laatst toegevoegd eerst. Films die er al op stonden voordat we
     * de tijd bijhielden (tijd 0) komen onderaan, in hun oude volgorde. Verdwenen titels vallen weg.
     */
    fun myList(
        films: List<Pair<Int, Long>>,
        series: List<Pair<Int, Long>>,
        filmIndex: VodIndex,
        seriesIndex: SeriesIndex?,
    ): List<Listing> {
        val f = films.mapIndexedNotNull { i, (id, t) -> filmIndex.byId[id]?.let { Triple<Listing, Long, Int>(Listing.Film(it), t, i) } }
        val s = series.mapIndexedNotNull { i, (id, t) -> seriesIndex?.byId?.get(id)?.let { Triple<Listing, Long, Int>(Listing.Show(it), t, i) } }
        return (f + s).sortedWith(compareByDescending<Triple<Listing, Long, Int>> { it.second }.thenBy { it.third }).map { it.first }
    }
}
