package dev.rafa.waktusholat.data

import dev.rafa.waktusholat.core.CalculationMethod
import dev.rafa.waktusholat.core.CivilDate
import dev.rafa.waktusholat.core.City
import dev.rafa.waktusholat.core.HijriDate
import dev.rafa.waktusholat.core.LocalClock
import dev.rafa.waktusholat.core.Madhab
import dev.rafa.waktusholat.core.Prayer
import dev.rafa.waktusholat.core.PrayerTimes
import dev.rafa.waktusholat.core.UmmAlQura
import dev.rafa.waktusholat.core.prayerTimesFor

/**
 * Everything the UI, the widgets and the alarms need for "right now". All three read from here, so
 * they can never disagree about the schedule.
 *
 * "Today" always means today in the *selected city's* zone rather than the device's, which is what
 * makes the times correct for a user who has travelled or whose phone is set to another region.
 *
 * One instance lives on the application. The current [Day] is cached and only recomputed when the
 * date, city, method or madhab changes, so a widget refresh or a countdown tick costs no solar maths.
 */
class ScheduleRepository(private val preferences: Preferences) {

    @Volatile
    private var cachedDay: Day? = null

    val city: City get() = preferences.resolveCity()

    /** Today's civil date in the city's time zone. */
    fun today(nowMillis: Long = System.currentTimeMillis()): CivilDate =
        LocalClock.dateAt(nowMillis, city.offsetMinutesAt(nowMillis))

    fun timesFor(city: City, date: CivilDate): PrayerTimes =
        prayerTimesFor(city, date, preferences.method, preferences.madhab)

    /** Today's and tomorrow's schedule for the selected location, cached per day. */
    fun day(nowMillis: Long = System.currentTimeMillis()): Day {
        val city = city
        val date = LocalClock.dateAt(nowMillis, city.offsetMinutesAt(nowMillis))
        val method = preferences.method
        val madhab = preferences.madhab
        cachedDay?.let { if (it.matches(city, date, method, madhab)) return it }
        return Day(
            city = city,
            date = date,
            method = method,
            madhab = madhab,
            times = prayerTimesFor(city, date, method, madhab),
            tomorrow = prayerTimesFor(city, date.plusDays(1), method, madhab),
            hijri = UmmAlQura.fromGregorian(date.year, date.month, date.day),
        ).also { cachedDay = it }
    }

    /** What to show at [nowMillis]: the prayer in effect, the next one, and the countdown. */
    fun snapshot(nowMillis: Long = System.currentTimeMillis()): Snapshot = Snapshot(day(nowMillis), nowMillis)

    /** One month of schedules, starting on the 1st. */
    fun month(year: Int, month: Int): List<PrayerTimes> {
        val city = city
        val method = preferences.method
        val madhab = preferences.madhab
        return List(CivilDate.daysInMonth(year, month)) { index ->
            prayerTimesFor(city, CivilDate(year, month, index + 1), method, madhab)
        }
    }

    /** One local day's schedule plus tomorrow's, which the countdown needs once Isha has begun. */
    class Day(
        val city: City,
        val date: CivilDate,
        private val method: CalculationMethod,
        private val madhab: Madhab,
        val times: PrayerTimes,
        val tomorrow: PrayerTimes,
        /** Null outside the Umm al-Qura table's range. */
        val hijri: HijriDate?,
    ) {
        fun matches(city: City, date: CivilDate, method: CalculationMethod, madhab: Madhab): Boolean =
            this.city == city && this.date == date && this.method == method && this.madhab == madhab
    }

    /** A [Day] resolved at one instant. Every field is derived once, at construction. */
    class Snapshot(val day: Day, val nowMillis: Long) {
        val city: City get() = day.city
        val times: PrayerTimes get() = day.times

        val minuteOfDay: Int = LocalClock.minuteOfDay(nowMillis, day.city.offsetMinutesAt(nowMillis))

        /** The obligatory prayer whose window we are in; before Fajr that is last night's Isha. */
        val current: Prayer = day.times.currentAt(minuteOfDay) ?: Prayer.ISHA

        private val nextToday: Prayer? = day.times.nextFrom(minuteOfDay)

        /** True once Isha has begun, so [next] is tomorrow's Fajr. */
        val nextIsTomorrow: Boolean = nextToday == null

        /** The prayer being counted down to. */
        val next: Prayer = nextToday ?: Prayer.FAJR

        /** Local minute of day of [next]. */
        val nextMinute: Int = if (nextToday != null) day.times[nextToday] else day.tomorrow[Prayer.FAJR]

        /** Whole minutes until [next], counting the current minute as elapsed. */
        val minutesRemaining: Int =
            if (nextToday != null) nextMinute - minuteOfDay else 1440 - minuteOfDay + nextMinute

        /** The instant [next] begins, in epoch millis. */
        val nextAtMillis: Long = (nowMillis / MILLIS_PER_MINUTE + minutesRemaining) * MILLIS_PER_MINUTE

        /** Instant of the next local midnight, in epoch millis. */
        val midnightAtMillis: Long = nowMillis + LocalClock.millisUntilNextDay(nowMillis, day.city.offsetMinutesAt(nowMillis))

        /** Fraction of the current prayer window already elapsed, `0f..1f`. */
        val progress: Float = run {
            val start = day.times.currentAt(minuteOfDay)?.let { day.times[it] } ?: (day.times[Prayer.ISHA] - 1440)
            val end = if (nextToday != null) nextMinute else nextMinute + 1440
            val span = end - start
            if (span <= 0) 1f else ((minuteOfDay - start).toFloat() / span).coerceIn(0f, 1f)
        }

        /** True when [prayer] has already begun today. */
        fun hasPassed(prayer: Prayer): Boolean = day.times[prayer] <= minuteOfDay
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
