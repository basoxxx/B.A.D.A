package app.bada

import android.content.Context

/** Opzioni della registrazione, dal menu della home. Valgono dalla registrazione successiva. */
object Prefs {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun autoPause(ctx: Context) = prefs(ctx).getBoolean("auto_pause", true)
    fun setAutoPause(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("auto_pause", on).apply()

    fun voice(ctx: Context) = prefs(ctx).getBoolean("voice", false)
    fun setVoice(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("voice", on).apply()
}
