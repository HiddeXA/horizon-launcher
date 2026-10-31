package nl.thuis.launcher.tv

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nl.thuis.launcher.bios.Bios
import nl.thuis.launcher.bios.XtreamConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Alle zenders, gegroepeerd en snel op te zoeken. */
class LiveLineup(val categories: List<LiveCategory>, val channels: List<LiveChannel>) {
    val byId: Map<Int, LiveChannel> = channels.associateBy { it.id }
    val byCategory: Map<String, List<LiveChannel>> = channels.groupBy { it.categoryId }
    val byNum: Map<Int, LiveChannel> = channels.associateBy { it.num }
    /** Alleen categorieën met zenders; Nederlandse eerst, daarna de volgorde van je aanbieder. */
    val visibleCategories: List<LiveCategory> = categories.filter { byCategory[it.id].orEmpty().isNotEmpty() }
        .sortedBy { if (it.region == "NL") 0 else if (it.region == "BE") 1 else 2 }
}

sealed interface LiveState {
    data object NotConfigured : LiveState
    data class Loading(val phase: String) : LiveState
    data class Ready(val lineup: LiveLineup) : LiveState
    data class Error(val message: String) : LiveState
}

/**
 * Live-tv uit je Xtream-aanbod: inloggen, de stream-adressen en de gedeelde verbinding.
 * De zenderlijsten zelf zitten in [LiveSource]: één voor Horizon TV ([tv], zonder 18+) en één voor
 * Roze Hoek ([roze], alleen 18+). Wachtwoord en stream-adressen worden nooit gelogd.
 */
object LiveRepo {
    val config: XtreamConfig? get() = Bios.config

    /** Horizon TV: alle zenders behalve volwassenen-zenders. */
    val tv = LiveSource(adultOnly = false, cacheFile = "live-lineup-v1.json")
    /** Roze Hoek: alleen de volwassenen-zenders. */
    val roze = LiveSource(adultOnly = true, cacheFile = "roze-lineup-v1.json")

    /** Na nieuwe inloggegevens: beide zenderlijsten opnieuw ophalen. */
    fun resetAll(context: Context) { tv.reset(context); roze.reset(context) }

    /** Tijdzone van de server (voor terugkijken); de aanbieder geeft hem bij het inloggen. */
    @Volatile var serverZone: ZoneId = ZoneId.systemDefault()

    internal val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // --- Stream-adressen ---

    /** Live: standaard MPEG-TS (snelste start); HLS als reserve. */
    fun liveUrl(ch: LiveChannel, hls: Boolean = false): String {
        val c = config ?: error("niet ingesteld")
        return "${c.base}/live/${c.user}/${c.pass}/${ch.id}.${if (hls) "m3u8" else "ts"}"
    }

    private val tsFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd:HH-mm")

    /** Terugkijken: /timeshift/user/pass/{minuten}/{start in servertijd}/{id}.ts */
    fun catchupUrl(ch: LiveChannel, p: Programme, hls: Boolean = false): String {
        val c = config ?: error("niet ingesteld")
        val minutes = ((p.end - p.start) / 60).coerceAtLeast(1)
        val start = tsFormat.format(Instant.ofEpochSecond(p.start).atZone(serverZone))
        return "${c.base}/timeshift/${c.user}/${c.pass}/$minutes/$start/${ch.id}.${if (hls) "m3u8" else "ts"}"
    }

    internal fun api(c: XtreamConfig): HttpUrl.Builder = "${c.base}/player_api.php".toHttpUrl().newBuilder()
        .addQueryParameter("username", c.user)
        .addQueryParameter("password", c.pass)

    internal class AuthException : IOException("auth")

    internal fun login(c: XtreamConfig) {
        http.newCall(Request.Builder().url(api(c).build()).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val o = JSONObject(r.body!!.string())
            if (o.optJSONObject("user_info")?.optInt("auth", 0) != 1) throw AuthException()
            o.optJSONObject("server_info")?.optString("timezone")?.takeIf { it.isNotBlank() }?.let { tz ->
                runCatching { serverZone = ZoneId.of(tz) }
            }
        }
    }

    /** Herkent volwassenen-zenders en -categorieën aan hun naam (naast de vlag is_adult van de aanbieder). */
    fun isAdultName(n: String) = n.contains("XXX", true) || n.contains("ADULT", true) || n.contains("18+")
}

/**
 * Eén zenderlijst. [adultOnly] = false laat volwassenen-zenders weg (Horizon TV),
 * true houdt alléén die over (Roze Hoek). Op het kastje bewaard en elke 12 uur ververst.
 */
class LiveSource(val adultOnly: Boolean, private val cacheFile: String) {
    private companion object { const val CACHE_MS = 12 * 60 * 60 * 1000L }

