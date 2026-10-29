package nl.thuis.launcher.tv

import android.content.Context
import android.util.Base64
import android.util.Log
import android.util.LruCache
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/** Tijden uit XMLTV ("20261026173000 +0100") omzetten naar seconden sinds 1970. Pure Kotlin. */
object XmltvTime {
    private val cumDays = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)

    fun parse(s: String): Long? {
        if (s.length < 14) return null
        fun num(a: Int, b: Int): Int? { var v = 0; for (i in a until b) { val c = s[i]; if (c !in '0'..'9') return null; v = v * 10 + (c - '0') }; return v }
        val y = num(0, 4) ?: return null; val mo = num(4, 6) ?: return null; val d = num(6, 8) ?: return null
        val h = num(8, 10) ?: return null; val mi = num(10, 12) ?: return null; val se = num(12, 14) ?: return null
        if (mo !in 1..12) return null
        // Dagen sinds 1970 (proleptisch gregoriaans).
        val leap = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
        var days = (y - 1970) * 365L + ((y - 1969) / 4) - ((y - 1901) / 100) + ((y - 1601) / 400)
        days += cumDays[mo - 1] + (if (leap && mo > 2) 1 else 0) + (d - 1)
        var secs = days * 86400 + h * 3600 + mi * 60 + se
        // Tijdzone "+0100" / "-0500"
        val tz = s.substring(14).trim()
        if (tz.length >= 5 && (tz[0] == '+' || tz[0] == '-')) {
            val th = tz.substring(1, 3).toIntOrNull() ?: 0; val tm = tz.substring(3, 5).toIntOrNull() ?: 0
            val off = th * 3600 + tm * 60
            secs -= if (tz[0] == '+') off else -off
        }
        return secs
    }
}

/** Programma's van één zender, op begintijd gesorteerd. */
class ChannelGuide(val programmes: List<Programme>) {
    /** Index van het programma dat op [t] loopt (of het eerste daarna), of -1. */
    fun indexAt(t: Long): Int {
        var lo = 0; var hi = programmes.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val p = programmes[mid]
            when {
                t < p.start -> hi = mid - 1
                t >= p.end -> lo = mid + 1
                else -> return mid
            }
        }
        return if (lo < programmes.size) lo else -1
    }

    fun at(t: Long): Programme? = indexAt(t).takeIf { it >= 0 }?.let { programmes[it] }?.takeIf { it.isNow(t) }
    fun next(t: Long): Programme? {
        val i = indexAt(t); if (i < 0) return null
        val p = programmes[i]
        return if (p.isNow(t)) programmes.getOrNull(i + 1) else p
    }
    fun between(from: Long, to: Long): List<Programme> {
        var i = indexAt(from); if (i < 0) return emptyList()
        if (i > 0 && programmes[i - 1].end > from) i--
        val out = ArrayList<Programme>()
        while (i < programmes.size && programmes[i].start < to) { if (programmes[i].end > from) out += programmes[i]; i++ }
        return out
    }
}

/**
 * De tv-gids. Bron: XMLTV van je aanbieder (± 70 MB, alle zenders, gisteren t/m morgen).
 * Alleen titels en tijden van zenders in je lijst blijven in het geheugen; omschrijvingen
 * worden per zender opgehaald als je ze bekijkt. Bewaard op het kastje, ververst na 8 uur.
 */
object EpgStore {
    private const val TAG = "HorizonTV"
    private const val FILE = "live-epg-v1.bin"
    private const val REFRESH_MS = 8 * 60 * 60 * 1000L

    @Volatile private var guides: Map<String, ChannelGuide> = emptyMap()
    private val _version = MutableStateFlow(0)
    /** Verandert als er nieuwe gidsgegevens zijn (voor Compose). */
    val version: StateFlow<Int> = _version
    private val mutex = Mutex()

    fun guide(ch: LiveChannel?): ChannelGuide? = ch?.epgId?.let { guides[it] }

