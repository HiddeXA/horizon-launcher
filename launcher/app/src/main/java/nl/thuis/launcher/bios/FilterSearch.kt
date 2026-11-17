package nl.thuis.launcher.bios

import android.util.Log
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Uitkomst van het filter: de titels en wat er per soort mis of nog onderweg is. */
@Immutable
data class FilterOutcome(
    val items: List<Listing>,
    val films: Int,
    val series: Int,
    /** Met genres en 60 of meer bij een soort: het zijn de bekendste. */
    val capped: Boolean,
    /** TMDB gaf geen antwoord voor de films / de series. */
    val filmError: Boolean = false,
    val seriesError: Boolean = false,
    /** De series worden nog geladen (de films staan er al). */
    val seriesPending: Boolean = false,
    /** Genres gekozen maar geen TMDB-token: de andere filters zijn wel toegepast. */
    val genreIgnored: Boolean = false,
    val searchedFilms: Boolean = true,
    val searchedSeries: Boolean = true,
) {
    /** Alles wat gezocht werd is mislukt: dan de foutstaat in plaats van "geen titels". */
    val allFailed get() = (filmError || seriesError) && (!searchedFilms || filmError) && (!searchedSeries || seriesError)

    /** Eén soort mislukt, de andere niet: titels tonen met een melding. */
    val partlyFailed get() = (filmError || seriesError) && !allFailed
}

object FilterSearch {
    private const val TAG = "HorizonFilter"

    suspend fun run(filter: BiosFilter, films: VodIndex, series: SeriesState): FilterOutcome = coroutineScope {
        val plan = FilterQuery.plan(filter)
        val tmdb = BiosGenres.hasTmdb
        val ignoreGenres = plan.hasGenres && !tmdb
        // Zonder token tellen de genres niet mee: dan gewoon de soort(en) van het filter.
        val wantFilms = if (ignoreGenres) filter.kind != FilterKind.SERIES else plan.films
        val wantSeries = if (ignoreGenres) filter.kind != FilterKind.FILMS else plan.series

        val filmJob = if (wantFilms) async {
            if (plan.filmCombos.isNotEmpty() && tmdb) {
                runCatching { BiosGenres.filter(films, plan.filmCombos, filter.period, filter.minScore, filter.sort) }
            } else Result.success(withContext(Dispatchers.Default) { filterCatalog(films, filter) })
        } else null

        val seriesIdx = (series as? SeriesState.Ready)?.index
        val seriesJob = if (wantSeries && seriesIdx != null) async {
            if (plan.tvCombos.isNotEmpty() && tmdb) {
                runCatching { SeriesGenres.filter(seriesIdx, plan.tvCombos, filter.period, filter.minScore, filter.sort) }
            } else Result.success(withContext(Dispatchers.Default) { SeriesGenres.filterCatalog(seriesIdx, filter) })
        } else null

        val f = filmJob?.await()
        val s = seriesJob?.await()
        val filmList = f?.getOrNull().orEmpty()
        val showList = s?.getOrNull().orEmpty()
        val scores = FilterMerge.Scores(
            filmPop = { BiosGenres.meta[it.id]?.popularity },
            showPop = { SeriesGenres.meta[it.id]?.popularity },
            filmVote = { it.rating ?: BiosGenres.meta[it.id]?.vote },
            showVote = { it.rating ?: SeriesGenres.meta[it.id]?.vote },
        )
        val withGenres = plan.hasGenres && tmdb
        FilterOutcome(
            items = FilterMerge.merge(filmList, showList, filter.sort, scores),
            films = filmList.size,
            series = showList.size,
            capped = withGenres && (filmList.size >= 60 || showList.size >= 60),
            filmError = f?.isFailure == true,
            seriesError = s?.isFailure == true || (wantSeries && series is SeriesState.Error),
            seriesPending = wantSeries && series is SeriesState.Loading,
            genreIgnored = ignoreGenres,
            searchedFilms = wantFilms,
            searchedSeries = wantSeries,
        ).also {
            Log.i(TAG, "filter ${filter.kind} genres=${filter.options.map { o -> o.label }} periode=${filter.period} score=${filter.minScore} " +
                "sort=${filter.sort} -> films=${it.films} series=${it.series} filmFout=${it.filmError} serieFout=${it.seriesError} " +
                "seriesLaden=${it.seriesPending} zonderGenre=${it.genreIgnored}")
        }
    }
}
