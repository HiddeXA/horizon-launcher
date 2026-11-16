package nl.thuis.launcher.bios

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit

sealed interface SeriesState {
    data object NotConfigured : SeriesState
    data class Loading(val phase: String = "Series laden…", val progress: Float? = null, val count: Int = 0) : SeriesState
    data class Ready(val index: SeriesIndex) : SeriesState
    /** De aanbieder heeft geen series. */
    data object Empty : SeriesState
    data class Error(val message: String) : SeriesState
}

/**
 * Je serie-aanbod (Xtream), los van de films: gaat hier iets mis, dan werken films gewoon door.
 * De lijst wordt op het kastje bewaard en hooguit elke 12 uur ververst; afleveringen worden per serie
 * opgehaald als je het seriescherm opent. Wachtwoord en afspeeladressen worden nooit gelogd.
 */
object Series {
    private const val TAG = "HorizonSeries"
    private const val CACHE_MS = 12 * 60 * 60 * 1000L
    private const val CACHE_FILE = "series-catalog-v1.json"

    private val _state = MutableStateFlow<SeriesState>(if (Bios.config == null) SeriesState.NotConfigured else SeriesState.Loading())
    val state: StateFlow<SeriesState> = _state

    val index: SeriesIndex? get() = (state.value as? SeriesState.Ready)?.index

    private val mutex = Mutex()
    private var loadedAt = 0L
    private val details = LruCache<Int, SeriesDetail>(24)

    @Volatile private var storeInstance: SeriesStore? = null
    fun store(context: Context): SeriesStore =
        storeInstance ?: synchronized(this) { storeInstance ?: SeriesStore(context.applicationContext).also { storeInstance = it } }

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Andere aanbieder of inlog: de lijst van de vorige vergeten. */
    fun reset(context: Context) {
        loadedAt = 0L
        details.evictAll()
        File(context.filesDir, CACHE_FILE).delete()
        _state.value = if (Bios.config == null) SeriesState.NotConfigured else SeriesState.Loading()
    }

    /** Laadt de serielijst: eerst uit de opslag op het kastje, en ververst via internet als hij oud is. */
    suspend fun load(context: Context, force: Boolean = false) {
        val cfg = Bios.config ?: run { _state.value = SeriesState.NotConfigured; return }
        mutex.withLock {
            val fresh = System.currentTimeMillis() - loadedAt < CACHE_MS
            if (!force && fresh && state.value.let { it is SeriesState.Ready || it is SeriesState.Empty }) return

            val file = File(context.filesDir, CACHE_FILE)
            if (index == null && file.exists()) {
                val t0 = System.currentTimeMillis()
                runCatching { withContext(Dispatchers.IO) { readCache(file) } }
                    .onSuccess {
                        _state.value = if (it.items.isEmpty()) SeriesState.Empty else SeriesState.Ready(it)
                        loadedAt = file.lastModified()
                        Log.i(TAG, "Series klaar uit opslag: ${it.items.size} series in ${System.currentTimeMillis() - t0} ms")
                    }
                    .onFailure { Log.w(TAG, "Opslag onleesbaar: ${it.javaClass.simpleName}") }
                if (!force && System.currentTimeMillis() - file.lastModified() < CACHE_MS && state.value !is SeriesState.Loading) return
            }
            val show = index == null
            if (show) _state.value = SeriesState.Loading()

            try {
                val t0 = System.currentTimeMillis()
                val idx = withContext(Dispatchers.IO) {
                    val cats = fetchCategories(cfg)
                    val items = fetchSeries(cfg) { n -> if (show) _state.value = SeriesState.Loading("Series laden…", null, n) }
                    SeriesIndex(items, cats).also { writeCache(file, it) }
                }
                loadedAt = System.currentTimeMillis()
                _state.value = if (idx.items.isEmpty()) SeriesState.Empty else SeriesState.Ready(idx)
                val withTmdb = idx.items.count { it.tmdbId != null }
                Log.i(TAG, "Series klaar via aanbieder: ${idx.items.size} series (${withTmdb} met TMDB-nummer), ${idx.categories.size} categorieën, ${System.currentTimeMillis() - t0} ms")
            } catch (e: Exception) {
                Log.w(TAG, "Series laden mislukt: ${e.javaClass.simpleName}")
                // Een eerder opgeslagen lijst blijft bruikbaar.
                if (index == null) _state.value = SeriesState.Error(
                    if (e is IOException) "Je aanbieder is niet bereikbaar. Films werken wel." else "De serielijst kon niet gelezen worden. Films werken wel."
                )
            }
        }
    }

    fun episodeUrl(ep: Episode): String {
        val c = Bios.config ?: error("Horizon niet ingesteld")
        return "${c.base}/series/${c.user}/${c.pass}/${ep.id}.${ep.ext.ifBlank { "mp4" }}"
    }

