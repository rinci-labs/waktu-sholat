package dev.rafa.waktusholat.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Qibla bearing is the one number a user physically acts on, so it is pinned to published
 * figures rather than to the formula's own output.
 *
 * Reference values cross-checked against the great-circle initial-bearing solution used by Kemenag's
 * published directions and by independent Qibla calculators; each Indonesian city points a little
 * north of west, between 291 and 296 degrees.
 */
class QiblaTest {

    private fun assertBearing(latitude: Double, longitude: Double, expected: Double, tolerance: Double = 0.5) {
        val actual = Qibla.bearingDegrees(latitude, longitude)
        assertEquals("bearing for $latitude,$longitude", expected, actual, tolerance)
    }

    @Test
    fun bearingsMatchPublishedDirectionsForIndonesianCities() {
        assertBearing(-6.2088, 106.8456, 295.15)   // Jakarta
        assertBearing(-7.2575, 112.7521, 294.03)   // Surabaya
        assertBearing(3.5952, 98.6722, 292.77)     // Medan
        assertBearing(-5.1477, 119.4327, 292.48)   // Makassar
        assertBearing(-2.5916, 140.6690, 291.33)   // Jayapura
        assertBearing(5.5483, 95.3238, 292.17)     // Banda Aceh
        assertBearing(-10.1772, 123.6070, 292.18)  // Kupang
    }

    @Test
    fun everyIndonesianCityPointsWestNorthWest() {
        // The whole archipelago lies east and south of Mecca, so no city's bearing may leave this
        // band. A sign error or a swapped argument would immediately break this.
        for (city in City.ALL) {
            val bearing = Qibla.bearingDegrees(city.latitude, city.longitude)
            assertTrue(
                "${city.label} bearing $bearing is outside the expected band",
                bearing in 285.0..300.0,
            )
        }
    }

    @Test
    fun bearingIsDueNorthWhenDirectlySouthOfTheKaaba() {
        // Same meridian, so the great circle runs straight north.
        val bearing = Qibla.bearingDegrees(-6.0, Qibla.KAABA_LONGITUDE)
        assertEquals(0.0, bearing, 0.01)
    }

    @Test
    fun bearingIsDueSouthWhenDirectlyNorthOfTheKaaba() {
        val bearing = Qibla.bearingDegrees(41.0, Qibla.KAABA_LONGITUDE)
        assertEquals(180.0, bearing, 0.01)
    }

    @Test
    fun bearingIsInRangeForCitiesAllOverTheWorld() {
        // The result must stay a valid compass heading everywhere, including through the
        // wrap-around at 360 degrees.
        for (latitude in -80..80 step 10) {
            for (longitude in -180..170 step 10) {
                val bearing = Qibla.bearingDegrees(latitude.toDouble(), longitude.toDouble())
                assertTrue(
                    "bearing out of range at $latitude,$longitude: $bearing",
                    bearing >= 0.0 && bearing < 360.0,
                )
            }
        }
    }

    @Test
    fun distanceIsZeroAtTheKaabaAndGrowsWithDistance() {
        assertEquals(0.0, Qibla.distanceKm(Qibla.KAABA_LATITUDE, Qibla.KAABA_LONGITUDE), 0.5)
        // Jakarta is about 7,920 km from Mecca.
        assertEquals(7_920.0, Qibla.distanceKm(-6.2088, 106.8456), 25.0)
        // Jayapura is the farthest Indonesian city, past 11,000 km.
        assertTrue(Qibla.distanceKm(-2.5916, 140.6690) > 11_000.0)
    }

    @Test
    fun distanceNeverExceedsHalfTheCircumference() {
        for (latitude in -80..80 step 20) {
            for (longitude in -180..170 step 20) {
                val distance = Qibla.distanceKm(latitude.toDouble(), longitude.toDouble())
                assertTrue("distance out of range: $distance", distance in 0.0..20_015.0)
            }
        }
    }

    @Test
    fun ofBundlesBothFiguresAndFormatsThem() {
        val direction = Qibla.of(-6.2088, 106.8456)
        assertEquals("295°", direction.bearingText)
        assertEquals("7.920 km", direction.distanceText)
    }
}
