package dev.rafa.waktusholat.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gregorian arithmetic, since every other calculation is expressed in epoch days. */
class CivilDateTest {

    @Test
    fun epochDayMatchesKnownDates() {
        assertEquals(0, CivilDate(1970, 1, 1).epochDay)
        assertEquals(-1, CivilDate(1969, 12, 31).epochDay)
        assertEquals(19_723, CivilDate(2024, 1, 1).epochDay)
        assertEquals(19_814, CivilDate(2024, 4, 1).epochDay)
    }

    @Test
    fun epochDayRoundTripsAcrossTwoCenturies() {
        var date = CivilDate(1900, 1, 1)
        repeat(80_000) {
            assertEquals("round trip failed for $date", date, CivilDate.fromEpochDay(date.epochDay))
            date = date.plusDays(1)
        }
    }

    @Test
    fun leapYearsFollowTheGregorianRule() {
        assertTrue(CivilDate.isLeapYear(2000))
        assertTrue(CivilDate.isLeapYear(2024))
        assertTrue(!CivilDate.isLeapYear(1900))
        assertTrue(!CivilDate.isLeapYear(2023))
        assertEquals(29, CivilDate.daysInMonth(2024, 2))
        assertEquals(28, CivilDate.daysInMonth(1900, 2))
        assertEquals(366, CivilDate.daysInYear(2024))
        assertEquals(365, CivilDate.daysInYear(1900))
    }

    @Test
    fun dayOfWeekUsesIsoNumbering() {
        // 2026-09-26 is a Saturday; ISO numbering puts Monday at 1 and Sunday at 7.
        assertEquals(6, CivilDate(2026, 9, 26).dayOfWeek)
        assertEquals(1, CivilDate(2026, 9, 21).dayOfWeek)
        assertEquals(7, CivilDate(2026, 9, 27).dayOfWeek)
        assertTrue(CivilDate(2026, 9, 26).isDayOfWeek(6))
    }

