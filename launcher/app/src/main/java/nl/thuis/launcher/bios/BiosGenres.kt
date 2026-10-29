package nl.thuis.launcher.bios

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import nl.thuis.launcher.data.Secrets
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** TMDB-genres met Nederlandse namen. */
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

/** Soort rij op het startscherm. */
enum class ShelfKind { TOP, POPULAR, CLASSIC, GENRE }

/**
 * Een categorie op het startscherm. "Nieuwe films" en "Top 100" staan altijd bovenaan;
 * de rest wordt gesorteerd op je kijkgedrag (zie [Taste]). Zonder kijkgedrag geldt deze volgorde.
 */
@Immutable
data class GenreShelf(val key: String, val title: String, val kind: ShelfKind, val genres: List<Genre> = emptyList(), val pinned: Boolean = false)

val GenreShelves: List<GenreShelf> = listOf(
    GenreShelf("top100", "Top 100", ShelfKind.TOP, pinned = true),
    GenreShelf("popular", "Populair nu", ShelfKind.POPULAR),
    GenreShelf("classics", "Klassiekers", ShelfKind.CLASSIC),
    GenreShelf("action", "Actie", ShelfKind.GENRE, listOf(Genre.ACTION)),
    GenreShelf("comedy", "Komedie", ShelfKind.GENRE, listOf(Genre.COMEDY)),
    GenreShelf("thriller", "Thriller", ShelfKind.GENRE, listOf(Genre.THRILLER)),
    GenreShelf("horror", "Horror", ShelfKind.GENRE, listOf(Genre.HORROR)),
    GenreShelf("drama", "Drama", ShelfKind.GENRE, listOf(Genre.DRAMA)),
    GenreShelf("scifi", "Sciencefiction", ShelfKind.GENRE, listOf(Genre.SCIFI)),
    GenreShelf("crime", "Misdaad", ShelfKind.GENRE, listOf(Genre.CRIME)),
    GenreShelf("romance", "Romantiek", ShelfKind.GENRE, listOf(Genre.ROMANCE)),
    GenreShelf("adventure", "Avontuur", ShelfKind.GENRE, listOf(Genre.ADVENTURE)),
    GenreShelf("animation", "Animatie", ShelfKind.GENRE, listOf(Genre.ANIMATION)),
    GenreShelf("family", "Familie", ShelfKind.GENRE, listOf(Genre.FAMILY)),
    GenreShelf("fantasy", "Fantasy", ShelfKind.GENRE, listOf(Genre.FANTASY)),
    GenreShelf("mystery", "Mysterie", ShelfKind.GENRE, listOf(Genre.MYSTERY)),
    GenreShelf("documentary", "Documentaires", ShelfKind.GENRE, listOf(Genre.DOCUMENTARY)),
    GenreShelf("war", "Oorlog", ShelfKind.GENRE, listOf(Genre.WAR)),
    GenreShelf("history", "Historisch", ShelfKind.GENRE, listOf(Genre.HISTORY)),
    GenreShelf("music", "Muziek", ShelfKind.GENRE, listOf(Genre.MUSIC)),
    GenreShelf("western", "Western", ShelfKind.GENRE, listOf(Genre.WESTERN)),
)

/** Wat we via TMDB over een film in je aanbod weten (voor billboard en filter). */
@Immutable
data class TmdbMeta(
    val tmdbId: Int,
    val genres: List<Int>,
    val vote: Double,
    val popularity: Double,
    val backdrop: String?,
    val overview: String?,
    val year: String?,
)

/**
 * Genres via TMDB: per rij vragen we TMDB om de populairste films met die genre(s) en houden
 * we de films over die in jouw aanbod zitten. Zo zijn een paar tientallen verzoeken genoeg,
 * in plaats van één per film. Resultaat wordt per dag op het kastje bewaard.
 */
object BiosGenres {
    private const val TAG = "BiosGenres"
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

    @Volatile var meta: Map<Int, TmdbMeta> = emptyMap() // VOD-id -> TMDB-info
        private set

