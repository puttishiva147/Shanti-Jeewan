package com.ezflexz.shantijeevan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import com.google.android.gms.location.LocationServices

class LocationModeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LocationManager.MODE_CHANGED_ACTION) return
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val prefs = context.getSharedPreferences("shanti", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("active", false)) return
        if (!locationManager.isLocationEnabled) {
            try {
                LocationServices.getGeofencingClient(context).removeGeofences(
                    android.app.PendingIntent.getBroadcast(context, 120, Intent(context, HomeGeofenceReceiver::class.java),
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE)
                )
            } catch (_: Exception) {}
            HomeWifiReceiver.evaluate(context)
            prefs.edit().putString("last_event", "Location OFF — checking saved home Wi-Fi")
                .putLong("last_event_time", System.currentTimeMillis()).apply()
        }
    }
}