package app.bada

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Aggiornamenti da GitHub Releases. Unica chiamata di rete oltre alla mappa: al massimo una volta al giorno,
 * disattivabile dal menu. Android rifiuta da solo un APK firmato con una chiave diversa.
 */
object Updater {
    private const val API = "https://api.github.com/repos/basoxxx/B.A.D.A/releases/latest"
    private const val APK_NAME = "offtrack.apk"
    private const val DAY_MS = 86_400_000L
    const val ACTION_INSTALL_STATUS = "app.bada.INSTALL_STATUS"

    data class Release(val code: Int, val name: String, val apkUrl: String)

    /** "v1.0.57" -> 57: il numero finale è il versionCode (numero di run della CI). */
    fun releaseCode(tag: String): Int? = tag.substringAfterLast('.').toIntOrNull()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("updates", Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context) = prefs(ctx).getBoolean("enabled", true)
    fun setEnabled(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("enabled", on).apply()

    /** L'aggiornamento già trovato e non ancora installato, se c'è. */
    fun pending(ctx: Context): Release? {
        val p = prefs(ctx)
        val code = p.getInt("code", 0)
        val url = p.getString("url", null) ?: return null
        return if (code > BuildConfig.VERSION_CODE) Release(code, p.getString("name", "") ?: "", url) else null
    }

    /** Controlla GitHub se è passato un giorno (o se [force]); il risultato arriva sul main thread. */
    fun check(ctx: Context, force: Boolean, onResult: (Release?, String?) -> Unit) {
        val app = ctx.applicationContext
        val p = prefs(app)
        if (!force && (!isEnabled(app) || System.currentTimeMillis() - p.getLong("last_check", 0) < DAY_MS)) {
            onResult(pending(app), null); return
        }
        thread {
            val error = try {
                val r = fetch()
                p.edit().putLong("last_check", System.currentTimeMillis()).apply()
                if (r != null) p.edit().putInt("code", r.code).putString("url", r.apkUrl).putString("name", r.name).apply()
                null
            } catch (e: Exception) {
                "Controllo aggiornamenti non riuscito: ${e.message}"
            }
            Handler(Looper.getMainLooper()).post { onResult(pending(app), error) }
        }
    }

    private fun fetch(): Release? {
        val conn = URL(API).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000; conn.readTimeout = 10_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (conn.responseCode == 404) return null // nessuna release ancora
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val tag = json.getString("tag_name")
            val code = releaseCode(tag) ?: return null
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name") == APK_NAME) return Release(code, tag, a.getString("browser_download_url"))
            }
            return null
        } finally {
            conn.disconnect()
        }
    }

    /** Scarica l'APK e lo passa al PackageInstaller; Android mostra la conferma all'utente. */
    fun install(activity: Activity, r: Release, onError: (String) -> Unit) {
        val app = activity.applicationContext
        thread {
            try {
                val apk = File(app.cacheDir, APK_NAME)
                val conn = URL(r.apkUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000; conn.readTimeout = 30_000
                try {
                    if (conn.responseCode != 200) error("download HTTP ${conn.responseCode}")
                    conn.inputStream.use { input -> apk.outputStream().use { input.copyTo(it) } }
                } finally {
                    conn.disconnect()
                }
                val installer = app.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    session.openWrite("offtrack", 0, apk.length()).use { out ->
                        apk.inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    val status = PendingIntent.getActivity(
                        app, 1, Intent(app, MainActivity::class.java).setAction(ACTION_INSTALL_STATUS), flags,
                    )
                    session.commit(status.intentSender)
                }
                apk.delete()
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post { onError("Aggiornamento non riuscito: ${e.message}") }
            }
        }
    }
}
