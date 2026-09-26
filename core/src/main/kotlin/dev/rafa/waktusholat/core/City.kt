package dev.rafa.waktusholat.core

import java.util.Locale
import java.util.TimeZone
import kotlin.math.cos

/**
 * One location. Built-in entries are the administrative seat of an Indonesian kota/kabupaten, which
 * is what published Kemenag schedules are keyed on; a device fix anywhere in the world is a City
 * too, carrying the device's IANA [zoneId] so half-hour offsets and daylight saving are honoured.
 */
data class City(
    val name: String,
    /** Province for built-in entries; region or country for a device fix (may be blank). */
    val province: String,
    val latitude: Double,
    val longitude: Double,
    /** Fixed UTC offset in whole hours for built-in entries: 7 = WIB, 8 = WITA, 9 = WIT. */
    val timeZoneHours: Int,
    /** IANA zone (e.g. `Europe/London`); when set it overrides [timeZoneHours], DST included. */
    val zoneId: String? = null,
) {
    @delegate:Transient
    private val zone: TimeZone? by lazy { zoneId?.let(TimeZone::getTimeZone) }

    /** `Jakarta, DKI Jakarta`, or just the name when there is no region. */
    val label: String get() = if (province.isBlank()) name else "$name, $province"

    /** UTC offset in minutes in effect at [utcMillis]. */
    fun offsetMinutesAt(utcMillis: Long): Int = zone?.getOffset(utcMillis)?.div(60_000) ?: timeZoneHours * 60

    /** UTC offset in minutes on [date], taken at local noon so a DST switch at 2am never splits a day. */
    fun offsetMinutesOn(date: CivilDate): Int =
        offsetMinutesAt(date.epochDay * 86_400_000L + 12 * 3_600_000L - timeZoneHours * 3_600_000L)

    /** Time zone abbreviation used in the UI: WIB/WITA/WIT, the zone's own short name, or `UTC+5:30`. */
    val zoneLabel: String
        get() {
            val tz = zone
            if (tz == null || tz.id in INDONESIAN_ZONES) {
                val hours = if (tz == null) timeZoneHours else tz.rawOffset / 3_600_000
                when (hours) {
                    7 -> return "WIB"
                    8 -> return "WITA"
                    9 -> return "WIT"
                }
            }
            val minutes = offsetMinutesAt(System.currentTimeMillis())
            val name = tz?.getDisplayName(tz.inDaylightTime(java.util.Date()), TimeZone.SHORT, Locale.ROOT)
            return if (name != null && !name.startsWith("GMT")) name else utcLabel(minutes)
        }

    /** Short form for dense rows: `Jakarta`. */
    val shortLabel: String get() = name

    companion object {
        val DEFAULT = City("Jakarta", "DKI Jakarta", -6.2088, 106.8456, 7)

        private val INDONESIAN_ZONES = setOf("Asia/Jakarta", "Asia/Pontianak", "Asia/Makassar", "Asia/Jayapura")

        /** `UTC+7`, `UTC+5:30`, `UTC-3`. */
        fun utcLabel(offsetMinutes: Int): String {
            val sign = if (offsetMinutes < 0) "-" else "+"
            val abs = kotlin.math.abs(offsetMinutes)
            val hours = abs / 60
            val minutes = abs % 60
            return "UTC$sign$hours" + if (minutes == 0) "" else ":" + (if (minutes < 10) "0$minutes" else "$minutes")
        }

        /**
         * How far a location may be from the closest built-in city and still produce a meaningful
         * schedule. The table holds admin seats, so the widest real gap inside a province is about
         * 150 km; and Indonesia's land borders are close to Malaysian and Papuan towns, where the
         * neighbour's times genuinely are within a minute or two.
         */
        private const val MAX_SERVICEABLE_DISTANCE_KM = 250.0

        private const val EARTH_RADIUS_KM = 6_371.0

        /**
         * True when a coordinate is close enough to a built-in city to borrow its name and zone.
         *
         * A bounding box is not enough here: Indonesia's box contains Singapore, Malaysia, Brunei and
         * part of Papua New Guinea, and a fix in California would still snap to a plausible-looking
         * city 11,000 km away. Distance to the nearest city is the property that actually matters.
         */
        fun isServiceable(latitude: Double, longitude: Double): Boolean =
            nearestDistanceKm(latitude, longitude) <= MAX_SERVICEABLE_DISTANCE_KM

        /**
         * Great-circle distance in kilometres to the closest built-in city.
         */
        fun nearestDistanceKm(latitude: Double, longitude: Double): Double {
            var best = Double.MAX_VALUE
            for (city in ALL) {
                val dLat = Math.toRadians(city.latitude - latitude)
                val dLng = Math.toRadians(city.longitude - longitude)
                val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                    kotlin.math.cos(Math.toRadians(latitude)) * kotlin.math.cos(Math.toRadians(city.latitude)) *
                    kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
                val distance = 2 * EARTH_RADIUS_KM * kotlin.math.asin(kotlin.math.sqrt(a))
                if (distance < best) best = distance
            }
            return best
        }

        /** All cities, name-sorted then province. */
        val ALL: List<City> = Cities.ALL.sortedWith(compareBy({ it.name }, { it.province }))

        /** Province names, sorted, for the location picker. */
        val PROVINCES: List<String> = ALL.map { it.province }.distinct().sorted()

        /** Nearest built-in city to a coordinate, by equatorial-scaled degree distance. */
        fun nearest(latitude: Double, longitude: Double): City {
            val lngScale = cos(Math.toRadians(latitude.coerceIn(-12.0, 7.0)))
            var best = ALL.first()
            var bestDistance = Double.MAX_VALUE
            for (city in ALL) {
                val dLat = city.latitude - latitude
                val dLng = (city.longitude - longitude) * lngScale
                val distance = dLat * dLat + dLng * dLng
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = city
                }
            }
            return best
        }
    }
}
