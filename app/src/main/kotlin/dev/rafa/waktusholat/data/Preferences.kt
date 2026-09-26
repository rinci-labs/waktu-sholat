package dev.rafa.waktusholat.data

import android.content.Context
import android.content.SharedPreferences
import dev.rafa.waktusholat.R
import dev.rafa.waktusholat.core.CalculationMethod
import dev.rafa.waktusholat.core.City
import dev.rafa.waktusholat.core.Madhab
import java.util.TimeZone

/**
 * The whole persisted state of the app: which location, which method, which madhab, and which
 * notifications are on. A single [SharedPreferences] file is more than enough here.
 *
 * The resolved [City] is memoised: resolving it means a scan of the city table (and, for a device
 * fix, a nearest-neighbour search plus a serviceability check), and the widgets, the alarms and the
 * countdown all ask for it. Every write goes through this class, so each write simply drops
 * the cached value.
 */
class Preferences(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @Volatile
    private var cachedCity: City? = null


    private inline fun write(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        cachedCity = null
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
     * The device fix, anywhere in the world. Its wall clock comes from the IANA zone the phone was in
     * when the fix was taken ([KEY_ZONE]), so half-hour offsets and daylight saving are right; its
     * name comes from the geocoder when there was one, else from the nearest built-in city when that
     * is close, else a generic label.
     */
    private fun deviceCity(): City {
        val zone = prefs.getString(KEY_ZONE, null) ?: TimeZone.getDefault().id
        val placeName = prefs.getString(KEY_PLACE_NAME, null)
        val placeRegion = prefs.getString(KEY_PLACE_REGION, null).orEmpty()
        val nearby = City.nearest(latitude, longitude).takeIf { City.isServiceable(latitude, longitude) }
        return City(
            name = placeName ?: nearby?.name ?: appContext.getString(R.string.my_location),
            province = if (placeName != null) placeRegion else nearby?.province.orEmpty(),
            latitude = latitude,
            longitude = longitude,
            timeZoneHours = nearby?.timeZoneHours ?: (TimeZone.getTimeZone(zone).rawOffset / 3_600_000),
            zoneId = zone,
        )
    }

    /** Current location, resolved from the built-in table or from a device fix. */
    fun resolveCity(): City = cachedCity ?: computeCity().also { cachedCity = it }

    private fun computeCity(): City {
        if (useGps) return deviceCity()
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

    /** Stores a device fix taken in [zoneId]; the place name is cleared until [setPlace] names it. */
    fun setCoordinates(latitude: Double, longitude: Double, zoneId: String = TimeZone.getDefault().id) = write {
        putBoolean(KEY_USE_GPS, true)
        putBoolean(KEY_PICKED, false)
        putFloat(KEY_LATITUDE, latitude.toFloat())
        putFloat(KEY_LONGITUDE, longitude.toFloat())
        putString(KEY_ZONE, zoneId)
        putLong(KEY_FIX_TIME, System.currentTimeMillis())
        remove(KEY_PLACE_NAME)
        remove(KEY_PLACE_REGION)
    }

    /** A place picked from the worldwide search: fixed coordinates, zone and name; never auto-updated. */
    fun setPickedPlace(latitude: Double, longitude: Double, zoneId: String, name: String, region: String) = write {
        putBoolean(KEY_USE_GPS, true)
        putBoolean(KEY_PICKED, true)
        putFloat(KEY_LATITUDE, latitude.toFloat())
        putFloat(KEY_LONGITUDE, longitude.toFloat())
        putString(KEY_ZONE, zoneId)
        putString(KEY_PLACE_NAME, name)
        putString(KEY_PLACE_REGION, region)
    }

    /** True when the coordinates come from the device (and may follow it), not from a search. */
    val followsDevice: Boolean get() = useGps && !prefs.getBoolean(KEY_PICKED, false)

    fun setPlace(name: String, region: String, zoneId: String) = write {
        putString(KEY_PLACE_NAME, name)
        putString(KEY_PLACE_REGION, region)
        putString(KEY_ZONE, zoneId)
    }

    /** The phone moved to another zone while using the device location: follow it. */
    fun updateDeviceZone(zoneId: String) {
        // Only when the stored zone came from the phone rather than from the place itself.
        if (followsDevice && prefs.getString(KEY_PLACE_NAME, null) == null && prefs.getString(KEY_ZONE, null) != zoneId) {
            write { putString(KEY_ZONE, zoneId) }
        }
    }

    /** When the stored device fix was taken, or 0. */
    val fixTime: Long get() = prefs.getLong(KEY_FIX_TIME, 0L)

    /** Metres between the stored fix and a new one, to decide whether a re-fix is worth saving. */
    fun distanceFromFix(latitude: Double, longitude: Double): Float {
        val out = FloatArray(1)
        android.location.Location.distanceBetween(this.latitude, this.longitude, latitude, longitude, out)
        return out[0]
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
        const val KEY_ZONE = "zone_id"
        const val KEY_PICKED = "picked_place"
        const val KEY_FIX_TIME = "fix_time"
        const val KEY_PLACE_NAME = "place_name"
        const val KEY_PLACE_REGION = "place_region"
    }
}
