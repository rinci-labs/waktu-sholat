package dev.rafa.waktusholat.core

/**
 * Exact Umm al-Qura (Saudi) Hijri <-> Gregorian conversion: dependency-free, pure integer math,
 * no `java.time` and no `java.util.Calendar`, so it runs on Android API 24 without desugaring.
 *
 * The month-length table and the epoch/estimate constants are transcribed verbatim from ICU4C's
 * `IslamicUmalquraCalendar`, so results match `HijrahChronology.INSTITUTIONAL` (the JDK/Android
 * implementation backed by the same data).
 *
 * Source: https://raw.githubusercontent.com/unicode-org/icu/main/icu4c/source/i18n/islamcal.cpp
 * Revision: commit 59af52bb6f967bc5061bc3f6a91063de88e4aa78 (2025-02-11), branch `main`.
 * Transcribed declarations: `UMALQURA_YEAR_START` (1300), `UMALQURA_YEAR_END` (1600),
 * `UMALQURA_MONTHLENGTH[]` (301 entries), `umAlQuraYrStartEstimateFix[]` (301 entries),
 * `CIVIL_EPOC` (1948440), and the `yearStart()` estimate `354.36720 * n + 460322.05`.
 *
 * Supported range: AH 1300..1600 inclusive; outside it the API returns null (or 0 for
 * [monthLength]).
 */
object UmmAlQura {

    /** First supported Hijri year (ICU `UMALQURA_YEAR_START`). */
    private const val YEAR_START = 1300

    /** Last supported Hijri year (ICU `UMALQURA_YEAR_END`). */
    private const val YEAR_END = 1600

    /**
     * Julian day number of the Islamic epoch: Friday 622-07-16 in the Julian calendar
     * (ICU `CIVIL_EPOC`).
     */
    private const val CIVIL_EPOC = 1948440

