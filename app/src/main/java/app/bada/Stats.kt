package app.bada

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Riepilogo di un'uscita: quanto basta per lista, totali e record senza rileggere i punti. */
data class Workout(
    val file: String,
    val run: Boolean,
    val start: Long,
    val title: String,
    val m: Double,
    val ms: Long,
    /** Migliori prestazioni: distanza in metri -> tempo in ms. */
    val best: Map<Int, Long> = emptyMap(),
    /** Percorso ridotto per la miniatura. */
    val thumb: List<List<Fix>> = emptyList(),
) {
    companion object {
        fun of(file: String, run: Boolean, start: Long, title: String, events: List<Event>): Workout {
            val p = Track.profile(events)
            // Per la miniatura bastano ~100 punti a segmento: tiene bassa la memoria con molte uscite.
            val thumb = Track.segments(events).map { seg ->
                val step = seg.size / 100 + 1
                seg.filterIndexed { i, _ -> i % step == 0 || i == seg.lastIndex }
            }
            return Workout(
                file, run, start, title, p.lastOrNull()?.m ?: 0.0, if (events.isEmpty()) 0L else Track.activeMs(events),
                Track.bestEfforts(p, Stats.efforts(run).map { it.first }), thumb,
            )
        }
    }
}

data class Totals(val count: Int, val m: Double, val ms: Long)

/** Un record: l'uscita che lo detiene e il valore (metri, millisecondi o m/s a seconda del record). */
data class Record(val workout: Workout, val value: Double)

object Stats {
    /** Per la media contano solo uscite di almeno 1 km: 300 m di scatto non sono un record. */
    const val MIN_AVERAGE_M = 1000.0

    private val RUN_EFFORTS = listOf(1000 to "1 km", 5000 to "5 km", 10000 to "10 km", 21097 to "Mezza maratona", 42195 to "Maratona")
    private val BIKE_EFFORTS = listOf(5000 to "5 km", 10000 to "10 km", 20000 to "20 km", 40000 to "40 km", 100000 to "100 km")

    /** Distanze delle migliori prestazioni, con il nome da mostrare. */
    fun efforts(run: Boolean) = if (run) RUN_EFFORTS else BIKE_EFFORTS

    enum class Period(val label: String) { WEEK("Questa settimana"), MONTH("Questo mese"), YEAR("Quest'anno"), ALL("Totale") }

    /** Inizio del periodo in millisecondi (settimana da lunedì), 0 per il totale. */
    fun since(p: Period, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Long {
        val d = when (p) {
            Period.WEEK -> monday(today)
            Period.MONTH -> today.withDayOfMonth(1)
            Period.YEAR -> today.withDayOfYear(1)
            Period.ALL -> return 0L
        }
        return d.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private fun monday(d: LocalDate) = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun totals(acts: List<Workout>, from: Long = 0L): Totals {
        val a = acts.filter { it.start >= from }
        return Totals(a.size, a.sumOf { it.m }, a.sumOf { it.ms })
    }

    /** Metri per settimana, dalla più vecchia alla corrente, con il lunedì di ognuna. */
    fun weeks(acts: List<Workout>, n: Int = 12, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): List<Pair<LocalDate, Double>> {
        val first = monday(today).minusWeeks((n - 1).toLong())
        return (0 until n).map { i ->
            val from = first.plusWeeks(i.toLong())
            val a = from.atStartOfDay(zone).toInstant().toEpochMilli()
            val b = from.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
            from to acts.filter { it.start in a until b }.sumOf { it.m }
        }
    }

    fun longest(acts: List<Workout>): Record? =
        acts.filter { it.m > 0 }.maxByOrNull { it.m }?.let { Record(it, it.m) }

    fun longestTime(acts: List<Workout>): Record? =
        acts.filter { it.ms > 0 }.maxByOrNull { it.ms }?.let { Record(it, it.ms.toDouble()) }

    /** Miglior media (ms per metro più basso); value = velocità in m/s. */
    fun bestAverage(acts: List<Workout>): Record? =
        acts.filter { it.m >= MIN_AVERAGE_M && it.ms > 0 }.minByOrNull { it.ms / it.m }
            ?.let { Record(it, it.m / (it.ms / 1000.0)) }

    /** I tre tempi migliori su [distance] (oro, argento, bronzo); value = millisecondi. */
    fun podium(acts: List<Workout>, distance: Int): List<Record> =
        acts.mapNotNull { a -> a.best[distance]?.takeIf { it > 0 }?.let { Record(a, it.toDouble()) } }
            .sortedBy { it.value }.take(3)

    /** Posizione (1-3) del tempo di [w] su [distance] tra tutte le uscite, null se fuori dal podio. */
    fun medal(acts: List<Workout>, w: Workout, distance: Int): Int? =
        podium(acts, distance).indexOfFirst { it.workout.file == w.file }.takeIf { it >= 0 }?.plus(1)
}

/**
 * Riepiloghi in memoria, ricalcolati solo per i file cambiati: la lista e le statistiche non rileggono
 * ogni CSV a ogni apertura.
 */
object Workouts {
    private val cache = HashMap<String, Pair<Long, Workout>>()

    @Synchronized
    fun all(ctx: android.content.Context): List<Workout> {
        val files = TrackStore.list(ctx).filter { it != TrackingService.file }
        cache.keys.retainAll(files.map { it.path }.toSet())
        return files.map { f ->
            val meta = java.io.File(f.parentFile, "${f.name.substringBefore('_')}.meta")
            val key = f.lastModified() * 31 + meta.lastModified() + f.length()
            cache[f.path]?.takeIf { it.first == key }?.second
                ?: Workout.of(f.path, TrackStore.isRun(f), TrackStore.startMillis(f), TrackStore.title(f), TrackStore.read(f))
                    .also { cache[f.path] = key to it }
        }
    }
}
