package dev.rafa.waktusholat.data

import android.content.Context
import dev.rafa.waktusholat.core.City
import java.text.Normalizer
import java.util.Locale
import java.util.TimeZone

/**
 * Offline world cities outside Indonesia (assets/world_cities.tsv, from GeoNames, CC BY 4.0):
 * every national capital, state capitals, and cities of 300k+, each with its exact IANA time zone.
 * Built by tools/gen_world_cities.py.
 *
 * Loaded on first use into flat arrays (a few hundred KB of heap, freed with the picker), and
 * searched by prefix, then word prefix, then substring, most populous first.
 */
class WorldCities private constructor(
    private val names: Array<String>,
    private val keys: Array<String>,
    private val regionOf: IntArray,
    private val latitudes: FloatArray,
    private val longitudes: FloatArray,
    private val zoneOf: IntArray,
    private val zones: Array<String>,
    private val regions: Array<Pair<String, String>>,
) {
    val size: Int get() = names.size

    /** Up to [limit] matches for an already-normalised [needle], best first. */
    fun search(needle: String, limit: Int): List<Match> {
        if (needle.isEmpty()) return emptyList()
        val out = ArrayList<Match>()
        for (i in names.indices) {
            val rank = rank(keys[i], needle)
            if (rank >= 0) out += Match(i, rank)
        }
        // Rows are stored most populous first, so a stable sort by rank keeps that order within a rank.
        out.sortBy { it.rank }
        return if (out.size > limit) out.subList(0, limit) else out
    }

    class Match(val index: Int, val rank: Int)

    /** The city at [index], its region written as `State, Country` in [locale]. */
    fun city(index: Int, locale: Locale): City {
        val (country, state) = regions[regionOf[index]]
        val countryName = Locale("", country).getDisplayCountry(locale).ifEmpty { country }
        val zone = zones[zoneOf[index]]
        return City(
            name = names[index],
            province = if (state.isEmpty()) countryName else "$state, $countryName",
            latitude = latitudes[index].toDouble(),
            longitude = longitudes[index].toDouble(),
            timeZoneHours = TimeZone.getTimeZone(zone).rawOffset / 3_600_000,
            zoneId = zone,
        )
    }

    companion object {
        @Volatile
        private var loaded: WorldCities? = null

        /** Parses the asset once per process; call off the main thread the first time. */
        fun load(context: Context): WorldCities = loaded ?: synchronized(this) {
            loaded ?: parse(context).also { loaded = it }
        }

        private fun parse(context: Context): WorldCities {
            val lines = context.assets.open("world_cities.tsv").bufferedReader().readLines()
            val zones = lines[0].split('\t').drop(1).toTypedArray()
            val regions = arrayOf("" to "") + lines[1].split('\t').drop(1).map {
                val bar = it.indexOf('|')
                it.substring(0, bar) to it.substring(bar + 1)
            }
            val rows = lines.size - 2
            val names = arrayOfNulls<String>(rows)
            val keys = arrayOfNulls<String>(rows)
            val regionOf = IntArray(rows)
            val lat = FloatArray(rows)
            val lng = FloatArray(rows)
            val zoneOf = IntArray(rows)
            for (i in 0 until rows) {
                val f = lines[i + 2].split('\t')
                names[i] = f[0]
                regionOf[i] = f[2].toInt()
                lat[i] = f[3].toFloat()
                lng[i] = f[4].toFloat()
                zoneOf[i] = f[5].toInt()
                // Search key: the name, then any aliases, space-separated so word prefixes match.
                keys[i] = normalize(if (f[1].isEmpty()) f[0] else f[0] + " " + f[1].replace('|', ' '))
            }
            @Suppress("UNCHECKED_CAST")
            return WorldCities(names as Array<String>, keys as Array<String>, regionOf, lat, lng, zoneOf, zones, regions)
        }

        private val ACCENTS = "\\p{Mn}+".toRegex()

        /** Lower-case, accents stripped, apostrophes and dashes treated as spaces. */
        fun normalize(text: String): String =
            Normalizer.normalize(text.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(ACCENTS, "")
                .replace('-', ' ')
                .replace("'", "")
                .replace("’", "")

        /** 0 = starts with the query, 1 = a word starts with it, 2 = contains it, -1 = no match. */
        fun rank(key: String, needle: String): Int = when {
            key.startsWith(needle) -> 0
            key.contains(" $needle") -> 1
            key.contains(needle) -> 2
            else -> -1
        }
    }
}
