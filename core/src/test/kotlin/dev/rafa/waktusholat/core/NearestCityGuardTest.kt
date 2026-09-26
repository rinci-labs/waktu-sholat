package dev.rafa.waktusholat.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The nearest-city lookup turns a device fix into coordinates and a time zone, so a fix it cannot
 * serve must be rejected rather than silently mapped to a plausible-looking Indonesian city.
 *
 * A bounding box is not a sufficient test, which is why these cases are written around distance: a
 * fix in Singapore sits inside Indonesia's bounding box and only ~20 km from Batam, so it is
 * legitimately serviceable, while a fix in California is 11,000 km from anything in the table.
 */
class NearestCityGuardTest {

    @Test
    fun coordinatesAcrossTheArchipelagoAreServiceable() {
        for ((latitude, longitude, where) in listOf(
            Triple(-6.2088, 106.8456, "Jakarta"),
            Triple(-7.2575, 112.7521, "Surabaya"),
            Triple(-2.5916, 140.6690, "Jayapura"),
            Triple(-5.1477, 119.4327, "Makassar"),
            Triple(1.4748, 124.8421, "Manado"),
        )) {
            assertTrue("$where should be serviceable", City.isServiceable(latitude, longitude))
        }
    }

    @Test
    fun distantCoordinatesAreRejected() {
        // Sunnyvale is the emulator's default fix; the rest are a phone carried abroad.
        for ((latitude, longitude, where) in listOf(
            Triple(37.4220, -122.0841, "Sunnyvale"),
            Triple(51.5074, -0.1278, "London"),
            Triple(-33.8688, 151.2093, "Sydney"),
            Triple(25.2048, 55.2708, "Dubai"),
            Triple(35.6762, 139.6503, "Tokyo"),
        )) {
            val km = City.nearestDistanceKm(latitude, longitude)
            assertTrue("$where must be rejected (nearest city is ${km.toInt()} km)", !City.isServiceable(latitude, longitude))
        }
    }

    @Test
    fun aRejectedFixIsStillSnappedToADistantCityWithoutTheGuard() {
        // Documents why the guard cannot be dropped: the nearest lookup happily returns a real city
        // for a fix on another continent, so only the distance test distinguishes the two cases.
        val nearest = City.nearest(37.4220, -122.0841)
        assertTrue(
            "expected a distant city, got ${nearest.label}",
            City.nearestDistanceKm(37.4220, -122.0841) > 5_000,
        )
    }

    @Test
    fun borderTownsAreStillServiceable() {
        // The threshold must not be so tight that legitimate border areas are refused.
        for ((latitude, longitude, where) in listOf(
            Triple(1.3521, 103.8198, "Singapore"),
            Triple(4.1755, 117.9800, "Tawau across the Kalimantan border"),
            Triple(-2.6960, 141.3000, "Vanimo across the Papua border"),
        )) {
            val km = City.nearestDistanceKm(latitude, longitude)
            assertTrue("$where should be serviceable (nearest city ${km.toInt()} km)", City.isServiceable(latitude, longitude))
        }
    }

    @Test
    fun everyBuiltInCityIsServiceable() {
        for (city in City.ALL) {
            assertTrue("${city.label} must be serviceable", City.isServiceable(city.latitude, city.longitude))
        }
    }

    @Test
    fun nearestReturnsACityWithAValidZone() {
        for (city in City.ALL.take(50)) {
            val nearest = City.nearest(city.latitude, city.longitude)
            assertEquals("lookup for ${city.label}", city.name, nearest.name)
            assertTrue(nearest.timeZoneHours in 7..9)
        }
    }
}
