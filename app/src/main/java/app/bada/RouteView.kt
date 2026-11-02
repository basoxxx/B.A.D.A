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

    override fun onDraw(canvas: Canvas) {
        val all = segments.flatten()
        if (all.isEmpty()) return
        val minLat = all.minOf { it.lat }; val maxLat = all.maxOf { it.lat }
        val minLon = all.minOf { it.lon }; val maxLon = all.maxOf { it.lon }
        val kx = cos(Math.toRadians((minLat + maxLat) / 2)) // equirettangolare: corregge l'allungamento est-ovest
        val w = max((maxLon - minLon) * kx, 1e-9); val h = max(maxLat - minLat, 1e-9)
        val pad = paint.strokeWidth * 3
        val scale = minOf((width - 2 * pad) / w, (height - 2 * pad) / h)
        val ox = (width - w * scale) / 2; val oy = (height - h * scale) / 2
        for (seg in segments) {
            val path = Path()
            seg.forEachIndexed { i, f ->
                val x = (ox + (f.lon - minLon) * kx * scale).toFloat()
                val y = (oy + (maxLat - f.lat) * scale).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, paint)
        }
    }
}
