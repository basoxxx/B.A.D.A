package app.bada

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

sealed interface Event { val t: Long }
data class Fix(val lat: Double, val lon: Double, override val t: Long, val alt: Double? = null) : Event
/** kind: start | pause | resume | stop */
data class Mark(val kind: String, override val t: Long) : Event
/** Un km (m = 1000) o l'ultimo tratto parziale. */
data class Split(val m: Double, val ms: Long)
/** Distanza e tempo attivo cumulati a ogni fix contato. */
data class Sample(val m: Double, val ms: Long, val alt: Double?)
/** Un punto dei grafici: velocità media e altitudine media di un tratto. */
data class ChartPoint(val m: Double, val mps: Double, val alt: Double?)

object Track {
    private const val MIN_STEP_M = 3.0 // sotto questa soglia è rumore GPS da fermo

    /** L'ultimo pezzo senza "\n" finale è una riga troncata da un crash e viene scartato. */
    fun parse(text: String): List<Event> = text.split('\n').dropLast(1).mapNotNull { line ->
        val p = line.trim().split(',')
        when {
            p.size == 2 && p[0] in setOf("start", "pause", "resume", "stop") ->
                p[1].toLongOrNull()?.let { Mark(p[0], it) }
            p.size == 3 || p.size == 4 -> { // 3 campi: uscite registrate prima dell'altitudine
                val lat = p[0].toDoubleOrNull(); val lon = p[1].toDoubleOrNull(); val t = p[2].toLongOrNull()
                if (lat != null && lon != null && t != null) Fix(lat, lon, t, p.getOrNull(3)?.toDoubleOrNull()) else null
            }
            else -> null
        }
    }

    fun line(e: Event): String = when (e) {
        is Fix -> "${e.lat},${e.lon},${e.t}" + (e.alt?.let { ",%.1f".format(java.util.Locale.ROOT, it) } ?: "")
        is Mark -> "${e.kind},${e.t}"
    }

    fun segments(events: List<Event>): List<List<Fix>> {
        val out = mutableListOf<MutableList<Fix>>(mutableListOf())
        for (e in events) {
            if (e is Mark && e.kind == "pause") out.add(mutableListOf())
            if (e is Fix) out.last().add(e)
        }
        return out.filter { it.isNotEmpty() }
    }

    /** Unico punto in cui si misura il percorso: distanza, km e grafici derivano tutti da qui. */
    fun profile(events: List<Event>): List<Sample> {
        val out = mutableListOf<Sample>()
        var m = 0.0
        var ms = 0L
        for (seg in segments(events)) {
            var anchor = seg.first()
            out.add(Sample(m, ms, anchor.alt)) // inizio segmento: dopo una pausa non si aggiunge niente
            for (f in seg.drop(1)) {
                val d = haversineM(anchor, f)
                if (d < MIN_STEP_M) continue
                m += d; ms += f.t - anchor.t; anchor = f
                out.add(Sample(m, ms, f.alt))
            }
        }
        return out
    }

    fun distanceM(events: List<Event>): Double = profile(events).lastOrNull()?.m ?: 0.0

    /** Tempi km per km con interpolazione al confine del km; le pause non contano. */
    fun splits(events: List<Event>): List<Split> {
        val out = mutableListOf<Split>()
        var kmM = 0.0
        var kmMs = 0.0
        for ((a, b) in profile(events).zipWithNext()) {
            var d = b.m - a.m
            var dt = (b.ms - a.ms).toDouble()
            if (d <= 0) continue
            while (kmM + d >= 1000) {
                val need = 1000 - kmM
                val needMs = dt * need / d
                out.add(Split(1000.0, (kmMs + needMs).roundToLong()))
                d -= need; dt -= needMs; kmM = 0.0; kmMs = 0.0
            }
            kmM += d; kmMs += dt
        }
        if (kmM > 0) out.add(Split(kmM, kmMs.roundToLong()))
        return out
    }

    /**
     * Divide il percorso in tratti uguali (al più [bins], almeno 20 m l'uno) e per ognuno dà velocità
     * e altitudine medie: la media sul tratto smussa il rumore del GPS. Vuoto sotto i 100 m.
     */
    fun chart(events: List<Event>, bins: Int = 60): List<ChartPoint> {
        val p = profile(events)
        val total = p.lastOrNull()?.m ?: return emptyList()
        if (total < 100) return emptyList()
        val n = minOf(bins, (total / 20).toInt())
        // shortcut: ricerca lineare per ogni tratto (O(bins × punti)); con uscite di molte ore passare alla ricerca binaria.
        fun msAt(m: Double): Double {
            val j = p.indexOfFirst { it.m >= m }.let { if (it < 0) p.lastIndex else it }
            if (j == 0) return p[0].ms.toDouble()
            val a = p[j - 1]; val b = p[j]
            val k = if (b.m > a.m) (m - a.m) / (b.m - a.m) else 1.0
            return a.ms + (b.ms - a.ms) * k
        }
        return (0 until n).map { i ->
            val m0 = total * i / n; val m1 = total * (i + 1) / n
            val dtS = (msAt(m1) - msAt(m0)) / 1000
            val alts = p.filter { it.m in m0..m1 }.mapNotNull { it.alt }
            ChartPoint((m0 + m1) / 2, if (dtS > 0) (m1 - m0) / dtS else 0.0, alts.takeIf { it.isNotEmpty() }?.average())
        }
    }

    /** Dislivello positivo dalle altitudini già mediate del grafico (quelle grezze gonfiano il totale). */
    fun climbM(points: List<ChartPoint>): Double =
        points.mapNotNull { it.alt }.zipWithNext().sumOf { (a, b) -> maxOf(0.0, b - a) }

    /** Tempo in movimento escluse le pause. now = null per un'uscita finita: senza "stop" si chiude all'ultimo evento. */
    fun activeMs(events: List<Event>, now: Long? = null): Long {
        var total = 0L
        var since: Long? = null
        for (e in events) {
            if (e !is Mark) continue
            when (e.kind) {
                "start", "resume" -> if (since == null) since = e.t
                "pause", "stop" -> since?.let { total += e.t - it; since = null }
            }
        }
        since?.let { total += (now ?: events.last().t) - it }
        return total
    }

    fun haversineM(a: Fix, b: Fix): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).let { it * it }
        return 2 * r * asin(sqrt(h))
    }
}
