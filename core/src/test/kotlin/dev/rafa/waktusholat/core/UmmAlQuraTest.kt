package dev.rafa.waktusholat.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verification for [UmmAlQura] against hardcoded literals.
 *
 * Expected values were produced from the same ICU Umm al-Qura table, via
 * `HijrahChronology.INSTITUTIONAL` (`java.time.chrono`) and independently via Node's
 * `Intl.DateTimeFormat("en-u-ca-islamic-umalqura")`. During development the implementation was
 * confirmed to agree with the JDK on all 301 supported year starts, all 3612 month starts and
 * every month length in AH 1300..1600, and with Node's ICU on 406 sampled dates.
 *
 * `https://api.aladhan.com/v1/gToH/DD-MM-YYYY?calendarMethod=UAQ` was also sampled, but it
 * disagrees with ICU on a noticeable fraction of dates (typically by one day, e.g. it reports
 * 1400-01-02 for 1979-11-21 where ICU reports 1400-01-01), so it was not used as the oracle.
 * This implementation intentionally follows ICU, which is what `HijrahChronology` uses.
 *
 * No test performs any I/O or network access.
 */
class UmmAlQuraTest {

    // --- Gregorian -> Hijri, spread across AH 1300..1600 (56 samples) ---

    @Test
    fun gregorianToHijriSamples() {
        assertHijri(1882, 11, 12, 1300, 1, 1)
        assertHijri(1883, 1, 15, 1300, 3, 6)
        assertHijri(1884, 6, 20, 1301, 8, 26)
        assertHijri(1890, 3, 3, 1307, 7, 11)
        assertHijri(1900, 1, 1, 1317, 8, 29)
        assertHijri(1910, 7, 19, 1328, 7, 12)
        assertHijri(1920, 5, 9, 1338, 8, 20)
        assertHijri(1930, 11, 2, 1349, 6, 11)
        assertHijri(1937, 5, 21, 1356, 3, 10)
        assertHijri(1945, 9, 9, 1364, 10, 2)
        assertHijri(1950, 6, 16, 1369, 8, 30)
        assertHijri(1955, 12, 4, 1375, 4, 19)
        assertHijri(1960, 8, 13, 1380, 2, 20)
        assertHijri(1965, 4, 25, 1384, 12, 23)
        assertHijri(1970, 1, 1, 1389, 10, 22)
        assertHijri(1975, 10, 6, 1395, 9, 30)
        assertHijri(1979, 11, 21, 1400, 1, 1)
        assertHijri(1985, 5, 7, 1405, 8, 16)
        assertHijri(1990, 3, 27, 1410, 8, 29)
        assertHijri(1995, 12, 7, 1416, 7, 14)
        assertHijri(2000, 4, 6, 1421, 1, 1)
        assertHijri(2005, 8, 22, 1426, 7, 17)
        assertHijri(2010, 1, 1, 1431, 1, 15)
        assertHijri(2015, 6, 18, 1436, 9, 1)
        assertHijri(2020, 8, 20, 1442, 1, 1)
        assertHijri(2024, 3, 11, 1445, 9, 1)
        assertHijri(2024, 4, 10, 1445, 10, 1)
        assertHijri(2025, 3, 1, 1446, 9, 1)
        assertHijri(2026, 2, 18, 1447, 9, 1)
        assertHijri(2026, 9, 25, 1448, 4, 14)
        assertHijri(2027, 2, 8, 1448, 9, 1)
        assertHijri(2030, 1, 1, 1451, 8, 26)
        assertHijri(2035, 7, 15, 1457, 5, 9)
        assertHijri(2040, 10, 30, 1462, 10, 24)
        assertHijri(2045, 3, 12, 1467, 4, 23)
        assertHijri(2050, 6, 1, 1472, 9, 11)
        assertHijri(2055, 2, 14, 1477, 7, 17)
        assertHijri(2060, 9, 3, 1483, 4, 8)
        assertHijri(2065, 1, 20, 1487, 10, 13)
        assertHijri(2070, 5, 5, 1493, 3, 24)
        assertHijri(2075, 11, 11, 1498, 12, 2)
        assertHijri(2077, 1, 1, 1500, 2, 6)
        assertHijri(2080, 4, 9, 1503, 6, 18)
        assertHijri(2085, 8, 25, 1509, 1, 4)
        assertHijri(2090, 2, 6, 1513, 8, 6)
        assertHijri(2095, 6, 17, 1519, 2, 14)
        assertHijri(2100, 1, 1, 1523, 10, 20)
        assertHijri(2110, 10, 10, 1534, 11, 26)
        assertHijri(2120, 7, 7, 1544, 12, 11)
        assertHijri(2130, 3, 3, 1554, 11, 22)
        assertHijri(2140, 12, 25, 1566, 1, 15)
        assertHijri(2150, 9, 15, 1576, 1, 23)
        assertHijri(2160, 6, 6, 1586, 2, 1)
        assertHijri(2170, 2, 2, 1596, 1, 15)
        assertHijri(2173, 12, 7, 1600, 1, 1)
        assertHijri(2174, 11, 25, 1600, 12, 30)
    }