    /** Afleveringen en info van een serie die al is opgehaald (voor de speler). */
    fun cachedDetail(seriesId: Int): SeriesDetail? = details.get(seriesId)

    /** Haalt seizoenen, afleveringen en info op. Geeft een fout terug als de aanbieder niet antwoordt. */
    suspend fun detail(item: SeriesItem, force: Boolean = false): Result<SeriesDetail> {
        if (!force) details.get(item.id)?.let { return Result.success(it) }
        val cfg = Bios.config ?: return Result.failure(IOException("niet ingesteld"))
        return runCatching {
            withContext(Dispatchers.IO) {
                val url = api(cfg).addQueryParameter("action", "get_series_info").addQueryParameter("series_id", item.id.toString()).build()
                parseDetail(getJson(url), item.title)
            }
        }.onSuccess { details.put(item.id, it) }
            .onFailure { Log.w(TAG, "Afleveringen laden mislukt: ${it.javaClass.simpleName}") }
    }

    private fun parseDetail(o: JSONObject, seriesTitle: String): SeriesDetail {
        val info = o.optJSONObject("info") ?: JSONObject()
        fun str(k: String) = info.optString(k).takeIf { it.isNotBlank() && it != "null" && it != "N/A" }
        val backdrop = when (val b = info.opt("backdrop_path")) {
            is JSONArray -> if (b.length() > 0) b.optString(0) else null
            is String -> b
            else -> null
        }?.takeIf { it.startsWith("http") }

        // "episodes" is meestal een object per seizoen ({"1":[…],"2":[…]}), soms een lijst van lijsten.
        val lists = ArrayList<Pair<Int?, JSONArray>>()
        when (val e = o.opt("episodes")) {
            is JSONObject -> e.keys().forEach { k -> e.optJSONArray(k)?.let { lists += k.toIntOrNull() to it } }
            is JSONArray -> for (i in 0 until e.length()) e.optJSONArray(i)?.let { lists += null to it }
        }
        val episodes = ArrayList<Episode>()
        for ((seasonKey, arr) in lists) {
            for (i in 0 until arr.length()) {
                val ep = arr.optJSONObject(i) ?: continue
                val id = ep.optString("id").toIntOrNull() ?: continue
                val number = ep.optString("episode_num").toIntOrNull() ?: (i + 1)
                val season = ep.optString("season").toIntOrNull() ?: seasonKey ?: 1
                val ei = ep.optJSONObject("info") ?: JSONObject()
                fun es(k: String) = ei.optString(k).takeIf { it.isNotBlank() && it != "null" && it != "N/A" }
                episodes += Episode(
                    id = id, season = season, number = number,
                    title = EpisodeTitles.clean(ep.optString("title"), seriesTitle, number),
                    ext = ep.optString("container_extension").ifBlank { "mp4" },
                    durationSecs = ei.optInt("duration_secs", 0).takeIf { it >= 60 },
                    plot = es("plot") ?: es("overview"),
                    image = (es("movie_image") ?: es("cover_big"))?.takeIf { it.startsWith("http") },
                    added = ep.optString("added").toLongOrNull() ?: 0L,
                )
            }
        }
        return SeriesDetail(
            seasons = EpisodeOrder.seasons(episodes),
            plot = str("plot"),
            cast = str("cast"),
            genre = str("genre"),
            backdrop = backdrop,
            trailerKey = str("youtube_trailer")?.substringAfterLast("v=")?.substringAfterLast('/'),
            tmdbId = (str("tmdb_id") ?: str("tmdb"))?.toIntOrNull(),
            releaseDate = str("releaseDate") ?: str("release_date") ?: str("releasedate"),
        )
    }

    private fun api(c: XtreamConfig): HttpUrl.Builder = "${c.base}/player_api.php".toHttpUrl().newBuilder()
        .addQueryParameter("username", c.user)
        .addQueryParameter("password", c.pass)

