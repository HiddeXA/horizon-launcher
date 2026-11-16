package nl.thuis.launcher.bios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesIndexTest {
    private fun s(id: Int, raw: String, modified: Long = id.toLong(), tmdb: Int? = null, cat: String = "1") =
        SeriesItem(id, raw, TitleMatch.cleanName(raw), TitleMatch.year(raw), null, null, modified, cat, tmdb)

    private val index = SeriesIndex(
        listOf(
            s(1, "┃NL┃ Zoutwater (2021)", tmdb = 500),
            s(2, "┃EN┃ Zoutwater (2021)", modified = 9, tmdb = 500),
            s(3, "NL | De Havenmeester (2019)"),
            s(4, "Zoute Grond", cat = "2"),
            s(5, "Noorderlicht (2025)"),
        ),
        listOf(VodCategory("1", "Drama"), VodCategory("2", "Overig")),
    )

    @Test fun titelsWordenOpgeschoond() {
        assertEquals("Zoutwater", index.byId[1]!!.title)
        assertEquals("2021", index.byId[1]!!.year)
        assertEquals("De Havenmeester", index.byId[3]!!.title)
    }

    @Test fun zoekenOntdubbeltEnZetBesteEerst() {
        val r = index.search("zout")
        assertEquals(listOf("Zoutwater", "Zoute Grond"), r.map { it.title })
        // Van de dubbele: de laatst bijgewerkte.
        assertEquals(2, r.first().id)
    }

    @Test fun zoekenZonderTekstGeeftNiets() = assertEquals(emptyList<SeriesItem>(), index.search("  "))

    @Test fun vindenOpTmdbNummer() = assertEquals(2, index.find(500, emptyList(), null)?.id)

    @Test fun vindenOpTitelEnJaar() {
        assertEquals(3, index.find(null, listOf("De Havenmeester"), "2019")?.id)
        assertEquals(3, index.find(null, listOf("The Harbour Master", "Havenmeester"), "2020")?.id)
        assertNull(index.find(null, listOf("De Havenmeester"), "2005"))
    }

    @Test fun vindenZonderJaarAlleenAlsEenduidig() {
        assertEquals(4, index.find(null, listOf("Zoute Grond"), "2010")?.id)
        assertNull(index.find(null, listOf("Bestaat Niet"), "2010"))
    }

    @Test fun nieuwsteEersteZonderDubbelen() {
        assertEquals(listOf(2, 5, 4, 3), index.newest.map { it.id })
    }

    @Test fun perCategorieNieuwsteEerst() {
        assertEquals(listOf(2, 5, 3, 1), index.byCategory["1"]!!.map { it.id })
    }
}