    // --- Round trips ---

    @Test
    fun hijriGregorianRoundTrips() {
        assertRoundTrip(1882, 11, 12)
        assertRoundTrip(1900, 1, 1)
        assertRoundTrip(1937, 3, 14)
        assertRoundTrip(1970, 1, 1)
        assertRoundTrip(1979, 11, 21)
        assertRoundTrip(2000, 4, 6)
        assertRoundTrip(2024, 3, 11)
        assertRoundTrip(2026, 9, 25)
        assertRoundTrip(2050, 6, 1)
        assertRoundTrip(2077, 1, 1)
        assertRoundTrip(2100, 1, 1)
        assertRoundTrip(2174, 11, 25)
    }

    // --- Range boundaries ---

    @Test
    fun firstAndLastSupportedDays() {
        // First day of AH 1300 and its Gregorian equivalent.
        assertHijri(1882, 11, 12, 1300, 1, 1)
        assertEquals(30, UmmAlQura.monthLength(1300, 1)) // 0x0AAA -> month 1 has 30 days
        assertGregorian(1300, 1, 1, 1882, 11, 12)
        // Last day of AH 1600 is the final supported day; the next Gregorian day is out of range.
        assertGregorian(1600, 12, 30, 2174, 11, 25)
        assertHijri(2174, 11, 25, 1600, 12, 30)
        assertNull(UmmAlQura.fromGregorian(2174, 11, 26))
    }

    @Test
    fun dayBeforeAndAfterRangeAreNull() {
        assertNull(UmmAlQura.fromGregorian(1882, 11, 11)) // one day before AH 1300
        assertNull(UmmAlQura.fromGregorian(2174, 11, 26)) // one day after AH 1600
    }

    @Test
    fun outOfRangeGregorianIsNull() {
        assertNull(UmmAlQura.fromGregorian(1800, 1, 1))
        assertNull(UmmAlQura.fromGregorian(2200, 1, 1))
        assertNull(UmmAlQura.fromGregorian(1299, 12, 31))
        assertNull(UmmAlQura.fromGregorian(1601, 1, 1))
        assertNull(UmmAlQura.fromGregorian(1882, 1, 1))
        assertNull(UmmAlQura.fromGregorian(2175, 1, 1))
    }

    @Test
    fun outOfRangeHijriIsNull() {
        assertNull(UmmAlQura.toGregorian(1299, 1, 1))
        assertNull(UmmAlQura.toGregorian(1601, 1, 1))
        assertNull(UmmAlQura.toGregorian(1299, 12, 29))
        assertNull(UmmAlQura.toGregorian(1601, 12, 1))
    }

    @Test
    fun monthLengthOutOfRangeIsZero() {
        assertEquals(0, UmmAlQura.monthLength(1299, 12))
        assertEquals(0, UmmAlQura.monthLength(1601, 1))
        assertEquals(0, UmmAlQura.monthLength(1445, 0))
        assertEquals(0, UmmAlQura.monthLength(1445, 13))
        assertEquals(0, UmmAlQura.monthLength(0, 1))
    }

    @Test
    fun invalidHijriDayIsNull() {
        assertEquals(30, UmmAlQura.monthLength(1445, 9))
        assertNull(UmmAlQura.toGregorian(1445, 9, 31)) // Ramadan 1445 has only 30 days
        assertNull(UmmAlQura.toGregorian(1445, 9, 0))
        assertNull(UmmAlQura.toGregorian(1445, 0, 1))
        assertNull(UmmAlQura.toGregorian(1445, 13, 1))
        assertNotNull(UmmAlQura.toGregorian(1445, 9, 30))
        // A 29-day month rejects its 30th day.
        assertEquals(29, UmmAlQura.monthLength(1446, 9))
        assertNull(UmmAlQura.toGregorian(1446, 9, 30))
    }

