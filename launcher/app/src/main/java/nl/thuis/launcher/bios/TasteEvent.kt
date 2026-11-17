package nl.thuis.launcher.bios

/** Eén keer iets met een film of serie gedaan: gekeken (gewicht naar minuten), in je lijst gezet, of geopend. */
data class TasteEvent(val vodId: Int, val genres: List<Int>, val year: Int?, val weight: Double, val time: Long)
