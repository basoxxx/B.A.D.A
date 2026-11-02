package app.bada

import java.util.Locale

object Format {
    fun distance(m: Double) = String.format(Locale.ITALY, "%.2f km", m / 1000)

    fun duration(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600; val m = s % 3600 / 60; val sec = s % 60
        return if (h > 0) String.format(Locale.ITALY, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.ITALY, "%d:%02d", m, sec)
    }

    fun pace(m: Double, ms: Long): String {
        if (m < 10) return "--"
        val secPerKm = (ms / 1000.0 / (m / 1000)).toLong()
        return String.format(Locale.ITALY, "%d:%02d /km", secPerKm / 60, secPerKm % 60)
    }

    fun speed(m: Double, ms: Long): String {
        if (ms <= 0) return "--"
        return String.format(Locale.ITALY, "%.1f km/h", m / 1000 / (ms / 3_600_000.0))
    }

    fun average(run: Boolean, m: Double, ms: Long) = if (run) pace(m, ms) else speed(m, ms)
}
