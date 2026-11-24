package com.custom.tvlauncher

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object PrayerNotifier {
    const val CHANNEL_ID = "prayer_times_channel"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pengingat Waktu Sholat",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi saat masuk waktu sholat fardhu"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun checkAndNotify(context: Context, timings: Timings, cityName: String) {
        ensureChannel(context)

        val now = Date()
        val currentHm = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)

        val schedule = listOf(
            "Subuh" to cleanTime(timings.Fajr),
            "Dzuhur" to cleanTime(timings.Dhuhr),
            "Ashar" to cleanTime(timings.Asr),
            "Maghrib" to cleanTime(timings.Maghrib),
            "Isya" to cleanTime(timings.Isha)
        )

        val prefs = context.getSharedPreferences("prayer_alert_prefs", Context.MODE_PRIVATE)

        schedule.forEach { (prayerName, prayerHm) ->
            if (prayerHm == currentHm) {
                val firedKey = "${todayKey}_${prayerName}"
                val alreadyFired = prefs.getBoolean(firedKey, false)
                if (!alreadyFired) {
                    showNotification(context, prayerName, prayerHm, cityName)
                    prefs.edit().putBoolean(firedKey, true).apply()
                }
            }
        }
    }

    fun cleanTime(raw: String): String {
        val match = Regex("""(\d{2}:\d{2})""").find(raw)
        return match?.groupValues?.getOrNull(1) ?: raw.take(5)
    }

    private fun showNotification(context: Context, prayerName: String, timeHm: String, cityName: String) {
        val notificationId = prayerName.hashCode()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Waktu Sholat $prayerName ($timeHm)")
            .setContentText("Telah masuk waktu sholat $prayerName untuk wilayah $cityName.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Izin notifikasi belum diberikan pada Android 13+
        }
    }

    fun schedulePeriodicCheck(context: Context) {
        val request = PeriodicWorkRequestBuilder<PrayerReminderWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "prayer_reminder_periodic",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}

class PrayerReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val locationSettings = LocationSettings(applicationContext)
            val activeCity = locationSettings.cityFlow.first()
            val response = NetworkClient.aladhan.getPrayerTimes(
                city = activeCity.city,
                country = activeCity.country
            )
            PrayerNotifier.checkAndNotify(
                context = applicationContext,
                timings = response.data.timings,
                cityName = activeCity.city
            )
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
