package app.bada

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class SpokenTest {
    private fun at(h: Int) = LocalDateTime.of(2026, 11, 3, h, 30).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test fun defaultTitles() {
        assertEquals("Corsa mattutina", Spoken.defaultTitle(true, at(7), ZoneOffset.UTC))
        assertEquals("Corsa pomeridiana", Spoken.defaultTitle(true, at(14), ZoneOffset.UTC))
        assertEquals("Giro in bici serale", Spoken.defaultTitle(false, at(19), ZoneOffset.UTC))
        assertEquals("Corsa notturna", Spoken.defaultTitle(true, at(23), ZoneOffset.UTC))
        assertEquals("Corsa notturna", Spoken.defaultTitle(true, at(3), ZoneOffset.UTC))
    }

    @Test fun phrases() {
        assertEquals("1 ora e 5 minuti", Spoken.duration(3_900_000))
        assertEquals("25 minuti e 1 secondo", Spoken.duration(1_501_000))
        assertEquals("5 e 24 al chilometro", Spoken.pace(324_000))
        assertEquals("Un chilometro. Tempo 5 minuti e 24 secondi. Ultimo chilometro 5 e 24 al chilometro.",
            Spoken.split(true, 1, 324_000, 1000.0, 324_000))
        assertEquals("10 chilometri. Tempo 25 minuti. Velocità media 24,0 chilometri orari.",
            Spoken.split(false, 10, 1_500_000, 10_000.0, null))
    }

    @Test fun metaRoundTripAndDefaults() {
        val m = Meta("Lungo domenicale: 18 km", "Gambe pesanti\nscarpe nuove = ok")
        assertEquals(m, Meta.parse(m.format()))
        assertEquals(Meta(), Meta.parse(""))
    }
}
