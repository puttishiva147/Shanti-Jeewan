package com.ezflexz.shantijeevan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.wifi.WifiManager

/**
 * Single source of truth for the device-level Instant Detection state.
 * Every broadcast re-reads live system state instead of trusting intent extras,
 * so a delayed/out-of-order broadcast cannot restore a stale state.
 */
object InstantDetectionState {
    fun sync(context: Context): Boolean {
        val app = context.applicationContext
        val wifiOn = try {
            val wifi = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifi.isWifiEnabled
        } catch (_: Throwable) { false }

        val locationOn = try {
            val location = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (android.os.Build.VERSION.SDK_INT >= 28) location.isLocationEnabled
            else location.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                location.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Throwable) { false }

        val enabled = wifiOn && locationOn
        val prefs = app.getSharedPreferences("shanti", Context.MODE_PRIVATE)
        val previousMode = prefs.getBoolean("instant_detection_mode", false)
        val previousWifi = prefs.getBoolean("instant_detection_wifi_on", false)
        val previousLocation = prefs.getBoolean("instant_detection_location_on", false)
        val changed = previousMode != enabled || previousWifi != wifiOn || previousLocation != locationOn
        val stateText = when {
            enabled -> "Instant Detection active — Wi-Fi and Location are ON"
            !wifiOn && !locationOn -> "Instant Detection paused — Wi-Fi and Location are OFF"
            !wifiOn -> "Instant Detection paused — Wi-Fi is OFF"
            else -> "Instant Detection paused — Location is OFF"
        }
        // One atomic preference transaction prevents the UI and background receivers
        // from observing a new combined mode alongside old individual signal values.
        val editor = prefs.edit()
            .putBoolean("instant_detection_mode", enabled)
            .putBoolean("instant_detection_wifi_on", wifiOn)
            .putBoolean("instant_detection_location_on", locationOn)
            .putString("instant_detection_state", stateText)
        if (changed) editor.putLong("instant_detection_state_changed_at", System.currentTimeMillis())
        editor.commit()
        return enabled
    }
}

class InstantDetectionBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            InstantDetectionState.sync(context)
        }
    }
}
