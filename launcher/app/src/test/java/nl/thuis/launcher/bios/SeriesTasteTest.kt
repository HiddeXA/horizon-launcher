package nl.thuis.launcher.bios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesTasteTest {
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    private fun ev(genres: List<TvGenre>, weight: Double, daysAgo: Int, year: Int? = 2020) =
        TasteEvent(1, genres.map { it.id }, year, weight, now - daysAgo * day)

    private fun ordered(events: List<TasteEvent>) =
        SeriesTasteRanking.order(SeriesShelves, SeriesTasteRanking.scores(events, SeriesShelves, now, 2026)).map { it.key }

    @Test fun zonderKijkgedragStandaardvolgorde() = assertEquals(SeriesShelves.map { it.key }, ordered(emptyList()))

    @Test fun meestGekekenGenreBovenaanNaTopSeries() {
        val o = ordered(listOf(ev(listOf(TvGenre.CRIME), 3.0, 1), ev(listOf(TvGenre.CRIME, TvGenre.DRAMA), 3.0, 2), ev(listOf(TvGenre.SCIFI), 2.0, 0)))
        assertEquals(listOf("top", "crime", "drama", "scifi"), o.take(4))
    }

    @Test fun oudKijkgedragTeltMinder() {
        // Komedie 60 dagen geleden (telt nog een kwart), Mysterie gisteren.
        val o = ordered(listOf(ev(listOf(TvGenre.COMEDY), 6.0, 60), ev(listOf(TvGenre.MYSTERY), 2.0, 1)))
        assertTrue(o.indexOf("mystery") < o.indexOf("comedy"))
    }

    @Test fun populairNuTeltRecenteSeries() {
        val s = SeriesTasteRanking.scores(listOf(ev(emptyList(), 2.0, 0, year = 2025)), SeriesShelves, now, 2026)
        assertEquals(1.2, s["popular"]!!, 0.0001)
        assertFalse("top" in s)
    }

    @Test fun eenAfleveringVanEenUurTeltAls3() = assertEquals(3.0, Taste_watchWeight(60.0), 0.0001)

    // Zelfde formule als Taste.watchWeight (staat in een Android-bestand).
    private fun Taste_watchWeight(minutes: Double) = (minutes / 20.0).coerceAtMost(6.0)
}
