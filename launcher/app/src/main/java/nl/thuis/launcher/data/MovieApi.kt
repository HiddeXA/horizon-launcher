package nl.thuis.launcher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * TMDB levert de films (trending, klassiekers, vergelijkbare films, posters).
 * OMDb levert per film de IMDb-score en de Rotten Tomatoes Tomatometer.
 *
 * Geen van beide heeft een officiële gratis API voor IMDb/Rotten Tomatoes zelf,
 * en scrapen schendt hun voorwaarden; dit is de nette route.
 */
data class MovieDetails(
    val runtimeMinutes: Int?,
    val genres: List<String>,
    val tagline: String?,
    val director: String?,
    val cast: List<String>,
    /** YouTube-videocode van de trailer, of null als TMDB er geen heeft. */
    val trailerKey: String?,
    val originalTitle: String?,
)

class MovieApi(
    private val tmdbTokenProvider: () -> String,
    private val omdbKeyProvider: () -> String,
    private val language: String = "nl-NL",
) {
    /** Altijd de actuele waarde uit de instellingen. */
    private val tmdbToken get() = tmdbTokenProvider()
    private val omdbKey get() = omdbKeyProvider()

    /** Na het wijzigen van sleutels: oude antwoorden vergeten. */
    fun clearCache() = synchronized(memo) { memo.clear() }

    val hasTmdb get() = tmdbToken.isNotBlank()
    val hasOmdb get() = omdbKey.isNotBlank()

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Simpele geheugencache per sessie, zodat dezelfde aanvraag niet twee keer gaat.
    private val memo = HashMap<String, JSONObject>()

    suspend fun trending(): List<Movie> =
        tmdb("trending/movie/week").results()

    /** Hoog gewaardeerde films van vóór 2006 met veel stemmen: de "klassiekers". */
    suspend fun classics(page: Int): List<Movie> =
        tmdb("discover/movie",
            "sort_by" to "vote_average.desc",
            "vote_count.gte" to "4000",
            "primary_release_date.lte" to "2005-12-31",
            "page" to page.toString(),
        ).results()

    suspend fun similarTo(movieId: Int): List<Movie> =
        tmdb("movie/$movieId/recommendations").results()

    /** Alles voor het filmscherm in één verzoek: duur, genres, regie, cast en de trailer. */
    suspend fun details(movieId: Int): MovieDetails {
        val o = tmdb(
            "movie/$movieId",
            "append_to_response" to "credits,videos",
            "include_video_language" to "nl,en,null",
        )
        val genres = o.optJSONArray("genres")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("name") } }.orEmpty()
        val credits = o.optJSONObject("credits")
        val director = credits?.optJSONArray("crew")?.let { a ->
            (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.optString("job") == "Director" }?.optString("name")
        }
        val cast = credits?.optJSONArray("cast")?.let { a -> (0 until minOf(4, a.length())).map { a.getJSONObject(it).getString("name") } }.orEmpty()
        val videos = o.optJSONObject("videos")?.optJSONArray("results")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
            .filter { it.optString("site") == "YouTube" && it.optString("key").isNotBlank() }
        // Voorkeur: officiële trailer, Nederlands boven Engels, anders een teaser.
        val trailer = videos.sortedWith(
            compareBy<JSONObject>(
                { if (it.optString("type") == "Trailer") 0 else if (it.optString("type") == "Teaser") 1 else 2 },
                { if (it.optBoolean("official")) 0 else 1 },
                { when (it.optString("iso_639_1")) { "nl" -> 0; "en" -> 1; else -> 2 } },
            )
        ).firstOrNull()?.optString("key")
        return MovieDetails(
            runtimeMinutes = o.optInt("runtime", 0).takeIf { it > 0 },
            genres = genres,
            tagline = o.optStringOrNull("tagline"),
            director = director,
            cast = cast,
            trailerKey = trailer,
            originalTitle = o.optStringOrNull("original_title"),
        )
    }

    suspend fun genreNames(): Map<Int, String> {
        val arr = tmdb("genre/movie/list").optJSONArray("genres") ?: return emptyMap()
        return (0 until arr.length()).associate {
            val g = arr.getJSONObject(it)
            g.getInt("id") to g.getString("name")
        }
    }

    suspend fun imdbId(movieId: Int): String? =
        tmdb("movie/$movieId/external_ids").optStringOrNull("imdb_id")

    /** Geeft (IMDb-score, Rotten Tomatoes-percentage) terug, bijv. ("8.1", "93%"). */
    suspend fun ratings(imdbId: String): Pair<String?, String?> {
        if (!hasOmdb) return null to null
        val url = "https://www.omdbapi.com/".toHttpUrl().newBuilder()
            .addQueryParameter("i", imdbId)
            .addQueryParameter("apikey", omdbKey)
            .build()
        val json = get(url, auth = false)
        val rt = json.optJSONArray("Ratings")?.let { arr ->
            (0 until arr.length()).map { arr.getJSONObject(it) }
                .firstOrNull { it.optString("Source") == "Rotten Tomatoes" }
                ?.optStringOrNull("Value")
        }
        return json.optStringOrNull("imdbRating") to rt
    }

    private fun JSONObject.results(): List<Movie> {
        val arr = optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).map { Movie.fromTmdb(arr.getJSONObject(it)) }
    }

    private suspend fun tmdb(path: String, vararg params: Pair<String, String>): JSONObject {
        val url = "https://api.themoviedb.org/3/$path".toHttpUrl().newBuilder().apply {
            addQueryParameter("language", language)
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        return get(url, auth = true)
    }

    private suspend fun get(url: HttpUrl, auth: Boolean): JSONObject {
        val key = url.toString()
        synchronized(memo) { memo[key]?.let { return it } }
        val json = withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).apply {
                if (auth) header("Authorization", "Bearer $tmdbToken")
                header("Accept", "application/json")
            }.build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} voor ${url.encodedPath}")
                JSONObject(resp.body!!.string())
            }
        }
        synchronized(memo) { memo[key] = json }
        return json
    }
}
