package com.aayush.weather

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import java.util.*
import android.annotation.SuppressLint
import android.os.AsyncTask
import android.support.v7.widget.SearchView
import org.json.JSONObject
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import android.app.SearchManager
import android.database.Cursor
import android.support.v4.widget.SimpleCursorAdapter
import android.provider.BaseColumns
import android.database.MatrixCursor
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.ActionBar
import android.util.Log
import android.view.Gravity
import android.widget.CursorAdapter
import android.widget.Toast
import com.google.android.gms.location.LocationServices


//Global constants
const val WEATHER_ENDPOINT = "https://api.wunderground.com/api/24f0b7e4ed53f605/forecast10day"
const val AUTOCOMPLETE_ENDPOINT = "https://autocomplete.wunderground.com/aq?query="
const val DELAY = 500L // milliseconds
const val MY_PERMISSION_ACCESS_COURSE_LOCATION = 69
var sAutocompleteColNames = arrayOf(BaseColumns._ID, // necessary for adapter
        SearchManager.SUGGEST_COLUMN_TEXT_1,      // the full search term
        SearchManager.EXTRA_DATA_KEY // full json
)

/**
 * @author Aayush Tyagi
 *
 * Da rulez:
 * Single screen, 10 day forecast app
 * enter city name, forecast displays, 1 item for each day
 * Any language, any libraries
 * Should minimally contain day name, provided icon, and description
 * UI will not be judged (consciously)
 */
class MainActivity : AppCompatActivity() {
    private var timerTask: TimerTask? = null
    private val timer = Timer()
    private lateinit var searchView: SearchView
    private var location: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if ( ContextCompat.checkSelfPermission( this, android.Manifest.permission.ACCESS_COARSE_LOCATION ) != PackageManager.PERMISSION_GRANTED ) {

            ActivityCompat.requestPermissions( this,
                    arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION),
                    MY_PERMISSION_ACCESS_COURSE_LOCATION );
        } else {
            startLocationUpdates()
        }

    }

    override fun onResume() {
        super.onResume()
        startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            MY_PERMISSION_ACCESS_COURSE_LOCATION -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    startLocationUpdates()
                } else {
                    Toast.makeText(this, "Location detection disabled.", Toast.LENGTH_SHORT).show()
                }
                return
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        if (menu != null) {
            searchView = menu.findItem(R.id.action_search).actionView as SearchView
            searchView.layoutParams = ActionBar.LayoutParams(Gravity.RIGHT)
            searchView.suggestionsAdapter = SimpleCursorAdapter(
                    applicationContext, R.layout.list_item, null,
                    arrayOf(SearchManager.SUGGEST_COLUMN_TEXT_1, SearchManager.EXTRA_DATA_KEY),
                    intArrayOf(R.id.textView),
                    CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER)
            searchView.setOnQueryTextListener(object: SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(p0: String?) = true

                override fun onQueryTextChange(string: String?): Boolean {
                    searchView.suggestionsAdapter.changeCursor(null)
                    if (string != null && string.isNotEmpty()) {
                        getPredictions(string)
                    }
                    return true
                }
            })
            searchView.setOnSuggestionListener(object : SearchView.OnSuggestionListener {

                override fun onSuggestionSelect(position: Int): Boolean {

                    val cursor = searchView.suggestionsAdapter.getItem(position) as Cursor
                    val term = cursor.getString(cursor.getColumnIndex(SearchManager.SUGGEST_COLUMN_TEXT_1))
                    val data = cursor.getString(cursor.getColumnIndex(SearchManager.EXTRA_DATA_KEY))
                    cursor.close()
                    Toast.makeText(applicationContext, data, Toast.LENGTH_SHORT).show()

                    return true
                }

                override fun onSuggestionClick(position: Int): Boolean {
                    return onSuggestionSelect(position)
                }
            })
        }

        return true
    }

    private fun startLocationUpdates() {

    }

    private fun stopLocationUpdates() {

    }

    private fun getPredictions(query: String) {
        if (timerTask != null) {
            timerTask?.cancel()
            timerTask = null
        }

        timerTask = object: TimerTask() {
            override fun run() {
                runOnUiThread {
                    timerTask = null
                    (@SuppressLint("StaticFieldLeak")
                    object: AsyncTask<Void, Void, Cursor>() {
                        override fun doInBackground(vararg p0: Void?): Cursor {
                            val cursor = MatrixCursor(sAutocompleteColNames)
                            val url = URL(AUTOCOMPLETE_ENDPOINT + query)
                            val httpConnection = url.openConnection() as HttpURLConnection
                            httpConnection.requestMethod = "GET"
                            val inputStream = BufferedInputStream(httpConnection.inputStream)
                            val scanner = Scanner(inputStream).useDelimiter("\\A")
                            val response = if(scanner.hasNext()) scanner.next() else ""
                            val placesJSON = JSONObject(response).getJSONArray("RESULTS")
                            (0 until placesJSON.length())
                                    .map { arrayOf(it,
                                            placesJSON.getJSONObject(it).getString("name"),
                                            placesJSON.getJSONObject(it).toString())
                                    }
                                    .forEach { cursor.addRow(it) }

                            return cursor
                        }

                        override fun onPostExecute(result: Cursor?) {
                            searchView.suggestionsAdapter.changeCursor(result)
                        }
                    }).execute()
                }
            }
        }

        timer.schedule(timerTask, DELAY)
    }
}