    private val tag = if (adultOnly) "RozeHoek" else "HorizonTV"

    // Let op: hier Bios.config en niet LiveRepo.config, want deze objecten worden gemaakt terwijl LiveRepo nog opstart.
    private val _state = MutableStateFlow<LiveState>(if (Bios.config == null) LiveState.NotConfigured else LiveState.Loading("Zenders laden…"))
    val state: StateFlow<LiveState> = _state
    val lineup: LiveLineup? get() = (state.value as? LiveState.Ready)?.lineup

    private val mutex = Mutex()
    private val http get() = LiveRepo.http

    fun reset(context: Context) {
        File(context.filesDir, cacheFile).delete()
        _state.value = if (Bios.config == null) LiveState.NotConfigured else LiveState.Loading("Zenders laden…")
    }

    suspend fun load(context: Context, force: Boolean = false) {
        val cfg = Bios.config ?: run { _state.value = LiveState.NotConfigured; return }
        mutex.withLock {
            val file = File(context.filesDir, cacheFile)
            if (lineup == null && file.exists()) {
                runCatching { withContext(Dispatchers.IO) { readCache(file) } }
                    .onSuccess { _state.value = LiveState.Ready(it); Log.i(tag, "Zenders uit opslag: ${it.channels.size}") }
                    .onFailure { Log.w(tag, "Zenderopslag onleesbaar: ${it.javaClass.simpleName}") }
                if (!force && lineup != null && System.currentTimeMillis() - file.lastModified() < CACHE_MS) {
                    // Wel even de tijdzone van de server ophalen (licht verzoek), op de achtergrond.
                    runCatching { withContext(Dispatchers.IO) { LiveRepo.login(cfg) } }
                    return
                }
            }
            try {
                val show = lineup == null
                val t0 = System.currentTimeMillis()
                val l = withContext(Dispatchers.IO) {
                    if (show) _state.value = LiveState.Loading("Inloggen bij je aanbieder…")
                    LiveRepo.login(cfg)
                    if (show) _state.value = LiveState.Loading("Zenders ophalen…")
                    val cats = fetchCategories(cfg)
                    val chans = fetchChannels(cfg, cats.associateBy { it.id })
                    LiveLineup(cats, chans).also { writeCache(file, it) }
                }
                _state.value = LiveState.Ready(l)
                Log.i(tag, "Zenders via aanbieder: ${l.channels.size} in ${System.currentTimeMillis() - t0} ms")
            } catch (e: Exception) {
                Log.w(tag, "Zenders laden mislukt: ${e.javaClass.simpleName}")
                if (lineup == null) _state.value = LiveState.Error(
                    if (e is LiveRepo.AuthException) "Inloggen bij je aanbieder mislukt. Controleer je gegevens bij Instellingen."
                    else "Je aanbieder is niet bereikbaar. Probeer het zo opnieuw."
                )
            }
        }
    }

