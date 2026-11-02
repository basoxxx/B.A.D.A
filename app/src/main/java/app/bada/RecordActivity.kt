package app.bada

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView

class RecordActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { update(); handler.postDelayed(this, 1000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_record)
        findViewById<Button>(R.id.pause).setOnClickListener {
            if (TrackingService.paused) TrackingService.resume() else TrackingService.pause()
            update()
        }
        findViewById<Button>(R.id.stop).setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage("Terminare l'uscita?")
                .setPositiveButton("Termina") { _, _ -> stop() }
                .setNegativeButton("Continua", null)
                .show()
        }
    }

    private fun stop() {
        val f = TrackingService.file
        val hasFix = TrackingService.events.any { it is Fix }
        TrackingService.stop(this)
        if (f != null && hasFix) {
            startActivity(Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_FILE, f.path))
        }
        finish()
    }

    override fun onResume() { super.onResume(); handler.post(tick) }
    override fun onPause() { handler.removeCallbacks(tick); super.onPause() }

    private fun update() {
        if (TrackingService.file == null) { finish(); return }
        val ev = TrackingService.events
        val run = TrackingService.run
        val m = Track.distanceM(ev)
        val ms = Track.activeMs(ev, System.currentTimeMillis())
        findViewById<TextView>(R.id.kind).text = if (run) "Corsa" else "Bici"
        findViewById<TextView>(R.id.average_label).text = if (run) "Passo medio /km" else "Velocità km/h"
        findViewById<TextView>(R.id.time).text = Format.duration(ms)
        findViewById<TextView>(R.id.distance).text = Format.distance(m).removeSuffix(" km") // unità nell'etichetta
        findViewById<TextView>(R.id.average).text = Format.average(run, m, ms).removeSuffix(" /km").removeSuffix(" km/h")
        findViewById<TextView>(R.id.status).text = when {
            TrackingService.paused -> "IN PAUSA"
            ev.none { it is Fix } -> "In attesa del segnale GPS"
            else -> ""
        }
        findViewById<Button>(R.id.pause).text = if (TrackingService.paused) "Riprendi" else "Pausa"
    }
}
