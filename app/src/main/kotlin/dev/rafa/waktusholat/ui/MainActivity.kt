package dev.rafa.waktusholat.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import dev.rafa.waktusholat.R
import dev.rafa.waktusholat.WaktuSholatApp
import dev.rafa.waktusholat.core.LocalClock
import dev.rafa.waktusholat.core.Prayer
import dev.rafa.waktusholat.core.PrayerTimes
import dev.rafa.waktusholat.core.Qibla
import dev.rafa.waktusholat.data.ScheduleRepository
import dev.rafa.waktusholat.widget.PrayerWidgetProvider

/**
 * Today's schedule and the live countdown.
 *
 * The countdown ticks once a second from a [Handler] rather than a wall-clock broadcast: it stays
 * smooth while the screen is visible and costs nothing after [onStop]. Rows are rebuilt only when
 * the minute changes, and the per-second tick touches nothing but the countdown text.
 *
 * Colours are read from the active theme with [themeColor] rather than from `R.color`, so a device
 * theme change needs no code path here and the list stays legible in both palettes.
 */
class MainActivity : Activity() {

    private lateinit var repository: ScheduleRepository
    private lateinit var rows: LinearLayout
    private lateinit var cityLabel: TextView
    private lateinit var hijriLabel: TextView
    private lateinit var nextName: TextView
    private lateinit var nextTime: TextView
    private lateinit var countdown: TextView
    private lateinit var progress: ProgressBar
    private lateinit var zoneNote: TextView

    private val handler = Handler(Looper.getMainLooper())

    /** Today's schedule, cached so the 1 Hz tick never recalculates. */
    private var times: PrayerTimes = PrayerTimes.EMPTY

    /** Fajr of the following day, needed once Isha has begun. */
    private var tomorrowFajr: Int = 0

    private var renderedDay: Int = Int.MIN_VALUE
    private var renderedMinute: Int = Int.MIN_VALUE

    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val zone = repository.city.timeZoneHours
            val day = LocalClock.dateAt(now, zone).epochDay
            val minute = LocalClock.minuteOfDay(now, zone)

            if (day != renderedDay) {
                renderedDay = day
                renderedMinute = minute
                renderDay(now)
            } else if (minute != renderedMinute) {
                renderedMinute = minute
                buildRows(minute)
            }
            bindCountdown(now, minute)

            // Align the next tick to the top of the second so the digits never appear to stall.
            handler.postDelayed(this, 1000L - now % 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = ScheduleRepository((application as WaktuSholatApp).preferences)

        rows = findViewById(R.id.rows)
        cityLabel = findViewById(R.id.city)
        hijriLabel = findViewById(R.id.hijri)
        nextName = findViewById(R.id.next_name)
        nextTime = findViewById(R.id.next_time)
        countdown = findViewById(R.id.countdown)
        progress = findViewById(R.id.progress)
        zoneNote = findViewById(R.id.zone_note)

        findViewById<View>(R.id.settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.open_month).setOnClickListener {
            startActivity(Intent(this, MonthActivity::class.java))
        }
        findViewById<View>(R.id.open_qibla).setOnClickListener {
            startActivity(Intent(this, QiblaActivity::class.java))
        }
        findViewById<View>(R.id.refresh).setOnClickListener {
            renderedDay = Int.MIN_VALUE
            renderedMinute = Int.MIN_VALUE
            tick.run()
            PrayerWidgetProvider.requestRefresh(this)
        }
    }

    override fun onStart() {
        super.onStart()
        renderedDay = Int.MIN_VALUE
        renderedMinute = Int.MIN_VALUE
        handler.post(tick)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(tick)
    }

    /** Rebuild everything for the current day. Runs on day change and on first start. */
    private fun renderDay(now: Long) {
        val city = repository.city
        cityLabel.text = city.name
        hijriLabel.text = repository.hijri()?.toString().orEmpty()
        zoneNote.text = getString(R.string.zone_note, city.label, city.zoneLabel)

        val qibla = Qibla.of(city.latitude, city.longitude)
        findViewById<TextView>(R.id.qibla_bearing).text = qibla.bearingText
        findViewById<TextView>(R.id.qibla_summary).text =
            getString(R.string.qibla_summary, qibla.bearingText, qibla.distanceText)

        val date = repository.today(now)
        times = repository.timesFor(city, date)
        tomorrowFajr = repository.timesFor(city, date.plusDays(1))[Prayer.FAJR]
        buildRows(LocalClock.minuteOfDay(now, city.timeZoneHours))
    }

