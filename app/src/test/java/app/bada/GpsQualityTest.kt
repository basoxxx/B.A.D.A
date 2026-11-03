package app.bada

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsQualityTest {
    @Test fun acceptance() {
        assertTrue(GpsQuality.accept(8f, 1000))
        assertTrue(GpsQuality.accept(25f, null))
        assertFalse(GpsQuality.accept(35f, 1000))   // punto impreciso in mezzo a punti buoni: si scarta
        assertTrue(GpsQuality.accept(35f, 20_000))  // dopo 20 s senza punti: meglio impreciso che niente
        assertFalse(GpsQuality.accept(80f, 60_000))
        assertFalse(GpsQuality.accept(35f, null))
    }

    @Test fun reportFindsLongestGapIgnoringPauses() {
        val ev = listOf(
            Mark("start", 0), Fix(45.0, 9.0, 1000), Fix(45.0, 9.0, 2000),
            Fix(45.0, 9.0, 242_000),                     // buco di 4 minuti: il telefono non ha dato punti
            Mark("pause", 243_000), Mark("resume", 900_000),
            Fix(45.0, 9.0, 901_000), Fix(45.0, 9.0, 902_000), Mark("stop", 903_000),
        )
        val r = GpsQuality.report(ev)
        assertEquals(5, r.points)
        assertEquals(240_000, r.longestGapMs)
        assertEquals(2000L, r.gapStart)
        assertEquals((1000 + 240_000 + 1000) / 3L, r.avgIntervalMs) // la pausa non conta come buco
    }
}
