package nl.thuis.launcher.data

import kotlin.random.Random

/** Pure rangschik-logica, los van netwerk en Android zodat hij te testen is. */
object RecScoring {

    private class Candidate(val movie: Movie) {
        var trendingRank: Int? = null
        var classic = false
        val becauseOf = mutableListOf<String>()
    }

    fun rank(
        trending: List<Movie>,
        classics: List<Movie>,
        similar: Map<Movie, List<Movie>>,
        liked: List<Movie>,
        genreNames: Map<Int, String>,
        size: Int = 20,
        minClassics: Int = 5,
        random: Random = Random(0),
    ): List<Movie> = rankScored(trending, classics, similar, liked, genreNames, size, minClassics, random).map { it.first }

    /** Likes splitsen: films sturen de films, series de series (ze beïnvloeden elkaar niet). */
    fun splitLikes(liked: List<Movie>): Pair<List<Movie>, List<Movie>> = liked.partition { !it.isSeries }.let { it.first to it.second }

    /** Films en series in één rij, op volgorde van score (beide al gerangschikt met [rankScored]). */
    fun mix(films: List<Pair<Movie, Double>>, series: List<Pair<Movie, Double>>): List<Movie> =
        (films + series).sortedByDescending { it.second }.distinctBy { it.first.key }.map { it.first }

    /** Zelfde als [rank], met de score erbij (hoog → laag). */
    fun rankScored(
        trending: List<Movie>,
        classics: List<Movie>,
        similar: Map<Movie, List<Movie>>,
        liked: List<Movie>,
        genreNames: Map<Int, String>,
        size: Int = 20,
        minClassics: Int = 5,
        random: Random = Random(0),
    ): List<Pair<Movie, Double>> {
        val likedKeys = liked.map { it.key }.toSet()
        val genreWeight = genreWeights(liked)

        val cands = LinkedHashMap<Int, Candidate>()
        fun add(m: Movie) = cands.getOrPut(m.id) { Candidate(m) }

        trending.forEachIndexed { i, m -> add(m).trendingRank = i }
        classics.forEach { add(it).classic = true }
        similar.forEach { (seed, list) -> list.forEach { add(it).becauseOf += seed.title } }

        val scored = cands.values
            .filter { it.movie.key !in likedKeys && it.movie.posterPath != null }
            .map { c ->
                val affinity = affinity(c.movie, genreWeight)
                val score = 0.30 * (c.movie.voteAverage / 10.0).coerceIn(0.0, 1.0) +
                    0.20 * (c.trendingRank?.let { 1.0 - it.toDouble() / trending.size } ?: if (c.classic) 0.4 else 0.0) +
                    0.30 * affinity +
                    0.20 * (c.becauseOf.size / 2.0).coerceAtMost(1.0) +
                    0.05 * random.nextDouble() // kleine dagelijkse variatie
                Triple(c, score, affinity)
            }
            .sortedByDescending { it.second }

        // Altijd een paar klassiekers erin, ook als trending hoger scoort.
        val picked = scored.filter { it.first.classic }.take(minClassics).toMutableList()
        for (s in scored) {
            if (picked.size >= size) break
            if (s !in picked) picked += s
        }

        return picked.sortedByDescending { it.second }.map { (c, score, affinity) ->
            c.movie.copy(reason = reason(c, affinity, genreWeight, genreNames)) to score
        }
    }

    /** Per genre: hoe vaak komt het voor in je likes, geschaald naar 0..1. */
    fun genreWeights(liked: List<Movie>): Map<Int, Double> {
        val counts = liked.flatMap { it.genreIds }.groupingBy { it }.eachCount()
        val max = counts.values.maxOrNull()?.toDouble() ?: return emptyMap()
        return counts.mapValues { it.value / max }
    }

    fun affinity(movie: Movie, weights: Map<Int, Double>): Double {
        if (weights.isEmpty() || movie.genreIds.isEmpty()) return 0.0
        val w = movie.genreIds.map { weights[it] ?: 0.0 }
        return 0.7 * w.max() + 0.3 * w.average()
    }

    private fun reason(c: Candidate, affinity: Double, weights: Map<Int, Double>, names: Map<Int, String>): String {
        val m = c.movie
        return when {
            c.becauseOf.isNotEmpty() -> "Omdat je ${c.becauseOf.first()} leuk vond"
            affinity >= 0.5 -> {
                val top = m.genreIds.maxByOrNull { weights[it] ?: 0.0 }
                "Past bij je smaak: ${names[top]?.lowercase() ?: "jouw genres"}"
            }
            c.classic -> "Klassieker uit ${m.year}"
            else -> "Populair deze week"
        }
    }
}
