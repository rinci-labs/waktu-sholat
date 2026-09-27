package dev.rafa.waktusholat.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import dev.rafa.waktusholat.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Compass dial with the Kaaba riding around its rim, drawn entirely on a [Canvas] so it scales to
 * any size and needs no image assets.
 *
 * The dial rotates by the device heading. A needle points from the hub towards the Qibla, and the
 * Kaaba sits just outside the rim at the end of it, always drawn upright. A faint arc shows which
 * way to turn; when the top of the phone faces the Qibla (within [ALIGNED_DEGREES]) the rim and the
 * Kaaba's badge fill with the accent colour.
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
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val path = Path()
    private val arcBounds = RectF()

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

    /** Signed turn from the phone's heading to the Qibla, -180..180 (positive = clockwise). */
    private val turn: Float
        get() = ((qiblaDegrees - headingDegrees + 540f) % 360f) - 180f

    val aligned: Boolean
        get() = hasReading && abs(turn) <= ALIGNED_DEGREES

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        // Room outside the rim for the Kaaba badge to travel around it.
        val radius = minOf(cx, cy) * 0.72f
        val badge = radius * 0.2f
        val isAligned = aligned

        // Face and rim.
        fillPaint.color = if (isAligned) brandSoft else surface
        canvas.drawCircle(cx, cy, radius, fillPaint)
        strokePaint.color = if (isAligned) brand else outline
        strokePaint.strokeWidth = radius * if (isAligned) 0.03f else 0.014f
        canvas.drawCircle(cx, cy, radius, strokePaint)

        // Which way to turn: an arc along the rim from the top of the phone to the Qibla.
        if (hasReading && !isAligned) {
            strokePaint.color = brand
            strokePaint.alpha = 90
            strokePaint.strokeWidth = radius * 0.03f
            arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
            canvas.drawArc(arcBounds, -90f, turn, false, strokePaint)
            strokePaint.alpha = 255
        }

        canvas.save()
        canvas.rotate(-headingDegrees, cx, cy)
        drawTicks(canvas, cx, cy, radius)
        drawCardinals(canvas, cx, cy, radius)
        drawNeedle(canvas, cx, cy, radius)
        canvas.restore()

        // Fixed index at the top of the dial: the direction the phone is pointing.
        fillPaint.color = if (isAligned) brand else primary
        path.reset()
        path.moveTo(cx, cy - radius * 0.97f)
        path.lineTo(cx - radius * 0.045f, cy - radius * 0.87f)
        path.lineTo(cx + radius * 0.045f, cy - radius * 0.87f)
        path.close()
        canvas.drawPath(path, fillPaint)

        // Hub.
        fillPaint.color = if (hasReading) brand else muted
        canvas.drawCircle(cx, cy, radius * 0.055f, fillPaint)
        fillPaint.color = surface
        canvas.drawCircle(cx, cy, radius * 0.022f, fillPaint)

        // The Kaaba, outside the rim at the needle's bearing, kept upright.
        val screenAngle = Math.toRadians((qiblaDegrees - headingDegrees).toDouble())
        val distance = radius + badge * 1.3f
        val kx = cx + sin(screenAngle).toFloat() * distance
        val ky = cy - cos(screenAngle).toFloat() * distance
        drawKaaba(canvas, kx, ky, badge, isAligned)
    }

    private fun drawTicks(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val outer = radius * 0.93f
        for (degree in 0 until 360 step 6) {
            val major = degree % 30 == 0
            val inner = if (major) radius * 0.84f else radius * 0.88f
            tickPaint.color = muted
            tickPaint.alpha = if (major) 255 else 110
            tickPaint.strokeWidth = radius * if (major) 0.013f else 0.007f
            val rad = Math.toRadians(degree.toDouble())
            val s = sin(rad).toFloat()
            val c = cos(rad).toFloat()
            canvas.drawLine(cx + s * inner, cy - c * inner, cx + s * outer, cy - c * outer, tickPaint)
        }
        tickPaint.alpha = 255
    }

    private fun drawCardinals(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        labelPaint.textSize = radius * 0.13f
        val distance = radius * 0.68f
        for ((index, degree) in CARDINAL_DEGREES.withIndex()) {
            val rad = Math.toRadians(degree.toDouble())
            val x = cx + sin(rad).toFloat() * distance
            val y = cy - cos(rad).toFloat() * distance + labelPaint.textSize * 0.36f
            labelPaint.color = if (degree == 0) brand else primary
            canvas.save()
            // Letters stay readable: each is turned back upright around its own centre.
            canvas.rotate(headingDegrees, x, y - labelPaint.textSize * 0.36f)
            canvas.drawText(cardinals[index], x, y, labelPaint)
            canvas.restore()
        }
    }

    /** Slim tapered pointer from the hub to the rim, with a short tail. */
    private fun drawNeedle(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        canvas.save()
        canvas.rotate(qiblaDegrees, cx, cy)
        val half = radius * 0.035f
        fillPaint.color = brand
        fillPaint.alpha = if (hasReading) 255 else 110
        path.reset()
        path.moveTo(cx, cy - radius * 0.8f)
        path.lineTo(cx + half, cy)
        path.lineTo(cx, cy + radius * 0.16f)
        path.lineTo(cx - half, cy)
        path.close()
        canvas.drawPath(path, fillPaint)
        fillPaint.alpha = 255
        canvas.restore()
    }

    /**
     * The Kaaba in three-quarter view on a round sand-coloured badge (the marble of the Haram, and
     * enough contrast for the black kiswah in either theme): the black cube with its lit top, the
     * gold band (hizam) around the upper third, and the raised golden door on the front wall.
     */
    private fun drawKaaba(canvas: Canvas, x: Float, y: Float, s: Float, isAligned: Boolean) {
        fillPaint.color = if (isAligned) brand else SAND
        canvas.drawCircle(x, y, s * 1.2f, fillPaint)
        if (!isAligned) {
            strokePaint.color = outline
            strokePaint.strokeWidth = s * 0.06f
            canvas.drawCircle(x, y, s * 1.2f, strokePaint)
        }

        val u = s * 0.7f
        fun poly(color: Int, vararg points: Float) {
            path.reset()
            path.moveTo(x + points[0] * u, y + points[1] * u)
            var i = 2
            while (i < points.size) {
                path.lineTo(x + points[i] * u, y + points[i + 1] * u)
                i += 2
            }
            path.close()
            fillPaint.color = color
            canvas.drawPath(path, fillPaint)
        }

        // Ground shadow.
        fillPaint.color = Color.argb(if (isAligned) 70 else 45, 0, 0, 0)
        canvas.drawOval(x - 1.05f * u, y + 0.82f * u, x + 1.05f * u, y + 1.08f * u, fillPaint)

        // Cube: front wall, side wall (in shade), roof.
        poly(KISWAH, -0.95f, -0.35f, 0.35f, -0.35f, 0.35f, 0.95f, -0.95f, 0.95f)
        poly(KISWAH_SHADE, 0.35f, -0.35f, 0.95f, -0.7f, 0.95f, 0.6f, 0.35f, 0.95f)
        poly(KISWAH_TOP, -0.95f, -0.35f, -0.35f, -0.7f, 0.95f, -0.7f, 0.35f, -0.35f)

        // Hizam: the gold band a third of the way down, following both walls.
        poly(GOLD, -0.95f, -0.12f, 0.35f, -0.12f, 0.35f, 0.02f, -0.95f, 0.02f)
        poly(GOLD_SHADE, 0.35f, -0.12f, 0.95f, -0.47f, 0.95f, -0.33f, 0.35f, 0.02f)

        // Door, raised above the ground, near the corner of the front wall.
        poly(GOLD, 0.02f, 0.28f, 0.24f, 0.28f, 0.24f, 0.74f, 0.02f, 0.74f)
        poly(KISWAH, 0.11f, 0.33f, 0.15f, 0.33f, 0.15f, 0.74f, 0.11f, 0.74f)
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

        val SAND = Color.rgb(0xF1, 0xEA, 0xD8)
        val KISWAH = Color.rgb(0x1C, 0x1C, 0x1E)
        val KISWAH_SHADE = Color.rgb(0x0E, 0x0E, 0x10)
        val KISWAH_TOP = Color.rgb(0x3A, 0x3A, 0x3E)
        val GOLD = Color.rgb(0xD9, 0xAE, 0x4C)
        val GOLD_SHADE = Color.rgb(0xB0, 0x88, 0x33)
    }
}
