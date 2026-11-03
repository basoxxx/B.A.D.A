package app.bada

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Esclusione dall'ottimizzazione batteria: su molti telefoni (Xiaomi, Huawei, Samsung...) senza questa
 * il sistema può fermare il GPS durante le registrazioni lunghe a schermo spento.
 */
object Battery {
    fun isExempt(ctx: Context) = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    // Il dialogo diretto è ammesso per un tracker GPS; Offtrack non è sul Play Store.
    @SuppressLint("BatteryLife")
    fun request(a: Activity) {
        try {
            a.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${a.packageName}")))
        } catch (e: ActivityNotFoundException) {
            a.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    /** Alla prima registrazione spiega perché serve e chiede una volta sola; poi resta nel menu. */
    fun askOnce(a: Activity) {
        val prefs = a.getSharedPreferences("battery", Context.MODE_PRIVATE)
        if (isExempt(a) || prefs.getBoolean("asked", false)) return
        prefs.edit().putBoolean("asked", true).apply()
        AlertDialog.Builder(a)
            .setTitle("Registrazioni lunghe")
            .setMessage(
                "Alcuni telefoni fermano le app in background per risparmiare batteria e la registrazione si interrompe. " +
                    "Consenti a Offtrack di funzionare senza limitazioni: la batteria si consuma solo mentre registri.",
            )
            .setPositiveButton("Consenti") { _, _ -> request(a) }
            .setNegativeButton("Non ora", null)
            .show()
    }
}