    suspend fun load(context: Context, lineup: LiveLineup) = mutex.withLock {
        val file = File(context.filesDir, FILE)
        if (guides.isEmpty() && file.exists()) {
            runCatching { withContext(Dispatchers.IO) { readBin(file) } }
                .onSuccess { guides = it; _version.value++; Log.i(TAG, "Gids uit opslag: ${it.size} zenders") }
                .onFailure { Log.w(TAG, "Gidsopslag onleesbaar: ${it.javaClass.simpleName}") }
        }
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < REFRESH_MS
        // Ook verversen als de gids bijna op is (minder dan 6 uur vooruit).
        val now = System.currentTimeMillis() / 1000
        val coversAhead = guides.values.take(50).any { g -> (g.programmes.lastOrNull()?.end ?: 0) > now + 6 * 3600 }
        if (fresh && coversAhead) return@withLock
        val cfg = LiveRepo.config ?: return@withLock
        val wanted = lineup.channels.mapNotNullTo(HashSet()) { it.epgId }
        val t0 = System.currentTimeMillis()
        runCatching {
            withContext(Dispatchers.IO) {
                val url = "${cfg.base}/xmltv.php".toHttpUrl().newBuilder()
                    .addQueryParameter("username", cfg.user).addQueryParameter("password", cfg.pass).build()
                val client = LiveRepo.http.newBuilder().readTimeout(120, TimeUnit.SECONDS).build()
                client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    if (!r.isSuccessful) error("HTTP ${r.code}")
                    parse(InputStreamReader(r.body!!.byteStream(), Charsets.UTF_8), wanted, now)
                }
            }
        }.onSuccess { g ->
            guides = g; _version.value++
            Log.i(TAG, "Gids via aanbieder: ${g.size} zenders, ${g.values.sumOf { it.programmes.size }} programma's in ${System.currentTimeMillis() - t0} ms")
            runCatching { withContext(Dispatchers.IO) { writeBin(file, g) } }
        }.onFailure { Log.w(TAG, "Gids laden mislukt: ${it.javaClass.simpleName}") }
    }

    /** XMLTV als stroom lezen; alleen gewenste zenders, en niets ouder dan een dag. */
    private fun parse(reader: java.io.Reader, wanted: Set<String>, now: Long): Map<String, ChannelGuide> {
        val xp = Xml.newPullParser()
        xp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        xp.setInput(reader)
        val tmp = HashMap<String, ArrayList<Programme>>(4096)
        val titles = HashMap<String, String>(16384)   // zelfde titels delen geheugen
        val minStart = now - 26 * 3600
        var ev = xp.eventType
        var ch: String? = null; var start = 0L; var end = 0L; var inProg = false
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (xp.name) {
                    "programme" -> {
                        val c = xp.getAttributeValue(null, "channel")?.lowercase()
                        inProg = c != null && c in wanted
                        if (inProg) {
                            ch = c
                            start = XmltvTime.parse(xp.getAttributeValue(null, "start") ?: "") ?: 0
                            end = XmltvTime.parse(xp.getAttributeValue(null, "stop") ?: "") ?: 0
                            if (end <= start || end < minStart) inProg = false
                        }
                    }
                    "title" -> if (inProg && ch != null) {
                        val t = xp.nextText().trim()
                        val title = titles.getOrPut(t) { t }
                        tmp.getOrPut(ch!!) { ArrayList() } += Programme(start, end, title)
                        inProg = false   // alleen de eerste titel
                    }
                }
            }
            ev = xp.next()
        }
        return tmp.mapValues { (_, v) -> ChannelGuide(v.sortedBy { it.start }.distinctBy { it.start }) }
    }

    private fun writeBin(file: File, g: Map<String, ChannelGuide>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        DataOutputStream(tmp.outputStream().buffered(256 * 1024)).use { o ->
            o.writeInt(1); o.writeInt(g.size)
            g.forEach { (id, guide) ->
                o.writeUTF(id); o.writeInt(guide.programmes.size)
                guide.programmes.forEach { p -> o.writeInt(p.start.toInt()); o.writeInt((p.end - p.start).toInt()); o.writeUTF(p.title.take(200)) }
            }
        }
        tmp.renameTo(file)
    }

    private fun readBin(file: File): Map<String, ChannelGuide> {
        val titles = HashMap<String, String>(16384)
        DataInputStream(file.inputStream().buffered(256 * 1024)).use { i ->
            check(i.readInt() == 1)
            val n = i.readInt()
            val out = HashMap<String, ChannelGuide>(n * 2)
            repeat(n) {
                val id = i.readUTF(); val c = i.readInt()
                val list = ArrayList<Programme>(c)
                repeat(c) {
                    val s = i.readInt().toLong(); val d = i.readInt()
                    val t = i.readUTF()
                    list += Programme(s, s + d, titles.getOrPut(t) { t })
                }
                out[id] = ChannelGuide(list)
            }
            return out
        }
    }

    // --- Omschrijvingen en archief per zender (op aanvraag) ---

    /** Programma met omschrijving en of het terug te kijken is (van de aanbieder, per zender). */
    data class Detail(val start: Long, val end: Long, val title: String, val description: String?, val archive: Boolean)

    private val details = LruCache<Int, List<Detail>>(24)

    suspend fun details(ch: LiveChannel): List<Detail> {
        details.get(ch.id)?.let { return it }
        val cfg = LiveRepo.config ?: return emptyList()
        return runCatching {
            withContext(Dispatchers.IO) {
                val url = LiveRepo.api(cfg).addQueryParameter("action", "get_simple_data_table").addQueryParameter("stream_id", ch.id.toString()).build()
                LiveRepo.http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    val arr = JSONObject(r.body!!.string()).optJSONArray("epg_listings") ?: return@use emptyList<Detail>()
                    fun b64(s: String) = runCatching { String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8) }.getOrDefault(s)
                    (0 until arr.length()).mapNotNull { k ->
                        val e = arr.getJSONObject(k)
                        val s = e.optString("start_timestamp").toLongOrNull() ?: return@mapNotNull null
                        val en = e.optString("stop_timestamp").toLongOrNull() ?: return@mapNotNull null
                        Detail(s, en, b64(e.optString("title")).trim(), b64(e.optString("description")).trim().takeIf { it.isNotBlank() }, e.optInt("has_archive") == 1)
                    }.sortedBy { it.start }
                }
            }
        }.getOrDefault(emptyList()).also { if (it.isNotEmpty()) details.put(ch.id, it) }
    }

    /** Gids voor een zender zonder XMLTV-gegevens: uit de details van de aanbieder. */
    fun guideFromDetails(list: List<Detail>) = ChannelGuide(list.map { Programme(it.start, it.end, it.title) })
}
