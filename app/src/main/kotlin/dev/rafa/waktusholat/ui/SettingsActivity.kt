package dev.rafa.waktusholat.ui

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import dev.rafa.waktusholat.R
import dev.rafa.waktusholat.WaktuSholatApp
import dev.rafa.waktusholat.core.CalculationMethod
import dev.rafa.waktusholat.core.Madhab
import dev.rafa.waktusholat.data.Preferences
import dev.rafa.waktusholat.notify.Notifications

/**
 * All settings as grouped rows. Choices with several options open a single-choice dialog; toggles
 * are rows with a switch. Every mutation writes through [Preferences] and then re-arms the alarms
 * and repaints the widgets, so the three surfaces cannot drift apart.
 */
class SettingsActivity : Activity() {

    private lateinit var app: WaktuSholatApp
    private val preferences: Preferences get() = app.preferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setupTopBar(getString(R.string.settings_title))
        app = application as WaktuSholatApp

        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
        findViewById<TextView>(R.id.about).text = getString(R.string.settings_about_text, version.orEmpty())
    }

    override fun onResume() {
        super.onResume()
        // A location or permission change made elsewhere has to be reflected on return.
        render()
    }

    private fun render() {
        val location = findViewById<LinearLayout>(R.id.group_location)
        val calculation = findViewById<LinearLayout>(R.id.group_calculation)
        val general = findViewById<LinearLayout>(R.id.group_general)
        location.removeAllViews()
        calculation.removeAllViews()
        general.removeAllViews()

        val city = preferences.resolveCity()
        addValueRow(
            location,
            title = getString(R.string.settings_city),
            summary = when {
                preferences.deviceLocationOutOfRange -> getString(R.string.location_outside_indonesia, city.label)
                preferences.useGps -> getString(R.string.city_gps_active, city.label)
                else -> "${city.label} · ${city.zoneLabel}"
            },
        ) { startActivity(Intent(this, CityPickerActivity::class.java)) }

        addValueRow(
            calculation,
            title = getString(R.string.settings_method),
            summary = preferences.method.region,
            value = preferences.method.label,
        ) {
            choose(R.string.settings_method, CalculationMethod.entries.map { it.label to it.id }, preferences.methodId) {
                preferences.methodId = it
            }
        }
        addValueRow(
            calculation,
            title = getString(R.string.settings_madhab),
            summary = getString(R.string.settings_madhab_summary),
            value = preferences.madhab.label,
            divider = true,
        ) {
            choose(R.string.settings_madhab, Madhab.entries.map { it.label to it.ordinal }, preferences.madhabId) {
                preferences.madhabId = it
            }
        }

        val notifying = preferences.notificationsEnabled
        addToggleRow(
            general,
            title = getString(R.string.settings_notifications),
            summary = getString(R.string.settings_notifications_summary),
            checked = notifying,
        ) { enabled ->
            preferences.notificationsEnabled = enabled
            if (enabled && !Notifications.canPostNotifications(this) && Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            }
            app.notifyScheduleChanged()
        }
        if (notifying) {
            addValueRow(
                general,
                title = getString(R.string.settings_reminder_lead),
                summary = null,
                value = getString(leadLabel(preferences.reminderLeadMinutes)),
                divider = true,
            ) {
                choose(
                    R.string.settings_reminder_lead,
                    LEADS.map { getString(leadLabel(it)) to it },
                    preferences.reminderLeadMinutes,
                ) { preferences.reminderLeadMinutes = it }
            }
            if (!canScheduleExactAlarms()) {
                addValueRow(
                    general,
                    title = getString(R.string.settings_exact_alarm),
                    summary = getString(R.string.settings_exact_alarm_summary),
                    divider = true,
                ) { openExactAlarmSettings() }
            }
        }
        addToggleRow(
            general,
            title = getString(R.string.settings_show_imsak),
            checked = preferences.showImsak,
            divider = true,
        ) { enabled ->
            preferences.showImsak = enabled
            app.notifyScheduleChanged()
        }
    }

    private fun addValueRow(
        parent: LinearLayout,
        title: String,
        summary: String?,
        value: String? = null,
        divider: Boolean = false,
        onClick: () -> Unit,
    ) {
        val row = inflateRow(parent, title, summary, divider)
        row.findViewById<TextView>(R.id.setting_value).apply {
            text = value.orEmpty()
            visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        row.findViewById<View>(R.id.setting_chevron).visibility = View.VISIBLE
        row.setOnClickListener { onClick() }
    }

    /** The whole row toggles, matching the platform's own switch rows. */
    private fun addToggleRow(
        parent: LinearLayout,
        title: String,
        summary: String? = null,
        checked: Boolean,
        divider: Boolean = false,
        onChanged: (Boolean) -> Unit,
    ) {
        val row = inflateRow(parent, title, summary, divider)
        val toggle = Switch(this).apply {
            isChecked = checked
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        row.addView(toggle)
        row.contentDescription = title
        row.setOnClickListener {
            toggle.isChecked = !toggle.isChecked
            onChanged(toggle.isChecked)
            render()
        }
    }

    private fun inflateRow(parent: LinearLayout, title: String, summary: String?, divider: Boolean): LinearLayout {
        if (divider) {
            val density = resources.displayMetrics.density
            parent.addView(
                View(this).apply { setBackgroundColor(getColor(R.color.outline)) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                    marginStart = (16 * density).toInt()
                    marginEnd = (16 * density).toInt()
                },
            )
        }
        val row = LayoutInflater.from(this).inflate(R.layout.item_setting_row, parent, false) as LinearLayout
        row.findViewById<TextView>(R.id.setting_title).text = title
        row.findViewById<TextView>(R.id.setting_summary).apply {
            text = summary.orEmpty()
            visibility = if (summary.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        parent.addView(row)
        return row
    }

    /** Single-choice dialog; the label/value pairs pre-select the current entry and map back. */
    private fun choose(title: Int, options: List<Pair<String, Int>>, selected: Int, onChosen: (Int) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(
                options.map { it.first }.toTypedArray(),
                options.indexOfFirst { it.second == selected }.coerceAtLeast(0),
            ) { dialog, which ->
                dialog.dismiss()
                onChosen(options[which].second)
                app.notifyScheduleChanged()
                render()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() != false

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) render()
    }

    private fun leadLabel(minutes: Int): Int = when (minutes) {
        5 -> R.string.lead_5
        10 -> R.string.lead_10
        15 -> R.string.lead_15
        else -> R.string.lead_none
    }

    private companion object {
        const val REQUEST_NOTIFICATIONS = 42
        val LEADS = listOf(0, 5, 10, 15)
    }
}
