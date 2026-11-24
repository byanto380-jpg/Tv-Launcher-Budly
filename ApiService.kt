package com.custom.tvlauncher

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// --- MODELS ---
data class PrayerResponse(val data: PrayerData)
data class PrayerData(val timings: Timings, val date: DateInfo)
data class Timings(val Fajr: String, val Dhuhr: String, val Asr: String, val Maghrib: String, val Isha: String)
data class DateInfo(val hijri: HijriDate)
data class HijriDate(val date: String, val month: HijriMonth, val year: String)
data class HijriMonth(val en: String, val ar: String)

data class WeatherResponse(val current_weather: CurrentWeather)
data class CurrentWeather(val temperature: Double, val weathercode: Int)

data class SportsResponse(val events: List<MatchEvent>?)
data class MatchEvent(val strEvent: String, val dateEvent: String, val strTime: String, val strLeague: String)

// --- INTERFACES ---
interface AladhanApi {
    @GET("v1/timingsByCity")
    suspend fun getPrayerTimes(
        @Query("city") city: String = "South Tangerang",
        @Query("country") country: String = "Indonesia",
        @Query("method") method: Int = 20
    ): PrayerResponse
}

interface WeatherApi {
    @GET("v1/forecast?current_weather=true")
    suspend fun getWeather(
        @Query("latitude") lat: Double = -6.2886,
        @Query("longitude") lon: Double = 106.7179
    ): WeatherResponse
}

interface SportsApi {
    @GET("api/v1/json/3/eventsnextleague.php")
    suspend fun getNextMatches(
        @Query("id") leagueId: String = "4328"
    ): SportsResponse
}

object NetworkClient {
    val aladhan: AladhanApi = Retrofit.Builder()
        .baseUrl("https://api.aladhan.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build().create(AladhanApi::class.java)

    val weather: WeatherApi = Retrofit.Builder()
        .baseUrl("https://api.open-meteo.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build().create(WeatherApi::class.java)

    val sports: SportsApi = Retrofit.Builder()
        .baseUrl("https://www.thesportsdb.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build().create(SportsApi::class.java)
}
