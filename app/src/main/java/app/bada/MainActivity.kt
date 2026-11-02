package app.bada

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private class Item(val file: File, val title: String, val stats: String, val route: List<List<Fix>>)

    private var pendingRun = true
    private var items: List<Item> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.run).setOnClickListener { start(true) }
        findViewById<Button>(R.id.bike).setOnClickListener { start(false) }
        findViewById<Button>(R.id.back_to_recording).setOnClickListener {
            startActivity(Intent(this, RecordActivity::class.java))
        }
        findViewById<ListView>(R.id.list).setOnItemClickListener { _, _, pos, _ ->
            startActivity(Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_FILE, items[pos].file.path))
        }
    }

    override fun onResume() {
        super.onResume()
        val recording = TrackingService.file
        items = TrackStore.list(this).filter { it != recording }.map(::item)
        findViewById<ListView>(R.id.list).adapter = object : ArrayAdapter<Item>(this, 0, items) {
            override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
                val v = convertView ?: layoutInflater.inflate(R.layout.item_activity, parent, false)
                val item = items[pos]
                v.findViewById<TextView>(R.id.title).text = item.title
                v.findViewById<TextView>(R.id.stats).text = item.stats
                v.findViewById<RouteView>(R.id.route).setRoute(item.route)
                return v
            }
        }
        findViewById<TextView>(R.id.empty).visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.start_buttons).visibility = if (recording == null) View.VISIBLE else View.GONE
        findViewById<View>(R.id.back_to_recording).visibility = if (recording != null) View.VISIBLE else View.GONE
    }

    private fun item(f: File): Item {
        val ev = TrackStore.read(f)
        val run = TrackStore.isRun(f)
        val m = Track.distanceM(ev)
        val ms = if (ev.isEmpty()) 0L else Track.activeMs(ev)
        // Per la miniatura bastano ~100 punti a segmento: tiene bassa la memoria con molte uscite.
        val route = Track.segments(ev).map { seg ->
            val step = seg.size / 100 + 1
            seg.filterIndexed { i, _ -> i % step == 0 || i == seg.lastIndex }
        }
        return Item(
            f, "${kind(run)} · ${date(f)}",
            "${Format.distance(m)}  ·  ${Format.duration(ms)}  ·  ${Format.average(run, m, ms)}", route,
        )
    }

    private fun kind(run: Boolean) = if (run) "Corsa" else "Bici"
    private fun date(f: File) = SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.ITALY).format(Date(TrackStore.startMillis(f)))

    private fun start(run: Boolean) {
        pendingRun = run
        // Da Android 12 la posizione precisa va chiesta insieme a quella approssimativa.
        val wanted = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
        val missing = wanted.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) begin() else requestPermissions(missing.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) begin()
        else Toast.makeText(this, "Serve il permesso di posizione precisa", Toast.LENGTH_LONG).show()
    }

    private fun begin() {
        if (!getSystemService(LocationManager::class.java).isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "Attiva la posizione (GPS)", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }
        TrackingService.start(this, pendingRun)
        startActivity(Intent(this, RecordActivity::class.java))
    }
}
