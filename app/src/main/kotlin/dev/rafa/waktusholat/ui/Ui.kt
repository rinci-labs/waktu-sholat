package dev.rafa.waktusholat.ui

import android.app.Activity
import android.content.Context
import android.widget.TextView
import dev.rafa.waktusholat.R
import dev.rafa.waktusholat.core.CalculationMethod
import dev.rafa.waktusholat.core.CivilDate
import dev.rafa.waktusholat.core.HijriDate
import dev.rafa.waktusholat.core.Prayer

/** Wires the shared top bar: title plus a back button that simply finishes the screen. */
fun Activity.setupTopBar(title: CharSequence) {
    findViewById<TextView>(R.id.title).text = title
    findViewById<android.view.View>(R.id.back).setOnClickListener { finish() }
}

/** Localized display names for the seven daily entries. */
object PrayerLabels {

    fun of(context: Context, prayer: Prayer): String = context.getString(id(prayer))

    fun id(prayer: Prayer): Int = when (prayer) {
        Prayer.IMSAK -> R.string.prayer_imsak
        Prayer.FAJR -> R.string.prayer_fajr
        Prayer.SUNRISE -> R.string.prayer_sunrise
        Prayer.DHUHR -> R.string.prayer_dhuhr
        Prayer.ASR -> R.string.prayer_asr
        Prayer.MAGHRIB -> R.string.prayer_maghrib
        Prayer.ISHA -> R.string.prayer_isha
    }

    /** Entries shown in the daily list, honouring the user's Imsak preference. */
    fun visible(showImsak: Boolean): List<Prayer> =
        if (showImsak) Prayer.DAILY else Prayer.DAILY.filter { it != Prayer.IMSAK }
}

/**
 * Calendar wording from resources, so it follows the app language. Month and weekday names are
 * string arrays rather than `java.text` output because the platform's Indonesian names differ
 * between API levels.
 */
object Dates {

    /** `Saturday, 26 September 2026` / `Sabtu, 26 September 2026`. */
    fun long(context: Context, date: CivilDate): String {
        val weekday = context.resources.getStringArray(R.array.weekdays)[date.dayOfWeek - 1]
        val month = context.resources.getStringArray(R.array.months)[date.month - 1]
        return context.getString(R.string.date_long, weekday, date.day, month, date.year)
    }

    /** `September 2026`. */
    fun monthYear(context: Context, year: Int, month: Int): String =
        "${context.resources.getStringArray(R.array.months)[month - 1]} $year"

    /** One-letter weekday for the month table, Monday first (matches `CivilDate.dayOfWeek`). */
    fun weekdayInitial(context: Context, isoDay: Int): String =
        context.resources.getStringArray(R.array.weekday_initials)[isoDay - 1]

    fun hijriMonth(context: Context, month: Int): String =
        context.resources.getStringArray(R.array.hijri_months)[month - 1]

    /** `15 Rabi al-Thani 1448` / `15 Rabiul Akhir 1448`. */
    fun hijri(context: Context, date: HijriDate): String = "${date.day} ${hijriMonth(context, date.month)} ${date.year}"
}

/** Localized region for a calculation method, in [CalculationMethod.entries] order. */
fun CalculationMethod.regionLabel(context: Context): String =
    context.resources.getStringArray(R.array.method_regions)[ordinal]

/**
 * Relative time until a prayer, in words: easier to read at a glance than a ticking clock. Minutes
 * count the current one as elapsed, so the figure never promises more time than there is.
 */
object Relative {

    /** `7 jam 37 menit`, `2 jam`, `37 menit`, or `kurang dari 1 menit`. */
    fun long(context: Context, minutes: Int): String = format(context, minutes, R.string.rel_hours_minutes, R.string.rel_minutes)

    /** `7 jam 37 mnt`: the same, abbreviated for widgets. */
    fun short(context: Context, minutes: Int): String = format(context, minutes, R.string.rel_hours_minutes_short, R.string.rel_minutes_short)

    private fun format(context: Context, minutes: Int, both: Int, onlyMinutes: Int): String = when {
        minutes < 1 -> context.getString(R.string.rel_now)
        minutes < 60 -> context.getString(onlyMinutes, minutes)
        minutes % 60 == 0 -> context.getString(R.string.rel_hours, minutes / 60)
        else -> context.getString(both, minutes / 60, minutes % 60)
    }
}
