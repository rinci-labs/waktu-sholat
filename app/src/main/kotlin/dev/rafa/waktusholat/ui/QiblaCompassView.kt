package dev.rafa.waktusholat.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import dev.rafa.waktusholat.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Compass dial with a Qibla needle, drawn entirely on a [Canvas] so it scales to any size and needs
 * no image assets.
 *
 * The dial rotates by the device heading, keeping the needle fixed relative to the dial, so the
 * needle always points at the Qibla on screen. When the top of the phone faces the Qibla (within
 * [ALIGNED_DEGREES]) the rim fills with the accent colour as confirmation.
 *
 * Colours are resolved once at construction; the view is recreated on a theme change, so nothing is
 * looked up per frame.
 */
class QiblaCompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val brand = context.getColor(R.color.brand)
    private val brandSoft = context.getColor(R.color.brand_soft)
    private val surface = context.getColor(R.color.surface)
    private val outline = context.getColor(R.color.outline)
    private val primary = context.getColor(R.color.text_primary)
    private val muted = context.getColor(R.color.text_tertiary)

    /** N, E, S, W in the app language (U, T, S, B in Indonesian). */
    private val cardinals: Array<String> = context.resources.getStringArray(R.array.cardinals)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val needle = Path()

    /** Device heading in degrees clockwise from north; the dial rotates by the negative of this. */
    var headingDegrees: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    /** Qibla bearing from true north, in degrees. */
    var qiblaDegrees: Float = 295f
        set(value) {
            field = value
            invalidate()
        }

    /** True once the sensor has produced a reading; until then the needle is drawn faded. */
    var hasReading: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val aligned: Boolean
        get() = hasReading && abs(((qiblaDegrees - headingDegrees + 540f) % 360f) - 180f) <= ALIGNED_DEGREES

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(cx, cy) * 0.94f

        // Face and rim.
        fillPaint.color = if (aligned) brandSoft else surface
        canvas.drawCircle(cx, cy, radius, fillPaint)
        strokePaint.color = if (aligned) brand else outline
        strokePaint.strokeWidth = radius * if (aligned) 0.03f else 0.012f
        canvas.drawCircle(cx, cy, radius, strokePaint)

        // Fixed index mark at the top: the direction the phone is pointing.
        tickPaint.color = if (aligned) brand else primary
        tickPaint.strokeWidth = radius * 0.025f
        canvas.drawLine(cx, cy - radius * 1.0f, cx, cy - radius * 0.86f, tickPaint)

        canvas.save()
        canvas.rotate(-headingDegrees, cx, cy)
        drawTicks(canvas, cx, cy, radius)
        drawCardinals(canvas, cx, cy, radius)
        drawNeedle(canvas, cx, cy, radius)
        canvas.restore()

        fillPaint.color = if (hasReading) brand else muted
        canvas.drawCircle(cx, cy, radius * 0.05f, fillPaint)
        fillPaint.color = surface
        canvas.drawCircle(cx, cy, radius * 0.02f, fillPaint)
    }

    private fun drawTicks(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val outer = radius * 0.9f
        for (degree in 0 until 360 step 6) {
            val major = degree % 30 == 0
            val inner = if (major) radius * 0.82f else radius * 0.86f
            tickPaint.color = muted
            tickPaint.alpha = if (major) 255 else 120
            tickPaint.strokeWidth = radius * if (major) 0.012f else 0.007f
            val rad = Math.toRadians(degree.toDouble())
            val s = sin(rad).toFloat()
            val c = cos(rad).toFloat()
            canvas.drawLine(cx + s * inner, cy - c * inner, cx + s * outer, cy - c * outer, tickPaint)
        }
        tickPaint.alpha = 255
    }

    private fun drawCardinals(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        labelPaint.textSize = radius * 0.12f
        val distance = radius * 0.68f
        for ((index, degree) in CARDINAL_DEGREES.withIndex()) {
            val label = cardinals[index]
            val rad = Math.toRadians(degree.toDouble())
            val x = cx + sin(rad).toFloat() * distance
            val y = cy - cos(rad).toFloat() * distance + labelPaint.textSize * 0.36f
            labelPaint.color = if (degree == 0) brand else primary
            canvas.drawText(label, x, y, labelPaint)
        }
    }

    /** Tapered pointer from the hub towards the rim, ending in a Kaaba square marker. */
    private fun drawNeedle(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        canvas.save()
        canvas.rotate(qiblaDegrees, cx, cy)
        val alpha = if (hasReading) 255 else 110
        val half = radius * 0.045f

        fillPaint.color = brand
        fillPaint.alpha = alpha
        needle.reset()
        needle.moveTo(cx, cy - radius * 0.62f)
        needle.lineTo(cx + half, cy)
        needle.lineTo(cx, cy + radius * 0.14f)
        needle.lineTo(cx - half, cy)
        needle.close()
        canvas.drawPath(needle, fillPaint)

        val size = radius * 0.07f
        val top = cy - radius * 0.78f
        canvas.drawRoundRect(cx - size, top - size, cx + size, top + size, size * 0.25f, size * 0.25f, fillPaint)
        fillPaint.alpha = 255
        canvas.restore()
    }

    /** Always square; inside a scroll view the width decides the size. */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val size = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) minOf(width, height) else width
        setMeasuredDimension(size, size)
    }

    private companion object {
        val CARDINAL_DEGREES = intArrayOf(0, 90, 180, 270)

        const val ALIGNED_DEGREES = 3f
    }
}
