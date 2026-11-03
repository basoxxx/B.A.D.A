package app.bada

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Testi in italiano: titoli automatici delle uscite e frasi degli avvisi vocali. */
object Spoken {
    /** Ogni quanti metri parla l'avviso vocale: ogni km di corsa, ogni 5 km in bici. */
    fun every(run: Boolean) = if (run) 1000 else 5000

    /** Titolo automatico in stile Strava: "Corsa mattutina", "Giro in bici serale"... */
    fun defaultTitle(run: Boolean, start: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val part = when (Instant.ofEpochMilli(start).atZone(zone).hour) {
            in 5..11 -> 0
            in 12..17 -> 1
            in 18..21 -> 2
            else -> 3
        }
        return if (run) listOf("Corsa mattutina", "Corsa pomeridiana", "Corsa serale", "Corsa notturna")[part]
        else listOf("Giro in bici mattutino", "Giro in bici pomeridiano", "Giro in bici serale", "Giro in bici notturno")[part]
    }

    /** Durata da pronunciare: "1 ora e 5 minuti", "25 minuti e 3 secondi". */
    fun duration(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600; val m = s % 3600 / 60; val sec = s % 60
        fun unit(n: Long, one: String, many: String) = "$n ${if (n == 1L) one else many}"
        return when {
            h > 0 -> unit(h, "ora", "ore") + if (m > 0) " e " + unit(m, "minuto", "minuti") else ""
            m > 0 -> unit(m, "minuto", "minuti") + if (sec > 0) " e " + unit(sec, "secondo", "secondi") else ""
            else -> unit(sec, "secondo", "secondi")
        }
    }

    /** Passo da pronunciare: "5 e 24 al chilometro". */
    fun pace(msPerKm: Long): String {
        val s = msPerKm / 1000
        return if (s % 60 == 0L) "${s / 60} minuti al chilometro" else "${s / 60} e ${s % 60} al chilometro"
    }

    /**
     * Frase dell'avviso: distanza, tempo e passo dell'ultimo km (corsa) o velocità media (bici).
     * [lastKmMs] è il tempo dell'ultimo km intero.
     */
    fun split(run: Boolean, km: Int, totalMs: Long, totalM: Double, lastKmMs: Long?): String {
        val head = "${if (km == 1) "Un chilometro" else "$km chilometri"}. Tempo ${duration(totalMs)}."
        return if (run) head + (lastKmMs?.let { " Ultimo chilometro ${pace(it)}." } ?: "")
        else {
            val kmh = if (totalMs > 0) totalM / 1000 / (totalMs / 3_600_000.0) else 0.0
            head + " Velocità media " + String.format(Locale.ITALY, "%.1f", kmh) + " chilometri orari."
        }
    }
}
