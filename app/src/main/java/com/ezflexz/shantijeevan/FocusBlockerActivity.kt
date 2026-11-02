package com.ezflexz.shantijeevan

import android.app.Activity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val p = getSharedPreferences("focus_beta", MODE_PRIVATE)
        val msg = p.getString("message", "Take a break. Return to what matters.") ?: "Take a break."
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(28, 32, 28, 32); setBackgroundColor(0xFF17211C.toInt())
        }
        root.addView(TextView(this).apply {
            text = "PAUSE & RESET"; textSize = 13f; letterSpacing = .18f
            setTextColor(0xFFB8C8BA.toInt())
        })
        root.addView(TextView(this).apply {
            text = msg; textSize = 27f; gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt()); setPadding(0, 28, 0, 28)
        })
        root.addView(TextView(this).apply {
            text = "Your 10-minute break starts now."
            textSize = 14f; setTextColor(0xFFD0D8D1.toInt())
        })
        root.addView(Button(this).apply {
            text = "TAKE 10-MINUTE BREAK"
            setOnClickListener {
                p.edit().putLong("break_until", System.currentTimeMillis() + 10L * 60L * 1000L).apply()
                finish()
            }
        })
        root.addView(Button(this).apply { text = "CLOSE APP"; setOnClickListener { finish() } })
        setContentView(root)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS && !spoken) {
                tts?.language = Locale.getDefault()
                tts?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "focus-reminder")
                spoken = true
            }
        }
        repeatTask = object : Runnable {
            override fun run() {
                if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                    tts?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "focus-repeat")
                    handler.postDelayed(this, 60_000L)
                }
            }
        }
        handler.postDelayed(repeatTask!!, 60_000L)
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
