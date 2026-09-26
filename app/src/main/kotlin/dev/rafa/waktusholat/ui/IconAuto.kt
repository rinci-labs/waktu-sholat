package dev.rafa.waktusholat.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.rafa.waktusholat.WaktuSholatApp
import dev.rafa.waktusholat.core.Prayer

/**
 * Automatic launcher icon: follows the part of the day, the same [Period] that paints the sky
 * inside the app.
 *
 * The switch is deferred while any screen of the app is open (swapping launcher components under a
 * running task can make the launcher restart it) and happens instead when the app goes to the
 * background, or from a non-waking alarm armed for the next period boundary. A non-waking alarm
 * that falls due while the screen is off is delivered on the next wake, so the icon is already
 * right when the home screen appears, and the device is never woken for it.
 */
object IconAuto {

    private const val FILE = "icon"
    private const val KEY_AUTO = "auto"

    /** Allowed lateness; lets the system batch the alarm with other work. */
    private const val WINDOW_MILLIS = 10 * 60_000L

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_AUTO, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO, enabled).apply()
        if (enabled) sync(context) else cancel(context)
    }

    /** Applies the current period's theme if allowed now, and arms the next boundary. */
    fun sync(context: Context) {
        if (!isEnabled(context)) return
        val snapshot = WaktuSholatApp.instance.repository.snapshot()
        val target = IconTheme.valueOf(Period.of(snapshot).name)
        if (!BaseActivity.inForeground && IconTheme.current(context) != target) {
            IconTheme.apply(context, target)
        }
        schedule(context, snapshot)
    }

    /** The next period boundary: the next of Fajr, sunrise, Dhuhr, Asr, Maghrib, Isha. */
    private fun schedule(context: Context, snapshot: dev.rafa.waktusholat.data.ScheduleRepository.Snapshot) {
        val boundaries = listOf(Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)
        val now = snapshot.minuteOfDay
        val next = boundaries.map { snapshot.times[it] }.filter { it > now }.minOrNull()
        val minutesAhead = if (next != null) next - now else 1440 - now + snapshot.day.tomorrow[Prayer.FAJR]
        val at = (snapshot.nowMillis / 60_000L + minutesAhead) * 60_000L + 1_000L
        context.getSystemService(AlarmManager::class.java)
            ?.setWindow(AlarmManager.RTC, at, WINDOW_MILLIS, intent(context))
    }

    private fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(intent(context))
    }

    private fun intent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, IconAutoReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** Target of the period-boundary alarm. Not exported. */
class IconAutoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = IconAuto.sync(context)
}
