package app.bada

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/** Unica schermata che usa la rete: scarica i tile OpenStreetMap della zona del percorso. */
class DetailActivity : Activity() {
    companion object {
        const val EXTRA_FILE = "file"
        private const val REQ_EXPORT_GPX = 1
    }

    private lateinit var map: MapView
    private lateinit var file: File
    private lateinit var events: List<Event>
    private var run = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().apply {
            userAgentValue = packageName // richiesto dalla policy dei tile OSM
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
        setContentView(R.layout.activity_detail)

        file = File(intent.getStringExtra(EXTRA_FILE) ?: return finish())
        events = TrackStore.read(file)
        run = TrackStore.isRun(file)
        val m = Track.distanceM(events)
        val ms = if (events.isEmpty()) 0L else Track.activeMs(events)
        val chart = Track.chart(events)

        findViewById<TextView>(R.id.kind).text = if (run) "Corsa" else "Bici"
        findViewById<TextView>(R.id.date).text =
            SimpleDateFormat("EEEE d MMMM yyyy, HH:mm", Locale.ITALY).format(Date(TrackStore.startMillis(file)))
        stat(R.id.stat_distance, "Distanza", Format.distance(m))
        stat(R.id.stat_time, "Tempo in movimento", Format.duration(ms))
        stat(R.id.stat_average, if (run) "Passo medio" else "Velocità media", Format.average(run, m, ms))
        val alts = chart.mapNotNull { it.alt }
        stat(R.id.stat_climb, "Dislivello", if (alts.size < 2) "--" else "%.0f m".format(Track.climbM(chart)))

        showCharts(chart, alts)
        showSplits()
        showMap()

        findViewById<Button>(R.id.export_gpx).setOnClickListener {
            val name = "offtrack-${SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.ROOT).format(Date(TrackStore.startMillis(file)))}" +
                (if (run) "-corsa" else "-bici") + ".gpx"
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/gpx+xml").putExtra(Intent.EXTRA_TITLE, name),
                REQ_EXPORT_GPX,
            )
        }
        findViewById<Button>(R.id.delete).setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage("Eliminare questa attività? Non si può annullare.")
                .setPositiveButton("Elimina") { _, _ -> file.delete(); finish() }
                .setNegativeButton("Annulla", null)
                .show()
        }
    }

    private fun stat(id: Int, label: String, value: String) {
        val v = findViewById<View>(id)
        v.findViewById<TextView>(R.id.label).text = label
        v.findViewById<TextView>(R.id.value).text = value
    }

    private fun showCharts(chart: List<ChartPoint>, alts: List<Double>) {
        val paceChart = findViewById<ChartView>(R.id.pace_chart)
        if (chart.isEmpty()) paceChart.visibility = View.GONE
        // Si disegna sempre la velocità (più in alto = più veloce); per la corsa le etichette sono in passo.
        else paceChart.set(if (run) "Passo" else "Velocità", chart.map { it.mps }) { v ->
            if (v <= 0) "--" else Format.average(run, v * 1000, 1_000_000L)
        }
        val altChart = findViewById<ChartView>(R.id.alt_chart)
        if (alts.size < 2) altChart.visibility = View.GONE // attività registrate prima dell'altitudine
        else altChart.set("Altitudine", alts) { "%.0f m".format(it) }
    }

    private fun showSplits() {
        val splits = Track.splits(events)
        val box = findViewById<LinearLayout>(R.id.splits)
        if (splits.isEmpty()) { findViewById<View>(R.id.splits_title).visibility = View.GONE; return }
        // Barra proporzionale alla velocità: il parziale più veloce è pieno.
        val speeds = splits.map { if (it.ms > 0) it.m / it.ms else 0.0 }
        val fastest = speeds.max().takeIf { it > 0 } ?: 1.0
        splits.forEachIndexed { i, s ->
            val row = layoutInflater.inflate(R.layout.item_split, box, false)
            row.findViewById<TextView>(R.id.km).text = if (s.m >= 1000) "${i + 1}" else String.format(Locale.ITALY, "%.2f", s.m / 1000)
            row.findViewById<TextView>(R.id.value).text = Format.average(run, s.m, s.ms)
            row.findViewById<View>(R.id.bar).layoutParams =
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (speeds[i] / fastest).toFloat())
            box.addView(row)
        }
    }

    private fun showMap() {
        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.overlays.add(CopyrightOverlay(this)) // attribuzione richiesta dalla licenza OSM
        val segs = Track.segments(events).map { seg -> seg.map { GeoPoint(it.lat, it.lon) } }
        if (segs.isEmpty()) {
            map.visibility = View.GONE
            findViewById<TextView>(R.id.no_points).visibility = View.VISIBLE
            return
        }
        for (pts in segs) map.overlays.add(Polyline(map).apply {
            setPoints(pts)
            outlinePaint.color = getColor(R.color.accent)
            outlinePaint.strokeWidth = 5 * resources.displayMetrics.density
        })
        val box = BoundingBox.fromGeoPoints(segs.flatten())
        map.addOnFirstLayoutListener { _, _, _, _, _ -> map.zoomToBoundingBox(box, false, 64) }
    }

    @Deprecated("API di piattaforma; senza AndroidX non c'è l'alternativa")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val uri = data?.data
        if (requestCode != REQ_EXPORT_GPX || resultCode != RESULT_OK || uri == null) return
        val gpx = Gpx.write(events, run, "Offtrack " + (if (run) "corsa" else "bici"))
        thread {
            val msg = try {
                contentResolver.openOutputStream(uri)!!.use { it.write(gpx.toByteArray()) }
                "GPX salvato"
            } catch (e: Exception) {
                "Esportazione non riuscita: ${e.message}"
            }
            runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        }
    }

    override fun onResume() { super.onResume(); map.onResume() }
    override fun onPause() { map.onPause(); super.onPause() }
}
