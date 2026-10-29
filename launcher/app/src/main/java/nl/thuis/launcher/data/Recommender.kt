package nl.thuis.launcher.data

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.random.Random

/**
 * Bouwt de rij "Aanbevolen voor jou".
 *
 * Kandidaten komen uit drie bronnen:
 *  1. trending deze week (TMDB)
 *  2. klassiekers: hoog gewaardeerd, veel stemmen, van vóór 2006 (TMDB)
 *  3. films die lijken op wat je leuk vond (TMDB recommendations van je laatste likes)
 *
 * De score combineert kwaliteit, populariteit, hoe goed de genres bij je likes passen
 * en hoe vaak een film opduikt als "lijkt op". Zie [RecScoring].
 */
class Recommender(context: Context, private val api: MovieApi) {

    data class Result(val movies: List<Movie>, val message: String? = null)

    private val prefs = context.getSharedPreferences("recs", Context.MODE_PRIVATE)
    private val ratingsPrefs = context.getSharedPreferences("ratings", Context.MODE_PRIVATE)

    /** Laatst bekende lijst, direct beschikbaar bij opstarten (ook offline). */
    fun cached(): List<Movie> =
        runCatching { JSONArray(prefs.getString("json", "[]")).toMovies() }.getOrDefault(emptyList())

    suspend fun load(liked: List<Movie>, signature: String, force: Boolean = false): Result {
        if (!api.hasTmdb) {
            return Result(emptyList(), "Voeg je TMDB-token toe in local.properties om aanbevelingen te zien.")
        }
        val age = System.currentTimeMillis() - prefs.getLong("time", 0)
        val fresh = age < CACHE_MS && prefs.getString("sig", null) == signature && prefs.getInt("v", 1) == CACHE_VERSION
        if (fresh && !force) return Result(cached())

        return try {
            val today = LocalDate.now()
            val (trending, classics, similar, genres) = coroutineScope {
                val t = async { api.trending() }
                val c = async { api.classics(page = 1 + today.dayOfYear % 3) }
                val s = liked.take(MAX_SEEDS).map { seed -> async { seed to api.similarTo(seed.id).take(12) } }
                val g = async { api.genreNames() }
                Quad(t.await(), c.await(), s.awaitAll().toMap(), g.await())
            }

            val ranked = RecScoring.rank(
                trending = trending,
                classics = classics,
                similar = similar,
                liked = liked,
                genreNames = genres,
                random = Random(today.toEpochDay()),
            )
            val enriched = enrich(ranked)

            prefs.edit()
                .putString("json", enriched.toJsonArray().toString())
                .putString("sig", signature)
                .putLong("time", System.currentTimeMillis())
                .putInt("v", CACHE_VERSION)
                .apply()
            Result(enriched)
        } catch (e: Exception) {
            Result(cached(), "Aanbevelingen konden niet ververst worden (${e.message}).")
        }
    }

    /** Vult IMDb- en Rotten Tomatoes-scores aan. Wordt permanent per film gecachet. */
    private suspend fun enrich(movies: List<Movie>): List<Movie> = coroutineScope {
        movies.chunked(5).flatMap { chunk ->
            chunk.map { m ->
                async {
                    ratingsPrefs.getString(m.id.toString(), null)?.let { cachedRatings ->
                        val o = JSONObject(cachedRatings)
                        return@async m.copy(
                            imdbId = o.optStringOrNull("id"),
                            imdbRating = o.optStringOrNull("imdb"),
                            rtScore = o.optStringOrNull("rt"),
                        )
                    }
                    runCatching {
                        val imdbId = api.imdbId(m.id) ?: return@runCatching m
                        val (imdb, rt) = api.ratings(imdbId)
                        if (api.hasOmdb) {
                            ratingsPrefs.edit().putString(
                                m.id.toString(),
                                JSONObject().put("id", imdbId).put("imdb", imdb).put("rt", rt).toString(),
                            ).apply()
                        }
                        m.copy(imdbId = imdbId, imdbRating = imdb, rtScore = rt)
                    }.getOrDefault(m)
                }
            }.awaitAll()
        }
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    companion object {
        /** De laatst berekende aanbevelingen, voor de Bios-app (zonder netwerk). */
        fun readCache(context: Context): List<Movie> = runCatching {
            JSONArray(context.getSharedPreferences("recs", Context.MODE_PRIVATE).getString("json", "[]")).toMovies()
        }.getOrDefault(emptyList())

        private const val CACHE_VERSION = 2
        private const val CACHE_MS = 6 * 60 * 60 * 1000L
        private const val MAX_SEEDS = 6
    }
}

/** Pure rangschik-logica, los van netwerk en Android zodat hij te testen is. */
object RecScoring {

