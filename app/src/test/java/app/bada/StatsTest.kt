package app.bada

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StatsTest {
    private val utc = ZoneOffset.UTC
    private fun w(name: String, start: Long, m: Double, ms: Long, best: Map<Int, Long> = emptyMap(), run: Boolean = true) =
        Workout(name, run, start, name, m, ms, best)

    @Test fun periodStarts() {
        val wed = LocalDate.of(2026, 11, 4) // mercoledì
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

    @Test fun weeksBuckets() {
        val today = LocalDate.of(2026, 11, 4)
        fun at(d: LocalDate) = d.atStartOfDay(utc).toInstant().toEpochMilli() + 3_600_000
        val acts = listOf(
            w("questa", at(LocalDate.of(2026, 11, 2)), 5000.0, 1),
            w("questa2", at(LocalDate.of(2026, 11, 4)), 3000.0, 1),
            w("scorsa", at(LocalDate.of(2026, 11, 1)), 10000.0, 1), // domenica: settimana precedente
            w("vecchia", at(LocalDate.of(2025, 1, 1)), 99000.0, 1), // fuori dalle 12 settimane
        )
        val weeks = Stats.weeks(acts, 12, today, utc)
        assertEquals(12, weeks.size)
        assertEquals(LocalDate.of(2026, 11, 2), weeks.last().first)
        assertEquals(8000.0, weeks.last().second, 0.0)
        assertEquals(10000.0, weeks[10].second, 0.0)
        assertEquals(18000.0, weeks.sumOf { it.second }, 0.0)
    }

    @Test fun records() {
        val short = w("sprint", 1, 400.0, 60_000) // velocissima ma sotto il km: non è record di media
        val long = w("lunga", 2, 21_100.0, 7_200_000)
        val fast = w("veloce", 3, 10_000.0, 2_700_000)
        val acts = listOf(short, long, fast)
        assertEquals("lunga", Stats.longest(acts)!!.workout.file)
        assertEquals("lunga", Stats.longestTime(acts)!!.workout.file)
        assertEquals("veloce", Stats.bestAverage(acts)!!.workout.file)
        assertEquals(10_000 / 2700.0, Stats.bestAverage(acts)!!.value, 1e-9)
    }

    @Test fun podiumAndMedals() {
        val a = w("a", 1, 5000.0, 1, mapOf(5000 to 1_500_000L))
        val b = w("b", 2, 5000.0, 1, mapOf(5000 to 1_400_000L))
        val c = w("c", 3, 5000.0, 1, mapOf(5000 to 1_600_000L))
        val d = w("d", 4, 5000.0, 1, mapOf(5000 to 1_700_000L))
        val e = w("e", 5, 3000.0, 1) // non ha corso 5 km
        val acts = listOf(a, b, c, d, e)
        assertEquals(listOf("b", "a", "c"), Stats.podium(acts, 5000).map { it.workout.file })
        assertEquals(1, Stats.medal(acts, b, 5000))
        assertEquals(3, Stats.medal(acts, c, 5000))
        assertNull(Stats.medal(acts, d, 5000))
        assertNull(Stats.medal(acts, e, 5000))
    }

    @Test fun noRecordsWithoutData() {
        assertNull(Stats.longest(emptyList()))
        assertNull(Stats.bestAverage(listOf(w("x", 1, 500.0, 100_000))))
        assertEquals(emptyList<Record>(), Stats.podium(listOf(w("x", 1, 500.0, 100_000)), 1000))
    }

    @Test fun workoutFromEvents() {
        // ~2.2 km in linea retta a velocità costante
        val ev = listOf<Event>(Mark("start", 0)) + (0..200).map { Fix(45.0 + it * 0.0001, 9.0, it * 3000L) } + Mark("stop", 600_000)
        val a = Workout.of("f", true, 0, "t", ev)
        assertEquals(2224.0, a.m, 5.0)
        assertEquals(600_000, a.ms)
        assertEquals(269_800.0, a.best.getValue(1000).toDouble(), 2_000.0)
        assertNull(a.best[5000])
        assertEquals(1, a.thumb.size)
    }
}
