package dev.rafa.waktusholat.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.rafa.waktusholat.WaktuSholatApp
import dev.rafa.waktusholat.core.Prayer

/**
 * Fired by [AlarmScheduler] at a prayer's instant. Work is a notification post plus a re-arm, both
 * cheap and synchronous, so no worker or service is needed.
 */
class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra(AlarmScheduler.EXTRA_PRAYER)
        val prayer = name?.let { value ->
            Prayer.entries.firstOrNull { it.name == value }
        } ?: return

        val minute = intent.getIntExtra(AlarmScheduler.EXTRA_MINUTE, -1)
        if (minute !in 0 until 1440) return

        val reminder = intent.getBooleanExtra(AlarmScheduler.EXTRA_REMINDER, false)
        val lead = if (reminder) WaktuSholatApp.instance.preferences.reminderLeadMinutes else 0

        Notifications.post(context, prayer, minute, reminder, lead)

        // Re-arm the whole window so tomorrow's occurrence is in place even if the process has not
        // been started since, which is the case when the phone has been asleep overnight.
        AlarmScheduler.reschedule(context)
    }
}
