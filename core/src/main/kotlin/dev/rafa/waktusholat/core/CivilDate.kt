package dev.rafa.waktusholat.core

/**
 * Plain Gregorian calendar date with the arithmetic the app needs, implemented without
 * `java.time` so the module stays usable on Android API 24 without core-library desugaring.
 *
 * Algorithms are Howard Hinnant's `days_from_civil` / `civil_from_days`, so [epochDay] is the
 * number of days since 1970-01-01 and round-trips exactly.
 */
data class CivilDate(val year: Int, val month: Int, val day: Int) : Comparable<CivilDate> {

    init {
        require(month in 1..12) { "month out of range: $month" }
        require(day in 1..daysInMonth(year, month)) { "day out of range: $day" }
    }

    /** Days since 1970-01-01 (negative before that). */
    val epochDay: Int
        get() {
            // Shift the year so March is the first month, which makes the leap day the last day.
            val y = year - if (month <= 2) 1 else 0
            val era = floorDiv(y, 400)
            val yearOfEra = y - era * 400                                  // [0, 399]
            val dayOfYear = (153 * (month + if (month > 2) -3 else 9) + 2) / 5 + day - 1
            val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
            return era * 146_097 + dayOfEra - 719_468
        }

    fun plusDays(days: Int): CivilDate = fromEpochDay(epochDay + days)

    /** ISO day of week: 1 = Monday .. 7 = Sunday. */
    val dayOfWeek: Int
        get() = floorMod(epochDay + 3, 7) + 1

    /** True when this date falls on the given ISO day of week (1 = Monday .. 7 = Sunday). */
    fun isDayOfWeek(isoDay: Int): Boolean = dayOfWeek == isoDay

    override fun compareTo(other: CivilDate): Int {
        year.compareTo(other.year).let { if (it != 0) return it }
        month.compareTo(other.month).let { if (it != 0) return it }
        return day.compareTo(other.day)
    }

    override fun toString(): String = "$year-${pad(month)}-${pad(day)}"

    companion object {
        fun fromEpochDay(epochDay: Int): CivilDate {
            val z = epochDay + 719_468
            val era = floorDiv(z, 146_097)
            val dayOfEra = z - era * 146_097                                      // [0, 146096]
            val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
            val y = yearOfEra + era * 400
            val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
            val mp = (5 * dayOfYear + 2) / 153                                    // [0, 11]
            val d = dayOfYear - (153 * mp + 2) / 5 + 1
            val m = mp + if (mp < 10) 3 else -9
            return CivilDate(y + if (m <= 2) 1 else 0, m, d)
        }

        fun isLeapYear(year: Int): Boolean =
            year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

        fun daysInMonth(year: Int, month: Int): Int = when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (isLeapYear(year)) 29 else 28
            else -> throw IllegalArgumentException("month out of range: $month")
        }

        fun daysInYear(year: Int): Int = if (isLeapYear(year)) 366 else 365

        /** ISO day of week for a date, 1 = Monday .. 7 = Sunday. */
        fun dayOfWeek(year: Int, month: Int, day: Int): Int = CivilDate(year, month, day).dayOfWeek

        private fun pad(value: Int): String = if (value < 10) "0$value" else value.toString()
    }
}
