package dev.rafa.waktusholat.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.rafa.waktusholat.WaktuSholatApp
import dev.rafa.waktusholat.ui.IconAuto
import dev.rafa.waktusholat.widget.WidgetUpdater

/**
 * Re-arms the schedule after events that invalidate it: a reboot wipes every pending alarm, an
 * update can, and a clock or time-zone change moves the absolute instants the alarms were computed
 * from.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
                    WaktuSholatApp.instance.preferences.updateDeviceZone(java.util.TimeZone.getDefault().id)
                }
                AlarmScheduler.reschedule(context)
                // The widget's "next prayer" is stale after these events too, but its refresh is
                // purely cosmetic: never let it take the alarm re-arm down with it.
                runCatching { WidgetUpdater.updateAll(context) }
                runCatching { IconAuto.sync(context) }
            }
        }
    }
}
