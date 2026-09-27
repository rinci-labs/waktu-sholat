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
class SettingsActivity : BaseActivity() {

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
            summary = if (preferences.useGps) {
                getString(R.string.city_gps_active, city.label, city.zoneLabel)
            } else {
                "${city.label} · ${city.zoneLabel}"
            },
        ) { startActivity(Intent(this, CityPickerActivity::class.java)) }

        addValueRow(
            calculation,
            title = getString(R.string.settings_method),
            summary = preferences.method.regionLabel(this),
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
            // Everything that silently stops an adhan from appearing, each with its fix one tap away.
            when (Notifications.status(this)) {
                Notifications.Status.NO_PERMISSION -> addWarningRow(
                    general,
                    getString(R.string.settings_notify_permission),
                    getString(R.string.settings_notify_permission_summary),
                ) {
                    // The system dialog when it can still appear; the result handler falls back
                    // to the app's notification settings once the user has denied it for good.
                    if (Build.VERSION.SDK_INT >= 33) {
                        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
                    } else {
                        openNotificationSettings()
                    }
                }
                Notifications.Status.BLOCKED -> addWarningRow(
                    general,
                    getString(R.string.settings_notify_blocked),
                    getString(R.string.settings_notify_blocked_summary),
                ) { openNotificationSettings() }
                Notifications.Status.OK -> Unit
            }
            if (!canScheduleExactAlarms()) {
                addWarningRow(
                    general,
                    getString(R.string.settings_exact_alarm),
                    getString(R.string.settings_exact_alarm_summary),
                ) { openExactAlarmSettings() }
            }
            if (!ignoresBatteryOptimizations()) {
                addWarningRow(
                    general,
                    getString(R.string.settings_battery),
                    getString(R.string.settings_battery_summary),
                ) { requestBatteryExemption() }
            }
            addValueRow(
                general,
                title = getString(R.string.settings_notify_test),
                summary = getString(R.string.settings_notify_test_summary),
                divider = true,
            ) {
                Notifications.postTest(this)
                if (Notifications.status(this) != Notifications.Status.OK) render()
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
        addValueRow(
            general,
            title = getString(R.string.settings_language),
            summary = null,
            value = getString(Language.current(this).label),
            divider = true,
        ) {
            val languages = AppLanguage.entries
            choose(
                R.string.settings_language,
                languages.map { getString(it.label) to it.ordinal },
                Language.current(this).ordinal,
            ) { chosen ->
                Language.set(this, languages[chosen])
                app.notifyScheduleChanged()
            }
        }
        addValueRow(
            general,
            title = getString(R.string.settings_icon),
            summary = null,
            value = if (IconAuto.isEnabled(this)) getString(R.string.icon_auto) else getString(IconTheme.current(this).label),
            divider = true,
        ) { chooseIcon() }
    }

    /** Icon theme picker: each row previews the theme's icon beside its name. */
    private fun chooseIcon() {
        val themes = IconTheme.entries
        // Row 0 is "Automatic"; rows 1.. are the fixed themes.
        val current = if (IconAuto.isEnabled(this)) 0 else themes.indexOf(IconTheme.current(this)) + 1
        val autoPreview = IconTheme.valueOf(Period.of(app.repository.snapshot()).name)
        val size = (36 * resources.displayMetrics.density).toInt()
        val gap = (16 * resources.displayMetrics.density).toInt()
        val adapter = object : android.widget.ArrayAdapter<String>(
            this,
            android.R.layout.select_dialog_singlechoice,
            listOf(getString(R.string.icon_auto_long)) + themes.map { getString(it.label) },
        ) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                val icon = android.graphics.drawable.LayerDrawable(
                    arrayOf(
                        getDrawable(if (position == 0) autoPreview.preview else themes[position - 1].preview),
                        getDrawable(R.drawable.ic_launcher_foreground),
                    ),
                )
                icon.setBounds(0, 0, size, size)
                view.setCompoundDrawablesRelativeWithIntrinsicBounds(ClipOval(icon, size), null, null, null)
                view.compoundDrawablePadding = gap
                return view
            }
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_icon)
            .setSingleChoiceItems(adapter, current) { dialog, which ->
                dialog.dismiss()
                if (which == 0) {
                    IconAuto.setEnabled(this, true)
                } else {
                    IconAuto.setEnabled(this, false)
                    IconTheme.apply(this, themes[which - 1])
                }
                render()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    /** Draws [content] clipped to a circle, like a launcher's icon mask. */
    private class ClipOval(
        private val content: android.graphics.drawable.Drawable,
        private val size: Int,
    ) : android.graphics.drawable.Drawable() {
        private val path = android.graphics.Path()

        override fun onBoundsChange(bounds: android.graphics.Rect) {
            content.bounds = bounds
            path.reset()
            path.addOval(android.graphics.RectF(bounds), android.graphics.Path.Direction.CW)
        }

        override fun draw(canvas: android.graphics.Canvas) {
            val save = canvas.save()
            canvas.clipPath(path)
            content.draw(canvas)
            canvas.restoreToCount(save)
        }

        override fun getIntrinsicWidth() = size
        override fun getIntrinsicHeight() = size
        override fun setAlpha(alpha: Int) = content.setAlpha(alpha)
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { content.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
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

    /** A value row styled as a warning, for settings that stop notifications from appearing. */
    private fun addWarningRow(parent: LinearLayout, title: String, summary: String, onClick: () -> Unit) {
        addValueRow(parent, title = title, summary = summary, divider = true, onClick = onClick)
        val row = parent.getChildAt(parent.childCount - 1)
        row.findViewById<TextView>(R.id.setting_title).setTextColor(getColor(R.color.warning_text))
    }

    private fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        }
        runCatching { startActivity(intent) }
    }

    private fun ignoresBatteryOptimizations(): Boolean =
        getSystemService(android.os.PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) != false

    /**
     * Asks to be exempt from battery optimisation. Aggressive vendor battery managers (ColorOS, MIUI,
     * One UI...) otherwise stop the app's alarms, which is the most common reason an adhan is missed.
     */
    @android.annotation.SuppressLint("BatteryLife")
    private fun requestBatteryExemption() {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        runCatching { startActivity(direct) }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
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
        if (requestCode == REQUEST_NOTIFICATIONS) {
            val denied = grantResults.firstOrNull() != android.content.pm.PackageManager.PERMISSION_GRANTED
            if (denied && Build.VERSION.SDK_INT >= 33 &&
                !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
            ) {
                openNotificationSettings()
            }
            render()
        }
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