    val hasTmdb get() = Secrets.get(Secrets.Key.TMDB).isNotBlank()

    /** Rijen (sleutel -> VOD-ids) voor het startscherm. */
    suspend fun shelves(context: Context, index: VodIndex): Map<String, List<Int>> = withContext(Dispatchers.IO) {
        if (!hasTmdb) return@withContext emptyMap()
        val file = File(context.filesDir, "bios-genres.json")
        val today = LocalDate.now().toString() + "/t" + TitleMatch.VERSION + "/c3"
        readCache(file, today, index)?.let {
            Log.i(TAG, "Genrerijen uit opslag: ${it.count { r -> r.value.size >= 6 }} met genoeg films")
            return@withContext it
        }

        val gate = Semaphore(4)
        val found = HashMap<Int, TmdbMeta>()
        val thisYear = LocalDate.now().year
        val rows = coroutineScope {
            GenreShelves.map { shelf ->
                async {
                    gate.withPermit {
                        val want = if (shelf.kind == ShelfKind.TOP) 100 else 40
                        val maxPages = if (shelf.kind == ShelfKind.TOP) 30 else 5
                        val ids = ArrayList<Int>()
                        for (page in 1..maxPages) {
                            val movies = runCatching {
                                when (shelf.kind) {
                                    // Best beoordeeld aller tijden (zoals de IMDb Top 250), op volgorde.
                                    ShelfKind.TOP -> discover(emptyList(), page, "vote_average.desc", minVotes = 4000, toYear = thisYear - 1)
                                    ShelfKind.POPULAR -> discover(emptyList(), page, "popularity.desc", minVotes = 150, fromYear = thisYear - 2)
                                    ShelfKind.CLASSIC -> discover(emptyList(), page, "vote_count.desc", minVotes = 1500, toYear = 1995)
                                    ShelfKind.GENRE -> discover(shelf.genres.map { it.id }, page, "popularity.desc", minVotes = 80)
                                }
                            }.getOrNull() ?: break
                            for (m in movies) {
                                val item = index.find(m.tmdbId, listOfNotNull(m.title, m.originalTitle), m.year) ?: continue
                                if (item.id !in ids) ids += item.id
                                synchronized(found) { found[item.id] = m.meta }
                            }
                            if (ids.size >= want || movies.isEmpty()) break
                        }
                        shelf.key to ids.take(want)
                    }
                }
            }.awaitAll().toMap()
        }
        meta = meta + found
        writeCache(file, today, rows)
        Log.i(TAG, "Genrerijen: ${rows.count { it.value.size >= 6 }} met genoeg films, top100=${rows["top100"]?.size}")
        rows
    }

    /** TMDB-genres van één film (voor je kijkprofiel): uit wat we al weten, of één verzoek bij TMDB. */
    suspend fun genresFor(item: VodItem, tmdbId: Int?): List<Int> = withContext(Dispatchers.IO) {
        meta[item.id]?.genres?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
        if (!hasTmdb) return@withContext emptyList()
        runCatching {
            val url = if (tmdbId != null) "https://api.themoviedb.org/3/movie/$tmdbId".toHttpUrl().newBuilder().addQueryParameter("language", "nl-NL").build()
            else "https://api.themoviedb.org/3/search/movie".toHttpUrl().newBuilder().addQueryParameter("query", item.title)
                .apply { item.year?.let { addQueryParameter("year", it) } }.build()
            val req = Request.Builder().url(url).header("Authorization", "Bearer ${Secrets.get(Secrets.Key.TMDB)}").build()
            http.newCall(req).execute().use { r ->
                val o = JSONObject(r.body!!.string())
                if (tmdbId != null) o.optJSONArray("genres")?.let { g -> List(g.length()) { g.getJSONObject(it).getInt("id") } }.orEmpty()
                else o.optJSONArray("results")?.optJSONObject(0)?.optJSONArray("genre_ids")?.let { g -> List(g.length()) { g.getInt(it) } }.orEmpty()
            }
        }.getOrDefault(emptyList())
    }

