package dev.rafa.waktusholat.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Locks the solar calculation to published reference output.
 *
 * The expected values are captured verbatim from live APIs rather than recomputed from this code:
 *  - [ALADHAN] rows come from `api.aladhan.com/v1/timings` with `method=20` and an explicit
 *    `timezone`, i.e. the Kemenag convention exposed without any local tuning.
 *  - [KEMENAG] rows come from `api.myquran.com/v2/sholat/jadwal`, the schedule Indonesian users
 *    actually read, which adds rounding plus the ihtiyati margins modelled by [Tuning.KEMENAG].
 *
 * Agreement is asserted to the published minute with a one-minute tolerance, because the reference
 * services round slightly differently and Asr is convention-dependent.
 */
class PrayerCalculatorTest {

    private class Sample(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val timeZoneHours: Int,
        val year: Int,
        val month: Int,
        val day: Int,
        val expected: IntArray,
    )

    private fun city(sample: Sample) =
        City(sample.name, "", sample.latitude, sample.longitude, sample.timeZoneHours)

    private fun date(sample: Sample) = CivilDate(sample.year, sample.month, sample.day)

    @Test
    fun matchesAladhanReferenceTimings() {
        // Aladhan applies no local margin: its output is the raw solar time on the nearest minute.
        // Asr is allowed an extra minute because Aladhan's Asr convention sits slightly earlier than
        // the standard shadow-factor formula this app uses; every other entry agrees to the minute.
        assertAgainstReference(ALADHAN, Tuning.NONE, tolerance = { if (it == Prayer.ASR) 2 else 1 })
    }

    @Test
    fun matchesPublishedKemenagSchedule() {
        // The published Indonesian schedule adds round-up plus the ihtiyati margins.
        assertAgainstReference(KEMENAG, Tuning.KEMENAG, tolerance = { 1 })
    }

    private fun assertAgainstReference(
        samples: List<Sample>,
        tuning: Tuning,
        tolerance: (Prayer) -> Int,
    ) {
        for (sample in samples) {
            val times = prayerTimesFor(
                city = city(sample),
                date = date(sample),
                method = CalculationMethod.KEMENAG,
                tuning = tuning,
            )
            for (prayer in Prayer.entries) {
                val expected = sample.expected[prayer.ordinal]
                val actual = times[prayer]
                val allowed = tolerance(prayer)
                assertTrue(
                    "${sample.name} ${sample.year}-${sample.month}-${sample.day} ${prayer.name}: " +
                        "reference ${PrayerTimes.format(expected)}, calculated ${PrayerTimes.format(actual)} " +
                        "(allowed ${allowed}min)",
                    abs(expected - actual) <= allowed,
                )
            }
        }
    }

    @Test
    fun entriesAreStrictlyOrderedEveryDayOfAYear() {
        for (day in 1..365) {
            for (sample in KEMENAG) {
                val times = prayerTimesFor(city(sample), CivilDate(sample.year, 1, 1).plusDays(day - 1))
                val ordered = Prayer.entries.map { times[it] }
                for (i in 1 until ordered.size) {
                    assertTrue(
                        "${sample.name} day $day: ${Prayer.entries[i]} must follow " +
                            "${Prayer.entries[i - 1]} in $times",
                        ordered[i] > ordered[i - 1],
                    )
                }
            }
        }
    }

    @Test
    fun hanafiMadhabMovesAsrLaterAndLeavesOthers() {
        val jakarta = City("Jakarta", "DKI Jakarta", -6.2088, 106.8456, 7)
        val day = CivilDate(2026, 9, 26)
        val shafii = prayerTimesFor(jakarta, day, madhab = Madhab.SHAFII)
        val hanafi = prayerTimesFor(jakarta, day, madhab = Madhab.HANAFI)

        assertTrue("Hanafi Asr must be later", hanafi[Prayer.ASR] > shafii[Prayer.ASR])
        for (prayer in Prayer.entries) {
            if (prayer == Prayer.ASR) continue
            assertEquals("$prayer must not depend on the madhab", shafii[prayer], hanafi[prayer])
        }
    }

