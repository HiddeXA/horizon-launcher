package nl.thuis.launcher.bios

import android.content.Context
import android.util.Log
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

/**
 * Rijen voor de tab Series via TMDB: per rij de populairste series met dat genre, waarvan we houden
 * wat in jouw aanbod zit. Zelfde aanpak als [BiosGenres] voor films; resultaat wordt per dag bewaard.
 */
object SeriesGenres {
    private const val TAG = "HorizonSeries"
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

    @Volatile var meta: Map<Int, TmdbMeta> = emptyMap() // serie-id -> TMDB-info
        private set

    val hasTmdb get() = Secrets.get(Secrets.Key.TMDB).isNotBlank()

    @Volatile private var memo: Pair<SeriesIndex, Map<String, List<Int>>>? = null

    suspend fun shelves(context: Context, index: SeriesIndex): Map<String, List<Int>> = withContext(Dispatchers.IO) {
        if (!hasTmdb) return@withContext emptyMap()
        // Al opgehaald in deze sessie (bij het wisselen van tab): niet opnieuw laden.
        memo?.let { (i, r) -> if (i === index) return@withContext r }
        val file = File(context.filesDir, "series-genres.json")
        val today = LocalDate.now().toString() + "/t" + TitleMatch.VERSION + "/s1/" + index.items.size
        readCache(file, today, index)?.let {
            Log.i(TAG, "Serierijen uit opslag: ${it.count { r -> r.value.size >= 6 }} met genoeg series")
            memo = index to it
            return@withContext it
        }
        val gate = Semaphore(4)
        val found = HashMap<Int, TmdbMeta>()
        val thisYear = LocalDate.now().year
        val rows = coroutineScope {
            SeriesShelves.map { shelf ->
                async {
                    gate.withPermit {
                        val want = if (shelf.kind == SeriesShelfKind.TOP) 100 else 40
                        val maxPages = if (shelf.kind == SeriesShelfKind.TOP) 20 else 5
                        val ids = ArrayList<Int>()
                        for (page in 1..maxPages) {
                            val list = runCatching {
                                when (shelf.kind) {
                                    SeriesShelfKind.TOP -> discover(emptyList(), page, "vote_average.desc", minVotes = 1500)
                                    SeriesShelfKind.POPULAR -> discover(emptyList(), page, "popularity.desc", minVotes = 80, fromYear = thisYear - 2)
                                    else -> discover(shelf.genres.map { it.id }, page, "popularity.desc", minVotes = 50)
                                }
                            }.getOrNull() ?: break
                            for (m in list) {
                                val item = index.find(m.tmdbId, listOfNotNull(m.title, m.originalTitle), m.year) ?: continue
                                if (item.id !in ids) ids += item.id
                                synchronized(found) { found[item.id] = m.meta }
                            }
                            if (ids.size >= want || list.isEmpty()) break
                        }
                        shelf.key to ids.take(want)
                    }
                }
            }.awaitAll().toMap()
        }
        meta = meta + found
        writeCache(file, today, rows)
        memo = index to rows
        Log.i(TAG, "Serierijen: ${rows.count { it.value.size >= 6 }} met genoeg series, top=${rows["top"]?.size}")
        rows
    }

