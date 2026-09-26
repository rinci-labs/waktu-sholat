package dev.rafa.waktusholat.core

import kotlin.math.cos

/**
 * One selectable location. Coordinates are the administrative seat of an Indonesian
 * kota/kabupaten, which is what published Kemenag schedules are keyed on.
 */
data class City(
    val name: String,
    val province: String,
    val latitude: Double,
    val longitude: Double,
    /** UTC offset in whole hours: 7 = WIB, 8 = WITA, 9 = WIT. */
    val timeZoneHours: Int,
) {
    /** `Jakarta, DKI Jakarta`. */
    val label: String get() = "$name, $province"

    /** Time zone abbreviation used in the UI. */
    val zoneLabel: String
        get() = when (timeZoneHours) {
            7 -> "WIB"
            8 -> "WITA"
            9 -> "WIT"
            else -> "UTC${if (timeZoneHours >= 0) "+" else ""}$timeZoneHours"
        }

    /** Short form for dense rows: `Jakarta`. */
    val shortLabel: String get() = name

    companion object {
        val DEFAULT = City("Jakarta", "DKI Jakarta", -6.2088, 106.8456, 7)

        /**
         * How far a location may be from the closest built-in city and still produce a meaningful
         * schedule. The table holds admin seats, so the widest real gap inside a province is about
         * 150 km; and Indonesia's land borders are close to Malaysian and Papuan towns, where the
         * neighbour's times genuinely are within a minute or two.
         */
        private const val MAX_SERVICEABLE_DISTANCE_KM = 250.0

        private const val EARTH_RADIUS_KM = 6_371.0

        /**
         * True when a coordinate is close enough to a built-in city for its times to be meaningful.
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
