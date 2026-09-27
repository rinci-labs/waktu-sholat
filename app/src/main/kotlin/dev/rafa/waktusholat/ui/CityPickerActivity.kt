package dev.rafa.waktusholat.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import dev.rafa.waktusholat.R
import dev.rafa.waktusholat.WaktuSholatApp
import dev.rafa.waktusholat.core.City
import dev.rafa.waktusholat.data.DeviceLocation
import dev.rafa.waktusholat.data.WorldCities

/**
 * Location picker: a precise device fix that works anywhere in the world, or a city from the
 * built-in Indonesian table for choosing a place without GPS.
 *
 * A plain [BaseAdapter] with client-side filtering is enough for ~500 rows. The search keys are
 * normalised once up front, so each keystroke is a substring scan with no allocation per row.
 */
class CityPickerActivity : BaseActivity() {

    private lateinit var list: ListView
    private lateinit var empty: TextView
    private lateinit var adapter: CityAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_city_picker)
        setupTopBar(getString(R.string.city_title))

        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)

        val preferences = (application as WaktuSholatApp).preferences
        val current = if (preferences.followsDevice) null else preferences.resolveCity()
        adapter = CityAdapter(this, current)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> select(adapter.getItem(position)) }
        refreshEmptyState("")

        findViewById<View>(R.id.use_location).setOnClickListener { useDeviceLocation() }
        if (preferences.followsDevice) {
            val city = preferences.resolveCity()
            findViewById<TextView>(R.id.use_location_summary).text =
                getString(R.string.city_gps_active, city.label, city.zoneLabel)
        }

        findViewById<EditText>(R.id.search).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString().orEmpty().trim()
                if (query.isNotEmpty()) loadWorldOnce()
                adapter.filter(query)
                bindWorldSearch(query)
                refreshEmptyState(query)
            }
        })
    }

    /** Nothing is listed until something is typed; the hint says what can be searched. */
    private fun refreshEmptyState(query: String) {
        val isEmpty = adapter.count == 0
        list.visibility = if (isEmpty) View.GONE else View.VISIBLE
        empty.visibility = if (isEmpty && query.length < 2) View.VISIBLE else View.GONE
        empty.setText(if (query.isEmpty()) R.string.city_hint else R.string.city_empty)
    }

    private var worldRequested = false

    /** The world list is parsed on the first keystroke, off the main thread, then kept for the process. */
    private fun loadWorldOnce() {
        if (worldRequested) return
        worldRequested = true
        Thread {
            val world = WorldCities.load(applicationContext)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                adapter.world = world
                refreshEmptyState(findViewById<EditText>(R.id.search).text.toString().trim())
            }
        }.start()
    }

    private fun select(entry: CityAdapter.Entry) {
        val app = application as WaktuSholatApp
        val city = entry.city
        if (entry.world) {
            app.preferences.setPickedPlace(city.latitude, city.longitude, city.zoneId!!, city.name, city.province)
        } else {
            app.preferences.setCity(city)
        }
        app.notifyScheduleChanged()
        finish()
    }

    /** Asks for precise location (the user may still grant approximate on Android 12+), then fixes. */
    private fun useDeviceLocation() {
        if (!DeviceLocation.hasPermission(this)) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                REQUEST_LOCATION,
            )
            return
        }
        val summary = findViewById<TextView>(R.id.use_location_summary)
        val previous = summary.text
        summary.setText(R.string.city_locating)
        findViewById<View>(R.id.use_location).isEnabled = false
        DeviceLocation.locate(this) { location ->
            if (isFinishing || isDestroyed) return@locate
            findViewById<View>(R.id.use_location).isEnabled = true
            if (location == null) {
                summary.text = previous
                toast(R.string.location_unavailable)
            } else {
                (application as WaktuSholatApp).applyFix(location)
                finish()
            }
        }
    }

    /** Offers a worldwide search for anything typed, for places outside the built-in table. */
    private fun bindWorldSearch(query: String) {
        val row = findViewById<View>(R.id.search_world)
        row.visibility = if (query.length >= 2) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.search_world_text).text = getString(R.string.search_world, query)
        row.setOnClickListener { searchWorld(query) }
    }

    private fun searchWorld(query: String) {
        val text = findViewById<TextView>(R.id.search_world_text)
        val row = findViewById<View>(R.id.search_world)
        text.setText(R.string.search_world_loading)
        row.isEnabled = false
        DeviceLocation.search(this, query) { found ->
            if (isFinishing || isDestroyed) return@search
            row.isEnabled = true
            text.text = getString(R.string.search_world, query)
            if (found.isEmpty()) {
                toast(R.string.search_world_empty)
                return@search
            }
            val labels = found.map { place ->
                val zone = City(place.name, place.region, place.latitude, place.longitude, 0, place.zoneId).zoneLabel
                listOf(place.name, place.region, zone).filter { it.isNotBlank() }.joinToString(" \u00b7 ")
            }
            android.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.search_world_title, query))
                .setItems(labels.toTypedArray()) { _, which ->
                    val place = found[which]
                    val app = application as WaktuSholatApp
                    app.preferences.setPickedPlace(place.latitude, place.longitude, place.zoneId, place.name, place.region)
                    app.notifyScheduleChanged()
                    finish()
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }
    }

    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_LOCATION) return
        if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            useDeviceLocation()
        } else {
            toast(R.string.location_permission_needed)
        }
    }

    /**
     * Search results only (nothing is listed for an empty query): the Indonesian table plus the
     * offline world cities.
     * Matches that start with the query come first, then word starts, then substrings; at the same
     * rank Indonesian entries lead, and world cities keep their most-populous-first order.
     */
    private class CityAdapter(private val context: Context, private val selected: City?) : BaseAdapter() {

        class Entry(val city: City, val world: Boolean)

        private val keys: Array<String> = Array(City.ALL.size) { WorldCities.normalize(City.ALL[it].name) }
        private val provinceKeys: Array<String> = Array(City.ALL.size) { WorldCities.normalize(City.ALL[it].province) }
        private val all: List<Entry> = City.ALL.map { Entry(it, world = false) }
        private var visible: List<Entry> = emptyList()
        private var query = ""
        private val locale = context.resources.configuration.locales[0]

        var world: WorldCities? = null
            set(value) {
                field = value
                if (query.isNotEmpty()) filter(query)
            }

        override fun getCount(): Int = visible.size
        override fun getItem(position: Int): Entry = visible[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_city, parent, false).also {
                it.tag = Holder(it)
            }
            val holder = view.tag as Holder
            val city = visible[position].city
            holder.name.text = city.name
            holder.detail.text = context.getString(R.string.city_detail, city.province, city.zoneLabel)
            holder.check.visibility = if (isSelected(city)) View.VISIBLE else View.GONE
            return view
        }

        private fun isSelected(city: City): Boolean = selected != null &&
            selected.name == city.name && kotlin.math.abs(selected.latitude - city.latitude) < 0.01 &&
            kotlin.math.abs(selected.longitude - city.longitude) < 0.01

        /** Case- and accent-insensitive search over names (and Indonesian provinces). */
        fun filter(text: String) {
            query = text
            val needle = WorldCities.normalize(text)
            if (needle.isEmpty()) {
                visible = emptyList()
                notifyDataSetChanged()
                return
            }
            val ranked = ArrayList<Pair<Int, Entry>>()
            for (i in keys.indices) {
                val rank = WorldCities.rank(keys[i], needle).takeIf { it >= 0 }
                    ?: if (provinceKeys[i].contains(needle)) 3 else continue
                ranked += rank to all[i]
            }
            world?.search(needle, WORLD_LIMIT)?.forEach { match ->
                ranked += match.rank to Entry(world!!.city(match.index, locale), world = true)
            }
            // Stable: Indonesian entries were added first, so they lead within a rank.
            ranked.sortBy { it.first }
            visible = ranked.take(RESULT_LIMIT).map { it.second }
            notifyDataSetChanged()
        }

        private class Holder(view: View) {
            val name: TextView = view.findViewById(R.id.city_name)
            val detail: TextView = view.findViewById(R.id.city_detail)
            val check: View = view.findViewById(R.id.city_selected)
        }

        private companion object {
            const val WORLD_LIMIT = 40
            const val RESULT_LIMIT = 60
        }
    }

    private companion object {
        const val REQUEST_LOCATION = 41
    }
}
