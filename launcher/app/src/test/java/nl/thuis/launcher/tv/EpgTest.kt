package nl.thuis.launcher.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class EpgTest {
    @Test fun xmltvTijd() {
        // 27-10-2026 17:10 Amsterdam (+0100) = 16:10 UTC
        assertEquals(java.time.Instant.parse("2026-10-27T16:10:00Z").epochSecond, XmltvTime.parse("20261027171000 +0100"))
        assertEquals(java.time.Instant.parse("2024-02-29T23:59:59Z").epochSecond, XmltvTime.parse("20240229235959 +0000"))
        assertEquals(java.time.Instant.parse("2000-03-01T05:00:00Z").epochSecond, XmltvTime.parse("20000301000000 -0500"))
        assertEquals(java.time.Instant.parse("2026-07-01T10:00:00Z").epochSecond, XmltvTime.parse("20260701120000 +0200"))
        assertEquals(null, XmltvTime.parse("2026"))
    }
    @Test fun gids() {
        val g = ChannelGuide(listOf(Programme(100, 200, "A"), Programme(200, 300, "B"), Programme(400, 500, "C")))
        assertEquals("A", g.at(150)?.title)
        assertEquals("B", g.at(200)?.title)
        assertEquals(null, g.at(350))
        assertEquals("C", g.next(350)?.title)
        assertEquals("B", g.next(150)?.title)
        assertEquals(listOf("A", "B"), g.between(150, 250).map { it.title })
        assertEquals(listOf("B", "C"), g.between(250, 450).map { it.title })
    }
}
