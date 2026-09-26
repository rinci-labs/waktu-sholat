package dev.rafa.waktusholat.core

/** A Hijri (Islamic) calendar date. Immutable value type. */
data class HijriDate(val year: Int, val month: Int, val day: Int) : Comparable<HijriDate> {

    init {
        require(month in 1..12) { "Hijri month out of range: $month" }
        require(day in 1..30) { "Hijri day out of range: $day" }
    }

    /** Indonesian month name, e.g. `Ramadan`. */
    val monthName: String get() = MONTHS[month - 1]

    override fun compareTo(other: HijriDate): Int {
        year.compareTo(other.year).let { if (it != 0) return it }
        month.compareTo(other.month).let { if (it != 0) return it }
        return day.compareTo(other.day)
    }

    override fun toString(): String = "$day $monthName $year"

    companion object {
        val MONTHS = arrayOf(
            "Muharam", "Safar", "Rabiul Awal", "Rabiul Akhir", "Jumadil Awal", "Jumadil Akhir",
            "Rajab", "Syaban", "Ramadan", "Syawal", "Zulkaidah", "Zulhijah",
        )
    }
}
