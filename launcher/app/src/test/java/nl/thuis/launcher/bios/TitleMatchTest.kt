package nl.thuis.launcher.bios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleMatchTest {
    @Test fun schoneTitels() {
        assertEquals("The Matrix", TitleMatch.cleanName("NL | The Matrix (1999) 4K"))
        assertEquals("Dune: Part Two", TitleMatch.cleanName("EN - Dune: Part Two - 2024"))
        assertEquals("Oppenheimer", TitleMatch.cleanName("[4K] Oppenheimer [2023]"))
        assertEquals("Spider-Man: Brand New Day", TitleMatch.cleanName("|NL| Spider-Man: Brand New Day (2026) NL Subs"))
        assertEquals("Amélie", TitleMatch.cleanName("FR: Amélie 2001"))
        assertEquals("1917", TitleMatch.cleanName("1917 (2019)"))
        assertEquals("The Stunt Driver", TitleMatch.cleanName("┃NL┃ The Stunt Driver  (2026)"))
        assertEquals("Spider-Man: Brand New Day", TitleMatch.cleanName("┃EN┃ Spider-Man: Brand New Day (2026)"))
        assertEquals("Heat", TitleMatch.cleanName("│NL│ Heat (1995)"))
        assertEquals("Heat", TitleMatch.cleanName("｜NL｜ Heat"))
        assertEquals("Heat", TitleMatch.cleanName("NL ┃ Heat"))
    }
    @Test fun jaartal() {
        assertEquals("1999", TitleMatch.year("NL | The Matrix (1999) 4K"))
        assertEquals("2019", TitleMatch.year("1917 (2019)"))
        assertEquals(null, TitleMatch.year("Dune Part Two"))
    }
    @Test fun sleutels() {
        assertEquals("matrix", TitleMatch.key("The Matrix"))
        assertEquals("amelie", TitleMatch.key("Amélie"))
        assertEquals("fast and furious", TitleMatch.key("Fast & Furious"))
        assertEquals(TitleMatch.key("Spider-Man: Brand New Day"), TitleMatch.key("Spider Man Brand New Day"))
    }
    private fun item(id: Int, raw: String, tmdb: Int? = null, added: Long = id.toLong()) =
        VodItem(id, raw, TitleMatch.cleanName(raw), TitleMatch.year(raw), null, null, added, "1", "mkv", tmdb)

    @Test fun matchOpTitelEnJaar() {
        val idx = VodIndex(listOf(
            item(1, "NL | Dune (1984)"), item(2, "NL | Dune (2021)"), item(3, "4K | Dune (2021) 4K", added = 99),
            item(4, "The Matrix"), item(5, "EN | Parasite", tmdb = 496243),
        ), emptyList())
        assertEquals(3, idx.find(null, listOf("Dune"), "2021")?.id)
        assertEquals(1, idx.find(null, listOf("Dune"), "1984")?.id)
        assertEquals(4, idx.find(null, listOf("Matrix", "The Matrix"), "1999")?.id)
        assertEquals(5, idx.find(496243, listOf("Gisaengchung"), "2019")?.id)
        assertEquals(null, idx.find(null, listOf("Dune"), "2026"))
    }
    @Test fun zoeken() {
        val idx = VodIndex(listOf(item(1, "NL | The Dark Knight (2008)"), item(2, "NL | Knight and Day (2010)"), item(3, "Darkest Hour (2017)")), emptyList())
        assertEquals(listOf(1, 2), idx.search("knight").map { it.id }.sorted())
        assertEquals(1, idx.search("dark kn").first().id)
        assertEquals(listOf(2), idx.search("knight and").map { it.id })
    }
}
