package nl.thuis.launcher.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelNamesTest {
    @Test fun zenders() {
        assertEquals("NL" to "NPO 1 HD", ChannelNames.split("┃NL┃ NPO 1 HD  ⏺ʳᵃʷ"))
        assertEquals("BE" to "VRT CANVAS HD", ChannelNames.split("┃BE┃ VRT CANVAS HD"))
        assertEquals("BE" to "TRENDS Z HD (NL)", ChannelNames.split("┃BE┃ TRENDS Z HD (NL)"))
        assertEquals("UK" to "Sky Sports", ChannelNames.split("UK: Sky Sports"))
        assertEquals(null to "Discovery", ChannelNames.split("Discovery"))
    }
    @Test fun categorieen() {
        assertEquals("NL" to "Nederland HD", ChannelNames.category("┃NL┃ NEDERLAND HD"))
        assertEquals("NL" to "Viaplay Premier League", ChannelNames.category("┃NL┃ VIAPLAY PREMIER LEAGUE"))
        assertEquals("PPV" to "UFC PPV", ChannelNames.category("┃PPV┃ UFC PPV"))
        assertEquals(null to "Terugkijken", ChannelNames.category("TERUGKIJKEN ⏺"))
        assertEquals("NL" to "Jeugd \u00B7 Baby", ChannelNames.category("\u2503NL\u2503 JEUGD | BABY"))
        assertEquals("VRT Canvas HD", ChannelNames.titleCase("VRT CANVAS HD"))
        assertEquals("NL" to "F1 TV Pro", ChannelNames.category("┃NL┃ F1 TV PRO"))
    }
}