    private fun getJson(url: HttpUrl): JSONObject {
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val body = r.body!!.string().trim()
            if (body.isEmpty() || body == "[]") return JSONObject()
            return JSONObject(body)
        }
    }

    private fun fetchCategories(c: XtreamConfig): List<VodCategory> {
        val url = api(c).addQueryParameter("action", "get_series_categories").build()
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val body = r.body!!.string().trim()
            if (!body.startsWith("[")) return emptyList()
            val arr = JSONArray(body)
            return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map {
                VodCategory(it.optString("category_id"), it.optString("category_name").trim())
            }
        }
    }

    /** Leest get_series als stroom, zonder alles tegelijk in het geheugen te laden. */
    private fun fetchSeries(c: XtreamConfig, onProgress: (Int) -> Unit): List<SeriesItem> {
        val url = api(c).addQueryParameter("action", "get_series").build()
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            JsonReader(InputStreamReader(r.body!!.byteStream(), Charsets.UTF_8)).use { jr ->
                val out = ArrayList<SeriesItem>(4096)
                if (jr.peek() != JsonToken.BEGIN_ARRAY) return out // geen series: de aanbieder stuurt dan {} of null
                var last = 0L
                jr.beginArray()
                while (jr.hasNext()) {
                    readSeries(jr)?.let(out::add)
                    val now = System.currentTimeMillis()
                    if (now - last > 200) { last = now; onProgress(out.size) }
                }
                jr.endArray()
                return out
            }
        }
    }

    private fun readSeries(jr: JsonReader): SeriesItem? {
        var id: Int? = null; var name = ""; var cover: String? = null; var rating: Double? = null
        var modified = 0L; var cat = ""; var tmdb: Int? = null; var release: String? = null
        jr.beginObject()
        while (jr.hasNext()) {
            when (jr.nextName()) {
                "series_id" -> id = jr.str()?.toIntOrNull()
                "name" -> name = jr.str().orEmpty()
                "cover" -> cover = jr.str()?.takeIf { it.startsWith("http") }
                "rating" -> rating = jr.str()?.toDoubleOrNull()?.takeIf { it > 0 }
                "last_modified" -> modified = jr.str()?.toLongOrNull() ?: 0L
                "category_id" -> cat = jr.str().orEmpty()
                "tmdb", "tmdb_id" -> tmdb = jr.str()?.toIntOrNull()?.takeIf { it > 0 } ?: tmdb
                "releaseDate", "release_date", "releasedate" -> release = jr.str()?.takeIf { it.length >= 4 } ?: release
                else -> jr.skipValue()
            }
        }
        jr.endObject()
        if (id == null || name.isBlank()) return null
        // Het jaar uit de naam; staat het er niet in, dan het jaar van de eerste uitzending.
        val year = TitleMatch.year(name) ?: release?.take(4)?.takeIf { it.toIntOrNull() in 1920..2049 }
        return SeriesItem(id, name, TitleMatch.cleanName(name), year, cover, rating, modified, cat, tmdb)
    }

    private fun JsonReader.str(): String? = when (peek()) {
        JsonToken.NULL -> { nextNull(); null }
        JsonToken.BOOLEAN -> nextBoolean().toString()
        JsonToken.BEGIN_ARRAY, JsonToken.BEGIN_OBJECT -> { skipValue(); null }
        else -> nextString()
    }

    // --- Opslag op het kastje ---

    private fun writeCache(file: File, idx: SeriesIndex) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        JsonWriter(OutputStreamWriter(tmp.outputStream(), Charsets.UTF_8)).use { w ->
            w.beginObject()
            w.name("v").value(TitleMatch.VERSION.toLong())
            w.name("c").beginArray()
            idx.categories.forEach { w.beginArray().value(it.id).value(it.name).endArray() }
            w.endArray()
            w.name("i").beginArray()
            idx.items.forEach {
                w.beginArray().value(it.id.toLong()).value(it.rawName).value(it.poster).value(it.rating)
                    .value(it.modified).value(it.categoryId).value(it.tmdbId?.toLong())
                    .value(it.title).value(it.year).value(it.key).endArray()
            }
            w.endArray()
            w.endObject()
        }
        tmp.renameTo(file)
    }

    private fun readCache(file: File): SeriesIndex {
        val cats = ArrayList<VodCategory>()
        val items = ArrayList<SeriesItem>(4096)
        var version = 0
        JsonReader(InputStreamReader(file.inputStream().buffered(64 * 1024), Charsets.UTF_8)).use { r ->
            r.beginObject()
            while (r.hasNext()) when (r.nextName()) {
                "v" -> version = r.nextInt()
                "c" -> { r.beginArray(); while (r.hasNext()) { r.beginArray(); cats += VodCategory(r.nextString(), r.nextString()); r.endArray() }; r.endArray() }
                "i" -> {
                    r.beginArray()
                    while (r.hasNext()) {
                        r.beginArray()
                        val id = r.nextInt(); val raw = r.nextString(); val cover = r.str()
                        val rating = r.str()?.toDoubleOrNull(); val modified = r.nextLong()
                        val cat = r.nextString(); val tmdb = r.str()?.toIntOrNull()
                        val title = r.nextString(); val year = r.str(); val key = r.nextString()
                        r.endArray()
                        items += if (version == TitleMatch.VERSION) SeriesItem(id, raw, title, year, cover, rating, modified, cat, tmdb, key)
                        else SeriesItem(id, raw, TitleMatch.cleanName(raw), TitleMatch.year(raw) ?: year, cover, rating, modified, cat, tmdb)
                    }
                    r.endArray()
                }
                else -> r.skipValue()
            }
            r.endObject()
        }
        return SeriesIndex(items, cats)
    }

    /**
     * Eenmalige controle (via adb): wat stuurt de aanbieder mee over series en afleveringen?
     * Logt alleen veldnamen, hoe vaak ze gevuld zijn en korte voorbeelden van onschuldige velden.
     * Nooit links, inlog of bronadressen.
     */
    suspend fun probe() = withContext(Dispatchers.IO) {
        val c = Bios.config ?: return@withContext
        fun log(m: String) = Log.i(PROBE, m)
        fun sampleable(k: String) = listOf("genre", "plot", "cast", "year", "release", "tmdb", "rating", "episode", "season", "title", "duration", "name", "num", "extension", "modified", "added")
            .any { k.lowercase().contains(it) } && listOf("url", "source", "link", "stream", "icon", "pass", "user", "cover", "image", "backdrop", "trailer").none { k.lowercase().contains(it) }
        runCatching {
            val t0 = System.currentTimeMillis()
            val filled = HashMap<String, Int>(); val samples = HashMap<String, LinkedHashSet<String>>()
            var total = 0; var firstId: Int? = null
            http.newCall(Request.Builder().url(api(c).addQueryParameter("action", "get_series").build()).build()).execute().use { r ->
                JsonReader(InputStreamReader(r.body!!.byteStream(), Charsets.UTF_8)).use { jr ->
                    if (jr.peek() != JsonToken.BEGIN_ARRAY) { log("get_series geeft geen lijst: geen series"); return@use }
                    jr.beginArray()
                    while (jr.hasNext()) {
                        total++
                        jr.beginObject()
                        while (jr.hasNext()) {
                            val k = jr.nextName()
                            val v = if (jr.peek() == JsonToken.BEGIN_ARRAY) { var n = 0; jr.beginArray(); while (jr.hasNext()) { jr.skipValue(); n++ }; jr.endArray(); if (n > 0) "[$n]" else null } else jr.str()
                            if (!v.isNullOrBlank() && v != "null" && v != "0") {
                                filled[k] = (filled[k] ?: 0) + 1
                                if (sampleable(k)) samples.getOrPut(k) { LinkedHashSet() }.let { if (it.size < 3) it += v.take(40) }
                                if (k == "series_id" && (total == 1 || total == 50)) firstId = v.toIntOrNull()
                            }
                        }
                        jr.endObject()
                    }
                    jr.endArray()
                }
            }
            log("Serielijst: $total series in ${System.currentTimeMillis() - t0} ms")
            filled.entries.sortedByDescending { it.value }.forEach { (k, n) ->
                log("veld $k: gevuld bij $n van $total" + (samples[k]?.joinToString(" | ")?.let { "  bv: $it" } ?: ""))
            }
            val id = firstId ?: return@runCatching
            val t1 = System.currentTimeMillis()
            val o = getJson(api(c).addQueryParameter("action", "get_series_info").addQueryParameter("series_id", id.toString()).build())
            log("get_series_info in ${System.currentTimeMillis() - t1} ms: sleutels=${o.keys().asSequence().toList()} info-velden=${o.optJSONObject("info")?.keys()?.asSequence()?.toList()}")
            val eps = o.opt("episodes")
            log("episodes is een ${eps?.javaClass?.simpleName}; seizoenen=${(eps as? JSONObject)?.keys()?.asSequence()?.toList() ?: (eps as? JSONArray)?.length()}")
            val firstList = (eps as? JSONObject)?.let { e -> e.keys().asSequence().firstOrNull()?.let { e.optJSONArray(it) } } ?: (eps as? JSONArray)?.optJSONArray(0)
            firstList?.optJSONObject(0)?.let { e ->
                val keys = e.keys().asSequence().toList()
                val ei = e.optJSONObject("info")
                log("aflevering: velden=$keys titel='${e.optString("title").take(50)}' nr=${e.optString("episode_num")} seizoen=${e.optString("season")} ext=${e.optString("container_extension")} added=${e.optString("added")}")
                log("aflevering.info: velden=${ei?.keys()?.asSequence()?.toList()} duur_s=${ei?.optString("duration_secs")} duur='${ei?.optString("duration")}' plaatje=${!ei?.optString("movie_image").isNullOrBlank()} plot=${(ei?.optString("plot") ?: "").length} tekens")
                log("afleveringen in dit seizoen: ${firstList.length()}")
            }
        }.onFailure { log("Controle mislukt: ${it.javaClass.simpleName}: ${it.message?.take(80)?.replace(c.pass, "***")?.replace(c.user, "***")}") }
        log("Series-controle klaar")
    }
    private const val PROBE = "BiosProbe"
}
