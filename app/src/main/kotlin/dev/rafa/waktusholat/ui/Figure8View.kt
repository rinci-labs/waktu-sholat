package dev.rafa.waktusholat.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import dev.rafa.waktusholat.R
import kotlin.math.cos
import kotlin.math.sin

/**
 * Calibration animation: a phone tracing a figure 8 (a lemniscate), the motion that exposes the
 * magnetometer to every orientation so it can re-estimate its offsets. Drawn, not an asset; the
 * loop runs only while visible and system animations are on.
 */
class Figure8View @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3 * density
        strokeCap = Paint.Cap.ROUND
        color = context.getColor(R.color.outline)
    }
    private val tracePaint = Paint(trackPaint).apply { color = context.getColor(R.color.brand) }
    private val phonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.brand) }
    private val screenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.surface) }

    private val track = Path()
    private val trace = Path()
    private val measure = PathMeasure()
    private val position = FloatArray(2)
    private val tangent = FloatArray(2)
    private val phone = RectF()
    private var running = false
    private val startedAt = SystemClock.uptimeMillis()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        track.reset()
        val a = w * 0.36f
        val cx = w / 2f
        val cy = h / 2f
        // Lemniscate of Gerono: x = a·cos t, y = a·sin t·cos t (a horizontal 8).
        for (i in 0..120) {
            val t = i / 120f * 2 * Math.PI
            val x = cx + a * cos(t).toFloat()
            val y = cy + a * 0.9f * (sin(t) * cos(t)).toFloat()
            if (i == 0) track.moveTo(x, y) else track.lineTo(x, y)
        }
        measure.setPath(track, true)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(track, trackPaint)
        val length = measure.length
        if (length <= 0f) return
        val progress = ((SystemClock.uptimeMillis() - startedAt) % PERIOD_MILLIS) / PERIOD_MILLIS.toFloat()
        val at = progress * length

        // A short trail behind the phone shows the direction of travel.
        trace.reset()
        val tail = length * 0.18f
        if (at >= tail) {
            measure.getSegment(at - tail, at, trace, true)
        } else {
            measure.getSegment(length - (tail - at), length, trace, true)
            measure.getSegment(0f, at, trace, true)
        }
        canvas.drawPath(trace, tracePaint)

        measure.getPosTan(at, position, tangent)
        val angle = Math.toDegrees(kotlin.math.atan2(tangent[1], tangent[0]).toDouble()).toFloat()
        canvas.save()
        canvas.translate(position[0], position[1])
        canvas.rotate(angle * 0.35f)
        val w = 14 * density
        val h = 24 * density
        phone.set(-w / 2, -h / 2, w / 2, h / 2)
        canvas.drawRoundRect(phone, 3 * density, 3 * density, phonePaint)
        phone.inset(2 * density, 3 * density)
        canvas.drawRoundRect(phone, 1.5f * density, 1.5f * density, screenPaint)
        canvas.restore()

        if (running) postInvalidateOnAnimation()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        val enabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
        running = isVisible && enabled
        if (running) invalidate()
    }

    override fun onDetachedFromWindow() {
        running = false
        super.onDetachedFromWindow()
    }

    private companion object {
        const val PERIOD_MILLIS = 2_800L
    }
}
