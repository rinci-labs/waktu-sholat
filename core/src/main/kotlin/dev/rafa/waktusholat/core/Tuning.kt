package dev.rafa.waktusholat.core

import kotlin.math.ceil
import kotlin.math.floor

/** How a raw solar time is snapped to the published whole minute. */
enum class Rounding {
    /** Nearest minute. What Aladhan returns for `method=20` with no tune. */
    NEAREST,

    /** Next whole minute, so the schedule is never earlier than the astronomical instant. */
    CEILING,
}

/**
 * Per-prayer rounding plus a fixed safety margin in minutes (*ihtiyati*). Published Indonesian
 * schedules round every entry up and then add a small margin; the exact rule was reverse-engineered
 * from MyQuran's Kemenag endpoint and validated at 100% agreement within one minute over
 * 22 cities x 8 dates x 7 entries (`KemenagMarginTest`).
 */
class Tuning(
    private val rounding: IntArray,
    private val offset: IntArray,
    /** Imsak is Fajr minus this many minutes. */
    val imsakMinutes: Int = 10,
) {
    init {
        require(rounding.size == Prayer.entries.size) { "rounding must cover every prayer" }
        require(offset.size == Prayer.entries.size) { "offset must cover every prayer" }
    }

    fun roundingFor(prayer: Prayer): Rounding = Rounding.entries[rounding[prayer.ordinal]]

    fun offsetFor(prayer: Prayer): Int = offset[prayer.ordinal]

    /** Apply the configured rounding and safety margin to a raw minute value. */
    fun apply(rawMinutes: Double, prayer: Prayer): Int {
        val snapped = when (roundingFor(prayer)) {
            Rounding.NEAREST -> floor(rawMinutes + 0.5)
            // Subtract an epsilon so a value that lands exactly on a minute is not pushed up.
            Rounding.CEILING -> ceil(rawMinutes - 1e-9)
        }
        return snapped.toInt() + offsetFor(prayer)
    }

    companion object {
        /** Round to the nearest minute with no correction. */
        val NONE: Tuning = uniform(Rounding.NEAREST, 0)

        /**
         * Kemenag/Aladhan `method=20`: round up everywhere except sunrise and sunset (which are
         * truncated to the minute), then apply the ihtiyati margins
         * `{Imsak +2, Fajr +2, Sunrise -3, Dhuhr +3, Asr +2, Maghrib +3, Isha +2}`.
         */
        val KEMENAG: Tuning = Tuning(
            rounding = IntArray(Prayer.entries.size) { index ->
                when (Prayer.entries[index]) {
                    Prayer.SUNRISE, Prayer.MAGHRIB -> Rounding.NEAREST.ordinal
                    else -> Rounding.CEILING.ordinal
                }
            },
            offset = intArrayOf(2, 2, -3, 3, 2, 3, 2),
        )

        private fun uniform(rounding: Rounding, offset: Int): Tuning = Tuning(
            rounding = IntArray(Prayer.entries.size) { rounding.ordinal },
            offset = IntArray(Prayer.entries.size) { offset },
        )
    }
}
