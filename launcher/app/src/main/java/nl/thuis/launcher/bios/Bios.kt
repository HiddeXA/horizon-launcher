package nl.thuis.launcher.bios

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.thuis.launcher.data.Secrets
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit

/** Je Xtream-gegevens (uit local.properties, ingevuld met 6-api-sleutels). */
data class XtreamConfig(val server: String, val user: String, val pass: String) {
    val base: String = server.trimEnd('/').let { if (it.startsWith("http")) it else "http://$it" }
}

@Immutable
data class VodInfo(
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val durationSecs: Int?,
    val releaseDate: String?,
    val backdrop: String?,
    val trailerKey: String?,
    val tmdbId: Int?,
)

sealed interface CatalogState {
    data object NotConfigured : CatalogState
    /** [progress] 0..1, of null als de aanbieder niet zegt hoe groot de lijst is. */
    data class Loading(val phase: String = "Verbinden met je aanbieder…", val progress: Float? = null, val count: Int = 0) : CatalogState
    data class Ready(val index: VodIndex) : CatalogState
    data class Error(val message: String) : CatalogState
}

/**
 * Eén gedeelde toegang tot je filmaanbod, voor zowel de launcher als de Bios-app (zelfde proces).
 * De catalogus wordt op het kastje bewaard en hooguit elke 12 uur ververst.
 * Wachtwoord en stream-adressen worden nooit gelogd.
 */
object Bios {
    private const val TAG = "Bios"
    private const val CACHE_MS = 12 * 60 * 60 * 1000L
    /** v2: schone titel, jaar en zoeksleutel staan erin, zodat openen geen tekstbewerking per film kost. */
    private const val CACHE_FILE = "bios-catalog-v2.json"

    /** Actuele gegevens uit de instellingen (of uit local.properties als je niets hebt ingesteld). */
    val config: XtreamConfig?
        get() {
            val server = Secrets.get(Secrets.Key.XSERVER)
            val user = Secrets.get(Secrets.Key.XUSER)
            if (server.isBlank() || user.isBlank()) return null
            return XtreamConfig(server, user, Secrets.get(Secrets.Key.XPASS))
        }

    /** Andere aanbieder of inlog: alles van de vorige vergeten en opnieuw beginnen. */
    fun reset(context: Context) {
        loadedAt = 0L
        infoCache.evictAll()
        File(context.filesDir, "bios-catalog.json").delete()
        File(context.filesDir, CACHE_FILE).delete()
        _state.value = if (config == null) CatalogState.NotConfigured else CatalogState.Loading()
    }

    private val _state = MutableStateFlow<CatalogState>(if (config == null) CatalogState.NotConfigured else CatalogState.Loading())
    val state: StateFlow<CatalogState> = _state

    val index: VodIndex? get() = (state.value as? CatalogState.Ready)?.index

    @Volatile private var tasteStore: Taste? = null
    private val bg = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private val _tasteVersion = MutableStateFlow(0)
    /** Verandert als je kijkprofiel verandert (dan wordt het startscherm opnieuw gesorteerd). */
    val tasteVersion: StateFlow<Int> = _tasteVersion

    fun taste(context: Context): Taste =
        tasteStore ?: synchronized(this) { tasteStore ?: Taste(context.applicationContext).also { tasteStore = it } }

    /** Kijkgedrag vastleggen; genres worden zo nodig bij TMDB opgezocht (op de achtergrond). */
    fun recordTaste(context: Context, item: VodItem, weight: Double, tmdbId: Int? = item.tmdbId) {
        if (weight <= 0) return
        val app = context.applicationContext
        bg.launch {
            val genres = BiosGenres.genresFor(item, tmdbId ?: infoCache.get(item.id)?.tmdbId)
            taste(app).record(item, genres, weight)
            _tasteVersion.value++
            Log.i(TAG, "Kijkprofiel: ${item.title} +${"%.1f".format(weight)} (${genres.size} genres)")
        }
    }

