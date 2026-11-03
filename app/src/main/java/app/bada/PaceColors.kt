package app.bada

/**
 * Colori della linea per velocità: azzurro = più lento, giallo = nella media, arancio-rosso = più veloce.
 * La scala va dal 10° al 90° percentile dell'uscita, così un semaforo o uno sprint non la schiacciano.
 */
object PaceColors {
    private val STOPS = intArrayOf(0xFF4FC3F7.toInt(), 0xFFFFC53D.toInt(), 0xFFFF3D00.toInt())

    /** Per ogni velocità un valore 0..1 rispetto alle altre della stessa uscita. */
    fun normalize(speeds: List<Double>): List<Float> {
        if (speeds.isEmpty()) return emptyList()
        val sorted = speeds.sorted()
        val lo = sorted[(sorted.size * 0.1).toInt().coerceAtMost(sorted.lastIndex)]
        val hi = sorted[(sorted.size * 0.9).toInt().coerceAtMost(sorted.lastIndex)]
        if (hi - lo < 1e-6) return speeds.map { 0.5f }
        return speeds.map { ((it - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f) }
    }

    fun color(t: Float): Int {
        val x = t.coerceIn(0f, 1f) * (STOPS.size - 1)
        val i = x.toInt().coerceAtMost(STOPS.size - 2)
        return mix(STOPS[i], STOPS[i + 1], x - i)
    }

    val gradient: IntArray get() = STOPS.copyOf()

    private fun mix(a: Int, b: Int, k: Float): Int {
        fun ch(c: Int, s: Int) = (c shr s) and 0xFF
        fun lerp(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * k).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (lerp(16) shl 16) or (lerp(8) shl 8) or lerp(0)
    }
}
