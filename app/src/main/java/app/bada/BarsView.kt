package app.bada

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

/** Barre verticali (km per settimana): l'ultima, la settimana in corso, è arancio; le altre più tenui. */
class BarsView(ctx: Context, attrs: AttributeSet?) : View(ctx, attrs) {
    private val dp = resources.displayMetrics.density
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A4A6B.toInt() }
    private val current = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF5722.toInt() }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF93A0B5.toInt()
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
    }
    private var values: List<Double> = emptyList()
    private var first = ""
    private var last = ""
    private var top = ""

    fun set(values: List<Double>, firstLabel: String, lastLabel: String, maxLabel: (Double) -> String) {
        this.values = values; first = firstLabel; last = lastLabel
        top = maxLabel(values.maxOrNull() ?: 0.0)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (values.isEmpty()) return
        val max = values.max().takeIf { it > 0 } ?: 1.0
        // Rispetta il padding: la vista sta dentro una scheda con i bordi arrotondati.
        val left = paddingLeft.toFloat(); val right = (width - paddingRight).toFloat()
        val labelH = text.textSize + 6 * dp
        val chartTop = paddingTop + labelH
        val bottom = height - paddingBottom - labelH
        val slot = (right - left) / values.size
        val gap = slot * 0.25f
        values.forEachIndexed { i, v ->
            val h = ((v / max) * (bottom - chartTop)).toFloat().coerceAtLeast(if (v > 0) 2 * dp else 1 * dp)
            val r = RectF(left + i * slot + gap / 2, bottom - h, left + (i + 1) * slot - gap / 2, bottom)
            canvas.drawRoundRect(r, 2 * dp, 2 * dp, if (i == values.lastIndex) current else bar)
        }
        canvas.drawText(top, left, paddingTop + text.textSize, text)
        val base = (height - paddingBottom).toFloat() - 2 * dp
        canvas.drawText(first, left, base, text)
        canvas.drawText(last, right - text.measureText(last), base, text)
    }
}
