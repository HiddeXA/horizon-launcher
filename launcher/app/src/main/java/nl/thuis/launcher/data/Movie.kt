package nl.thuis.launcher.data

import org.json.JSONArray
import org.json.JSONObject

/** Bron van een aanbeveling, gebruikt voor de score en de uitleg in de top shelf. */
enum class Source { TRENDING, CLASSIC, BECAUSE_YOU_LIKED }

data class Movie(
    val id: Int,
    val title: String,
    val year: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val genreIds: List<Int>,
    val voteAverage: Double,
    val popularity: Double,
    val imdbId: String? = null,
    val imdbRating: String? = null,
    val rtScore: String? = null,
    val reason: String = "",
    val originalTitle: String? = null,
) {
    val posterUrl get() = posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }
    val backdropUrl get() = backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("title", title).put("year", year).put("overview", overview)
        .put("poster", posterPath).put("backdrop", backdropPath)
        .put("genres", JSONArray(genreIds))
        .put("vote", voteAverage).put("pop", popularity)
        .put("imdbId", imdbId).put("imdb", imdbRating).put("rt", rtScore)
        .put("reason", reason)
        .put("orig", originalTitle)

    companion object {
        fun fromJson(o: JSONObject) = Movie(
            id = o.getInt("id"),
            title = o.optString("title"),
            year = o.optString("year"),
            overview = o.optString("overview"),
            posterPath = o.optStringOrNull("poster"),
            backdropPath = o.optStringOrNull("backdrop"),
            genreIds = o.optJSONArray("genres").toIntList(),
            voteAverage = o.optDouble("vote", 0.0),
            popularity = o.optDouble("pop", 0.0),
            imdbId = o.optStringOrNull("imdbId"),
            imdbRating = o.optStringOrNull("imdb"),
            rtScore = o.optStringOrNull("rt"),
            reason = o.optString("reason"),
            originalTitle = o.optStringOrNull("orig"),
        )

        /** Leest één film uit een TMDB-resultaat (trending, discover, recommendations). */
        fun fromTmdb(o: JSONObject) = Movie(
            id = o.getInt("id"),
            title = o.optString("title", o.optString("original_title")),
            year = o.optString("release_date").take(4),
            overview = o.optString("overview"),
            posterPath = o.optStringOrNull("poster_path"),
            backdropPath = o.optStringOrNull("backdrop_path"),
            genreIds = o.optJSONArray("genre_ids").toIntList(),
            voteAverage = o.optDouble("vote_average", 0.0),
            popularity = o.optDouble("popularity", 0.0),
            originalTitle = o.optStringOrNull("original_title"),
        )
    }
}

internal fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key) || !has(key)) null else optString(key).takeIf { it.isNotBlank() && it != "N/A" }

internal fun JSONArray?.toIntList(): List<Int> =
    if (this == null) emptyList() else List(length()) { getInt(it) }

internal fun List<Movie>.toJsonArray(): JSONArray = JSONArray().also { a -> forEach { a.put(it.toJson()) } }

internal fun JSONArray.toMovies(): List<Movie> = List(length()) { Movie.fromJson(getJSONObject(it)) }
