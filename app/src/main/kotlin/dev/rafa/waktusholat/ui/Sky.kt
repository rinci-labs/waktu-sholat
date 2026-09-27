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
 * The illustrated sky behind the header, drawn entirely from vector shapes so it needs no assets
 * and stays sharp at any density:
 *
 * - the period's gradient with a soft glow on the horizon beneath the sun or moon;
 * - the sun (with slowly turning rays by day) or a crescent moon, on its arc across the sky;
 * - stars with depth that twinkle at their own pace, and the occasional shooting star at night;
 * - two layers of soft clouds drifting at different speeds, tinted warm at dawn and dusk;
 * - a small flock of birds crossing in the morning and afternoon;
 * - a mosque skyline whose windows glow warmly after dark.
 *
 * Motion is time-based and synced to the display's vsync, so it is smooth at 60 or 120 Hz. The
 * celestial layer moves slower than the page when scrolling ([parallax]), and on first show the sun
 * or moon rises into place. Paths and shaders are built once per size or period, nothing allocates
 * per frame, and the loop only runs while the view is visible and system animations are enabled.
 */
class SkyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density

    private val skyPaint = Paint(Paint.DITHER_FLAG)
    private val horizonGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val groundPaint = Paint()
    private val farPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mosquePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val windowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val sparklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeCap = Paint.Cap.ROUND }
    private val meteorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val birdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val mosque = Path()
    private val houses = Path()
    private val cloud = Path()
    private val moon = Path()
    private val moonBite = Path()
    private val bird = Path()
    private val windows = ArrayList<RectF>()

    /** Stars as (x fraction, y fraction, radius dp, phase, speed). */
    private val stars: Array<FloatArray> = Random(7).let { rnd ->
        Array(64) {
            val depth = rnd.nextFloat()
            floatArrayOf(
                rnd.nextFloat(),
                rnd.nextFloat() * 0.82f,
                0.5f + depth * depth * 1.5f,
                rnd.nextFloat() * 6.28f,
                0.6f + rnd.nextFloat() * 1.8f,
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

    /** Y of the horizon in this view, i.e. where the skyline stands. Set by the owner. */
    var horizon: Float = 0f
        set(value) {
            if (field == value) return
            field = value
            rebuild()
        }

    /**
     * Page scroll in pixels. The sky layer (stars, sun or moon, clouds, birds) is shifted down by a
     * fraction of it, so it drifts away more slowly than the skyline and the content: depth.
     */
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
        val ground = mix(shownBottom, Color.BLACK, 0.55f)
        skyPaint.shader = LinearGradient(
            0f, 0f, 0f, horizon,
            intArrayOf(shownTop, mix(shownTop, shownBottom, 0.55f), shownBottom),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        // A wide, soft glow on the horizon under the sun or moon: warm at dawn and dusk.
        val glowColor = when (period) {
            Period.SUBUH, Period.SORE, Period.SENJA -> 0x66FFB37A
            Period.MALAM -> 0x224F6BFF
            else -> 0x33FFFFFF
        }
        val cx = w * (0.08f + 0.84f * celestial.progress.coerceIn(0f, 1f))
        horizonGlowPaint.shader = RadialGradient(cx, horizon, w * 0.75f, glowColor, 0x00FFFFFF, Shader.TileMode.CLAMP)
        val bodyRadius = (if (celestial.isMoon) 15f else 18f) * density
        val glow = if (celestial.isMoon) 0x55DDE6FF else 0x80FFE2A8.toInt()
        glowPaint.shader = RadialGradient(0f, 0f, bodyRadius * 3.4f, glow, 0x00FFFFFF, Shader.TileMode.CLAMP)
        groundPaint.color = ground
        farPaint.color = mix(shownBottom, Color.BLACK, 0.32f)
        mosquePaint.color = ground
        val tint = when (period) {
            Period.SUBUH, Period.SORE, Period.SENJA -> mix(Color.WHITE, 0xFFFFB38A.toInt(), 0.35f)
            else -> Color.WHITE
        }
        cloudPaint.color = tint
        birdPaint.color = mix(shownBottom, Color.BLACK, 0.6f)
        birdPaint.strokeWidth = 1.6f * density
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
        val horizon = horizonY()
        val now = SystemClock.uptimeMillis()
        val t = (now - startedAt) / 1000f
        val transitioning = stepTransition()
        // Intro: 0 -> 1 over the first moments after the scene appears, eased out.
        val intro = if (introStart == 0L) 1f else ((now - introStart) / INTRO_MILLIS.toFloat()).coerceIn(0f, 1f)
        val introEased = 1f - (1f - intro) * (1f - intro) * (1f - intro)

        canvas.drawRect(0f, 0f, w, horizon, skyPaint)
        canvas.drawRect(0f, 0f, w, horizon, horizonGlowPaint)

        // Sky layer, with parallax. Everything here is later covered by the skyline and ground.
        canvas.save()
        canvas.translate(0f, parallax * PARALLAX)
        drawStars(canvas, w, horizon, t, introEased)
        drawMeteor(canvas, w, horizon, now)
        drawBody(canvas, w, horizon, t, introEased)
        drawClouds(canvas, w, horizon, t, introEased)
        drawBirds(canvas, w, horizon, now)
        canvas.restore()

        canvas.drawPath(houses, farPaint)
        canvas.drawPath(mosque, mosquePaint)
        drawWindows(canvas, t)
        canvas.drawRect(0f, horizon, w, h, groundPaint)

        if (running || transitioning || intro < 1f) postInvalidateOnAnimation()
    }

    private fun drawStars(canvas: Canvas, w: Float, horizon: Float, t: Float, intro: Float) {
        if (period.stars <= 0f) return
        val base = period.stars * intro
        for ((i, s) in stars.withIndex()) {
            val twinkle = 0.45f + 0.55f * (0.5f + 0.5f * sin(t * s[4] + s[3]))
            val alpha = base * twinkle
            val x = s[0] * w
            val y = s[1] * horizon * 0.92f
            val r = s[2] * density
            starPaint.alpha = (255 * alpha).toInt()
            canvas.drawCircle(x, y, r, starPaint)
            // The brightest few get a faint four-point sparkle.
            if (i % 11 == 0 && twinkle > 0.8f) {
                val len = r * 4f * (twinkle - 0.8f) / 0.2f
                sparklePaint.alpha = (140 * alpha).toInt()
                sparklePaint.strokeWidth = r * 0.5f
                canvas.drawLine(x - len, y, x + len, y, sparklePaint)
                canvas.drawLine(x, y - len, x, y + len, sparklePaint)
            }
        }
    }

    /** Now and then at night, a streak across the upper sky. Parameters are fixed per time slot. */
    private fun drawMeteor(canvas: Canvas, w: Float, horizon: Float, now: Long) {
        if (period != Period.MALAM && period != Period.SUBUH) return
        val slot = now / METEOR_SLOT_MILLIS
        if (slot != meteorSlot) {
            meteorSlot = slot
            val seed = Random(slot * 7919)
            meteor[0] = if (seed.nextFloat() < 0.55f) 1f else 0f
            meteor[1] = seed.nextInt((METEOR_SLOT_MILLIS - METEOR_MILLIS).toInt()).toFloat()
            meteor[2] = 0.15f + seed.nextFloat() * 0.6f
            meteor[3] = 0.05f + seed.nextFloat() * 0.25f
        }
        if (meteor[0] == 0f) return
        val local = now % METEOR_SLOT_MILLIS - meteor[1].toLong()
        if (local !in 0..METEOR_MILLIS) return
        val f = local / METEOR_MILLIS.toFloat()
        val dx = w * 0.32f
        val dy = horizon * 0.18f
        val length = kotlin.math.hypot(dx, dy) * METEOR_TAIL
        if (meteorLength != length) {
            // Along +x from the tail (transparent) to the head (white); rotated into place per frame.
            meteorLength = length
            meteorPaint.shader = LinearGradient(-length, 0f, 0f, 0f, 0x00FFFFFF, Color.WHITE, Shader.TileMode.CLAMP)
        }
        meteorPaint.strokeWidth = 1.8f * density
        meteorPaint.alpha = (230 * sin(PI.toFloat() * f)).toInt()
        canvas.save()
        canvas.translate(w * meteor[2] + dx * f, horizon * meteor[3] + dy * f)
        canvas.rotate(Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()).toFloat())
        canvas.drawLine(-length, 0f, 0f, 0f, meteorPaint)
        canvas.restore()
    }

    /** Sun or moon on a shallow arc, rising into place on first show, with a breathing glow. */
    private fun drawBody(canvas: Canvas, w: Float, horizon: Float, t: Float, intro: Float) {
        val p = celestial.progress
        val x = w * (0.08f + 0.84f * p)
        val arc = horizon * 0.62f
        val restY = horizon + 18 * density - arc * sin(PI.toFloat() * p.coerceIn(-0.2f, 1.2f))
        val y = horizon + 40 * density + (restY - horizon - 40 * density) * intro
        if (y > horizon + 30 * density) return
        val r = (if (celestial.isMoon) 15f else 18f) * density

        // Glow: one shader built around the origin (in rebuild), breathing by scaling the canvas.
        val breathe = 1f + 0.06f * sin(t * 0.8f)
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(breathe, breathe)
        canvas.drawCircle(0f, 0f, r * 3.4f, glowPaint)
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
            if (!warm) {
                // Slowly turning rays: twelve faint strokes around the disc.
                rayPaint.color = 0x2EFFF4D6
                rayPaint.strokeWidth = 2.2f * density
                val turn = t * 6f
                for (i in 0 until 12) {
                    val a = Math.toRadians((turn + i * 30f).toDouble())
                    val c = kotlin.math.cos(a).toFloat()
                    val s = sin(a).toFloat()
                    val inner = r * 1.5f
                    val outer = r * (2.2f + 0.25f * sin(t * 1.3f + i))
                    canvas.drawLine(x + c * inner, y + s * inner, x + c * outer, y + s * outer, rayPaint)
                }
            }
            bodyPaint.color = if (warm) 0xFFFFD08A.toInt() else 0xFFFFF4D6.toInt()
            canvas.drawCircle(x, y, r, bodyPaint)
        }
    }

    /** Two parallax layers of soft clouds; each has a faint halo, which reads as a soft edge. */
    private fun drawClouds(canvas: Canvas, w: Float, horizon: Float, t: Float, intro: Float) {
        if (period.clouds <= 0f) return
        val span = w + 200 * density
        for (i in 0 until CLOUDS) {
            val far = i % 2 == 1
            val speed = (if (far) 4f else 9f + i) * density
            val x = ((i * 0.29f * span + t * speed) % span) - 100 * density
            val y = horizon * (0.18f + (i * 0.13f) % 0.5f) + sin(t * 0.35f + i) * 2 * density
            val scale = if (far) 0.62f else 1f - i * 0.08f
            val alpha = period.clouds * intro * if (far) 0.55f else 1f
            canvas.save()
            canvas.translate(x, y)
            canvas.scale(scale * 1.1f, scale * 1.1f)
            cloudPaint.alpha = (255 * alpha * 0.28f).toInt()
            canvas.drawPath(cloud, cloudPaint)
            canvas.scale(1f / 1.1f, 1f / 1.1f)
            cloudPaint.alpha = (255 * alpha).toInt()
            canvas.drawPath(cloud, cloudPaint)
            canvas.restore()
        }
    }

    /** A flock of three crossing every so often in the morning and afternoon, wings flapping. */
    private fun drawBirds(canvas: Canvas, w: Float, horizon: Float, now: Long) {
        if (period != Period.PAGI && period != Period.SORE) return
        val local = now % BIRD_CYCLE_MILLIS
        if (local > BIRD_FLIGHT_MILLIS) return
        val f = local / BIRD_FLIGHT_MILLIS.toFloat()
        val baseX = -40 * density + (w + 80 * density) * f
        val baseY = horizon * 0.45f - f * horizon * 0.12f
        for (i in 0 until 3) {
            val bx = baseX - i * 16 * density
            val by = baseY + (if (i == 1) -8f else i * 6f) * density
            val flap = sin((now / 1000f) * 9f + i * 1.7f)
            val span = 6 * density
            val lift = span * 0.55f * flap
            bird.rewind()
            bird.moveTo(bx - span, by - lift)
            bird.quadTo(bx - span * 0.4f, by - span * 0.35f, bx, by)
            bird.quadTo(bx + span * 0.4f, by - span * 0.35f, bx + span, by - lift)
            canvas.drawPath(bird, birdPaint)
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
            windowPaint.color = Color.argb((210 * lit * flicker).toInt(), 255, 206, 120)
            canvas.drawRoundRect(rect, rect.width() / 2, rect.width() / 2, windowPaint)
        }
    }

    /** A soft cloud: overlapping circles on a flat base, around the origin. */
    private fun buildCloud() {
        val d = density
        cloud.reset()
        cloud.addCircle(-28 * d, 5 * d, 13 * d, Path.Direction.CW)
        cloud.addCircle(-6 * d, -5 * d, 19 * d, Path.Direction.CW)
        cloud.addCircle(18 * d, -1 * d, 15 * d, Path.Direction.CW)
        cloud.addCircle(36 * d, 6 * d, 11 * d, Path.Direction.CW)
        cloud.addRoundRect(-40 * d, 4 * d, 46 * d, 18 * d, 7 * d, 7 * d, Path.Direction.CW)
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
                houses.quadTo(x + bw / 2, horizon - bh - 12 * d, x + bw, horizon - bh)
            } else {
                houses.lineTo(x + bw, horizon - bh)
            }
            x += bw
        }
        houses.lineTo(w, horizon)
        houses.close()
    }

    /** A mosque: hall, drum and onion dome with a crescent finial, side domes, two minarets. */
    private fun buildMosque(cx: Float, base: Float) {
        val d = density
        fun X(v: Float) = cx + v * d
        fun Y(v: Float) = base - v * d
        mosque.reset()
        mosque.addRect(X(-58f), Y(34f), X(58f), base, Path.Direction.CW)
        mosque.addRect(X(-96f), Y(20f), X(96f), base, Path.Direction.CW)
        mosque.addRect(X(-36f), Y(40f), X(36f), Y(33f), Path.Direction.CW)
        mosque.moveTo(X(-36f), Y(40f))
        mosque.cubicTo(X(-40f), Y(70f), X(-14f), Y(80f), X(0f), Y(90f))
        mosque.cubicTo(X(14f), Y(80f), X(40f), Y(70f), X(36f), Y(40f))
        mosque.close()
        mosque.addRect(X(-1f), Y(100f), X(1f), Y(89f), Path.Direction.CW)
        val crescent = Path().apply {
            addCircle(X(0f), Y(104f), 4.5f * d, Path.Direction.CW)
            op(Path().apply { addCircle(X(2f), Y(105f), 3.8f * d, Path.Direction.CW) }, Path.Op.DIFFERENCE)
        }
        mosque.addPath(crescent)
        for (side in floatArrayOf(-1f, 1f)) {
            val sx = 76f * side
            mosque.moveTo(X(sx - 14f), Y(20f))
            mosque.cubicTo(X(sx - 14f), Y(34f), X(sx - 4f), Y(38f), X(sx), Y(42f))
            mosque.cubicTo(X(sx + 4f), Y(38f), X(sx + 14f), Y(34f), X(sx + 14f), Y(20f))
            mosque.close()
        }
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
        // Arched windows along the hall and wings, and one on each minaret.
        windows.clear()
        for (i in -2..2) windows += RectF(X(i * 18f - 3.5f), Y(26f), X(i * 18f + 3.5f), Y(12f))
        for (side in floatArrayOf(-1f, 1f)) {
            windows += RectF(X(side * 76f - 3f), Y(14f), X(side * 76f + 3f), Y(5f))
            windows += RectF(X(side * 112f - 1.8f), Y(96f), X(side * 112f + 1.8f), Y(88f))
        }
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
        const val CLOUDS = 5
        const val METEOR_SLOT_MILLIS = 9_000L
        const val METEOR_MILLIS = 900L
        const val METEOR_TAIL = 0.28f
        const val BIRD_CYCLE_MILLIS = 22_000L
        const val BIRD_FLIGHT_MILLIS = 9_000L

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