    /**
     * ICU `UMALQURA_MONTHLENGTH`: one entry per Hijri year 1300..1600. The low 12 bits are the
     * month lengths for months 1..12, MSB-first, where a set bit means 30 days and a clear bit
     * means 29 days.
     */
    private val MONTH_LENGTHS = intArrayOf(
        0x0AAA, 0x0D54, 0x0EC9, 0x06D4, 0x06EA, 0x036C, 0x0AAD, 0x0555, 0x06A9, 0x0792, 0x0BA9, 0x05D4,
        0x0ADA, 0x055C, 0x0D2D, 0x0695, 0x074A, 0x0B54, 0x0B6A, 0x05AD, 0x04AE, 0x0A4F, 0x0517, 0x068B,
        0x06A5, 0x0AD5, 0x02D6, 0x095B, 0x049D, 0x0A4D, 0x0D26, 0x0D95, 0x05AC, 0x09B6, 0x02BA, 0x0A5B,
        0x052B, 0x0A95, 0x06CA, 0x0AE9, 0x02F4, 0x0976, 0x02B6, 0x0956, 0x0ACA, 0x0BA4, 0x0BD2, 0x05D9,
        0x02DC, 0x096D, 0x054D, 0x0AA5, 0x0B52, 0x0BA5, 0x05B4, 0x09B6, 0x0557, 0x0297, 0x054B, 0x06A3,
        0x0752, 0x0B65, 0x056A, 0x0AAB, 0x052B, 0x0C95, 0x0D4A, 0x0DA5, 0x05CA, 0x0AD6, 0x0957, 0x04AB,
        0x094B, 0x0AA5, 0x0B52, 0x0B6A, 0x0575, 0x0276, 0x08B7, 0x045B, 0x0555, 0x05A9, 0x05B4, 0x09DA,
        0x04DD, 0x026E, 0x0936, 0x0AAA, 0x0D54, 0x0DB2, 0x05D5, 0x02DA, 0x095B, 0x04AB, 0x0A55, 0x0B49,
        0x0B64, 0x0B71, 0x05B4, 0x0AB5, 0x0A55, 0x0D25, 0x0E92, 0x0EC9, 0x06D4, 0x0AE9, 0x096B, 0x04AB,
        0x0A93, 0x0D49, 0x0DA4, 0x0DB2, 0x0AB9, 0x04BA, 0x0A5B, 0x052B, 0x0A95, 0x0B2A, 0x0B55, 0x055C,
        0x04BD, 0x023D, 0x091D, 0x0A95, 0x0B4A, 0x0B5A, 0x056D, 0x02B6, 0x093B, 0x049B, 0x0655, 0x06A9,
        0x0754, 0x0B6A, 0x056C, 0x0AAD, 0x0555, 0x0B29, 0x0B92, 0x0BA9, 0x05D4, 0x0ADA, 0x055A, 0x0AAB,
        0x0595, 0x0749, 0x0764, 0x0BAA, 0x05B5, 0x02B6, 0x0A56, 0x0E4D, 0x0B25, 0x0B52, 0x0B6A, 0x05AD,
        0x02AE, 0x092F, 0x0497, 0x064B, 0x06A5, 0x06AC, 0x0AD6, 0x055D, 0x049D, 0x0A4D, 0x0D16, 0x0D95,
        0x05AA, 0x05B5, 0x02DA, 0x095B, 0x04AD, 0x0595, 0x06CA, 0x06E4, 0x0AEA, 0x04F5, 0x02B6, 0x0956,
        0x0AAA, 0x0B54, 0x0BD2, 0x05D9, 0x02EA, 0x096D, 0x04AD, 0x0A95, 0x0B4A, 0x0BA5, 0x05B2, 0x09B5,
        0x04D6, 0x0A97, 0x0547, 0x0693, 0x0749, 0x0B55, 0x056A, 0x0A6B, 0x052B, 0x0A8B, 0x0D46, 0x0DA3,
        0x05CA, 0x0AD6, 0x04DB, 0x026B, 0x094B, 0x0AA5, 0x0B52, 0x0B69, 0x0575, 0x0176, 0x08B7, 0x025B,
        0x052B, 0x0565, 0x05B4, 0x09DA, 0x04ED, 0x016D, 0x08B6, 0x0AA6, 0x0D52, 0x0DA9, 0x05D4, 0x0ADA,
        0x095B, 0x04AB, 0x0653, 0x0729, 0x0762, 0x0BA9, 0x05B2, 0x0AB5, 0x0555, 0x0B25, 0x0D92, 0x0EC9,
        0x06D2, 0x0AE9, 0x056B, 0x04AB, 0x0A55, 0x0D29, 0x0D54, 0x0DAA, 0x09B5, 0x04BA, 0x0A3B, 0x049B,
        0x0A4D, 0x0AAA, 0x0AD5, 0x02DA, 0x095D, 0x045E, 0x0A2E, 0x0C9A, 0x0D55, 0x06B2, 0x06B9, 0x04BA,
        0x0A5D, 0x052D, 0x0A95, 0x0B52, 0x0BA8, 0x0BB4, 0x05B9, 0x02DA, 0x095A, 0x0B4A, 0x0DA4, 0x0ED1,
        0x06E8, 0x0B6A, 0x056D, 0x0535, 0x0695, 0x0D4A, 0x0DA8, 0x0DD4, 0x06DA, 0x055B, 0x029D, 0x062B,
        0x0B15, 0x0B4A, 0x0B95, 0x05AA, 0x0AAE, 0x092E, 0x0C8F, 0x0527, 0x0695, 0x06AA, 0x0AD6, 0x055D,
        0x029D,
    )

    /**
     * ICU `umAlQuraYrStartEstimateFix`: correction added to the linear estimate of each year's
     * start day, one entry per Hijri year 1300..1600.
     */
    private val YEAR_START_FIX = intArrayOf(
        0, 0, -1, 0, -1, 0, 0, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0, 0, -1, 0,
        1, 0, 1, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0,
        0, 0, 1, 0, 0, -1, -1, 0, 0, 0, 1, 0, 0, -1, 0, 0, 0, 1, 1, 0,
        0, 0, 0, 0, 0, 0, 0, -1, 0, 0, 0, 1, 1, 0, 0, -1, 0, 1, 0, 1,
        1, 0, 0, -1, 0, 1, 0, 0, 0, -1, 0, 1, 0, 1, 0, 0, 0, -1, 0, 0,
        0, 0, -1, -1, 0, -1, 0, 1, 0, 0, 0, -1, 0, 0, 0, 1, 0, 0, 0, 0,
        0, 1, 0, 0, -1, -1, 0, 0, 0, 1, 0, 0, -1, -1, 0, -1, 0, 0, -1, -1,
        0, -1, 0, -1, 0, 0, -1, -1, 0, 0, 0, 0, 0, 0, -1, 0, 1, 0, 1, 1,
        0, 0, -1, 0, 1, 0, 0, 0, 0, 0, 1, 0, 1, 0, 0, 0, -1, 0, 1, 0,
        0, -1, -1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0,
        1, 0, 0, -1, 0, 0, 0, 1, 1, 0, 0, -1, 0, 1, 0, 1, 1, 0, 0, 0,
        0, 1, 0, 0, 0, -1, 0, 0, 0, 1, 0, 0, 0, -1, 0, 0, 0, 0, 0, -1,
        0, -1, 0, 1, 0, 0, 0, -1, 0, 1, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0,
        -1, 0, 0, 0, 0, 1, 0, 0, 0, -1, 0, 0, 0, 0, -1, -1, 0, -1, 0, 1,
        0, 0, -1, -1, 0, 0, 1, 1, 0, 0, -1, 0, 0, 0, 0, 1, 0, 0, 0, 0,
        1,
    )

