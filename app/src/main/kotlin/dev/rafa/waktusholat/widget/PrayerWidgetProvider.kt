package dev.rafa.waktusholat.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import dev.rafa.waktusholat.WaktuSholatApp
import dev.rafa.waktusholat.core.LocalClock
import dev.rafa.waktusholat.data.ScheduleRepository

/**
 * Home-screen widget: renders [WidgetRenderer] output and keeps it honest with one pending alarm.
 *
 * The alarm is a plain [AlarmManager.set] repaint, not a wake-up, so the widget never needs
 * `SCHEDULE_EXACT_ALARM` and never holds a wake lock. It is armed for the earlier of the next prayer
 * and the next local midnight, which is exactly when the rendered content would go stale. Every
 * render path re-arms it, so a resize or a launcher restart cannot leave the timer lost.
 */
class PrayerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        render(context, manager, appWidgetIds)
        schedule(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        render(context, manager, intArrayOf(appWidgetId), newOptions)
        schedule(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Dispatching first lets AppWidgetProvider run its own actions, notably APPWIDGET_UPDATE.
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WIDGET) refreshNow(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancel(context)
    }

    companion object {

        /**
         * Plain in-process broadcast. The app sends it after a settings or location change, and the
         * refresh alarm sends it to itself. Either way the receivers repaint and re-arm.
         */
        const val ACTION_REFRESH_WIDGET = "dev.rafa.waktusholat.action.REFRESH_WIDGET"

        /** The alarm shares the widget's PendingIntent slot; zero is never a real app widget id. */
        private const val REFRESH_REQUEST = 0

        /** A repaint that fires immediately is pointless work, so never arm closer than this. */
        private const val MIN_TRIGGER_MILLIS = 60_000L

        /**
         * Broadcasts a repaint request, for callers that must not touch the widgets directly: the
         * app after a settings change and the boot receiver after a reboot. The receiver re-arms the
         * alarm, so this always leaves the widget scheduled as well as current.
         */
        fun requestRefresh(context: Context) {
            context.sendBroadcast(
                Intent(context, PrayerWidgetProvider::class.java).setAction(ACTION_REFRESH_WIDGET)
            )
        }

        /** Repaints every widget, or the given ones, at the current instant. */
        fun refreshNow(context: Context, ids: IntArray? = null) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val target = ids ?: manager.getAppWidgetIds(
                ComponentName(context, PrayerWidgetProvider::class.java)
            )
            if (target.isEmpty()) return
            render(context, manager, target)
            schedule(context)
        }

        /**
         * One render for all ids at a single instant, so two widgets on the same screen can never
         * disagree about the minute. [options] overrides the size read from [manager], which is what
         * the resize callback already holds.
         */
        private fun render(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
            options: Bundle? = null
        ) {
            val repository = WaktuSholatApp.instance.repository
            val now = System.currentTimeMillis()
            for (id in ids) {
                val options2 = options ?: manager.getAppWidgetOptions(id)
                val size = WidgetSize(
                    widthDp = options2.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
                    heightDp = options2.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
                )
                manager.updateAppWidget(
                    id,
                    WidgetRenderer.build(
                        context = context,
                        repository = repository,
                        nowMillis = now,
                        size = size,
                        appWidgetId = id,
                    ),
                )
            }
        }

        /**
         * Arms the next repaint: the earlier of the next prayer for the selected location and local
         * midnight plus a second, never sooner than a minute away. `set` rather than
         * `setExactAndAllowWhileIdle`: the content is informational, so let the system batch it.
         */
        private fun schedule(context: Context) {
            val repository = WaktuSholatApp.instance.repository
            val now = System.currentTimeMillis()
            val city = repository.city
            val minute = LocalClock.minuteOfDay(now, city.timeZoneHours)

            val nextMinute = repository.snapshot(now).nextMinute
            val nextPrayerAt = nextMinute?.let { nextPrayerMillis(now, minute, it) }
            val midnightAt = now + LocalClock.millisUntilNextDay(now, city.timeZoneHours) + 1_000L
            val at = if (nextPrayerAt == null) midnightAt else minOf(nextPrayerAt, midnightAt)

            val manager = context.getSystemService(AlarmManager::class.java) ?: return
            val pending = refreshIntent(context)
            manager.cancel(pending)
            manager.set(AlarmManager.RTC, at.coerceAtLeast(now + MIN_TRIGGER_MILLIS), pending)
        }

        private fun cancel(context: Context) {
            val manager = context.getSystemService(AlarmManager::class.java) ?: return
            manager.cancel(refreshIntent(context))
        }

        /** Epoch millis of the next prayer, using the same minute-of-day the countdown shows. */
        private fun nextPrayerMillis(nowMillis: Long, minuteOfDay: Int, nextMinute: Int): Long {
            val rollover = if (nextMinute > minuteOfDay) 0 else 1440
            return (nowMillis / 60_000L + (nextMinute + rollover - minuteOfDay)) * 60_000L
        }

        private fun refreshIntent(context: Context): PendingIntent {
            val intent = Intent(context, PrayerWidgetProvider::class.java)
                .setAction(ACTION_REFRESH_WIDGET)
            return PendingIntent.getBroadcast(
                context,
                REFRESH_REQUEST,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
