package nl.thuis.launcher.bios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TasteTest {
    private val day = 86_400_000L
    private val now = 1_800_000_000_000L
    private fun ev(genres: List<Genre>, weight: Double, daysAgo: Int, year: Int = 2015) =
        TasteEvent(1, genres.map { it.id }, year, weight, now - daysAgo * day)

    private fun ordered(events: List<TasteEvent>) =
        TasteRanking.order(GenreShelves, TasteRanking.scores(events, GenreShelves, now, 2026)).map { it.key }

    @Test fun zonderKijkgedragStandaardvolgorde() {
        assertEquals(GenreShelves.map { it.key }, ordered(emptyList()))
    }

    @Test fun top100BlijftBovenaan() {
        val o = ordered(listOf(ev(listOf(Genre.HORROR), 6.0, 0), ev(listOf(Genre.HORROR, Genre.THRILLER), 6.0, 1)))
        assertEquals("top100", o[0])
        assertEquals("horror", o[1])
        assertEquals("thriller", o[2])
    }

    @Test fun ouderKijkgedragTeltMinder() {
        // Twee maanden geleden veel komedie, gisteren één avond actie: actie wint.
        val o = ordered(listOf(ev(listOf(Genre.COMEDY), 6.0, 60), ev(listOf(Genre.COMEDY), 6.0, 62), ev(listOf(Genre.ACTION), 5.0, 1)))
        assertTrue(o.indexOf("action") < o.indexOf("comedy"))
    }

    @Test fun klassiekersOpJaartal() {
        val o = ordered(listOf(ev(listOf(Genre.DRAMA), 3.0, 2, year = 1972)))
        assertEquals(listOf("top100"), o.take(1))
        assertTrue(o.indexOf("classics") <= 2 && o.indexOf("drama") <= 2)
    }

    @Test fun gewichten() {
        assertEquals(3.0, Taste.watchWeight(60.0), 0.001)
        assertEquals(6.0, Taste.watchWeight(500.0), 0.001)
    }
}
