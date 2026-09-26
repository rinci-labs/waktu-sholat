package dev.rafa.waktusholat

import android.app.Application
import dev.rafa.waktusholat.data.Preferences
import dev.rafa.waktusholat.data.PrayerScheduleFactory

/**
 * Holds the no-argument collaborators the app needs. There is no DI framework: the object graph is
 * three items deep and a container would cost more than it saves.
 */
class WaktuSholatApp : Application() {

    val preferences: Preferences by lazy { Preferences(this) }

    val scheduleFactory: PrayerScheduleFactory by lazy { PrayerScheduleFactory() }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    /**
     * Called whenever a setting that changes the schedule is written. Re-arms the adhan alarms and
     * asks the widget to repaint, so the three surfaces never disagree.
     */
    fun notifyScheduleChanged() {
        dev.rafa.waktusholat.notify.AlarmScheduler.reschedule(this)
        dev.rafa.waktusholat.widget.PrayerWidgetProvider.requestRefresh(this)
    }

    companion object {
        lateinit var instance: WaktuSholatApp
            private set
    }
}
