package com.example.jadwaladzan

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("adzan", Context.MODE_PRIVATE)

    // Default: Cikarang. Ganti sesuai kebutuhan.
    fun place(c: Context): Place = sp(c).let {
        Place(
            it.getString("name", "Cikarang, Jawa Barat")!!,
            it.getString("lat", "-6.2614")!!.toDouble(),
            it.getString("lng", "107.1528")!!.toDouble()
        )
    }

    fun savePlace(c: Context, p: Place) {
        sp(c).edit().putString("name", p.name).putString("lat", p.lat.toString())
            .putString("lng", p.lng.toString()).apply()
    }

    fun enabled(c: Context) = sp(c).getBoolean("alarm", false)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("alarm", v).apply()
}

object Scheduler {
    const val CHANNEL = "adzan"

    fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Waktu adzan", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun pi(c: Context, code: Int, name: String): PendingIntent {
        val i = Intent(c, AdzanReceiver::class.java).putExtra("name", name)
        return PendingIntent.getBroadcast(
            c, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Memanggil jaringan: jalankan di background thread. */
    fun refresh(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        if (!Store.enabled(c)) {
            for (code in 0 until 10) am.cancel(pi(c, code, ""))
            return
        }
        val p = Store.place(c)
        val now = System.currentTimeMillis()
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        for (off in 0L..1L) {
            val date = LocalDate.now().plusDays(off)
            val s = try { fetchSchedule(p, date, "daily") } catch (e: Exception) { continue }
            val day = s.days.firstOrNull() ?: continue
            val zone = ZoneOffset.ofTotalSeconds((s.tz * 3600).toInt())
            ADZAN.forEachIndexed { idx, k ->
                val t = day.times[k] ?: ""
                if (t.isEmpty()) return@forEachIndexed
                val at = date.atTime(LocalTime.parse(t)).toInstant(zone).toEpochMilli()
                if (at > now) {
                    val pend = pi(c, (off * 5 + idx).toInt(), k)
                    if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pend)
                    else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pend)
                }
            }
        }
    }
}

class AdzanReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val label = LABELS[i.getStringExtra("name")] ?: "Sholat"
        Scheduler.ensureChannel(c)
        val open = PendingIntent.getActivity(
            c, 99, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(c, Scheduler.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Waktu $label")
            .setContentText("Telah masuk waktu $label untuk ${Store.place(c).name}")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        c.getSystemService(NotificationManager::class.java).notify(label.hashCode(), n)

        val pr = goAsync()
        Thread { try { Scheduler.refresh(c) } finally { pr.finish() } }.start()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val pr = goAsync()
        Thread { try { Scheduler.refresh(c) } finally { pr.finish() } }.start()
    }
}
