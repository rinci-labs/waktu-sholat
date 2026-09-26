package dev.rafa.waktusholat.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import dev.rafa.waktusholat.R
import dev.rafa.waktusholat.core.City
import dev.rafa.waktusholat.core.LocalClock
import dev.rafa.waktusholat.core.Prayer
import dev.rafa.waktusholat.core.PrayerMoment
import dev.rafa.waktusholat.core.PrayerTimes
import dev.rafa.waktusholat.data.ScheduleRepository
import dev.rafa.waktusholat.ui.MainActivity
import dev.rafa.waktusholat.ui.PrayerLabels

/** Which widget form a measured size calls for. */
enum class WidgetForm {
    /** Next prayer only: for a small or narrow cell. */
    THUMB,

    /** The day's times laid out as a horizontal band: for a wide, short cell such as 4x1. */
    TIMES,

    /** Next prayer large above a list: for a tall cell such as 3x2 and up. */
    HERO,
}

/** How much of the widget's surrounding chrome fits in the granted height. */
enum class Chrome { NONE, HEADER, FULL }

/**
 * A measured widget size in dp, as reported by the launcher.
 *
 * The renderer takes this rather than a pre-decided form so the whole size policy lives in one place:
 * a form cannot be chosen without also deciding how many entries fit and which chrome to drop.
 */
data class WidgetSize(val widthDp: Int, val heightDp: Int)

/**
 * Turns a schedule into RemoteViews. This object only renders; [PrayerWidgetProvider] remains the
 * single place that touches the alarm manager.
 *
 * ## Why colour is never assigned from code
 *
 * `setTextColor` writes a resolved ARGB int into the RemoteViews action list, and the launcher caches
 * and replays that list. A colour resolved while the device was in light mode would therefore stay
 * light after the user switches to dark, making the widget unreadable until something else triggered a
 * re-render. Every colour comes from a layout, a style or `setBackgroundResource` instead — all of
 * which carry resource ids the launcher re-resolves against the active configuration. The only
 * per-prayer state applied at render time is visibility and that one background resource.
 *
 * ## Why the forms hide their own chrome
 *
 * A 4x1 cell is roughly 40dp tall, which fits one band of text and nothing else. Rather than a layout
 * per size (which would need keeping in sync), each layout hides the pieces that do not fit, so a
 * resize can never land on a form missing a view the renderer writes to.
 */
object WidgetRenderer {

    /** Below this height (dp) only one band of content fits. */
    const val SHORT_HEIGHT_MAX_DP = 70

    /** At or above this height (dp) the list form has room. */
    const val LIST_HEIGHT_MIN_DP = 130

    /** Below this width (dp) a row of prayer cells is too cramped to read. */
    const val WIDE_WIDTH_MIN_DP = 180

    /** Width (dp) one cell of the horizontal band needs. Six cells plus padding fit in 4 columns. */
    const val CELL_WIDTH_DP = 44

    /** Width (dp) one hero column needs. */
    const val HERO_ENTRY_WIDTH_DP = 46

    /** Height (dp) from which the header line fits above the content. */
    private const val HEADER_HEIGHT_MIN_DP = 90

    /**
     * The form for a measured size.
     *
     * Height alone is not enough: a 4x1 cell is only ~40dp tall but 250dp wide, and the horizontal
     * band is exactly what fits there. Width alone is not enough either, since a tall narrow cell
     * wants the list. Both are needed, which is why this takes a [WidgetSize].
     */
    fun formFor(size: WidgetSize): WidgetForm = when {
        size.heightDp <= SHORT_HEIGHT_MAX_DP ->
            if (size.widthDp >= WIDE_WIDTH_MIN_DP) WidgetForm.TIMES else WidgetForm.THUMB
        size.widthDp < WIDE_WIDTH_MIN_DP -> WidgetForm.THUMB
        size.heightDp <= LIST_HEIGHT_MIN_DP -> WidgetForm.TIMES
        else -> WidgetForm.HERO
    }

    fun layoutFor(form: WidgetForm): Int = when (form) {
        WidgetForm.THUMB -> R.layout.widget_next_tiny
        WidgetForm.TIMES -> R.layout.widget_times
        WidgetForm.HERO -> R.layout.widget_next
    }