    @Test
    fun calculationIsStableAcrossLongitudeAndTimeZoneAgreement() {
        // Same instant, expressed twice: a point at longitude L with zone L/15 must give the same
        // wall clock as any other such point on the same parallel.
        val day = CivilDate(2026, 6, 21)
        val western = PrayerCalculator.calculate(
            day, -6.0, 105.0, 7, CalculationMethod.KEMENAG.config, Tuning.KEMENAG,
        )
        val shifted = PrayerCalculator.calculate(
            day, -6.0, 120.0, 8, CalculationMethod.KEMENAG.config, Tuning.KEMENAG,
        )
        for (prayer in Prayer.entries) {
            assertTrue(
                "$prayer should track local solar time, western=${PrayerTimes.format(western[prayer])} " +
                    "shifted=${PrayerTimes.format(shifted[prayer])}",
                abs(western[prayer] - shifted[prayer]) <= 3,
            )
        }
    }

    @Test
    fun polarCoordinatesStillProduceACompleteOrderedDay() {
        // Tromso: the sun never rises or sets around the December solstice, and never reaches the
        // Fajr/Isha angle either. Every method must still yield an ordered, in-range timetable.
        val latitude = 69.6492
        val longitude = 18.9553
        for (method in CalculationMethod.entries) {
            for (rule in HighLatitudeRule.entries) {
                val times = PrayerCalculator.calculate(
                    date = CivilDate(2026, 12, 21),
                    latitude = latitude,
                    longitude = longitude,
                    timeZoneHours = 1,
                    config = method.config.copy(highLatitude = rule),
                )
                for (prayer in Prayer.entries) {
                    assertTrue(
                        "$method/$rule $prayer is not a wall-clock time: ${times[prayer]}",
                        times[prayer] in 0..1439,
                    )
                }
                assertTrue(
                    "$method/$rule: Fajr must precede sunrise: $times",
                    times[Prayer.FAJR] < times[Prayer.SUNRISE],
                )
                assertTrue(
                    "$method/$rule: Isha must follow Maghrib: $times",
                    times[Prayer.ISHA] > times[Prayer.MAGHRIB],
                )
            }
        }
    }

