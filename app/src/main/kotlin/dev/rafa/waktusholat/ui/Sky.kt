package dev.rafa.waktusholat.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
 * The illustrated sky behind the header: a calm, layered landscape drawn entirely from vector
 * shapes, so it needs no assets and stays sharp at any density.
 *
 * Back to front: the period's gradient, the sun or moon with a soft halo, a few long streak clouds,
 * three bands of rolling hills (each a deeper tint of the sky, which gives depth), and a single
 * slender mosque on the middle ridge whose windows glow after dark. At night the stars twinkle and
 * the occasional shooting star crosses.
 *
 * Motion is time-based and vsync-synced; the sky layer moves slower than the page when scrolling
 * ([parallax]), and the sun or moon rises into place on first show. Paths and shaders are built
 * once per size or period, nothing allocates per frame, and the loop only runs while the view is
 * visible and system animations are enabled.
 */
class SkyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density

    private val skyPaint = Paint(Paint.DITHER_FLAG)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val meteorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val streakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val farPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val midPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nearPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val windowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val farHills = Path()
    private val farCity = Path()
    private val farCityPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val midHills = Path()
    private val nearHills = Path()
    private val mosque = Path()
    private val moon = Path()
    private val moonBite = Path()
    private val streak = RectF()
    private val windows = ArrayList<RectF>()

    /** Stars as (x fraction, y fraction, radius dp, phase, speed). */
    private val stars: Array<FloatArray> = Random(7).let { rnd ->
        Array(56) {
            val depth = rnd.nextFloat()
            floatArrayOf(
                rnd.nextFloat(),
                rnd.nextFloat() * 0.7f,
                0.5f + depth * depth * 1.3f,
                rnd.nextFloat() * 6.28f,
                0.5f + rnd.nextFloat() * 1.5f,
            )
        }
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

    /** Y of the horizon in this view: the foot of the hills. Set by the owner. */
    var horizon: Float = 0f
        set(value) {
            if (field == value) return
            field = value
            rebuild()
        }

    /** Page scroll in pixels; the sky layer drifts at a fraction of it, for depth. */
    var parallax: Float = 0f
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val startedAt = SystemClock.uptimeMillis()
    private var introStart = 0L

    /** Current meteor slot and its (visible, start ms, x fraction, y fraction). */
    private var meteorSlot = -1L
    private val meteor = FloatArray(4)
    private var meteorLength = 0f

    /** Size key the landscape paths were last built for, so colour changes never rebuild them. */
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
                introStart = SystemClock.uptimeMillis()
            }
            hasShown = true
        }
        rebuild()
    }

    /** Advances the colour crossfade; true while it is still running. */
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

    private fun horizonY(): Float = if (horizon > 0f) horizon else height.toFloat()

    private fun rebuild() {
        if (width == 0 || height == 0) return
        val w = width.toFloat()
        val horizon = horizonY()
        skyPaint.shader = LinearGradient(
            0f, 0f, 0f, horizon,
            intArrayOf(shownTop, mix(shownTop, shownBottom, 0.6f), shownBottom),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        )
        // Hills: each band a deeper, slightly desaturated tint of the sky's lower colour.
        val top = horizon - HILLS_DP * density
        farPaint.shader = LinearGradient(
            0f, top, 0f, horizon,
            mix(shownBottom, shownTop, 0.25f), mix(shownBottom, Color.BLACK, 0.22f),
            Shader.TileMode.CLAMP,
        )
        midPaint.color = mix(shownBottom, Color.BLACK, 0.38f)
        farCityPaint.color = mix(shownBottom, shownTop, 0.25f)
        farCityPaint.alpha = 190
        nearPaint.color = mix(shownBottom, Color.BLACK, 0.55f)

        val bodyRadius = (if (celestial.isMoon) 14f else 17f) * density
        val halo = if (celestial.isMoon) 0x4DDDE6FF else 0x73FFE2A8
        haloPaint.shader = RadialGradient(0f, 0f, bodyRadius * 3.6f, halo, 0x00FFFFFF, Shader.TileMode.CLAMP)

        if (pathsFor != w * 31 + horizon) {
            pathsFor = w * 31 + horizon
            buildLandscape(w, horizon)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val horizon = horizonY()
        val now = SystemClock.uptimeMillis()
        val t = (now - startedAt) / 1000f
        val transitioning = stepTransition()
        val intro = if (introStart == 0L) 1f else ((now - introStart) / INTRO_MILLIS.toFloat()).coerceIn(0f, 1f)
        val introEased = 1f - (1f - intro) * (1f - intro) * (1f - intro)

        canvas.drawRect(0f, 0f, w, horizon, skyPaint)

        canvas.save()
        canvas.translate(0f, parallax * PARALLAX)
        drawStars(canvas, w, horizon, t, introEased)
        drawMeteor(canvas, w, horizon, now)
        drawBody(canvas, w, horizon, t, introEased)
        drawStreaks(canvas, w, horizon, t, introEased)
        canvas.restore()

        // The landscape scrolls with the page; the far band drifts a little for depth.
        canvas.save()
        canvas.translate(0f, parallax * PARALLAX * 0.25f)
        canvas.drawPath(farCity, farCityPaint)
        canvas.drawPath(farHills, farPaint)
        canvas.restore()
        canvas.drawPath(midHills, midPaint)
        canvas.drawPath(mosque, midPaint)
        drawWindows(canvas, t)
        canvas.drawPath(nearHills, nearPaint)
        canvas.drawRect(0f, horizon, w, h, nearPaint)

        if (running || transitioning || intro < 1f) postInvalidateOnAnimation()
    }

    private fun drawStars(canvas: Canvas, w: Float, horizon: Float, t: Float, intro: Float) {
        if (period.stars <= 0f) return
        val base = period.stars * intro
        for (s in stars) {
            val twinkle = 0.4f + 0.6f * (0.5f + 0.5f * sin(t * s[4] + s[3]))
            starPaint.alpha = (255 * base * twinkle).toInt()
            canvas.drawCircle(s[0] * w, s[1] * horizon, s[2] * density, starPaint)
        }
    }

    /** Now and then at night, a streak across the upper sky. Parameters are fixed per time slot. */
    private fun drawMeteor(canvas: Canvas, w: Float, horizon: Float, now: Long) {
        if (period != Period.MALAM && period != Period.SUBUH) return
        val slot = now / METEOR_SLOT_MILLIS
        if (slot != meteorSlot) {
            meteorSlot = slot
            val seed = Random(slot * 7919)
            meteor[0] = if (seed.nextFloat() < 0.5f) 1f else 0f
            meteor[1] = seed.nextInt((METEOR_SLOT_MILLIS - METEOR_MILLIS).toInt()).toFloat()
            meteor[2] = 0.1f + seed.nextFloat() * 0.55f
            meteor[3] = 0.05f + seed.nextFloat() * 0.2f
        }
        if (meteor[0] == 0f) return
        val local = now % METEOR_SLOT_MILLIS - meteor[1].toLong()
        if (local !in 0..METEOR_MILLIS) return
        val f = local / METEOR_MILLIS.toFloat()
        val dx = w * 0.3f
        val dy = horizon * 0.14f
        val length = kotlin.math.hypot(dx, dy) * METEOR_TAIL
        if (meteorLength != length) {
            meteorLength = length
            meteorPaint.shader = LinearGradient(-length, 0f, 0f, 0f, 0x00FFFFFF, Color.WHITE, Shader.TileMode.CLAMP)
        }
        meteorPaint.strokeWidth = 1.6f * density
        meteorPaint.alpha = (220 * sin(PI.toFloat() * f)).toInt()
        canvas.save()
        canvas.translate(w * meteor[2] + dx * f, horizon * meteor[3] + dy * f)
        canvas.rotate(Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()).toFloat())
        canvas.drawLine(-length, 0f, 0f, 0f, meteorPaint)
        canvas.restore()
    }

    /** Sun or moon on a shallow arc, rising into place on first show, with a breathing halo. */
    private fun drawBody(canvas: Canvas, w: Float, horizon: Float, t: Float, intro: Float) {
        val p = celestial.progress
        val x = w * (0.1f + 0.8f * p)
        val arc = (horizon - HILLS_DP * density) * 0.78f
        val base = horizon - HILLS_DP * density * 0.4f
        val restY = base - arc * sin(PI.toFloat() * p.coerceIn(-0.2f, 1.2f))
        val y = base + 30 * density + (restY - base - 30 * density) * intro
        if (y > horizon) return
        val r = (if (celestial.isMoon) 14f else 17f) * density
        val breathe = 1f + 0.05f * sin(t * 0.7f)
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(breathe, breathe)
        canvas.drawCircle(0f, 0f, r * 3.6f, haloPaint)
        canvas.restore()

        if (celestial.isMoon) {
            moon.rewind()
            moon.addCircle(x, y, r, Path.Direction.CW)
            moonBite.rewind()
            moonBite.addCircle(x + r * 0.45f, y - r * 0.3f, r * 0.88f, Path.Direction.CW)
            moon.op(moonBite, Path.Op.DIFFERENCE)
            bodyPaint.color = 0xFFF3F1E4.toInt()
            canvas.drawPath(moon, bodyPaint)
        } else {
            val warm = period == Period.SORE || period == Period.SUBUH || period == Period.SENJA
            bodyPaint.color = if (warm) 0xFFFFD49A.toInt() else 0xFFFFF6DD.toInt()
            canvas.drawCircle(x, y, r, bodyPaint)
        }
    }

    /** Long, thin streak clouds that drift slowly: calmer than puffy clouds. */
    private fun drawStreaks(canvas: Canvas, w: Float, horizon: Float, t: Float, intro: Float) {
        if (period.clouds <= 0f) return
        val span = w + 240 * density
        for (i in 0 until STREAKS) {
            val length = (90 + i * 38) * density
            val thickness = (5 + (i % 2) * 3) * density
            val x = ((i * 0.37f * span + t * (4f + i * 2f) * density) % span) - 120 * density
            // Between the header text and the hills, so the streaks never sit behind the title.
            val y = horizon * (0.42f + i * 0.07f)
            streakPaint.alpha = (255 * period.clouds * intro * (0.55f - i * 0.1f)).toInt().coerceAtLeast(0)
            streak.set(x, y, x + length, y + thickness)
            canvas.drawRoundRect(streak, thickness / 2, thickness / 2, streakPaint)
            // A shorter companion just below, which reads as a wisp rather than a bar.
            streak.set(x + length * 0.25f, y + thickness * 1.8f, x + length * 0.8f, y + thickness * 2.6f)
            canvas.drawRoundRect(streak, thickness / 2, thickness / 2, streakPaint)
        }
    }

    /** After dark the mosque's windows glow warm, flickering very gently. */
    private fun drawWindows(canvas: Canvas, t: Float) {
        val lit = when (period) {
            Period.MALAM -> 1f
            Period.SENJA, Period.SUBUH -> 0.7f
            else -> 0f
        }
        if (lit <= 0f) return
        for ((i, rect) in windows.withIndex()) {
            val flicker = 0.85f + 0.15f * sin(t * (1.1f + i * 0.17f) + i)
            windowPaint.color = Color.argb((220 * lit * flicker).toInt(), 255, 206, 120)
            canvas.drawRoundRect(rect, rect.width() / 2, rect.width() / 2, windowPaint)
        }
    }

    /** Three bands of rolling hills and the mosque on the middle ridge. */
    private fun buildLandscape(w: Float, horizon: Float) {
        val d = density
        val hill = HILLS_DP * d
        fun ridgeY(x: Float, baseY: Float, amplitude: Float, waves: Float, phase: Float): Float {
            val a = (x / w) * waves * 2 * PI.toFloat() + phase
            return baseY - amplitude * (0.6f * sin(a) + 0.4f * sin(a * 0.53f + 1.3f))
        }
        fun ridge(path: Path, baseY: Float, amplitude: Float, waves: Float, phase: Float) {
            path.rewind()
            path.moveTo(0f, horizon + 1)
            val steps = 48
            for (i in 0..steps) {
                val x = w * i / steps
                path.lineTo(x, ridgeY(x, baseY, amplitude, waves, phase))
            }
            path.lineTo(w, horizon + 1)
            path.close()
        }
        val farBase = horizon - hill * 0.72f
        ridge(farHills, farBase, hill * 0.16f, 1.3f, 0.6f)
        ridge(midHills, horizon - hill * 0.42f, hill * 0.12f, 0.9f, 2.4f)
        ridge(nearHills, horizon - hill * 0.12f, hill * 0.08f, 1.6f, 4.1f)

        // A distant town on the far ridge: a few small domes and minarets, behind the far hills.
        farCity.rewind()
        for ((fx, minaret, s) in listOf(Triple(0.12f, false, 0.8f), Triple(0.16f, true, 0.9f), Triple(0.2f, false, 0.6f),
            Triple(0.34f, true, 0.7f), Triple(0.38f, false, 0.7f))) {
            val x = w * fx
            val base = ridgeY(x, farBase, hill * 0.16f, 1.3f, 0.6f) + 4 * d
            if (minaret) {
                farCity.addRect(x - 2 * s * d, base - 34 * s * d, x + 2 * s * d, base, Path.Direction.CW)
                farCity.moveTo(x - 3 * s * d, base - 34 * s * d)
                farCity.lineTo(x, base - 42 * s * d)
                farCity.lineTo(x + 3 * s * d, base - 34 * s * d)
                farCity.close()
            } else {
                val r = 10 * s * d
                farCity.addRect(x - r, base - 8 * s * d, x + r, base, Path.Direction.CW)
                farCity.addArc(x - r, base - 8 * s * d - r, x + r, base - 8 * s * d + r, 180f, 180f)
            }
        }

        // Mosque on the middle ridge, at 72% of the width: drum, onion dome with a crescent
        // finial, two slender minarets. Its base sits on the ridge line at that x.
        val cx = w * 0.72f
        val a = 0.72f * 0.9f * 2 * PI.toFloat() + 2.4f
        val base = horizon - hill * 0.42f - hill * 0.12f * (0.6f * sin(a) + 0.4f * sin(a * 0.53f + 1.3f)) + 2 * d
        fun X(v: Float) = cx + v * d
        fun Y(v: Float) = base - v * d
        mosque.rewind()
        mosque.addRect(X(-34f), Y(22f), X(34f), base + 6 * d, Path.Direction.CW)
        mosque.addRect(X(-22f), Y(27f), X(22f), Y(21f), Path.Direction.CW)
        mosque.moveTo(X(-22f), Y(27f))
        mosque.cubicTo(X(-25f), Y(47f), X(-9f), Y(54f), X(0f), Y(61f))
        mosque.cubicTo(X(9f), Y(54f), X(25f), Y(47f), X(22f), Y(27f))
        mosque.close()
        mosque.addRect(X(-0.7f), Y(68f), X(0.7f), Y(60f), Path.Direction.CW)
        val crescent = Path().apply {
            addCircle(X(0f), Y(71f), 3.2f * d, Path.Direction.CW)
            op(Path().apply { addCircle(X(1.4f), Y(71.8f), 2.7f * d, Path.Direction.CW) }, Path.Op.DIFFERENCE)
        }
        mosque.addPath(crescent)
        for (side in floatArrayOf(-1f, 1f)) {
            val mx = 46f * side
            mosque.addRect(X(mx - 2.6f), Y(66f), X(mx + 2.6f), base + 6 * d, Path.Direction.CW)
            mosque.addRect(X(mx - 4.4f), Y(50f), X(mx + 4.4f), Y(47.5f), Path.Direction.CW)
            mosque.moveTo(X(mx - 3.4f), Y(66f))
            mosque.lineTo(X(mx), Y(76f))
            mosque.lineTo(X(mx + 3.4f), Y(66f))
            mosque.close()
        }
        // Date palms beside the mosque, standing on the middle ridge.
        fun palm(x: Float, height: Float, lean: Float) {
            val base = ridgeY(x, horizon - hill * 0.42f, hill * 0.12f, 0.9f, 2.4f) + 3 * d
            val topX = x + lean * height * 0.35f
            val topY = base - height
            val half = height * 0.045f
            mosque.moveTo(x - half, base)
            mosque.quadTo(x + lean * height * 0.3f - half, base - height * 0.55f, topX - half * 0.6f, topY)
            mosque.lineTo(topX + half * 0.6f, topY)
            mosque.quadTo(x + lean * height * 0.3f + half, base - height * 0.55f, x + half, base)
            mosque.close()
            for (degrees in intArrayOf(-165, -140, -115, -90, -65, -40, -15, 10)) {
                val a = Math.toRadians(degrees.toDouble())
                val length = height * if (kotlin.math.abs(degrees + 85) < 40) 0.55f else 0.62f
                val cos = kotlin.math.cos(a).toFloat()
                val sinA = sin(a).toFloat()
                val endX = topX + cos * length
                val endY = topY + sinA * length * 0.55f + length * 0.35f
                val ctrlX = topX + cos * length * 0.5f
                val ctrlY = topY + sinA * length * 0.9f
                val nx = -sinA * height * 0.032f
                val ny = cos * height * 0.032f
                mosque.moveTo(topX, topY)
                mosque.quadTo(ctrlX + nx, ctrlY + ny, endX, endY)
                mosque.quadTo(ctrlX - nx, ctrlY - ny, topX, topY)
                mosque.close()
            }
        }
        palm(cx - 86 * d, 58 * d, -0.2f)
        palm(cx + 92 * d, 50 * d, 0.25f)
        palm(cx + 112 * d, 36 * d, 0.1f)

        windows.clear()
        for (i in -1..1) windows += RectF(X(i * 14f - 3f), Y(16f), X(i * 14f + 3f), Y(6f))
        for (side in floatArrayOf(-1f, 1f)) windows += RectF(X(side * 46f - 1.2f), Y(60f), X(side * 46f + 1.2f), Y(54f))
    }

    // --- Animation lifecycle: run only while visible, never when animations are turned off. ---

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        val enabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
        val shouldRun = isVisible && enabled
        if (!enabled) introStart = 0L
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
        const val TRANSITION_MILLIS = 1200L
        const val INTRO_MILLIS = 1400L
        const val PARALLAX = 0.45f
        const val STREAKS = 3
        const val METEOR_SLOT_MILLIS = 9_000L
        const val METEOR_MILLIS = 900L
        const val METEOR_TAIL = 0.28f

        /** Height of the hills band above the horizon. */
        const val HILLS_DP = 120f

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
