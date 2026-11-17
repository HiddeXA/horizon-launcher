package nl.thuis.launcher.bios

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewEpisodesTest {
    private fun serie(modified: Long) = SeriesItem(7, "Zoutwater", "Zoutwater", "2021", null, null, modified, "1", null)
    private fun sum(started: Boolean, finished: Boolean, seenModified: Long = 1_000, seenAt: Long = 1_000_000) =
        SeriesSummary(0, 0, "S1 · A2", 0f, finished, started, seenModified, seenAt)
    private fun ep(added: Long) = Episode(1, 1, 1, "x", "mkv", null, null, null, added)

    @Test fun nieuwBijGevolgdeSerieNaBijwerken() {
        assertTrue(NewEpisodes.hasNew(serie(2_000), sum(started = true, finished = false), inMyList = false))
        assertTrue(NewEpisodes.hasNew(serie(2_000), sum(started = false, finished = true), inMyList = true))
    }

    @Test fun nietNieuwZonderBijwerken() = assertFalse(NewEpisodes.hasNew(serie(1_000), sum(true, false), false))

    @Test fun nooitNieuwAlsJeDeSerieNietVolgt() {
        // Alleen het seriescherm geopend: niet gevolgd.
        assertFalse(NewEpisodes.hasNew(serie(2_000), sum(started = false, finished = true), inMyList = false))
        assertFalse(NewEpisodes.hasNew(serie(2_000), null, inMyList = false))
    }

    @Test fun afleveringNieuwNaVorigBezoek() {
        assertTrue(NewEpisodes.isNew(ep(added = 1_500), sinceMs = 1_000_000))
        assertFalse(NewEpisodes.isNew(ep(added = 900), sinceMs = 1_000_000))
        assertFalse(NewEpisodes.isNew(ep(added = 1_500), sinceMs = null))
        assertFalse(NewEpisodes.isNew(ep(added = 0), sinceMs = 1))
    }

    @Test fun uitgekekenSerieMetNieuweAfleveringTerugInVerderKijken() {
        assertTrue(NewEpisodes.inContinue(serie(2_000), sum(started = true, finished = true)))
        assertFalse(NewEpisodes.inContinue(serie(1_000), sum(started = true, finished = true)))
        assertTrue(NewEpisodes.inContinue(serie(1_000), sum(started = true, finished = false)))
        assertFalse(NewEpisodes.inContinue(serie(2_000), sum(started = false, finished = true)))
        assertFalse(NewEpisodes.inContinue(serie(2_000), null))
    }
}
