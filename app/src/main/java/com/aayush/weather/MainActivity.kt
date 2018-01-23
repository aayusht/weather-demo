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
import android.content.pm.PackageManager
import android.location.Location
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.ActionBar
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.CursorAdapter
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.location.*
import java.net.URLEncoder


//Global constants
const val WEATHER_ENDPOINT = "https://api.wunderground.com/api/24f0b7e4ed53f605/forecast10day"
const val AUTOCOMPLETE_ENDPOINT = "https://autocomplete.wunderground.com/aq?query="
const val DELAY = 500L // milliseconds
const val MY_PERMISSION_ACCESS_COURSE_LOCATION = 69
val sAutocompleteColNames = arrayOf(BaseColumns._ID, // necessary for adapter
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
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val loadingCursor = MatrixCursor(sAutocompleteColNames) //dummy cursor instead of null
    private var location: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        loadingCursor.addRow(arrayOf(420, "Loading...", ""))

        //init location stuff, may not be used if no permission or cant get location
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object: LocationCallback() {
            override fun onLocationResult(result: LocationResult?) {
                if (result != null) {
                    for (location in result.locations) this@MainActivity.location = location
                }
            }
        }
        if ( ContextCompat.checkSelfPermission( this, android.Manifest.permission.ACCESS_COARSE_LOCATION )
                != PackageManager.PERMISSION_GRANTED ) {
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
            val searchMenuItem = menu.findItem(R.id.action_search)
            searchView = searchMenuItem.actionView as SearchView
            searchView.layoutParams = ActionBar.LayoutParams(Gravity.RIGHT)
            searchView.suggestionsAdapter = SimpleCursorAdapter(
                    applicationContext, R.layout.list_item, null,
                    arrayOf(SearchManager.SUGGEST_COLUMN_TEXT_1, SearchManager.EXTRA_DATA_KEY),
                    intArrayOf(R.id.textView),
                    CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER)
            searchView.setOnQueryTextListener(object: SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(p0: String?) = true

                override fun onQueryTextChange(string: String?): Boolean {
                    if (string != null) {
                        if (string.length > 2) {
                            searchView.suggestionsAdapter.changeCursor(loadingCursor)
                            getPredictions(string)
                        } else searchView.suggestionsAdapter.changeCursor(null)
                    }
                    return true
                }
            })
            searchView.setOnSuggestionListener(object : SearchView.OnSuggestionListener {

                override fun onSuggestionSelect(position: Int): Boolean {
                    val cursor = searchView.suggestionsAdapter.getItem(position) as Cursor
                    if (cursor != loadingCursor) {
                        val data = JSONObject(cursor.getString(cursor.getColumnIndex(SearchManager.EXTRA_DATA_KEY)))
                        cursor.close()

                        fetchWeatherData(URL(WEATHER_ENDPOINT + data.getString("l") + ".json"))
                        searchView.setQuery("", false)
                        searchView.isIconified = true
                        searchView.clearFocus()
                        searchMenuItem.collapseActionView()
                        supportActionBar?.title = cursor.getString(cursor.getColumnIndex(SearchManager.SUGGEST_COLUMN_TEXT_1))
                    }
                    return true
                }

                override fun onSuggestionClick(position: Int): Boolean {
                    return onSuggestionSelect(position)
                }
            })
        }

        return true
    }

    /**
     * Fetches weather data from wunderground forecast10day query and attaches to RecyclerView
     * @param url endpoint to fetch weather data from
     */
    private fun fetchWeatherData(url: URL) {
        (@SuppressLint("StaticFieldLeak")
        object: AsyncTask<Void, Void, JSONObject>() {
            override fun doInBackground(vararg p0: Void?): JSONObject {
                val httpConnection = url.openConnection() as HttpURLConnection
                httpConnection.requestMethod = "GET"
                val inputStream = BufferedInputStream(httpConnection.inputStream)
                val scanner = Scanner(inputStream).useDelimiter("\\A")
                return JSONObject(if(scanner.hasNext()) scanner.next() else "")
            }

            override fun onPostExecute(result: JSONObject?) {
                if (result != null) {
                    findViewById<TextView>(R.id.loadingText).visibility = View.GONE
                    val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
                    recyclerView.layoutManager = LinearLayoutManager(this@MainActivity)
                    recyclerView.adapter = WeatherAdapter(this@MainActivity, result)
                }
            }
        }).execute()
    }

    /**
     * Attempts to get location and starts location updates
     */
    private fun startLocationUpdates() {
        if ( ContextCompat.checkSelfPermission( this, android.Manifest.permission.ACCESS_COARSE_LOCATION ) == PackageManager.PERMISSION_GRANTED ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location = it }
            fusedLocationClient.requestLocationUpdates(createLocationRequest(),
                    locationCallback,
                    null /* Looper */)
        } else {
            Log.d("location updates", "permission denied")
        }
    }

    /**
     * Creates a location request around evey minute with low priority
     */
    private fun createLocationRequest(): LocationRequest {
        val locationRequest = LocationRequest()
        locationRequest.interval = 100000L
        locationRequest.fastestInterval = 60000L
        locationRequest.priority = LocationRequest.PRIORITY_LOW_POWER
        return locationRequest
    }

    /**
     * Stops location updates
     */
    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    /**
     * To avoid unnecessary API calls, the asynctask is only executed if there has not been an
     * API call in DELAY milliseconds.
     */
    private fun getPredictions(query: String) {
        if (timerTask != null) {
            timerTask?.cancel()
            timerTask = null
        }

        timerTask = object: TimerTask() {
            override fun run() {
                runOnUiThread {
                    timerTask = null
                    //Autocomplete endpoint to retrieve valid city / area names
                    (@SuppressLint("StaticFieldLeak")
                    object: AsyncTask<Void, Void, Cursor>() {
                        override fun doInBackground(vararg p0: Void?): Cursor {
                            val cursor = MatrixCursor(sAutocompleteColNames)
                            val url = URL(AUTOCOMPLETE_ENDPOINT + URLEncoder.encode(query, "UTF-8"))
                            val httpConnection = url.openConnection() as HttpURLConnection
                            httpConnection.requestMethod = "GET"
                            val inputStream = BufferedInputStream(httpConnection.inputStream)
                            val scanner = Scanner(inputStream).useDelimiter("\\A")
                            val response = if(scanner.hasNext()) scanner.next() else ""
                            val placesJSON = JSONObject(response).getJSONArray("RESULTS")
                            val rows: MutableList<Array<Any>> = mutableListOf()
                            (0 until placesJSON.length()).mapTo(rows) {
                                arrayOf(it, //does this have to change if I sort?
                                        placesJSON.getJSONObject(it).getString("name"),
                                        placesJSON.getJSONObject(it).toString())
                            }
                            //sort by closeness to current location if we have it
                            if (location != null) {
                                rows.sortBy {
                                    val location = Location("")
                                    location.latitude = JSONObject(it[2] as String).getDouble("lat")
                                    location.longitude = JSONObject(it[2] as String).getDouble("lon")
                                    return@sortBy location.distanceTo(this@MainActivity.location)
                                }
                                rows.add(0, arrayOf(rows.size + 1, "Current Location",
                                        "{\"l\": \"/q/${location!!.latitude},${location!!.longitude}\"}"))
                            }
                            rows.forEach { cursor.addRow(it) }

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