    /**
     * Renders the form and chrome that fit [size].
     */
    fun build(
        context: Context,
        repository: ScheduleRepository,
        nowMillis: Long = System.currentTimeMillis(),
        size: WidgetSize,
        appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
    ): RemoteViews {
        val form = formFor(size)
        val chrome = when {
            size.heightDp < HEADER_HEIGHT_MIN_DP -> Chrome.NONE
            size.heightDp <= LIST_HEIGHT_MIN_DP -> Chrome.HEADER
            else -> Chrome.FULL
        }
        // One entry per roughly 44-46dp of width; never fewer than three, since a band of two looks
        // like a bug rather than a deliberate window on a longer list.
        val entries = (size.widthDp / CELL_WIDTH_DP).coerceAtLeast(3)
        return build(
            context = context,
            repository = repository,
            nowMillis = nowMillis,
            form = form,
            chrome = chrome,
            maxEntries = entries,
            appWidgetId = appWidgetId,
        )
    }

    /** Renders [form] with explicit chrome and entry cap. Prefer the [WidgetSize] overload. */
    fun build(
        context: Context,
        repository: ScheduleRepository,
        nowMillis: Long,
        form: WidgetForm,
        chrome: Chrome,
        maxEntries: Int,
        appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
    ): RemoteViews {
        val moment = repository.moment(nowMillis)
        val views = RemoteViews(context.packageName, layoutFor(form))

        when (form) {
            // The thumbnail is the only form whose whole content is the next prayer.
            WidgetForm.THUMB -> {
                val target = target(repository, moment, nowMillis)
                views.setTextViewText(R.id.widget_next_name, PrayerLabels.of(context, target.prayer))
                views.setTextViewText(R.id.widget_next_time, PrayerTimes.format(target.minute))
                views.setTextViewText(R.id.widget_countdown, countdown(context, moment))
            }

            // The horizontal band spends its width on the day's times, so it carries no hero block.
            WidgetForm.TIMES -> {
                bindHeader(context, views, repository, nowMillis, chrome)
                bindFooter(
                    context = context,
                    views = views,
                    text = countdown(context, moment),
                    visible = chrome != Chrome.NONE,
                )
                bindEntries(
                    context, views, R.id.widget_cells, repository, nowMillis, moment, maxEntries,
                )
            }

            WidgetForm.HERO -> {
                val target = target(repository, moment, nowMillis)
                bindHeader(context, views, repository, nowMillis, chrome)
                views.setTextViewText(R.id.widget_next_name, PrayerLabels.of(context, target.prayer))
                views.setTextViewText(R.id.widget_next_time, PrayerTimes.format(target.minute))
                views.setTextViewText(R.id.widget_countdown, countdown(context, moment))
                bindFooter(
                    context = context,
                    views = views,
                    text = footer(repository.city, nowMillis),
                    visible = chrome == Chrome.FULL,
                )
                bindEntries(
                    context, views, R.id.widget_rows, repository, nowMillis, moment, maxEntries,
                )
            }
        }

        views.setOnClickPendingIntent(R.id.widget_root, launch(context, appWidgetId))
        return views
    }

    /** Sets the footer line, or hides it when the height cannot spare the row. */
    private fun bindFooter(
        context: Context,
        views: RemoteViews,
        text: String,
        visible: Boolean,
    ) {
        views.setViewVisibility(R.id.widget_footer, if (visible) View.VISIBLE else View.GONE)
        if (visible) views.setTextViewText(R.id.widget_footer, text)
    }

    /** Location and Hijri date; hidden entirely when the height cannot spare the line. */
    private fun bindHeader(
        context: Context,
        views: RemoteViews,
        repository: ScheduleRepository,
        nowMillis: Long,
        chrome: Chrome,
    ) {
        val visible = if (chrome == Chrome.NONE) View.GONE else View.VISIBLE
        views.setViewVisibility(R.id.widget_header, visible)
        if (chrome == Chrome.NONE) return
        views.setTextViewText(R.id.widget_location, repository.city.label)
        views.setTextViewText(R.id.widget_hijri, hijriText(repository, nowMillis))
    }

