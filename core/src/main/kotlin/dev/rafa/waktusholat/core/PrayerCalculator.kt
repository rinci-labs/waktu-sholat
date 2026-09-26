package dev.rafa.waktusholat.core

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * How twilight is handled when the sun never reaches the Fajr/Isha angle. In Indonesia the plain
 * twilight angle is always reachable, so this only applies to coordinates outside the archipelago.
 */
enum class HighLatitudeRule {
    /** No adjustment; the twilight angle is used as-is and may not be reached. */
    TWILIGHT_ANGLE,

    /** Fajr/Isha fall at the middle of the night when the angle is never reached. */
    MIDDLE_OF_THE_NIGHT,

    /** Fajr/Isha fall one seventh of the night from the horizon when the angle is never reached. */
    SEVENTH_OF_THE_NIGHT,
}

/**
 * One twilight/asr convention. Angles are degrees below the horizon for Fajr and Isha; Isha may
 * instead be a fixed interval after sunset, which is what Umm al-Qura and Qatar use.
 */
data class CalculationConfig(
    val fajrAngle: Double,
    val ishaAngle: Double = 0.0,
    /** When > 0, Isha is sunset plus this many minutes instead of an angle. */
    val ishaIntervalMinutes: Int = 0,
    /** When > 0, Maghrib is the sun's descent to this angle below the horizon. */
    val maghribAngle: Double = 0.0,
    val maghribIntervalMinutes: Int = 0,
    /** Fixed offset added to solar noon, in minutes. */
    val dhuhrIntervalMinutes: Int = 0,
    /** 1.0 = Shafi'i/Maliki/Hanbali (shadow length 1x), 2.0 = Hanafi (2x). */
    val asrFactor: Double = 1.0,
    val highLatitude: HighLatitudeRule = HighLatitudeRule.TWILIGHT_ANGLE,
) {
    init {
        require(fajrAngle in 0.0..30.0) { "fajrAngle out of range: $fajrAngle" }
        require(ishaAngle in 0.0..30.0) { "ishaAngle out of range: $ishaAngle" }
        require(asrFactor > 0.0) { "asrFactor must be positive: $asrFactor" }
    }

    fun withMadhab(madhab: Madhab): CalculationConfig = copy(asrFactor = madhab.asrFactor)
}

/** One day's schedule: local wall-clock minutes since midnight, indexed by [Prayer]. */
class PrayerTimes(val date: CivilDate, private val minutes: IntArray) {

    init {
        require(minutes.size == Prayer.entries.size) { "expected a value per prayer" }
    }

    operator fun get(prayer: Prayer): Int = minutes[prayer.ordinal]

    /** Local wall-clock minutes since midnight for [prayer]. */
    fun minuteOfDay(prayer: Prayer): Int = minutes[prayer.ordinal]

    fun copyMinutes(): IntArray = minutes.copyOf()

    /**
     * The next obligatory prayer strictly after [minuteOfDay], or null once Isha has begun.
     * [Prayer.IMSAK] and [Prayer.SUNRISE] are skipped: neither is a prayer.
     */
    fun nextFrom(minuteOfDay: Int): Prayer? =
        Prayer.OBLIGATORY.firstOrNull { minutes[it.ordinal] > minuteOfDay }

    /** The prayer whose window contains [minuteOfDay], or null before Fajr. */
    fun currentAt(minuteOfDay: Int): Prayer? =
        Prayer.OBLIGATORY.lastOrNull { minutes[it.ordinal] <= minuteOfDay }

    override fun toString(): String =
        Prayer.entries.joinToString(", ") { "${it.name}=${format(minutes[it.ordinal])}" }

    companion object {
        val EMPTY = PrayerTimes(CivilDate(2000, 1, 1), IntArray(Prayer.entries.size))

        /** `HH:mm`, 24-hour. */
        fun format(minutes: Int): String {
            val m = floorMod(minutes, 1440)
            return pad2(m / 60) + ":" + pad2(m % 60)
        }

        internal fun pad2(value: Int): String = if (value < 10) "0$value" else value.toString()
    }
}

/**
 * Solar prayer-time calculation: a faithful port of the PrayTimes algorithm that Aladhan and the
 * published Indonesian schedules are generated with, including its per-prayer refinement of the sun
 * position. Evaluating the sun once at 0h UT instead shifts results by up to a minute, which is
 * visible in a timetable, so the refinement is load-bearing.
 *
 * Agreement with Aladhan is asserted in `PrayerCalculatorTest`.
 */
