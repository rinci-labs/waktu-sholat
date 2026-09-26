package dev.rafa.waktusholat.core

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Where the Qibla is from a given place: the bearing to face, and how far away it is. */
data class QiblaDirection(
    /** Clockwise degrees from true north, `0..<360`. */
    val bearingDegrees: Double,
    /** Great-circle distance to the Kaaba in kilometres. */
    val distanceKm: Double,
) {
    /** `295°` — the heading as a whole degree, which is the precision a compass can hold. */
    val bearingText: String get() = "${bearingDegrees.toInt()}°"

    /** `7.920 km`. */
    val distanceText: String get() = String.format("%,.0f km", distanceKm).replace(',', '.')
}

/**
 * Qibla bearing by great-circle (initial) bearing from the observer to the Kaaba.
 *
 * The formula is the standard initial-bearing equation, so the result is the direction to *start*
 * travelling along a great circle, which is what a compass rose needs. For Indonesian latitudes the
 * difference between this and the rhumb-line bearing is under a degree, but the great-circle figure
 * is the correct one and costs the same.
 */
object Qibla {

    /** The Kaaba, in Mecca. */
    const val KAABA_LATITUDE = 21.4224779
    const val KAABA_LONGITUDE = 39.8251832

    private const val EARTH_RADIUS_KM = 6_371.0088
    private const val RAD = Math.PI / 180.0
    private const val DEG = 180.0 / Math.PI

    /**
     * True bearing to the Kaaba from [latitude]/[longitude], clockwise from true north in `0..<360`.
     * At the Kaaba itself the bearing is undefined and 0 is returned.
     */
    fun bearingDegrees(latitude: Double, longitude: Double): Double {
        val phi1 = latitude * RAD
        val phi2 = KAABA_LATITUDE * RAD
        val deltaLambda = (KAABA_LONGITUDE - longitude) * RAD

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val bearing = atan2(y, x) * DEG
        return if (bearing < 0) bearing + 360.0 else bearing
    }

    /** Great-circle distance to the Kaaba in kilometres. */
    fun distanceKm(latitude: Double, longitude: Double): Double {
        val phi1 = latitude * RAD
        val phi2 = KAABA_LATITUDE * RAD
        val deltaPhi = (KAABA_LATITUDE - latitude) * RAD
        val deltaLambda = (KAABA_LONGITUDE - longitude) * RAD

        val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) +
            cos(phi1) * cos(phi2) * sin(deltaLambda / 2) * sin(deltaLambda / 2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a).coerceIn(0.0, 1.0))
    }

    fun of(latitude: Double, longitude: Double): QiblaDirection =
        QiblaDirection(bearingDegrees(latitude, longitude), distanceKm(latitude, longitude))
}
