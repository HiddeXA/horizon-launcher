package nl.thuis.launcher.bios

import androidx.compose.runtime.Immutable

/** TMDB-genres voor films, met Nederlandse namen. */
enum class Genre(val id: Int, val label: String) {
    ACTION(28, "Actie"), ADVENTURE(12, "Avontuur"), ANIMATION(16, "Animatie"), COMEDY(35, "Komedie"),
    CRIME(80, "Misdaad"), DOCUMENTARY(99, "Documentaire"), DRAMA(18, "Drama"), FAMILY(10751, "Familie"),
    FANTASY(14, "Fantasy"), HISTORY(36, "Historisch"), HORROR(27, "Horror"), MUSIC(10402, "Muziek"),
    MYSTERY(9648, "Mysterie"), ROMANCE(10749, "Romantiek"), SCIFI(878, "Sciencefiction"),
    THRILLER(53, "Thriller"), WAR(10752, "Oorlog"), WESTERN(37, "Western");

    companion object {
        val byId = entries.associateBy { it.id }
    }
}

enum class FilterPeriod(val label: String, val from: Int?, val to: Int?) {
    ALL("Alles", null, null), NOW("2020 en later", 2020, null), TENS("Jaren 10", 2010, 2019),
    NOUGHTIES("Jaren 00", 2000, 2009), NINETIES("Jaren 90", 1990, 1999), EIGHTIES("Jaren 80", 1980, 1989),
    CLASSIC("Ouder", null, 1979),
}

enum class FilterSort(val label: String) { POPULAR("Populair"), NEWEST("Nieuw toegevoegd"), RATING("Beste score"), AZ("A–Z") }

/** Wat het filter laat zien: films en series samen (standaard), of één soort. */
enum class FilterKind(val label: String) { ALL("Alles"), FILMS("Films"), SERIES("Series") }

/**
 * Eén keuze in het genrepaneel. [filmIds] en [tvIds] zijn de TMDB-genres aan beide kanten:
 * bij een film- of seriegenre staan aan de andere kant de genres die ermee overeenkomen (voor het
 * wisselen van soort). Een samengevoegd genre (Actie & Avontuur) heeft meerdere filmgenres: een
 * film hoeft er maar één van te hebben.
 */
@Immutable
data class GenreOption(val key: String, val label: String, val filmIds: List<Int>, val tvIds: List<Int>) {
    val onlyFilms get() = tvIds.isEmpty()
    val onlySeries get() = filmIds.isEmpty()
}

object FilterGenres {
    /** Filmgenres die bij series anders heten of anders zijn ingedeeld. */
    private val filmToTv = mapOf(28 to 10759, 12 to 10759, 878 to 10765, 14 to 10765, 10752 to 10768)
    private val tvToFilm = mapOf(10759 to listOf(28, 12), 10765 to listOf(878, 14), 10768 to listOf(10752))

    /** Bij Films: de 18 filmgenres, in de vertrouwde volgorde. */
    val films: List<GenreOption> = Genre.entries.map { g ->
        val tv = filmToTv[g.id] ?: g.id.takeIf { it in TvGenre.byId }
        GenreOption("f${g.id}", g.label, listOf(g.id), listOfNotNull(tv))
    }

    /** Bij Series: de 15 seriegenres. */
    val series: List<GenreOption> = TvGenre.entries.map { g ->
        val film = tvToFilm[g.id] ?: listOf(g.id).filter { it in Genre.byId }
        GenreOption("t${g.id}", g.label, film, listOf(g.id))
    }

    /**
     * Bij Alles: één lijst, alfabetisch. Genres die bij films en series hetzelfde zijn staan er één
     * keer in; Actie & Avontuur, Sci-Fi & Fantasy en Oorlog zijn samengevoegd.
     */
    val all: List<GenreOption> = run {
        val merged = listOf(
            GenreOption("a-actie", "Actie & Avontuur", listOf(28, 12), listOf(10759)),
            GenreOption("a-scifi", "Sci-Fi & Fantasy", listOf(878, 14), listOf(10765)),
            GenreOption("a-oorlog", "Oorlog", listOf(10752), listOf(10768)),
        )
        val inMerged = merged.flatMap { it.filmIds + it.tvIds }.toSet()
        val filmRest = Genre.entries.filter { it.id !in inMerged }
            .map { GenreOption("a${it.id}", it.label, listOf(it.id), listOfNotNull(it.id.takeIf { id -> id in TvGenre.byId })) }
        val tvOnly = TvGenre.entries.filter { it.id !in inMerged && it.id !in Genre.byId }
            .map { GenreOption("a${it.id}", it.label, emptyList(), listOf(it.id)) }
        (merged + filmRest + tvOnly).sortedBy { it.label.lowercase() }
    }

    fun forKind(kind: FilterKind): List<GenreOption> = when (kind) {
        FilterKind.ALL -> all
        FilterKind.FILMS -> films
        FilterKind.SERIES -> series
    }

    private val byKey = (all + films + series).associateBy { it.key }
    fun byKey(key: String): GenreOption? = byKey[key]

    /**
     * Gekozen genres meenemen naar een andere soort. Per genre de eerste keuze die een filmgenre deelt
     * (in de volgorde van het genre zelf), anders een seriegenre. Wat de nieuwe soort niet kent, vervalt.
     */
    fun carry(chosen: Collection<GenreOption>, to: FilterKind): Set<String> {
        val target = forKind(to)
        return chosen.mapNotNull { s ->
            s.filmIds.firstNotNullOfOrNull { id -> target.firstOrNull { id in it.filmIds } }
                ?: s.tvIds.firstNotNullOfOrNull { id -> target.firstOrNull { id in it.tvIds } }
        }.map { it.key }.toSet()
    }
}

@Immutable
data class BiosFilter(
    val kind: FilterKind = FilterKind.ALL,
    /** Sleutels van gekozen [GenreOption]s, uit de lijst van [kind]. */
    val genres: Set<String> = emptySet(),
    val period: FilterPeriod = FilterPeriod.ALL,
    val minScore: Double = 0.0,
    val sort: FilterSort = FilterSort.POPULAR,
) {
    /** Gekozen genres, in de volgorde van de lijst. */
    val options: List<GenreOption> get() = FilterGenres.forKind(kind).filter { it.key in genres }

    /** Geen echte filters (voor de stip op de pil Filter). Sortering en soort tellen niet mee. */
    val isEmpty get() = genres.isEmpty() && period == FilterPeriod.ALL && minScore == 0.0

    /** Wissen heeft zin: een filter, een andere sortering of een andere soort dan Alles. */
    val canClear get() = !isEmpty || sort != FilterSort.POPULAR || kind != FilterKind.ALL

    fun toggle(o: GenreOption): BiosFilter = copy(genres = if (o.key in genres) genres - o.key else genres + o.key)

    /** Naar een andere soort: periode, score en sortering blijven; genres alleen als de nieuwe soort ze kent. */
    fun switchTo(to: FilterKind): BiosFilter =
        if (to == kind) this else copy(kind = to, genres = FilterGenres.carry(options, to))
}
