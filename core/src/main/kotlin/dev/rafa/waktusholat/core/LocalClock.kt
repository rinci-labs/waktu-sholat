package dev.rafa.waktusholat.core

/**
 * Fixed-offset wall-clock arithmetic. Indonesia has no daylight saving, so a city's offset is a
 * whole number of hours and never shifts; deriving the local time from the device clock this way
 * keeps the countdown correct even when the phone is set to a different time zone.
 */
object LocalClock {

    private const val MILLIS_PER_MINUTE = 60_000L

    /** Minutes since local midnight for [utcEpochMillis], in a zone [timeZoneHours] from UTC. */
    fun minuteOfDay(utcEpochMillis: Long, timeZoneHours: Int): Int {
        val localMinutes = utcEpochMillis / MILLIS_PER_MINUTE + timeZoneHours * 60L
        return floorMod(localMinutes, 1440L).toInt()
    }

    /** Civil date at [utcEpochMillis] in a zone [timeZoneHours] from UTC. */
    fun dateAt(utcEpochMillis: Long, timeZoneHours: Int): CivilDate {
        val localMinutes = utcEpochMillis / MILLIS_PER_MINUTE + timeZoneHours * 60L
        val epochDay = floorDiv(localMinutes, 1440L).toInt()
        return CivilDate.fromEpochDay(epochDay)
    }

    /** Milliseconds until local midnight, used to schedule the next day's refresh. */
    fun millisUntilNextDay(utcEpochMillis: Long, timeZoneHours: Int): Long {
        val localMinutes = utcEpochMillis / MILLIS_PER_MINUTE + timeZoneHours * 60L
        val minutesIntoDay = floorMod(localMinutes, 1440L)
        return (1440L - minutesIntoDay) * MILLIS_PER_MINUTE
    }
}

private fun floorDiv(a: Long, b: Long): Long {
    var q = a / b
    if (a % b != 0L && (a xor b) < 0L) q--
    return q
}

private fun floorMod(a: Long, b: Long): Long = a - floorDiv(a, b) * b
