package app.bada

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Riepilogo di un'uscita: quanto basta per totali e record senza rileggere i punti. */
data class Workout(val file: String, val run: Boolean, val start: Long, val m: Double, val ms: Long, val fastestKmMs: Long?) {
    companion object {
        fun of(file: String, run: Boolean, start: Long, events: List<Event>) = Workout(
            file, run, start, Track.distanceM(events), if (events.isEmpty()) 0L else Track.activeMs(events),
            Track.splits(events).filter { it.m >= 1000 }.minOfOrNull { it.ms },
        )
    }
}

data class Totals(val count: Int, val m: Double, val ms: Long)

/** Un record: l'uscita che lo detiene e il valore (metri o millisecondi a seconda del record). */
data class Record(val workout: Workout, val value: Double)

object Stats {
    /** Per il passo medio contano solo uscite di almeno 1 km: 300 m di scatto non sono un record. */
    const val MIN_AVERAGE_M = 1000.0

    enum class Period(val label: String) { WEEK("Questa settimana"), MONTH("Questo mese"), YEAR("Quest'anno"), ALL("Totale") }

    /** Inizio del periodo in millisecondi (settimana da lunedì), 0 per il totale. */
    fun since(p: Period, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Long {
        val d = when (p) {
            Period.WEEK -> today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            Period.MONTH -> today.withDayOfMonth(1)
            Period.YEAR -> today.withDayOfYear(1)
            Period.ALL -> return 0L
        }
        return d.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun totals(acts: List<Workout>, from: Long = 0L): Totals {
        val a = acts.filter { it.start >= from }
        return Totals(a.size, a.sumOf { it.m }, a.sumOf { it.ms })
    }

    fun longest(acts: List<Workout>): Record? =
        acts.filter { it.m > 0 }.maxByOrNull { it.m }?.let { Record(it, it.m) }

    fun longestTime(acts: List<Workout>): Record? =
        acts.filter { it.ms > 0 }.maxByOrNull { it.ms }?.let { Record(it, it.ms.toDouble()) }

    /** Miglior media (ms per metro più basso); value = velocità in m/s. */
    fun bestAverage(acts: List<Workout>): Record? =
        acts.filter { it.m >= MIN_AVERAGE_M && it.ms > 0 }.minByOrNull { it.ms / it.m }
            ?.let { Record(it, it.m / (it.ms / 1000.0)) }

    /** Km intero più veloce; value = millisecondi. */
    fun fastestKm(acts: List<Workout>): Record? =
        acts.filter { it.fastestKmMs != null && it.fastestKmMs > 0 }.minByOrNull { it.fastestKmMs!! }
            ?.let { Record(it, it.fastestKmMs!!.toDouble()) }
}
