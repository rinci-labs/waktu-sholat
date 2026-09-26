package dev.rafa.waktusholat.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
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
import java.text.Normalizer

/**
 * Location picker over the built-in city table, plus "use my location".
 *
 * A plain [BaseAdapter] with client-side filtering is enough for ~500 rows. The search keys are
 * normalised once up front, so each keystroke is a substring scan with no allocation per row.
 */
class CityPickerActivity : Activity() {

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
        if (preferences.useGps) {
            findViewById<TextView>(R.id.use_location_summary).text =
                getString(R.string.city_gps_active, preferences.resolveCity().label)
        }

        findViewById<EditText>(R.id.search).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                adapter.filter(s?.toString().orEmpty())
                val isEmpty = adapter.count == 0
                empty.visibility = if (isEmpty) View.VISIBLE else View.GONE
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

    /** Coarse location is plenty: the schedule snaps to the nearest built-in city anyway. */
    private fun useDeviceLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_LOCATION)
            return
        }
        applyLastKnownLocation()
    }

    private fun applyLastKnownLocation() {
        val manager = getSystemService(LocationManager::class.java)
        // Re-checked here: the permission can be revoked while the app is backgrounded, and
        // getLastKnownLocation throws rather than returning null in that case.
        val granted = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val location = if (granted && manager != null) {
            manager.getProviders(true)
                .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
        } else {
            null
        }
        when {
            location == null -> toast(R.string.location_unavailable)
            // A fix the app cannot serve is refused: the nearest built-in city would be thousands of
            // kilometres away and its wall-clock zone meaningless.
            !City.isServiceable(location.latitude, location.longitude) -> toast(R.string.location_outside)
            else -> {
                val app = application as WaktuSholatApp
                app.preferences.setCoordinates(location.latitude, location.longitude)
                app.notifyScheduleChanged()
                finish()
            }
        }
    }

    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_LOCATION) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            applyLastKnownLocation()
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