    /**
     * Filter met genres: TMDB zoekt, wij houden alleen wat in je aanbod zit. Zelfde regels als
     * [BiosGenres.filter]: meerdere [combos] mogen, en geen antwoord van TMDB is een fout.
     */
    suspend fun filter(index: SeriesIndex, combos: List<List<Int>>, period: FilterPeriod, minScore: Double, sort: FilterSort): List<SeriesItem> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<Int, SeriesItem>()
        val order = if (sort == FilterSort.RATING) "vote_average.desc" else "popularity.desc"
        val pages = if (combos.size <= 1) 8 else maxOf(2, 8 / combos.size)
        var answered = false
        var lastError: Throwable? = null
        for (combo in combos) {
            for (page in 1..pages) {
                val list = runCatching { discover(combo, page, order, if (sort == FilterSort.RATING) 200 else 30, period, minScore) }
                    .onFailure { lastError = it }.getOrNull() ?: break
                answered = true
                if (list.isEmpty()) break
                val found = HashMap<Int, TmdbMeta>()
                for (m in list) {
                    val item = index.find(m.tmdbId, listOfNotNull(m.title, m.originalTitle), m.year) ?: continue
                    out.putIfAbsent(item.id, item)
                    found[item.id] = m.meta
                }
                meta = meta + found
                if (out.size >= 60) break
            }
            if (out.size >= 60) break
        }
        if (!answered) throw java.io.IOException("TMDB niet bereikbaar", lastError)
        var list = out.values.toList()
        if (combos.size > 1) list = when (sort) {
            FilterSort.RATING -> list.sortedByDescending { meta[it.id]?.vote ?: 0.0 }
            else -> list.sortedByDescending { meta[it.id]?.popularity ?: 0.0 }
        }
        when (sort) {
            FilterSort.AZ -> list.sortedBy { it.title.lowercase() }
            FilterSort.NEWEST -> list.sortedByDescending { it.modified }
            else -> list
        }
    }

    /** Filter zonder genres: rechtstreeks op je aanbod (zelfde regels als [filterCatalog] voor films). */
    fun filterCatalog(index: SeriesIndex, f: BiosFilter, limit: Int = 120): List<SeriesItem> {
        val seq = index.items.asSequence().filter { item ->
            val y = (item.year ?: meta[item.id]?.year)?.toIntOrNull()
            val inPeriod = when {
                f.period == FilterPeriod.ALL -> true
                y == null -> false
                else -> (f.period.from == null || y >= f.period.from) && (f.period.to == null || y <= f.period.to)
            }
            val score = item.rating ?: meta[item.id]?.vote
            inPeriod && (f.minScore == 0.0 || (score != null && score >= f.minScore))
        }.distinctBy { it.key + (it.year ?: "") }
        return when (f.sort) {
            FilterSort.AZ -> seq.sortedBy { it.title.lowercase() }
            FilterSort.RATING -> seq.sortedByDescending { it.rating ?: meta[it.id]?.vote ?: 0.0 }
            FilterSort.POPULAR -> seq.sortedByDescending { meta[it.id]?.popularity ?: (it.modified / 1e9) }
            FilterSort.NEWEST -> seq.sortedByDescending { it.modified }
        }.take(limit).toList()
    }

    /** TMDB-genres van één serie (voor je kijkprofiel). */
    suspend fun genresFor(item: SeriesItem, tmdbId: Int?): List<Int> = withContext(Dispatchers.IO) {
        meta[item.id]?.genres?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
        if (!hasTmdb) return@withContext emptyList()
        runCatching {
            val url = if (tmdbId != null) "https://api.themoviedb.org/3/tv/$tmdbId".toHttpUrl().newBuilder().addQueryParameter("language", "nl-NL").build()
            else "https://api.themoviedb.org/3/search/tv".toHttpUrl().newBuilder().addQueryParameter("query", item.title)
                .apply { item.year?.let { addQueryParameter("first_air_date_year", it) } }.build()
            val req = Request.Builder().url(url).header("Authorization", "Bearer ${Secrets.get(Secrets.Key.TMDB)}").build()
            http.newCall(req).execute().use { r ->
                val o = JSONObject(r.body!!.string())
                if (tmdbId != null) o.optJSONArray("genres")?.let { g -> List(g.length()) { g.getJSONObject(it).getInt("id") } }.orEmpty()
                else o.optJSONArray("results")?.optJSONObject(0)?.optJSONArray("genre_ids")?.let { g -> List(g.length()) { g.getInt(it) } }.orEmpty()
            }
        }.getOrDefault(emptyList())
    }

    private data class Found(val tmdbId: Int, val title: String, val originalTitle: String?, val year: String?, val meta: TmdbMeta)

    private fun discover(
        genres: List<Int>, page: Int, sort: String, minVotes: Int,
        period: FilterPeriod = FilterPeriod.ALL, minScore: Double = 0.0, fromYear: Int? = null,
    ): List<Found> {
        val url = "https://api.themoviedb.org/3/discover/tv".toHttpUrl().newBuilder().apply {
            addQueryParameter("language", "nl-NL")
            addQueryParameter("sort_by", sort)
            addQueryParameter("vote_count.gte", minVotes.toString())
            if (genres.isNotEmpty()) addQueryParameter("with_genres", genres.joinToString(","))
            (fromYear ?: period.from)?.let { addQueryParameter("first_air_date.gte", "$it-01-01") }
            period.to?.let { addQueryParameter("first_air_date.lte", "$it-12-31") }
            if (minScore > 0) addQueryParameter("vote_average.gte", minScore.toString())
            addQueryParameter("page", page.toString())
        }.build()
        val req = Request.Builder().url(url).header("Authorization", "Bearer ${Secrets.get(Secrets.Key.TMDB)}").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("TMDB ${r.code}")
            val arr = JSONObject(r.body!!.string()).optJSONArray("results") ?: return emptyList()
            return (0 until arr.length()).map { arr.getJSONObject(it) }.map { o ->
                val id = o.getInt("id")
                val year = o.optString("first_air_date").take(4).ifBlank { null }
                Found(
                    id, o.optString("name"), o.optString("original_name").ifBlank { null }, year,
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

    private fun writeCache(file: File, day: String, rows: Map<String, List<Int>>) = runCatching {
        val o = JSONObject().put("day", day)
        o.put("rows", JSONObject().also { j -> rows.forEach { (k, v) -> j.put(k, JSONArray(v)) } })
        o.put("meta", JSONArray().also { a ->
            meta.forEach { (sid, m) ->
                a.put(JSONArray().put(sid).put(m.tmdbId).put(JSONArray(m.genres)).put(m.vote).put(m.popularity)
                    .put(m.backdrop ?: JSONObject.NULL).put(m.overview ?: JSONObject.NULL).put(m.year ?: JSONObject.NULL))
            }
        })
        file.writeText(o.toString())
    }

    private fun readCache(file: File, day: String, index: SeriesIndex): Map<String, List<Int>>? = runCatching {
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
