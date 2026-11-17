package nl.thuis.launcher.bios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterSwitchTest {
    private fun keys(kind: FilterKind, vararg labels: String) =
        FilterGenres.forKind(kind).filter { it.label in labels }.map { it.key }.toSet()

    private fun labels(f: BiosFilter) = f.options.map { it.label }.toSet()

    @Test fun standaardIsAlles() {
        val f = BiosFilter()
        assertEquals(FilterKind.ALL, f.kind)
        assertTrue(f.isEmpty)
        assertFalse(f.canClear)
    }

    @Test fun periodeScoreEnSorteringBlijvenStaan() {
        val f = BiosFilter(period = FilterPeriod.NINETIES, minScore = 7.0, sort = FilterSort.RATING)
        for (to in FilterKind.entries) {
            val g = f.switchTo(to)
            assertEquals(to, g.kind)
            assertEquals(FilterPeriod.NINETIES, g.period)
            assertEquals(7.0, g.minScore, 0.0)
            assertEquals(FilterSort.RATING, g.sort)
        }
    }

    @Test fun naarSeriesVervaltWatSeriesNietKennen() {
        val f = BiosFilter(FilterKind.FILMS, keys(FilterKind.FILMS, "Komedie", "Horror", "Misdaad"))
        assertEquals(setOf("Komedie", "Misdaad"), labels(f.switchTo(FilterKind.SERIES)))
    }

    @Test fun naarFilmsVervaltWatFilmsNietKennen() {
        val f = BiosFilter(FilterKind.SERIES, keys(FilterKind.SERIES, "Drama", "Kids"))
        assertEquals(setOf("Drama"), labels(f.switchTo(FilterKind.FILMS)))
    }

    @Test fun samengevoegdNaarFilmsWordtHetEersteFilmgenre() {
        val f = BiosFilter(FilterKind.ALL, keys(FilterKind.ALL, "Actie & Avontuur", "Sci-Fi & Fantasy", "Oorlog"))
        assertEquals(setOf("Actie", "Sciencefiction", "Oorlog"), labels(f.switchTo(FilterKind.FILMS)))
        assertEquals(setOf("Actie & Avontuur", "Sci-Fi & Fantasy", "Oorlog & Politiek"), labels(f.switchTo(FilterKind.SERIES)))
    }

    @Test fun filmgenresNaarAllesWordenSamengevoegd() {
        val f = BiosFilter(FilterKind.FILMS, keys(FilterKind.FILMS, "Actie", "Avontuur", "Horror"))
        assertEquals(setOf("Actie & Avontuur", "Horror"), labels(f.switchTo(FilterKind.ALL)))
    }

    @Test fun actieBijFilmsWordtActieEnAvontuurBijSeries() {
        val f = BiosFilter(FilterKind.FILMS, keys(FilterKind.FILMS, "Actie"))
        assertEquals(setOf("Actie & Avontuur"), labels(f.switchTo(FilterKind.SERIES)))
    }

    @Test fun seriegenresNaarAlles() {
        val f = BiosFilter(FilterKind.SERIES, keys(FilterKind.SERIES, "Oorlog & Politiek", "Reality"))
        assertEquals(setOf("Oorlog", "Reality"), labels(f.switchTo(FilterKind.ALL)))
    }

    @Test fun zelfdeSoortVerandertNiets() {
        val f = BiosFilter(FilterKind.FILMS, keys(FilterKind.FILMS, "Drama"), FilterPeriod.TENS)
        assertEquals(f, f.switchTo(FilterKind.FILMS))
    }

    @Test fun aanUitZetten() {
        val drama = FilterGenres.forKind(FilterKind.ALL).first { it.label == "Drama" }
        val f = BiosFilter().toggle(drama)
        assertEquals(setOf("Drama"), labels(f))
        assertTrue(f.toggle(drama).genres.isEmpty())
    }

    @Test fun wissenKanBijSorteringOfSoort() {
        assertTrue(BiosFilter(sort = FilterSort.AZ).canClear)
        assertTrue(BiosFilter(kind = FilterKind.SERIES).canClear)
        assertTrue(BiosFilter(minScore = 6.0).canClear)
        // De stip op de pil Filter: alleen echte filters, geen sortering of soort.
        assertTrue(BiosFilter(sort = FilterSort.AZ, kind = FilterKind.SERIES).isEmpty)
    }

    @Test fun optiesInLijstvolgorde() {
        val f = BiosFilter(FilterKind.ALL, keys(FilterKind.ALL, "Western", "Drama", "Animatie"))
        assertEquals(listOf("Animatie", "Drama", "Western"), f.options.map { it.label })
    }
}
