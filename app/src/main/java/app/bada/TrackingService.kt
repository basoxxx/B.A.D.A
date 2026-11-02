package app.bada

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import java.io.File

/** Registra il GPS in primo piano (funziona a schermo spento) e scrive ogni evento subito su file. */
class TrackingService : Service(), LocationListener {

    companion object {
        private const val ACTION_START = "start"
        private const val ACTION_STOP = "stop"
        private const val CHANNEL = "tracking"
        private const val MAX_ACCURACY_M = 20f

        // Stato live, toccato solo dal main thread (UI e callback GPS sul main looper).
        var file: File? = null
            private set
        val events = mutableListOf<Event>()
        var paused = false
            private set
        var run = true
            private set

        /** Lo stato nasce subito, così la UI lo vede prima che il servizio parta. */
        fun start(ctx: Context, isRun: Boolean) {
            if (file != null) return
            val t = now()
            run = isRun
            paused = false
            events.clear()
            file = TrackStore.create(ctx, isRun, t)
            record(Mark("start", t))
            ctx.startForegroundService(Intent(ctx, TrackingService::class.java).setAction(ACTION_START))
        }

        /** Chiude subito la traccia su disco (la UI la legge appena dopo), poi ferma il servizio. */
        fun stop(ctx: Context) {
            close()
            ctx.startService(Intent(ctx, TrackingService::class.java).setAction(ACTION_STOP))
        }

        private fun close() {
            val f = file ?: return
            record(Mark("stop", now()))
            if (events.none { it is Fix }) f.delete() // nessun fix GPS: niente da mostrare
            file = null
            paused = false
        }

        fun pause() { if (file != null && !paused) { paused = true; record(Mark("pause", now())) } }
        fun resume() { if (file != null && paused) { paused = false; record(Mark("resume", now())) } }

        private fun now() = System.currentTimeMillis()

        private fun record(e: Event) {
            events.add(e)
            file?.let { TrackStore.append(it, e) }
        }
    }

    private val lm by lazy { getSystemService(LocationManager::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> begin()
            ACTION_STOP -> end()
            else -> stopSelf() // riavvio di sistema senza intent: la traccia parziale è già su disco
        }
        return START_NOT_STICKY
    }

    private fun begin() {
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification(run), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        else startForeground(1, notification(run))
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            end(); return
        }
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
    }

    private fun end() {
        lm.removeUpdates(this)
        close()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        lm.removeUpdates(this)
        super.onDestroy()
    }

    override fun onLocationChanged(loc: Location) {
        if (file == null || paused) return
        if (!loc.hasAccuracy() || loc.accuracy > MAX_ACCURACY_M) return
        record(Fix(loc.latitude, loc.longitude, now(), if (loc.hasAltitude()) loc.altitude else null))
    }

    // Su API < 30 questi metodi non hanno implementazione di default.
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("API < 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    private fun notification(isRun: Boolean): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Registrazione", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, RecordActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (isRun) "Corsa in registrazione" else "Bici in registrazione")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }
}
