package dev.rafa.waktusholat.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import dev.rafa.waktusholat.R
import dev.rafa.waktusholat.core.Prayer
import dev.rafa.waktusholat.core.PrayerTimes
import dev.rafa.waktusholat.data.ScheduleRepository.Snapshot
import dev.rafa.waktusholat.ui.Dates
import dev.rafa.waktusholat.ui.MainActivity
import dev.rafa.waktusholat.ui.PrayerLabels
import dev.rafa.waktusholat.ui.Relative

/**
 * Base for every home-screen widget. Subclasses only render; [WidgetUpdater] owns refresh timing.
 *
 * ## Why colour is never assigned from code
 *
 * `setTextColor` writes a resolved ARGB int into the RemoteViews action list, which the launcher
 * caches and replays, so a colour resolved in light mode would stay light after a switch to dark.
 * Colours therefore come only from layouts and styles (resource ids the launcher re-resolves), and
 * "active" rows are separate layouts rather than recoloured ones.
 */
abstract class BaseWidgetProvider : AppWidgetProvider() {

    abstract fun build(context: Context, snapshot: Snapshot, size: WidgetSize): RemoteViews

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) =
        WidgetUpdater.update(context, this, appWidgetIds)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        WidgetUpdater.update(context, this, intArrayOf(id), options)

    /** When the last widget of any type goes, the repaint alarm goes with it. */
    override fun onDisabled(context: Context) = WidgetUpdater.updateAll(context)

    protected fun views(context: Context, layout: Int): RemoteViews =
        RemoteViews(context.packageName, layout).apply {
            setOnClickPendingIntent(android.R.id.background, openApp(context))
        }

    protected fun RemoteViews.bindNext(context: Context, snapshot: Snapshot) {
        setTextViewText(R.id.widget_next_name, PrayerLabels.of(context, snapshot.next))
        setTextViewText(R.id.widget_next_time, PrayerTimes.format(snapshot.nextMinute))
    }

    /** `Subuh 7 jam 33 mnt lagi`: refreshed every minute by [WidgetUpdater]. */
    protected fun relative(context: Context, snapshot: Snapshot): String = context.getString(
        R.string.widget_next_relative,
        PrayerLabels.of(context, snapshot.next),
        Relative.short(context, snapshot.minutesRemaining),
    )

    /** `7 jam 33 mnt lagi`, without the prayer name. */
    protected fun remaining(context: Context, snapshot: Snapshot): String =
        context.getString(R.string.countdown_in, Relative.short(context, snapshot.minutesRemaining))

    /**
     * Starts a counting-down Chronometer that reaches zero at the next prayer. Its base is on the
     * elapsed-realtime clock, so it keeps ticking correctly across wall-clock changes until the
     * repaint alarm replaces it.
     */
    protected fun RemoteViews.bindCountdown(context: Context, snapshot: Snapshot, format: Int?) {
        val base = SystemClock.elapsedRealtime() + (snapshot.nextAtMillis - System.currentTimeMillis())
        setChronometer(R.id.widget_countdown, base, format?.let { context.getString(it) }, true)
        setChronometerCountDown(R.id.widget_countdown, true)
    }

    protected fun RemoteViews.visible(id: Int, visible: Boolean) =
        setViewVisibility(id, if (visible) View.VISIBLE else View.GONE)

    /** The day's entries a widget lists: Imsak is a Ramadan marker the widgets have no room to explain. */
    protected val listed: List<Prayer> = Prayer.DAILY.filter { it != Prayer.IMSAK }

    /** The prayer to highlight; before Fajr nothing on today's list is in effect yet. */
    protected fun active(snapshot: Snapshot): Prayer? =
        if (snapshot.hasPassed(Prayer.FAJR)) snapshot.current else null

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** "Sholat berikutnya": the next prayer, large, with a live countdown. */
class NextWidgetProvider : BaseWidgetProvider() {
    override fun build(context: Context, snapshot: Snapshot, size: WidgetSize) = views(context, R.layout.widget_next).apply {
        bindNext(context, snapshot)
        setTextViewText(R.id.widget_relative, remaining(context, snapshot))
        setTextViewText(R.id.widget_location, snapshot.city.name)
        // Two cells tall fits every line; one cell only fits name, time and countdown.
        val roomy = size.heightDp >= 100
        visible(R.id.widget_label, roomy)
        visible(R.id.widget_location, roomy)
    }
}

