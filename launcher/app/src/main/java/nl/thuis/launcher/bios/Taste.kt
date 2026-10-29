package nl.thuis.launcher.bios

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.pow

/** Eén keer iets met een film gedaan: gekeken (gewicht naar minuten), in je lijst gezet, of geopend. */
data class TasteEvent(val vodId: Int, val genres: List<Int>, val year: Int?, val weight: Double, val time: Long)

/**
 * Bepaalt de volgorde van de categorieën op het startscherm. Pure Kotlin, met unit tests.
 *
 * Elke categorie krijgt een score uit wat je gekeken hebt: hoe langer je keek, hoe zwaarder het telt,
 * en oud kijkgedrag telt steeds minder mee (halveert elke 30 dagen). Categorieën zonder score houden
 * de standaardvolgorde, ná de categorieën die je wel kijkt. Vastgepinde rijen staan altijd bovenaan.
 */
object TasteRanking {
    const val HALF_LIFE_DAYS = 30.0

    fun scores(events: List<TasteEvent>, shelves: List<GenreShelf>, now: Long, thisYear: Int): Map<String, Double> {
        val out = HashMap<String, Double>()
        for (e in events) {
            val ageDays = ((now - e.time).coerceAtLeast(0)) / 86_400_000.0
            val w = e.weight * 0.5.pow(ageDays / HALF_LIFE_DAYS)
            for (s in shelves) {
                val match = when (s.kind) {
                    ShelfKind.GENRE -> if (s.genres.all { it.id in e.genres }) 1.0 else 0.0
                    ShelfKind.CLASSIC -> if (e.year != null && e.year <= 1995) 1.0 else 0.0
                    ShelfKind.POPULAR -> if (e.year != null && e.year >= thisYear - 2) 0.6 else 0.0
                    ShelfKind.TOP -> 0.0
                }
                if (match > 0) out[s.key] = (out[s.key] ?: 0.0) + w * match
            }
        }
        return out
    }

    /** Vastgepind eerst (in eigen volgorde), dan op score (hoog → laag), dan de standaardvolgorde. */
    fun order(shelves: List<GenreShelf>, scores: Map<String, Double>): List<GenreShelf> {
        val pinned = shelves.filter { it.pinned }
        val rest = shelves.filterNot { it.pinned }.withIndex()
            .sortedWith(compareByDescending<IndexedValue<GenreShelf>> { scores[it.value.key] ?: 0.0 }.thenBy { it.index })
            .map { it.value }
        return pinned + rest
    }
}

/** Je kijkgedrag in Horizon, bewaard op het kastje (alleen in deze app, gaat nergens heen). */
class Taste(context: Context) {
    private val prefs = context.getSharedPreferences("bios-taste", Context.MODE_PRIVATE)
    @Volatile var events: List<TasteEvent> = load()
        private set

    private fun load(): List<TasteEvent> = runCatching {
        val a = JSONArray(prefs.getString("events", "[]"))
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            val g = o.getJSONArray("g")
            TasteEvent(o.getInt("v"), List(g.length()) { g.getInt(it) }, o.optInt("y", 0).takeIf { it > 0 }, o.getDouble("w"), o.getLong("t"))
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun record(item: VodItem, genres: List<Int>, weight: Double) {
        if (weight <= 0) return
        val e = TasteEvent(item.id, genres, item.year?.toIntOrNull(), weight, System.currentTimeMillis())
        events = (events + e).takeLast(400)
        val a = JSONArray()
        events.forEach { ev ->
            a.put(JSONObject().put("v", ev.vodId).put("g", JSONArray(ev.genres)).put("y", ev.year ?: 0).put("w", ev.weight).put("t", ev.time))
        }
        prefs.edit().putString("events", a.toString()).apply()
    }

    companion object {
        /** Gewichten: een uur kijken telt als 3, in je lijst zetten als 1, een film openen als 0,2. */
        fun watchWeight(minutes: Double) = (minutes / 20.0).coerceAtMost(6.0)
        const val ADD_TO_LIST = 1.0
        const val OPEN_DETAIL = 0.2
    }
}