    @Volatile private var progressStore: ProgressStore? = null
    @Volatile private var myListStore: MyList? = null

    /** Gedeelde voortgang ("Verder kijken") en "Mijn lijst", voor launcher, Bios en speler. */
    fun progress(context: Context): ProgressStore =
        progressStore ?: synchronized(this) { progressStore ?: ProgressStore(context.applicationContext).also { progressStore = it } }

    fun myList(context: Context): MyList =
        myListStore ?: synchronized(this) { myListStore ?: MyList(context.applicationContext).also { myListStore = it } }

    private val mutex = Mutex()
    private var loadedAt = 0L
    private val infoCache = LruCache<Int, VodInfo>(64)

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Laadt de catalogus: eerst uit de opslag op het kastje, en ververst via internet als hij oud is. */
    suspend fun load(context: Context, force: Boolean = false) {
        val cfg = config ?: return
        mutex.withLock {
            val fresh = System.currentTimeMillis() - loadedAt < CACHE_MS
            if (!force && fresh && index != null) return

            val file = File(context.filesDir, CACHE_FILE)
            File(context.filesDir, "bios-catalog.json").delete() // oud formaat (traag in te lezen)
            if (index == null && file.exists()) {
                val t0 = System.currentTimeMillis()
                runCatching {
                    withContext(Dispatchers.IO) {
                        readCache(file) { f, n -> _state.value = CatalogState.Loading("Je filmlijst openen…", f, n) }
                    }
                }
                    .onSuccess {
                        _state.value = CatalogState.Ready(it); loadedAt = file.lastModified()
                        Log.i(TAG, "Bios klaar uit opslag: ${it.items.size} films in ${System.currentTimeMillis() - t0} ms")
                    }
                    .onFailure { Log.w(TAG, "Cache onleesbaar: ${it.javaClass.simpleName}") }
                if (!force && System.currentTimeMillis() - file.lastModified() < CACHE_MS) return
            }
            if (index == null) _state.value = CatalogState.Loading()

            try {
                // Voortgang alleen tonen als er nog niets in beeld is (eerste keer of na nieuwe inlog).
                val show = index == null
                fun report(phase: String, p: Float?, n: Int = 0) { if (show) _state.value = CatalogState.Loading(phase, p, n) }
                val idx = withContext(Dispatchers.IO) {
                    report("Inloggen bij je aanbieder…", 0.02f)
                    val cats = fetchCategories(cfg)
                    report("Categorieën ophalen…", 0.06f)
                    val items = fetchStreams(cfg) { fraction, n ->
                        report("Films laden…", fraction?.let { 0.08f + it * 0.84f }, n)
                    }
                    report("Zoekindex maken…", 0.94f, items.size)
                    VodIndex(items, cats).also {
                        report("Opslaan op het kastje…", 0.97f, items.size)
                        writeCache(file, it)
                    }
                }
                loadedAt = System.currentTimeMillis()
                _state.value = CatalogState.Ready(idx)
                Log.i(TAG, "Bios klaar via aanbieder: ${idx.items.size} films, ${idx.categories.size} categorieën")
            } catch (e: Exception) {
                Log.w(TAG, "Catalogus laden mislukt: ${e.javaClass.simpleName}")
                if (index == null) _state.value = CatalogState.Error(friendly(e))
            }
        }
    }

    fun streamUrl(item: VodItem): String {
        val c = config ?: error("Bios niet ingesteld")
        return "${c.base}/movie/${c.user}/${c.pass}/${item.id}.${item.ext.ifBlank { "mp4" }}"
    }

