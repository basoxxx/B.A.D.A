package app.bada

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/** Totali per periodo e record personali, separati per corsa e bici. Tutto calcolato in locale. */
class StatsActivity : Activity() {
    private var all: List<Workout> = emptyList()
    private var run = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)
        run = savedInstanceState?.getBoolean("run") ?: intent.getBooleanExtra(EXTRA_RUN, true)
        findViewById<Button>(R.id.type_run).setOnClickListener { run = true; show() }
        findViewById<Button>(R.id.type_bike).setOnClickListener { run = false; show() }
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putBoolean("run", run)
    }

    override fun onResume() {
        super.onResume()
        // Riletto a ogni ritorno: un'attività aperta da un record potrebbe essere stata eliminata.
        thread {
            val loaded = Workouts.all(this)
            runOnUiThread { all = loaded; show() }
        }
    }

    private fun show() {
        selectable(findViewById(R.id.type_run), run)
        selectable(findViewById(R.id.type_bike), !run)
        val acts = all.filter { it.run == run }

        val totals = findViewById<LinearLayout>(R.id.totals)
        totals.removeAllViews()
        for (p in Stats.Period.entries) {
            val t = Stats.totals(acts, Stats.since(p))
            val n = if (t.count == 1) "1 uscita" else "${t.count} uscite"
            row(totals, p.label, "$n  ·  ${Format.duration(t.ms)}", Format.distance(t.m), null)
        }

        val weeks = Stats.weeks(acts)
        val day = SimpleDateFormat("d MMM", Locale.ITALY)
        val zone = java.time.ZoneId.systemDefault()
        fun label(d: java.time.LocalDate) = day.format(Date(d.atStartOfDay(zone).toInstant().toEpochMilli()))
        findViewById<BarsView>(R.id.weeks).set(weeks.map { it.second }, label(weeks.first().first), "Questa settimana") {
            "max " + Format.distance(it)
        }

        val records = findViewById<LinearLayout>(R.id.records)
        records.removeAllViews()
        val avg = Stats.bestAverage(acts)
        val list = listOf(
            Triple("Distanza più lunga", Stats.longest(acts)) { r: Record -> Format.distance(r.value) },
            Triple("Uscita più lunga", Stats.longestTime(acts)) { r: Record -> Format.duration(r.value.toLong()) },
            Triple(if (run) "Miglior passo medio" else "Miglior velocità media", avg) { r: Record ->
                Format.average(run, r.value * 1000, 1_000_000L)
            },
        )
        for ((title, rec, fmt) in list) {
            if (rec == null) row(records, title, "Ancora nessun dato", "--", null)
            else row(records, title, date(rec.workout.start), fmt(rec), rec.workout.file)
        }

        // Il miglior tempo su ogni distanza classica, preso anche da metà di un'uscita più lunga.
        val efforts = findViewById<LinearLayout>(R.id.efforts)
        efforts.removeAllViews()
        for ((d, name) in Stats.efforts(run)) {
            val best = Stats.podium(acts, d).firstOrNull()
            if (best == null) row(efforts, name, "Ancora nessun dato", "--", null)
            else row(efforts, name, date(best.workout.start) + "  ·  " + Format.average(run, d.toDouble(), best.value.toLong()),
                Format.duration(best.value.toLong()), best.workout.file)
        }
    }

    private fun row(parent: LinearLayout, title: String, subtitle: String, value: String, file: String?) {
        val v = layoutInflater.inflate(R.layout.item_row, parent, false)
        v.findViewById<TextView>(R.id.title).text = title
        v.findViewById<TextView>(R.id.subtitle).text = subtitle
        v.findViewById<TextView>(R.id.value).text = value
        if (file != null) v.setOnClickListener {
            startActivity(Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_FILE, file))
        } else v.isClickable = false
        parent.addView(v)
    }

    private fun date(t: Long) = SimpleDateFormat("d MMMM yyyy", Locale.ITALY).format(Date(t))

    companion object {
        const val EXTRA_RUN = "run"

        /** Selettore a due o più scelte: quella attiva ha bordo e testo arancio. */
        fun selectable(v: View, selected: Boolean) {
            v.setBackgroundResource(if (selected) R.drawable.btn_selected else R.drawable.btn_secondary)
            (v as? TextView)?.setTextColor(v.context.getColor(if (selected) R.color.accent else R.color.text))
        }
    }
}
