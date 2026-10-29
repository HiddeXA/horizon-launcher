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