object PrayerCalculator {

    /** Refraction plus solar semi-diameter: the standard horizon dip for sunrise/sunset. */
    private const val HORIZON = -0.833

    /** Julian day of J2000.0. */
    private const val JULIAN_2000 = 2451545.0

    /** Julian day at 0h UT of the Unix epoch, matching [CivilDate.epochDay]. */
    private const val JULIAN_UNIX_EPOCH = 2440587.5

    private const val DEG = 180.0 / Math.PI
    private const val RAD = Math.PI / 180.0

    /**
     * The portion of the day at which each prayer's sun position is evaluated. These are the
     * PrayTimes defaults; they only need to be within a few hours for the refinement to converge.
     */
    private const val SUNRISE_PORTION = 6.0 / 24.0
    private const val DHUHR_PORTION = 12.0 / 24.0
    private const val ASR_PORTION = 13.0 / 24.0
    private const val SUNSET_PORTION = 18.0 / 24.0
    private const val FAJR_PORTION = 5.0 / 24.0
    private const val ISHA_PORTION = 18.0 / 24.0

    private const val FAJR_INDEX = 0
    private const val SUNRISE_INDEX = 1
    private const val DHUHR_INDEX = 2
    private const val ASR_INDEX = 3
    private const val MAGHRIB_INDEX = 4
    private const val ISHA_INDEX = 5

    fun calculate(
        date: CivilDate,
        latitude: Double,
        longitude: Double,
        /** Local UTC offset in minutes on [date]. */
        utcOffsetMinutes: Int,
        config: CalculationConfig,
        tuning: Tuning = Tuning.NONE,
    ): PrayerTimes {
        require(latitude in -90.0..90.0) { "latitude out of range: $latitude" }
        require(longitude in -180.0..180.0) { "longitude out of range: $longitude" }

        val jDate = date.epochDay + JULIAN_UNIX_EPOCH

        // Times before `localShift` are in UT; adding it moves them to local wall clock.
        val fajr = sunAngleTime(jDate, -config.fajrAngle, FAJR_PORTION, latitude, ccw = true)
        val sunrise = sunAngleTime(jDate, HORIZON, SUNRISE_PORTION, latitude, ccw = true)
        val dhuhr = midDay(jDate, DHUHR_PORTION)
        val asr = asrTime(jDate, ASR_PORTION, latitude, config.asrFactor)
        val sunset = sunAngleTime(jDate, HORIZON, SUNSET_PORTION, latitude, ccw = false)
        val maghrib = when {
            config.maghribIntervalMinutes > 0 -> sunset + config.maghribIntervalMinutes / 60.0
            config.maghribAngle > 0.0 ->
                sunAngleTime(jDate, -config.maghribAngle, SUNSET_PORTION, latitude, ccw = false)
            else -> sunset
        }
        // An interval-based Isha is an offset from Maghrib, so it is derived after Maghrib is
        // adjusted. Computing it from the raw sunset would break at latitudes where the sun never
        // sets, pushing Isha past midnight while Maghrib sits in the afternoon.
        val isha = if (config.ishaIntervalMinutes > 0) {
            Double.NaN
        } else {
            sunAngleTime(jDate, -config.ishaAngle, ISHA_PORTION, latitude, ccw = false)
        }

        val times = adjustHighLatitudes(
            times = doubleArrayOf(fajr, sunrise, dhuhr, asr, maghrib, isha),
            rule = config.highLatitude,
            ishaIntervalMinutes = config.ishaIntervalMinutes,
        )

        val localShift = utcOffsetMinutes / 60.0 - longitude / 15.0
        for (i in times.indices) times[i] += localShift
        if (config.dhuhrIntervalMinutes != 0) times[DHUHR_INDEX] += config.dhuhrIntervalMinutes / 60.0

        // Imsak is derived from the local Fajr so it tracks DST-free wall clock exactly.
        val imsak = (times[FAJR_INDEX] - tuning.imsakMinutes / 60.0) * 60.0

        val out = IntArray(Prayer.entries.size)
        out[Prayer.IMSAK.ordinal] = tuning.apply(imsak, Prayer.IMSAK)
        out[Prayer.FAJR.ordinal] = tuning.apply(times[FAJR_INDEX] * 60.0, Prayer.FAJR)
        out[Prayer.SUNRISE.ordinal] = tuning.apply(times[SUNRISE_INDEX] * 60.0, Prayer.SUNRISE)
        out[Prayer.DHUHR.ordinal] = tuning.apply(times[DHUHR_INDEX] * 60.0, Prayer.DHUHR)
        out[Prayer.ASR.ordinal] = tuning.apply(times[ASR_INDEX] * 60.0, Prayer.ASR)
        out[Prayer.MAGHRIB.ordinal] = tuning.apply(times[MAGHRIB_INDEX] * 60.0, Prayer.MAGHRIB)
        out[Prayer.ISHA.ordinal] = tuning.apply(times[ISHA_INDEX] * 60.0, Prayer.ISHA)
        for (i in out.indices) out[i] = floorMod(out[i], 1440)
        return PrayerTimes(date, out)
    }

