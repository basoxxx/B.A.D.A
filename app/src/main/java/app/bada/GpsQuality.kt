package app.bada

import android.content.Context
import android.os.Build
import android.os.PowerManager

/** Qualità del segnale: quali punti tenere, e quanto è affidabile un'uscita già registrata. */
object GpsQuality {
    /** Precisione massima di un punto normale. */
    const val GOOD_M = 25f
    /** Dopo un buco si accettano anche punti meno precisi: meglio un punto a 40 m che una linea dritta di un km. */
    const val AFTER_GAP_M = 50f
    const val GAP_MS = 15_000L
    /** Sopra questo buco l'uscita viene segnalata nel dettaglio. */
    const val WARN_GAP_MS = 60_000L

    fun accept(accuracyM: Float, msSinceLastKept: Long?): Boolean =
        accuracyM <= GOOD_M || (msSinceLastKept != null && msSinceLastKept >= GAP_MS && accuracyM <= AFTER_GAP_M)

    data class Report(val points: Int, val avgIntervalMs: Long, val longestGapMs: Long, val gapStart: Long?)

    /** Punti, intervallo medio e buco più lungo tra due punti mentre si registrava (le pause non contano). */
    fun report(events: List<Event>): Report {
        var points = 0
        var gaps = 0L; var gapsN = 0
        var longest = 0L; var start: Long? = null
        var last: Fix? = null
        for (e in events) {
            when {
                e is Fix -> {
                    points++
                    last?.let { val g = e.t - it.t; gaps += g; gapsN++; if (g > longest) { longest = g; start = it.t } }
                    last = e
                }
                e is Mark && e.kind == "pause" -> last = null
            }
        }
        return Report(points, if (gapsN > 0) gaps / gapsN else 0, longest, start)
    }

    /**
     * Il risparmio energetico può spegnere il GPS a schermo spento: la registrazione resta in piedi
     * ma arrivano punti solo quando guardi il telefono, e il percorso diventa una spezzata.
     */
    fun powerSaveLimitsGps(ctx: Context): Boolean {
        val pm = ctx.getSystemService(PowerManager::class.java)
        if (!pm.isPowerSaveMode) return false
        if (Build.VERSION.SDK_INT < 28) return true
        return pm.locationPowerSaveMode != PowerManager.LOCATION_MODE_NO_CHANGE
    }
}
