package nl.thuis.launcher.data

import android.content.Context
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Test of sleutels en inlog werken. Geeft per onderdeel een korte uitslag terug.
 * Wachtwoorden en sleutels komen nooit in een melding of log terecht.
 */
object KeyTester {
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build()

    data class Result(val ok: Boolean, val message: String)

    suspend fun tmdb(token: String): Result = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext Result(false, "Niet ingevuld")
        runCatching {
            http.newCall(
                Request.Builder().url("https://api.themoviedb.org/3/configuration").header("Authorization", "Bearer $token").build()
            ).execute().use { r ->
                when {
                    r.isSuccessful -> Result(true, "Werkt")
                    r.code == 401 -> Result(false, "Token klopt niet (gebruik de lange Read Access Token)")
                    else -> Result(false, "Fout ${r.code}")
                }
            }
        }.getOrElse { Result(false, "Geen verbinding") }
    }

    suspend fun omdb(key: String): Result = withContext(Dispatchers.IO) {
        if (key.isBlank()) return@withContext Result(false, "Niet ingevuld")
        runCatching {
            val url = "https://www.omdbapi.com/".toHttpUrl().newBuilder().addQueryParameter("i", "tt0111161").addQueryParameter("apikey", key).build()
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                val body = r.body?.string().orEmpty()
                when {
                    body.contains("\"Response\":\"True\"") -> Result(true, "Werkt")
                    body.contains("activated", ignoreCase = true) -> Result(false, "Nog niet geactiveerd: klik op de link in de mail")
                    else -> Result(false, "Sleutel klopt niet")
                }
            }
        }.getOrElse { Result(false, "Geen verbinding") }
    }

    suspend fun xtream(server: String, user: String, pass: String): Result = withContext(Dispatchers.IO) {
        if (server.isBlank() || user.isBlank()) return@withContext Result(false, "Niet ingevuld")
        runCatching {
            val base = server.trim().trimEnd('/').let { if (it.startsWith("http")) it else "http://$it" }
            val url = "$base/player_api.php".toHttpUrl().newBuilder()
                .addQueryParameter("username", user.trim()).addQueryParameter("password", pass).build()
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return@use Result(false, "Server gaf fout ${r.code}")
                val info = JSONObject(r.body!!.string()).optJSONObject("user_info")
                if (info?.optInt("auth", 0) == 1) {
                    val exp = info.optString("exp_date").toLongOrNull()?.let {
                        java.text.SimpleDateFormat("d-M-yyyy", java.util.Locale.forLanguageTag("nl-NL")).format(java.util.Date(it * 1000))
                    }
                    Result(true, "Ingelogd" + (exp?.let { ", geldig tot $it" } ?: ""))
                } else Result(false, "Gebruikersnaam of wachtwoord klopt niet")
            }
        }.getOrElse { Result(false, "Server niet bereikbaar of ongeldig adres") }
    }
}

/**
 * Kleine webpagina op de tv om sleutels vanaf je telefoon of pc te plakken.
 * - Draait alleen zolang het instellingenscherm open is.
 * - Alleen bereikbaar in je eigen thuisnetwerk, en alleen met de code die op de tv staat.
 * - Na 5 foute codes stopt hij.
 * - Toont nooit bestaande sleutels; lege velden laten de huidige waarde staan.
 */
class SetupServer(private val onSaved: (Map<Secrets.Key, String>) -> Unit) {

    val pin: String = Random.nextInt(1000, 10000).toString()
    val port = 8765
    private var socket: ServerSocket? = null
    private var wrong = 0
    private val main = Handler(Looper.getMainLooper())

    fun start() {
        if (socket != null) return
        val s = runCatching { ServerSocket(port) }.getOrNull() ?: return
        socket = s
        Thread({
            while (!s.isClosed) {
                val client = runCatching { s.accept() }.getOrNull() ?: break
                runCatching { client.use { handle(it) } }
            }
        }, "thuis-setup").apply { isDaemon = true }.start()
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun handle(c: Socket) {
        c.soTimeout = 10_000
        val reader = BufferedReader(InputStreamReader(c.getInputStream(), Charsets.UTF_8))
        val requestLine = reader.readLine() ?: return
        var length = 0
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Content-Length:", ignoreCase = true)) length = line.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        val out = c.getOutputStream()
        fun respond(html: String) {
            val bytes = page(html).toByteArray(Charsets.UTF_8)
            out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray())
            out.write(bytes)
            out.flush()
        }

        if (requestLine.startsWith("POST /save") && length in 1..16_384) {
            val buf = CharArray(length)
            var read = 0
            while (read < length) { val n = reader.read(buf, read, length - read); if (n < 0) break; read += n }
            val form = String(buf, 0, read).split('&').mapNotNull {
                val i = it.indexOf('=')
                if (i <= 0) null else URLDecoder.decode(it.substring(0, i), "UTF-8") to URLDecoder.decode(it.substring(i + 1), "UTF-8")
            }.toMap()
            if (form["pin"]?.trim() != pin) {
                wrong++
                if (wrong >= 5) { respond("<p>Te vaak een foute code. Open het instellingenscherm op de tv opnieuw.</p>"); stop(); return }
                respond("<p class=err>Foute code. Kijk op je tv.</p>" + formHtml())
                return
            }
            val values = Secrets.Key.entries.mapNotNull { k -> form[k.pref]?.trim()?.takeIf { it.isNotEmpty() }?.let { k to it } }.toMap()
            main.post { onSaved(values) }
            respond("<p class=ok>Opgeslagen: ${values.keys.joinToString(", ") { it.label }.ifEmpty { "niets gewijzigd" }}.</p><p>Kijk op je tv: daar zie je of alles werkt.</p>")
        } else {
            respond(formHtml())
        }
    }

    private fun formHtml(): String = buildString {
        append("<form method=post action=/save>")
        append("<label>Code op je tv<input name=pin inputmode=numeric autocomplete=off required></label>")
        Secrets.Key.entries.forEach { k ->
            val type = if (k == Secrets.Key.XPASS) "password" else "text"
            append("<label>${k.label}<input name=${k.pref} type=$type autocomplete=off placeholder=\"${k.hint} (leeg = niet wijzigen)\"></label>")
        }
        append("<button>Opslaan op de tv</button></form>")
    }

    private fun page(body: String) = """<!doctype html><html lang=nl><meta charset=utf-8>
<meta name=viewport content="width=device-width,initial-scale=1"><title>Thuis instellen</title>
<style>body{font-family:system-ui,sans-serif;background:#0b0d10;color:#f2f2f2;max-width:520px;margin:0 auto;padding:16px}
h1{font-size:22px}label{display:block;margin:14px 0 4px;color:#a7acb4;font-size:14px}
input{display:block;width:100%;box-sizing:border-box;margin-top:6px;padding:12px;border-radius:8px;border:1px solid #2a2e35;background:#1c1f24;color:#fff;font-size:16px}
button{margin-top:20px;width:100%;padding:14px;border:0;border-radius:8px;background:#f2b84b;color:#111;font-size:16px;font-weight:600}
.ok{color:#46d369}.err{color:#ff5a6e}</style>
<h1>Thuis: sleutels en inlog</h1>$body</html>"""

    companion object {
        /** IPv4-adres van de tv in je thuisnetwerk, of null. */
        fun localIp(context: Context): String? {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
            val props = cm.getLinkProperties(cm.activeNetwork) ?: return null
            return props.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
        }
    }
}
