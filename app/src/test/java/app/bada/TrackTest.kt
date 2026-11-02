package app.bada

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackTest {
    // 0.0001° di latitudine ≈ 11.1 m
    private fun fix(i: Int, t: Long) = Fix(45.0 + i * 0.0001, 9.0, t)

    @Test fun haversineOneDegreeLatitude() {
        assertEquals(111_195.0, Track.haversineM(Fix(0.0, 0.0, 0), Fix(1.0, 0.0, 0)), 50.0)
    }

    @Test fun parseSkipsBrokenLines() {
        val ev = Track.parse("start,1000\n45.0,9.0,2000\n45.0001,9.0\nbanana\n")
        assertEquals(listOf(Mark("start", 1000), Fix(45.0, 9.0, 2000)), ev)
    }

    @Test fun parseDropsTruncatedLastLine() {
        // crash a metà scrittura: "17300" sembrerebbe un tempo valido
        val ev = Track.parse("start,1000\n45.0,9.0,2000,120.5\n45.1,9.2,17300")
        assertEquals(listOf(Mark("start", 1000), Fix(45.0, 9.0, 2000, 120.5)), ev)
    }

    @Test fun lineRoundTrips() {
        val ev = listOf(Mark("start", 1), Fix(45.123456, 9.654321, 2, 312.4), Fix(45.2, 9.7, 3), Mark("stop", 4))
        assertEquals(ev, Track.parse(ev.joinToString("") { Track.line(it) + "\n" }))
    }

    @Test fun jitterDoesNotAddDistance() {
        val ev = mutableListOf<Event>(Mark("start", 0))
        // oscilla di ~1 m attorno allo stesso punto
        repeat(100) { ev.add(Fix(45.0 + (it % 2) * 0.00001, 9.0, it * 1000L)) }
        assertEquals(0.0, Track.distanceM(ev), 0.001)
    }

    @Test fun slowSmallStepsStillCount() {
        // passi da ~1.1 m: sotto soglia uno per uno, ma 100 passi ≈ 111 m
        val ev = listOf(Mark("start", 0)) + (0..100).map { Fix(45.0 + it * 0.00001, 9.0, it * 1000L) }
        assertEquals(111.0, Track.distanceM(ev), 3.0)
    }

    @Test fun pauseExcludedFromTimeAndDistance() {
        val ev = listOf(
            Mark("start", 0), fix(0, 0), fix(1, 10_000),
            Mark("pause", 10_000), Mark("resume", 70_000),
            fix(50, 70_000), fix(51, 80_000), Mark("stop", 80_000),
        )
        assertEquals(20_000, Track.activeMs(ev))
        assertEquals(2, Track.segments(ev).size)
        assertEquals(22.2, Track.distanceM(ev), 0.5) // il salto di 49 punti durante la pausa non conta
    }

    @Test fun splitsInterpolateKmBoundary() {
        // 19 passi da ~111.2 m ogni 30 s ≈ 2112.6 m in 570 s
        val ev = listOf(Mark("start", 0)) + (0..19).map { Fix(45.0 + it * 0.001, 9.0, it * 30_000L) }
        val s = Track.splits(ev)
        assertEquals(3, s.size)
        assertEquals(1000.0, s[0].m, 0.001)
        assertEquals(269_800.0, s[0].ms.toDouble(), 500.0) // 1000 / 111.2 * 30 s
        assertEquals(112.6, s[2].m, 1.0)
        assertEquals(570_000.0, s.sumOf { it.ms }.toDouble(), 2.0)
    }

    @Test fun splitsSkipPauseTime() {
        val ev = listOf(
            Mark("start", 0), fix(0, 0), fix(1, 10_000),
            Mark("pause", 10_000), Mark("resume", 70_000),
            fix(50, 70_000), fix(51, 80_000),
        )
        assertEquals(listOf(20_000L), Track.splits(ev).map { it.ms })
    }

    @Test fun chartConstantSpeedAndClimb() {
        // ~111.2 m ogni 30 s (3.71 m/s), sale di 2 m a punto
        val ev = listOf(Mark("start", 0)) + (0..19).map { Fix(45.0 + it * 0.001, 9.0, it * 30_000L, 100.0 + it * 2) }
        val c = Track.chart(ev, bins = 10)
        assertEquals(10, c.size)
        c.forEach { assertEquals(3.706, it.mps, 0.01) }
        assertEquals(38.0, Track.climbM(c), 8.0) // le medie per tratto tagliano un po' gli estremi
        assertEquals(true, c.first().alt!! < c.last().alt!!)
    }

    @Test fun chartIgnoresPauseTime() {
        val ev = listOf(Mark("start", 0)) + (0..10).map { fix(it * 9, it * 10_000L) } +
            listOf(Mark("pause", 100_000), Mark("resume", 400_000)) +
            (11..20).map { fix(it * 9, 400_000L + (it - 10) * 10_000L) }
        // la pausa (e il salto di 9 punti fermi) non crea un tratto lento
        Track.chart(ev, bins = 5).forEach { assertEquals(10.0, it.mps, 0.3) }
    }

    @Test fun chartEmptyUnder100m() = assertEquals(0, Track.chart(listOf(Mark("start", 0), fix(0, 0), fix(5, 5000))).size)

    @Test fun missingStopEndsAtLastEvent() {
        val ev = listOf(Mark("start", 0), fix(0, 1000), fix(1, 5000))
        assertEquals(5000, Track.activeMs(ev))
        assertEquals(9000, Track.activeMs(ev, now = 9000))
    }

    @Test fun pausedLiveDoesNotTick() {
        val ev = listOf(Mark("start", 0), Mark("pause", 3000))
        assertEquals(3000, Track.activeMs(ev, now = 60_000))
    }
}
