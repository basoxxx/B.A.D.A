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
    private var pendingRun = true
    private var files: List<File> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.run).setOnClickListener { start(true) }
        findViewById<Button>(R.id.bike).setOnClickListener { start(false) }
        findViewById<Button>(R.id.back_to_recording).setOnClickListener {
            startActivity(Intent(this, RecordActivity::class.java))
        }
        findViewById<ListView>(R.id.list).setOnItemClickListener { _, _, pos, _ ->
            startActivity(Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_FILE, files[pos].path))
        }
    }

    override fun onResume() {
        super.onResume()
        val recording = TrackingService.file
        files = TrackStore.list(this).filter { it != recording }
        findViewById<ListView>(R.id.list).adapter =
            ArrayAdapter(this, android.R.layout.simple_list_item_1, files.map(::label))
        findViewById<TextView>(R.id.empty).visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.start_buttons).visibility = if (recording == null) View.VISIBLE else View.GONE
        findViewById<View>(R.id.back_to_recording).visibility = if (recording != null) View.VISIBLE else View.GONE
    }

    private fun label(f: File): String {
        val ev = TrackStore.read(f)
        val run = TrackStore.isRun(f)
        val m = Track.distanceM(ev)
        val ms = if (ev.isEmpty()) 0L else Track.activeMs(ev)
        return "${kind(run)} · ${date(f)}\n${Format.distance(m)} · ${Format.duration(ms)} · ${Format.average(run, m, ms)}"
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
