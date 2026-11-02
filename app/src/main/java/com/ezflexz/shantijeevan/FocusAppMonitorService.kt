package com.ezflexz.shantijeevan

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import java.util.Calendar

class FocusAppMonitorService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastBlockedPackage = ""
    private var lastCheckinShown = 0L
    private val loop = object : Runnable {
        override fun run() {
            evaluateForeground()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onServiceConnected() { handler.post(loop) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { evaluateForeground() }
    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(loop)
        restoreCallVolume()
        super.onDestroy()
    }

    private fun evaluateForeground() {
        val p = getSharedPreferences("focus_beta", MODE_PRIVATE)
        if (!p.getBoolean("enabled", false)) { finishSession(); return }
        val now = System.currentTimeMillis()

        if (p.getBoolean("periodic_checkin_enabled", false)) {
            val next = p.getLong("next_checkin_at", now + 45L * 60L * 1000L)
            if (now >= next && now - lastCheckinShown > 60_000L) {
                lastCheckinShown = now
                p.edit().putLong("next_checkin_at", now + 45L * 60L * 1000L).apply()
                showReminder("periodic")
            }
        }

        val packages = p.getStringSet("packages", emptySet()) ?: emptySet()
        val existingUntil = p.getLong("session_until", 0L)
        if (existingUntil > 0L && now >= existingUntil) { finishSession(); return }
        if (packages.isEmpty() || (existingUntil == 0L && !insideSchedule(p))) return
        val root = rootInActiveWindow ?: return
        val current = root.packageName?.toString() ?: return
        if (current == packageName || !packages.contains(current)) { lastBlockedPackage = ""; return }

        var until = p.getLong("session_until", 0L)
        if (until == 0L) {
            until = now + 45L * 60L * 1000L
            p.edit().putLong("session_until", until).putLong("break_until", 0L).apply()
            if (!p.getBoolean("manual_mode_off", false)) lowerCallVolume()
        }
        if (now >= until) { finishSession(); return }
        val breakUntil = p.getLong("break_until", 0L)
        if (now < breakUntil) { lastBlockedPackage = ""; return }
        if (lastBlockedPackage != current) {
            lastBlockedPackage = current
            showReminder("app")
        }
    }

    private fun showReminder(reason: String) {
        val i = Intent(this, FocusBlockerActivity::class.java)
            .putExtra("reminder_reason", reason)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        try { startActivity(i) } catch (_: Throwable) {}
    }

    private fun insideSchedule(p: android.content.SharedPreferences): Boolean {
        val c = Calendar.getInstance()
        val now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val start = p.getInt("start_minute", 900)
        val end = p.getInt("end_minute", 1020)
        return if (start <= end) now in start until end else now >= start || now < end
    }

    private fun lowerCallVolume() {
        val p = getSharedPreferences("focus_beta", MODE_PRIVATE)
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_RING)
        if (!p.getBoolean("volume_saved", false)) {
            p.edit().putInt("previous_ring_volume", audio.getStreamVolume(AudioManager.STREAM_RING)).putBoolean("volume_saved", true).apply()
        }
        audio.setStreamVolume(AudioManager.STREAM_RING, (max * 0.30f).toInt().coerceAtLeast(1), 0)
    }

    private fun restoreCallVolume() {
        val p = getSharedPreferences("focus_beta", MODE_PRIVATE)
        if (!p.getBoolean("volume_saved", false)) return
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        audio.setStreamVolume(AudioManager.STREAM_RING, p.getInt("previous_ring_volume", audio.getStreamMaxVolume(AudioManager.STREAM_RING)), 0)
        p.edit().remove("volume_saved").remove("previous_ring_volume").apply()
    }

    private fun finishSession() {
        val p = getSharedPreferences("focus_beta", MODE_PRIVATE)
        p.edit().remove("session_until").remove("break_until").apply()
        if (!p.getBoolean("manual_mode_off", false)) restoreCallVolume()
        lastBlockedPackage = ""
    }
}