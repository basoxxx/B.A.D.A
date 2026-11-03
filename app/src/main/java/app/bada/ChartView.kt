package app.bada

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

/** Grafico ad area semplice: titolo in alto a sinistra, valore massimo e minimo a destra. */
class ChartView(ctx: Context, attrs: AttributeSet?) : View(ctx, attrs) {
    private val dp = resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF5722.toInt(); style = Paint.Style.STROKE; strokeWidth = 2 * dp; strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FF5722; style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFCCCCCC.toInt()
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, resources.displayMetrics)
    }
    private var title = ""
    private var values: List<Double> = emptyList()
    private var label: (Double) -> String = { "" }
    private var minRange = 0.0

    /** [minRange]: scala minima, così 10 m di rumore GPS in pianura non sembrano una salita. */
    fun set(title: String, values: List<Double>, minRange: Double = 0.0, label: (Double) -> String) {
        this.title = title; this.values = values; this.label = label; this.minRange = minRange
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (values.size < 2) return
        canvas.drawText(title, 0f, text.textSize, text)
        val top = text.textSize * 2
        val bottom = height - 4 * dp
        val vMin = values.min(); val vMax = values.max()
        val pad = maxOf(0.0, minRange - (vMax - vMin)) / 2
        val min = vMin - pad; val max = vMax + pad
        val range = maxOf(max - min, 1e-9)
        val step = width.toFloat() / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val y = (bottom - (v - min) / range * (bottom - top)).toFloat()
            if (i == 0) path.moveTo(0f, y) else path.lineTo(i * step, y)
        }
        canvas.drawPath(Path(path).apply { lineTo(width.toFloat(), bottom); lineTo(0f, bottom); close() }, fill)
        canvas.drawPath(path, line)
        val maxLabel = label(vMax); val minLabel = label(vMin)
        canvas.drawText(maxLabel, width - text.measureText(maxLabel), text.textSize, text)
        canvas.drawText(minLabel, width - text.measureText(minLabel), bottom - 2 * dp, text)
    }
}
