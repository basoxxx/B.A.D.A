package app.bada

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorsAndSplitsTest {
    // 0.0001° di latitudine ≈ 11.1 m
    private fun run(n: Int, step: Long = 3000L, alt: (Int) -> Double? = { null }) =
        listOf<Event>(Mark("start", 0)) + (0..n).map { Fix(45.0 + it * 0.0001, 9.0, it * step, alt(it)) }

    @Test fun piecesAreContinuousAndCarrySpeed() {
        val p = Track.pieces(run(300), pieces = 30)
        assertTrue(p.size in 25..35)
        p.zipWithNext().forEach { (a, b) -> assertEquals(a.points.last(), b.points.first()) }
        p.forEach { assertEquals(3.7, it.mps, 0.1) } // 11.12 m ogni 3 s
    }

    @Test fun piecesBreakAtPauses() {
        val ev = run(50) + Mark("pause", 200_000) + Mark("resume", 300_000) +
            (60..100).map { Fix(45.0 + it * 0.0001, 9.0, 300_000 + it * 3000L) }
        val p = Track.pieces(ev, pieces = 10)
        // nessun pezzo attraversa la pausa
        assertTrue(p.none { piece -> piece.points.any { it.t < 200_000 } && piece.points.any { it.t >= 300_000 } })
    }

    @Test fun splitClimbFollowsAltitude() {
        // sale di 1 m ogni punto (~11 m): ~90 m per km
        val s = Track.splits(run(200) { 100.0 + it })
        assertEquals(90.0, s[0].climb!!, 3.0)
        assertNull(Track.splits(run(200))[0].climb)
    }

    @Test fun colorScaleIgnoresOutliers() {
        val t = PaceColors.normalize(listOf(3.0, 3.0, 3.1, 3.2, 3.3, 3.4, 3.5, 3.5, 3.6, 3.6, 0.1, 20.0))
        assertEquals(0f, t[10], 0f) // semaforo: il più lento, ma non schiaccia la scala
        assertEquals(1f, t[11], 0f)
        assertTrue(t[4] in 0.2f..0.8f)
        assertEquals(PaceColors.color(0f), 0xFF4FC3F7.toInt())
        assertEquals(PaceColors.color(1f), 0xFFFF3D00.toInt())
    }

    @Test fun recordCountsPerWorkout() {
        fun w(f: String, run: Boolean, best: Map<Int, Long>) = Workout(f, run, 0, f, 1.0, 1, best)
        val acts = listOf(
            w("a", true, mapOf(1000 to 240_000L, 5000 to 1_400_000L)),
            w("b", true, mapOf(1000 to 250_000L, 5000 to 1_300_000L, 10000 to 2_800_000L)),
            w("c", false, mapOf(5000 to 600_000L)),
        )
        assertEquals(mapOf("a" to 1, "b" to 2, "c" to 1), Stats.recordCounts(acts))
        assertEquals(listOf("5 km", "10 km"), Stats.recordsOf(acts, acts[1]))
    }
}