    // --- Year-length invariants ---

    @Test
    fun monthLengthsSumToYearLength() {
        assertYearLength(1300, 354, listOf(30, 29, 30, 29, 30, 29, 30, 29, 30, 29, 30, 29))
        assertYearLength(1301, 354, listOf(30, 30, 29, 30, 29, 30, 29, 30, 29, 30, 29, 29))
        assertYearLength(1350, 354, listOf(29, 30, 29, 30, 29, 30, 29, 29, 30, 30, 29, 30))
        assertYearLength(1400, 354, listOf(30, 29, 30, 29, 29, 30, 29, 30, 29, 30, 29, 30))
        assertYearLength(1420, 355, listOf(29, 30, 29, 29, 30, 29, 30, 30, 30, 30, 29, 30))
        assertYearLength(1430, 354, listOf(29, 30, 30, 29, 29, 30, 29, 30, 29, 30, 29, 30))
        assertYearLength(1440, 354, listOf(29, 30, 29, 30, 30, 30, 29, 30, 29, 30, 29, 29))
        assertYearLength(1444, 354, listOf(29, 30, 29, 30, 30, 29, 29, 30, 29, 30, 29, 30))
        assertYearLength(1445, 354, listOf(29, 30, 30, 30, 29, 30, 29, 29, 30, 29, 29, 30))
        assertYearLength(1446, 354, listOf(29, 30, 30, 30, 29, 30, 30, 29, 29, 30, 29, 29))
        assertYearLength(1447, 355, listOf(30, 29, 30, 30, 30, 29, 30, 29, 30, 29, 30, 29))
        assertYearLength(1450, 354, listOf(30, 29, 30, 29, 29, 30, 29, 30, 29, 30, 30, 29))
        assertYearLength(1475, 354, listOf(29, 30, 30, 29, 30, 30, 30, 29, 29, 30, 29, 29))
        assertYearLength(1500, 354, listOf(29, 30, 29, 30, 29, 29, 30, 29, 30, 29, 30, 30))
        assertYearLength(1525, 355, listOf(30, 30, 29, 30, 30, 29, 30, 29, 30, 29, 29, 30))
        assertYearLength(1550, 355, listOf(30, 29, 30, 29, 29, 29, 30, 30, 30, 29, 30, 30))
        assertYearLength(1575, 355, listOf(30, 30, 30, 29, 30, 30, 29, 30, 29, 29, 29, 30))
        assertYearLength(1590, 355, listOf(30, 29, 30, 30, 30, 29, 29, 30, 29, 30, 29, 30))
        assertYearLength(1599, 355, listOf(29, 30, 29, 30, 29, 30, 29, 30, 30, 30, 29, 30))
        assertYearLength(1600, 354, listOf(29, 29, 30, 29, 30, 29, 29, 30, 30, 30, 29, 30))
    }

    @Test
    fun everyYearLengthIs354Or355() {
        for (year in 1300..1600) {
            var sum = 0
            for (month in 1..12) sum += UmmAlQura.monthLength(year, month)
            assertTrue("year $year length $sum", sum == 354 || sum == 355)
        }
    }

    @Test
    fun everyMonthLengthIs29Or30() {
        for (year in 1300..1600) {
            for (month in 1..12) {
                val length = UmmAlQura.monthLength(year, month)
                assertTrue("$year-$month length $length", length == 29 || length == 30)
            }
        }
    }

    // --- Dhul-Hijjah (month 12) length varies between 29 and 30 ---

    @Test
    fun dhulHijjahLengthVaries() {
        val expected = mapOf(1300 to 29, 1301 to 29, 1325 to 30, 1350 to 30, 1375 to 29, 1400 to 30, 1420 to 30, 1430 to 30, 1440 to 29, 1444 to 30, 1445 to 30, 1446 to 29, 1447 to 29, 1450 to 29, 1475 to 29, 1500 to 30, 1525 to 30, 1550 to 30, 1575 to 30, 1590 to 30, 1598 to 29, 1599 to 30)
        for ((year, length) in expected) {
            assertEquals("Dhul-Hijjah $year", length, UmmAlQura.monthLength(year, 12))
        }
        assertTrue(expected.values.any { it == 29 })
        assertTrue(expected.values.any { it == 30 })
    }

