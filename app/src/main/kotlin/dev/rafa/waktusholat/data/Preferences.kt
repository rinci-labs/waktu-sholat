package dev.rafa.waktusholat.data

import android.content.Context
import dev.rafa.waktusholat.core.CalculationMethod
import dev.rafa.waktusholat.core.City
import dev.rafa.waktusholat.core.Madhab

/**
 * The whole persisted state of the app: which location, which method, which madhab, and which
 * notifications are on. A single [android.content.SharedPreferences] file is more than enough here.
 */
class Preferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Key into the built-in city table. */
    var cityName: String?
        get() = prefs.getString(KEY_CITY_NAME, null)
        set(value) = prefs.edit().putString(KEY_CITY_NAME, value).apply()

    var cityProvince: String?
        get() = prefs.getString(KEY_CITY_PROVINCE, null)
        set(value) = prefs.edit().putString(KEY_CITY_PROVINCE, value).apply()

    /** Set when the user picked "my location" instead of a city from the list. */
    var useGps: Boolean
        get() = prefs.getBoolean(KEY_USE_GPS, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_GPS, value).apply()

    var latitude: Float
        get() = prefs.getFloat(KEY_LATITUDE, City.DEFAULT.latitude.toFloat())
        set(value) = prefs.edit().putFloat(KEY_LATITUDE, value).apply()

    var longitude: Float
        get() = prefs.getFloat(KEY_LONGITUDE, City.DEFAULT.longitude.toFloat())
        set(value) = prefs.edit().putFloat(KEY_LONGITUDE, value).apply()

    var timeZoneHours: Int
        get() = prefs.getInt(KEY_TZ, City.DEFAULT.timeZoneHours)
        set(value) = prefs.edit().putInt(KEY_TZ, value).apply()

    var methodId: Int
        get() = prefs.getInt(KEY_METHOD, CalculationMethod.DEFAULT.id)
        set(value) = prefs.edit().putInt(KEY_METHOD, value).apply()

    var madhabId: Int
        get() = prefs.getInt(KEY_MADHAB, Madhab.DEFAULT.ordinal)
        set(value) = prefs.edit().putInt(KEY_MADHAB, value).apply()

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()

    /** Minutes before the adhan to post the reminder. */
    var reminderLeadMinutes: Int
        get() = prefs.getInt(KEY_REMINDER_LEAD, 0)
        set(value) = prefs.edit().putInt(KEY_REMINDER_LEAD, value).apply()

    /** Imsak is shown as a separate row; some users do not want it. */
    var showImsak: Boolean
        get() = prefs.getBoolean(KEY_SHOW_IMSAK, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_IMSAK, value).apply()

    /**
     * True when the stored device fix is outside Indonesia, so the schedule cannot be resolved from
     * it. The app is built for Indonesian coordinates and the nearest-city fallback would otherwise
     * silently produce a meaningless timetable for a user abroad.
     */
    val deviceLocationOutOfRange: Boolean
        get() = useGps && !City.isServiceable(latitude.toDouble(), longitude.toDouble())

    /** Current location, resolved from the built-in table or from a device fix. */
    fun resolveCity(): City {
        if (useGps) {
            val latitude = latitude.toDouble()
            val longitude = longitude.toDouble()
            // Only an in-country fix may override the chosen city: outside Indonesia the nearest
            // entry is thousands of kilometres away and its wall-clock zone is meaningless.
            if (City.isServiceable(latitude, longitude)) {
                val nearest = City.nearest(latitude, longitude)
                return City(
                    name = nearest.name,
                    province = nearest.province,
                    latitude = latitude,
                    longitude = longitude,
                    timeZoneHours = nearest.timeZoneHours,
                )
            }
        }
        val name = cityName ?: return City.DEFAULT
        val province = cityProvince
        return City.ALL.firstOrNull {
            it.name == name && (province == null || it.province == province)
        } ?: City.DEFAULT
    }

    fun setCity(city: City) {
        useGps = false
        cityName = city.name
        cityProvince = city.province
        latitude = city.latitude.toFloat()
        longitude = city.longitude.toFloat()
        timeZoneHours = city.timeZoneHours
    }

    fun setCoordinates(latitude: Double, longitude: Double) {
        val nearest = City.nearest(latitude, longitude)
        useGps = true
        this.latitude = latitude.toFloat()
        this.longitude = longitude.toFloat()
        this.timeZoneHours = nearest.timeZoneHours
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
        const val KEY_TZ = "timezone_hours"
        const val KEY_METHOD = "method_id"
        const val KEY_MADHAB = "madhab_id"
        const val KEY_NOTIFICATIONS = "notifications"
        const val KEY_REMINDER_LEAD = "reminder_lead"
        const val KEY_SHOW_IMSAK = "show_imsak"
    }
}
