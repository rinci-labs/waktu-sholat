package dev.rafa.waktusholat.ui

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import dev.rafa.waktusholat.R
import dev.rafa.waktusholat.WaktuSholatApp
import dev.rafa.waktusholat.core.CivilDate
import dev.rafa.waktusholat.core.Prayer
import dev.rafa.waktusholat.core.PrayerTimes
import dev.rafa.waktusholat.data.ScheduleRepository

/**
 * One month of schedules as a scrollable table, with the columns every Indonesian prayer timetable
 * uses. The month is rendered eagerly: thirty-one rows of seven columns is far below the point where
 * a recycling list would pay for itself.
 *
 * Today's row is tinted and emphasised, and rows are striped so the eye can track a line across seven
 * numeric columns.
 */
class MonthActivity : Activity() {

    private lateinit var repository: ScheduleRepository
    private lateinit var table: LinearLayout
    private lateinit var title: TextView

    private var year = 0
    private var month = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_month)
        setTitle(R.string.section_month)

        repository = ScheduleRepository((application as WaktuSholatApp).preferences)
        table = findViewById(R.id.table)
        title = findViewById(R.id.month_title)

        val today = repository.today()
        year = today.year
        month = today.month

        findViewById<View>(R.id.prev).setOnClickListener { shift(-1) }
        findViewById<View>(R.id.next).setOnClickListener { shift(1) }

        render()
    }

    private fun shift(delta: Int) {
        month += delta
        if (month < 1) {
            month = 12
            year--
        } else if (month > 12) {
            month = 1
            year++
        }
        render()
    }

    private fun render() {
        val today = repository.today()
        title.text = getString(R.string.month_title, "${MONTH_NAMES[month - 1]} $year")

        table.removeAllViews()
        val inflater = LayoutInflater.from(this)
        table.addView(headerRow(inflater))

        val schedules = repository.month(year, month)
        val todayCell = if (today.year == year && today.month == month) today.day else -1

        schedules.forEachIndexed { index, times ->
            val day = index + 1
            table.addView(bodyRow(inflater, times, day, day == todayCell, index))
        }
    }

    /** Column header. Uses the same cell weights as the body so the columns line up exactly. */
    private fun headerRow(inflater: LayoutInflater): View {
        val row = inflater.inflate(R.layout.item_month_row, table, false) as ViewGroup
        for (cell in 0 until row.childCount) {
            (row.getChildAt(cell) as TextView).setTextColor(getColor(R.color.text_tertiary))
        }
        // The two leading cells are too narrow for a label; the prayer names start at index 2.
        PRAYER_COLUMNS.forEachIndexed { index, prayer ->
            (row.getChildAt(index + 2) as TextView).text = getString(PrayerLabels.id(prayer))
        }
        return row
    }

    private fun bodyRow(
        inflater: LayoutInflater,
        times: PrayerTimes,
        day: Int,
        isToday: Boolean,
        index: Int,
    ): View {
        val row = inflater.inflate(R.layout.item_month_row, table, false) as ViewGroup
        val weekday = times.date.dayOfWeek
        val ink = getColor(if (isToday) R.color.brand else R.color.text_primary)
        val weekInk = getColor(
            // Friday is the congregation day and Sunday is the weekend, so both are worth marking.
            when {
                isToday -> R.color.brand
                weekday == 7 -> R.color.brand
                weekday == 5 -> R.color.text_secondary
                else -> R.color.text_tertiary
            },
        )

        (row.getChildAt(0) as TextView).apply {
            text = WEEKDAY_INITIALS[weekday - 1]
            setTextColor(weekInk)
        }
        (row.getChildAt(1) as TextView).apply {
            text = day.toString()
            setTextColor(ink)
        }
        PRAYER_COLUMNS.forEachIndexed { column, prayer ->
            (row.getChildAt(column + 2) as TextView).apply {
                text = PrayerTimes.format(times[prayer])
                setTextColor(ink)
                if (isToday) typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
        }
        if (isToday) {
            row.setBackgroundResource(R.drawable.row_active)
        } else if (index % 2 == 1) {
            row.setBackgroundResource(R.drawable.row_stripe)
        }
        return row
    }

    private companion object {
        /** The date column is separate, so the table shows six prayer columns. */
        val PRAYER_COLUMNS: List<Prayer> = listOf(
            Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA,
        )

        val MONTH_NAMES = arrayOf(
            "Januari", "Februari", "Maret", "April", "Mei", "Juni",
            "Juli", "Agustus", "September", "Oktober", "November", "Desember",
        )

        /**
         * Indonesian weekday initials, Monday first, matching `CivilDate.dayOfWeek` (1 = Monday):
         * Senin, Selasa, Rabu, Kamis, Jumat, Sabtu, Minggu.
         */
        val WEEKDAY_INITIALS = arrayOf("S", "S", "R", "K", "J", "S", "M")
    }
}
