package com.ezflexz.shantijeevan

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
                    if (!prefs.getBoolean("inside_home", false)) {
                        prefs.edit().putInt("previous_mode", audio.ringerMode).apply()
                    }
                    audio.ringerMode = AudioManager.RINGER_MODE_SILENT
                }
                prefs.edit().putBoolean("inside_home", true).apply()
                val minutes = prefs.getInt("interval", 20).toLong().coerceAtLeast(15)
                val reminder = PeriodicWorkRequestBuilder<ReminderWorker>(minutes, TimeUnit.MINUTES)
                    .setInitialDelay(minutes, TimeUnit.MINUTES).build()
                val workManager = WorkManager.getInstance(context)
                workManager.enqueueUniqueWork(
                    "home-reminder-first",
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<ReminderWorker>().build()
                )
                workManager.enqueueUniquePeriodicWork(
                    "home-reminder", ExistingPeriodicWorkPolicy.UPDATE, reminder)
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                WorkManager.getInstance(context).cancelUniqueWork("home-reminder")
                if (policy.isNotificationPolicyAccessGranted && prefs.getBoolean("inside_home", false)) {
                    audio.ringerMode = prefs.getInt("previous_mode", AudioManager.RINGER_MODE_NORMAL)
                }
                prefs.edit().putBoolean("inside_home", false).apply()
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
        if (!prefs.getBoolean("active", false) || !prefs.getBoolean("inside_home", false)) return Result.success()
        val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val policy = appContext.getSystemService(NotificationManager::class.java)
        if (policy.isNotificationPolicyAccessGranted && audio.ringerMode != AudioManager.RINGER_MODE_SILENT)
            audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        speak(appContext, "${prefs.getString("name","User")}, ${prefs.getString("message","Aapka phone Silent Mode mein hai.")}")
        return Result.success()
    }
    companion object {
        fun speak(context: Context, text: String) {
            val finished = CountDownLatch(1)
            var tts: TextToSpeech? = null
            tts = TextToSpeech(context.applicationContext) { state ->
                if (state == TextToSpeech.SUCCESS) {
                    val engine = tts ?: run { finished.countDown(); return@TextToSpeech }
                    engine.language = Locale("hi", "IN")
                    engine.setSpeechRate(0.88f)
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) { finished.countDown() }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) { finished.countDown() }
                        override fun onError(utteranceId: String?, errorCode: Int) { finished.countDown() }
                    })
                    val savedVolume = context.getSharedPreferences("shanti", Context.MODE_PRIVATE).getInt("voice_volume", 45).coerceIn(0, 100) / 100f
                    val options = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, savedVolume) }
                    engine.speak(text, TextToSpeech.QUEUE_FLUSH, options, "shanti-reminder")
                } else finished.countDown()
            }
            try { finished.await(25, TimeUnit.SECONDS) } catch (_: InterruptedException) {}
            tts?.stop()
            tts?.shutdown()
        }
    }
}