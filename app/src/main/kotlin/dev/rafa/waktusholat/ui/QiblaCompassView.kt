package dev.rafa.waktusholat.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

/**
 * Compass rose with a Qibla needle.
 *
 * Everything is drawn rather than composited from images so the view scales to any size, follows the
 * theme without a second set of assets, and needs no layout file. The rose rotates by the device
 * heading, which keeps the needle's own angle fixed relative to the dial: the needle always points at
 * the Qibla on the screen.
 *
 * The view is [View] rather than a `TextureView` wrapper so it costs one hardware layer and no
 * camera preview; the sensor is read by the owning activity.
 */
class QiblaCompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val dialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val cardinalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val arrow = Path()

    /** Device heading in degrees clockwise from north; the rose rotates by the negative of this. */
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

    /** True once the sensor has produced a reading, which lets the owner show a hint until then. */
    var hasReading: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private fun color(attr: Int, fallback: Int): Int {
        val value = TypedValue()
        return if (context.theme.resolveAttribute(attr, value, true)) {
            if (value.resourceId != 0) context.getColor(value.resourceId) else value.data
        } else {
            fallback
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(cx, cy) * 0.86f

        val accent = color(android.R.attr.colorAccent, 0xFF1B7F5A.toInt())
        val onSurface = color(android.R.attr.textColorPrimary, 0xFF171A18.toInt())
        val muted = color(android.R.attr.textColorSecondary, 0xFF5A635E.toInt())

        dialPaint
        dialPaint.color = muted
        dialPaint.alpha = 90
        dialPaint.strokeWidth = radius * 0.010f
        canvas.drawCircle(cx, cy, radius, dialPaint)

        // The dial turns with the device; the needle is drawn in the rotated frame so it lands on
        // the Qibla's true screen position.
        canvas.save()
        canvas.rotate(-headingDegrees, cx, cy)

        drawTicks(canvas, cx, cy, radius, muted, accent)
        drawCardinals(canvas, cx, cy, radius, onSurface, accent)
        drawNeedle(canvas, cx, cy, radius, accent)

        canvas.restore()

        // Centre hub stays unrotated so the pivot does not appear to spin.
        hubPaint.color = if (hasReading) accent else muted
        canvas.drawCircle(cx, cy, radius * 0.045f, hubPaint)
    }

    /** Degree ticks: longer and brighter every 30 degrees, longer still at the cardinals. */
    private fun drawTicks(canvas: Canvas, cx: Float, cy: Float, radius: Float, muted: Int, accent: Int) {
        val outer = radius * 0.94f
        for (degree in 0 until 360 step 5) {
            val cardinal = degree % 90 == 0
            val major = degree % 30 == 0
            val inner = when {
                cardinal -> radius * 0.80f
                major -> radius * 0.84f
                else -> radius * 0.89f
            }
            tickPaint.color = when {
                cardinal -> accent
                major -> muted
                else -> muted
            }
            tickPaint.alpha = if (cardinal || major) 255 else 110
            tickPaint.strokeWidth = if (cardinal) radius * 0.016f else radius * 0.008f

            val radians = Math.toRadians(degree.toDouble())
            val sin = kotlin.math.sin(radians).toFloat()
            val cos = kotlin.math.cos(radians).toFloat()
            canvas.drawLine(
                cx + sin * inner,
                cy - cos * inner,
                cx + sin * outer,
                cy - cos * outer,
                tickPaint,
            )
        }
    }

    private fun drawCardinals(canvas: Canvas, cx: Float, cy: Float, radius: Float, onSurface: Int, accent: Int) {
        cardinalPaint.textSize = radius * 0.15f
        val distance = radius * 0.68f
        for ((label, degree) in CARDINALS) {
            val radians = Math.toRadians(degree.toDouble())
            val x = cx + kotlin.math.sin(radians).toFloat() * distance
            // Vertically centre the glyph on its baseline offset.
            val y = cy - kotlin.math.cos(radians).toFloat() * distance + cardinalPaint.textSize * 0.35f
            cardinalPaint.color = if (label == "U") accent else onSurface
            canvas.drawText(label, x, y, cardinalPaint)
        }
    }

    /** Needle: a slim tapered pointer from the hub to the rim, plus a short tail. */
    private fun drawNeedle(canvas: Canvas, cx: Float, cy: Float, radius: Float, accent: Int) {
        canvas.save()
        canvas.rotate(qiblaDegrees, cx, cy)

        val tip = radius * 0.78f
        val tail = radius * 0.22f
        val halfWidth = radius * 0.055f

        needlePaint.color = accent
        needlePaint.alpha = if (hasReading) 255 else 120
        arrow.reset()
        arrow.moveTo(cx, cy - tip)
        arrow.lineTo(cx + halfWidth, cy - radius * 0.10f)
        arrow.lineTo(cx - halfWidth, cy - radius * 0.10f)
        arrow.close()
        canvas.drawPath(arrow, needlePaint)

        needlePaint.alpha = if (hasReading) 90 else 50
        arrow.reset()
        arrow.moveTo(cx, cy + tail)
        arrow.lineTo(cx + halfWidth * 0.7f, cy)
        arrow.lineTo(cx - halfWidth * 0.7f, cy)
        arrow.close()
        canvas.drawPath(arrow, needlePaint)

        canvas.restore()
    }

    /**
     * Always square. Inside a scrolling parent the height arrives as `wrap_content`, so the width is
     * the constraint that decides the size; an explicit height is honoured only when it is smaller.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val widthIsBounded = MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED
        val heightIsBounded = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY

        val size = when {
            heightIsBounded -> minOf(width, height)
            widthIsBounded -> width
            else -> minOf(width, height)
        }
        setMeasuredDimension(size, size)
    }

    private companion object {
        /** Indonesian cardinal points, in compass order. */
        val CARDINALS = listOf("U" to 0, "T" to 90, "S" to 180, "B" to 270)
    }
}
