package nl.thuis.launcher.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.json.JSONArray

/** Vastgezette apps, in de volgorde van het dock. */
class PinStore(context: Context) {
    private val prefs = context.getSharedPreferences("pins", Context.MODE_PRIVATE)

    val pins: SnapshotStateList<String> = mutableStateListOf<String>().apply {
        addAll(prefs.getString("order", "")!!.split(',').filter { it.isNotBlank() }.map {
            // Oude pin op ons eigen pakket hoorde bij Horizon (toen Bios).
            if (it == context.packageName) "${context.packageName}/${context.packageName}.bios.BiosActivity" else it
        }.distinct())
    }

    fun isPinned(pkg: String) = pkg in pins

    fun toggle(pkg: String) {
        if (!pins.remove(pkg)) pins.add(pkg)
        save()
    }

    /** Verschuift een pin één plek naar links (-1) of rechts (+1). */
    fun move(pkg: String, delta: Int) {
        val from = pins.indexOf(pkg)
        val to = from + delta
        if (from < 0 || to !in pins.indices) return
        pins.removeAt(from)
        pins.add(to, pkg)
        save()
    }

    /** Ruimt pins op van apps die niet meer geïnstalleerd zijn. */
    fun retainInstalled(installed: Set<String>) {
        if (pins.removeAll { it !in installed }) save()
    }

    private fun save() = prefs.edit().putString("order", pins.joinToString(",")).apply()
}

/** Films die je leuk vindt. Nieuwste eerst. Dit is de input voor de aanbevelingen. */
class LikeStore(context: Context) {
    private val prefs = context.getSharedPreferences("likes", Context.MODE_PRIVATE)

    val liked: SnapshotStateList<Movie> = mutableStateListOf<Movie>().apply {
        runCatching { addAll(JSONArray(prefs.getString("movies", "[]")).toMovies()) }
    }

    /** Films en series apart: een film en een serie kunnen bij TMDB hetzelfde nummer hebben. */
    fun isLiked(movie: Movie) = liked.any { it.key == movie.key }

    fun toggle(movie: Movie) {
        if (!liked.removeAll { it.key == movie.key }) liked.add(0, movie.copy(reason = ""))
        prefs.edit().putString("movies", liked.toList().toJsonArray().toString()).apply()
    }

    /** Verandert zodra je iets (niet meer) leuk vindt; gebruikt om de cache te verversen. */
    fun signature(): String = liked.map { it.key }.sorted().joinToString(",")
}
