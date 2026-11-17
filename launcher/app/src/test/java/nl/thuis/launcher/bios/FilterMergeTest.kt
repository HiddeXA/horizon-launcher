package nl.thuis.launcher.bios

import org.junit.Assert.assertEquals
import org.junit.Test

class FilterMergeTest {
    private fun film(id: Int, title: String, rating: Double? = null, added: Long = 0) =
        VodItem(id, title, title, null, null, rating, added, "1", "mkv", null)

    private fun serie(id: Int, title: String, rating: Double? = null, modified: Long = 0) =
        SeriesItem(id, title, title, null, null, rating, modified, "1", null)

    private val films = listOf(film(1, "Heat", 7.9, 100), film(2, "Casino", 8.0, 300), film(3, "Fargo", 7.8, 200))
    private val series = listOf(serie(1, "Twin Peaks", 8.3, 250), serie(2, "Baantjer", 7.4, 50))
    private val noPop = FilterMerge.Scores(filmPop = { null }, showPop = { null }, filmVote = { it.rating }, showVote = { it.rating })

    @Test fun besteScoreOverBeideSoorten() {
        val r = FilterMerge.merge(films, series, FilterSort.RATING, noPop)
        assertEquals(listOf("Twin Peaks", "Casino", "Heat", "Fargo", "Baantjer"), r.map { it.title })
    }

    @Test fun nieuwToegevoegdOpTijd() {
        val r = FilterMerge.merge(films, series, FilterSort.NEWEST, noPop)
        assertEquals(listOf("Casino", "Twin Peaks", "Fargo", "Heat", "Baantjer"), r.map { it.title })
    }

    @Test fun aTotZ() {
        val r = FilterMerge.merge(films, series, FilterSort.AZ, noPop)
        assertEquals(listOf("Baantjer", "Casino", "Fargo", "Heat", "Twin Peaks"), r.map { it.title })
    }

    @Test fun populairOpTmdbPopulariteit() {
        val pop = mapOf("Heat" to 50.0, "Casino" to 20.0, "Fargo" to 10.0, "Twin Peaks" to 30.0, "Baantjer" to 5.0)
        val s = FilterMerge.Scores({ pop[it.title] }, { pop[it.title] }, { it.rating }, { it.rating })
        val r = FilterMerge.merge(films, series, FilterSort.POPULAR, s)
        assertEquals(listOf("Heat", "Twin Peaks", "Casino", "Fargo", "Baantjer"), r.map { it.title })
    }

    @Test fun populairZonderCijfersVlechtDeLijstenInElkaar() {
        // Zonder populariteit houden beide lijsten hun eigen volgorde en wisselen ze elkaar af.
        val r = FilterMerge.merge(films, series, FilterSort.POPULAR, noPop)
        assertEquals(listOf("Heat", "Twin Peaks", "Casino", "Baantjer", "Fargo"), r.map { it.title })
    }

    @Test fun filmEnSerieMetDezelfdeTitelBlijvenAllebei() {
        val r = FilterMerge.merge(listOf(film(7, "Fargo", 8.1)), listOf(serie(7, "Fargo", 8.9)), FilterSort.RATING, noPop)
        assertEquals(listOf("s7", "f7"), r.map { it.listKey })
    }

    @Test fun eenKantLeeg() {
        assertEquals(3, FilterMerge.merge(films, emptyList(), FilterSort.AZ, noPop).size)
        assertEquals(2, FilterMerge.merge(emptyList(), series, FilterSort.AZ, noPop).size)
    }
}
