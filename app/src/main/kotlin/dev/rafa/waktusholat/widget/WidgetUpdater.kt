package dev.rafa.waktusholat.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import dev.rafa.waktusholat.WaktuSholatApp
import dev.rafa.waktusholat.data.ScheduleRepository
import dev.rafa.waktusholat.ui.Language

/**
 * The single place that repaints widgets and keeps them current.
 *
 * Every widget type renders from one [ScheduleRepository.Snapshot] taken at one instant, so two
 * widgets can never disagree about the minute.
 *
 * The relative "7 jam 37 mnt lagi" text changes every minute, so one alarm is armed for the next
 * minute boundary. It is exact, so the widget turns over to the next prayer on the very minute the
 * current one begins, and `RTC`, not `RTC_WAKEUP`: while the screen is off it never wakes the
 * device, and the system delivers it once on the next wake, so the widget is current the moment it
 * can be seen. It is cancelled as soon as the last widget is removed. Each widget's
 * `updatePeriodMillis` restarts the chain should the alarm ever be lost.
 */
object WidgetUpdater {

    /** One stateless instance of every widget type, used purely as its renderer. */
    private val PROVIDERS: List<BaseWidgetProvider> by lazy {
        listOf(
            NextWidgetProvider(),
            PrayerWidgetProvider(),
            ScheduleWidgetProvider(),
            CountdownWidgetProvider(),
            GlanceWidgetProvider(),
        )
    }

    /** Allowed lateness when exact alarms are not permitted; the system may stretch it further. */
    private const val WINDOW_MILLIS = 5_000L

    private const val MINUTE_MILLIS = 60_000L

    /** Arms the next repaint, then repaints every placed widget of every type. */
    fun updateAll(base: Context) {
        val context = Language.wrap(base)
        val manager = AppWidgetManager.getInstance(context) ?: return
        val snapshot = WaktuSholatApp.instance.repository.snapshot()
        val placed = PROVIDERS.associateWith { manager.getAppWidgetIds(ComponentName(context, it.javaClass)) }
            .filterValues { it.isNotEmpty() }
        // Arm the next repaint before drawing, so a failure while drawing can never end the chain.
        if (placed.isEmpty()) return cancel(context)
        schedule(context, snapshot)
        for ((provider, ids) in placed) {
            for (id in ids) paint(manager, id) { provider.build(context, snapshot, sizeOf(manager, id)) }
        }
    }

    /** Repaints specific widgets of one type, e.g. after a resize. */
    fun update(base: Context, provider: BaseWidgetProvider, ids: IntArray, options: Bundle? = null) {
        val context = Language.wrap(base)
        val manager = AppWidgetManager.getInstance(context) ?: return
        val snapshot = WaktuSholatApp.instance.repository.snapshot()
        schedule(context, snapshot)
        for (id in ids) {
            val size = if (options != null) WidgetSize.of(options) else sizeOf(manager, id)
            paint(manager, id) { provider.build(context, snapshot, size) }
        }
    }

    /** One widget failing to draw (say, a launcher reporting an odd size) must not blank the rest. */
    private inline fun paint(manager: AppWidgetManager, id: Int, views: () -> RemoteViews) {
        runCatching { manager.updateAppWidget(id, views()) }
            .onFailure { Log.w("WidgetUpdater", "Widget $id failed to draw", it) }
    }

    private fun sizeOf(manager: AppWidgetManager, id: Int) = WidgetSize.of(manager.getAppWidgetOptions(id))

    private fun schedule(context: Context, snapshot: ScheduleRepository.Snapshot) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        // Just past the next minute boundary, so the repaint lands on the new minute.
        val at = (snapshot.nowMillis / MINUTE_MILLIS + 1) * MINUTE_MILLIS + 500L
        val intent = refreshIntent(context)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExact(AlarmManager.RTC, at, intent)
        } else {
            alarms.setWindow(AlarmManager.RTC, at, WINDOW_MILLIS, intent)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(refreshIntent(context))
    }

    private fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, WidgetRefreshReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** Target of the repaint alarm. Not exported: only this app's own PendingIntent can reach it. */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = WidgetUpdater.updateAll(context)
}

/**
 * A widget's current size in dp. In portrait the launcher's cell is [AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH]
 * wide and [AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT] tall, which is what the phone shows most of the time.
 */
data class WidgetSize(val widthDp: Int, val heightDp: Int) {
    companion object {
        fun of(options: Bundle): WidgetSize = WidgetSize(
            widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
            heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                .takeIf { it > 0 } ?: options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
        )
    }
}
