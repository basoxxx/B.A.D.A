package app.bada

import android.content.Context
import java.io.File

/** Un file CSV per uscita, solo nella memoria interna dell'app. */
object TrackStore {
    fun dir(ctx: Context) = File(ctx.filesDir, "tracks").apply { mkdirs() }

    // shortcut: rilegge ogni file a ogni apertura della lista; se diventa lento con centinaia di uscite, salvare un riepilogo per file.
    fun list(ctx: Context): List<File> =
        dir(ctx).listFiles { f -> f.name.endsWith(".csv") }.orEmpty().sortedByDescending { it.name }

    fun read(file: File): List<Event> = if (file.exists()) Track.parse(file.readText()) else emptyList()

    fun create(ctx: Context, run: Boolean, t: Long) = File(dir(ctx), "${t}_${if (run) "RUN" else "BIKE"}.csv")

    fun append(file: File, e: Event) = file.appendText(Track.line(e) + "\n")

    fun isRun(file: File) = file.name.contains("_RUN")

    fun startMillis(file: File) = file.name.substringBefore('_').toLongOrNull() ?: file.lastModified()
}
