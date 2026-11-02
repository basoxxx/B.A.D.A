package app.bada

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Polyline
import java.io.File

/** Unica schermata che usa la rete: scarica i tile OpenStreetMap della zona del percorso. */
class DetailActivity : Activity() {
    companion object { const val EXTRA_FILE = "file" }

    private lateinit var map: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().apply {
            userAgentValue = packageName // richiesto dalla policy dei tile OSM
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
        setContentView(R.layout.activity_detail)

        val file = File(intent.getStringExtra(EXTRA_FILE) ?: return finish())
        val ev = TrackStore.read(file)
        val run = TrackStore.isRun(file)
        val m = Track.distanceM(ev)
        val ms = if (ev.isEmpty()) 0L else Track.activeMs(ev)
        findViewById<TextView>(R.id.stats).text = (if (run) "Corsa" else "Bici") +
            "\n${Format.distance(m)}   ·   ${Format.duration(ms)}   ·   ${Format.average(run, m, ms)}"

        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.overlays.add(CopyrightOverlay(this)) // attribuzione richiesta dalla licenza OSM
        val segs = Track.segments(ev).map { seg -> seg.map { GeoPoint(it.lat, it.lon) } }
        if (segs.isEmpty()) {
            map.visibility = View.GONE
            findViewById<TextView>(R.id.no_points).visibility = View.VISIBLE
        } else {
            for (pts in segs) map.overlays.add(Polyline(map).apply {
                setPoints(pts)
                outlinePaint.color = Color.rgb(255, 87, 34)
                outlinePaint.strokeWidth = 10f
            })
            val box = BoundingBox.fromGeoPoints(segs.flatten())
            map.addOnFirstLayoutListener { _, _, _, _, _ -> map.zoomToBoundingBox(box, false, 80) }
        }

        findViewById<Button>(R.id.delete).setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage("Eliminare questa uscita? Non si può annullare.")
                .setPositiveButton("Elimina") { _, _ -> file.delete(); finish() }
                .setNegativeButton("Annulla", null)
                .show()
        }
    }

    override fun onResume() { super.onResume(); map.onResume() }
    override fun onPause() { map.onPause(); super.onPause() }
}
