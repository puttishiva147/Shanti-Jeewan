package com.ezflexz.shantijeevan

import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.google.android.gms.location.LocationServices
import androidx.work.WorkManager

class ShantiQuickTileService : TileService() {
    private fun refreshTile() {
        val active = getSharedPreferences("shanti", MODE_PRIVATE).getBoolean("active", false)
        qsTile?.apply {
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "Shanti Jeewan"
            subtitle = if (active) "Automation ON" else "Automation OFF"
            contentDescription = if (active) "Turn Shanti Jeewan automation off" else "Turn Shanti Jeewan automation on"
            icon = Icon.createWithResource(this@ShantiQuickTileService, R.drawable.ic_shanti)
            updateTile()
        }
    }

    override fun onStartListening() { super.onStartListening(); refreshTile() }

    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences("shanti", MODE_PRIVATE)
        if (prefs.getBoolean("active", false)) {
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            try {
                audio.setRingerMode(AudioManager.RINGER_MODE_NORMAL)
                audio.setStreamVolume(AudioManager.STREAM_RING, audio.getStreamMaxVolume(AudioManager.STREAM_RING), 0)
            } catch (_: SecurityException) {}
            getSharedPreferences("focus_beta", MODE_PRIVATE).edit()
                .putBoolean("manual_mode_off", true).putBoolean("focus_mode_on", false)
                .remove("volume_saved").remove("previous_ring_volume").apply()
            prefs.edit().putBoolean("active", false).putBoolean("inside_home", false)
                .putString("last_event", "Automation OFF from Quick Settings — Normal mode and full ringtone volume")
                .putLong("last_event_time", System.currentTimeMillis()).apply()
            try {
                LocationServices.getGeofencingClient(this).removeGeofences(
                    android.app.PendingIntent.getBroadcast(this, 120, Intent(this, HomeGeofenceReceiver::class.java),
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE)
                )
            } catch (_: Exception) {}
            WorkManager.getInstance(this).cancelUniqueWork("home-reminder")
            WorkManager.getInstance(this).cancelUniqueWork("home-reminder-first")
            refreshTile()
        } else {
            val launch = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("QUICK_TILE_TOGGLE", true)
            }
            startActivityAndCollapse(launch)
        }
    }
}