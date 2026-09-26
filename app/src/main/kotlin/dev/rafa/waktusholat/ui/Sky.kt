package dev.rafa.waktusholat.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import dev.rafa.waktusholat.R
import dev.rafa.waktusholat.core.Prayer
import dev.rafa.waktusholat.data.ScheduleRepository
import kotlin.math.PI
import kotlin.math.sin
import java.util.Random

/**
 * The part of the day, bounded by the prayer times themselves rather than by the clock, so the sky
 * turns to dusk exactly at Maghrib wherever the user is. Colours are deep enough that white text
 * stays readable on every one of them, in light and dark theme alike.
 */
enum class Period(val label: Int, val top: Int, val bottom: Int, val stars: Float, val clouds: Float) {
    SUBUH(R.string.period_subuh, 0xFF1B2350.toInt(), 0xFF8E4A6B.toInt(), 0.5f, 0.12f),
    PAGI(R.string.period_pagi, 0xFF1F5FAF.toInt(), 0xFF4A98DA.toInt(), 0f, 0.32f),
    SIANG(R.string.period_siang, 0xFF155E9C.toInt(), 0xFF3B8FCF.toInt(), 0f, 0.3f),
    SORE(R.string.period_sore, 0xFF9A4A1E.toInt(), 0xFFD08236.toInt(), 0f, 0.22f),
    SENJA(R.string.period_senja, 0xFF2B1650.toInt(), 0xFFA8465F.toInt(), 0.45f, 0.1f),
    MALAM(R.string.period_malam, 0xFF070B1C.toInt(), 0xFF1B2752.toInt(), 1f, 0.05f);

    companion object {
        fun of(snapshot: ScheduleRepository.Snapshot): Period {
            val t = snapshot.times
            val m = snapshot.minuteOfDay
            return when {
                m < t[Prayer.FAJR] -> MALAM
                m < t[Prayer.SUNRISE] -> SUBUH
                m < t[Prayer.DHUHR] -> PAGI
                m < t[Prayer.ASR] -> SIANG
                m < t[Prayer.MAGHRIB] -> SORE
                m < t[Prayer.ISHA] -> SENJA
                else -> MALAM
            }
        }
    }
}

/**
 * Where the sun or moon is on its arc: `0` at the eastern horizon, `1` at the western one. The sun
 * travels from sunrise to Maghrib; the moon from Maghrib to the next Fajr. Values outside `0..1`
 * put the body below the horizon, which is how dawn looks before sunrise.
 */
class Celestial(val progress: Float, val isMoon: Boolean) {
    companion object {
        fun of(snapshot: ScheduleRepository.Snapshot): Celestial {
            val t = snapshot.times
            val m = snapshot.minuteOfDay.toFloat()
            val sunrise = t[Prayer.SUNRISE].toFloat()
            val sunset = t[Prayer.MAGHRIB].toFloat()
            if (m >= t[Prayer.FAJR] && m < sunset) {
                return Celestial((m - sunrise) / (sunset - sunrise), isMoon = false)
            }
            // Night spans midnight: measure from Maghrib to the next Fajr on one continuous axis.
            // Before midnight that Fajr is tomorrow's; after midnight it is today's, and the
            // Maghrib it follows was yesterday's (today's differs by at most a minute).
            val afterMidnight = m < t[Prayer.FAJR]
            val fajr = (if (afterMidnight) t[Prayer.FAJR] else snapshot.day.tomorrow[Prayer.FAJR]) + 1440f
            val now = if (afterMidnight) m + 1440f else m
            return Celestial((now - sunset) / (fajr - sunset), isMoon = true)
        }
    }
}

/**
 * The illustrated sky behind the header: the period's gradient, the sun or moon on its arc, stars
 * that twinkle, clouds that drift, and a mosque skyline standing on the horizon, all drawn as
 * vector shapes so it needs no image assets and stays sharp at any density.
 *
 * Below the horizon is solid ground, behind the next-prayer card; the page sheet laid over its
 * bottom gives the scene a clean edge. A change of period crossfades over about a second.
 *
 * Animation is deliberately cheap: paths are built once per size, frames are capped at 30 fps, and
 * the loop only runs while the view is actually visible and system animations are enabled. The
 * gradient is dithered so it shows no bands on 8-bit panels.
 */
class SkyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val skyPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val groundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val farPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mosquePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    private val mosque = Path()
    private val houses = Path()
    private val cloud = Path()
    private val moon = Path()

    /** Stars as (x fraction, y fraction, radius dp, phase). */
    private val stars: Array<FloatArray> = Random(7).let { rnd ->
        Array(46) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat() * 0.85f, 0.6f + rnd.nextFloat() * 1.1f, rnd.nextFloat() * 6.28f) }
    }

    private var period = Period.MALAM
    private var celestial = Celestial(0.5f, isMoon = true)

    /** Colours currently painted; they ease towards [period]'s over [TRANSITION_MILLIS]. */
    private var shownTop = period.top
    private var shownBottom = period.bottom
    private var fromTop = shownTop
    private var fromBottom = shownBottom
    private var transitionStart = 0L
    private var hasShown = false

    /** Y of the horizon in this view, i.e. where the skyline stands. Set by the owner. */
    var horizon: Float = 0f
        set(value) {
            if (field == value) return
            field = value
            rebuild()
        }

    private val startedAt = SystemClock.uptimeMillis()

    /** Size key the skyline paths were last built for, so colour changes never rebuild them. */
    private var pathsFor = -1f
    private var running = false

    fun show(period: Period, celestial: Celestial) {
        val changed = period != this.period || !hasShown
        this.period = period
        this.celestial = celestial
        if (changed) {
            if (hasShown && running) {
                fromTop = shownTop
                fromBottom = shownBottom
                transitionStart = SystemClock.uptimeMillis()
            } else {
                shownTop = period.top
                shownBottom = period.bottom
                transitionStart = 0L
            }
            hasShown = true
            rebuild()
        }
        invalidate()
    }

    /** Advances the crossfade; true while it is still running. */
    private fun stepTransition(): Boolean {
        if (transitionStart == 0L) return false
        val f = ((SystemClock.uptimeMillis() - transitionStart) / TRANSITION_MILLIS.toFloat()).coerceIn(0f, 1f)
        val eased = f * f * (3 - 2 * f)
        shownTop = mix(fromTop, period.top, eased)
        shownBottom = mix(fromBottom, period.bottom, eased)
        if (f >= 1f) transitionStart = 0L
        rebuild()
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = rebuild()

    private fun rebuild() {
        if (width == 0 || height == 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        val horizon = if (horizon > 0f) horizon else h
        val ground = mix(shownBottom, Color.BLACK, 0.55f)
        skyPaint.shader = LinearGradient(0f, 0f, 0f, horizon, shownTop, shownBottom, Shader.TileMode.CLAMP)
        groundPaint.color = ground
        farPaint.color = mix(shownBottom, Color.BLACK, 0.32f)
        mosquePaint.color = ground
        if (pathsFor != w * 31 + horizon) {
            pathsFor = w * 31 + horizon
            buildHouses(w, horizon)
            buildMosque(w * 0.68f, horizon)
            buildCloud()
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val horizon = if (horizon > 0f) horizon else h
        val t = (SystemClock.uptimeMillis() - startedAt) / 1000f
        val transitioning = stepTransition()

        canvas.drawRect(0f, 0f, w, horizon, skyPaint)
        canvas.drawRect(0f, horizon, w, h, groundPaint)
        drawStars(canvas, w, horizon, t)
        drawBody(canvas, w, horizon, t)
        drawClouds(canvas, w, horizon, t)

        canvas.drawPath(houses, farPaint)
        canvas.drawPath(mosque, mosquePaint)

        if (running || transitioning) postInvalidateDelayed(FRAME_MILLIS)
    }

    private fun drawStars(canvas: Canvas, w: Float, horizon: Float, t: Float) {
        if (period.stars <= 0f) return
        for (s in stars) {
            val twinkle = 0.55f + 0.45f * sin(t * 1.6f + s[3])
            starPaint.alpha = (255 * period.stars * twinkle).toInt()
            canvas.drawCircle(s[0] * w, s[1] * horizon * 0.9f, s[2] * density, starPaint)
        }
    }

    /** Sun or moon on a shallow arc from the left edge to the right, with a soft breathing glow. */
    private fun drawBody(canvas: Canvas, w: Float, horizon: Float, t: Float) {
        val p = celestial.progress
        val x = w * (0.08f + 0.84f * p)
        val arc = horizon * 0.62f
        val y = horizon + 18 * density - arc * sin(PI.toFloat() * p.coerceIn(-0.2f, 1.2f))
        if (y > horizon + 30 * density) return
        val r = (if (celestial.isMoon) 15f else 18f) * density
        val breathe = 1f + 0.05f * sin(t * 0.9f)

        val glow = if (celestial.isMoon) 0x55DDE6FF else 0x77FFE2A8
        glowPaint.shader = RadialGradient(x, y, r * 3.2f * breathe, glow, 0x00FFFFFF, Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, r * 3.2f * breathe, glowPaint)

        if (celestial.isMoon) {
            moon.reset()
            moon.addCircle(x, y, r, Path.Direction.CW)
            val bite = Path().apply { addCircle(x + r * 0.45f, y - r * 0.3f, r * 0.88f, Path.Direction.CW) }
            moon.op(bite, Path.Op.DIFFERENCE)
            bodyPaint.color = 0xFFF3F1E4.toInt()
            canvas.drawPath(moon, bodyPaint)
        } else {
            bodyPaint.color = if (period == Period.SORE || period == Period.SUBUH) 0xFFFFD08A.toInt() else 0xFFFFF4D6.toInt()
            canvas.drawCircle(x, y, r, bodyPaint)
        }
    }

    private fun drawClouds(canvas: Canvas, w: Float, horizon: Float, t: Float) {
        if (period.clouds <= 0f) return
        val span = w + 160 * density
        for (i in 0 until 3) {
            val speed = (6f + i * 3f) * density
            val x = ((i * 0.37f * span + t * speed) % span) - 80 * density
            val y = horizon * (0.28f + i * 0.17f)
            val scale = 1f - i * 0.18f
            cloudPaint.alpha = (255 * period.clouds * (1f - i * 0.22f)).toInt()
            canvas.save()
            canvas.translate(x, y)
            canvas.scale(scale, scale)
            canvas.drawPath(cloud, cloudPaint)
            canvas.restore()
        }
    }

    /** A soft cloud: overlapping circles on a flat base, around the origin. */
    private fun buildCloud() {
        val d = density
        cloud.reset()
        cloud.addCircle(-26 * d, 4 * d, 14 * d, Path.Direction.CW)
        cloud.addCircle(0f, -4 * d, 20 * d, Path.Direction.CW)
        cloud.addCircle(26 * d, 4 * d, 15 * d, Path.Direction.CW)
        cloud.addRoundRect(-40 * d, 2 * d, 42 * d, 18 * d, 9 * d, 9 * d, Path.Direction.CW)
    }

    /** A low, uneven row of rooftops across the whole width, behind the mosque. */
    private fun buildHouses(w: Float, horizon: Float) {
        val d = density
        houses.reset()
        houses.moveTo(0f, horizon)
        val rnd = Random(11)
        var x = 0f
        while (x < w) {
            val bw = (18 + rnd.nextInt(26)) * d
            val bh = (8 + rnd.nextInt(18)) * d
            houses.lineTo(x, horizon - bh)
            if (rnd.nextInt(5) == 0) {
                // A small dome on every few roofs.
                houses.quadTo(x + bw / 2, horizon - bh - 12 * d, x + bw, horizon - bh)
            } else {
                houses.lineTo(x + bw, horizon - bh)
            }
            x += bw
        }
        houses.lineTo(w, horizon)
        houses.close()
    }

    /** A mosque: hall, drum and onion dome with a crescent finial, side domes and two minarets. */
    private fun buildMosque(cx: Float, base: Float) {
        val d = density
        fun X(v: Float) = cx + v * d
        fun Y(v: Float) = base - v * d
        mosque.reset()
        // Hall and wings.
        mosque.addRect(X(-58f), Y(34f), X(58f), base, Path.Direction.CW)
        mosque.addRect(X(-96f), Y(20f), X(96f), base, Path.Direction.CW)
        // Drum and main dome.
        mosque.addRect(X(-36f), Y(40f), X(36f), Y(33f), Path.Direction.CW)
        mosque.moveTo(X(-36f), Y(40f))
        mosque.cubicTo(X(-40f), Y(70f), X(-14f), Y(80f), X(0f), Y(90f))
        mosque.cubicTo(X(14f), Y(80f), X(40f), Y(70f), X(36f), Y(40f))
        mosque.close()
        // Finial with a crescent.
        mosque.addRect(X(-1f), Y(100f), X(1f), Y(89f), Path.Direction.CW)
        val crescent = Path().apply {
            addCircle(X(0f), Y(104f), 4.5f * d, Path.Direction.CW)
            op(Path().apply { addCircle(X(2f), Y(105f), 3.8f * d, Path.Direction.CW) }, Path.Op.DIFFERENCE)
        }
        mosque.addPath(crescent)
        // Side domes.
        for (side in floatArrayOf(-1f, 1f)) {
            val sx = 76f * side
            mosque.moveTo(X(sx - 14f), Y(20f))
            mosque.cubicTo(X(sx - 14f), Y(34f), X(sx - 4f), Y(38f), X(sx), Y(42f))
            mosque.cubicTo(X(sx + 4f), Y(38f), X(sx + 14f), Y(34f), X(sx + 14f), Y(20f))
            mosque.close()
        }
        // Minarets: shaft, balcony, cap.
        for (side in floatArrayOf(-1f, 1f)) {
            val mx = 112f * side
            mosque.addRect(X(mx - 4.5f), Y(104f), X(mx + 4.5f), base, Path.Direction.CW)
            mosque.addRect(X(mx - 7.5f), Y(80f), X(mx + 7.5f), Y(76f), Path.Direction.CW)
            mosque.addRect(X(mx - 6f), Y(108f), X(mx + 6f), Y(103f), Path.Direction.CW)
            mosque.moveTo(X(mx - 6f), Y(108f))
            mosque.lineTo(X(mx), Y(124f))
            mosque.lineTo(X(mx + 6f), Y(108f))
            mosque.close()
        }
    }

    // --- Animation lifecycle: run only while visible, never when animations are turned off. ---

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        val enabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
        val shouldRun = isVisible && enabled
        if (shouldRun != running) {
            running = shouldRun
            if (running) invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        running = false
        super.onDetachedFromWindow()
    }

    private companion object {
        const val FRAME_MILLIS = 33L
        const val TRANSITION_MILLIS = 1200L

        fun mix(a: Int, b: Int, amount: Float): Int {
            val inv = 1f - amount
            return Color.rgb(
                (Color.red(a) * inv + Color.red(b) * amount).toInt(),
                (Color.green(a) * inv + Color.green(b) * amount).toInt(),
                (Color.blue(a) * inv + Color.blue(b) * amount).toInt(),
            )
        }
    }
}
