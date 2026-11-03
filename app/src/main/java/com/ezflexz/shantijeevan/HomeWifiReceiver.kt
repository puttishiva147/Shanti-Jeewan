package com.ezflexz.shantijeevan

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.LinkProperties
import java.net.Inet4Address
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class HomeWifiReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WifiManager.NETWORK_STATE_CHANGED_ACTION &&
            intent.action != WifiManager.WIFI_STATE_CHANGED_ACTION &&
            intent.action != "android.net.conn.CONNECTIVITY_CHANGE") return
        // Always refresh combined device state, even when home-Wi-Fi fallback is disabled.
        // Read live system state rather than trusting possibly stale broadcast extras.
        InstantDetectionState.sync(context)
        val prefs = context.getSharedPreferences("shanti", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("active", false) || !prefs.getBoolean("wifi_fallback_enabled", false)) return
        // Inspect the currently active networks; disconnect broadcasts may be stale.
        evaluate(context)
    }

    companion object {
        fun getWifiFingerprint(context: Context): String? {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val wifiNetwork = cm.allNetworks.firstOrNull { network ->
                cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            } ?: return null
            val lp: LinkProperties = cm.getLinkProperties(wifiNetwork) ?: return null
            val gateway = lp.routes.mapNotNull { route ->
                val address = route.gateway
                if (route.isDefaultRoute && address is Inet4Address) address.hostAddress else null
            }.sorted().firstOrNull() ?: return null
            // The phone DHCP address changes on reconnect; use the router gateway and DNS only.
            val dns = lp.dnsServers.filterIsInstance<Inet4Address>()
                .mapNotNull { it.hostAddress }.sorted()
            return "gw=$gateway|dns=${dns.joinToString(",")}"
        }

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
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val wifiConnected = cm.allNetworks.any { network ->
                cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
            if (!wifiConnected) {
                if (prefs.getBoolean("inside_home_wifi", false)) leaveHome(context)
                else prefs.edit().putString("last_event", "Wi-Fi disconnected — waiting for home network")
                    .putLong("last_event_time", System.currentTimeMillis()).apply()
                return
            }
            val currentFingerprint = getWifiFingerprint(context)
            val savedFingerprint = prefs.getString("home_wifi_fingerprint", null)
            val homeSsid = prefs.getString("home_wifi_ssid", null)
            // Prefer the saved router/LAN fingerprint. It remains readable on many
            // Android builds even when Location is OFF and SSID is privacy-redacted.
            val connectedHome = if (savedFingerprint != null && currentFingerprint != null) {
                val savedGateway = savedFingerprint.substringBefore("|dns=")
                val currentGateway = currentFingerprint.substringBefore("|dns=")
                val savedDns = savedFingerprint.substringAfter("|dns=", "")
                val currentDns = currentFingerprint.substringAfter("|dns=", "")
                savedGateway == currentGateway &&
                    (savedDns.isBlank() || currentDns.isBlank() || savedDns == currentDns)
            } else {
                homeSsid != null && ssid == homeSsid
            }
            if (currentFingerprint == null && ssid == null) {
                prefs.edit()
                    .putString("last_event", "Wi-Fi is connected, but Android hides both network identity and router details")
                    .putLong("last_event_time", System.currentTimeMillis())
                    .apply()
                return
            }
            if (savedFingerprint == null && homeSsid == null) return
            val insideWifi = prefs.getBoolean("inside_home_wifi", false)
            if (connectedHome && !insideWifi) enterHome(context)
            else if (!connectedHome && insideWifi) leaveHome(context)
        }

        private fun enterHome(context: Context) {
            val prefs = context.getSharedPreferences("shanti", Context.MODE_PRIVATE)
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val policy = context.getSystemService(NotificationManager::class.java)
            val alreadyHome = prefs.getBoolean("inside_home", false)
            val editor = prefs.edit()
            if (!alreadyHome) {
                editor.putInt("previous_mode", audio.ringerMode)
                    .putInt("previous_filter", policy.currentInterruptionFilter)
                    .putInt("previous_ring_volume", audio.getStreamVolume(AudioManager.STREAM_RING))
            }
            editor.putBoolean("inside_home_wifi", true)
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
                        .setContentText("Saved home Wi-Fi connected. Your selected phone mode was applied.")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true).build())
            } catch (_: SecurityException) {}
        }

        private fun leaveHome(context: Context) {
            val prefs = context.getSharedPreferences("shanti", Context.MODE_PRIVATE)
            val gpsStillInside = prefs.getBoolean("inside_home_gps", false)
            if (!gpsStillInside) restorePreviousMode(context)
            prefs.edit().putBoolean("inside_home_wifi", false)
                .putBoolean("inside_home", gpsStillInside)
                .putString("last_event", "Home Wi-Fi disconnected — previous phone mode restored")
                .putLong("last_event_time", System.currentTimeMillis()).apply()
            if (!gpsStillInside) {
                androidx.work.WorkManager.getInstance(context).cancelUniqueWork("home-reminder")
                androidx.work.WorkManager.getInstance(context).cancelUniqueWork("home-reminder-first")
            }
        }
    }
}