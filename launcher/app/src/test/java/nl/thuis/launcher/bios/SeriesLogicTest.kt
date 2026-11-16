package nl.thuis.launcher.bios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesLogicTest {
    private fun ep(id: Int, season: Int, number: Int) = Episode(id, season, number, "Aflevering $number", "mkv", 2880, null, null, 0L)

    /** Twee gewone seizoenen (S1: 3 afleveringen, S2: 2, met een gat: A1 en A3) en één special. */
    private val seasons = EpisodeOrder.seasons(
        listOf(ep(90, 0, 1), ep(23, 2, 3), ep(21, 2, 1), ep(13, 1, 3), ep(11, 1, 1), ep(12, 1, 2)),
    )

    private fun watched(vararg ids: Int) = ids.associate { it to EpState(0, 2_880_000, true, it.toLong()) }

    // --- Volgorde ---

    @Test fun seizoenenOpVolgordeSpecialsAchteraan() {
        assertEquals(listOf(1, 2, 0), seasons.map { it.number })
        assertEquals(listOf(11, 12, 13), seasons[0].episodes.map { it.id })
        assertEquals("Seizoen 2", seasons[1].label)
        assertEquals("Specials", seasons[2].label)
    }

    @Test fun volgendeBinnenSeizoen() = assertEquals(12, EpisodeOrder.next(seasons, 11)?.id)

    @Test fun volgendeNaarVolgendSeizoen() = assertEquals(21, EpisodeOrder.next(seasons, 13)?.id)

    @Test fun gatInNummersSlaatOver() = assertEquals(23, EpisodeOrder.next(seasons, 21)?.id)

    @Test fun laatsteGewoneAfleveringGaatNietNaarSpecials() = assertNull(EpisodeOrder.next(seasons, 23))

    @Test fun naLaatsteSpecialNiets() = assertNull(EpisodeOrder.next(seasons, 90))

    @Test fun onbekendeAfleveringGeeftNiets() = assertNull(EpisodeOrder.next(seasons, 999))

    @Test fun labels() {
        assertEquals("S2 · A3", ep(23, 2, 3).label)
        assertEquals("Special 1", ep(90, 0, 1).label)
    }

    @Test fun titelZonderNaam() {
        assertEquals("Aflevering 4", Episode.titleOr("", 4))
        assertEquals("Aflevering 4", Episode.titleOr("  ", 4))
        assertEquals("De terugkeer", Episode.titleOr("De terugkeer", 4))
    }

    // --- Waar je was ---

    @Test fun nogNietGekekenBegintBijS1A1() {
        val n = NextUp.resolve(seasons, emptyMap(), null)!!
        assertEquals(11, n.episode.id)
        assertEquals(NextUp.Kind.START, n.kind)
        assertEquals("Afspelen S1 · A1", n.buttonLabel)
        assertEquals(0L, n.resumeMs)
        assertFalse(n.finished)
    }

    @Test fun middenInAfleveringGaatVerder() {
        val states = watched(11) + (12 to EpState(754_000, 2_880_000, false, 50))
        val n = NextUp.resolve(seasons, states, 12)!!
        assertEquals(12, n.episode.id)
        assertEquals(NextUp.Kind.RESUME, n.kind)
        assertEquals("Verder kijken S1 · A2", n.buttonLabel)
        assertEquals(754_000L, n.resumeMs)
    }

    @Test fun uitgekekenWijstNaarVolgende() {
        val n = NextUp.resolve(seasons, watched(11, 12, 13), 13)!!
        assertEquals(21, n.episode.id)
        assertEquals(NextUp.Kind.NEXT, n.kind)
        assertEquals("Volgende aflevering S2 · A1", n.buttonLabel)
        assertEquals(0L, n.resumeMs)
    }

    @Test fun volgendeSlaatAlGezieneOver() {
        // A1 gezien, A2 met de hand op gezien gezet: verder met A3.
        val n = NextUp.resolve(seasons, watched(11, 12), 11)!!
        assertEquals(13, n.episode.id)
    }

    @Test fun allesGezienIsKlaarEnBiedtBeginOpnieuwAan() {
        val n = NextUp.resolve(seasons, watched(11, 12, 13, 21, 23), 23)!!
        assertTrue(n.finished)
        assertEquals(11, n.episode.id)
        assertEquals("Afspelen S1 · A1", n.buttonLabel)
    }

    @Test fun zonderLaatstBekekenTeltVersteGeziene() {
        // Heel seizoen 1 met de hand op gezien gezet, nooit iets afgespeeld.
        val n = NextUp.resolve(seasons, watched(11, 12, 13), null)!!
        assertEquals(21, n.episode.id)
        assertEquals(NextUp.Kind.NEXT, n.kind)
    }

    @Test fun laatstBekekenBestaatNietMeer() {
        // De aflevering is weg bij de aanbieder: eerstvolgende niet-geziene.
        val n = NextUp.resolve(seasons, watched(11) + (555 to EpState(100_000, 2_880_000, false, 99)), 555)!!
        assertEquals(12, n.episode.id)
    }

    @Test fun serieZonderAfleveringen() = assertNull(NextUp.resolve(emptyList(), emptyMap(), null))

    @Test fun alleenSpecialsBegintBijEersteSpecial() {
        val only = EpisodeOrder.seasons(listOf(ep(91, 0, 2), ep(90, 0, 1)))
        assertEquals(90, NextUp.resolve(only, emptyMap(), null)!!.episode.id)
    }

    // --- Verder kijken ---

    @Test fun inVerderKijkenVanaf30Seconden() {
        assertFalse(NextUp.inContinue(seasons, emptyMap(), null))
        assertFalse(NextUp.inContinue(seasons, mapOf(11 to EpState(20_000, 2_880_000, false, 1)), 11))
        assertTrue(NextUp.inContinue(seasons, mapOf(11 to EpState(31_000, 2_880_000, false, 1)), 11))
        assertTrue(NextUp.inContinue(seasons, watched(11), 11))
    }

    @Test fun uitgekekenSerieNietInVerderKijken() =
        assertFalse(NextUp.inContinue(seasons, watched(11, 12, 13, 21, 23), 23))

    @Test fun nieuweAfleveringBrengtSerieTerug() {
        val more = EpisodeOrder.seasons(seasons.flatMap { it.episodes } + ep(24, 2, 4))
        val states = watched(11, 12, 13, 21, 23)
        assertTrue(NextUp.inContinue(more, states, 23))
        assertEquals(24, NextUp.resolve(more, states, 23)!!.episode.id)
    }

    // --- Gezien ---

    @Test fun gezienBijMeerDan95Procent() {
        assertTrue(Watched.reached(2_750_000, 2_880_000))
        assertFalse(Watched.reached(2_700_000, 2_880_000))
        assertFalse(Watched.reached(100, 0))
    }

    @Test fun seizoenGezienAlsAllesGezien() {
        assertTrue(Watched.seasonDone(seasons[0], watched(11, 12, 13)))
        assertFalse(Watched.seasonDone(seasons[0], watched(11, 12)))
        assertEquals(2, Watched.count(seasons[0], watched(11, 12)))
    }
}
