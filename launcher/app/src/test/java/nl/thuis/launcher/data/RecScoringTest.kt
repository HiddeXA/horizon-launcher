package nl.thuis.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecScoringTest {

    private fun movie(id: Int, genres: List<Int>, vote: Double = 7.0, year: String = "2024") =
        Movie(id, "Film $id", year, "", "/p$id.jpg", null, genres, vote, 10.0)

    private val ACTION = 28
    private val DRAMA = 18
    private val HORROR = 27

    @Test
    fun zonderLikes_mixVanTrendingEnKlassiekers() {
        val trending = (1..20).map { movie(it, listOf(ACTION)) }
        val classics = (101..120).map { movie(it, listOf(DRAMA), vote = 8.5, year = "1994") }
        val result = RecScoring.rank(trending, classics, emptyMap(), emptyList(), emptyMap())

        assertEquals(20, result.size)
        assertTrue(result.count { it.id > 100 } >= 5)
        assertTrue(result.any { it.reason == "Populair deze week" })
        assertTrue(result.any { it.reason == "Klassieker uit 1994" })
    }

    @Test
    fun gelikteFilmsKomenNietTerug() {
        val liked = movie(1, listOf(ACTION))
        val result = RecScoring.rank(listOf(liked, movie(2, listOf(ACTION))), emptyList(), emptyMap(), listOf(liked), emptyMap())
        assertFalse(result.any { it.id == 1 })
    }

    @Test
    fun likesStuurGenreEnLijktOp() {
        val liked = listOf(movie(900, listOf(HORROR)), movie(901, listOf(HORROR, DRAMA)))
        val trending = (1..10).map { movie(it, listOf(ACTION), vote = 7.5) }
        val horror = movie(50, listOf(HORROR), vote = 6.8)
        val similar = mapOf(liked[0] to listOf(movie(60, listOf(HORROR), vote = 7.0)))
        val result = RecScoring.rank(
            trending + horror, emptyList(), similar, liked,
            genreNames = mapOf(HORROR to "Horror"), size = 5, minClassics = 0,
        )

        assertEquals(60, result[0].id)
        assertEquals("Omdat je Film 900 leuk vond", result[0].reason)
        assertTrue(result.any { it.id == 50 && it.reason == "Past bij je smaak: horror" })
    }

    @Test
    fun filmsZonderPosterWordenOvergeslagen() {
        val noPoster = movie(5, listOf(ACTION)).copy(posterPath = null)
        val result = RecScoring.rank(listOf(noPoster), emptyList(), emptyMap(), emptyList(), emptyMap())
        assertTrue(result.isEmpty())
    }
}

class MixedRecsTest {
    private fun m(id: Int, genres: List<Int>, vote: Double = 7.0, series: Boolean = false) =
        Movie(id, "Titel $id", "2024", "", "/p$id.jpg", null, genres, vote, 10.0, isSeries = series)

    @Test
    fun filmsEnSeriesDoorElkaarOpScore() {
        val films = listOf(m(1, listOf(28)) to 0.9, m(2, listOf(28)) to 0.5)
        val series = listOf(m(1, listOf(18), series = true) to 0.7, m(3, listOf(18), series = true) to 0.2)
        val r = RecScoring.mix(films, series)
        assertEquals(listOf("m1", "tv1", "m2", "tv3"), r.map { it.key })
    }

    @Test
    fun filmEnSerieMetHetzelfdeNummerZijnVerschillend() {
        assertFalse(m(5, emptyList()).key == m(5, emptyList(), series = true).key)
    }

    @Test
    fun serieLikesSturenAlleenSeries() {
        // Een geliketaste serie met genre Drama: de films worden gerangschikt met alleen de film-likes.
        val liked = listOf(m(900, listOf(18), series = true), m(901, listOf(28)))
        val (films, series) = RecScoring.splitLikes(liked)
        assertEquals(listOf(901), films.map { it.id })
        assertEquals(listOf(900), series.map { it.id })
    }

    @Test
    fun rankScoredGeeftDezelfdeVolgordeAlsRank() {
        val trending = (1..12).map { m(it, listOf(28), vote = 5.0 + it / 10.0) }
        val a = RecScoring.rank(trending, emptyList(), emptyMap(), emptyList(), emptyMap(), size = 8)
        val b = RecScoring.rankScored(trending, emptyList(), emptyMap(), emptyList(), emptyMap(), size = 8)
        assertEquals(a.map { it.id }, b.map { it.first.id })
        assertTrue(b.zipWithNext().all { (x, y) -> x.second >= y.second })
    }
}