    /** Day number since the epoch on which AH 1300 begins, i.e. `yearStartDay(1300)`. */
    private const val RANGE_START_DAY = 460322

    /** One past the last supported day: AH 1600 has 354 days and starts on day 566633. */
    private const val RANGE_END_DAY = 566987

    /**
     * Number of days (29 or 30) in the given Hijri month, or 0 if the year/month is out of range.
     *
     * @param hijriYear Hijri year, supported range 1300..1600
     * @param hijriMonth Hijri month, 1..12
     */
    fun monthLength(hijriYear: Int, hijriMonth: Int): Int {
        if (hijriYear !in YEAR_START..YEAR_END || hijriMonth !in 1..12) return 0
        val bits = MONTH_LENGTHS[hijriYear - YEAR_START]
        return 29 + ((bits shr (12 - hijriMonth)) and 1)
    }

    /**
     * Hijri date for a proleptic Gregorian calendar date (no timezone).
     * Returns null when the date falls outside AH 1300..1600.
     */
    fun fromGregorian(year: Int, month: Int, day: Int): HijriDate? {
        val days = julianDay(year, month, day) - CIVIL_EPOC
        if (days < RANGE_START_DAY || days >= RANGE_END_DAY) return null
        // Largest supported year whose start day is <= days.
        var lo = YEAR_START
        var hi = YEAR_END
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (yearStartDay(mid) <= days) lo = mid else hi = mid - 1
        }
        var remaining = days - yearStartDay(lo)
        var hijriMonth = 1
        while (true) {
            val length = monthLength(lo, hijriMonth)
            if (remaining < length) break
            remaining -= length
            hijriMonth++
        }
        return HijriDate(lo, hijriMonth, remaining + 1)
    }

    /**
     * Gregorian (year, month, day) for a Hijri date, month 1..12.
     * Returns null when the date is outside AH 1300..1600 or the day is invalid for the month.
     */
    fun toGregorian(hijriYear: Int, hijriMonth: Int, hijriDay: Int): IntArray? {
        if (hijriYear !in YEAR_START..YEAR_END || hijriMonth !in 1..12) return null
        if (hijriDay !in 1..monthLength(hijriYear, hijriMonth)) return null
        var dayOfYear = hijriDay - 1
        for (m in 1 until hijriMonth) dayOfYear += monthLength(hijriYear, m)
        return gregorianFromJulianDay(yearStartDay(hijriYear) + dayOfYear + CIVIL_EPOC)
    }

    // --- Umm al-Qura internals ---

    /**
     * Day number since the Islamic epoch on which the given Hijri year starts.
     *
     * ICU computes `trunc(354.36720 * n + 460322.05 + 0.5) + umAlQuraYrStartEstimateFix[n]`.
     * The same value is produced exactly with integers as
     * `floor((3543672 * n + 4603220500 + 5000) / 10000) + fix[n]`, where `n = year - 1300`.
     */
    private fun yearStartDay(hijriYear: Int): Int {
        val n = hijriYear - YEAR_START
        val estimate = (3543672L * n + 4603220500L + 5000L) / 10000L
        return estimate.toInt() + YEAR_START_FIX[n]
    }

    // --- Julian day arithmetic (Howard Hinnant's algorithms, integer only) ---

    /** Proleptic Gregorian date to Julian day number. */
    private fun julianDay(year: Int, month: Int, day: Int): Int {
        val a = (14 - month) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3
        return day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
    }

    /** Julian day number to proleptic Gregorian (year, month, day). */
    private fun gregorianFromJulianDay(julianDay: Int): IntArray {
        val a = julianDay + 32044
        val b = (4 * a + 3) / 146097
        val c = a - (146097 * b) / 4
        val d = (4 * c + 3) / 1461
        val e = c - (1461 * d) / 4
        val m = (5 * e + 2) / 153
        return intArrayOf(
            100 * b + d - 4800 + m / 10,
            m + 3 - 12 * (m / 10),
            e - (153 * m + 2) / 5 + 1,
        )
    }
}
