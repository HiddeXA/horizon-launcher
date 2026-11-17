package nl.thuis.launcher.bios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterQueryTest {
    private fun f(kind: FilterKind, vararg labels: String) =
        BiosFilter(kind, FilterGenres.forKind(kind).filter { it.label in labels }.map { it.key }.toSet())

    @Test fun allesZonderGenresZoektBeide() {
        val p = FilterQuery.plan(BiosFilter())
        assertTrue(p.films && p.series)
        assertFalse(p.hasGenres)
    }

    @Test fun eenSoortZoektAlleenDieSoort() {
        val films = FilterQuery.plan(BiosFilter(FilterKind.FILMS))
        assertTrue(films.films); assertFalse(films.series)
        val series = FilterQuery.plan(BiosFilter(FilterKind.SERIES))
        assertFalse(series.films); assertTrue(series.series)
    }

    @Test fun gewoneGenresGevenEenCombinatie() {
        val p = FilterQuery.plan(f(FilterKind.FILMS, "Komedie", "Misdaad"))
        assertEquals(listOf(listOf(35, 80)), p.filmCombos)
    }

    @Test fun samengevoegdGenreGeeftOfCombinaties() {
        val p = FilterQuery.plan(f(FilterKind.ALL, "Actie & Avontuur", "Komedie"))
        // Een film met Actie óf Avontuur, en Komedie.
        assertEquals(setOf(listOf(28, 35), listOf(12, 35)), p.filmCombos.toSet())
        assertEquals(listOf(listOf(10759, 35)), p.tvCombos)
    }

    @Test fun tweeSamengevoegdGeeftVierCombinaties() {
        val p = FilterQuery.plan(f(FilterKind.ALL, "Actie & Avontuur", "Sci-Fi & Fantasy"))
        assertEquals(4, p.filmCombos.size)
        assertEquals(4, p.filmCombos.toSet().size)
    }

    @Test fun alleenFilmgenreBijAllesZoektAlleenFilms() {
        val p = FilterQuery.plan(f(FilterKind.ALL, "Horror", "Komedie"))
        assertTrue(p.films); assertFalse(p.series)
        assertEquals("Horror", p.onlyFilms?.label)
        assertNull(p.onlySeries)
    }

    @Test fun alleenSeriegenreBijAllesZoektAlleenSeries() {
        val p = FilterQuery.plan(f(FilterKind.ALL, "Reality"))
        assertFalse(p.films); assertTrue(p.series)
        assertEquals("Reality", p.onlySeries?.label)
    }

    @Test fun filmgenreEnSeriegenreSamenZoektNiets() {
        val p = FilterQuery.plan(f(FilterKind.ALL, "Horror", "Kids"))
        assertFalse(p.films); assertFalse(p.series)
    }

    @Test fun bijFilmsGeenMeldingAlleenFilms() = assertNull(FilterQuery.plan(f(FilterKind.FILMS, "Horror")).onlyFilms)
}