    private companion object {
        /** Captured from `api.aladhan.com/v1/timings`, method=20, explicit timezone. */
        val ALADHAN: List<Sample> = listOf(
            Sample("Jakarta", -6.2088, 106.8456, 7, 2026, 1, 15, intArrayOf(255, 265, 349, 722, 926, 1095, 1170)),
            Sample("Jakarta", -6.2088, 106.8456, 7, 2026, 3, 20, intArrayOf(270, 280, 357, 720, 912, 1083, 1153)),
            Sample("Jakarta", -6.2088, 106.8456, 7, 2026, 6, 21, intArrayOf(268, 278, 361, 714, 916, 1067, 1142)),
            Sample("Jakarta", -6.2088, 106.8456, 7, 2026, 9, 26, intArrayOf(253, 263, 340, 704, 892, 1068, 1137)),
            Sample("Jakarta", -6.2088, 106.8456, 7, 2026, 12, 21, intArrayOf(241, 251, 336, 711, 918, 1085, 1161)),
            Sample("Jakarta", -6.2088, 106.8456, 7, 2027, 4, 10, intArrayOf(266, 276, 354, 714, 913, 1074, 1144)),
            Sample("Surabaya", -7.2575, 112.7521, 7, 2026, 1, 15, intArrayOf(229, 239, 323, 698, 902, 1073, 1148)),
            Sample("Surabaya", -7.2575, 112.7521, 7, 2026, 3, 20, intArrayOf(246, 256, 333, 697, 890, 1060, 1129)),
            Sample("Surabaya", -7.2575, 112.7521, 7, 2026, 6, 21, intArrayOf(246, 256, 340, 691, 892, 1042, 1117)),
            Sample("Surabaya", -7.2575, 112.7521, 7, 2026, 9, 26, intArrayOf(229, 239, 317, 680, 870, 1044, 1114)),
            Sample("Surabaya", -7.2575, 112.7521, 7, 2026, 12, 21, intArrayOf(215, 225, 310, 687, 894, 1063, 1140)),
            Sample("Surabaya", -7.2575, 112.7521, 7, 2027, 4, 10, intArrayOf(243, 253, 331, 690, 890, 1050, 1119)),
            Sample("Medan", 3.5952, 98.6722, 7, 2026, 1, 15, intArrayOf(304, 314, 397, 755, 958, 1113, 1186)),
            Sample("Medan", 3.5952, 98.6722, 7, 2026, 3, 20, intArrayOf(303, 313, 390, 753, 938, 1116, 1185)),
            Sample("Medan", 3.5952, 98.6722, 7, 2026, 6, 21, intArrayOf(283, 293, 377, 747, 954, 1117, 1193)),
            Sample("Medan", 3.5952, 98.6722, 7, 2026, 9, 26, intArrayOf(287, 297, 374, 737, 926, 1100, 1168)),
            Sample("Medan", 3.5952, 98.6722, 7, 2026, 12, 21, intArrayOf(292, 302, 386, 743, 947, 1101, 1176)),
            Sample("Medan", 3.5952, 98.6722, 7, 2027, 4, 10, intArrayOf(294, 304, 381, 747, 936, 1112, 1182)),
            Sample("Makassar", -5.1477, 119.4327, 8, 2026, 1, 15, intArrayOf(266, 276, 360, 732, 936, 1103, 1178)),
            Sample("Makassar", -5.1477, 119.4327, 8, 2026, 3, 20, intArrayOf(279, 289, 366, 730, 920, 1093, 1162)),
            Sample("Makassar", -5.1477, 119.4327, 8, 2026, 6, 21, intArrayOf(276, 286, 369, 724, 927, 1079, 1154)),
            Sample("Makassar", -5.1477, 119.4327, 8, 2026, 9, 26, intArrayOf(263, 273, 350, 714, 900, 1077, 1146)),
            Sample("Makassar", -5.1477, 119.4327, 8, 2026, 12, 21, intArrayOf(252, 262, 347, 720, 927, 1093, 1169)),
            Sample("Makassar", -5.1477, 119.4327, 8, 2027, 4, 10, intArrayOf(276, 286, 363, 724, 922, 1084, 1154)),
            Sample("Denpasar", -8.6705, 115.2126, 8, 2026, 1, 15, intArrayOf(276, 286, 371, 748, 952, 1126, 1201)),
            Sample("Denpasar", -8.6705, 115.2126, 8, 2026, 3, 20, intArrayOf(296, 306, 383, 747, 942, 1110, 1180)),
            Sample("Denpasar", -8.6705, 115.2126, 8, 2026, 6, 21, intArrayOf(298, 308, 392, 741, 941, 1089, 1165)),
            Sample("Denpasar", -8.6705, 115.2126, 8, 2026, 9, 26, intArrayOf(279, 289, 367, 731, 922, 1095, 1164)),
            Sample("Denpasar", -8.6705, 115.2126, 8, 2026, 12, 21, intArrayOf(262, 272, 358, 737, 944, 1116, 1193)),
            Sample("Denpasar", -8.6705, 115.2126, 8, 2027, 4, 10, intArrayOf(294, 304, 382, 741, 941, 1099, 1169)),
            Sample("Jayapura", -2.5916, 140.669, 9, 2026, 1, 15, intArrayOf(246, 256, 339, 707, 911, 1074, 1148)),
            Sample("Jayapura", -2.5916, 140.669, 9, 2026, 3, 20, intArrayOf(255, 265, 342, 705, 891, 1068, 1137)),
            Sample("Jayapura", -2.5916, 140.669, 9, 2026, 6, 21, intArrayOf(246, 256, 340, 699, 903, 1058, 1133)),
            Sample("Jayapura", -2.5916, 140.669, 9, 2026, 9, 26, intArrayOf(239, 249, 325, 689, 870, 1052, 1121)),
            Sample("Jayapura", -2.5916, 140.669, 9, 2026, 12, 21, intArrayOf(233, 243, 327, 695, 902, 1063, 1139)),
            Sample("Jayapura", -2.5916, 140.669, 9, 2027, 4, 10, intArrayOf(250, 260, 337, 699, 895, 1061, 1130)),
            Sample("Kupang", -10.1772, 123.607, 8, 2026, 1, 15, intArrayOf(240, 250, 335, 715, 918, 1094, 1171)),
            Sample("Kupang", -10.1772, 123.607, 8, 2026, 3, 20, intArrayOf(262, 272, 350, 713, 910, 1077, 1146)),
            Sample("Kupang", -10.1772, 123.607, 8, 2026, 6, 21, intArrayOf(267, 277, 361, 707, 906, 1053, 1129)),
            Sample("Kupang", -10.1772, 123.607, 8, 2026, 9, 26, intArrayOf(245, 255, 333, 697, 890, 1061, 1131)),
            Sample("Kupang", -10.1772, 123.607, 8, 2026, 12, 21, intArrayOf(225, 235, 322, 703, 911, 1085, 1163)),
            Sample("Kupang", -10.1772, 123.607, 8, 2027, 4, 10, intArrayOf(261, 271, 349, 707, 908, 1065, 1135)),
            Sample("Banda Aceh", 5.5483, 95.3238, 7, 2026, 1, 15, intArrayOf(321, 331, 413, 768, 970, 1123, 1197)),
            Sample("Banda Aceh", 5.5483, 95.3238, 7, 2026, 3, 20, intArrayOf(316, 326, 403, 766, 955, 1130, 1199)),
            Sample("Banda Aceh", 5.5483, 95.3238, 7, 2026, 6, 21, intArrayOf(292, 302, 387, 760, 968, 1134, 1210)),
            Sample("Banda Aceh", 5.5483, 95.3238, 7, 2026, 9, 26, intArrayOf(300, 310, 387, 750, 943, 1113, 1182)),
            Sample("Banda Aceh", 5.5483, 95.3238, 7, 2026, 12, 21, intArrayOf(309, 319, 403, 757, 959, 1111, 1186)),
            Sample("Banda Aceh", 5.5483, 95.3238, 7, 2027, 4, 10, intArrayOf(306, 316, 394, 760, 947, 1127, 1196)),
        )

        /** Captured from `api.myquran.com/v2/sholat/jadwal` (the published Kemenag schedule). */
        val KEMENAG: List<Sample> = listOf(
            Sample("Jakarta", -6.2088, 106.8456, 7, 2026, 1, 15, intArrayOf(257, 267, 345, 725, 930, 1098, 1173)),
            Sample("Jakarta", -6.2088, 106.8456, 7, 2026, 3, 20, intArrayOf(272, 282, 354, 724, 913, 1087, 1155)),
            Sample("Jakarta", -6.2088, 106.8456, 7, 2026, 6, 21, intArrayOf(270, 280, 358, 718, 919, 1071, 1145)),
            Sample("Jakarta", -6.2088, 106.8456, 7, 2026, 9, 26, intArrayOf(256, 266, 337, 708, 896, 1071, 1140)),
            Sample("Jakarta", -6.2088, 106.8456, 7, 2026, 12, 21, intArrayOf(243, 253, 333, 714, 920, 1088, 1164)),
            Sample("Jakarta", -6.2088, 106.8456, 7, 2027, 4, 10, intArrayOf(269, 279, 351, 718, 914, 1077, 1146)),
            Sample("Surabaya", -7.2575, 112.7521, 7, 2026, 1, 15, intArrayOf(232, 242, 320, 702, 906, 1077, 1151)),
            Sample("Surabaya", -7.2575, 112.7521, 7, 2026, 3, 20, intArrayOf(248, 258, 330, 700, 891, 1063, 1132)),
            Sample("Surabaya", -7.2575, 112.7521, 7, 2026, 6, 21, intArrayOf(249, 259, 337, 694, 894, 1045, 1119)),
            Sample("Surabaya", -7.2575, 112.7521, 7, 2026, 9, 26, intArrayOf(232, 242, 314, 684, 874, 1048, 1116)),
            Sample("Surabaya", -7.2575, 112.7521, 7, 2026, 12, 21, intArrayOf(217, 227, 307, 691, 897, 1067, 1143)),
            Sample("Surabaya", -7.2575, 112.7521, 7, 2027, 4, 10, intArrayOf(246, 256, 328, 694, 892, 1053, 1122)),
            Sample("Medan", 3.5952, 98.6722, 7, 2026, 1, 15, intArrayOf(307, 317, 393, 758, 961, 1116, 1189)),
            Sample("Medan", 3.5952, 98.6722, 7, 2026, 3, 20, intArrayOf(305, 315, 386, 756, 942, 1119, 1187)),
            Sample("Medan", 3.5952, 98.6722, 7, 2026, 6, 21, intArrayOf(285, 295, 374, 751, 956, 1120, 1195)),
            Sample("Medan", 3.5952, 98.6722, 7, 2026, 9, 26, intArrayOf(289, 299, 371, 740, 927, 1103, 1171)),
            Sample("Medan", 3.5952, 98.6722, 7, 2026, 12, 21, intArrayOf(294, 304, 382, 747, 950, 1104, 1178)),
            Sample("Medan", 3.5952, 98.6722, 7, 2027, 4, 10, intArrayOf(296, 306, 378, 750, 938, 1115, 1184)),
            Sample("Makassar", -5.1477, 119.4327, 8, 2026, 1, 15, intArrayOf(269, 279, 357, 735, 939, 1106, 1180)),
            Sample("Makassar", -5.1477, 119.4327, 8, 2026, 3, 20, intArrayOf(282, 292, 363, 733, 921, 1096, 1165)),
            Sample("Makassar", -5.1477, 119.4327, 8, 2026, 6, 21, intArrayOf(278, 288, 366, 728, 929, 1082, 1156)),
            Sample("Makassar", -5.1477, 119.4327, 8, 2026, 9, 26, intArrayOf(266, 276, 347, 717, 904, 1081, 1149)),
            Sample("Makassar", -5.1477, 119.4327, 8, 2026, 12, 21, intArrayOf(255, 265, 344, 724, 930, 1096, 1171)),
            Sample("Makassar", -5.1477, 119.4327, 8, 2027, 4, 10, intArrayOf(278, 288, 360, 727, 923, 1087, 1156)),
            Sample("Denpasar", -8.6705, 115.2126, 8, 2026, 1, 15, intArrayOf(279, 289, 368, 752, 955, 1129, 1204)),
            Sample("Denpasar", -8.6705, 115.2126, 8, 2026, 3, 20, intArrayOf(298, 308, 380, 750, 943, 1113, 1182)),
            Sample("Denpasar", -8.6705, 115.2126, 8, 2026, 6, 21, intArrayOf(301, 311, 389, 744, 943, 1093, 1167)),
            Sample("Denpasar", -8.6705, 115.2126, 8, 2026, 9, 26, intArrayOf(281, 291, 363, 734, 926, 1098, 1167)),
            Sample("Denpasar", -8.6705, 115.2126, 8, 2026, 12, 21, intArrayOf(264, 274, 355, 741, 947, 1119, 1196)),
            Sample("Denpasar", -8.6705, 115.2126, 8, 2027, 4, 10, intArrayOf(296, 306, 379, 744, 942, 1102, 1171)),
            Sample("Jayapura", -2.5916, 140.669, 9, 2026, 1, 15, intArrayOf(248, 258, 336, 710, 914, 1077, 1151)),
            Sample("Jayapura", -2.5916, 140.669, 9, 2026, 3, 20, intArrayOf(257, 267, 338, 708, 892, 1071, 1139)),
            Sample("Jayapura", -2.5916, 140.669, 9, 2026, 6, 21, intArrayOf(248, 258, 336, 702, 906, 1061, 1136)),
            Sample("Jayapura", -2.5916, 140.669, 9, 2026, 9, 26, intArrayOf(241, 251, 322, 692, 874, 1055, 1123)),
            Sample("Jayapura", -2.5916, 140.669, 9, 2026, 12, 21, intArrayOf(235, 245, 324, 699, 904, 1066, 1141)),
            Sample("Jayapura", -2.5916, 140.669, 9, 2027, 4, 10, intArrayOf(252, 262, 334, 702, 896, 1064, 1132)),
            Sample("Kupang", -10.1772, 123.607, 8, 2026, 1, 15, intArrayOf(242, 252, 332, 718, 921, 1098, 1173)),
            Sample("Kupang", -10.1772, 123.607, 8, 2026, 3, 20, intArrayOf(264, 274, 347, 717, 911, 1080, 1149)),
            Sample("Kupang", -10.1772, 123.607, 8, 2026, 6, 21, intArrayOf(270, 280, 358, 711, 908, 1057, 1131)),
            Sample("Kupang", -10.1772, 123.607, 8, 2026, 9, 26, intArrayOf(248, 258, 330, 701, 894, 1065, 1134)),
            Sample("Kupang", -10.1772, 123.607, 8, 2026, 12, 21, intArrayOf(227, 237, 319, 707, 913, 1088, 1166)),
            Sample("Kupang", -10.1772, 123.607, 8, 2027, 4, 10, intArrayOf(264, 274, 346, 711, 910, 1068, 1137)),
            Sample("Banda Aceh", 5.5483, 95.3238, 7, 2026, 1, 15, intArrayOf(323, 333, 410, 772, 973, 1126, 1199)),
            Sample("Banda Aceh", 5.5483, 95.3238, 7, 2026, 3, 20, intArrayOf(319, 329, 400, 770, 959, 1133, 1201)),
            Sample("Banda Aceh", 5.5483, 95.3238, 7, 2026, 6, 21, intArrayOf(294, 304, 384, 764, 970, 1137, 1213)),
            Sample("Banda Aceh", 5.5483, 95.3238, 7, 2026, 9, 26, intArrayOf(303, 313, 384, 754, 944, 1116, 1184)),
            Sample("Banda Aceh", 5.5483, 95.3238, 7, 2026, 12, 21, intArrayOf(311, 321, 399, 760, 962, 1114, 1188)),
            Sample("Banda Aceh", 5.5483, 95.3238, 7, 2027, 4, 10, intArrayOf(308, 318, 391, 764, 948, 1130, 1199)),
        )
    }
}
