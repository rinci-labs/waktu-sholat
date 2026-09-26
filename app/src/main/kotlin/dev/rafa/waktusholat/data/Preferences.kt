package dev.rafa.waktusholat.data

import android.content.Context
import android.content.SharedPreferences
import dev.rafa.waktusholat.core.CalculationMethod
import dev.rafa.waktusholat.core.City
import dev.rafa.waktusholat.core.Madhab

/**
 * The whole persisted state of the app: which location, which method, which madhab, and which
 * notifications are on. A single [SharedPreferences] file is more than enough here.
 *
 * The resolved [City] is memoised: resolving it means a scan of the city table (and, for a device
 * fix, a nearest-neighbour search plus a serviceability check), and the widget, the alarms and the
 * 1 Hz countdown all ask for it. Every write goes through this class, so each write simply drops
 * the cached value.
 */
class Preferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @Volatile
    private var cachedCity: City? = null

    @Volatile
    private var cachedOutOfRange: Boolean? = null

    private inline fun write(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        cachedCity = null
        cachedOutOfRange = null
    }

    /** Set when the user picked "my location" instead of a city from the list. */
    var useGps: Boolean
        get() = prefs.getBoolean(KEY_USE_GPS, false)
        set(value) = write { putBoolean(KEY_USE_GPS, value) }

    private val latitude: Double
        get() = prefs.getFloat(KEY_LATITUDE, City.DEFAULT.latitude.toFloat()).toDouble()

    private val longitude: Double
        get() = prefs.getFloat(KEY_LONGITUDE, City.DEFAULT.longitude.toFloat()).toDouble()

    var methodId: Int
        get() = prefs.getInt(KEY_METHOD, CalculationMethod.DEFAULT.id)
        set(value) = write { putInt(KEY_METHOD, value) }

    var madhabId: Int
        get() = prefs.getInt(KEY_MADHAB, Madhab.DEFAULT.ordinal)
        set(value) = write { putInt(KEY_MADHAB, value) }

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, false)
        set(value) = write { putBoolean(KEY_NOTIFICATIONS, value) }

    /** Minutes before the adhan to post the reminder. */
    var reminderLeadMinutes: Int
        get() = prefs.getInt(KEY_REMINDER_LEAD, 0)
        set(value) = write { putInt(KEY_REMINDER_LEAD, value) }

    /** Imsak is shown as a separate row; some users do not want it. */
    var showImsak: Boolean
        get() = prefs.getBoolean(KEY_SHOW_IMSAK, true)
        set(value) = write { putBoolean(KEY_SHOW_IMSAK, value) }

    /**
     * True when the stored device fix is outside Indonesia, so the schedule cannot be resolved from
     * it. The app is built for Indonesian coordinates and the nearest-city fallback would otherwise
     * silently produce a meaningless timetable for a user abroad.
     */
    val deviceLocationOutOfRange: Boolean
        get() = cachedOutOfRange ?: (useGps && !City.isServiceable(latitude, longitude))
            .also { cachedOutOfRange = it }

    /** Current location, resolved from the built-in table or from a device fix. */
    fun resolveCity(): City = cachedCity ?: computeCity().also { cachedCity = it }

    private fun computeCity(): City {
        if (useGps && !deviceLocationOutOfRange) {
            // Only an in-country fix may override the chosen city: outside Indonesia the nearest
            // entry is thousands of kilometres away and its wall-clock zone is meaningless.
            val nearest = City.nearest(latitude, longitude)
            return nearest.copy(latitude = latitude, longitude = longitude)
        }
        val name = prefs.getString(KEY_CITY_NAME, null) ?: return City.DEFAULT
        val province = prefs.getString(KEY_CITY_PROVINCE, null)
        return City.ALL.firstOrNull {
            it.name == name && (province == null || it.province == province)
        } ?: City.DEFAULT
    }

    fun setCity(city: City) = write {
        putBoolean(KEY_USE_GPS, false)
        putString(KEY_CITY_NAME, city.name)
        putString(KEY_CITY_PROVINCE, city.province)
        putFloat(KEY_LATITUDE, city.latitude.toFloat())
        putFloat(KEY_LONGITUDE, city.longitude.toFloat())
    }

    fun setCoordinates(latitude: Double, longitude: Double) {
        val nearest = City.nearest(latitude, longitude)
        write {
            putBoolean(KEY_USE_GPS, true)
            putFloat(KEY_LATITUDE, latitude.toFloat())
            putFloat(KEY_LONGITUDE, longitude.toFloat())
        }
    }

    val method: CalculationMethod get() = CalculationMethod.fromId(methodId)

    val madhab: Madhab get() = Madhab.fromId(madhabId)

    private companion object {
        const val FILE = "waktu_sholat"
        const val KEY_CITY_NAME = "city_name"
        const val KEY_CITY_PROVINCE = "city_province"
        const val KEY_USE_GPS = "use_gps"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_METHOD = "method_id"
        const val KEY_MADHAB = "madhab_id"
        const val KEY_NOTIFICATIONS = "notifications"
        const val KEY_REMINDER_LEAD = "reminder_lead"
        const val KEY_SHOW_IMSAK = "show_imsak"
    }
}
