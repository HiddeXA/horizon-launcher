package nl.thuis.launcher.bios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilterTextsTest {
    private fun f(kind: FilterKind, vararg labels: String, period: FilterPeriod = FilterPeriod.ALL, score: Double = 0.0) =
        BiosFilter(kind, FilterGenres.forKind(kind).filter { it.label in labels }.map { it.key }.toSet(), period, score)

    @Test fun waardeInDeKeuzeknop() {
        assertNull(FilterTexts.chipValue(emptyList()))
        assertEquals("Misdaad", FilterTexts.chipValue(listOf("Misdaad")))
        assertEquals("Misdaad, Drama", FilterTexts.chipValue(listOf("Misdaad", "Drama")))
        assertEquals("Actie, Komedie +5", FilterTexts.chipValue(listOf("Actie", "Komedie", "A", "B", "C", "D", "E")))
    }

    @Test fun aantalBijAlles() {
        val c = FilterTexts.count(f(FilterKind.ALL, "Misdaad"), films = 29, series = 9, capped = false)
        assertEquals("38 titels", c.main)
        assertEquals("29 films, 9 series", c.detail)
    }

    @Test fun aantalEnkelvoud() {
        val c = FilterTexts.count(f(FilterKind.ALL, "Misdaad"), films = 1, series = 0, capped = false)
        assertEquals("1 titel", c.main)
        assertEquals("1 film, 0 series", c.detail)
        assertEquals("1 serie", FilterTexts.count(BiosFilter(FilterKind.SERIES), 0, 1, false).main)
    }

    @Test fun aantalBijEenSoort() {
        val c = FilterTexts.count(f(FilterKind.FILMS, "Drama"), films = 29, series = 0, capped = true)
        assertEquals("29 films", c.main)
        assertEquals("de bekendste", c.detail)
    }

    @Test fun beginstandZegtPopulairsteEerst() {
        val c = FilterTexts.count(BiosFilter(), films = 81, series = 39, capped = false)
        assertEquals("81 films, 39 series · populairste eerst", c.detail)
    }

    @Test fun alleenFilmgenreStaatBijHetAantal() {
        val c = FilterTexts.count(f(FilterKind.ALL, "Horror"), films = 12, series = 0, capped = false)
        assertEquals("12 films", c.main)
        assertEquals("Horror is alleen een filmgenre", c.detail)
    }

    @Test fun uitlegLegeStaat() {
        val t = FilterTexts.emptyExplanation(f(FilterKind.ALL, "Horror", "Komedie", period = FilterPeriod.CLASSIC, score = 8.0))
        assertEquals(
            "Er zijn in je aanbod geen titels die Horror én Komedie zijn, van vóór 1980 en met een score van 8 of hoger. " +
                "Horror is alleen een filmgenre, dus je zoekt nu alleen in films.",
            t,
        )
    }

    @Test fun uitlegZonderGenres() {
        assertEquals(
            "Er zijn in je aanbod geen series uit de jaren 90 en met een score van 7 of hoger.",
            FilterTexts.emptyExplanation(f(FilterKind.SERIES, period = FilterPeriod.NINETIES, score = 7.0)),
        )
    }

    @Test fun uitlegDrieGenres() {
        assertEquals(
            "Er zijn in je aanbod geen films die Actie, Drama én Western zijn.",
            FilterTexts.emptyExplanation(f(FilterKind.FILMS, "Actie", "Drama", "Western")),
        )
    }

    @Test fun uitlegFilmgenreEnSeriegenre() {
        assertEquals(
            "Er zijn in je aanbod geen titels die Horror én Kids zijn. Horror is alleen een filmgenre en Kids alleen een seriegenre, dus samen vinden ze niets.",
            FilterTexts.emptyExplanation(f(FilterKind.ALL, "Horror", "Kids")),
        )
    }

    @Test fun voorstellenStrengsteFilterEerst() {
        val s = FilterTexts.suggestions(f(FilterKind.ALL, "Horror", period = FilterPeriod.CLASSIC, score = 8.0))
        assertEquals(listOf(FilterTexts.Fix.SCORE, FilterTexts.Fix.PERIOD, FilterTexts.Fix.CLEAR), s)
        assertEquals("Score naar Alles", FilterTexts.Fix.SCORE.label)
    }

    @Test fun voorstellenAlleenGenres() {
        assertEquals(listOf(FilterTexts.Fix.GENRES, FilterTexts.Fix.CLEAR), FilterTexts.suggestions(f(FilterKind.FILMS, "Western", "Kids")))
    }

    @Test fun tegenstrijdigeGenresEerstGenresWissen() {
        val s = FilterTexts.suggestions(f(FilterKind.ALL, "Horror", "Kids", score = 6.0))
        assertEquals(FilterTexts.Fix.GENRES, s.first())
    }

    @Test fun toepassenVanEenVoorstel() {
        val start = f(FilterKind.SERIES, "Drama", period = FilterPeriod.NOW, score = 8.0)
        assertEquals(0.0, FilterTexts.Fix.SCORE.apply(start).minScore, 0.0)
        assertEquals(FilterPeriod.ALL, FilterTexts.Fix.PERIOD.apply(start).period)
        assertEquals(emptySet<String>(), FilterTexts.Fix.GENRES.apply(start).genres)
        assertEquals(BiosFilter(), FilterTexts.Fix.CLEAR.apply(start))
    }
}
