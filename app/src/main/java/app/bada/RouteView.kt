package app.bada

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.max

/** Miniatura del percorso: solo la linea, nessun tile di mappa, quindi nessuna rete. */
class RouteView(ctx: Context, attrs: AttributeSet?) : View(ctx, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF5722.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3 * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var segments: List<List<Fix>> = emptyList()

    fun setRoute(segs: List<List<Fix>>) { segments = segs; invalidate() }

    override fun onDraw(canvas: Canvas) = draw(canvas, segments, width.toFloat(), height.toFloat(), paint)

    companion object {
        /** Proiezione equirettangolare che centra tutti i punti in un rettangolo [w]x[h]; null se non c'è niente da disegnare. */
        private fun projector(all: List<Fix>, w: Float, h: Float, pad: Float): ((Fix) -> Pair<Float, Float>)? {
            if (all.isEmpty()) return null
            val minLat = all.minOf { it.lat }; val maxLat = all.maxOf { it.lat }
            val minLon = all.minOf { it.lon }; val maxLon = all.maxOf { it.lon }
            val kx = cos(Math.toRadians((minLat + maxLat) / 2)) // corregge l'allungamento est-ovest
            val rw = max((maxLon - minLon) * kx, 1e-9); val rh = max(maxLat - minLat, 1e-9)
            val scale = minOf((w - 2 * pad) / rw, (h - 2 * pad) / rh)
            val ox = (w - rw * scale) / 2; val oy = (h - rh * scale) / 2
            return { f -> (ox + (f.lon - minLon) * kx * scale).toFloat() to (oy + (maxLat - f.lat) * scale).toFloat() }
        }

        private fun path(points: List<Fix>, proj: (Fix) -> Pair<Float, Float>) = Path().apply {
            points.forEachIndexed { i, f -> val (x, y) = proj(f); if (i == 0) moveTo(x, y) else lineTo(x, y) }
        }

        /** Disegna il percorso centrato in un rettangolo [w]x[h]. I tratti di un solo punto (fermo in partenza) si saltano. */
        fun draw(canvas: Canvas, segments: List<List<Fix>>, w: Float, h: Float, paint: Paint, pad: Float = paint.strokeWidth * 3) {
            val segs = segments.filter { it.size >= 2 }
            val proj = projector(segs.flatten(), w, h, pad) ?: return
            for (seg in segs) canvas.drawPath(path(seg, proj), paint)
        }

        /** Come [draw], ma ogni pezzo con il colore della sua velocità (immagine da condividere). */
        fun drawColored(canvas: Canvas, pieces: List<Piece>, w: Float, h: Float, paint: Paint, pad: Float = paint.strokeWidth * 3) {
            val proj = projector(pieces.flatMap { it.points }, w, h, pad) ?: return
            val tone = PaceColors.normalize(pieces.map { it.mps })
            val p = Paint(paint)
            pieces.forEachIndexed { i, piece ->
                p.color = PaceColors.color(tone[i])
                canvas.drawPath(path(piece.points, proj), p)
            }
        }
    }
}
