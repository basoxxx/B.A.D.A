package app.bada

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

sealed interface Event { val t: Long }
data class Fix(val lat: Double, val lon: Double, override val t: Long) : Event
/** kind: start | pause | resume | stop */
data class Mark(val kind: String, override val t: Long) : Event
/** Un km (m = 1000) o l'ultimo tratto parziale. */
data class Split(val m: Double, val ms: Long)

object Track {
    private const val MIN_STEP_M = 3.0 // sotto questa soglia è rumore GPS da fermo

    fun parse(lines: List<String>): List<Event> = lines.mapNotNull { line ->
        val p = line.trim().split(',')
        when {
            p.size == 2 && p[0] in setOf("start", "pause", "resume", "stop") ->
                p[1].toLongOrNull()?.let { Mark(p[0], it) }
            p.size == 3 -> {
                val lat = p[0].toDoubleOrNull(); val lon = p[1].toDoubleOrNull(); val t = p[2].toLongOrNull()
                if (lat != null && lon != null && t != null) Fix(lat, lon, t) else null
            }
            else -> null // riga troncata da un crash
        }
    }

    fun line(e: Event): String = when (e) {
        is Fix -> "${e.lat},${e.lon},${e.t}"
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

    fun distanceM(events: List<Event>): Double = splits(events).sumOf { it.m }

    /** Tempi km per km tra i fix, con interpolazione al confine del km; le pause non contano. */
    fun splits(events: List<Event>): List<Split> {
        val out = mutableListOf<Split>()
        var kmM = 0.0
        var kmMs = 0.0
        for (seg in segments(events)) {
            var anchor = seg.first()
            for (f in seg.drop(1)) {
                var d = haversineM(anchor, f)
                if (d < MIN_STEP_M) continue
                var dt = (f.t - anchor.t).toDouble()
                anchor = f
                while (kmM + d >= 1000) {
                    val need = 1000 - kmM
                    val needMs = dt * need / d
                    out.add(Split(1000.0, (kmMs + needMs).roundToLong()))
                    d -= need; dt -= needMs; kmM = 0.0; kmMs = 0.0
                }
                kmM += d; kmMs += dt
            }
        }
        if (kmM > 0) out.add(Split(kmM, kmMs.roundToLong()))
        return out
    }

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