    /**
     * Fills [container] with one cell per prayer.
     *
     * The set is the whole day rather than only what remains, because a list that empties out after
     * Isha reads as a broken widget; the current prayer is marked instead, which is the information a
     * glance is actually after.
     */
    private fun bindEntries(
        context: Context,
        views: RemoteViews,
        container: Int,
        repository: ScheduleRepository,
        nowMillis: Long,
        moment: PrayerMoment,
        maxEntries: Int,
    ) {
        val times = repository.todayTimes(nowMillis)
        val visible = visiblePrayers()
        val shown = window(visible, moment.prayer, maxEntries)
        views.removeAllViews(container)
        for (prayer in shown) {
            val cell = RemoteViews(context.packageName, R.layout.widget_prayer_cell)
            cell.setTextViewText(R.id.cell_name, PrayerLabels.of(context, prayer))
            cell.setTextViewText(R.id.cell_time, PrayerTimes.format(times[prayer]))
            if (prayer == moment.prayer) {
                // A resource, not a colour: see the class note on theme-following.
                cell.setInt(R.id.cell_name, "setBackgroundResource", R.drawable.widget_cell_active)
                cell.setInt(R.id.cell_time, "setBackgroundResource", R.drawable.widget_cell_active)
            }
            views.addView(container, cell)
        }
        // Equal share of the width, so five or six cells divide it evenly.
        views.setFloat(container, "setWeightSum", shown.size.toFloat())
    }

    /**
     * The prayers a widget lists. Imsak is omitted: it is a Ramadan marker rather than a prayer, and
     * the widget has no room to clarify that.
     */
    private fun visiblePrayers(): List<Prayer> = Prayer.DAILY.filter { it != Prayer.IMSAK }

    /** The prayer being counted down to, resolved to a displayed minute of day. */
    private fun target(
        repository: ScheduleRepository,
        moment: PrayerMoment,
        nowMillis: Long,
    ): Target {
        val next = moment.next
        val minute = moment.nextMinute
        if (next != null && minute != null) return Target(next, minute)
        // Isha has begun and no next prayer is known: the countdown runs to tomorrow's Fajr.
        return Target(Prayer.FAJR, repository.tomorrowTimes(nowMillis)[Prayer.FAJR])
    }

    /** `2 jam 5 mnt lagi`, or the next-day wording once Isha has begun. */
    private fun countdown(context: Context, moment: PrayerMoment): String {
        if (!moment.hasNext) return context.getString(R.string.after_isha)
        val minutes = moment.minutesRemaining ?: return ""
        return when {
            minutes >= 60 -> context.getString(R.string.widget_countdown_hours, minutes / 60, minutes % 60)
            else -> context.getString(R.string.widget_countdown_minutes, minutes)
        }
    }

    /** `15 Rabiul Akhir 1448`; empty outside the Umm al-Qura table's range. */
    private fun hijriText(repository: ScheduleRepository, nowMillis: Long): String =
        repository.hijri(repository.today(nowMillis))?.toString() ?: ""

    /** `WIB 20:41`: the zone the schedule is expressed in plus the time there now. */
    private fun footer(city: City, nowMillis: Long): String {
        val clock = PrayerTimes.format(LocalClock.minuteOfDay(nowMillis, city.timeZoneHours))
        return "${city.zoneLabel} $clock"
    }

    /**
     * At most [limit] entries, positioned so [current] stays inside the slice: a widget that dropped
     * the current prayer would look stuck on the wrong one. When the whole day fits, the slice is the
     * whole day, so the widget always shows times.
     */
    private fun window(visible: List<Prayer>, current: Prayer, limit: Int): List<Prayer> {
        if (limit >= visible.size) return visible
        val size = limit.coerceAtLeast(1)
        val anchor = visible.indexOf(current).coerceAtLeast(0)
        val from = anchor.coerceIn(0, visible.size - size)
        return visible.subList(from, from + size)
    }

    /**
     * Opens the app. The request code is the widget id, so two widgets never share a PendingIntent and
     * a removed widget cannot drag another one's click target with it.
     */
    private fun launch(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private class Target(val prayer: Prayer, val minute: Int)
}
