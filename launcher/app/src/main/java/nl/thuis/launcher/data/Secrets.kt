package nl.thuis.launcher.data

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import nl.thuis.launcher.BuildConfig

/**
 * API-sleutels en inloggegevens. Wat je in het instellingenscherm invult wordt op het kastje
 * bewaard (privé-opslag van de app) en gaat vóór wat bij het bouwen is meegegeven (local.properties).
 */
object Secrets {

    enum class Key(val pref: String, val label: String, val secret: Boolean, val hint: String) {
        TMDB("tmdb", "TMDB-token", true, "API Read Access Token, begint met eyJ"),
        OMDB("omdb", "OMDb-sleutel", true, "8 tekens, uit de mail van omdbapi.com"),
        XSERVER("xserver", "IPTV-server", false, "bijv. http://provider.tv:8080"),
        XUSER("xuser", "IPTV-gebruikersnaam", false, "zelfde als in TiviMate"),
        XPASS("xpass", "IPTV-wachtwoord", true, "zelfde als in TiviMate"),
    }

    private lateinit var prefs: SharedPreferences

    /** Wordt opgehoogd bij elke wijziging; schermen die ervan afhangen lezen dit. */
    val version = mutableIntStateOf(0)

    private val listeners = mutableListOf<(Set<Key>) -> Unit>()

    fun init(context: Context) {
        if (!::prefs.isInitialized) prefs = context.applicationContext.getSharedPreferences("secrets", Context.MODE_PRIVATE)
    }

    fun get(key: Key): String {
        val stored = if (::prefs.isInitialized) prefs.getString(key.pref, null) else null
        return stored ?: default(key)
    }

    /** Alleen de opgegeven velden wijzigen. Een lege waarde wist de instelling op het kastje. */
    fun set(values: Map<Key, String>) {
        if (values.isEmpty()) return
        val changed = values.filter { (k, v) -> get(k) != v.trim() }.keys
        if (changed.isEmpty()) return
        prefs.edit().apply {
            values.forEach { (k, v) -> putString(k.pref, v.trim()) }
        }.apply()
        version.intValue++
        listeners.toList().forEach { it(changed) }
    }

    fun onChange(listener: (Set<Key>) -> Unit) { listeners += listener }

    /** "••••••••ab12": laat zien dát er iets staat, zonder de waarde te tonen. */
    fun masked(key: Key): String {
        val v = get(key)
        if (v.isBlank()) return ""
        if (!key.secret) return v
        return "•".repeat(8) + v.takeLast(4)
    }

    private fun default(key: Key): String = when (key) {
        Key.TMDB -> BuildConfig.TMDB_TOKEN
        Key.OMDB -> BuildConfig.OMDB_KEY
        Key.XSERVER -> BuildConfig.XTREAM_SERVER
        Key.XUSER -> BuildConfig.XTREAM_USER
        Key.XPASS -> BuildConfig.XTREAM_PASS
    }
}

/** Zorgt dat de instellingen klaarstaan vóór welk scherm dan ook start. */
class ThuisApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Secrets.init(this)
    }
}
