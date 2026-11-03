package app.bada

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Immagine da condividere (storie, chat): percorso grande, titolo e tre numeri. Disegnata in locale, niente mappa. */
object ShareCard {
    private const val W = 1080
    private const val H = 1350

    fun render(ctx: Context, w: Workout, events: List<Event>): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, 0f, H.toFloat(), 0xFF14213D.toInt(), 0xFF0E1626.toInt(), Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)

        val pad = 72f
        val cond = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF93A0B5.toInt(); textSize = 30f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); letterSpacing = 0.12f
        }
        val value = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 84f; typeface = cond }
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 64f; typeface = cond }
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF5722.toInt(); textSize = 34f; typeface = cond; letterSpacing = 0.14f }

        c.drawText("OFFTRACK", pad, pad + 34f, accent)
        val date = SimpleDateFormat("d MMMM yyyy", Locale.ITALY).format(Date(w.start))
        c.drawText(date, W - pad - label.measureText(date), pad + 32f, label)

        // Percorso nella parte alta
        val route = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFF5722.toInt(); style = Paint.Style.STROKE; strokeWidth = 14f
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        c.save()
        c.translate(pad, 160f)
        // Bordo scuro sotto e linea colorata per velocità sopra, come sulla mappa.
        RouteView.draw(c, Track.segments(events), W - 2 * pad, 700f, Paint(route).apply { color = 0xFF0A1020.toInt(); strokeWidth = 20f }, 40f)
        RouteView.drawColored(c, Track.pieces(events, pieces = 80), W - 2 * pad, 700f, route, 40f)
        c.restore()

        var y = 960f
        c.drawText(ellipsize(w.title, title, W - 2 * pad), pad, y, title)
        y += 70f
        c.drawLine(pad, y, W - pad, y, Paint().apply { color = 0xFF24304A.toInt(); strokeWidth = 3f })
        y += 70f
        val cols = listOf(
            "DISTANZA" to Format.distance(w.m),
            "TEMPO" to Format.duration(w.ms),
            (if (w.run) "PASSO" else "MEDIA") to Format.average(w.run, w.m, w.ms),
        )
        val colW = (W - 2 * pad) / 3
        cols.forEachIndexed { i, (l, v) ->
            val x = pad + i * colW
            c.drawText(l, x, y, label)
            value.textSize = 84f
            while (value.measureText(v) > colW - 16 && value.textSize > 40) value.textSize -= 4
            c.drawText(v, x, y + 100f, value)
        }
        return bmp
    }

    private fun ellipsize(s: String, p: Paint, max: Float): String {
        if (p.measureText(s) <= max) return s
        var t = s
        while (t.isNotEmpty() && p.measureText("$t…") > max) t = t.dropLast(1)
        return "$t…"
    }

    /** Salva l'immagine nella cache e ritorna l'indirizzo da passare alle altre app. */
    fun save(ctx: Context, bmp: Bitmap): Uri {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, "offtrack-${System.currentTimeMillis()}.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.parse("content://${ctx.packageName}.share/${f.name}")
    }
}

/**
 * Espone in sola lettura le immagini di cacheDir/share alle app scelte da "Condividi"
 * (al posto del FileProvider di AndroidX, che Offtrack non usa).
 */
class ShareProvider : ContentProvider() {
    override fun onCreate() = true

    private fun file(uri: Uri): File {
        val name = uri.lastPathSegment ?: throw FileNotFoundException()
        if (!Regex("offtrack-\\d+\\.png").matches(name)) throw FileNotFoundException(name)
        return File(File(context!!.cacheDir, "share"), name).takeIf { it.exists() } ?: throw FileNotFoundException(name)
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY)

    override fun getType(uri: Uri) = "image/png"

    override fun query(uri: Uri, projection: Array<out String>?, sel: String?, args: Array<out String>?, sort: String?): Cursor {
        val f = file(uri)
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply { addRow(arrayOf(f.name, f.length())) }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, sel: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, sel: String?, args: Array<out String>?) = 0
}
