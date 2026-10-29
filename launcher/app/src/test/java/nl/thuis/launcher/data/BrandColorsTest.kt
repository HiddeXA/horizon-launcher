package nl.thuis.launcher.data

import nl.thuis.launcher.ui.BrandColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandColorsTest {
    private fun img(w: Int, h: Int, bg: Int, logo: Int, logoFrac: Double = 0.2): IntArray {
        val p = IntArray(w * h) { bg }
        val lw = (w * logoFrac).toInt().coerceAtLeast(1)
        for (y in h / 3 until 2 * h / 3) for (x in w / 2 - lw / 2 until w / 2 + lw / 2) p[y * w + x] = logo
        return p
    }
    private fun close(a: Int, b: Int) = listOf(16, 8, 0).all { s -> kotlin.math.abs(((a shr s) and 0xFF) - ((b shr s) and 0xFF)) <= 16 }

    @Test fun egaleMerkkleurOpDeRandWint() {
        val disneyBlue = 0xFF0B3C5D.toInt()
        val c = BrandColors.from(img(48, 27, disneyBlue, 0xFFFFFFFF.toInt()), 48, 27)
        assertTrue("base was ${Integer.toHexString(c.base)}", close(c.base, disneyBlue))
    }

    @Test fun witteRandGebruiktLogoKleur() {
        val red = 0xFFE50914.toInt()
        val c = BrandColors.from(img(48, 27, 0xFFFFFFFF.toInt(), red, 0.4), 48, 27)
        assertTrue("base was ${Integer.toHexString(c.base)}", close(c.base, red))
        assertTrue(close(c.glow, red))
    }

    @Test fun helemaalGrijsGeeftNeutraleRand() {
        val grey = 0xFF555555.toInt()
        val c = BrandColors.from(IntArray(48 * 27) { grey }, 48, 27)
        assertTrue(close(c.base, grey))
    }

    @Test fun legeAfbeeldingIsNeutraal() {
        assertEquals(BrandColors.Neutral, BrandColors.from(IntArray(0), 0, 0))
        assertEquals(BrandColors.Neutral, BrandColors.from(IntArray(4) { 0 }, 2, 2))
    }
}
