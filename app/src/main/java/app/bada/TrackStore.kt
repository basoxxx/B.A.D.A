package app.bada

import android.content.Context
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.StringReader
import java.io.StringWriter
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Titolo e note scelti dall'utente; titolo vuoto = titolo automatico. */
data class Meta(val title: String = "", val note: String = "") {
    fun format(): String = StringWriter().also { w ->
        Properties().apply { setProperty("title", title); setProperty("note", note) }.store(w, null)
    }.toString()

    companion object {
        fun parse(text: String): Meta {
            val p = Properties().apply { load(StringReader(text)) }
            return Meta(p.getProperty("title", "").trim(), p.getProperty("note", "").trim())
        }
    }
}

/**
 * Un file CSV per uscita, solo nella memoria interna dell'app, più un file <inizio>.meta facoltativo
 * con titolo e note: legato all'orario di inizio, non al tipo, così cambiare tipo non lo perde.
 */
object TrackStore {
    /** Solo questi nomi entrano dal backup: blocca percorsi tipo "../" (zip slip) e file estranei. */
    val NAME = Regex("\\d{10,15}_(RUN|BIKE)\\.csv")
    val META = Regex("\\d{10,15}\\.meta")

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

    private fun metaFile(file: File) = File(file.parentFile, "${file.name.substringBefore('_')}.meta")

    fun readMeta(file: File): Meta = metaFile(file).let { if (it.exists()) Meta.parse(it.readText()) else Meta() }

    fun writeMeta(file: File, meta: Meta) {
        val f = metaFile(file)
        if (meta == Meta()) f.delete() else writeAtomically(f) { it.write(meta.format().toByteArray()) }
    }

    /** Titolo da mostrare: quello scelto o quello automatico. */
    fun title(file: File, meta: Meta = readMeta(file)) = meta.title.ifEmpty { Spoken.defaultTitle(isRun(file), startMillis(file)) }

    /** Cambia tipo rinominando il file; ritorna il nuovo file (o quello vecchio se il nome è già occupato). */
    fun setRun(file: File, run: Boolean): File {
        if (isRun(file) == run) return file
        val dest = File(file.parentFile, name(run, startMillis(file)))
        return if (!dest.exists() && file.renameTo(dest)) dest else file
    }

    fun delete(file: File) {
        file.delete()
        metaFile(file).delete()
    }

    fun exportZip(ctx: Context, out: OutputStream) = ZipOutputStream(out).use { z ->
        for (f in list(ctx).flatMap { listOf(it, metaFile(it)) }.filter { it.exists() }) {
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
                if (entry.isDirectory || !(NAME.matches(entry.name) || META.matches(entry.name))) continue
                val dest = File(dir(ctx), entry.name)
                if (dest.exists()) continue
                writeAtomically(dest) { z.copyTo(it) }
                if (NAME.matches(entry.name)) added++
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
