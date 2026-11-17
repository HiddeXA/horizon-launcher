package nl.thuis.launcher.bios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterGenresTest {
    @Test fun aantallenPerSoort() {
        assertEquals(20, FilterGenres.forKind(FilterKind.ALL).size)
        assertEquals(18, FilterGenres.forKind(FilterKind.FILMS).size)
        assertEquals(15, FilterGenres.forKind(FilterKind.SERIES).size)
    }

    @Test fun filmsEnSeriesVolgenDeTmdbLijsten() {
        assertEquals(Genre.entries.map { it.label }, FilterGenres.forKind(FilterKind.FILMS).map { it.label })
        assertEquals(TvGenre.entries.map { it.label }, FilterGenres.forKind(FilterKind.SERIES).map { it.label })
    }

    @Test fun allesIsAlfabetischMetDrieSamengevoegd() {
        val labels = FilterGenres.forKind(FilterKind.ALL).map { it.label }
        assertEquals(labels.sortedBy { it.lowercase() }, labels)
        val aa = FilterGenres.forKind(FilterKind.ALL).first { it.label == "Actie & Avontuur" }
        assertEquals(listOf(28, 12), aa.filmIds)
        assertEquals(listOf(10759), aa.tvIds)
        val sf = FilterGenres.forKind(FilterKind.ALL).first { it.label == "Sci-Fi & Fantasy" }
        assertEquals(setOf(878, 14), sf.filmIds.toSet())
        assertEquals(listOf(10765), sf.tvIds)
        val war = FilterGenres.forKind(FilterKind.ALL).first { it.label == "Oorlog" }
        assertEquals(listOf(10752), war.filmIds)
        assertEquals(listOf(10768), war.tvIds)
    }

    @Test fun allesKentGeenLosseActieOfFantasy() {
        val labels = FilterGenres.forKind(FilterKind.ALL).map { it.label }
        listOf("Actie", "Avontuur", "Fantasy", "Sciencefiction", "Oorlog & Politiek").forEach { assertFalse(it, it in labels) }
    }

    @Test fun alleenFilmsOfAlleenSeries() {
        val all = FilterGenres.forKind(FilterKind.ALL).associateBy { it.label }
        assertEquals(setOf("Historisch", "Horror", "Muziek", "Romantiek", "Thriller"), all.values.filter { it.onlyFilms }.map { it.label }.toSet())
        assertEquals(setOf("Kids", "Reality", "Soap", "Talkshow"), all.values.filter { it.onlySeries }.map { it.label }.toSet())
        assertTrue(all.getValue("Drama").filmIds == listOf(18) && all.getValue("Drama").tvIds == listOf(18))
    }

    @Test fun sleutelsZijnUniekOverAlleLijsten() {
        val keys = FilterKind.entries.flatMap { k -> FilterGenres.forKind(k).map { it.key } }
        assertEquals(keys.size, keys.toSet().size)
    }
}
