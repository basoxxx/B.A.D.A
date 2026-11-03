package app.bada

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoPauseTest {
    // 0.00001° di latitudine ≈ 1.1 m
    private fun at(m: Double, t: Long) = Fix(45.0 + m / 111_195.0, 9.0, t)

    @Test fun runningNeverPauses() {
        val a = AutoPause(run = true)
        // 3 m/s per 2 minuti
        repeat(120) { assertEquals(AutoPause.Keep, a.onFix(at(it * 3.0, it * 1000L))) }
        assertFalse(a.paused)
    }

    @Test fun stopPausesFromStartOfStillWindowThenResumes() {
        val a = AutoPause(run = true)
        repeat(30) { a.onFix(at(it * 3.0, it * 1000L)) } // corre fino a 87 m, t = 29 s
        val decisions = (30..45).map { a.onFix(at(87.0 + (it % 2), it * 1000L)) } // fermo al semaforo con 1 m di rumore
        val pause = decisions.filterIsInstance<AutoPause.Pause>().single()
        assertTrue(pause.t in 26_000L..36_000L)
        assertTrue(a.paused)
        assertEquals(AutoPause.Ignore, a.onFix(at(91.0, 50_000))) // 4 m: ancora rumore
        assertEquals(AutoPause.Resume(60_000), a.onFix(at(100.0, 60_000)))
        assertFalse(a.paused)
    }

    @Test fun walkingDoesNotPauseRun() {
        val a = AutoPause(run = true)
        // 1.2 m/s: si cammina, non ci si ferma
        repeat(60) { assertEquals(AutoPause.Keep, a.onFix(at(it * 1.2, it * 1000L))) }
    }

    @Test fun bikeHasHigherThreshold() {
        val a = AutoPause(run = false)
        // bici a passo d'uomo (1 m/s): per la bici è fermo
        val d = (0..15).map { a.onFix(at(it * 1.0, it * 1000L)) }
        assertTrue(d.any { it is AutoPause.Pause })
    }
}