    private fun fetchCategories(c: XtreamConfig): List<LiveCategory> {
        val url = LiveRepo.api(c).addQueryParameter("action", "get_live_categories").build()
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val arr = org.json.JSONArray(r.body!!.string())
            return (0 until arr.length()).map { arr.getJSONObject(it) }.map {
                val raw = it.optString("category_name")
                val (region, name) = ChannelNames.category(raw)
                LiveCategory(it.optString("category_id"), name.ifBlank { raw.trim() }, region, LiveRepo.isAdultName(raw))
            }
        }
    }

    private fun fetchChannels(c: XtreamConfig, cats: Map<String, LiveCategory>): List<LiveChannel> {
        val url = LiveRepo.api(c).addQueryParameter("action", "get_live_streams").build()
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            JsonReader(InputStreamReader(r.body!!.byteStream(), Charsets.UTF_8)).use { jr ->
                val out = ArrayList<LiveChannel>(16384)
                jr.beginArray()
                while (jr.hasNext()) {
                    var id = 0; var num = 0; var raw = ""; var logo: String? = null; var epg: String? = null
                    var cat = ""; var archive = 0; var archiveDays = 0; var type = ""; var adult = false
                    jr.beginObject()
                    while (jr.hasNext()) {
                        when (jr.nextName()) {
                            "stream_id" -> id = jr.str()?.toIntOrNull() ?: 0
                            "num" -> num = jr.str()?.toIntOrNull() ?: 0
                            "name" -> raw = jr.str().orEmpty()
                            "stream_icon" -> logo = jr.str()?.takeIf { it.startsWith("http") }
                            "epg_channel_id" -> epg = jr.str()?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
                            "category_id" -> cat = jr.str().orEmpty()
                            "tv_archive" -> archive = jr.str()?.toIntOrNull() ?: 0
                            "tv_archive_duration" -> archiveDays = jr.str()?.toIntOrNull() ?: 0
                            "stream_type" -> type = jr.str().orEmpty()
                            "is_adult" -> adult = jr.str() == "1"
                            else -> jr.skipValue()
                        }
                    }
                    jr.endObject()
                    if (id == 0) continue
                    val category = cats[cat]
                    // Horizon TV laat volwassenen-zenders weg; Roze Hoek houdt alleen die over.
                    val isAdult = adult || category?.adult == true || LiveRepo.isAdultName(raw)
                    if (isAdult != adultOnly) continue
                    val (region, name) = ChannelNames.split(raw)
                    out += LiveChannel(
                        id = id, num = num, name = ChannelNames.titleCase(name).ifBlank { raw.trim() }, region = region,
                        logo = logo, epgId = epg?.lowercase(), categoryId = cat,
                        archiveDays = if (archive == 1) archiveDays.coerceAtLeast(1) else 0,
                        radio = type == "radio_streams", adult = adultOnly,
                    )
                }
                jr.endArray()
                return out
            }
        }
    }

    private fun JsonReader.str(): String? = when (peek()) {
        JsonToken.NULL -> { nextNull(); null }
        JsonToken.BOOLEAN -> nextBoolean().toString()
        JsonToken.BEGIN_ARRAY, JsonToken.BEGIN_OBJECT -> { skipValue(); null }
        else -> nextString()
    }

    // --- Opslag op het kastje ---

    private fun writeCache(file: File, l: LiveLineup) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        JsonWriter(OutputStreamWriter(tmp.outputStream().buffered(), Charsets.UTF_8)).use { w ->
            w.beginObject()
            w.name("tz").value(LiveRepo.serverZone.id)
            w.name("c").beginArray()
            l.categories.forEach { w.beginArray().value(it.id).value(it.name).value(it.region).value(it.adult).endArray() }
            w.endArray()
            w.name("s").beginArray()
            l.channels.forEach {
                w.beginArray().value(it.id.toLong()).value(it.num.toLong()).value(it.name).value(it.region).value(it.logo)
                    .value(it.epgId).value(it.categoryId).value(it.archiveDays.toLong()).value(it.radio).endArray()
            }
            w.endArray()
            w.endObject()
        }
        tmp.renameTo(file)
    }

    private fun readCache(file: File): LiveLineup {
        val cats = ArrayList<LiveCategory>(); val chans = ArrayList<LiveChannel>(16384)
        JsonReader(InputStreamReader(file.inputStream().buffered(64 * 1024), Charsets.UTF_8)).use { r ->
            r.beginObject()
            while (r.hasNext()) when (r.nextName()) {
                "tz" -> r.str()?.let { tz -> runCatching { LiveRepo.serverZone = ZoneId.of(tz) } }
                "c" -> { r.beginArray(); while (r.hasNext()) { r.beginArray(); cats += LiveCategory(r.nextString(), r.nextString(), r.str(), r.nextBoolean()); r.endArray() }; r.endArray() }
                "s" -> {
                    r.beginArray()
                    while (r.hasNext()) {
                        r.beginArray()
                        chans += LiveChannel(
                            id = r.nextInt(), num = r.nextInt(), name = r.nextString(), region = r.str(), logo = r.str(),
                            epgId = r.str(), categoryId = r.nextString(), archiveDays = r.nextInt(), radio = r.nextBoolean(), adult = adultOnly,
                        )
                        r.endArray()
                    }
                    r.endArray()
                }
                else -> r.skipValue()
            }
            r.endObject()
        }
        return LiveLineup(cats, chans)
    }
}

/** Favorieten, laatst gekeken en de laatste zender: klein, dus in SharedPreferences. */
class LivePrefs(context: Context, name: String = "horizon-tv") {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    val favorites = mutableStateListOf<Int>().apply { addAll(ids("fav")) }
    val recent = mutableStateListOf<Int>().apply { addAll(ids("recent")) }

    var lastChannel: Int
        get() = prefs.getInt("last", 0)
        set(v) { prefs.edit().putInt("last", v).apply() }

    var lastCategory: String?
        get() = prefs.getString("lastCat", null)
        set(v) { prefs.edit().putString("lastCat", v).apply() }

    private fun ids(key: String) = prefs.getString(key, "")!!.split(',').mapNotNull { it.toIntOrNull() }

    fun toggleFavorite(id: Int) {
        if (!favorites.remove(id)) favorites.add(id)
        prefs.edit().putString("fav", favorites.joinToString(",")).apply()
    }

    fun watched(id: Int) {
        recent.remove(id); recent.add(0, id)
        while (recent.size > 20) recent.removeAt(recent.lastIndex)
        prefs.edit().putString("recent", recent.joinToString(",")).putInt("last", id).apply()
    }
}
