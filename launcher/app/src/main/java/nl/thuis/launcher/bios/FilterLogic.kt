package nl.thuis.launcher.bios

import androidx.compose.runtime.Immutable

/**
 * Wat er gezocht moet worden voor een filter. [filmCombos] en [tvCombos] zijn de TMDB-genrecombinaties
 * (binnen een combinatie: alle genres; tussen combinaties: één van de combinaties is genoeg). Leeg = geen genre.
 */
@Immutable
data class FilterPlan(
    val films: Boolean,
    val series: Boolean,
    val filmCombos: List<List<Int>>,
    val tvCombos: List<List<Int>>,
    /** Bij Alles: een gekozen genre dat alleen bij films hoort (dan zoeken we geen series). */
    val onlyFilms: GenreOption?,
    /** Bij Alles: een gekozen genre dat alleen bij series hoort. */
    val onlySeries: GenreOption?,
) {
    val hasGenres get() = filmCombos.isNotEmpty() || tvCombos.isNotEmpty()
}

object FilterQuery {
    fun plan(f: BiosFilter): FilterPlan {
        val opts = f.options
        val onlyFilms = if (f.kind == FilterKind.ALL) opts.firstOrNull { it.onlyFilms } else null
        val onlySeries = if (f.kind == FilterKind.ALL) opts.firstOrNull { it.onlySeries } else null
        val films = when (f.kind) {
            FilterKind.FILMS -> true
            FilterKind.SERIES -> false
            FilterKind.ALL -> onlySeries == null
        }
        val series = when (f.kind) {
            FilterKind.FILMS -> false
            FilterKind.SERIES -> true
            FilterKind.ALL -> onlyFilms == null
        }
        return FilterPlan(
            films = films,
            series = series,
            filmCombos = if (films) combos(opts.map { it.filmIds }) else emptyList(),
            tvCombos = if (series) combos(opts.map { it.tvIds }) else emptyList(),
            onlyFilms = onlyFilms,
            onlySeries = onlySeries,
        )
    }

    /** Alle manieren om uit elke groep één genre te kiezen. Geen groepen = geen combinaties. */
    private fun combos(groups: List<List<Int>>): List<List<Int>> {
        if (groups.isEmpty()) return emptyList()
        return groups.fold(listOf(emptyList<Int>())) { acc, g -> acc.flatMap { c -> g.map { c + it } } }.distinct()
    }
}

/** Films en series samen in één lijst, op de gekozen sortering. */
object FilterMerge {
    /** Hoe populair en hoe goed een titel is; null als we het niet weten. */
    class Scores(
        val filmPop: (VodItem) -> Double?,
        val showPop: (SeriesItem) -> Double?,
        val filmVote: (VodItem) -> Double?,
        val showVote: (SeriesItem) -> Double?,
    )

    fun merge(films: List<VodItem>, series: List<SeriesItem>, sort: FilterSort, s: Scores): List<Listing> {
        val f = films.map { Listing.Film(it) }
        val t = series.map { Listing.Show(it) }
        return when (sort) {
            FilterSort.AZ -> (f + t).sortedBy { it.title.lowercase() }
            FilterSort.NEWEST -> (f + t).sortedByDescending { time(it) }
            FilterSort.RATING -> (f + t).sortedByDescending { vote(it, s) ?: 0.0 }
            FilterSort.POPULAR -> {
                val pops = (f + t).map { pop(it, s) }
                if (pops.all { it != null }) (f + t).sortedByDescending { pop(it, s) }
                else interleave(f, t)   // elke lijst is al op populariteit gesorteerd
            }
        }
    }

    private fun time(l: Listing): Long = when (l) {
        is Listing.Film -> l.item.added
        is Listing.Show -> l.item.modified
    }

    private fun vote(l: Listing, s: Scores): Double? = when (l) {
        is Listing.Film -> s.filmVote(l.item)
        is Listing.Show -> s.showVote(l.item)
    }

    private fun pop(l: Listing, s: Scores): Double? = when (l) {
        is Listing.Film -> s.filmPop(l.item)
        is Listing.Show -> s.showPop(l.item)
    }

    private fun interleave(a: List<Listing>, b: List<Listing>): List<Listing> {
        val out = ArrayList<Listing>(a.size + b.size)
        for (i in 0 until maxOf(a.size, b.size)) {
            a.getOrNull(i)?.let { out += it }
            b.getOrNull(i)?.let { out += it }
        }
        return out
    }
}

/** Teksten op de filterpagina. */
object FilterTexts {
    /** Waarde in de keuzeknop Genres: twee namen, daarna "+n". Null als er niets gekozen is. */
    fun chipValue(labels: List<String>): String? = when {
        labels.isEmpty() -> null
        labels.size <= 2 -> labels.joinToString(", ")
        else -> labels.take(2).joinToString(", ") + " +${labels.size - 2}"
    }

