package dev.rafa.waktusholat.core

/**
 * Offset wall-clock arithmetic. Callers pass the UTC offset in minutes that is in effect at the
 * instant (see [City.offsetMinutesAt]), which covers half-hour zones and daylight saving; deriving
 * the local time from the device clock this way keeps the countdown correct even when the phone is
 * set to a different time zone than the selected city.
 */
object LocalClock {

    private const val MILLIS_PER_MINUTE = 60_000L

    /** Minutes since local midnight for [utcEpochMillis], at [offsetMinutes] from UTC. */
    fun minuteOfDay(utcEpochMillis: Long, offsetMinutes: Int): Int {
        val localMinutes = utcEpochMillis / MILLIS_PER_MINUTE + offsetMinutes.toLong()
        return floorMod(localMinutes, 1440L).toInt()
    }

    /** Civil date at [utcEpochMillis] at [offsetMinutes] from UTC. */
    fun dateAt(utcEpochMillis: Long, offsetMinutes: Int): CivilDate {
        val localMinutes = utcEpochMillis / MILLIS_PER_MINUTE + offsetMinutes.toLong()
        val epochDay = floorDiv(localMinutes, 1440L).toInt()
        return CivilDate.fromEpochDay(epochDay)
    }

    /** Milliseconds until local midnight, used to schedule the next day's refresh. */
    fun millisUntilNextDay(utcEpochMillis: Long, offsetMinutes: Int): Long {
        val localMinutes = utcEpochMillis / MILLIS_PER_MINUTE + offsetMinutes.toLong()
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
