package dev.rafa.waktusholat.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.text.Editable
import android.text.TextWatcher
import dev.rafa.waktusholat.R
import dev.rafa.waktusholat.WaktuSholatApp
import dev.rafa.waktusholat.core.City
import java.text.Normalizer

/**
 * Location picker over the built-in city table. A plain [BaseAdapter] with client-side filtering is
 * enough: the table is around five hundred rows, so a database or a recycling list view would be
 * more machinery than the task needs.
 */
class CityPickerActivity : Activity() {

    private lateinit var list: ListView
    private lateinit var empty: TextView
    private lateinit var adapter: CityAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_city_picker)
        setTitle(R.string.city_title)

        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)
        val search = findViewById<EditText>(R.id.search)

        adapter = CityAdapter(this, City.ALL)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> select(adapter.getItem(position)) }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                adapter.filter(s?.toString().orEmpty())
                refreshEmptyState()
            }
        })

        refreshEmptyState()
    }

    /** Persists the choice, re-arms the alarms and returns to the caller. */
    private fun select(city: City) {
        val app = application as WaktuSholatApp
        app.preferences.setCity(city)
        app.notifyScheduleChanged()
        setResult(RESULT_OK)
        finish()
    }

    private fun refreshEmptyState() {
        val isEmpty = adapter.count == 0
        empty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        list.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    /** Two-line rows: city on top, province and time zone beneath. */
    private class CityAdapter(
        private val context: Context,
        private val all: List<City>,
    ) : BaseAdapter() {

        private var visible: List<City> = all

        override fun getCount(): Int = visible.size
        override fun getItem(position: Int): City = visible[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView
                ?: LayoutInflater.from(context).inflate(R.layout.item_city, parent, false)
            val city = visible[position]
            view.findViewById<TextView>(R.id.city_name).text = city.name
            view.findViewById<TextView>(R.id.city_detail).text =
                context.getString(R.string.city_detail, city.province, city.zoneLabel)
            return view
        }

        /** Case- and accent-insensitive match over the city and province names. */
        fun filter(query: String) {
            val needle = query.normalizeText()
            visible = if (needle.isEmpty()) {
                all
            } else {
                all.filter {
                    it.name.normalizeText().contains(needle) || it.province.normalizeText().contains(needle)
                }
            }
            notifyDataSetChanged()
        }

        private fun String.normalizeText(): String =
            Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD).replace(ACCENTS, "")
    }

    private companion object {
        val ACCENTS = "\\p{Mn}+".toRegex()
    }
}
