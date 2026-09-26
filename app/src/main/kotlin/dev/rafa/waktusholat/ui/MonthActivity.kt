package dev.rafa.waktusholat.ui

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.rafa.waktusholat.R
import dev.rafa.waktusholat.WaktuSholatApp
import dev.rafa.waktusholat.core.CivilDate
import dev.rafa.waktusholat.core.Prayer
import dev.rafa.waktusholat.core.PrayerTimes
import dev.rafa.waktusholat.core.UmmAlQura
import dev.rafa.waktusholat.data.ScheduleRepository

/**
 * One month of schedules as a table with the columns every Indonesian timetable uses. Rendered
 * eagerly: thirty-one rows of eight cells is far below the point where a recycling list pays off.
 * Today is highlighted, Fridays are marked, and rows are striped so a line stays trackable.
 */
class MonthActivity : BaseActivity() {

    private lateinit var repository: ScheduleRepository
    private lateinit var table: LinearLayout

    private var year = 0
    private var month = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_month)
        repository = (application as WaktuSholatApp).repository
        table = findViewById(R.id.table)
        setupTopBar(repository.city.label)

        val today = repository.today()
        year = savedInstanceState?.getInt(STATE_YEAR) ?: today.year
        month = savedInstanceState?.getInt(STATE_MONTH) ?: today.month

        findViewById<FrameLayout>(R.id.header_slot).let { it.addView(headerRow(it)) }
        findViewById<View>(R.id.prev).setOnClickListener { shift(-1) }
        findViewById<View>(R.id.next).setOnClickListener { shift(1) }
        render(scrollToToday = true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_YEAR, year)
        outState.putInt(STATE_MONTH, month)
    }

    private fun shift(delta: Int) {
        val index = year * 12 + (month - 1) + delta
        year = index / 12
        month = index % 12 + 1
        render(scrollToToday = false)
    }

    private fun render(scrollToToday: Boolean) {
        val today = repository.today()
        findViewById<TextView>(R.id.month_title).text = Dates.monthYear(this, year, month)
        findViewById<TextView>(R.id.month_hijri).text = hijriRange()

        table.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val todayIndex = if (today.year == year && today.month == month) today.day - 1 else -1
        repository.month(year, month).forEachIndexed { index, times ->
            table.addView(bodyRow(inflater, times, index, index == todayIndex))
        }

        val scroll = findViewById<ScrollView>(R.id.scroll)
        if (scrollToToday && todayIndex > 3) {
            // Keep a few days of context above today.
            scroll.post { table.getChildAt(todayIndex - 3)?.let { scroll.scrollTo(0, it.top) } }
        } else {
            scroll.scrollTo(0, 0)
        }
    }

    /** `Rabiul Awal – Rabiul Akhir 1448`, spanning the Hijri months this Gregorian month covers. */
    private fun hijriRange(): String {
        val first = UmmAlQura.fromGregorian(year, month, 1) ?: return ""
        val last = UmmAlQura.fromGregorian(year, month, CivilDate.daysInMonth(year, month)) ?: return ""
        return when {
            first.month == last.month -> "${Dates.hijriMonth(this, first.month)} ${first.year}"
            first.year == last.year ->
                "${Dates.hijriMonth(this, first.month)} – ${Dates.hijriMonth(this, last.month)} ${last.year}"
            else ->
                "${Dates.hijriMonth(this, first.month)} ${first.year} – ${Dates.hijriMonth(this, last.month)} ${last.year}"
        }
    }

    /** Column header, pinned above the scrolling table. Uses the body's own row layout. */
    private fun headerRow(parent: FrameLayout): View {
        val row = LayoutInflater.from(this).inflate(R.layout.item_month_row, parent, false) as ViewGroup
        row.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (28 * resources.displayMetrics.density).toInt(),
        )
        val color = getColor(R.color.text_tertiary)
        for (i in 0 until row.childCount) {
            (row.getChildAt(i) as TextView).apply {
                setTextColor(color)
                textSize = 11f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
        }
        (row.getChildAt(1) as TextView).setText(R.string.month_col_date)
        PRAYER_COLUMNS.forEachIndexed { index, prayer ->
            (row.getChildAt(index + 2) as TextView).text = getString(PrayerLabels.id(prayer))
        }
        return row
    }

    private fun bodyRow(inflater: LayoutInflater, times: PrayerTimes, index: Int, isToday: Boolean): View {
        val row = inflater.inflate(R.layout.item_month_row, table, false) as ViewGroup
        val weekday = times.date.dayOfWeek
        val ink = getColor(if (isToday) R.color.brand else R.color.text_primary)
        // Friday is the congregation day, so it is the one weekday worth marking.
        val weekInk = getColor(
            when {
                isToday || weekday == FRIDAY -> R.color.brand
                else -> R.color.text_tertiary
            },
        )
        val face = if (isToday) Typeface.create("sans-serif-medium", Typeface.NORMAL) else null

        (row.getChildAt(0) as TextView).apply {
            text = Dates.weekdayInitial(this@MonthActivity, weekday)
            setTextColor(weekInk)
        }
        (row.getChildAt(1) as TextView).apply {
            text = (index + 1).toString()
            setTextColor(ink)
            face?.let { typeface = it }
        }
        PRAYER_COLUMNS.forEachIndexed { column, prayer ->
            (row.getChildAt(column + 2) as TextView).apply {
                text = PrayerTimes.format(times[prayer])
                setTextColor(ink)
                face?.let { typeface = it }
            }
        }
        when {
            isToday -> row.setBackgroundResource(R.drawable.row_active)
            index % 2 == 1 -> row.setBackgroundResource(R.drawable.row_stripe)
        }
        return row
    }

    private companion object {
        const val STATE_YEAR = "year"
        const val STATE_MONTH = "month"
        const val FRIDAY = 5

        val PRAYER_COLUMNS: List<Prayer> = listOf(
            Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA,
        )
    }
}
