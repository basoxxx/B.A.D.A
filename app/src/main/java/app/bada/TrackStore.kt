package app.bada

import android.content.Context
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Un file CSV per uscita, solo nella memoria interna dell'app. */
object TrackStore {
    /** Solo questi nomi entrano dal backup: blocca percorsi tipo "../" (zip slip) e file estranei. */
    val NAME = Regex("\\d{10,15}_(RUN|BIKE)\\.csv")

    fun dir(ctx: Context) = File(ctx.filesDir, "tracks").apply { mkdirs() }

    // shortcut: rilegge ogni file a ogni apertura della lista; se diventa lento con centinaia di uscite, salvare un riepilogo per file.
    fun list(ctx: Context): List<File> =
        dir(ctx).listFiles { f -> NAME.matches(f.name) }.orEmpty().sortedByDescending { it.name }

    fun read(file: File): List<Event> = if (file.exists()) Track.parse(file.readText()) else emptyList()

    fun create(ctx: Context, run: Boolean, t: Long) = File(dir(ctx), name(run, t))

    private fun name(run: Boolean, t: Long) = "${t}_${if (run) "RUN" else "BIKE"}.csv"

    fun append(file: File, e: Event) = file.appendText(Track.line(e) + "\n")

    fun isRun(file: File) = file.name.contains("_RUN")

    fun startMillis(file: File) = file.name.substringBefore('_').toLongOrNull() ?: file.lastModified()

    /** Salva un'uscita importata; false se ne esiste già una con lo stesso inizio (import ripetuto). */
    fun save(ctx: Context, events: List<Event>, run: Boolean): Boolean {
        val dest = File(dir(ctx), name(run, events.first().t))
        if (dest.exists()) return false
        writeAtomically(dest) { it.write(events.joinToString("") { e -> Track.line(e) + "\n" }.toByteArray()) }
        return true
    }

    fun exportZip(ctx: Context, out: OutputStream) = ZipOutputStream(out).use { z ->
        for (f in list(ctx)) {
            z.putNextEntry(ZipEntry(f.name))
            f.inputStream().use { it.copyTo(z) }
            z.closeEntry()
        }
    }

    /** Ripristina un backup senza sovrascrivere le uscite già presenti. Ritorna quante ne ha aggiunte. */
    fun importZip(ctx: Context, input: InputStream): Int {
        var added = 0
        ZipInputStream(input).use { z ->
            while (true) {
                val entry = z.nextEntry ?: break
                if (entry.isDirectory || !NAME.matches(entry.name)) continue
                val dest = File(dir(ctx), entry.name)
                if (dest.exists()) continue
                writeAtomically(dest) { z.copyTo(it) }
                added++
            }
        }
        return added
    }

    // Scrive su .tmp e rinomina: un file a metà non compare mai nella lista.
    private fun writeAtomically(dest: File, write: (OutputStream) -> Unit) {
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        tmp.outputStream().use(write)
        if (!tmp.renameTo(dest)) { tmp.delete(); error("Impossibile salvare ${dest.name}") }
    }
}
