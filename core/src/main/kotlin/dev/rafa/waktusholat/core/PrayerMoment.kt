package dev.rafa.waktusholat.core

/**
 * The prayer currently in effect, or the next one, together with the countdown that the UI and the
 * widget display.
 */
data class PrayerMoment(
    /** The prayer that has already begun, or the one being counted down to. */
    val prayer: Prayer,
    /** The prayer being counted down to, or null once Isha has begun. */
    val next: Prayer?,
    /** Local minute of day for [next], or null once Isha has begun. */
    val nextMinute: Int?,
    /** Whole minutes remaining, or null once Isha has begun. */
    val minutesRemaining: Int?,
    /** Seconds within the current minute, so the countdown ticks smoothly. */
    val secondsRemaining: Int,
) {
    val hasNext: Boolean get() = next != null

    /** `HH:mm:SS` until the next prayer; empty once Isha has begun. */
    fun countdownText(): String {
        val total = minutesRemaining ?: return ""
        val hours = total / 60
        val minutes = total % 60
        return if (hours > 0) {
            "${PrayerTimes.pad2(hours)}:${PrayerTimes.pad2(minutes)}:${PrayerTimes.pad2(secondsRemaining)}"
        } else {
            "${PrayerTimes.pad2(minutes)}:${PrayerTimes.pad2(secondsRemaining)}"
        }
    }

    /** Fraction of the current interval already elapsed, in `0f..1f`. */
    fun progress(from: Int, times: PrayerTimes): Float {
        val target = nextMinute ?: return 1f
        val span = target - from
        if (span <= 0) return 1f
        val elapsed = span - (minutesRemaining ?: 0)
        return (elapsed.toFloat() / span).coerceIn(0f, 1f)
    }

    companion object {
        /**
         * Resolve what to display at a local wall-clock instant. Once Isha has begun the label
         * switches to Fajr of the following day, which is what a prayer app is expected to show.
         */
        fun at(
            times: PrayerTimes,
            tomorrow: PrayerTimes?,
            minuteOfDay: Int,
            secondOfMinute: Int,
        ): PrayerMoment {
            val next = times.nextFrom(minuteOfDay)
            if (next != null) {
                val target = times[next]
                return PrayerMoment(
                    prayer = times.currentAt(minuteOfDay) ?: Prayer.ISHA,
                    next = next,
                    nextMinute = target,
                    minutesRemaining = target - minuteOfDay,
                    secondsRemaining = secondOfMinute,
                )
            }
            val current = Prayer.ISHA
            val fajr = tomorrow?.get(Prayer.FAJR) ?: return PrayerMoment(
                prayer = current,
                next = null,
                nextMinute = null,
                minutesRemaining = null,
                secondsRemaining = secondOfMinute,
            )
            val remaining = 1440 - minuteOfDay + fajr
            return PrayerMoment(
                prayer = current,
                next = Prayer.FAJR,
                nextMinute = fajr,
                minutesRemaining = remaining,
                secondsRemaining = secondOfMinute,
            )
        }
    }
}