    /** Filter met genres: TMDB zoekt, wij houden alleen wat in je aanbod zit. */
    suspend fun filter(index: VodIndex, f: BiosFilter): List<VodItem> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<Int, VodItem>()
        val sort = when (f.sort) {
            FilterSort.POPULAR, FilterSort.NEWEST -> "popularity.desc"
            FilterSort.RATING -> "vote_average.desc"
            FilterSort.AZ -> "popularity.desc"
        }
        for (page in 1..8) {
            val movies = runCatching {
                discover(f.genres.map { it.id }, page, sort, if (f.sort == FilterSort.RATING) 300 else 50, f.period, f.minScore)
            }.getOrNull() ?: break
            if (movies.isEmpty()) break
            val found = HashMap<Int, TmdbMeta>()
            for (m in movies) {
                val item = index.find(m.tmdbId, listOfNotNull(m.title, m.originalTitle), m.year) ?: continue
                out.putIfAbsent(item.id, item)
                found[item.id] = m.meta
            }
            meta = meta + found
            if (out.size >= 60) break
        }
        val list = out.values.toList()
        when (f.sort) {
            FilterSort.AZ -> list.sortedBy { it.title.lowercase() }
            FilterSort.NEWEST -> list.sortedByDescending { it.added }
            else -> list
        }
    }

    private data class Found(val tmdbId: Int, val title: String, val originalTitle: String?, val year: String?, val meta: TmdbMeta)

    private fun discover(
        genres: List<Int>, page: Int, sort: String, minVotes: Int,
        period: FilterPeriod = FilterPeriod.ALL, minScore: Double = 0.0,
        fromYear: Int? = null, toYear: Int? = null,
    ): List<Found> {
        val url = "https://api.themoviedb.org/3/discover/movie".toHttpUrl().newBuilder().apply {
            addQueryParameter("language", "nl-NL")
            addQueryParameter("sort_by", sort)
            addQueryParameter("vote_count.gte", minVotes.toString())
            if (genres.isNotEmpty()) addQueryParameter("with_genres", genres.joinToString(",")) // komma = én én
            (fromYear ?: period.from)?.let { addQueryParameter("primary_release_date.gte", "$it-01-01") }
            (toYear ?: period.to)?.let { addQueryParameter("primary_release_date.lte", "$it-12-31") }
            if (minScore > 0) addQueryParameter("vote_average.gte", minScore.toString())
            addQueryParameter("page", page.toString())
        }.build()
        val req = Request.Builder().url(url).header("Authorization", "Bearer ${Secrets.get(Secrets.Key.TMDB)}").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("TMDB ${r.code}")
            val arr = JSONObject(r.body!!.string()).optJSONArray("results") ?: return emptyList()
            return (0 until arr.length()).map { arr.getJSONObject(it) }.map { o ->
                val id = o.getInt("id")
                val year = o.optString("release_date").take(4).ifBlank { null }
                Found(
                    id, o.optString("title"), o.optString("original_title").ifBlank { null }, year,
                    TmdbMeta(
                        tmdbId = id,
                        genres = o.optJSONArray("genre_ids")?.let { g -> List(g.length()) { g.getInt(it) } }.orEmpty(),
                        vote = o.optDouble("vote_average", 0.0),
                        popularity = o.optDouble("popularity", 0.0),
                        backdrop = o.optString("backdrop_path").takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/w1280$it" },
                        overview = o.optString("overview").ifBlank { null },
                        year = year,
                    ),
                )
            }
        }
    }

    // --- Dagcache: rijen + TMDB-info, zodat het startscherm direct vol staat ---

    private fun writeCache(file: File, day: String, rows: Map<String, List<Int>>) = runCatching {
        val o = JSONObject().put("day", day)
        o.put("rows", JSONObject().also { j -> rows.forEach { (k, v) -> j.put(k, JSONArray(v)) } })
        o.put("meta", JSONArray().also { a ->
            meta.forEach { (vid, m) ->
                a.put(JSONArray().put(vid).put(m.tmdbId).put(JSONArray(m.genres)).put(m.vote).put(m.popularity)
                    .put(m.backdrop ?: JSONObject.NULL).put(m.overview ?: JSONObject.NULL).put(m.year ?: JSONObject.NULL))
            }
        })
        file.writeText(o.toString())
    }

    private fun readCache(file: File, day: String, index: VodIndex): Map<String, List<Int>>? = runCatching {
        if (!file.exists()) return null
        val o = JSONObject(file.readText())
        if (o.optString("day") != day) return null
        val m = HashMap<Int, TmdbMeta>()
        o.optJSONArray("meta")?.let { a ->
            for (i in 0 until a.length()) {
                val r = a.getJSONArray(i)
                val g = r.getJSONArray(2)
                m[r.getInt(0)] = TmdbMeta(
                    r.getInt(1), List(g.length()) { g.getInt(it) }, r.optDouble(3), r.optDouble(4),
                    r.optString(5).takeIf { !r.isNull(5) }, r.optString(6).takeIf { !r.isNull(6) }, r.optString(7).takeIf { !r.isNull(7) },
                )
            }
        }
        meta = meta + m
        val rows = o.getJSONObject("rows")
        rows.keys().asSequence().associateWith { k ->
            val a = rows.getJSONArray(k)
            List(a.length()) { a.getInt(it) }.filter { it in index.byId }
        }
    }.getOrNull()
}

