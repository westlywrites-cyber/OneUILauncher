package com.westly.oneuilauncher.data

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class WeatherInfo(
    val temperatureC: Int,
    val code: Int,
    val isDay: Boolean,
    val updatedAt: Long
)

enum class WeatherKind { CLEAR, PARTLY, CLOUDY, FOG, RAIN, STORM, SNOW }

/**
 * Current weather for one fixed city, from the free Open-Meteo service (no account or key).
 * The last result is saved on the phone, so the widget still shows something when offline.
 */
object WeatherRepository {

    const val CITY = "Lagos"
    private const val LATITUDE = 6.5244
    private const val LONGITUDE = 3.3792

    private const val PREFS = "weather_cache"
    private const val MAX_AGE_MS = 30 * 60 * 1000L

    fun cached(context: Context): WeatherInfo? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains("temp")) return null
        return WeatherInfo(
            temperatureC = prefs.getInt("temp", 0),
            code = prefs.getInt("code", 0),
            isDay = prefs.getBoolean("day", true),
            updatedAt = prefs.getLong("time", 0L)
        )
    }

    fun isFresh(info: WeatherInfo?): Boolean =
        info != null && System.currentTimeMillis() - info.updatedAt < MAX_AGE_MS

    /** Blocking network call: run it on a background thread. Returns null if anything fails. */
    fun fetch(context: Context): WeatherInfo? {
        return try {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$LATITUDE&longitude=$LONGITUDE" +
                    "&current=temperature_2m,weather_code,is_day&timezone=auto"
            )
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.requestMethod = "GET"
            try {
                if (connection.responseCode != 200) {
                    null
                } else {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val current = JSONObject(body).getJSONObject("current")
                    val info = WeatherInfo(
                        temperatureC = Math.round(current.getDouble("temperature_2m")).toInt(),
                        code = current.getInt("weather_code"),
                        isDay = current.getInt("is_day") == 1,
                        updatedAt = System.currentTimeMillis()
                    )
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putInt("temp", info.temperatureC)
                        .putInt("code", info.code)
                        .putBoolean("day", info.isDay)
                        .putLong("time", info.updatedAt)
                        .apply()
                    info
                }
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun kindOf(code: Int): WeatherKind = when (code) {
        0 -> WeatherKind.CLEAR
        1, 2 -> WeatherKind.PARTLY
        3 -> WeatherKind.CLOUDY
        45, 48 -> WeatherKind.FOG
        in 51..67, in 80..82 -> WeatherKind.RAIN
        in 71..77, 85, 86 -> WeatherKind.SNOW
        in 95..99 -> WeatherKind.STORM
        else -> WeatherKind.CLOUDY
    }

    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Foggy"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Showers"
        85, 86 -> "Snow showers"
        in 95..99 -> "Thunderstorm"
        else -> "Cloudy"
    }
}
