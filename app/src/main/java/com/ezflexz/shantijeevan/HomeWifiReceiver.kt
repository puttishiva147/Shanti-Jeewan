package com.ezflexz.shantijeevan

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class HomeWifiReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WifiManager.NETWORK_STATE_CHANGED_ACTION &&
            intent.action != "android.net.conn.CONNECTIVITY_CHANGE") return
        val prefs = context.getSharedPreferences("shanti", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("active", false) || !prefs.getBoolean("wifi_fallback_enabled", false)) return
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        if (locationManager.isLocationEnabled) return
        val networkInfo = intent.getParcelableExtra<android.net.NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
        if (networkInfo != null && !networkInfo.isConnected) {
            if (prefs.getBoolean("inside_home", false)) leaveHome(context)
            return
        }
        evaluate(context)
    }

    companion object {
        fun restorePreviousMode(context: Context) {
            val prefs = context.getSharedPreferences("shanti", Context.MODE_PRIVATE)
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val policy = context.getSystemService(NotificationManager::class.java)
            audio.setStreamVolume(AudioManager.STREAM_RING,
                prefs.getInt("previous_ring_volume", audio.getStreamMaxVolume(AudioManager.STREAM_RING)), 0)
            if (policy.isNotificationPolicyAccessGranted) {
                audio.ringerMode = prefs.getInt("previous_mode", AudioManager.RINGER_MODE_NORMAL)
                policy.setInterruptionFilter(prefs.getInt("previous_filter", NotificationManager.INTERRUPTION_FILTER_ALL))
            }
        }

        fun evaluate(context: Context) {
            val prefs = context.getSharedPreferences("shanti", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("active", false) || !prefs.getBoolean("wifi_fallback_enabled", false)) return
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ssid = try {
                @Suppress("DEPRECATION")
                wifi.connectionInfo?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
            } catch (_: SecurityException) { null }
            if (ssid == null) {
                // Android 10+ can redact the connected SSID when Location is OFF.
                // Keep the automation enabled, but expose the real blocker in app status.
                prefs.edit()
                    .putString("last_event", "Wi-Fi connected, but Android hid the network name while Location is OFF")
                    .putLong("last_event_time", System.currentTimeMillis())
                    .apply()
                return
            }
            val homeSsid = prefs.getString("home_wifi_ssid", null) ?: return
            val connectedHome = ssid == homeSsid
            val inside = prefs.getBoolean("inside_home", false)
            if (connectedHome && !inside) enterHome(context)
            else if (!connectedHome && inside) leaveHome(context)
        }

        private fun enterHome(context: Context) {
            val prefs = context.getSharedPreferences("shanti", Context.MODE_PRIVATE)
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val policy = context.getSystemService(NotificationManager::class.java)
            prefs.edit()
                .putInt("previous_mode", audio.ringerMode)
                .putInt("previous_filter", policy.currentInterruptionFilter)
                .putInt("previous_ring_volume", audio.getStreamVolume(AudioManager.STREAM_RING))
                .putBoolean("inside_home", true)
                .putString("last_event", "Home Wi-Fi detected — arrival")
                .putLong("last_event_time", System.currentTimeMillis())
                .apply()
            val mode = prefs.getString("arrival_mode", "silent")
            try {
                when (mode) {
                    "vibrate" -> audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    "low_sound" -> {
                        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_RING)
                        audio.setStreamVolume(AudioManager.STREAM_RING, (max * .25f).toInt().coerceAtLeast(1), 0)
                    }
                    else -> audio.ringerMode = AudioManager.RINGER_MODE_SILENT
                }
            } catch (_: SecurityException) {}
            val channel = "shanti_arrival"
            if (Build.VERSION.SDK_INT >= 26) {
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(android.app.NotificationChannel(channel, "Home arrival status", NotificationManager.IMPORTANCE_HIGH))
            }
            val label = when (mode) { "vibrate" -> "Vibrate"; "low_sound" -> "Low sound (25%)"; else -> "Silent" }
            try {
                NotificationManagerCompat.from(context).notify(1202,
                    NotificationCompat.Builder(context, channel)
                        .setSmallIcon(R.drawable.ic_shanti)
                        .setContentTitle("Home Wi-Fi detected — $label ON")
                        .setContentText("Location is OFF. Home Wi-Fi fallback applied your selected phone mode.")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true).build())
            } catch (_: SecurityException) {}
        }

        private fun leaveHome(context: Context) {
            val prefs = context.getSharedPreferences("shanti", Context.MODE_PRIVATE)
            restorePreviousMode(context)
            prefs.edit().putBoolean("inside_home", false)
                .putString("last_event", "Home Wi-Fi disconnected — previous phone mode restored")
                .putLong("last_event_time", System.currentTimeMillis()).apply()
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork("home-reminder")
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork("home-reminder-first")
        }
    }
}