    private class Candidate(val movie: Movie) {
        var trendingRank: Int? = null
        var classic = false
        val becauseOf = mutableListOf<String>()
    }

    fun rank(
        trending: List<Movie>,
        classics: List<Movie>,
        similar: Map<Movie, List<Movie>>,
        liked: List<Movie>,
        genreNames: Map<Int, String>,
        size: Int = 20,
        minClassics: Int = 5,
        random: Random = Random(0),
    ): List<Movie> {
        val likedIds = liked.map { it.id }.toSet()
        val genreWeight = genreWeights(liked)

        val cands = LinkedHashMap<Int, Candidate>()
        fun add(m: Movie) = cands.getOrPut(m.id) { Candidate(m) }

        trending.forEachIndexed { i, m -> add(m).trendingRank = i }
        classics.forEach { add(it).classic = true }
        similar.forEach { (seed, list) -> list.forEach { add(it).becauseOf += seed.title } }

        val scored = cands.values
            .filter { it.movie.id !in likedIds && it.movie.posterPath != null }
            .map { c ->
                val affinity = affinity(c.movie, genreWeight)
                val score = 0.30 * (c.movie.voteAverage / 10.0).coerceIn(0.0, 1.0) +
                    0.20 * (c.trendingRank?.let { 1.0 - it.toDouble() / trending.size } ?: if (c.classic) 0.4 else 0.0) +
                    0.30 * affinity +
                    0.20 * (c.becauseOf.size / 2.0).coerceAtMost(1.0) +
                    0.05 * random.nextDouble() // kleine dagelijkse variatie
                Triple(c, score, affinity)
            }
            .sortedByDescending { it.second }

        // Altijd een paar klassiekers erin, ook als trending hoger scoort.
        val picked = scored.filter { it.first.classic }.take(minClassics).toMutableList()
        for (s in scored) {
            if (picked.size >= size) break
            if (s !in picked) picked += s
        }

        return picked.sortedByDescending { it.second }.map { (c, _, affinity) ->
            c.movie.copy(reason = reason(c, affinity, genreWeight, genreNames))
        }
    }

    /** Per genre: hoe vaak komt het voor in je likes, geschaald naar 0..1. */
    fun genreWeights(liked: List<Movie>): Map<Int, Double> {
        val counts = liked.flatMap { it.genreIds }.groupingBy { it }.eachCount()
        val max = counts.values.maxOrNull()?.toDouble() ?: return emptyMap()
        return counts.mapValues { it.value / max }
    }

    fun affinity(movie: Movie, weights: Map<Int, Double>): Double {
        if (weights.isEmpty() || movie.genreIds.isEmpty()) return 0.0
        val w = movie.genreIds.map { weights[it] ?: 0.0 }
        return 0.7 * w.max() + 0.3 * w.average()
    }

    private fun reason(c: Candidate, affinity: Double, weights: Map<Int, Double>, names: Map<Int, String>): String {
        val m = c.movie
        return when {
            c.becauseOf.isNotEmpty() -> "Omdat je ${c.becauseOf.first()} leuk vond"
            affinity >= 0.5 -> {
                val top = m.genreIds.maxByOrNull { weights[it] ?: 0.0 }
                "Past bij je smaak: ${names[top]?.lowercase() ?: "jouw genres"}"
            }
            c.classic -> "Klassieker uit ${m.year}"
            else -> "Populair deze week"
        }
    }
}
