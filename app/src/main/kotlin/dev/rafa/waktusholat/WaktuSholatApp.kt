package dev.rafa.waktusholat

import android.app.Application
import dev.rafa.waktusholat.data.DeviceLocation
import dev.rafa.waktusholat.data.Preferences
import dev.rafa.waktusholat.data.ScheduleRepository
import dev.rafa.waktusholat.notify.AlarmScheduler
import dev.rafa.waktusholat.ui.Language
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

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(Language.wrap(base))

    override fun onCreate() {
        super.onCreate()
        instance = this
        // A force-stop (or an OEM battery manager acting like one) wipes every pending alarm and the
        // system sends nothing afterwards, so every process start re-arms them. It is idempotent.
        runCatching { AlarmScheduler.reschedule(this) }
    }

    /**
     * Called whenever a setting that changes the schedule is written. Re-arms the adhan alarms and
     * repaints every widget, so the three surfaces never disagree.
     */
    fun notifyScheduleChanged() {
        AlarmScheduler.reschedule(this)
        WidgetUpdater.updateAll(this)
        dev.rafa.waktusholat.ui.IconAuto.sync(this)
    }

    /**
     * Adopts a device fix: coordinates first (the schedule is right immediately, offline), then a
     * place name from the geocoder when one is available, which repaints the label a moment later.
     */
    fun applyFix(location: android.location.Location) {
        // The zone of the place itself (Indonesia: exact, offline); the phone's zone otherwise.
        preferences.setCoordinates(location.latitude, location.longitude, DeviceLocation.zoneFor(null, location.latitude, location.longitude))
        notifyScheduleChanged()
        DeviceLocation.describe(this, location.latitude, location.longitude) { place ->
            // Only name the fix if it is still the current one.
            if (place != null && preferences.followsDevice && preferences.distanceFromFix(location.latitude, location.longitude) < 1f) {
                val zone = DeviceLocation.zoneFor(place.countryCode, location.latitude, location.longitude)
                preferences.setPlace(place.name, place.region, zone)
                notifyScheduleChanged()
            }
        }
    }

    /**
     * When the device location is in use, quietly adopts a newer cached fix (no GPS request, no
     * battery cost) if the phone has moved more than [REFIX_METRES] since the stored one.
     */
    fun refreshFixPassively() {
        if (!preferences.followsDevice) return
        val location = DeviceLocation.lastKnown(this) ?: return
        if (location.time <= preferences.fixTime) return
        if (preferences.distanceFromFix(location.latitude, location.longitude) < REFIX_METRES) return
        applyFix(location)
    }

    companion object {
        /** About 4 seconds of prayer-time drift; smaller moves are not worth a repaint. */
        private const val REFIX_METRES = 1_500f

        lateinit var instance: WaktuSholatApp
            private set
    }
}