    suspend fun info(item: VodItem): VodInfo? {
        infoCache.get(item.id)?.let { return it }
        val cfg = config ?: return null
        return runCatching {
            withContext(Dispatchers.IO) {
                val url = api(cfg).addQueryParameter("action", "get_vod_info").addQueryParameter("vod_id", item.id.toString()).build()
                val o = getJson(url).optJSONObject("info") ?: JSONObject()
                fun str(k: String) = o.optString(k).takeIf { it.isNotBlank() && it != "null" && it != "N/A" }
                val backdrop = when (val b = o.opt("backdrop_path")) {
                    is org.json.JSONArray -> if (b.length() > 0) b.optString(0) else null
                    is String -> b
                    else -> null
                }?.takeIf { it.startsWith("http") }
                VodInfo(
                    plot = str("plot") ?: str("description"),
                    cast = str("cast") ?: str("actors"),
                    director = str("director"),
                    genre = str("genre"),
                    durationSecs = o.optInt("duration_secs", 0).takeIf { it > 0 },
                    releaseDate = str("releasedate") ?: str("release_date"),
                    backdrop = backdrop,
                    trailerKey = str("youtube_trailer")?.substringAfterLast("v=")?.substringAfterLast('/'),
                    tmdbId = (str("tmdb_id") ?: str("tmdb"))?.toIntOrNull(),
                )
            }
        }.getOrNull()?.also { infoCache.put(item.id, it) }
    }

    /** Test de inlog (gebruikt door het instelscript via de app is niet nodig; hier voor de foutmelding). */
    private fun friendly(e: Exception): String = when (e) {
        is AuthException -> "Inloggen bij je aanbieder mislukt. Controleer gebruikersnaam en wachtwoord bij Instellingen."
        is IOException -> "Je aanbieder is niet bereikbaar. Controleer het server-adres of probeer het later opnieuw."
        else -> "Je filmaanbod kon niet geladen worden."
    }

    private class AuthException : IOException("auth")

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
        // Eerst controleren of de inlog klopt.
        val login = getJson(api(c).build())
        if (login.optJSONObject("user_info")?.optInt("auth", 0) != 1) throw AuthException()