    @Immutable
    data class Count(val main: String, val detail: String)

    fun count(f: BiosFilter, films: Int, series: Int, capped: Boolean): Count {
        val plan = FilterQuery.plan(f)
        val main: String
        val parts = ArrayList<String>()
        if (plan.films && plan.series) {
            main = n(films + series, "titel", "titels")
            parts += n(films, "film", "films") + ", " + n(series, "serie", "series")
        } else if (plan.series) {
            main = n(series, "serie", "series")
        } else {
            main = n(films, "film", "films")
        }
        plan.onlyFilms?.let { parts += "${it.label} is alleen een filmgenre" }
        plan.onlySeries?.let { parts += "${it.label} is alleen een seriegenre" }
        if (capped) parts += "de bekendste"
        if (f.isEmpty && f.kind == FilterKind.ALL && f.sort == FilterSort.POPULAR) parts += "populairste eerst"
        return Count(main, parts.joinToString(" · "))
    }

    private fun n(count: Int, one: String, more: String) = "$count ${if (count == 1) one else more}"

    private fun noun(kind: FilterKind) = when (kind) {
        FilterKind.ALL -> "titels"
        FilterKind.FILMS -> "films"
        FilterKind.SERIES -> "series"
    }

    private fun periodPhrase(p: FilterPeriod): String? = when (p) {
        FilterPeriod.ALL -> null
        FilterPeriod.NOW -> "van 2020 of later"
        FilterPeriod.TENS -> "uit de jaren 10"
        FilterPeriod.NOUGHTIES -> "uit de jaren 00"
        FilterPeriod.NINETIES -> "uit de jaren 90"
        FilterPeriod.EIGHTIES -> "uit de jaren 80"
        FilterPeriod.CLASSIC -> "van vóór 1980"
    }

    private fun scoreText(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString().replace('.', ',')

    /** "a", "a én b", "a, b én c" */
    private fun andList(xs: List<String>, last: String): String =
        if (xs.size <= 1) xs.joinToString("") else xs.dropLast(1).joinToString(", ") + " $last " + xs.last()

    /** Uitleg bij "Geen titels gevonden", in gewone taal. */
    fun emptyExplanation(f: BiosFilter): String {
        val opts = f.options
        val parts = listOfNotNull(
            opts.takeIf { it.isNotEmpty() }?.let { "die ${andList(it.map { o -> o.label }, "én")} zijn" },
            periodPhrase(f.period),
            f.minScore.takeIf { it > 0 }?.let { "met een score van ${scoreText(it)} of hoger" },
        )
        val sentence = buildString {
            append("Er zijn in je aanbod geen ").append(noun(f.kind))
            if (parts.isNotEmpty()) {
                // Na de genres een komma; tussen de laatste twee "en".
                append(" ")
                append(if (parts.size == 1) parts[0] else parts.dropLast(1).joinToString(", ") + " en " + parts.last())
            }
            append(".")
        }
        val plan = FilterQuery.plan(f)
        val extra = when {
            plan.onlyFilms != null && plan.onlySeries != null ->
                "${plan.onlyFilms.label} is alleen een filmgenre en ${plan.onlySeries.label} alleen een seriegenre, dus samen vinden ze niets."
            plan.onlyFilms != null -> "${plan.onlyFilms.label} is alleen een filmgenre, dus je zoekt nu alleen in films."
            plan.onlySeries != null -> "${plan.onlySeries.label} is alleen een seriegenre, dus je zoekt nu alleen in series."
            else -> null
        }
        return if (extra == null) sentence else "$sentence $extra"
    }

    /** Knoppen bij de lege staat: het strengste filter loslaten, en altijd Alles wissen. */
    enum class Fix(val label: String) {
        SCORE("Score naar Alles"), PERIOD("Periode naar Alles"), GENRES("Genres wissen"), CLEAR("Alles wissen");

        fun apply(f: BiosFilter): BiosFilter = when (this) {
            SCORE -> f.copy(minScore = 0.0)
            PERIOD -> f.copy(period = FilterPeriod.ALL)
            GENRES -> f.copy(genres = emptySet())
            CLEAR -> BiosFilter()
        }
    }

    fun suggestions(f: BiosFilter): List<Fix> {
        val plan = FilterQuery.plan(f)
        val conflict = plan.onlyFilms != null && plan.onlySeries != null
        val loosen = buildList {
            if (conflict) add(Fix.GENRES)
            if (f.minScore > 0) add(Fix.SCORE)
            if (f.period != FilterPeriod.ALL) add(Fix.PERIOD)
            if (f.genres.isNotEmpty() && !conflict) add(Fix.GENRES)
        }
        return loosen.take(2) + Fix.CLEAR
    }
}
