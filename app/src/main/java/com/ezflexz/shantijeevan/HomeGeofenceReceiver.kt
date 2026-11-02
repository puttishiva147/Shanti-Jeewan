package com.ezflexz.shantijeevan

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import androidx.work.*
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class HomeGeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val prefs = context.getSharedPreferences("shanti", Context.MODE_PRIVATE)
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val policy = context.getSystemService(NotificationManager::class.java)
        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                if (policy.isNotificationPolicyAccessGranted) {
                    if (audio.ringerMode != AudioManager.RINGER_MODE_SILENT)
                        prefs.edit().putInt("previous_mode", audio.ringerMode).apply()
                    audio.ringerMode = AudioManager.RINGER_MODE_SILENT
                }
                val minutes = prefs.getInt("interval", 20).toLong()
                val reminder = PeriodicWorkRequestBuilder<ReminderWorker>(minutes, TimeUnit.MINUTES)
                    .setInitialDelay(minutes, TimeUnit.MINUTES).build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "home-reminder", ExistingPeriodicWorkPolicy.UPDATE, reminder)
                ReminderWorker.speak(context, reminderText(prefs))
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                WorkManager.getInstance(context).cancelUniqueWork("home-reminder")
                if (policy.isNotificationPolicyAccessGranted) {
                    audio.ringerMode = prefs.getInt("previous_mode", AudioManager.RINGER_MODE_NORMAL)
                }
            }
        }
    }
    private fun reminderText(prefs: android.content.SharedPreferences) =
        "${prefs.getString("name","User")}, ${prefs.getString("message","Aapka phone Silent Mode mein hai.")}"
}

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    private val appContext = context.applicationContext
    override fun doWork(): Result {
        val prefs = appContext.getSharedPreferences("shanti", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("active", false)) return Result.success()
        val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val policy = appContext.getSystemService(NotificationManager::class.java)
        if (policy.isNotificationPolicyAccessGranted && audio.ringerMode != AudioManager.RINGER_MODE_SILENT)
            audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        speak(appContext, "${prefs.getString("name","User")}, ${prefs.getString("message","Aapka phone Silent Mode mein hai.")}")
        return Result.success()
    }
    companion object {
        fun speak(context: Context, text: String) {
            val ready = CountDownLatch(1)
            var tts: TextToSpeech? = null
            tts = TextToSpeech(context.applicationContext) { state ->
                if (state == TextToSpeech.SUCCESS) {
                    tts?.language = Locale("hi", "IN")
                    tts?.setSpeechRate(0.9f)
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "shanti-reminder")
                }
                ready.countDown()
            }
            try { ready.await(7, TimeUnit.SECONDS); Thread.sleep(2500) } catch (_: InterruptedException) {}
            tts?.stop()
            tts?.shutdown()
        }
    }
}