/**
 * "Jadwal hari ini": the day as a horizontal band. Keeps the historical class name so widgets placed
 * by earlier versions survive the update.
 */
class PrayerWidgetProvider : BaseWidgetProvider() {
    override fun build(context: Context, snapshot: Snapshot, size: WidgetSize) = views(context, R.layout.widget_times).apply {
        // The header fits a single row on real phones (~64dp and up); only a squeezed cell drops it.
        val header = size.heightDp == 0 || size.heightDp >= 56
        visible(R.id.widget_header, header)
        if (header) {
            setTextViewText(R.id.widget_location, snapshot.city.name)
            setTextViewText(R.id.widget_relative, relative(context, snapshot))
        }
        // Roughly 46dp per cell; a narrow widget shows a window that always contains the active prayer.
        val count = (size.widthDp / 46).coerceIn(3, listed.size)
        val anchor = listed.indexOf(active(snapshot) ?: snapshot.next).coerceAtLeast(0)
        val from = anchor.coerceIn(0, listed.size - count)
        val active = active(snapshot)
        removeAllViews(R.id.widget_cells)
        for (prayer in listed.subList(from, from + count)) {
            val layout = if (prayer == active) R.layout.widget_prayer_cell_active else R.layout.widget_prayer_cell
            addView(R.id.widget_cells, RemoteViews(context.packageName, layout).apply {
                setTextViewText(R.id.cell_name, PrayerLabels.of(context, prayer))
                setTextViewText(R.id.cell_time, PrayerTimes.format(snapshot.times[prayer]))
            })
        }
    }
}

/** "Jadwal lengkap": the next prayer on top of the whole day as a list. */
class ScheduleWidgetProvider : BaseWidgetProvider() {
    override fun build(context: Context, snapshot: Snapshot, size: WidgetSize) = views(context, R.layout.widget_schedule).apply {
        bindNext(context, snapshot)
        setTextViewText(R.id.widget_location, snapshot.city.name)
        setTextViewText(R.id.widget_relative, remaining(context, snapshot))
        setTextViewText(R.id.widget_hijri, snapshot.day.hijri?.let { Dates.hijri(context, it) }.orEmpty())
        // The hero needs about 60dp on top of six 24dp rows; below that the list alone is kept.
        visible(R.id.widget_hero, size.heightDp >= 210)
        visible(R.id.widget_hijri, size.heightDp >= 240)
        val active = active(snapshot)
        removeAllViews(R.id.widget_rows)
        for (prayer in listed) {
            val layout = if (prayer == active) R.layout.widget_schedule_row_active else R.layout.widget_schedule_row
            addView(R.id.widget_rows, RemoteViews(context.packageName, layout).apply {
                setTextViewText(R.id.cell_name, PrayerLabels.of(context, prayer))
                setTextViewText(R.id.cell_time, PrayerTimes.format(snapshot.times[prayer]))
            })
        }
    }
}

/** "Hitung mundur": an accent tile with a live countdown. */
class CountdownWidgetProvider : BaseWidgetProvider() {
    override fun build(context: Context, snapshot: Snapshot, size: WidgetSize) = views(context, R.layout.widget_countdown).apply {
        setTextViewText(R.id.widget_next_name, PrayerLabels.of(context, snapshot.next))
        setTextViewText(
            R.id.widget_next_time,
            context.getString(R.string.widget_at_time, PrayerTimes.format(snapshot.nextMinute)),
        )
        bindCountdown(context, snapshot, null)
    }
}

/** "Minimalis": text on the wallpaper, no background. */
class GlanceWidgetProvider : BaseWidgetProvider() {
    override fun build(context: Context, snapshot: Snapshot, size: WidgetSize) = views(context, R.layout.widget_glance).apply {
        bindNext(context, snapshot)
        setTextViewText(R.id.widget_relative, remaining(context, snapshot))
        setTextViewText(R.id.widget_location, context.getString(R.string.widget_city_suffix, snapshot.city.name))
    }
}
