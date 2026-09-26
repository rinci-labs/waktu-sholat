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
import java.text.Normalizer

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
        val current = if (preferences.useGps) null else preferences.resolveCity()
        adapter = CityAdapter(this, current)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> select(adapter.getItem(position)) }
        current?.let { city -> City.ALL.indexOf(city).takeIf { it > 2 }?.let { list.setSelection(it - 2) } }

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
                adapter.filter(query)
                bindWorldSearch(query)
                val isEmpty = adapter.count == 0
                // With a query typed, the worldwide row already offers the way forward.
                empty.visibility = if (isEmpty && query.length < 2) View.VISIBLE else View.GONE
                list.visibility = if (isEmpty) View.GONE else View.VISIBLE
            }
        })
    }

    private fun select(city: City) {
        val app = application as WaktuSholatApp
        app.preferences.setCity(city)
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

    private class CityAdapter(private val context: Context, private val selected: City?) : BaseAdapter() {

        private val keys: Array<String> = Array(City.ALL.size) { normalize("${City.ALL[it].name} ${City.ALL[it].province}") }
        private var visible: List<City> = City.ALL

        override fun getCount(): Int = visible.size
        override fun getItem(position: Int): City = visible[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_city, parent, false).also {
                it.tag = Holder(it)
            }
            val holder = view.tag as Holder
            val city = visible[position]
            holder.name.text = city.name
            holder.detail.text = context.getString(R.string.city_detail, city.province, city.zoneLabel)
            holder.check.visibility = if (city == selected) View.VISIBLE else View.GONE
            return view
        }

        /** Case- and accent-insensitive match over the city and province names. */
        fun filter(query: String) {
            val needle = normalize(query)
            visible = if (needle.isEmpty()) {
                City.ALL
            } else {
                City.ALL.filterIndexed { index, _ -> keys[index].contains(needle) }
            }
            notifyDataSetChanged()
        }

        private class Holder(view: View) {
            val name: TextView = view.findViewById(R.id.city_name)
            val detail: TextView = view.findViewById(R.id.city_detail)
            val check: View = view.findViewById(R.id.city_selected)
        }
    }

    private companion object {
        const val REQUEST_LOCATION = 41
        val ACCENTS = "\\p{Mn}+".toRegex()

        fun normalize(text: String): String =
            Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD).replace(ACCENTS, "")
    }
}