// --- Filter ---

enum class FilterPeriod(val label: String, val from: Int?, val to: Int?) {
    ALL("Alles", null, null), NOW("2020 en later", 2020, null), TENS("Jaren 10", 2010, 2019),
    NOUGHTIES("Jaren 00", 2000, 2009), NINETIES("Jaren 90", 1990, 1999), EIGHTIES("Jaren 80", 1980, 1989),
    CLASSIC("Ouder", null, 1979),
}

enum class FilterSort(val label: String) { POPULAR("Populair"), NEWEST("Nieuw toegevoegd"), RATING("Beste score"), AZ("A–Z") }

@Immutable
data class BiosFilter(
    val genres: Set<Genre> = emptySet(),
    val period: FilterPeriod = FilterPeriod.ALL,
    val minScore: Double = 0.0,
    val sort: FilterSort = FilterSort.POPULAR,
) {
    val isEmpty get() = genres.isEmpty() && period == FilterPeriod.ALL && minScore == 0.0
}

/** Filter zonder genres: rechtstreeks op je aanbod (jaartal uit de titel, score van de aanbieder). */
fun filterCatalog(index: VodIndex, f: BiosFilter, limit: Int = 120): List<VodItem> {
    val seq = index.items.asSequence().filter { item ->
        val y = item.year?.toIntOrNull()
        val inPeriod = when {
            f.period == FilterPeriod.ALL -> true
            y == null -> false
            else -> (f.period.from == null || y >= f.period.from) && (f.period.to == null || y <= f.period.to)
        }
        // Score van de aanbieder is op 10; niet elke aanbieder vult hem in.
        val score = item.rating ?: BiosGenres.meta[item.id]?.vote
        inPeriod && (f.minScore == 0.0 || (score != null && score >= f.minScore))
    }.distinctBy { it.key + (it.year ?: "") }
    return when (f.sort) {
        FilterSort.AZ -> seq.sortedBy { it.title.lowercase() }
        FilterSort.RATING -> seq.sortedByDescending { it.rating ?: BiosGenres.meta[it.id]?.vote ?: 0.0 }
        FilterSort.POPULAR -> seq.sortedByDescending { BiosGenres.meta[it.id]?.popularity ?: (it.added / 1e9) }
        FilterSort.NEWEST -> seq.sortedByDescending { it.added }
    }.take(limit).toList()
}
