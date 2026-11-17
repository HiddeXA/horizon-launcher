package nl.thuis.launcher.bios

import androidx.compose.runtime.Immutable
import kotlin.math.pow

enum class SeriesShelfKind { TOP, POPULAR, GENRE }

/** TMDB-genres voor series (andere lijst dan voor films), met Nederlandse namen. */
enum class TvGenre(val id: Int, val label: String) {
    ACTION(10759, "Actie & Avontuur"), ANIMATION(16, "Animatie"), COMEDY(35, "Komedie"), CRIME(80, "Misdaad"),
    DOCUMENTARY(99, "Documentaire"), DRAMA(18, "Drama"), FAMILY(10751, "Familie"), KIDS(10762, "Kids"),
    MYSTERY(9648, "Mysterie"), REALITY(10764, "Reality"), SCIFI(10765, "Sci-Fi & Fantasy"), SOAP(10766, "Soap"),
    TALK(10767, "Talkshow"), WAR(10768, "Oorlog & Politiek"), WESTERN(37, "Western");

    companion object {
        val byId = entries.associateBy { it.id }
    }
}

/** Een rij op de tab Series. Top series staat altijd bovenaan; de rest wordt op kijkgedrag gesorteerd. */
@Immutable
data class SeriesShelf(val key: String, val title: String, val kind: SeriesShelfKind, val genres: List<TvGenre> = emptyList(), val pinned: Boolean = false)

val SeriesShelves: List<SeriesShelf> = listOf(
    SeriesShelf("top", "Top series", SeriesShelfKind.TOP, pinned = true),
    SeriesShelf("popular", "Populair nu", SeriesShelfKind.POPULAR),
    SeriesShelf("drama", "Drama", SeriesShelfKind.GENRE, listOf(TvGenre.DRAMA)),
    SeriesShelf("crime", "Misdaad", SeriesShelfKind.GENRE, listOf(TvGenre.CRIME)),
    SeriesShelf("comedy", "Komedie", SeriesShelfKind.GENRE, listOf(TvGenre.COMEDY)),
    SeriesShelf("action", "Actie & Avontuur", SeriesShelfKind.GENRE, listOf(TvGenre.ACTION)),
    SeriesShelf("scifi", "Sci-Fi & Fantasy", SeriesShelfKind.GENRE, listOf(TvGenre.SCIFI)),
    SeriesShelf("mystery", "Mysterie", SeriesShelfKind.GENRE, listOf(TvGenre.MYSTERY)),
    SeriesShelf("documentary", "Documentaires", SeriesShelfKind.GENRE, listOf(TvGenre.DOCUMENTARY)),
    SeriesShelf("animation", "Animatie", SeriesShelfKind.GENRE, listOf(TvGenre.ANIMATION)),
    SeriesShelf("family", "Familie", SeriesShelfKind.GENRE, listOf(TvGenre.FAMILY)),
    SeriesShelf("kids", "Kids", SeriesShelfKind.GENRE, listOf(TvGenre.KIDS)),
    SeriesShelf("reality", "Reality", SeriesShelfKind.GENRE, listOf(TvGenre.REALITY)),
    SeriesShelf("war", "Oorlog & Politiek", SeriesShelfKind.GENRE, listOf(TvGenre.WAR)),
    SeriesShelf("western", "Western", SeriesShelfKind.GENRE, listOf(TvGenre.WESTERN)),
)

/**
 * Volgorde van de genrerijen op de tab Series, uit je kijkgedrag bij series (los van films).
 * Zelfde regels als [TasteRanking]: kijken telt naar minuten, oud gedrag halveert elke 30 dagen,
 * rijen zonder score houden de standaardvolgorde, vastgepinde rijen staan bovenaan.
 */
object SeriesTasteRanking {
    /** Zelfde als bij films ([TasteRanking.HALF_LIFE_DAYS]). */
    const val HALF_LIFE_DAYS = 30.0

    fun scores(events: List<TasteEvent>, shelves: List<SeriesShelf>, now: Long, thisYear: Int): Map<String, Double> {
        val out = HashMap<String, Double>()
        for (e in events) {
            val ageDays = ((now - e.time).coerceAtLeast(0)) / 86_400_000.0
            val w = e.weight * 0.5.pow(ageDays / HALF_LIFE_DAYS)
            for (s in shelves) {
                val match = when (s.kind) {
                    SeriesShelfKind.GENRE -> if (s.genres.isNotEmpty() && s.genres.all { it.id in e.genres }) 1.0 else 0.0
                    SeriesShelfKind.POPULAR -> if (e.year != null && e.year >= thisYear - 2) 0.6 else 0.0
                    SeriesShelfKind.TOP -> 0.0
                }
                if (match > 0) out[s.key] = (out[s.key] ?: 0.0) + w * match
            }
        }
        return out
    }

    fun order(shelves: List<SeriesShelf>, scores: Map<String, Double>): List<SeriesShelf> {
        val pinned = shelves.filter { it.pinned }
        val rest = shelves.filterNot { it.pinned }.withIndex()
            .sortedWith(compareByDescending<IndexedValue<SeriesShelf>> { scores[it.value.key] ?: 0.0 }.thenBy { it.index })
            .map { it.value }
        return pinned + rest
    }
}

/**
 * Wisselen in het filter tussen Films en Series: periode, score en sortering blijven staan, en
 * gekozen genres alleen als het andere soort ze ook kent. TMDB gebruikt waar ze overlappen dezelfde
 * nummers (Komedie 35, Misdaad 80, Drama 18 …); Actie (28) en Horror (27) bestaan niet bij series.
 */
object GenreCarry {
    fun toSeries(filmGenreIds: Collection<Int>): Set<TvGenre> = filmGenreIds.mapNotNull { TvGenre.byId[it] }.toSet()

    /** Van series naar films: houd de nummers die bij films bestaan. */
    fun keep(ids: Collection<Int>, known: Collection<Int>): Set<Int> = ids.filter { it in known }.toSet()
}
