package app.bada

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.RadioButton
import android.widget.RadioGroup
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
        /** Appena registrata: se ha fatto un record lo si festeggia. */
        const val EXTRA_FRESH = "fresh"
        private const val REQ_EXPORT_GPX = 1
        /** Oro, argento, bronzo. */
        val MEDALS = intArrayOf(R.color.gold, R.color.silver, R.color.bronze)
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
        if (!file.exists()) return finish()
        events = TrackStore.read(file)
        run = TrackStore.isRun(file)
        showHeader()
        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.overlays.add(CopyrightOverlay(this)) // attribuzione richiesta dalla licenza OSM
        // Dentro una pagina che scorre: mentre tocchi la mappa i gesti sono suoi, non della pagina.
        map.setOnTouchListener { v, e ->
            v.parent.requestDisallowInterceptTouchEvent(e.actionMasked != android.view.MotionEvent.ACTION_UP &&
                e.actionMasked != android.view.MotionEvent.ACTION_CANCEL)
            false
        }

        // I calcoli (grafici, parziali, colori) vanno in background: con un giro di ore sono decine di migliaia di punti.
        val ev = events
        thread {
            val m = Track.distanceM(ev)
            val ms = if (ev.isEmpty()) 0L else Track.activeMs(ev)
            val chart = Track.chart(ev)
            val splits = Track.splits(ev)
            val pieces = Track.pieces(ev, pieces = 80)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                stat(R.id.stat_distance, "Distanza", Format.distance(m))
                stat(R.id.stat_time, "Tempo in movimento", Format.duration(ms))
                stat(R.id.stat_average, if (run) "Passo medio" else "Velocità media", Format.average(run, m, ms))
                val alts = chart.mapNotNull { it.alt }
                stat(R.id.stat_climb, "Dislivello", if (alts.size < 2) "--" else "%.0f m".format(Track.climbM(chart)))
                showCharts(chart, alts)
                showSplits(splits)
                showMap(pieces)
            }
        }
        showEfforts()

        findViewById<View>(R.id.title).setOnClickListener { edit() }
        findViewById<View>(R.id.menu).setOnClickListener { anchor ->
            val menu = PopupMenu(this, anchor)
            menu.menu.add(0, 1, 0, "Modifica")
            menu.menu.add(0, 2, 1, "Condividi immagine")
            menu.menu.add(0, 3, 2, "Esporta GPX")
            menu.menu.add(0, 4, 3, "Elimina")
            menu.setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> edit()
                    2 -> share()
                    3 -> findViewById<Button>(R.id.export_gpx).performClick()
                    4 -> confirmDelete()
                }
                true
            }
            menu.show()
        }
        findViewById<Button>(R.id.share).setOnClickListener { share() }
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
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setMessage("Eliminare questa attività? Non si può annullare.")
            .setPositiveButton("Elimina") { _, _ -> TrackStore.delete(file); finish() }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showHeader() {
        val meta = TrackStore.readMeta(file)
        findViewById<TextView>(R.id.title).text = TrackStore.title(file, meta)
        findViewById<TextView>(R.id.date).text = (if (run) "Corsa" else "Bici") + "  ·  " +
            SimpleDateFormat("EEEE d MMMM yyyy, HH:mm", Locale.ITALY).format(Date(TrackStore.startMillis(file)))
        findViewById<TextView>(R.id.note).apply {
            text = meta.note
            visibility = if (meta.note.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    /** Titolo, note e tipo. Cambiare tipo ricalcola passo/velocità, quindi la schermata si riapre. */
    private fun edit() {
        val meta = TrackStore.readMeta(file)
        val dp = resources.displayMetrics.density
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (8 * dp).toInt(), (20 * dp).toInt(), 0)
        }
        val title = EditText(this).apply {
            hint = Spoken.defaultTitle(run, TrackStore.startMillis(file))
            setText(meta.title)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine()
        }
        val note = EditText(this).apply {
            hint = "Note (come ti sei sentito, scarpe, meteo...)"
            setText(meta.note)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 2
            maxLines = 6
        }
        val type = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val runBtn = RadioButton(this).apply { id = View.generateViewId(); text = "Corsa" }
        val bikeBtn = RadioButton(this).apply { id = View.generateViewId(); text = "Bici" }
        type.addView(runBtn); type.addView(bikeBtn)
        type.check(if (run) runBtn.id else bikeBtn.id)
        box.addView(title); box.addView(note); box.addView(type)

        AlertDialog.Builder(this)
            .setTitle("Modifica attività")
            .setView(box)
            .setPositiveButton("Salva") { _, _ ->
                TrackStore.writeMeta(file, Meta(title.text.toString().trim(), note.text.toString().trim()))
                val newRun = type.checkedRadioButtonId == runBtn.id
                if (newRun != run) {
                    val moved = TrackStore.setRun(file, newRun)
                    startActivity(Intent(this, DetailActivity::class.java).putExtra(EXTRA_FILE, moved.path))
                    finish()
                } else showHeader()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    /** Immagine con percorso e numeri, condivisa con l'app scelta (WhatsApp, Instagram...). */
    private fun share() {
        thread {
            val w = Workout.of(file.path, run, TrackStore.startMillis(file), TrackStore.title(file), events)
            val uri = try { ShareCard.save(this, ShareCard.render(this, w, events)) } catch (e: Exception) { null }
            runOnUiThread {
                if (uri == null) { Toast.makeText(this, "Immagine non creata", Toast.LENGTH_LONG).show(); return@runOnUiThread }
                val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                send.clipData = android.content.ClipData.newRawUri(null, uri)
                startActivity(Intent.createChooser(send, "Condividi attività"))
            }
        }
    }

    /** Tempi migliori di questa uscita sulle distanze classiche, con medaglia se è tra i primi tre di sempre. */
    private fun showEfforts() {
        thread {
            val all = Workouts.all(this).filter { it.run == run }
            val me = all.firstOrNull { it.file == file.path } ?: return@thread
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                val records = Stats.recordsOf(all, me)
                if (intent.getBooleanExtra(EXTRA_FRESH, false) && records.isNotEmpty()) {
                    findViewById<TextView>(R.id.record_banner).apply {
                        text = (if (records.size == 1) "Nuovo record personale: " else "Nuovi record personali: ") +
                            records.joinToString(", ")
                        visibility = View.VISIBLE
                    }
                }
                val box = findViewById<LinearLayout>(R.id.efforts)
                box.removeAllViews()
                val rows = Stats.efforts(run).filter { me.best.containsKey(it.first) }
                findViewById<View>(R.id.efforts_title).visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
                for ((d, name) in rows) {
                    val ms = me.best.getValue(d)
                    val v = layoutInflater.inflate(R.layout.item_row, box, false)
                    v.findViewById<TextView>(R.id.title).text = name
                    val medal = Stats.medal(all, me, d)
                    v.findViewById<TextView>(R.id.subtitle).apply {
                        text = when (medal) { 1 -> "Record personale"; 2 -> "2° miglior tempo"; 3 -> "3° miglior tempo"; else -> Format.average(run, d.toDouble(), ms) }
                        if (medal != null) setTextColor(getColor(MEDALS[medal - 1]))
                    }
                    v.findViewById<TextView>(R.id.value).text = Format.duration(ms)
                    v.isClickable = false
                    box.addView(v)
                }
            }
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
        else altChart.set("Altitudine", alts, minRange = 40.0) { "%.0f m".format(it) }
    }

    private fun showSplits(splits: List<Split>) {
        val box = findViewById<LinearLayout>(R.id.splits)
        if (splits.isEmpty()) { findViewById<View>(R.id.splits_title).visibility = View.GONE; return }
        // Barra proporzionale alla velocità: il parziale più veloce è pieno.
        val speeds = splits.map { if (it.ms > 0) it.m / it.ms else 0.0 }
        // Barra tra il km più lento (35%) e il più veloce (100%): le differenze di pochi secondi si vedono.
        val full = splits.indices.filter { splits[it].m >= 1000 }.map { speeds[it] }
        val slowest = full.minOrNull() ?: 0.0
        val fastestSpeed = full.maxOrNull() ?: speeds.max()
        fun width(v: Double) = if (fastestSpeed - slowest < 1e-9) 1.0 else (0.35 + 0.65 * (v - slowest) / (fastestSpeed - slowest)).coerceIn(0.1, 1.0)
        // Il km intero più veloce è in arancio, gli altri in grigio-blu: si vede subito dove hai spinto.
        val best = splits.indices.filter { splits[it].m >= 1000 }.maxByOrNull { speeds[it] }
        splits.forEachIndexed { i, s ->
            val row = layoutInflater.inflate(R.layout.item_split, box, false)
            row.findViewById<TextView>(R.id.km).text = if (s.m >= 1000) "${i + 1}" else String.format(Locale.ITALY, "%.2f", s.m / 1000)
            row.findViewById<TextView>(R.id.value).apply {
                text = Format.average(run, s.m, s.ms)
                if (i == best) setTextColor(getColor(R.color.accent))
            }
            row.findViewById<View>(R.id.bar).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, width(speeds[i]).toFloat())
                setBackgroundResource(if (i == best) R.drawable.bar else R.drawable.bar_muted)
            }
            row.findViewById<TextView>(R.id.climb).text = s.climb?.let {
                val r = Math.round(it)
                if (r > 0) "+$r m" else if (r < 0) "−${-r} m" else "0 m"
            } ?: ""
            box.addView(row)
        }
    }

    private fun showMap(pieces: List<Piece>) {        val segs = Track.segments(events).filter { it.size >= 2 }.map { seg -> seg.map { GeoPoint(it.lat, it.lon) } }
        if (segs.isEmpty()) {
            findViewById<View>(R.id.map_box).visibility = View.GONE
            findViewById<TextView>(R.id.no_points).visibility = View.VISIBLE
            return
        }
        // Linea colorata per velocità, sopra un bordo scuro che la stacca dalla mappa.
        val dp = resources.displayMetrics.density
        fun line(pts: List<GeoPoint>, color: Int, width: Float) = Polyline(map).apply {
            setPoints(pts)
            outlinePaint.color = color
            outlinePaint.strokeWidth = width
            outlinePaint.strokeCap = Paint.Cap.ROUND // senza, i tanti segmenti corti sembrano tratteggiati
            outlinePaint.strokeJoin = Paint.Join.ROUND
            outlinePaint.isAntiAlias = true
        }
        for (pts in segs) map.overlays.add(line(pts, 0xCC0E1626.toInt(), 8 * dp))
        val tone = PaceColors.normalize(pieces.map { it.mps })
        pieces.forEachIndexed { i, piece ->
            map.overlays.add(line(piece.points.map { GeoPoint(it.lat, it.lon) }, PaceColors.color(tone[i]), 5 * dp))
        }
        findViewById<View>(R.id.legend_bar).background =
            GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, PaceColors.gradient).apply { cornerRadius = 3 * dp }
        val box = BoundingBox.fromGeoPoints(segs.flatten())
        // La mappa può essere già disposta quando arrivano i dati (calcolati in background).
        if (map.width > 0 && map.height > 0) map.zoomToBoundingBox(box, false, (44 * dp).toInt())
        else map.addOnFirstLayoutListener { _, _, _, _, _ -> map.zoomToBoundingBox(box, false, (44 * dp).toInt()) }
        map.invalidate()
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
