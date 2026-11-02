package com.ezflexz.shantijeevan

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

class LocationModeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LocationManager.MODE_CHANGED_ACTION) return
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val prefs = context.getSharedPreferences("shanti", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("active", false)) return

        if (!locationManager.isLocationEnabled) {
            try {
                LocationServices.getGeofencingClient(context).removeGeofences(geofencePendingIntent(context))
            } catch (_: Exception) {}
            // GPS is no longer a valid presence signal after Location is disabled.
            prefs.edit().putBoolean("inside_home_gps", false).apply()
            HomeWifiReceiver.evaluate(context)
            if (!prefs.getBoolean("inside_home_wifi", false)) {
                prefs.edit().putString("last_event", "Location OFF — Wi-Fi fallback active, waiting for saved home Wi-Fi")
                    .putLong("last_event_time", System.currentTimeMillis()).apply()
            }
        } else {
            registerGeofence(context, prefs)
            HomeWifiReceiver.evaluate(context)
        }
    }

    private fun registerGeofence(context: Context, prefs: android.content.SharedPreferences) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val background = Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        if ((!fine && !coarse) || !background) return
        val lat = prefs.getString("lat", null)?.toDoubleOrNull() ?: return
        val lng = prefs.getString("lng", null)?.toDoubleOrNull() ?: return
        val fence = Geofence.Builder()
            .setRequestId("SHANTI_HOME")
            .setCircularRegion(lat, lng, prefs.getInt("radius", 30).toFloat())
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .setNotificationResponsiveness(0)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(fence).build()
        try {
            LocationServices.getGeofencingClient(context).addGeofences(request, geofencePendingIntent(context))
        } catch (_: SecurityException) {}
    }

    private fun geofencePendingIntent(context: Context): android.app.PendingIntent =
        android.app.PendingIntent.getBroadcast(context, 120, Intent(context, HomeGeofenceReceiver::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE)
}