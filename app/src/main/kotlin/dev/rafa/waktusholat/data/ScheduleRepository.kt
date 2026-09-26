package dev.rafa.waktusholat.data

import dev.rafa.waktusholat.core.CivilDate
import dev.rafa.waktusholat.core.City
import dev.rafa.waktusholat.core.HijriDate
import dev.rafa.waktusholat.core.LocalClock
import dev.rafa.waktusholat.core.PrayerMoment
import dev.rafa.waktusholat.core.PrayerTimes

/**
 * Everything the UI and the widget need for "right now": today's times, tomorrow's Fajr, and the
 * countdown. Both surfaces read from here, so they can never disagree about the schedule.
 *
 * "Today" always means today in the *selected city's* zone rather than the device's, which is what
 * makes the times correct for a user who has travelled or whose phone is set to another region.
 */
class ScheduleRepository(private val preferences: Preferences) {

    private val factory = PrayerScheduleFactory()

    val city: City get() = preferences.resolveCity()

    /** Today's civil date in the city's time zone. */
    fun today(nowMillis: Long = System.currentTimeMillis()): CivilDate =
        LocalClock.dateAt(nowMillis, city.timeZoneHours)

    fun timesFor(city: City, date: CivilDate): PrayerTimes =
        factory.today(city, date, preferences.method, preferences.madhab)

    /** Today's schedule for the selected location. */
    fun todayTimes(nowMillis: Long = System.currentTimeMillis()): PrayerTimes =
        timesFor(city, today(nowMillis))

    fun tomorrowTimes(nowMillis: Long = System.currentTimeMillis()): PrayerTimes =
        timesFor(city, today(nowMillis).plusDays(1))

    fun hijri(date: CivilDate = today()): HijriDate? = factory.hijri(date)

    /** Current prayer and countdown at [nowMillis], resolved in the city's time zone. */
    fun moment(nowMillis: Long = System.currentTimeMillis()): PrayerMoment {
        val zone = city.timeZoneHours
        val date = LocalClock.dateAt(nowMillis, zone)
        val times = timesFor(city, date)
        val minute = LocalClock.minuteOfDay(nowMillis, zone)
        val second = ((nowMillis / 1000L) % 60L).toInt()
        // Fajr belongs to the next day once Isha has begun, so it needs tomorrow's schedule.
        val tomorrow = if (times.nextFrom(minute) == null) timesFor(city, date.plusDays(1)) else null
        return PrayerMoment.at(times, tomorrow, minute, second)
    }

    /** One month of schedules, starting on the 1st. */
    fun month(year: Int, month: Int): List<PrayerTimes> =
        factory.month(city, year, month, preferences.method, preferences.madhab)
}
