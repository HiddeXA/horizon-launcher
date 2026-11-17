package nl.thuis.launcher.bios

import org.junit.Assert.assertEquals
import org.junit.Test

class FilterSwitchTest {
    @Test fun naarSeriesBlijvenGedeeldeGenresStaan() {
        // Actie (28) en Horror (27) kennen series niet; Komedie (35) en Misdaad (80) wel.
        assertEquals(setOf(TvGenre.COMEDY, TvGenre.CRIME), GenreCarry.toSeries(setOf(28, 35, 27, 80)))
    }

    @Test fun naarFilmsVervaltWatFilmsNietKennen() {
        val filmIds = setOf(28, 35, 80, 18, 27)
        val tv = setOf(TvGenre.SCIFI, TvGenre.DRAMA, TvGenre.KIDS).map { it.id }
        assertEquals(setOf(18), GenreCarry.keep(tv, filmIds))
    }

    @Test fun niksGekozenBlijftNiks() = assertEquals(emptySet<TvGenre>(), GenreCarry.toSeries(emptySet()))
}
