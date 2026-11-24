package com.custom.tvlauncher

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_settings")

data class CityConfig(
    val city: String,
    val country: String,
    val lat: Double,
    val lon: Double
)

object CityPresets {
    val list = listOf(
        CityConfig("South Tangerang", "Indonesia", -6.2886, 106.7179),
        CityConfig("Jakarta", "Indonesia", -6.2088, 106.8456),
        CityConfig("Bandung", "Indonesia", -6.9175, 107.6191),
        CityConfig("Surabaya", "Indonesia", -7.2575, 112.7521),
        CityConfig("Yogyakarta", "Indonesia", -7.7956, 110.3695),
        CityConfig("Medan", "Indonesia", 3.5952, 98.6722),
        CityConfig("Makassar", "Indonesia", -5.1477, 119.4327)
    )
}

class LocationSettings(private val context: Context) {
    private object Keys {
        val CITY = stringPreferencesKey("city")
        val COUNTRY = stringPreferencesKey("country")
        val LAT = doublePreferencesKey("lat")
        val LON = doublePreferencesKey("lon")
    }

    val cityFlow: Flow<CityConfig> = context.dataStore.data.map { prefs ->
        CityConfig(
            city = prefs[Keys.CITY] ?: "South Tangerang",
            country = prefs[Keys.COUNTRY] ?: "Indonesia",
            lat = prefs[Keys.LAT] ?: -6.2886,
            lon = prefs[Keys.LON] ?: 106.7179
        )
    }

    suspend fun saveCity(config: CityConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CITY] = config.city
            prefs[Keys.COUNTRY] = config.country
            prefs[Keys.LAT] = config.lat
            prefs[Keys.LON] = config.lon
        }
    }
}
