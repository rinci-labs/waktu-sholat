package dev.rafa.waktusholat.core

/**
 * The seven daily entries, in display order. Ordinal is used to index the per-day time array.
 */
enum class Prayer {
    IMSAK,
    FAJR,
    SUNRISE,
    DHUHR,
    ASR,
    MAGHRIB,
    ISHA;

    companion object {
        /**
         * Everything shown in a daily list or timetable: the five obligatory prayers plus sunrise
         * and Imsak. Imsak is informational but every Indonesian schedule prints it, so it belongs
         * in the list where a preference can hide it.
         */
        val DAILY: List<Prayer> = listOf(IMSAK, FAJR, SUNRISE, DHUHR, ASR, MAGHRIB, ISHA)

        /** The five prayers that get an adhan notification. */
        val OBLIGATORY: List<Prayer> = listOf(FAJR, DHUHR, ASR, MAGHRIB, ISHA)
    }
}
