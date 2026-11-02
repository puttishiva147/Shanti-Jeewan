package com.ezflexz.shantijeevan

import android.app.Activity
import android.graphics.*
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.animation.ValueAnimator
import android.widget.*
import java.util.Locale
import kotlin.math.*

class FocusBlockerActivity : Activity() {
    private var tts: TextToSpeech? = null
    private val handler = Handler(Looper.getMainLooper())
    private var spoken = false
    private var repeatTask: Runnable? = null
    private var reason = "app"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
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
        val p = getSharedPreferences("focus_beta", MODE_PRIVATE)
        val msg = p.getString("message", "Take a break. Return to what matters.") ?: "Take a break."
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(22), dp(18), dp(22), dp(18))
            setBackgroundColor(Color.rgb(22, 29, 26))
        }
        root.addView(TextView(this).apply {
            text = if (reason == "periodic") "A LITTLE CHECK-IN" else "FOCUS TIME"
            textSize = 12f; letterSpacing = .2f; gravity = Gravity.CENTER
            setTextColor(Color.rgb(177, 195, 181))
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply {
            text = "No, no, no… not now, please!"
            textSize = 25f; gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, dp(10), 0, dp(2))
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply {
            text = "Your little focus buddy needs your attention"
            textSize = 13f; gravity = Gravity.CENTER
            setTextColor(Color.rgb(190, 201, 193))
            setPadding(0, dp(3), 0, dp(5))
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(PleaseCharacterView(this), LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(TextView(this).apply {
            text = msg; textSize = 16f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setPadding(dp(4), dp(4), dp(4), dp(12))
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(Button(this).apply {
            text = "OKAY, LET'S FOCUS"
            isAllCaps = false; textSize = 14f
            setOnClickListener {
                p.edit().putBoolean("manual_mode_off", false).putBoolean("focus_mode_on", true).apply()
                applyFocusVolume()
                Toast.makeText(this@FocusBlockerActivity, "Focus mode ON", Toast.LENGTH_SHORT).show()
                finish()
            }
        }, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(8) })
        root.addView(Button(this).apply {
            text = "NOT NOW • NORMAL + FULL VOLUME"
            isAllCaps = false; textSize = 12f
            setOnClickListener {
                p.edit().putBoolean("manual_mode_off", true).putBoolean("focus_mode_on", false).apply()
                forceNormalVolume()
                Toast.makeText(this@FocusBlockerActivity, "Normal mode restored", Toast.LENGTH_SHORT).show()
                finish()
            }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(6) })
        if (reason == "app") {
            root.addView(Button(this).apply {
                text = "TAKE 10-MINUTE BREAK"; isAllCaps = false
                setOnClickListener {
                    p.edit().putLong("break_until", System.currentTimeMillis() + 10L * 60L * 1000L).apply()
                    finish()
                }
            }, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(4) })
        }
        root.addView(TextView(this).apply {
            text = "CLOSE"; textSize = 12f; gravity = Gravity.CENTER
            setTextColor(Color.rgb(166, 180, 170)); setPadding(0, dp(7), 0, 0)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, dp(30)))
        setContentView(root)
        tts?.stop(); tts?.shutdown()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS && !spoken) {
                tts?.language = Locale.getDefault()
                tts?.speak("No, no, no. Not now, please. $msg", TextToSpeech.QUEUE_FLUSH, null, "focus-reminder")
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

private class PleaseCharacterView(context: android.content.Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1800L
        repeatCount = ValueAnimator.INFINITE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    init { animator.start() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w / 2f
        val bob = sin(phase * Math.PI * 2).toFloat() * h * .012f
        val scale = min(w / 330f, h / 390f).coerceAtMost(1.18f)
        canvas.save()
        canvas.translate(cx, h * .50f + bob)
        canvas.scale(scale, scale)
        // soft spotlight
        paint.color = Color.argb(24, 224, 235, 225)
        canvas.drawCircle(0f, 0f, 145f, paint)
        // body
        paint.color = Color.rgb(218, 225, 216)
        canvas.drawRoundRect(-65f, 12f, 65f, 158f, 42f, 42f, paint)
        // neck
        paint.color = Color.rgb(196, 151, 117)
        canvas.drawRoundRect(-17f, -39f, 17f, 23f, 12f, 12f, paint)
        // arms swing up and outward in a pleading "no no" gesture
        val wave = sin(phase * Math.PI * 2).toFloat() * 13f
        val leftShoulder = PointF(-54f, 35f)
        val rightShoulder = PointF(54f, 35f)
        paint.strokeWidth = 19f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.rgb(218, 225, 216)
        canvas.drawLine(leftShoulder.x, leftShoulder.y, -111f, -12f + wave, paint)
        canvas.drawLine(rightShoulder.x, rightShoulder.y, 111f, -12f - wave, paint)
        // palms facing forward
        paint.color = Color.rgb(196, 151, 117)
        canvas.drawCircle(-115f, -17f + wave, 19f, paint)
        canvas.drawCircle(115f, -17f - wave, 19f, paint)
        paint.strokeWidth = 6f
        canvas.drawLine(-125f, -29f + wave, -125f, -48f + wave, paint)
        canvas.drawLine(-116f, -31f + wave, -113f, -52f + wave, paint)
        canvas.drawLine(-106f, -28f + wave, -101f, -46f + wave, paint)
        canvas.drawLine(125f, -29f - wave, 125f, -48f - wave, paint)
        canvas.drawLine(116f, -31f - wave, 113f, -52f - wave, paint)
        canvas.drawLine(106f, -28f - wave, 101f, -46f - wave, paint)
        // head
        paint.color = Color.rgb(196, 151, 117)
        canvas.drawOval(-57f, -137f, 57f, -20f, paint)
        // hair
        paint.color = Color.rgb(48, 48, 43)
        canvas.drawArc(-59f, -145f, 59f, -50f, 180f, 180f, true, paint)
        canvas.drawRoundRect(-58f, -112f, -43f, -77f, 8f, 8f, paint)
        canvas.drawRoundRect(43f, -112f, 58f, -77f, 8f, 8f, paint)
        // sad brows
        paint.color = Color.rgb(65, 52, 46)
        paint.strokeWidth = 4f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(-34f, -91f, -15f, -96f, paint)
        canvas.drawLine(34f, -91f, 15f, -96f, paint)
        // eyes
        paint.color = Color.rgb(40, 39, 37)
        canvas.drawCircle(-22f, -78f, 5f, paint)
        canvas.drawCircle(22f, -78f, 5f, paint)
        // worried mouth
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3.5f
        paint.color = Color.rgb(112, 64, 57)
        canvas.drawArc(-13f, -61f, 13f, -43f, 200f, 140f, false, paint)
        paint.style = Paint.Style.FILL
        // tear drops shimmer
        val tear = (sin(phase * Math.PI * 2).toFloat() + 1f) * 2.5f
        paint.color = Color.rgb(133, 190, 204)
        canvas.drawOval(-29f, -68f, -23f, -55f - tear, paint)
        canvas.drawOval(23f, -68f, 29f, -55f - tear, paint)
        // little shoes
        paint.color = Color.rgb(54, 64, 57)
        canvas.drawRoundRect(-61f, 146f, -5f, 164f, 9f, 9f, paint)
        canvas.drawRoundRect(5f, 146f, 61f, 164f, 9f, 9f, paint)
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}
