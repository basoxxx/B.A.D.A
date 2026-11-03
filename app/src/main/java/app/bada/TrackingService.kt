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
import android.graphics.drawable.Icon
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
        private const val ACTION_PAUSE = "pause"
        private const val ACTION_RESUME = "resume"
        private const val NOTIFY_EVERY_MS = 5000L
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
        /** In pausa automatica (fermo a un semaforo...): distinta dalla pausa manuale, riparte da sola. */
        var autoPaused = false
            private set
        private var auto: AutoPause? = null

        /** Il servizio attivo ridisegna la notifica quando lo stato cambia (pausa dalla UI o dalla notifica). */
        private var onChange: (() -> Unit)? = null

        /** Lo stato nasce subito, così la UI lo vede prima che il servizio parta. */
        fun start(ctx: Context, isRun: Boolean) {
            if (file != null) return
            val t = now()
            run = isRun
            paused = false
            autoPaused = false
            auto = if (Prefs.autoPause(ctx)) AutoPause(isRun) else null
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
            autoPaused = false
        }

        /** Pausa manuale; se era già in pausa automatica il segno di pausa è già scritto. */
        fun pause() {
            if (file == null || paused) return
            paused = true
            if (!autoPaused) record(Mark("pause", now()))
            autoPaused = false
            auto?.reset()
            onChange?.invoke()
        }

        fun resume() {
            if (file == null || !paused) return
            paused = false
            auto?.reset()
            record(Mark("resume", now()))
            onChange?.invoke()
        }

        private fun now() = System.currentTimeMillis()

        private fun record(e: Event) {
            events.add(e)
            file?.let { TrackStore.append(it, e) }
        }
    }

    private val lm by lazy { getSystemService(LocationManager::class.java) }
    private val nm by lazy { getSystemService(NotificationManager::class.java) }
    private var lastNotify = 0L
    private var voice: Voice? = null
    private var announced = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> begin()
            ACTION_STOP -> end()
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            else -> stopSelf() // riavvio di sistema senza intent: la traccia parziale è già su disco
        }
        return START_NOT_STICKY
    }

    private fun begin() {
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Registrazione", NotificationManager.IMPORTANCE_LOW))
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        else startForeground(1, notification())
        onChange = { updateNotification() }
        announced = 0
        if (Prefs.voice(this)) voice = Voice(this)
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            end(); return
        }
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
    }

    private fun end() {
        onChange = null
        voice?.shutdown(); voice = null
        lm.removeUpdates(this)
        close()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        onChange = null
        voice?.shutdown(); voice = null
        lm.removeUpdates(this)
        super.onDestroy()
    }

    override fun onLocationChanged(loc: Location) {
        if (file == null || paused) return
        if (!loc.hasAccuracy() || loc.accuracy > MAX_ACCURACY_M) return
        val f = Fix(loc.latitude, loc.longitude, now(), if (loc.hasAltitude()) loc.altitude else null)
        when (val d = auto?.onFix(f) ?: AutoPause.Keep) {
            AutoPause.Ignore -> return
            is AutoPause.Pause -> {
                autoPaused = true
                // Fermo fin dalla partenza (aspetti il GPS o allacci le scarpe): il tempo parte quando ti muovi.
                val neverMoved = events.none { it is Mark && it.kind == "resume" } && Track.distanceM(events) < 1.0
                record(Mark("pause", if (neverMoved) events.first().t else d.t))
                voice?.say("Pausa automatica")
                updateNotification()
                return
            }
            is AutoPause.Resume -> {
                autoPaused = false
                record(Mark("resume", d.t))
                voice?.say("Si riparte")
            }
            AutoPause.Keep -> {}
        }
        record(f)
        announce()
        if (now() - lastNotify >= NOTIFY_EVERY_MS) updateNotification()
    }

    /** Avviso vocale a ogni km (corsa) o 5 km (bici). */
    private fun announce() {
        val v = voice ?: return
        val m = Track.distanceM(events)
        val every = Spoken.every(run)
        val n = (m / every).toInt()
        if (n <= announced) return
        announced = n
        val km = n * every / 1000
        val last = if (run) Track.splits(events).getOrNull(km - 1)?.takeIf { it.m >= 1000 }?.ms else null
        v.say(Spoken.split(run, km, Track.activeMs(events, now()), m, last))
    }

    private fun updateNotification() {
        if (file != null) nm.notify(1, notification())
    }

    // Su API < 30 questi metodi non hanno implementazione di default.
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("API < 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    /** Tempo che scorre da solo (cronometro di sistema), distanza e media, e il pulsante Pausa/Riprendi. */
    private fun notification(): Notification {
        lastNotify = now()
        val ms = Track.activeMs(events, now())
        val m = Track.distanceM(events)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, RecordActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val toggle = PendingIntent.getService(
            this, 1, Intent(this, TrackingService::class.java).setAction(if (paused) ACTION_RESUME else ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val kind = if (run) "Corsa" else "Bici"
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(getColor(R.color.accent))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentText("${Format.distance(m)}  ·  ${Format.average(run, m, ms)}")
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_notification), if (paused) "Riprendi" else "Pausa", toggle,
                ).build(),
            )
        if (paused || autoPaused) b.setContentTitle("$kind ${if (autoPaused) "in pausa automatica" else "in pausa"}  ·  ${Format.duration(ms)}").setShowWhen(false)
        else b.setContentTitle(kind).setUsesChronometer(true).setWhen(System.currentTimeMillis() - ms).setShowWhen(true)
        return b.build()
    }
}
