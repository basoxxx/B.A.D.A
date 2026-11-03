package app.bada

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StatsTest {
    private fun w(name: String, start: Long, m: Double, ms: Long, km: Long? = null, run: Boolean = true) =
        Workout(name, run, start, m, ms, km)

    @Test fun periodStarts() {
        val wed = LocalDate.of(2026, 11, 4) // mercoledì
        val utc = ZoneOffset.UTC
        assertEquals(LocalDate.of(2026, 11, 2).atStartOfDay(utc).toInstant().toEpochMilli(), Stats.since(Stats.Period.WEEK, wed, utc))
        assertEquals(LocalDate.of(2026, 11, 1).atStartOfDay(utc).toInstant().toEpochMilli(), Stats.since(Stats.Period.MONTH, wed, utc))
        assertEquals(LocalDate.of(2026, 1, 1).atStartOfDay(utc).toInstant().toEpochMilli(), Stats.since(Stats.Period.YEAR, wed, utc))
        assertEquals(0L, Stats.since(Stats.Period.ALL, wed, utc))
    }

    @Test fun totalsOnlyFromStart() {
        val acts = listOf(w("a", 100, 5000.0, 1_500_000), w("b", 200, 3000.0, 900_000), w("c", 50, 1000.0, 1))
        assertEquals(Totals(2, 8000.0, 2_400_000), Stats.totals(acts, from = 100))
        assertEquals(3, Stats.totals(acts).count)
    }

    @Test fun records() {
        val short = w("sprint", 1, 400.0, 60_000) // velocissima ma sotto il km: non è record di media
        val long = w("lunga", 2, 21_100.0, 7_200_000, km = 320_000)
        val fast = w("veloce", 3, 10_000.0, 2_700_000, km = 250_000)
        val acts = listOf(short, long, fast)
        assertEquals("lunga", Stats.longest(acts)!!.workout.file)
        assertEquals("lunga", Stats.longestTime(acts)!!.workout.file)
        assertEquals("veloce", Stats.bestAverage(acts)!!.workout.file)
        assertEquals(10_000 / 2700.0, Stats.bestAverage(acts)!!.value, 1e-9)
        assertEquals(250_000.0, Stats.fastestKm(acts)!!.value, 0.0)
    }

    @Test fun noRecordsWithoutData() {
        assertNull(Stats.longest(emptyList()))
        assertNull(Stats.bestAverage(listOf(w("x", 1, 500.0, 100_000))))
        assertNull(Stats.fastestKm(listOf(w("x", 1, 500.0, 100_000))))
    }

    @Test fun workoutFromEvents() {
        // ~2.2 km in linea retta a velocità costante: un km intero più veloce
        val ev = listOf<Event>(Mark("start", 0)) + (0..200).map { Fix(45.0 + it * 0.0001, 9.0, it * 3000L) } + Mark("stop", 600_000)
        val a = Workout.of("f", true, 0, ev)
        assertEquals(2224.0, a.m, 5.0)
        assertEquals(600_000, a.ms)
        assertEquals(269_800.0, a.fastestKmMs!!.toDouble(), 2_000.0)
    }
}
