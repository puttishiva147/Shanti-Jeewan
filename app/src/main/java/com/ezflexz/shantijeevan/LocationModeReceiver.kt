package com.ezflexz.shantijeevan

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.media.AudioManager
import com.google.android.gms.location.LocationServices
import androidx.work.WorkManager

class LocationModeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LocationManager.MODE_CHANGED_ACTION) return
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (locationManager.isLocationEnabled) return
        val prefs = context.getSharedPreferences("shanti", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("active", false)) return
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val policy = context.getSystemService(NotificationManager::class.java)
        if (prefs.getBoolean("inside_home", false)) {
            audio.setStreamVolume(AudioManager.STREAM_RING, prefs.getInt("previous_ring_volume", audio.getStreamMaxVolume(AudioManager.STREAM_RING)), 0)
            if (policy.isNotificationPolicyAccessGranted) {
                audio.ringerMode = prefs.getInt("previous_mode", AudioManager.RINGER_MODE_NORMAL)
                policy.setInterruptionFilter(prefs.getInt("previous_filter", NotificationManager.INTERRUPTION_FILTER_ALL))
            }
        }
        prefs.edit().putBoolean("inside_home", false).putBoolean("active", false)
            .putString("last_event", "Location turned off — restored previous phone mode")
            .putLong("last_event_time", System.currentTimeMillis()).apply()
        WorkManager.getInstance(context).cancelUniqueWork("home-reminder")
        WorkManager.getInstance(context).cancelUniqueWork("home-reminder-first")
        try { LocationServices.getGeofencingClient(context).removeGeofences(
            android.app.PendingIntent.getBroadcast(context, 120, Intent(context, HomeGeofenceReceiver::class.java),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE)
        ) } catch (_: Exception) {}
    }
}
