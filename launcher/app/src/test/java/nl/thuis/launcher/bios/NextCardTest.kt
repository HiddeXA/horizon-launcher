package nl.thuis.launcher.bios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextCardTest {
    private val dur = 2_880_000L // 48 minuten

    @Test fun geenKaartVoorDeLaatste10Seconden() {
        val c = NextCard()
        assertNull(c.secondsLeft(dur - 10_001, dur, hasNext = true, nowMs = 0))
        assertNull(c.secondsLeft(1_000, dur, hasNext = true, nowMs = 0))
    }

    @Test fun kaartInDeLaatste10SecondenMetAftelling() {
        val c = NextCard()
        assertEquals(10, c.secondsLeft(dur - 10_000, dur, true, 0))
        assertEquals(8, c.secondsLeft(dur - 7_400, dur, true, 0))
        assertEquals(1, c.secondsLeft(dur - 200, dur, true, 0))
        assertEquals(0, c.secondsLeft(dur, dur, true, 0))
    }

    @Test fun geenKaartBijLaatsteAflevering() = assertNull(NextCard().secondsLeft(dur - 5_000, dur, hasNext = false, nowMs = 0))

    @Test fun pauzeHoudtAftellingStil() {
        // Tijdens pauze verandert de positie niet: de aftelling blijft staan, hoe lang je ook wacht.
        val c = NextCard()
        assertEquals(6, c.secondsLeft(dur - 5_500, dur, true, nowMs = 0))
        assertEquals(6, c.secondsLeft(dur - 5_500, dur, true, nowMs = 60_000))
    }

    @Test fun annulerenVerbergtKaart() {
        val c = NextCard()
        assertEquals(5, c.secondsLeft(dur - 5_000, dur, true, 0))
        c.dismiss()
        assertTrue(c.dismissed)
        assertNull(c.secondsLeft(dur - 4_000, dur, true, 0))
        assertNull(c.secondsLeft(dur, dur, true, 0))
    }

    @Test fun terugspoelenVoorDeLaatste10SecondenBrengtKaartLaterTerug() {
        val c = NextCard()
        c.secondsLeft(dur - 5_000, dur, true, 0)
        c.dismiss()
        assertNull(c.secondsLeft(dur - 60_000, dur, true, 0)) // teruggespoeld
        assertFalse(c.dismissed)
        assertEquals(9, c.secondsLeft(dur - 9_000, dur, true, 0)) // komt weer
    }

    @Test fun duurOnbekendDanKaartNaAfloop() {
        val c = NextCard()
        assertNull(c.secondsLeft(500_000, 0, true, 1_000))
        c.endedWithoutDuration(nowMs = 1_000)
        assertEquals(10, c.secondsLeft(500_000, 0, true, 1_000))
        assertEquals(8, c.secondsLeft(500_000, 0, true, 3_500))
        assertFalse(c.expired(nowMs = 10_999))
        assertTrue(c.expired(nowMs = 11_000))
        assertEquals(0, c.secondsLeft(500_000, 0, true, 12_000))
    }

    @Test fun voortgangVanDeBalk() {
        assertEquals(0f, NextCard.fraction(10), 0.001f)
        assertEquals(0.2f, NextCard.fraction(8), 0.001f)
        assertEquals(1f, NextCard.fraction(0), 0.001f)
    }

    @Test fun resetVoorDeVolgendeAflevering() {
        val c = NextCard()
        c.endedWithoutDuration(0)
        c.dismiss()
        c.reset()
        assertFalse(c.dismissed)
        assertNull(c.secondsLeft(0, dur, true, 0))
    }
}
