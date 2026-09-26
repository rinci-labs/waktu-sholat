package dev.rafa.waktusholat.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import dev.rafa.waktusholat.core.City
import java.util.Locale
import java.util.TimeZone

/**
 * Device position, framework-only (no Play Services).
 *
 * [locate] prefers a recent cached fix and otherwise asks the best enabled provider for one fresh
 * fix, giving up after a timeout. [describe] turns coordinates into a place name with the platform
 * [Geocoder]; that needs the network, so it is optional and the caller falls back to the nearest
 * built-in city when it returns null. Prayer times themselves never need either.
 */
object DeviceLocation {

    /** A cached fix younger than this is precise enough; prayer times move ~1 min per 25 km. */
    private const val FRESH_MILLIS = 5 * 60_000L
    private const val TIMEOUT_MILLIS = 15_000L
    private const val MAX_RESULTS = 6

    class Place(val name: String, val region: String)

    fun hasPermission(context: Context): Boolean =
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun granted(context: Context, permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** The freshest cached fix from any enabled provider, or null. */
    @SuppressLint("MissingPermission") // checked by hasPermission(); SecurityException also caught
    fun lastKnown(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        return manager.getProviders(true)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    /** Delivers a precise fix on the main thread, or the best cached one (possibly null) on timeout. */
    @SuppressLint("MissingPermission") // checked by hasPermission(); SecurityException also caught
    fun locate(context: Context, onResult: (Location?) -> Unit) {
        val cached = lastKnown(context)
        if (cached != null && System.currentTimeMillis() - cached.time < FRESH_MILLIS) {
            onResult(cached)
            return
        }
        val manager = context.getSystemService(LocationManager::class.java)
        val provider = manager?.let { bestProvider(it) }
        if (manager == null || provider == null || !hasPermission(context)) {
            onResult(cached)
            return
        }

        val main = Handler(Looper.getMainLooper())
        var done = false
        fun finish(location: Location?) {
            if (done) return
            done = true
            onResult(location ?: cached)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val cancel = CancellationSignal()
                manager.getCurrentLocation(provider, cancel, context.mainExecutor) { finish(it) }
                main.postDelayed({ cancel.cancel(); finish(null) }, TIMEOUT_MILLIS)
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) = finish(location)
                    @Deprecated("Required on API < 29")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }
                @Suppress("DEPRECATION")
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                main.postDelayed({ manager.removeUpdates(listener); finish(null) }, TIMEOUT_MILLIS)
            }
        } catch (_: SecurityException) {
            finish(null)
        }
    }

    private fun bestProvider(manager: LocationManager): String? {
        val enabled = manager.getProviders(true)
        return listOf("fused", LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { it in enabled }
    }

    /**
     * Reverse-geocodes on a background thread and calls back on the main thread with a place such as
     * `Tebet` / `Jakarta Selatan`, or null when no geocoder or network is available.
     */
    fun describe(context: Context, latitude: Double, longitude: Double, onResult: (Place?) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        if (!Geocoder.isPresent()) {
            onResult(null)
            return
        }
        val geocoder = Geocoder(context.applicationContext, context.resources.configuration.locales[0] ?: Locale.getDefault())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    main.post { onResult(addresses.firstOrNull()?.let(::toPlace)) }
                }

                override fun onError(errorMessage: String?) {
                    main.post { onResult(null) }
                }
            })
        } else {
            Thread {
                @Suppress("DEPRECATION")
                val address = runCatching { geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull() }.getOrNull()
                main.post { onResult(address?.let(::toPlace)) }
            }.start()
        }
    }

    /** A search hit, with the IANA zone guessed offline from its country and longitude. */
    class Found(val name: String, val region: String, val latitude: Double, val longitude: Double, val zoneId: String)

    /** Forward-geocodes [query] (any place on Earth) on a background thread; empty when offline. */
    fun search(context: Context, query: String, onResult: (List<Found>) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        if (!Geocoder.isPresent()) {
            onResult(emptyList())
            return
        }
        val geocoder = Geocoder(context.applicationContext, context.resources.configuration.locales[0] ?: Locale.getDefault())
        fun deliver(addresses: List<Address>?) {
            val found = addresses.orEmpty().mapNotNull { address ->
                val place = toPlace(address) ?: return@mapNotNull null
                if (!address.hasLatitude() || !address.hasLongitude()) return@mapNotNull null
                Found(place.name, place.region, address.latitude, address.longitude, zoneFor(address.countryCode, address.latitude, address.longitude))
            }.distinctBy { "${it.name}|${it.region}" }
            main.post { onResult(found) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocationName(query, MAX_RESULTS, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) = deliver(addresses)
                override fun onError(errorMessage: String?) = deliver(null)
            })
        } else {
            Thread {
                @Suppress("DEPRECATION")
                deliver(runCatching { geocoder.getFromLocationName(query, MAX_RESULTS) }.getOrNull())
            }.start()
        }
    }

    /**
     * The IANA zone for a point, without a network or a boundary database. Inside Indonesia the
     * built-in table knows the zone exactly; elsewhere ICU lists the zones of the country, and the
     * one whose standard offset best matches the longitude is chosen, which is exact for the vast
     * majority of countries (one zone) and a close guess for the few spanning several.
     */
    fun zoneFor(countryCode: String?, latitude: Double, longitude: Double): String {
        if (City.isServiceable(latitude, longitude)) {
            return when (City.nearest(latitude, longitude).timeZoneHours) {
                8 -> "Asia/Makassar"
                9 -> "Asia/Jayapura"
                else -> "Asia/Jakarta"
            }
        }
        val zones = countryCode?.let { code ->
            android.icu.util.TimeZone.getAvailableIDs(code)
                .mapNotNull { android.icu.util.TimeZone.getCanonicalID(it) }
                .filter { '/' in it && !it.startsWith("Etc/") }
                .distinct()
        }.orEmpty()
        if (zones.isEmpty()) return TimeZone.getDefault().id
        val solar = longitude / 15.0 * 3_600_000
        return zones.minByOrNull { kotlin.math.abs(TimeZone.getTimeZone(it).rawOffset - solar) } ?: zones.first()
    }

    /** Most specific name first; the region is the next distinct level up, plus the country abroad. */
    private fun toPlace(address: Address): Place? {
        val levels = listOf(address.subLocality, address.locality, address.subAdminArea, address.adminArea)
            .filterNotNull()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val name = levels.firstOrNull() ?: return null
        val parts = mutableListOf<String>()
        levels.getOrNull(1)?.let(parts::add)
        if (address.countryCode != null && address.countryCode != "ID") address.countryName?.let(parts::add)
        return Place(name, parts.joinToString(", "))
    }
}
