package dev.rafa.waktusholat.data

import dev.rafa.waktusholat.core.CalculationMethod
import dev.rafa.waktusholat.core.CivilDate
import dev.rafa.waktusholat.core.City
import dev.rafa.waktusholat.core.HijriDate
import dev.rafa.waktusholat.core.Madhab
import dev.rafa.waktusholat.core.PrayerTimes
import dev.rafa.waktusholat.core.UmmAlQura
import dev.rafa.waktusholat.core.prayerTimesFor

/**
 * Turns a location and the user's settings into schedules. Every screen and the widget go through
 * here, so the calculation inputs are resolved in exactly one place.
 */
class PrayerScheduleFactory {

    /** Today's schedule for [city]. */
    fun today(
        city: City,
        date: CivilDate,
        method: CalculationMethod = CalculationMethod.DEFAULT,
        madhab: Madhab = Madhab.DEFAULT,
    ): PrayerTimes = prayerTimesFor(city, date, method, madhab)

    /** Hijri date shown next to the Gregorian one; null outside the Umm al-Qura table's range. */
    fun hijri(date: CivilDate): HijriDate? =
        UmmAlQura.fromGregorian(date.year, date.month, date.day)

    /** One month of schedules starting on the 1st, for the monthly view. */
    fun month(
        city: City,
        year: Int,
        month: Int,
        method: CalculationMethod = CalculationMethod.DEFAULT,
        madhab: Madhab = Madhab.DEFAULT,
    ): List<PrayerTimes> {
        val days = CivilDate.daysInMonth(year, month)
        return (1..days).map { day ->
            prayerTimesFor(city, CivilDate(year, month, day), method, madhab)
        }
    }
}