    /** Solar declination in degrees and equation of time in hours, at Julian day [jDate]. */
    private class SunPosition(val declination: Double, val equationOfTime: Double)

    private fun sunPosition(jDate: Double): SunPosition {
        val d = jDate - JULIAN_2000
        val g = fix(357.529 + 0.98560028 * d, 360.0)
        val q = fix(280.459 + 0.98564736 * d, 360.0)
        val l = fix(q + 1.915 * sin(g * RAD) + 0.020 * sin(2 * g * RAD), 360.0)
        val e = 23.439 - 0.00000036 * d
        val rightAscension = fix(
            atan2(cos(e * RAD) * sin(l * RAD), cos(l * RAD)) * DEG / 15.0,
            24.0,
        )
        val declination = asin(sin(e * RAD) * sin(l * RAD)) * DEG
        return SunPosition(declination, q / 15.0 - rightAscension)
    }

    /** Solar noon in UT hours, with the sun position refined at [portion] of the day. */
    private fun midDay(jDate: Double, portion: Double): Double =
        fix(12.0 - sunPosition(jDate + portion).equationOfTime, 24.0)

    /**
     * UT hours at which the sun reaches [altitude] degrees (negative below the horizon), refined at
     * [portion] of the day. [ccw] selects the morning branch. NaN when the sun never gets there.
     */
    private fun sunAngleTime(
        jDate: Double,
        altitude: Double,
        portion: Double,
        latitude: Double,
        ccw: Boolean,
    ): Double {
        val sun = sunPosition(jDate + portion)
        val noon = midDay(jDate, portion)
        val numerator = sin(altitude * RAD) - sin(sun.declination * RAD) * sin(latitude * RAD)
        val denominator = cos(sun.declination * RAD) * cos(latitude * RAD)
        val cosH = numerator / denominator
        if (cosH > 1.0 || cosH < -1.0) return Double.NaN
        val t = acos(cosH) * DEG / 15.0
        return noon + if (ccw) -t else t
    }

    /** Asr via the shadow-length factor: 1x for Shafi'i, 2x for Hanafi. */
    private fun asrTime(jDate: Double, portion: Double, latitude: Double, factor: Double): Double {
        val declination = sunPosition(jDate + portion).declination
        val altitude = atan(1.0 / (factor + tan(abs(latitude - declination) * RAD))) * DEG
        return sunAngleTime(jDate, altitude, portion, latitude, ccw = false)
    }

