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
 * Everything on screen changes at most once a minute (the countdown is relative, "7 jam 37 menit
 * lagi"), so a [Handler] ticks on minute boundaries only while the screen is visible. Rows are
 * inflated once per day and merely restyled on each tick.
 *
 * The sky behind the header follows the part of the day ([Period]) and runs up behind the status
 * bar, so this screen applies the system-bar insets itself.
 */
class MainActivity : BaseActivity() {

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

    private lateinit var sky: SkyView
    private lateinit var scrim: View

    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            render(repository.snapshot(now))
            // Just past the next minute boundary, which is when the text next changes.
            handler.postDelayed(this, MINUTE - now % MINUTE + 50L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val app = application as WaktuSholatApp
        repository = app.repository
        preferences = app.preferences

        rows = findViewById(R.id.rows)
        sky = findViewById(R.id.sky)
        scrim = findViewById(R.id.status_scrim)
        applyInsets()
        countdown = findViewById(R.id.countdown)
        progress = findViewById(R.id.progress)

        findViewById<View>(R.id.settings).setOnClickListener { open(SettingsActivity::class.java) }
        findViewById<View>(R.id.city_button).setOnClickListener { open(CityPickerActivity::class.java) }
        findViewById<View>(R.id.open_month).setOnClickListener { open(MonthActivity::class.java) }
        findViewById<View>(R.id.open_qibla).setOnClickListener { open(QiblaActivity::class.java) }
    }

    override fun onStart() {
        super.onStart()
        (application as WaktuSholatApp).refreshFixPassively()
        dev.rafa.waktusholat.update.Updater.autoCheck(this)
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
    }

    /**
     * Pads the content below the status bar and above the navigation bar, sizes the status-bar scrim,
     * and lets the sky start at the very top. The sky then extends to just below the next-prayer card.
     */
    private fun applyInsets() {
        val content = findViewById<View>(R.id.content)
        val hero = findViewById<View>(R.id.hero)
        val sheet = findViewById<View>(R.id.sheet)
        val baseTop = content.paddingTop
        val baseBottom = sheet.paddingBottom
        @Suppress("DEPRECATION")
        findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { _, insets ->
            content.setPadding(content.paddingLeft, baseTop + insets.systemWindowInsetTop, content.paddingRight, content.paddingBottom)
            sheet.setPadding(sheet.paddingLeft, sheet.paddingTop, sheet.paddingRight, baseBottom + insets.systemWindowInsetBottom)
            scrim.layoutParams = scrim.layoutParams.apply { height = insets.systemWindowInsetTop }
            insets
        }
        // The scrim only matters once content scrolls under the status bar; at rest the sky itself
        // shows there, so fade the scrim in over the first status-bar height of scrolling.
        scrim.alpha = 0f
        findViewById<View>(R.id.scroll).setOnScrollChangeListener { _, _, y, _, _ ->
            scrim.alpha = if (scrim.height == 0) 0f else (y.toFloat() / scrim.height).coerceIn(0f, 1f)
            sky.parallax = y.toFloat()
        }
        // The skyline stands on the card's top edge; the ground runs on behind the card and ends
        // under the sheet's rounded corners.
        hero.addOnLayoutChangeListener { _, _, top, _, _, _, _, _, _ ->
            sky.horizon = top + resources.displayMetrics.density
        }
        sheet.addOnLayoutChangeListener { _, _, top, _, _, _, _, _, _ ->
            val height = top + (SHEET_OVERLAP_DP * resources.displayMetrics.density).toInt()
            if (sky.layoutParams.height != height) sky.layoutParams = sky.layoutParams.apply { this.height = height }
        }
        // White status-bar icons over the sky, whatever the theme.
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
    }

    /** Everything that is fixed for a day: header, tiles, and the row skeleton. */
    private fun renderDay(day: ScheduleRepository.Day, showImsak: Boolean) {
        val city = day.city
        findViewById<TextView>(R.id.city).text = city.name
        findViewById<TextView>(R.id.date).text = Dates.long(this, day.date)
        findViewById<TextView>(R.id.hijri).text = day.hijri?.let { getString(R.string.hijri_suffix, Dates.hijri(this, it)) }.orEmpty()
        findViewById<TextView>(R.id.zone_note).text = getString(R.string.zone_note, city.label, city.zoneLabel)
        findViewById<TextView>(R.id.month_summary).text = Dates.monthYear(this, day.date.year, day.date.month)
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
        val period = Period.of(snapshot)
        sky.show(period, Celestial.of(snapshot))
        scrim.setBackgroundColor(period.top)
        countdown.text = getString(R.string.countdown_in, Relative.long(this, snapshot.minutesRemaining))
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
        val muted = getColor(R.color.text_secondary)
        // The list lights the same prayer the card above counts down to. After Isha that is
        // tomorrow's Fajr, which is not on today's list, so nothing is lit.
        val active = snapshot.next.takeUnless { snapshot.hasPassed(it) }
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
            if (isActive) row.root.setBackgroundResource(R.drawable.row_active) else row.root.background = null
        }
    }

    private companion object {
        const val MINUTE = 60_000L

        /** How far the sky runs under the sheet, enough to fill behind its rounded corners. */
        const val SHEET_OVERLAP_DP = 40
    }

    private class RowViews(val prayer: Prayer, val root: View) {
        val name: TextView = root.findViewById(R.id.row_name)
        val time: TextView = root.findViewById(R.id.row_time)
        val note: TextView = root.findViewById(R.id.row_note)
    }
}
