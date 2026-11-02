package com.ezflexz.shantijeevan

import android.app.Activity
import android.graphics.Color
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.WindowManager
import android.widget.*
import java.util.Locale

class FocusBlockerActivity : Activity() {
    private var tts: TextToSpeech? = null
    private var spoken = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.attributes = window.attributes.apply { dimAmount = 0.28f }
        window.setLayout((resources.displayMetrics.widthPixels * 0.90f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
        render()
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        render()
    }

    private fun render() {
        spoken = false
        val reason = intent.getStringExtra("reminder_reason") ?: "device"
        val p = getSharedPreferences("focus_beta", MODE_PRIVATE)
        val msg = p.getString("message", "Take a break. Return to what matters.") ?: "Take a break."
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(14), dp(18), dp(16))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(27, 34, 30)); cornerRadius = dp(22).toFloat()
            }
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = if (reason == "device") "GENTLE DEVICE REMINDER" else if (reason == "periodic") "A LITTLE CHECK-IN" else "FOCUS TIME"
            textSize = 11f; letterSpacing = .08f; setTextColor(Color.rgb(183, 199, 186))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply {
            text = "×"; textSize = 28f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setPadding(dp(10), 0, dp(4), dp(4))
            contentDescription = "Dismiss for 15 minutes"
            setOnClickListener {
                val until = System.currentTimeMillis() + 15L * 60L * 1000L
                p.edit().putLong("reminder_snooze_until", until).putLong("next_device_reminder_at", until).apply()
                finish()
            }
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        root.addView(header)
        root.addView(TextView(this).apply {
            text = "No, no, no… not now, please!"
            textSize = 20f; gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE); setPadding(0, dp(3), 0, dp(2))
        })
        root.addView(PleaseCharacterView(this), LinearLayout.LayoutParams(-1, dp(112)))
        root.addView(TextView(this).apply {
            text = msg; textSize = 14f; gravity = Gravity.CENTER
            setTextColor(Color.rgb(224, 230, 225)); setPadding(dp(4), dp(2), dp(4), dp(12))
        })
        root.addView(Button(this).apply {
            text = "OKAY, LET'S FOCUS"; isAllCaps = false; textSize = 13f
            setOnClickListener {
                p.edit().putBoolean("manual_mode_off", false).putBoolean("focus_mode_on", true).apply()
                applyFocusVolume()
                finish()
            }
        }, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(6) })
        root.addView(Button(this).apply {
            text = "NOT NOW • NORMAL MODE"; isAllCaps = false; textSize = 12f
            setOnClickListener {
                p.edit().putBoolean("manual_mode_off", true).putBoolean("focus_mode_on", false).apply()
                forceNormalVolume()
                finish()
            }
        }, LinearLayout.LayoutParams(-1, dp(42)))
        setContentView(root)
        window.setLayout((resources.displayMetrics.widthPixels * 0.90f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
        tts?.shutdown()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS && !spoken) {
                tts?.language = Locale.getDefault()
                tts?.speak("No, no, no. Not now, please. $msg", TextToSpeech.QUEUE_FLUSH, null, "focus-reminder")
                spoken = true
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun applyFocusVolume() {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_RING)
        val prefs = getSharedPreferences("focus_beta", MODE_PRIVATE)
        if (!prefs.getBoolean("volume_saved", false)) {
            prefs.edit().putInt("previous_ring_volume", audio.getStreamVolume(AudioManager.STREAM_RING))
                .putBoolean("volume_saved", true).apply()
        }
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

    override fun onDestroy() {
        tts?.stop(); tts?.shutdown(); tts = null
        super.onDestroy()
    }
}

private class PleaseCharacterView(context: android.content.Context) : android.view.View(context) {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1800L; repeatCount = android.animation.ValueAnimator.INFINITE
        interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }
    init { animator.start() }
    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width / 300f, height / 145f).coerceAtMost(0.38f)
        canvas.save()
        canvas.translate(width / 2f, height / 2f + kotlin.math.sin(phase * Math.PI * 2).toFloat() * 2f)
        canvas.scale(scale, scale)
        paint.color = Color.rgb(218, 225, 216)
        canvas.drawRoundRect(-48f, 8f, 48f, 105f, 30f, 30f, paint)
        paint.color = Color.rgb(196, 151, 117)
        canvas.drawOval(-43f, -105f, 43f, -18f, paint)
        paint.color = Color.rgb(48, 48, 43)
        canvas.drawArc(-45f, -112f, 45f, -48f, 180f, 180f, true, paint)
        paint.color = Color.rgb(65, 52, 46)
        paint.strokeWidth = 4f; paint.strokeCap = android.graphics.Paint.Cap.ROUND
        canvas.drawLine(-25f, -75f, -12f, -80f, paint)
        canvas.drawLine(25f, -75f, 12f, -80f, paint)
        paint.color = Color.rgb(40, 39, 37)
        canvas.drawCircle(-17f, -63f, 4f, paint); canvas.drawCircle(17f, -63f, 4f, paint)
        paint.style = android.graphics.Paint.Style.STROKE; paint.strokeWidth = 3f
        paint.color = Color.rgb(112, 64, 57)
        canvas.drawArc(-10f, -50f, 10f, -36f, 200f, 140f, false, paint)
        paint.style = android.graphics.Paint.Style.FILL
        val wave = kotlin.math.sin(phase * Math.PI * 2).toFloat() * 7f
        paint.color = Color.rgb(218, 225, 216); paint.strokeWidth = 12f
        canvas.drawLine(-40f, 25f, -76f, -5f + wave, paint)
        canvas.drawLine(40f, 25f, 76f, -5f - wave, paint)
        paint.color = Color.rgb(196, 151, 117)
        canvas.drawCircle(-78f, -7f + wave, 12f, paint); canvas.drawCircle(78f, -7f - wave, 12f, paint)
        canvas.restore()
    }
    override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }
}
