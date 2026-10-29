package nl.thuis.launcher.ui

import kotlin.math.max
import kotlin.math.min

/**
 * Merkkleuren uit een banner, icoon of poster. Pure Kotlin (geen Android), dus testbaar en snel.
 *
 * - [base]: de "achtergrondkleur" van het merk. De meeste TV-banners zijn een logo op een
 *   egale merkkleur; die lezen we van de rand af. Is de rand wit/zwart/grijs (bijv. Netflix),
 *   dan nemen we de meest uitgesproken kleur uit het logo.
 * - [glow]: de meest levendige kleur, voor de gloed achter de kaart.
 */
data class BrandColors(val base: Int, val glow: Int) {
    companion object {
        val Neutral = BrandColors(0xFF2A2E35.toInt(), 0xFF4A5060.toInt())

        /** [pixels] in ARGB, rij voor rij, [w] x [h]. Werkt prima op een verkleinde bitmap (bijv. 48x27). */
        fun from(pixels: IntArray, w: Int, h: Int): BrandColors {
            if (w <= 0 || h <= 0 || pixels.size < w * h) return Neutral

            // 1. Rand: meest voorkomende (gekwantiseerde) kleur.
            val edge = HashMap<Int, IntArray>() // bucket -> [count, rSum, gSum, bSum]
            fun addTo(map: HashMap<Int, IntArray>, c: Int) {
                if ((c ushr 24) < 0x80) return // transparant overslaan
                val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
                val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
                val acc = map.getOrPut(key) { IntArray(4) }
                acc[0]++; acc[1] += r; acc[2] += g; acc[3] += b
            }
            for (x in 0 until w) { addTo(edge, pixels[x]); addTo(edge, pixels[(h - 1) * w + x]) }
            for (y in 1 until h - 1) { addTo(edge, pixels[y * w]); addTo(edge, pixels[y * w + w - 1]) }
            val edgeColor = edge.values.maxByOrNull { it[0] }?.let(::avg)

            // 2. Hele afbeelding: meest "levendige" kleur, gewogen naar hoe vaak hij voorkomt.
            val all = HashMap<Int, IntArray>()
            for (c in pixels) addTo(all, c)
            val total = all.values.sumOf { it[0] }.coerceAtLeast(1)
            val vivid = all.values
                .filter { it[0] * 100 >= total } // minstens 1% van de pixels
                .map { avg(it) to it[0] }
                .filter { (c, _) -> lum(c) in 0.12..0.85 && sat(c) >= 0.25 }
                .maxByOrNull { (c, n) -> n * sat(c) * sat(c) }
                ?.first

            val edgeIsBrand = edgeColor != null && sat(edgeColor) >= 0.2 && lum(edgeColor) in 0.06..0.8
            val base = when {
                edgeIsBrand -> edgeColor!!
                vivid != null -> vivid
                edgeColor != null -> edgeColor
                else -> return Neutral
            }
            return BrandColors(base = base, glow = vivid ?: base)
        }

        private fun avg(a: IntArray): Int {
            val n = a[0].coerceAtLeast(1)
            return (0xFF shl 24) or ((a[1] / n) shl 16) or ((a[2] / n) shl 8) or (a[3] / n)
        }

        /** Relatieve helderheid 0..1 (benadering). */
        fun lum(c: Int): Double {
            val r = ((c shr 16) and 0xFF) / 255.0; val g = ((c shr 8) and 0xFF) / 255.0; val b = (c and 0xFF) / 255.0
            return 0.2126 * r + 0.7152 * g + 0.0722 * b
        }

        /** HSL-verzadiging 0..1. */
        fun sat(c: Int): Double {
            val r = ((c shr 16) and 0xFF) / 255.0; val g = ((c shr 8) and 0xFF) / 255.0; val b = (c and 0xFF) / 255.0
            val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
            val l = (mx + mn) / 2
            if (mx == mn) return 0.0
            val d = mx - mn
            return if (l > 0.5) d / (2 - mx - mn) else d / (mx + mn)
        }
    }
}
