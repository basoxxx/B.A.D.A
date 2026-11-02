package app.bada

import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale

/** Export e import GPX 1.1. L'import usa regex invece di un parser XML: i GPX di Strava & co. sono piatti e così è testabile su JVM. */
object Gpx {
    data class Imported(val events: List<Event>, val run: Boolean)

    fun write(events: List<Event>, run: Boolean, name: String): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"Offtrack\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        append("<trk><name>").append(escape(name)).append("</name><type>")
            .append(if (run) "running" else "cycling").append("</type>\n")
        for (seg in Track.segments(events)) {
            append("<trkseg>\n")
            for (f in seg) {
                append("<trkpt lat=\"${f.lat}\" lon=\"${f.lon}\">")
                f.alt?.let { append("<ele>").append(String.format(Locale.ROOT, "%.1f", it)).append("</ele>") }
                append("<time>").append(Instant.ofEpochMilli(f.t)).append("</time></trkpt>\n")
            }
            append("</trkseg>\n")
        }
        append("</trk>\n</gpx>\n")
    }

    private val SEG = Regex("<trkseg\\b.*?</trkseg>", RegexOption.DOT_MATCHES_ALL)
    private val PT = Regex("<trkpt\\b([^>]*)>(.*?)</trkpt>", RegexOption.DOT_MATCHES_ALL)
    private val LAT = Regex("\\blat=\"([^\"]+)\"")
    private val LON = Regex("\\blon=\"([^\"]+)\"")
    private val ELE = Regex("<ele>([^<]+)</ele>")
    private val TIME = Regex("<time>([^<]+)</time>")
    private val TYPE = Regex("<type>([^<]+)</type>")

    /** null se il file non ha almeno due punti con orario: senza tempi non si calcolano passo e durata. */
    fun read(text: String): Imported? {
        val segs = SEG.findAll(text).map { seg ->
            PT.findAll(seg.value).mapNotNull { m ->
                val lat = LAT.find(m.groupValues[1])?.groupValues?.get(1)?.toDoubleOrNull()
                val lon = LON.find(m.groupValues[1])?.groupValues?.get(1)?.toDoubleOrNull()
                val t = TIME.find(m.groupValues[2])?.groupValues?.get(1)?.let(::parseTime)
                val alt = ELE.find(m.groupValues[2])?.groupValues?.get(1)?.trim()?.toDoubleOrNull()
                if (lat != null && lon != null && t != null && lat in -90.0..90.0 && lon in -180.0..180.0) Fix(lat, lon, t, alt) else null
            }.toList()
        }.filter { it.isNotEmpty() }.toList()
        if (segs.sumOf { it.size } < 2) return null

        val events = mutableListOf<Event>(Mark("start", segs.first().first().t))
        segs.forEachIndexed { i, seg ->
            if (i > 0) { events += Mark("pause", segs[i - 1].last().t); events += Mark("resume", seg.first().t) }
            events += seg
        }
        events += Mark("stop", segs.last().last().t)
        val type = TYPE.find(text)?.groupValues?.get(1)?.trim()?.lowercase() ?: ""
        // Strava scrive "cycling"/"running" o i codici numerici ("1" = giro in bici).
        val bike = type == "1" || listOf("cycl", "bik", "ride").any { it in type }
        return Imported(events, run = !bike)
    }

    private fun parseTime(s: String): Long? = try {
        Instant.parse(s.trim()).toEpochMilli()
    } catch (e: Exception) {
        try { OffsetDateTime.parse(s.trim()).toInstant().toEpochMilli() } catch (e: Exception) { null }
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
