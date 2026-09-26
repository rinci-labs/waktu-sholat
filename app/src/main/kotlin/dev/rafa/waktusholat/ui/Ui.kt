package dev.rafa.waktusholat.ui

import android.app.Activity
import android.content.Context
import android.widget.TextView
import dev.rafa.waktusholat.R
import dev.rafa.waktusholat.core.CivilDate
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
 * Indonesian calendar wording. Hard-coded rather than taken from `java.text` because the app ships
 * a single locale and the platform's Indonesian month names differ between API levels.
 */
object Dates {

    val MONTHS = arrayOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember",
    )

    /** Monday first, matching `CivilDate.dayOfWeek` (1 = Monday). */
    val WEEKDAYS = arrayOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")

    /** `Sabtu, 26 September 2026`. */
    fun long(date: CivilDate): String =
        "${WEEKDAYS[date.dayOfWeek - 1]}, ${date.day} ${MONTHS[date.month - 1]} ${date.year}"

    /** `September 2026`. */
    fun monthYear(year: Int, month: Int): String = "${MONTHS[month - 1]} $year"
}

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