        val url = api(c).addQueryParameter("action", "get_vod_categories").build()
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val arr = org.json.JSONArray(r.body!!.string())
            return (0 until arr.length()).map { arr.getJSONObject(it) }.map {
                VodCategory(it.optString("category_id"), it.optString("category_name").trim())
            }
        }
    }

    /** Leest get_vod_streams als stroom (kan tienduizenden films zijn) zonder alles in het geheugen te laden. */
    private fun fetchStreams(c: XtreamConfig, onProgress: (Float?, Int) -> Unit): List<VodItem> {
        val url = api(c).addQueryParameter("action", "get_vod_streams").build()
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val body = r.body!!
            val total = body.contentLength() // -1 als de aanbieder het niet meegeeft
            val counting = CountingStream(body.byteStream())
            JsonReader(InputStreamReader(counting, Charsets.UTF_8)).use { jr ->
                val out = ArrayList<VodItem>(8192)
                var lastReport = 0L
                jr.beginArray()
                while (jr.hasNext()) {
                    readStream(jr)?.let(out::add)
                    val now = System.currentTimeMillis()
                    if (now - lastReport > 200) {
                        lastReport = now
                        onProgress(if (total > 0) (counting.count.toFloat() / total).coerceIn(0f, 1f) else null, out.size)
                    }
                }
                jr.endArray()
                onProgress(1f, out.size)
                return out
            }
        }
    }

    /**
     * Eenmalige controle: welke velden stuurt de aanbieder mee in de filmlijst?
     * Logt alleen veldnamen, hoe vaak ze gevuld zijn en voorbeelden van genre-achtige velden.
     * Nooit links, inlog of bronadressen.
     */
    suspend fun probeFields() = withContext(Dispatchers.IO) {
        val c = config ?: return@withContext
        val t0 = System.currentTimeMillis()
        runCatching {
            val url = api(c).addQueryParameter("action", "get_vod_streams").build()
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                val filled = HashMap<String, Int>()
                val samples = HashMap<String, LinkedHashSet<String>>()
                var total = 0
                fun sampleable(k: String) = listOf("genre", "categor", "plot", "cast", "year", "release", "tmdb", "imdb", "director", "rating").any { k.lowercase().contains(it) } &&
                    listOf("url", "source", "link", "stream", "icon", "pass", "user").none { k.lowercase().contains(it) }
                JsonReader(InputStreamReader(r.body!!.byteStream(), Charsets.UTF_8)).use { jr ->
                    jr.beginArray()
                    while (jr.hasNext()) {
                        total++
                        jr.beginObject()
                        while (jr.hasNext()) {
                            val k = jr.nextName()
                            val v = when (jr.peek()) {
                                JsonToken.BEGIN_ARRAY -> { val sb = StringBuilder("["); jr.beginArray(); while (jr.hasNext()) { sb.append(jr.str() ?: "").append(','); }; jr.endArray(); sb.append(']').toString() }
                                else -> jr.str()
                            }
                            if (!v.isNullOrBlank() && v != "[]" && v != "null" && v != "0") {
                                filled[k] = (filled[k] ?: 0) + 1
                                if (sampleable(k)) samples.getOrPut(k) { LinkedHashSet() }.let { if (it.size < 4) it += v.take(60) }
                            }
                        }
                        jr.endObject()
                    }
                    jr.endArray()
                }
                Log.i(PROBE, "Filmlijst: $total films in ${System.currentTimeMillis() - t0} ms")
                filled.entries.sortedByDescending { it.value }.forEach { (k, n) ->
                    val ex = samples[k]?.joinToString(" | ")?.let { "  voorbeeld: $it" } ?: ""
                    Log.i(PROBE, "veld $k: gevuld bij $n van $total$ex")
                }
                Log.i(PROBE, "Klaar")
            }
        }.onFailure { Log.w(PROBE, "Controle mislukt: ${it.javaClass.simpleName}"); Log.i(PROBE, "Klaar") }
    }
    /** Eenmalige controle van het live-tv-aanbod (voor het ontwerp van Horizon TV). Logt geen inlog of links. */
    suspend fun probeLive() = withContext(Dispatchers.IO) {
        val c = config ?: return@withContext
        fun log(m: String) = Log.i(PROBE, m)
        runCatching {
            val login = getJson(api(c).build())
            val ui = login.optJSONObject("user_info"); val si = login.optJSONObject("server_info")
            log("formaten=${ui?.optJSONArray("allowed_output_formats")} max_verbindingen=${ui?.optString("max_connections")} tijdzone=${si?.optString("timezone")} https=${si?.optString("https_port")}")

            val cats = org.json.JSONArray(http.newCall(Request.Builder().url(api(c).addQueryParameter("action", "get_live_categories").build()).build()).execute().use { it.body!!.string() })
            log("categorieen=${cats.length()}: " + (0 until minOf(cats.length(), 40)).joinToString(" | ") { cats.getJSONObject(it).optString("category_name") })

            val t0 = System.currentTimeMillis()
            val filled = HashMap<String, Int>(); val samples = HashMap<String, LinkedHashSet<String>>()
            var total = 0; var archive = 0; var withEpg = 0
            val nlTest = ArrayList<Pair<Int, String>>()
            val safe = setOf("num", "name", "stream_type", "epg_channel_id", "tv_archive", "tv_archive_duration", "category_id", "custom_sid", "added", "is_adult", "thumbnail")
            http.newCall(Request.Builder().url(api(c).addQueryParameter("action", "get_live_streams").build()).build()).execute().use { r ->
                JsonReader(InputStreamReader(r.body!!.byteStream(), Charsets.UTF_8)).use { jr ->
                    jr.beginArray()
                    while (jr.hasNext()) {
                        total++
                        var id = 0; var name = ""; var epg: String? = null; var arch = false
                        jr.beginObject()
                        while (jr.hasNext()) {
                            val k = jr.nextName()
                            val v = if (jr.peek() == JsonToken.BEGIN_ARRAY) { jr.skipValue(); "[..]" } else jr.str()
                            if (!v.isNullOrBlank() && v != "null" && v != "0") {
                                filled[k] = (filled[k] ?: 0) + 1
                                if (k in safe) samples.getOrPut(k) { LinkedHashSet() }.let { if (it.size < 5) it += v.take(50) }
                            }
                            when (k) { "stream_id" -> id = v?.toIntOrNull() ?: 0; "name" -> name = v.orEmpty(); "epg_channel_id" -> epg = v?.takeIf { it.isNotBlank() }; "tv_archive" -> arch = v == "1" }
                        }
                        jr.endObject()
                        if (arch) archive++
                        if (epg != null) withEpg++
                        if (epg != null && nlTest.size < 3 && name.contains("NL", ignoreCase = false)) nlTest += id to name
                    }
                    jr.endArray()
                }
            }
            log("zenders=$total in ${System.currentTimeMillis() - t0} ms, met epg_channel_id=$withEpg, met terugkijken=$archive")
            filled.entries.sortedByDescending { it.value }.forEach { (k, n) -> log("veld $k: $n van $total" + (samples[k]?.let { "  bv: " + it.joinToString(" | ") } ?: "")) }

            nlTest.forEach { (id, name) ->
                val o = getJson(api(c).addQueryParameter("action", "get_short_epg").addQueryParameter("stream_id", id.toString()).addQueryParameter("limit", "3").build())
                val list = o.optJSONArray("epg_listings")
                log("short_epg '$name': ${list?.length() ?: 0} items")
                list?.optJSONObject(0)?.let { e ->
                    val keys = e.keys().asSequence().toList()
                    val title = e.optString("title")
                    val dec = runCatching { String(android.util.Base64.decode(title, android.util.Base64.DEFAULT)) }.getOrNull()
                    log("  velden=$keys titel='${title.take(40)}' base64='${dec?.take(40)}' start='${e.optString("start")}' eind='${e.optString("end")}' ts=${e.optString("start_timestamp")}")
                }
            }
            nlTest.firstOrNull()?.let { (id, name) ->
                val o = getJson(api(c).addQueryParameter("action", "get_simple_data_table").addQueryParameter("stream_id", id.toString()).build())
                val list = o.optJSONArray("epg_listings")
                var arch = 0; var first = Long.MAX_VALUE; var last = 0L
                for (i in 0 until (list?.length() ?: 0)) {
                    val e = list!!.getJSONObject(i)
                    if (e.optInt("has_archive") == 1) arch++
                    e.optString("start_timestamp").toLongOrNull()?.let { first = minOf(first, it); last = maxOf(last, it) }
                }
                log("simple_data_table '$name': ${list?.length() ?: 0} items, met archief=$arch, bereik ${(last - first) / 3600} uur, velden=${list?.optJSONObject(0)?.keys()?.asSequence()?.toList()}")
            }

            // XMLTV: hoe groot, hoeveel zenders/programma's, welk tijdbereik?
            val t1 = System.currentTimeMillis()
            val xurl = "${c.base}/xmltv.php".toHttpUrl().newBuilder().addQueryParameter("username", c.user).addQueryParameter("password", c.pass).build()
            http.newBuilder().readTimeout(120, TimeUnit.SECONDS).build().newCall(Request.Builder().url(xurl).build()).execute().use { r ->
                log("xmltv http=${r.code} type=${r.header("Content-Type")} lengte=${r.header("Content-Length")} encoding=${r.header("Content-Encoding")}")
                val counting = CountingStream(r.body!!.byteStream())
                val xp = android.util.Xml.newPullParser()
                xp.setInput(InputStreamReader(counting, Charsets.UTF_8))
                var ch = 0; var pr = 0; var minS = "z"; var maxS = ""; val chIds = LinkedHashSet<String>()
                var ev = xp.eventType
                while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT && System.currentTimeMillis() - t1 < 150_000) {
                    if (ev == org.xmlpull.v1.XmlPullParser.START_TAG) when (xp.name) {
                        "channel" -> { ch++; if (chIds.size < 4) chIds += xp.getAttributeValue(null, "id") ?: "" }
                        "programme" -> { pr++; val st = xp.getAttributeValue(null, "start") ?: ""; if (st < minS) minS = st; if (st > maxS) maxS = st }
                    }
                    ev = xp.next()
                }
                log("xmltv: ${counting.count / 1024} kB in ${System.currentTimeMillis() - t1} ms, zenders=$ch, programmas=$pr, van $minS tot $maxS, bv ids=$chIds, compleet=${ev == org.xmlpull.v1.XmlPullParser.END_DOCUMENT}")
            }
        }.onFailure { log("Controle mislukt: ${it.javaClass.simpleName}: ${it.message?.take(80)?.replace(c.pass, "***")?.replace(c.user, "***")}") }
        log("Klaar")
    }
    private const val PROBE = "BiosProbe"

    /** Telt hoeveel bytes er al binnen zijn, voor de voortgangsbalk. */
    private class CountingStream(private val inner: java.io.InputStream) : java.io.FilterInputStream(inner) {
        @Volatile var count = 0L
        override fun read(): Int = super.read().also { if (it >= 0) count++ }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count += it }
    }

    private fun readStream(jr: JsonReader): VodItem? {
        var id: Int? = null; var name = ""; var icon: String? = null; var rating: Double? = null
        var added = 0L; var cat = ""; var ext = "mp4"; var tmdb: Int? = null
        jr.beginObject()
        while (jr.hasNext()) {
            when (jr.nextName()) {
                "stream_id" -> id = jr.str()?.toIntOrNull()
                "name" -> name = jr.str().orEmpty()
                "stream_icon" -> icon = jr.str()?.takeIf { it.startsWith("http") }
                "rating" -> rating = jr.str()?.toDoubleOrNull()?.takeIf { it > 0 }
                "added" -> added = jr.str()?.toLongOrNull() ?: 0L
                "category_id" -> cat = jr.str().orEmpty()
                "container_extension" -> ext = jr.str()?.takeIf { it.isNotBlank() } ?: "mp4"
                "tmdb", "tmdb_id" -> tmdb = jr.str()?.toIntOrNull()?.takeIf { it > 0 }
                else -> jr.skipValue()
            }
        }
        jr.endObject()
        if (id == null || name.isBlank()) return null
        return VodItem(id, name, TitleMatch.cleanName(name), TitleMatch.year(name), icon, rating, added, cat, ext, tmdb)
    }

    private fun JsonReader.str(): String? = when (peek()) {
        JsonToken.NULL -> { nextNull(); null }
        JsonToken.BOOLEAN -> nextBoolean().toString()
        JsonToken.BEGIN_ARRAY, JsonToken.BEGIN_OBJECT -> { skipValue(); null }
        else -> nextString()
    }

    // --- Opslag op het kastje: compact formaat, snel in te lezen bij het opstarten ---

    private fun writeCache(file: File, idx: VodIndex) {
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
                    .value(it.added).value(it.categoryId).value(it.ext).value(it.tmdbId?.toLong())
                    .value(it.title).value(it.year).value(it.key).endArray()
            }
            w.endArray()
            w.endObject()
        }
        tmp.renameTo(file)
    }

    private fun readCache(file: File, onProgress: (Float, Int) -> Unit): VodIndex {
        val cats = ArrayList<VodCategory>()
        val items = ArrayList<VodItem>(8192)
        val total = file.length().coerceAtLeast(1)
        val counting = CountingStream(file.inputStream().buffered(64 * 1024))
        var last = 0L
        var version = 0
        JsonReader(InputStreamReader(counting, Charsets.UTF_8)).use { r ->
            r.beginObject()
            while (r.hasNext()) when (r.nextName()) {
                "v" -> version = r.nextInt()
                "c" -> { r.beginArray(); while (r.hasNext()) { r.beginArray(); cats += VodCategory(r.nextString(), r.nextString()); r.endArray() }; r.endArray() }
                "i" -> {
                    r.beginArray()
                    while (r.hasNext()) {
                        r.beginArray()
                        val id = r.nextInt(); val raw = r.nextString(); val icon = r.str()
                        val rating = r.str()?.toDoubleOrNull(); val added = r.nextLong()
                        val cat = r.nextString(); val ext = r.nextString(); val tmdb = r.str()?.toIntOrNull()
                        val title = r.nextString(); val year = r.str(); val key = r.nextString()
                        r.endArray()
                        items += if (version == TitleMatch.VERSION) VodItem(id, raw, title, year, icon, rating, added, cat, ext, tmdb, key)
                        else TitleMatch.cleanName(raw).let { t -> VodItem(id, raw, t, TitleMatch.year(raw), icon, rating, added, cat, ext, tmdb) }
                        val now = System.currentTimeMillis()
                        if (now - last > 150) { last = now; onProgress((counting.count.toFloat() / total).coerceIn(0f, 1f) * 0.95f, items.size) }
                    }
                    r.endArray()
                }
                else -> r.skipValue()
            }
            r.endObject()
        }
        onProgress(0.97f, items.size)
        val idx = VodIndex(items, cats)
        // Titels opgeschoond met een oudere versie: opnieuw opgeschoond, dus meteen bijwerken.
        if (version != TitleMatch.VERSION) {
            runCatching { writeCache(file, idx) }
            Log.i(TAG, "Filmtitels opnieuw opgeschoond (versie $version -> ${TitleMatch.VERSION})")
        }
        return idx
    }
}

