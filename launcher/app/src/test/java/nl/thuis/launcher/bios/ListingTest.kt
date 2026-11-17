package nl.thuis.launcher.bios

import org.junit.Assert.assertEquals
import org.junit.Test

class ListingTest {
    private fun film(id: Int, raw: String, added: Long = id.toLong()) =
        VodItem(id, raw, TitleMatch.cleanName(raw), TitleMatch.year(raw), null, null, added, "1", "mkv", null)

    private fun serie(id: Int, raw: String, modified: Long = id.toLong()) =
        SeriesItem(id, raw, TitleMatch.cleanName(raw), TitleMatch.year(raw), null, null, modified, "1", null)

    private val films = VodIndex(listOf(film(1, "Zout op de huid"), film(2, "Zoutpilaar"), film(3, "Het Zoutpad"), film(4, "Zoutwater (1998)")), emptyList())
    private val series = SeriesIndex(listOf(serie(101, "Zoutwater (2021)"), serie(102, "Zoute Grond"), serie(103, "Noorderlicht")), emptyList())

    @Test fun zoekenMengtFilmsEnSeriesOpOvereenkomst() {
        val r = Listings.search(films, series, "zoutwater")
        // Beide "Zoutwater" passen exact; daarna de rest. Een film en een serie met dezelfde naam blijven allebei staan.
        assertEquals(listOf("Zoutwater", "Zoutwater"), r.take(2).map { it.title })
        assertEquals(setOf(true, false), r.take(2).map { it is Listing.Show }.toSet())
    }

    @Test fun zoekenBijGelijkeScoreNieuwsteEerst() {
        val r = Listings.search(films, series, "zout")
        assertEquals(6, r.size)
        assertEquals(listOf("Zoute Grond", "Zoutwater", "Zoutwater"), r.take(3).map { it.title })
        assertEquals("Zout op de huid", r.last().title)
    }

    @Test fun zoekenZonderSeries() = assertEquals(4, Listings.search(films, null, "zout").size)

    @Test fun leegZoekenGeeftNiets() = assertEquals(0, Listings.search(films, series, " ").size)

    @Test fun mijnLijstLaatstToegevoegdEerst() {
        val r = Listings.myList(
            films = listOf(2 to 500L, 1 to 0L, 3 to 0L), // 1 en 3 stonden er al voor deze versie (geen tijd)
            series = listOf(102 to 900L, 101 to 100L),
            filmIndex = films, seriesIndex = series,
        )
        assertEquals(listOf(102, 2, 101, 1, 3), r.map { it.id })
    }

    @Test fun mijnLijstSlaatVerdwenenTitelsOver() {
        val r = Listings.myList(listOf(99 to 5L, 1 to 1L), listOf(999 to 3L), films, series)
        assertEquals(listOf(1), r.map { it.id })
    }

    @Test fun mijnLijstZonderSerieLijst() {
        val r = Listings.myList(listOf(1 to 1L), listOf(101 to 3L), films, null)
        assertEquals(listOf(1), r.map { it.id })
    }
}
