package dev.rafa.waktusholat.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import dev.rafa.waktusholat.R
import dev.rafa.waktusholat.WaktuSholatApp
import dev.rafa.waktusholat.core.CalculationMethod
import dev.rafa.waktusholat.core.City
import dev.rafa.waktusholat.core.Madhab
import dev.rafa.waktusholat.data.Preferences
import dev.rafa.waktusholat.notify.AlarmScheduler
import dev.rafa.waktusholat.notify.Notifications
import dev.rafa.waktusholat.widget.PrayerWidgetProvider

/**
 * All settings, as a list of rows grouped into cards.
 *
 * Choices that have many options (calculation method, reminder lead time) open a single-choice
 * dialog instead of rendering every option inline, which is the platform's own pattern and keeps the
 * screen a readable list. Toggles stay as rows with a checkbox so their state is visible without a tap.
 *
 * Every mutation writes through to [Preferences] and then re-applies the alarms and repaints the
 * widget, so the three surfaces cannot drift apart.
 */
class SettingsActivity : Activity() {

    private lateinit var preferences: Preferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setTitle(R.string.settings_title)
        preferences = (application as WaktuSholatApp).preferences
        render()
    }

    override fun onResume() {
        super.onResume()
        // A location or permission change made elsewhere has to be reflected on return.
        if (::preferences.isInitialized) render()
    }

    /** Rebuilds every group from the current preferences. */
    private fun render() {
        val location = findViewById<LinearLayout>(R.id.group_location)
        val calculation = findViewById<LinearLayout>(R.id.group_calculation)
        val general = findViewById<LinearLayout>(R.id.group_general)
        location.removeAllViews()
        calculation.removeAllViews()
        general.removeAllViews()

        val city = preferences.resolveCity()
        val outOfRange = preferences.deviceLocationOutOfRange

        // --- Location -------------------------------------------------------------------------
        addValueRow(
            location,
            title = getString(R.string.settings_location),
            summary = if (outOfRange) {
                getString(R.string.location_outside_indonesia, city.label)
            } else {
                "${city.label} · ${city.zoneLabel}"
            },
            value = null,
        ) {
            startActivity(Intent(this, CityPickerActivity::class.java))
        }
        addToggleRow(
            location,
            title = getString(R.string.settings_use_gps),
            checked = preferences.useGps && !outOfRange,
            showDivider = true,
        ) { enabled ->
            preferences.useGps = enabled
            if (enabled) requestDeviceLocation() else preferences.setCity(preferences.resolveCity())
            onSettingsChanged()
        }

        // --- Calculation ----------------------------------------------------------------------
        addValueRow(
            calculation,
            title = getString(R.string.settings_method),
            summary = preferences.method.region,
            value = preferences.method.label,
        ) {
            chooseFrom(
                getString(R.string.settings_method),
                CalculationMethod.entries.map { it.label to it.id },
                preferences.methodId,
            ) { chosen ->
                preferences.methodId = chosen
                onSettingsChanged()
            }
        }
        addValueRow(
            calculation,
            title = getString(R.string.settings_madhab),
            summary = getString(R.string.settings_madhab_summary),
            value = preferences.madhab.label,
            showDivider = true,
        ) {
            chooseFrom(
                getString(R.string.settings_madhab),
                Madhab.entries.map { it.label to it.ordinal },
                preferences.madhabId,
            ) { chosen ->
                preferences.madhabId = chosen
                onSettingsChanged()
            }
        }

        // --- General --------------------------------------------------------------------------
        addToggleRow(
            general,
            title = getString(R.string.settings_notifications),
            summary = getString(R.string.settings_notifications_summary),
            checked = preferences.notificationsEnabled,
        ) { enabled ->
            preferences.notificationsEnabled = enabled
            if (enabled && !Notifications.canPostNotifications(this)) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATIONS,
                )
            }
            onSettingsChanged()
        }
        // Only meaningful once notifications are on, so it appears with them.
        if (preferences.notificationsEnabled) {
            addValueRow(
                general,
                title = getString(R.string.settings_reminder_lead),
                summary = null,
                value = leadLabel(preferences.reminderLeadMinutes),
                showDivider = true,
            ) {
                chooseFrom(
                    getString(R.string.settings_reminder_lead),
                    leadOptions(),
                    preferences.reminderLeadMinutes,
                ) { chosen ->
                    preferences.reminderLeadMinutes = chosen
                    onSettingsChanged()
                }
            }
        }
        addToggleRow(
            general,
            title = getString(R.string.settings_show_imsak),
            checked = preferences.showImsak,
            showDivider = true,
        ) { enabled ->
            preferences.showImsak = enabled
            onSettingsChanged()
        }
    }

    /**
     * A row that opens something: title, optional summary, the current value on the right.
     */
    private fun addValueRow(
        parent: LinearLayout,
        title: String,
        summary: String?,
        value: String?,
        showDivider: Boolean = false,
        onClick: () -> Unit,
    ) {
        if (showDivider) addDivider(parent)
        val row = inflateRow(parent)
        row.findViewById<TextView>(R.id.setting_title).text = title
        row.findViewById<TextView>(R.id.setting_summary).apply {
            text = summary.orEmpty()
            visibility = if (summary.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        row.findViewById<TextView>(R.id.setting_value).apply {
            text = value.orEmpty()
            visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        row.setOnClickListener { onClick() }
        parent.addView(row)
    }

    /**
     * A row whose whole surface toggles a boolean, with a checkbox as the state indicator. Tapping
     * the row rather than only the box matches how the platform's own switch rows behave.
     */
    private fun addToggleRow(
        parent: LinearLayout,
        title: String,
        summary: String? = null,
        checked: Boolean,
        showDivider: Boolean = false,
        onChanged: (Boolean) -> Unit,
    ) {
        if (showDivider) addDivider(parent)
        val row = inflateRow(parent)
        row.findViewById<TextView>(R.id.setting_title).text = title
        row.findViewById<TextView>(R.id.setting_summary).apply {
            text = summary.orEmpty()
            visibility = if (summary.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        val box = CheckBox(this).apply {
            isChecked = checked
            isClickable = false
            isFocusable = false
            contentDescription = title
        }
        (row as LinearLayout).addView(box)
        row.setOnClickListener {
            val next = !box.isChecked
            box.isChecked = next
            onChanged(next)
            render()
        }
        parent.addView(row)
    }

    private fun inflateRow(parent: LinearLayout) =
        LayoutInflater.from(this).inflate(R.layout.item_setting_row, parent, false) as LinearLayout

    /** Hairline between rows inside one card, inset to match the row padding. */
    private fun addDivider(parent: LinearLayout) {
        val density = resources.displayMetrics.density
        parent.addView(
            View(this).apply { setBackgroundColor(getColor(R.color.outline)) },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (0.7f * density).toInt() + 1,
            ).apply {
                marginStart = (16 * density).toInt()
            },
        )
    }

    /**
     * Single-choice dialog for a setting with several options. The label/value pair is used both to
     * pre-select the current entry and to map the chosen index back.
     */
    private fun chooseFrom(
        title: String,
        options: List<Pair<String, Int>>,
        selectedValue: Int,
        onChosen: (Int) -> Unit,
    ) {
        val labels = options.map { it.first }.toTypedArray()
        val checked = options.indexOfFirst { it.second == selectedValue }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                dialog.dismiss()
                onChosen(options[which].second)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    /** Coarse location is plenty: the nearest built-in city is chosen from the fix. */
    @Suppress("DEPRECATION")
    private fun requestDeviceLocation() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION),
                REQUEST_LOCATION,
            )
            return
        }
        applyLastKnownLocation()
    }

    @Suppress("DEPRECATION")
    private fun applyLastKnownLocation() {
        val manager = getSystemService(LOCATION_SERVICE) as? android.location.LocationManager ?: return
        // Checked here as well as at the entry point: the permission can be revoked while the app is
        // backgrounded, and getLastKnownLocation throws rather than returning null in that case.
        val granted = checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        val location = if (granted) {
            manager.getProviders(true)
                .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
        } else {
            null
        }
        if (location == null) {
            preferences.useGps = false
            render()
            return
        }
        // A fix the app cannot serve is refused rather than adopted: the nearest built-in city would
        // be thousands of kilometres away and its wall-clock zone meaningless.
        if (!City.isServiceable(location.latitude, location.longitude)) {
            preferences.useGps = false
            render()
            return
        }
        preferences.setCoordinates(location.latitude, location.longitude)
        render()
        onSettingsChanged()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) {
            render()
            return
        }
        if (requestCode != REQUEST_LOCATION) return
        if (grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            applyLastKnownLocation()
        } else {
            preferences.useGps = false
            render()
        }
    }

    /** A change in location, method or madhab invalidates every scheduled alarm. */
    private fun onSettingsChanged() {
        AlarmScheduler.reschedule(this)
        PrayerWidgetProvider.requestRefresh(this)
    }

    private fun leadLabel(minutes: Int): String = getString(
        when (minutes) {
            5 -> R.string.lead_5
            10 -> R.string.lead_10
            15 -> R.string.lead_15
            else -> R.string.lead_none
        },
    )

    private fun leadOptions(): List<Pair<String, Int>> = listOf(
        getString(R.string.lead_none) to 0,
        getString(R.string.lead_5) to 5,
        getString(R.string.lead_10) to 10,
        getString(R.string.lead_15) to 15,
    )

    private companion object {
        const val REQUEST_LOCATION = 41
        const val REQUEST_NOTIFICATIONS = 42
    }
}
