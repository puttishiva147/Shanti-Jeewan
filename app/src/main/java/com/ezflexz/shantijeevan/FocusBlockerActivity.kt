package com.ezflexz.shantijeevan

import android.app.Activity
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import java.util.Locale

class FocusBlockerActivity : Activity() {
    private var tts: TextToSpeech? = null
    private val handler = Handler(Looper.getMainLooper())
    private var spoken = false
    private var repeatTask: Runnable? = null
    private var reason = "app"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        render()
    }

    private fun render() {
        handler.removeCallbacksAndMessages(null)
        spoken = false
        reason = intent.getStringExtra("reminder_reason") ?: "app"
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val p = getSharedPreferences("focus_beta", MODE_PRIVATE)
        val msg = p.getString("message", "Take a break. Return to what matters.") ?: "Take a break."
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(28, 32, 28, 32); setBackgroundColor(0xFF17211C.toInt())
        }
        root.addView(TextView(this).apply {
            text = if (reason == "periodic") "45-MINUTE CHECK-IN" else "PAUSE & RESET"
            textSize = 13f; letterSpacing = .18f; setTextColor(0xFFB8C8BA.toInt())
        })
        root.addView(TextView(this).apply {
            text = msg; textSize = 27f; gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt()); setPadding(0, 28, 0, 20)
        })
        root.addView(TextView(this).apply {
            text = "Choose how your phone should behave."
            textSize = 14f; setTextColor(0xFFD0D8D1.toInt()); setPadding(0, 0, 0, 16)
        })
        root.addView(Button(this).apply {
            text = "MODE ON • FOCUS"
            setOnClickListener {
                p.edit().putBoolean("manual_mode_off", false).putBoolean("focus_mode_on", true).apply()
                applyFocusVolume()
                Toast.makeText(this@FocusBlockerActivity, "Focus mode ON", Toast.LENGTH_SHORT).show()
                finish()
            }
        })
        root.addView(Button(this).apply {
            text = "MODE OFF • NORMAL + FULL VOLUME"
            setOnClickListener {
                p.edit().putBoolean("manual_mode_off", true).putBoolean("focus_mode_on", false).apply()
                forceNormalVolume()
                Toast.makeText(this@FocusBlockerActivity, "Normal mode restored", Toast.LENGTH_SHORT).show()
                finish()
            }
        })
        if (reason == "app") {
            root.addView(Button(this).apply {
                text = "TAKE 10-MINUTE BREAK"
                setOnClickListener {
                    p.edit().putLong("break_until", System.currentTimeMillis() + 10L * 60L * 1000L).apply()
                    finish()
                }
            })
        }
        root.addView(Button(this).apply { text = "CLOSE"; setOnClickListener { finish() } })
        setContentView(root)
        tts?.stop(); tts?.shutdown()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS && !spoken) {
                tts?.language = Locale.getDefault()
                tts?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "focus-reminder")
                spoken = true
            }
        }
        repeatTask = object : Runnable {
            override fun run() {
                if (!isFinishing) {
                    tts?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "focus-repeat")
                    handler.postDelayed(this, 60_000L)
                }
            }
        }
        handler.postDelayed(repeatTask!!, 60_000L)
    }

    private fun applyFocusVolume() {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_RING)
        audio.setStreamVolume(AudioManager.STREAM_RING, (max * 0.30f).toInt().coerceAtLeast(1), 0)
    }

    private fun forceNormalVolume() {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        audio.setRingerMode(AudioManager.RINGER_MODE_NORMAL)
        audio.setStreamVolume(AudioManager.STREAM_RING, audio.getStreamMaxVolume(AudioManager.STREAM_RING), 0)
        getSharedPreferences("focus_beta", MODE_PRIVATE).edit()
            .putBoolean("manual_mode_off", true).putBoolean("focus_mode_on", false)
            .remove("volume_saved").remove("previous_ring_volume").apply()
    }

    override fun onPause() {
        repeatTask?.let { handler.removeCallbacks(it) }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        repeatTask?.let { handler.removeCallbacks(it); handler.postDelayed(it, 60_000L) }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        tts?.stop(); tts?.shutdown(); tts = null
        super.onDestroy()
    }
}