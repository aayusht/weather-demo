package com.aayush.weather

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import org.json.JSONObject

/**
 * Created by aayush on 1/21/18.
 *
 * Adapter for 10 day forecast
 * Kotlin makes this so much easier :)
 */
class WeatherAdapter(val context: Context, data: JSONObject): RecyclerView.Adapter<WeatherAdapter.CustomViewHolder>() {
    private val weatherData = arrayListOf<WeatherData>()
    init {
        val forecastText = data.getJSONObject("forecast").getJSONObject("txt_forecast").getJSONArray("forecastday")
        val forecastData = data.getJSONObject("forecast").getJSONObject("simpleforecast").getJSONArray("forecastday")
        for (i in 0 until 10) {
            val dayData = forecastData.getJSONObject(i).getJSONObject("date")
            weatherData.add(WeatherData(
                    if (i == 0) "Today"
                    else "${dayData.getString("weekday")}, ${dayData.getString("monthname_short")} ${dayData.getString("day")}",
                    forecastText.getJSONObject(2 * i).getString("icon_url"),
                    forecastText.getJSONObject(2 * i).getString("fcttext"),
                    forecastData.getJSONObject(i).getJSONObject("high").getInt("fahrenheit"),
                    forecastData.getJSONObject(i).getJSONObject("low").getInt("fahrenheit")))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int) =
            CustomViewHolder(LayoutInflater.from(parent?.context).inflate(R.layout.row_view, parent, false))

    override fun onBindViewHolder(holder: CustomViewHolder?, position: Int) { holder?.bind(weatherData[position]) }

    override fun getItemCount() = 10

    inner class CustomViewHolder(v: View): RecyclerView.ViewHolder(v) {
        private val icon: ImageView = v.findViewById(R.id.icon)
        private val day: TextView = v.findViewById(R.id.day)
        private val desc: TextView = v.findViewById(R.id.desc)
        private val high: TextView = v.findViewById(R.id.high)
        private val low: TextView = v.findViewById(R.id.low)

        fun bind(weatherData: WeatherData) {
            Glide.with(context).load(weatherData.iconUrl).into(icon)
            day.text = weatherData.day
            desc.text = weatherData.desc
            high.text = "${weatherData.high} °F"
            low.text = "${weatherData.low} °F"
        }
    }

    data class WeatherData(val day: String, val iconUrl: String, val desc: String, val high: Int, val low: Int)
}