/** Waar je gebleven was, per film. */
class ProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences("bios-progress", Context.MODE_PRIVATE)

    data class Entry(val positionMs: Long, val durationMs: Long, val updated: Long) {
        val fraction get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    }

    /** Zichtbaar in Compose: verandert direct mee. */
    val entries = mutableStateMapOf<Int, Entry>().apply {
        prefs.all.forEach { (k, v) ->
            val p = (v as? String)?.split(',') ?: return@forEach
            if (p.size == 3) put(k.toInt(), Entry(p[0].toLong(), p[1].toLong(), p[2].toLong()))
        }
    }

    fun save(id: Int, pos: Long, dur: Long) {
        // Bijna klaar (>95%) = gezien: dan uit "Verder kijken" halen.
        if (dur > 0 && pos > dur * 0.95) { remove(id); return }
        if (pos < 30_000) return
        val e = Entry(pos, dur, System.currentTimeMillis())
        entries[id] = e
        prefs.edit().putString(id.toString(), "${e.positionMs},${e.durationMs},${e.updated}").apply()
    }

    fun remove(id: Int) {
        entries.remove(id)
        prefs.edit().remove(id.toString()).apply()
    }

    fun resumeAt(id: Int): Long = entries[id]?.positionMs ?: 0L
}

/** "Mijn lijst". Nieuwste bovenaan. */
class MyList(context: Context) {
    private val prefs = context.getSharedPreferences("bios-mylist", Context.MODE_PRIVATE)
    val ids = mutableStateListOf<Int>().apply {
        addAll(prefs.getString("ids", "")!!.split(',').mapNotNull { it.toIntOrNull() })
    }

    fun contains(id: Int) = id in ids
    fun toggle(id: Int) {
        if (!ids.remove(id)) ids.add(0, id)
        prefs.edit().putString("ids", ids.joinToString(",")).apply()
    }
}
