package dev.rafa.waktusholat

import android.app.Application
import dev.rafa.waktusholat.data.Preferences
import dev.rafa.waktusholat.data.ScheduleRepository
import dev.rafa.waktusholat.notify.AlarmScheduler
import dev.rafa.waktusholat.widget.WidgetUpdater

/**
 * Holds the two collaborators the app needs. There is no DI framework: the object graph is two
 * items deep and a container would cost more than it saves. Both are process-wide singletons, so
 * the activities, the widgets and the alarm receivers share one preference cache and one schedule
 * cache.
 */
class WaktuSholatApp : Application() {

    val preferences: Preferences by lazy { Preferences(this) }

    val repository: ScheduleRepository by lazy { ScheduleRepository(preferences) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    /**
     * Called whenever a setting that changes the schedule is written. Re-arms the adhan alarms and
     * repaints every widget, so the three surfaces never disagree.
     */
    fun notifyScheduleChanged() {
        AlarmScheduler.reschedule(this)
        WidgetUpdater.updateAll(this)
    }

    companion object {
        lateinit var instance: WaktuSholatApp
            private set
    }
}
