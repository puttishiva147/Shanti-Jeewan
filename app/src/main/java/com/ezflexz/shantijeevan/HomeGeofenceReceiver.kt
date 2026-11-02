package com.ezflexz.shantijeevan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
                val wasInside = prefs.getBoolean("inside_home", false)
                if (!wasInside && policy.isNotificationPolicyAccessGranted) {
                    prefs.edit().putInt("previous_mode", audio.ringerMode).apply()
                }
                prefs.edit().putString("last_event", "Arrived home")
                    .putLong("last_event_time", System.currentTimeMillis())
                    .putBoolean("inside_home", true).apply()

                val silentApplied = policy.isNotificationPolicyAccessGranted
                if (silentApplied) audio.ringerMode = AudioManager.RINGER_MODE_SILENT
                showArrivalNotification(context, silentApplied)

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
                val wasInside = prefs.getBoolean("inside_home", false)
                prefs.edit().putString("last_event", "Left home")
                    .putLong("last_event_time", System.currentTimeMillis())
                    .putBoolean("inside_home", false).apply()
                WorkManager.getInstance(context).cancelUniqueWork("home-reminder")
                if (policy.isNotificationPolicyAccessGranted && wasInside) {
                    audio.ringerMode = prefs.getInt("previous_mode", AudioManager.RINGER_MODE_NORMAL)
                }
            }
        }
    }

    private fun showArrivalNotification(context: Context, silentApplied: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = "shanti_arrival"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Home arrival status", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Confirms when Shanti Jeewan detects home arrival and enables Silent mode." }
            manager.createNotificationChannel(channel)
        }
        val openApp = PendingIntent.getActivity(
            context, 121, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val detail = if (silentApplied) {
            "Home location detected. Your phone has been switched to Silent mode."
        } else {
            "Home location detected, but Silent mode permission is unavailable."
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_shanti)
            .setContentTitle(if (silentApplied) "You're home — Silent mode ON" else "Home location detected")
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(1201, notification)
        } catch (_: SecurityException) {
            // Android 13+ notification permission may have been revoked by the user.
        }
    }
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
