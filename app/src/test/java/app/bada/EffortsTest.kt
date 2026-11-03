package app.bada

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EffortsTest {
    @Test fun bestWindowIsFoundInTheMiddle() {
        // 3 km: primo km lento (6:00), secondo veloce (4:00), terzo lento (6:00)
        val p = listOf(Sample(0.0, 0, null), Sample(1000.0, 360_000, null), Sample(2000.0, 600_000, null), Sample(3000.0, 960_000, null))
        val best = Track.bestEfforts(p, listOf(1000, 2000, 5000))
        assertEquals(240_000L, best[1000])
        assertEquals(600_000L, best[2000]) // qualsiasi coppia di km consecutivi: 6+4 = 10 minuti
        assertNull(best[5000])
    }

    @Test fun interpolatesWindowStart() {
        // Ritmo costante 5:00/km campionato ogni 300 m: il km esatto deve venire 5:00.
        val p = (0..10).map { Sample(it * 300.0, it * 90_000L, null) }
        assertEquals(300_000.0, Track.bestEfforts(p, listOf(1000)).getValue(1000).toDouble(), 1.0)
    }

    @Test fun pausesDoNotCount() {
        // Pausa a metà: profile non aggiunge tempo tra i segmenti.
        val ev = listOf<Event>(Mark("start", 0)) +
            (0..50).map { Fix(45.0 + it * 0.0001, 9.0, it * 3000L) } +
            Mark("pause", 150_000) + Mark("resume", 1_000_000) +
            (50..100).map { Fix(45.0 + it * 0.0001, 9.0, 1_000_000 + (it - 50) * 3000L) }
        val best = Track.bestEfforts(ev, listOf(1000))
        assertEquals(269_800.0, best.getValue(1000).toDouble(), 2_000.0)
    }

    @Test fun recentSpeed() {
        val ev = listOf<Event>(Mark("start", 0)) + (0..60).map { Fix(45.0 + it * 0.0001, 9.0, it * 1000L) }
        assertEquals(11.1, Track.recentMps(ev)!!, 0.2)
        assertNull(Track.recentMps(ev.take(5)))
    }
}