    /**
     * Rebuilds the daily list. The row in effect is marked with the accent colour and a soft tinted
     * background, which is the same treatment the widget uses, so the two surfaces read alike.
     */
    private fun buildRows(minuteOfDay: Int) {
        rows.removeAllViews()
        val showImsak = (application as WaktuSholatApp).preferences.showImsak
        val current = times.currentAt(minuteOfDay)
        val inflater = LayoutInflater.from(this)
        val accent = themeColor(android.R.attr.colorAccent)
        val primary = themeColor(android.R.attr.textColorPrimary)
        val secondary = themeColor(android.R.attr.textColorSecondary)
        val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val regular = Typeface.create("sans-serif", Typeface.NORMAL)

        for (prayer in PrayerLabels.visible(Prayer.DAILY, showImsak)) {
            val row = inflater.inflate(R.layout.item_prayer_row, rows, false)
            val name = row.findViewById<TextView>(R.id.row_name)
            val time = row.findViewById<TextView>(R.id.row_time)
            val note = row.findViewById<TextView>(R.id.row_note)

            val active = prayer == current
            name.text = PrayerLabels.of(this, prayer)
            time.text = PrayerTimes.format(times[prayer])
            if (active) {
                name.setTextColor(accent)
                time.setTextColor(accent)
                name.typeface = medium
                time.typeface = medium
                row.setBackgroundResource(R.drawable.row_active)
            } else {
                name.setTextColor(if (prayer == Prayer.IMSAK) secondary else primary)
                time.setTextColor(if (prayer == Prayer.IMSAK) secondary else primary)
                name.typeface = regular
                time.typeface = regular
            }

            val hint = hintFor(prayer)
            note.text = hint
            note.visibility = if (hint.isEmpty()) View.GONE else View.VISIBLE

            rows.addView(row)
        }
    }

    /** The countdown and progress bar; the only work the per-second tick does. */
    private fun bindCountdown(now: Long, minuteOfDay: Int) {
        val next = times.nextFrom(minuteOfDay)
        val target = next ?: Prayer.FAJR
        val targetMinute = if (next != null) times[next] else tomorrowFajr
        val remaining = if (next != null) targetMinute - minuteOfDay else 1440 - minuteOfDay + targetMinute
        val current = times.currentAt(minuteOfDay)
        val from = if (current != null) times[current] else times[Prayer.ISHA] - 1440

        nextName.text = PrayerLabels.of(this, target)
        nextTime.text = PrayerTimes.format(targetMinute)

        val seconds = ((now / 1000L) % 60L).toInt()
        val hours = remaining / 60
        val minutes = remaining % 60
        countdown.text = if (hours > 0) {
            getString(R.string.countdown_hours, hours, minutes, seconds)
        } else {
            getString(R.string.countdown_minutes, minutes, seconds)
        }

        val span = targetMinute - from
        progress.progress = if (span <= 0) {
            1000
        } else {
            (((span - remaining).toFloat() / span) * 1000f).toInt().coerceIn(0, 1000)
        }
    }

    /** Sunrise and Imsak are not prayers, so they carry a clarifying note. */
    private fun hintFor(prayer: Prayer): String = when (prayer) {
        Prayer.IMSAK -> getString(R.string.note_imsak)
        Prayer.SUNRISE -> getString(R.string.note_sunrise)
        else -> ""
    }

    /** Resolves an attribute of the active theme, so both palettes come from one code path. */
    private fun themeColor(attribute: Int): Int {
        val value = TypedValue()
        theme.resolveAttribute(attribute, value, true)
        return if (value.resourceId != 0) getColor(value.resourceId) else value.data
    }
}