    /**
     * High-latitude handling. Outside the tropics two things can fail independently, so they are
     * resolved separately:
     *
     *  1. The sun never rises or sets (polar day or night), so the horizon crossings are NaN. There
     *     is no true sunrise or sunset; a nominal one is placed between the neighbouring prayers so
     *     the timetable stays ordered and honestly labelled.
     *  2. The sun never reaches the Fajr/Isha twilight angle, so those are NaN. The configured rule
     *     places them a fraction of the night from the horizon; when the rule is [TWILIGHT_ANGLE],
     *     which has no fallback of its own, the one-seventh rule is used as a safety net rather than
     *     emitting midnight.
     *
     * Case 1 can occur with case 2 not occurring and vice versa, which is why the two are not
     * handled by a single check. Indonesian coordinates reach neither case.
     */
    private fun adjustHighLatitudes(
        times: DoubleArray,
        rule: HighLatitudeRule,
        ishaIntervalMinutes: Int,
    ): DoubleArray {
        val out = times.copyOf()
        val noon = out[DHUHR_INDEX]
        val fajrKnown = out[FAJR_INDEX].isFinite()

        if (!out[SUNRISE_INDEX].isFinite()) {
            // Between Fajr and Dhuhr when Fajr is known, otherwise a nominal half-day before noon.
            out[SUNRISE_INDEX] = if (fajrKnown) (out[FAJR_INDEX] + noon) / 2.0 else noon - NOMINAL_HALF_DAY
        }
        // Whether the sun set at all decides how the remaining entries are anchored.
        val asr = out[ASR_INDEX]
        val ishaKnown = out[ISHA_INDEX].isFinite()
        if (!out[MAGHRIB_INDEX].isFinite()) {
            out[MAGHRIB_INDEX] = if (ishaKnown && asr.isFinite()) {
                (asr + out[ISHA_INDEX]) / 2.0
            } else {
                noon + NOMINAL_HALF_DAY
            }
        }

        // Interval-based Isha is measured from Maghrib, now that Maghrib exists.
        if (ishaIntervalMinutes > 0) {
            out[ISHA_INDEX] = out[MAGHRIB_INDEX] + ishaIntervalMinutes / 60.0
        } else if (!ishaKnown) {
            val night = 24.0 - (out[MAGHRIB_INDEX] - out[SUNRISE_INDEX])
            val portion = if (rule == HighLatitudeRule.MIDDLE_OF_THE_NIGHT) 1.0 / 2.0 else 1.0 / 7.0
            out[ISHA_INDEX] = out[MAGHRIB_INDEX] + night * portion
        }

        if (!fajrKnown) {
            val night = 24.0 - (out[MAGHRIB_INDEX] - out[SUNRISE_INDEX])
            // TWILIGHT_ANGLE has no fallback of its own, so it shares the one-seventh rule rather
            // than emitting a non-time.
            val portion = if (rule == HighLatitudeRule.MIDDLE_OF_THE_NIGHT) 1.0 / 2.0 else 1.0 / 7.0
            out[FAJR_INDEX] = out[SUNRISE_INDEX] - night * portion
        }

        // The Asr shadow altitude can also be unreachable; place it mid-afternoon.
        if (!out[ASR_INDEX].isFinite()) {
            out[ASR_INDEX] = (noon + out[MAGHRIB_INDEX]) / 2.0
        }
        return enforceOrder(out)
    }

    /**
     * Guarantees Fajr < sunrise < Dhuhr < Asr < Maghrib < Isha after rounding, by nudging any pair
     * that collapsed onto the same minute. Only reachable at extreme latitudes.
     */
    private fun enforceOrder(times: DoubleArray): DoubleArray {
        for (i in 1 until times.size) {
            if (times[i] <= times[i - 1]) {
                times[i] = times[i - 1] + ORDER_EPSILON_HOURS
            }
        }
        return times
    }

    /** Nominal half-day used only when the sun neither rises nor sets. */
    private const val NOMINAL_HALF_DAY = 6.0

    /** Separation enforced between consecutive entries of a degenerate day, in hours. */
    private const val ORDER_EPSILON_HOURS = 1.0 / 60.0

    private fun fix(value: Double, range: Double): Double {
        val v = value - range * Math.floor(value / range)
        return if (v < 0) v + range else v
    }
}

/**
 * The schedule for [city] on [date] under [method] and [madhab]. The Kemenag rounding and ihtiyati
 * margins apply only to the Kemenag method; every other authority publishes plain rounded times.
 */
fun prayerTimesFor(
    city: City,
    date: CivilDate,
    method: CalculationMethod = CalculationMethod.DEFAULT,
    madhab: Madhab = Madhab.DEFAULT,
    tuning: Tuning = if (method == CalculationMethod.KEMENAG) Tuning.KEMENAG else Tuning.NONE,
): PrayerTimes = PrayerCalculator.calculate(
    date = date,
    latitude = city.latitude,
    longitude = city.longitude,
    utcOffsetMinutes = city.offsetMinutesOn(date),
    config = method.config.withMadhab(madhab),
    tuning = tuning,
)
