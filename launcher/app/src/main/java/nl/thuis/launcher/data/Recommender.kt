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
            // Films en series apart rangschikken, elk met hun eigen likes; daarna samen op score.
            val (likedFilms, likedSeries) = RecScoring.splitLikes(liked)
            val (films, series) = coroutineScope {
                val f = async {
                    val (trending, classics, similar, genres) = coroutineScope {
                        val t = async { api.trending() }
                        val c = async { api.classics(page = 1 + today.dayOfYear % 3) }
                        val s = likedFilms.take(MAX_SEEDS).map { seed -> async { seed to api.similarTo(seed.id).take(12) } }
                        val g = async { api.genreNames() }
                        Quad(t.await(), c.await(), s.awaitAll().toMap(), g.await())
                    }
                    RecScoring.rankScored(trending, classics, similar, likedFilms, genres, random = Random(today.toEpochDay()))
                }
                // Lukt het voor series niet, dan blijven de films gewoon staan.
                val s = async {
                    runCatching {
                        val (trending, classics, similar, genres) = coroutineScope {
                            val t = async { api.trendingTv() }
                            val c = async { api.classicsTv(page = 1 + today.dayOfYear % 3) }
                            val sim = likedSeries.take(MAX_SEEDS).map { seed -> async { seed to api.similarToTv(seed.id).take(12) } }
                            val g = async { api.genreNamesTv() }
                            Quad(t.await(), c.await(), sim.awaitAll().toMap(), g.await())
                        }
                        RecScoring.rankScored(trending, classics, similar, likedSeries, genres, size = SERIES_SIZE, minClassics = 3, random = Random(today.toEpochDay() + 1))
                    }.getOrDefault(emptyList())
                }
                f.await() to s.await()
            }
            val enriched = enrich(RecScoring.mix(films, series))

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
                    ratingsPrefs.getString(ratingsKey(m), null)?.let { cachedRatings ->
                        val o = JSONObject(cachedRatings)
                        return@async m.copy(
                            imdbId = o.optStringOrNull("id"),
                            imdbRating = o.optStringOrNull("imdb"),
                            rtScore = o.optStringOrNull("rt"),
                        )
                    }
                    runCatching {
                        val imdbId = api.imdbId(m) ?: return@runCatching m
                        val (imdb, rt) = api.ratings(imdbId)
                        if (api.hasOmdb) {
                            ratingsPrefs.edit().putString(
                                ratingsKey(m),
                                JSONObject().put("id", imdbId).put("imdb", imdb).put("rt", rt).toString(),
                            ).apply()
                        }
                        m.copy(imdbId = imdbId, imdbRating = imdb, rtScore = rt)
                    }.getOrDefault(m)
                }
            }.awaitAll()
        }
    }

    /** Films houden hun oude sleutel (het nummer), series krijgen "tv<nummer>". */
    private fun ratingsKey(m: Movie) = if (m.isSeries) m.key else m.id.toString()

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    companion object {
        /** De laatst berekende aanbevelingen, voor de Bios-app (zonder netwerk). */
        fun readCache(context: Context): List<Movie> = runCatching {
            JSONArray(context.getSharedPreferences("recs", Context.MODE_PRIVATE).getString("json", "[]")).toMovies()
        }.getOrDefault(emptyList())

        private const val CACHE_VERSION = 3
        private const val SERIES_SIZE = 10
        private const val CACHE_MS = 6 * 60 * 60 * 1000L
        private const val MAX_SEEDS = 6
    }
}
