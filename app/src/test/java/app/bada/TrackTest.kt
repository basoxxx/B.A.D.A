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
        val ev = Track.parse(listOf("start,1000", "45.0,9.0,2000", "45.0001,9.0", "45.00", ""))
        assertEquals(listOf(Mark("start", 1000), Fix(45.0, 9.0, 2000)), ev)
    }

    @Test fun lineRoundTrips() {
        val ev = listOf(Mark("start", 1), Fix(45.123456, 9.654321, 2), Mark("stop", 3))
        assertEquals(ev, Track.parse(ev.map(Track::line)))
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
