package dev.rafa.waktusholat.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.rafa.waktusholat.R
import dev.rafa.waktusholat.WaktuSholatApp
import dev.rafa.waktusholat.core.Prayer
import dev.rafa.waktusholat.core.PrayerTimes
import dev.rafa.waktusholat.ui.Language
import dev.rafa.waktusholat.ui.MainActivity
import dev.rafa.waktusholat.ui.PrayerLabels
import dev.rafa.waktusholat.ui.Relative

/**
 * Posts the adhan notification. The framework builder is used directly: there is no support library
 * in this project, and everything needed here (channel, category, visibility) exists since API 26
 * with a workable API 24 fallback.
 */
object Notifications {

    const val CHANNEL = "prayer"

    /**
     * The notification's own id is the prayer ordinal, so a re-post (a reminder followed by the
     * adhan, or a replay after a time change) replaces the previous one instead of stacking.
     */
    private const val REQUEST_CONTENT = 0

    /** Creates the adhan channel once. Below API 26 there are no channels at all. */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        val channel = NotificationChannel(
            CHANNEL,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Whether a notification is allowed to be posted. On Android 13+ this is a runtime permission,
     * so a user who has not granted it gets nothing even though the alarms fire; below 13 it is
     * always allowed.
     */
    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /** Why a notification would not appear, most fixable first. */
    enum class Status { OK, NO_PERMISSION, BLOCKED }

    /**
     * Whether posted notifications will actually show: the Android 13 runtime permission, the app's
     * notification switch in system settings, and the adhan channel's own switch.
     */
    fun status(context: Context): Status {
        if (!canPostNotifications(context)) return Status.NO_PERMISSION
        val manager = context.getSystemService(NotificationManager::class.java) ?: return Status.BLOCKED
        if (!manager.areNotificationsEnabled()) return Status.BLOCKED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = manager.getNotificationChannel(CHANNEL)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) return Status.BLOCKED
        }
        return Status.OK
    }

    /** Posts a sample for the next prayer right now, so the user can check sound and permission. */
    fun postTest(context: Context) {
        val snapshot = WaktuSholatApp.instance.repository.snapshot()
        show(context, snapshot.next, snapshot.nextMinute, reminderMinutes = 0, force = true)
    }

    /**
     * Posts the adhan (or its lead reminder) for [prayer] at [minuteOfDay] local wall-clock minutes.
     * [isReminder] only chooses the wording; it never changes the prayer itself.
     */
    fun post(context: Context, prayer: Prayer, minuteOfDay: Int, isReminder: Boolean, leadMinutes: Int) =
        show(context, prayer, minuteOfDay, if (isReminder) leadMinutes else 0, force = false)

    private fun show(context: Context, prayer: Prayer, minuteOfDay: Int, reminderMinutes: Int, force: Boolean) {
        val app = Language.wrap(context.applicationContext)
        if (!force && !WaktuSholatApp.instance.preferences.notificationsEnabled) return
        if (!canPostNotifications(app)) return

        ensureChannel(app)

        val name = PrayerLabels.of(app, prayer)
        val time = PrayerTimes.format(minuteOfDay)
        val reminder = reminderMinutes > 0

        val title = if (reminder) {
            app.getString(R.string.notification_reminder_title, name, Relative.long(app, reminderMinutes))
        } else {
            app.getString(R.string.notification_title, name)
        }
        val text = app.getString(R.string.notification_text, name, time)

        // Back to the already-running task instead of a fresh one, and it is the only content
        // intent this object creates, so one stable request code is enough.
        val content = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            app,
            REQUEST_CONTENT,
            content,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(app, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(app)
        }

        builder
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(false)
            // The alarms fire at the prayer instant, so "now" is within the system's few seconds of
            // idle batching from that instant. The real instant could be derived from the city's
            // epoch day, but the receiver is not given the civil date and the device may sit in a
            // different zone than the city; inventing a wall-clock time here would risk showing a
            // timestamp that disagrees with the schedule.
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)

        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(prayer.ordinal, builder.build())
    }

}
