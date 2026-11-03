package app.bada

/**
 * Pausa automatica: se negli ultimi 10 s ti sei spostato meno di pochi metri l'uscita va in pausa
 * dall'inizio di quei 10 s (il tempo da fermo non conta), e riparte appena ti allontani dal punto.
 * Le soglie della bici sono più alte: a passo d'uomo una bici è praticamente ferma.
 */
class AutoPause(run: Boolean) {
    sealed interface Decision
    /** Punto normale: va registrato. */
    data object Keep : Decision
    /** In pausa automatica e ancora fermo: il punto si scarta. */
    data object Ignore : Decision
    data class Pause(val t: Long) : Decision
    data class Resume(val t: Long) : Decision

    private val stillM = if (run) 6.0 else 12.0
    private val moveM = if (run) 10.0 else 20.0
    private val windowMs = 10_000L

    var paused = false
        private set
    private val recent = ArrayDeque<Fix>()
    private var anchor: Fix? = null

    fun onFix(f: Fix): Decision {
        if (paused) {
            val a = anchor ?: return Keep
            if (Track.haversineM(a, f) < moveM) return Ignore
            reset()
            recent.addLast(f)
            return Resume(f.t)
        }
        recent.addLast(f)
        // Tiene come primo punto il più recente che sia vecchio almeno quanto la finestra.
        while (recent.size > 1 && f.t - recent[1].t >= windowMs) recent.removeFirst()
        val first = recent.first()
        if (f.t - first.t >= windowMs && recent.all { Track.haversineM(first, it) < stillM }) {
            paused = true
            anchor = first
            recent.clear()
            return Pause(first.t)
        }
        return Keep
    }

    fun reset() {
        paused = false
        anchor = null
        recent.clear()
    }
}
