package com.ezflexz.shantijeevan

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class ShantiQuickTileService : TileService() {
    private fun refreshTile() {
        val prefs = getSharedPreferences("shanti", MODE_PRIVATE)
        val active = prefs.getBoolean("active", false)
        qsTile?.apply {
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "Shanti Jeewan"
            subtitle = if (active) "Automation ON" else "Automation OFF"
            contentDescription = if (active) "Turn Shanti Jeewan automation off" else "Turn Shanti Jeewan automation on"
            icon = Icon.createWithResource(this@ShantiQuickTileService, R.drawable.ic_shanti)
            updateTile()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("QUICK_TILE_TOGGLE", true)
        }
        startActivityAndCollapse(launch)
    }
}
