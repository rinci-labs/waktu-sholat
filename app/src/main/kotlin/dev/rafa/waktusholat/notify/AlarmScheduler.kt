package dev.rafa.waktusholat.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.rafa.waktusholat.WaktuSholatApp
import dev.rafa.waktusholat.core.CivilDate
import dev.rafa.waktusholat.core.Prayer

/**
 * Arms the adhan alarms for the coming 24 hours. Alarms are not a persistent registration: every
 * time the settings or the schedule change (and after a reboot or a clock change) the whole window
 * is recomputed and re-armed, which keeps this object stateless.
 */
object AlarmScheduler {

    const val EXTRA_PRAYER = "prayer"
    const val EXTRA_MINUTE = "minute"
    const val EXTRA_REMINDER = "reminder"

    private const val MILLIS_PER_MINUTE = 60_000L

    /** Re-arms everything; the single entry point used by the app, its receivers and the settings. */
    fun reschedule(context: Context) {
        val app = context.applicationContext
        val preferences = WaktuSholatApp.instance.preferences

        if (!preferences.notificationsEnabled) {
            cancel(app)
            return
        }

        val manager = app.getSystemService(AlarmManager::class.java) ?: return
        val lead = preferences.reminderLeadMinutes
        val now = System.currentTimeMillis()
        val day = WaktuSholatApp.instance.repository.day(now)
        val zone = day.city.timeZoneHours
        val windows = listOf(day.times, day.tomorrow)

        // Prayer.OBLIGATORY is exactly FAJR, DHUHR, ASR, MAGHRIB, ISHA: the two entries that must
        // not ring (IMSAK and SUNRISE) are not in it.
        for (prayer in Prayer.OBLIGATORY) {
            // The lead time moves the alarm earlier; a reminder that crosses midnight still shows
            // the schedule's own minute, which is the adhan it belongs to.
            for (times in windows) {
                val minute = times.minuteOfDay(prayer)
                val triggerAtMillis = epochMillisAt(times.date, minute, zone) - lead * MILLIS_PER_MINUTE
                // Past occurrences are skipped: the first future one wins, and once both days have
                // passed there is nothing to arm.
                if (triggerAtMillis > now) {
                    schedule(manager, app, prayer, minute, lead, triggerAtMillis)
                    break
                }
            }
        }
    }

    /** Cancels every prayer's pending alarm. */
    fun cancel(context: Context) {
        val app = context.applicationContext
        val manager = app.getSystemService(AlarmManager::class.java) ?: return
        for (prayer in Prayer.entries) {
            // FLAG_NO_CREATE: cancel must never arm a brand-new broadcast for a prayer that was
            // never scheduled.
            val pending = PendingIntent.getBroadcast(
                app,
                prayer.ordinal,
                receiverIntent(app, prayer, 0, false),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: continue
            manager.cancel(pending)
        }
    }

    private fun schedule(
        manager: AlarmManager,
        context: Context,
        prayer: Prayer,
        minute: Int,
        lead: Int,
        triggerAtMillis: Long,
    ) {
        val intent = pendingIntent(context, prayer, minute, lead > 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
            // Exact alarms need the SCHEDULE_EXACT_ALARM permission *and*, on Android 12+, a
            // user-granted special access that can be revoked at any time. Without it
            // setExactAndAllowWhileIdle throws SecurityException, so fall back to the strongest
            // inexact primitive: it still wakes the device in doze, but the system may delay it by
            // minutes. An alarm that is a little late beats an adhan that is silently dropped.
            //
            // Below API 31 there is no check to make: the access is granted at install time.
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, intent)
        } else {
            // RTC_WAKEUP because the target is an absolute wall-clock instant that must wake the
            // device out of doze.
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, intent)
        }
    }

    /**
     * The instant of local midnight plus [minuteOfDay], in a zone [zone] hours from UTC.
     *
     * This is the inverse of [LocalClock.dateAt] and [LocalClock.minuteOfDay]: for a whole-hour
     * offset the local epoch minute is `date.epochDay * 1440 + minuteOfDay`, so
     * `epochMillis = (date.epochDay * 1440L + minuteOfDay - zone * 60L) * 60_000L`. Because
     * [minuteOfDay] is in `0..1439` the value stays inside that local day, and dividing back out
     * (`/ 60_000`, then `+ zone * 60`, then floorDiv/floorMod by 1440) returns exactly the date and
     * the minute that went in — which [require] pins down for every call this module makes.
     */
    fun epochMillisAt(date: CivilDate, minuteOfDay: Int, zone: Int): Long {
        require(minuteOfDay in 0 until 1440) { "minuteOfDay out of range: $minuteOfDay" }
        return (date.epochDay * 1440L + minuteOfDay - zone * 60L) * MILLIS_PER_MINUTE
    }

    /**
     * One pending intent per prayer, keyed by the ordinal. The same request code is used every day,
     * so the current alarm is simply replaced rather than stacked; FLAG_UPDATE_CURRENT refreshes the
     * minute and reminder extras for the new day.
     */
    private fun pendingIntent(context: Context, prayer: Prayer, minute: Int, reminder: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            prayer.ordinal,
            receiverIntent(context, prayer, minute, reminder),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun receiverIntent(context: Context, prayer: Prayer, minute: Int, reminder: Boolean): Intent =
        Intent(context, PrayerAlarmReceiver::class.java)
            .putExtra(EXTRA_PRAYER, prayer.name)
            .putExtra(EXTRA_MINUTE, minute)
            .putExtra(EXTRA_REMINDER, reminder)
}