    @Test
    fun plusDaysCrossesMonthAndYearBoundaries() {
        assertEquals(CivilDate(2027, 1, 1), CivilDate(2026, 12, 31).plusDays(1))
        assertEquals(CivilDate(2026, 3, 1), CivilDate(2026, 2, 28).plusDays(1))
        assertEquals(CivilDate(2024, 2, 29), CivilDate(2024, 2, 28).plusDays(1))
        assertEquals(CivilDate(2026, 9, 26), CivilDate(2026, 9, 26).plusDays(0))
        assertEquals(CivilDate(2026, 9, 25), CivilDate(2026, 9, 26).plusDays(-1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsImpossibleDay() {
        CivilDate(2026, 2, 30)
    }

    @Test
    fun formatsWithZeroPadding() {
        assertEquals("2026-09-05", CivilDate(2026, 9, 5).toString())
        assertEquals("2026-12-25", CivilDate(2026, 12, 25).toString())
    }
}

/** Fixed-offset wall-clock conversion used by the countdown and the alarm scheduler. */
class LocalClockTest {

    /** 2026-09-26T04:26:00Z, i.e. 11:26 WIB. */
    private val instant = 1_790_396_760_000L

    @Test
    fun minuteOfDayConvertsToCityWallClock() {
        // 04:26 UTC is 11:26 in WIB (UTC+7), 12:26 in WITA (UTC+8), 13:26 in WIT (UTC+9).
        assertEquals(11 * 60 + 26, LocalClock.minuteOfDay(instant, 420))
        assertEquals(12 * 60 + 26, LocalClock.minuteOfDay(instant, 480))
        assertEquals(13 * 60 + 26, LocalClock.minuteOfDay(instant, 540))
    }

    @Test
    fun dateAtUsesTheCityZone() {
        // 18:00 UTC on the 25th is already the 26th in every Indonesian zone.
        val evening = 1_790_359_200_000L
        assertEquals(CivilDate(2026, 9, 26), LocalClock.dateAt(evening, 420))
        assertEquals(CivilDate(2026, 9, 26), LocalClock.dateAt(evening, 540))
        // 16:00 UTC on the 25th is still the 25th at UTC+7 but the 26th at UTC+9.
        val afternoon = 1_790_352_000_000L
        assertEquals(CivilDate(2026, 9, 25), LocalClock.dateAt(afternoon, 420))
        assertEquals(CivilDate(2026, 9, 26), LocalClock.dateAt(afternoon, 540))
    }

    @Test
    fun minuteOfDayAndDateAlwaysAgree() {
        // Sweep a day and a half in five-minute steps; the pair must stay consistent.
        var millis = instant - 6 * 3_600_000L
        repeat(430) {
            val minute = LocalClock.minuteOfDay(millis, 480)
            val date = LocalClock.dateAt(millis, 480)
            assertTrue("minute must be in range", minute in 0..1439)
            // Adding the minute back to local midnight must return the same instant.
            val reconstructed = (date.epochDay * 1440L + minute - 8 * 60L) * 60_000L
            assertEquals("instant must round-trip", millis, reconstructed)
            millis += 300_000L
        }
    }

    @Test
    fun millisUntilNextDayCountsDownToLocalMidnight() {
        // 16:00 UTC is 23:00 WIB, so one hour remains.
        val millis = 1_790_352_000_000L
        assertEquals(3_600_000L, LocalClock.millisUntilNextDay(millis, 420))
        // At 17:00 UTC it is exactly midnight in WIB, so a full day remains.
        assertEquals(86_400_000L, LocalClock.millisUntilNextDay(millis + 3_600_000L, 420))
    }
}

/** The countdown model shared by the home screen and the widget. */
class PrayerMomentTest {

    private val jakarta = City("Jakarta", "DKI Jakarta", -6.2088, 106.8456, 7)
    private val date = CivilDate(2026, 9, 26)

    private fun times(): PrayerTimes = prayerTimesFor(jakarta, date)

    @Test
    fun picksTheNextPrayerStrictlyAfterTheCurrentMinute() {
        val schedule = times()
        val fajr = schedule[Prayer.FAJR]

        // Exactly at Fajr, Fajr itself is current and Dhuhr is next.
        val atFajr = PrayerMoment.at(schedule, null, fajr, 0)
        assertEquals(Prayer.FAJR, atFajr.prayer)
        assertEquals(Prayer.DHUHR, atFajr.next)

        // One minute before Fajr, Fajr is still the target.
        val beforeFajr = PrayerMoment.at(schedule, null, fajr - 1, 30)
        assertEquals(Prayer.FAJR, beforeFajr.next)
        assertEquals(1, beforeFajr.minutesRemaining)
        assertEquals(30, beforeFajr.secondsRemaining)
    }

    @Test
    fun rollsOverToFajrAfterIsha() {
        val schedule = times()
        val tomorrow = prayerTimesFor(jakarta, date.plusDays(1))
        val afterIsha = schedule[Prayer.ISHA] + 5

        val moment = PrayerMoment.at(schedule, tomorrow, afterIsha, 12)
        assertEquals(Prayer.ISHA, moment.prayer)
        assertEquals(Prayer.FAJR, moment.next)
        assertEquals(tomorrow[Prayer.FAJR], moment.nextMinute)
        assertEquals(1440 - afterIsha + tomorrow[Prayer.FAJR], moment.minutesRemaining)

        // Without tomorrow's schedule the prayer stays Isha and no countdown is offered.
        val degraded = PrayerMoment.at(schedule, null, afterIsha, 12)
        assertNull(degraded.next)
        assertNull(degraded.minutesRemaining)
    }

    @Test
    fun countdownTextSwitchesToHoursOnlyWhenNeeded() {
        val schedule = times()
        val moment = PrayerMoment.at(schedule, null, schedule[Prayer.FAJR] - 95, 7)
        assertEquals("01:35:07", moment.countdownText())

        val short = PrayerMoment.at(schedule, null, schedule[Prayer.FAJR] - 5, 7)
        assertEquals("05:07", short.countdownText())
    }

    @Test
    fun progressSweepsFromZeroToOneAcrossTheInterval() {
        val schedule = times()
        val from = schedule[Prayer.ASR]
        val target = schedule[Prayer.MAGHRIB]

        val atStart = PrayerMoment.at(schedule, null, from, 0)
        assertEquals(0f, atStart.progress(from, schedule), 0.001f)

        val midway = PrayerMoment.at(schedule, null, (from + target) / 2, 0)
        assertEquals(0.5f, midway.progress(from, schedule), 0.02f)

        // Just before the target the bar is essentially full, and it never exceeds one.
        var previous = -1f
        for (minute in from until target) {
            val progress = PrayerMoment.at(schedule, null, minute, 0).progress(from, schedule)
            assertTrue("progress must stay within bounds at $minute: $progress", progress in 0f..1f)
            assertTrue("progress must not go backwards at $minute", progress >= previous)
            previous = progress
        }
        assertTrue("bar should be nearly full one minute before the target: $previous", previous > 0.98f)
    }

    @Test
    fun everyMinuteOfTheDayResolvesToAPrayer() {
        val schedule = times()
        val tomorrow = prayerTimesFor(jakarta, date.plusDays(1))
        for (minute in 0..1439) {
            val moment = PrayerMoment.at(schedule, tomorrow, minute, 0)
            assertTrue("minute $minute must have a next prayer", moment.hasNext)
            assertTrue(
                "minute $minute must not count down past the next day",
                (moment.minutesRemaining ?: 0) in 1..1440,
            )
        }
    }
}

/** Snapping a raw solar time onto the published whole minute. */
class TuningTest {

    @Test
    fun kemenagRoundsUpThenAddsTheIhtiyatiMargin() {
        val tuning = Tuning.KEMENAG
        // Fajr and the other obligatory times are rounded up, then shifted later.
        assertEquals(261, tuning.apply(259.0, Prayer.FAJR))
        assertEquals(262, tuning.apply(259.4, Prayer.FAJR))
        assertEquals(263, tuning.apply(260.2, Prayer.FAJR))
        // An exact minute is not pushed up by the ceiling.
        assertEquals(262, tuning.apply(260.0, Prayer.FAJR))
        // Sunrise and Maghrib are rounded to the nearest minute and pulled three minutes earlier.
        assertEquals(343, tuning.apply(345.6, Prayer.SUNRISE))
        assertEquals(347, tuning.apply(350.2, Prayer.SUNRISE))
        assertEquals(1070, tuning.apply(1067.2, Prayer.MAGHRIB))
        // Dhuhr carries the larger three-minute margin; the ceiling still applies first.
        assertEquals(723, tuning.apply(719.5, Prayer.DHUHR))
        assertEquals(723, tuning.apply(719.1, Prayer.DHUHR))
        assertEquals(722, tuning.apply(719.0, Prayer.DHUHR))
        // Isha and Asr behave like Fajr.
        assertEquals(1139, tuning.apply(1136.4, Prayer.ISHA))
        assertEquals(894, tuning.apply(891.6, Prayer.ASR))
    }

    @Test
    fun noTuningRoundsToTheNearestMinuteOnly() {
        assertEquals(259, Tuning.NONE.apply(259.4, Prayer.FAJR))
        assertEquals(260, Tuning.NONE.apply(259.6, Prayer.FAJR))
        assertEquals(261, Tuning.NONE.apply(260.5, Prayer.FAJR))
        assertEquals(259, Tuning.NONE.apply(259.0, Prayer.FAJR))
    }

    @Test
    fun marginNeverMovesAPrayerEarlierThanItsAstronomicalInstant() {
        // The ihtiyati rule exists so a published time is never before the real one.
        val tuning = Tuning.KEMENAG
        var value = 0.0
        while (value < 1440.0) {
            for (prayer in Prayer.OBLIGATORY) {
                val published = tuning.apply(value, prayer)
                assertTrue(
                    "$prayer at raw $value published $published is earlier than the event",
                    published >= value.toInt(),
                )
            }
            value += 0.37
        }
    }
}

/** Nearest-city lookup, which is what turns a GPS fix into coordinates and a time zone. */
class CityTest {

    @Test
    fun nearestFindsTheCityItself() {
        for (city in City.ALL.take(40)) {
            val nearest = City.nearest(city.latitude, city.longitude)
            assertEquals("lookup for ${city.label}", city.name, nearest.name)
        }
    }

    @Test
    fun nearestResolvesTimeZoneForAGpsFix() {
        // A point in south Jakarta keeps WIB; one near Makassar keeps WITA.
        assertEquals(7, City.nearest(-6.30, 106.80).timeZoneHours)
        assertEquals(8, City.nearest(-5.20, 119.40).timeZoneHours)
        assertEquals(9, City.nearest(-2.60, 140.60).timeZoneHours)
    }

    @Test
    fun coordinatesAcrossIndonesiaAreServiceable() {
        val places = listOf(
            Triple(-6.2088, 106.8456, "Jakarta"),
            Triple(5.5483, 95.3238, "Banda Aceh"),
            Triple(-10.1772, 123.6070, "Kupang"),
            Triple(-2.5916, 140.6690, "Jayapura"),
            Triple(3.5952, 98.6722, "Medan"),
            Triple(-8.6705, 115.2126, "Denpasar"),
        )
        for ((latitude, longitude, where) in places) {
            assertTrue("$where must be serviceable", City.isServiceable(latitude, longitude))
        }
    }

    @Test
    fun everyCitySitsInsideIndonesiaWithAValidZone() {
        assertTrue("city table is unexpectedly small: ${City.ALL.size}", City.ALL.size >= 400)
        for (city in City.ALL) {
            assertTrue(
                "${city.label} is not serviceable: ${city.latitude},${city.longitude}",
                City.isServiceable(city.latitude, city.longitude),
            )
            assertTrue("${city.label} has zone ${city.timeZoneHours}", city.timeZoneHours in 7..9)
            assertTrue("${city.label} has an empty name", city.name.isNotBlank())
            assertTrue("${city.label} has an empty province", city.province.isNotBlank())
        }
    }

    @Test
    fun cityNamesAreUniqueWithinAProvince() {
        val duplicates = City.ALL.groupBy { it.name to it.province }.filterValues { it.size > 1 }
        assertTrue("duplicate city entries: ${duplicates.keys}", duplicates.isEmpty())
    }

    @Test
    fun namesCarryNoDisambiguatingSuffix() {
        // The province is its own field and is already shown next to the name, so a name must never
        // repeat it. This also catches the kota/kabupaten pairing bug: both were once listed under
        // the same province, distinguished only by a parenthesised suffix.
        for (city in City.ALL) {
            assertTrue("${city.label} repeats its province in the name", '(' !in city.name)
            assertTrue("${city.label} has a stray suffix", !city.name.endsWith(")"))
        }
    }

    @Test
    fun namesAreDistinctWithinEveryProvinceAcrossTheWholeTable() {
        // Equal names in different provinces are legitimate (Banjar exists in two), so the real
        // invariant is per province, checked through the same key the picker displays.
        val seen = mutableMapOf<Pair<String, String>, City>()
        for (city in City.ALL) {
            val key = city.name to city.province
            val previous = seen.put(key, city)
            assertTrue("${city.label} duplicates ${previous?.label}", previous == null)
        }
    }

    @Test
    fun zoneLabelMatchesTheOffset() {
        assertEquals("WIB", City("Jakarta", "", -6.2, 106.8, 7).zoneLabel)
        assertEquals("WITA", City("Makassar", "", -5.1, 119.4, 8).zoneLabel)
        assertEquals("WIT", City("Jayapura", "", -2.6, 140.7, 9).zoneLabel)
    }

    @Test
    fun provincesCoverTheWholeCountry() {
        val provinces = City.PROVINCES
        assertTrue("expected at least 30 provinces, got ${provinces.size}", provinces.size >= 30)
        for (expected in listOf("DKI Jakarta", "Jawa Barat", "Papua", "Sulawesi Selatan")) {
            assertTrue("missing province $expected", expected in provinces)
        }
    }
}

/** Calculation methods and madhabs, which are user-selectable and must stay stable across builds. */
class CalculationMethodTest {

    @Test
    fun idsMatchTheAladhanConvention() {
        assertEquals(20, CalculationMethod.KEMENAG.id)
        assertEquals(11, CalculationMethod.SINGAPORE.id)
        assertEquals(4, CalculationMethod.MAKKAH.id)
        assertEquals(3, CalculationMethod.MWL.id)
    }

    @Test
    fun unknownIdFallsBackToTheDefault() {
        assertEquals(CalculationMethod.DEFAULT, CalculationMethod.fromId(1234))
        assertEquals(CalculationMethod.KEMENAG, CalculationMethod.fromId(20))
    }

    @Test
    fun ummAlQuraUsesAFixedIntervalInsteadOfAnAngle() {
        val config = CalculationMethod.MAKKAH.config
        assertEquals(90, config.ishaIntervalMinutes)
        assertEquals(0.0, config.ishaAngle, 0.0)
    }

    @Test
    fun hanafiDoublesTheAsrShadowFactor() {
        assertEquals(1.0, Madhab.SHAFII.asrFactor, 0.0)
        assertEquals(2.0, Madhab.HANAFI.asrFactor, 0.0)
        assertEquals(Madhab.DEFAULT, Madhab.fromId(99))
        assertEquals(Madhab.HANAFI, Madhab.fromId(1))
    }

    @Test
    fun ianaZoneHonoursHalfHourOffsetsAndDaylightSaving() {
        val delhi = City("New Delhi", "", 28.6139, 77.2090, 0, "Asia/Kolkata")
        assertEquals(330, delhi.offsetMinutesOn(CivilDate(2026, 1, 15)))
        assertEquals("UTC+5:30", City.utcLabel(330))

        val london = City("London", "", 51.5074, -0.1278, 0, "Europe/London")
        assertEquals(0, london.offsetMinutesOn(CivilDate(2026, 1, 15)))
        assertEquals(60, london.offsetMinutesOn(CivilDate(2026, 7, 15)))

        // Solar noon in London sits near 12:00 GMT in winter and 13:00 BST in summer.
        val winter = prayerTimesFor(london, CivilDate(2026, 1, 15), CalculationMethod.MWL)[Prayer.DHUHR]
        val summer = prayerTimesFor(london, CivilDate(2026, 7, 15), CalculationMethod.MWL)[Prayer.DHUHR]
        assertTrue("winter Dhuhr ${PrayerTimes.format(winter)}", winter in 12 * 60..12 * 60 + 20)
        assertTrue("summer Dhuhr ${PrayerTimes.format(summer)}", summer in 13 * 60..13 * 60 + 20)
    }

    @Test
    fun indonesianZonesKeepTheirLocalAbbreviation() {
        assertEquals("WIB", City("Bandung", "", -6.9, 107.6, 0, "Asia/Jakarta").zoneLabel)
        assertEquals("WITA", City("Makassar", "", -5.1, 119.4, 0, "Asia/Makassar").zoneLabel)
        assertEquals("WIT", City("Jayapura", "", -2.5, 140.7, 0, "Asia/Jayapura").zoneLabel)
    }

    @Test
    fun everyMethodProducesACompleteOrderedDay() {
        val city = City("Jakarta", "DKI Jakarta", -6.2088, 106.8456, 7)
        for (method in CalculationMethod.entries) {
            val times = prayerTimesFor(city, CivilDate(2026, 9, 26), method)
            for (prayer in Prayer.entries) {
                assertTrue("$method $prayer is not a wall clock time", times[prayer] in 0..1439)
            }
            assertTrue("$method: Fajr must precede sunrise", times[Prayer.FAJR] < times[Prayer.SUNRISE])
            assertTrue("$method: sunrise must precede Dhuhr", times[Prayer.SUNRISE] < times[Prayer.DHUHR])
            assertTrue("$method: Dhuhr must precede Asr", times[Prayer.DHUHR] < times[Prayer.ASR])
            assertTrue("$method: Asr must precede Maghrib", times[Prayer.ASR] < times[Prayer.MAGHRIB])
            assertTrue("$method: Maghrib must precede Isha", times[Prayer.MAGHRIB] < times[Prayer.ISHA])
        }
    }
}