    @Test
    fun lastDayOfYearIsTheDayBeforeNextNewYear() {
        assertYearEndsAt(1300, 1883, 10, 31, 1883, 11, 1)
        assertYearEndsAt(1301, 1884, 10, 19, 1884, 10, 20)
        assertYearEndsAt(1325, 1908, 2, 3, 1908, 2, 4)
        assertYearEndsAt(1350, 1932, 5, 6, 1932, 5, 7)
        assertYearEndsAt(1375, 1956, 8, 7, 1956, 8, 8)
        assertYearEndsAt(1400, 1980, 11, 8, 1980, 11, 9)
        assertYearEndsAt(1420, 2000, 4, 5, 2000, 4, 6)
        assertYearEndsAt(1430, 2009, 12, 17, 2009, 12, 18)
        assertYearEndsAt(1440, 2019, 8, 30, 2019, 8, 31)
        assertYearEndsAt(1444, 2023, 7, 18, 2023, 7, 19)
        assertYearEndsAt(1445, 2024, 7, 6, 2024, 7, 7)
        assertYearEndsAt(1446, 2025, 6, 25, 2025, 6, 26)
        assertYearEndsAt(1447, 2026, 6, 15, 2026, 6, 16)
        assertYearEndsAt(1450, 2029, 5, 13, 2029, 5, 14)
        assertYearEndsAt(1475, 2053, 8, 14, 2053, 8, 15)
        assertYearEndsAt(1500, 2077, 11, 16, 2077, 11, 17)
        assertYearEndsAt(1525, 2102, 2, 18, 2102, 2, 19)
        assertYearEndsAt(1550, 2126, 5, 23, 2126, 5, 24)
        assertYearEndsAt(1575, 2150, 8, 23, 2150, 8, 24)
        assertYearEndsAt(1590, 2165, 3, 13, 2165, 3, 14)
        assertYearEndsAt(1598, 2172, 12, 16, 2172, 12, 17)
        assertYearEndsAt(1599, 2173, 12, 6, 2173, 12, 7)
    }

    // --- Helpers ---

    private fun assertHijri(gy: Int, gm: Int, gd: Int, hy: Int, hm: Int, hd: Int) {
        assertEquals(
            "Gregorian $gy-$gm-$gd",
            HijriDate(hy, hm, hd),
            UmmAlQura.fromGregorian(gy, gm, gd),
        )
    }

    private fun assertGregorian(hy: Int, hm: Int, hd: Int, gy: Int, gm: Int, gd: Int) {
        val expected = intArrayOf(gy, gm, gd)
        val actual = UmmAlQura.toGregorian(hy, hm, hd)
        assertNotNull("Hijri $hy-$hm-$hd", actual)
        assertTrue(
            "Hijri $hy-$hm-$hd expected $gy-$gm-$gd but was ${actual!!.joinToString("-")}",
            expected.contentEquals(actual),
        )
    }

    /** The Gregorian date for a Hijri date must convert back to that same Hijri date. */
    private fun assertRoundTrip(gy: Int, gm: Int, gd: Int) {
        val hijri = UmmAlQura.fromGregorian(gy, gm, gd)
        assertNotNull("Gregorian $gy-$gm-$gd", hijri)
        val back = UmmAlQura.toGregorian(hijri!!.year, hijri.month, hijri.day)
        assertNotNull("back-conversion of $hijri", back)
        assertTrue(
            "round trip $gy-$gm-$gd -> $hijri -> ${back!!.joinToString("-")}",
            back[0] == gy && back[1] == gm && back[2] == gd,
        )
    }

    private fun assertYearLength(year: Int, expected: Int, monthLengths: List<Int>) {
        var sum = 0
        for (month in 1..12) {
            val length = UmmAlQura.monthLength(year, month)
            assertEquals("$year-$month", monthLengths[month - 1], length)
            sum += length
        }
        assertEquals("year $year", expected, sum)
    }

    /** Year [year] ends the day before [year] + 1 begins. */
    private fun assertYearEndsAt(
        year: Int,
        lastY: Int, lastM: Int, lastD: Int,
        nextY: Int, nextM: Int, nextD: Int,
    ) {
        val lastDay = UmmAlQura.monthLength(year, 12)
        assertGregorian(year, 12, lastDay, lastY, lastM, lastD)
        assertGregorian(year + 1, 1, 1, nextY, nextM, nextD)
        assertEquals(
            "year $year end vs ${year + 1} start",
            CivilDate(nextY, nextM, nextD),
            CivilDate(lastY, lastM, lastD).plusDays(1),
        )
    }
}
