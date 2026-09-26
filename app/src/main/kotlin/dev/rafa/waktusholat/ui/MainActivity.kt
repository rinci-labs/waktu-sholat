package dev.rafa.waktusholat.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import dev.rafa.waktusholat.R
import dev.rafa.waktusholat.WaktuSholatApp
import dev.rafa.waktusholat.core.Prayer
import dev.rafa.waktusholat.core.PrayerTimes
import dev.rafa.waktusholat.core.Qibla
import dev.rafa.waktusholat.data.Preferences
import dev.rafa.waktusholat.data.ScheduleRepository

/**
 * Today's schedule and the live countdown.
 *
 * Work is tiered by how often it changes: the rows are inflated once per day, restyled once per
 * minute, and the 1 Hz tick touches nothing but the countdown text. The tick runs from a [Handler]
 * only while the screen is visible, so it costs nothing after [onStop].
 */
class MainActivity : Activity() {

    private lateinit var repository: ScheduleRepository
    private lateinit var preferences: Preferences
    private lateinit var rows: LinearLayout
    private lateinit var countdown: TextView
    private lateinit var progress: ProgressBar

    private val handler = Handler(Looper.getMainLooper())
    private val rowViews = ArrayList<RowViews>(Prayer.DAILY.size)

    /** Identity of what the rows currently show; a change means re-inflating them. */
    private var renderedDay: ScheduleRepository.Day? = null
    private var renderedImsak = false
    private var renderedMinute = -1

    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)

    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            render(repository.snapshot(now))
            // Align to the top of the next second so the digits never appear to stall.
            handler.postDelayed(this, 1000L - now % 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val app = application as WaktuSholatApp
        repository = app.repository
        preferences = app.preferences

        rows = findViewById(R.id.rows)
        countdown = findViewById(R.id.countdown)
        progress = findViewById(R.id.progress)

        findViewById<View>(R.id.settings).setOnClickListener { open(SettingsActivity::class.java) }
        findViewById<View>(R.id.city_button).setOnClickListener { open(CityPickerActivity::class.java) }
        findViewById<View>(R.id.open_month).setOnClickListener { open(MonthActivity::class.java) }
        findViewById<View>(R.id.open_qibla).setOnClickListener { open(QiblaActivity::class.java) }
    }

    override fun onStart() {
        super.onStart()
        // Settings may have changed while stopped; force a full re-render.
        renderedDay = null
        handler.post(tick)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(tick)
    }

    private fun open(target: Class<out Activity>) = startActivity(Intent(this, target))

    private fun render(snapshot: ScheduleRepository.Snapshot) {
        val showImsak = preferences.showImsak
        if (snapshot.day !== renderedDay || showImsak != renderedImsak) {
            renderedDay = snapshot.day
            renderedImsak = showImsak
            renderedMinute = -1
            renderDay(snapshot.day, showImsak)
        }
        if (snapshot.minuteOfDay != renderedMinute) {
            renderedMinute = snapshot.minuteOfDay
            renderMinute(snapshot)
        }
        val seconds = ((snapshot.nextAtMillis - snapshot.nowMillis + 999) / 1000).toInt().coerceAtLeast(0)
        countdown.text = getString(R.string.countdown_clock, Countdown.clock(seconds))
    }

    /** Everything that is fixed for a day: header, tiles, and the row skeleton. */
    private fun renderDay(day: ScheduleRepository.Day, showImsak: Boolean) {
        val city = day.city
        findViewById<TextView>(R.id.city).text = city.name
        findViewById<TextView>(R.id.date).text = Dates.long(day.date)
        findViewById<TextView>(R.id.hijri).text = day.hijri?.let { getString(R.string.hijri_suffix, it.toString()) }.orEmpty()
        findViewById<TextView>(R.id.zone_note).text = getString(R.string.zone_note, city.label, city.zoneLabel)
        findViewById<TextView>(R.id.month_summary).text = Dates.monthYear(day.date.year, day.date.month)
        val qibla = Qibla.of(city.latitude, city.longitude)
        findViewById<TextView>(R.id.qibla_summary).text = getString(R.string.qibla_tile_summary, qibla.bearingText)

        rows.removeAllViews()
        rowViews.clear()
        val inflater = LayoutInflater.from(this)
        for (prayer in PrayerLabels.visible(showImsak)) {
            val view = inflater.inflate(R.layout.item_prayer_row, rows, false)
            val row = RowViews(prayer, view)
            row.name.text = PrayerLabels.of(this, prayer)
            row.time.text = PrayerTimes.format(day.times[prayer])
            val note = when (prayer) {
                Prayer.IMSAK -> getString(R.string.note_imsak)
                Prayer.SUNRISE -> getString(R.string.note_sunrise)
                else -> null
            }
            if (note != null) {
                row.note.text = note
                row.note.visibility = View.VISIBLE
            }
            rowViews += row
            rows.addView(view)
        }
    }

    /** Minute-level state: the next-prayer card and which row is active or past. */
    private fun renderMinute(snapshot: ScheduleRepository.Snapshot) {
        findViewById<TextView>(R.id.next_name).text = PrayerLabels.of(this, snapshot.next)
        findViewById<TextView>(R.id.next_time).text = PrayerTimes.format(snapshot.nextMinute)
        findViewById<TextView>(R.id.current).text = getString(
            R.string.current_since,
            PrayerLabels.of(this, snapshot.current),
            PrayerTimes.format(snapshot.times[snapshot.current]),
        )
        progress.progress = (snapshot.progress * 1000).toInt()

        val brand = getColor(R.color.brand)
        val primary = getColor(R.color.text_primary)
        val muted = getColor(R.color.text_tertiary)
        // Before Fajr the active window is last night's Isha, which is not on today's list.
        val active = if (snapshot.hasPassed(Prayer.FAJR)) snapshot.current else null
        for (row in rowViews) {
            val isActive = row.prayer == active
            val passed = !isActive && snapshot.hasPassed(row.prayer)
            val color = when {
                isActive -> brand
                passed -> muted
                else -> primary
            }
            row.name.setTextColor(color)
            row.time.setTextColor(color)
            row.name.typeface = if (isActive) medium else regular
            row.time.typeface = if (isActive) medium else regular
            row.dot.visibility = if (isActive) View.VISIBLE else View.INVISIBLE
            if (isActive) row.root.setBackgroundResource(R.drawable.row_active) else row.root.background = null
        }
    }

    private class RowViews(val prayer: Prayer, val root: View) {
        val name: TextView = root.findViewById(R.id.row_name)
        val time: TextView = root.findViewById(R.id.row_time)
        val note: TextView = root.findViewById(R.id.row_note)
        val dot: View = root.findViewById(R.id.row_dot)
    }
}
