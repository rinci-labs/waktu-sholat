package dev.rafa.waktusholat.ui

import android.content.Context
import dev.rafa.waktusholat.R
import dev.rafa.waktusholat.core.Prayer

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
    fun visible(order: List<Prayer>, showImsak: Boolean): List<Prayer> =
        if (showImsak) order else order.filter { it != Prayer.IMSAK }

    val ALL: List<Prayer> = Prayer.entries
}
