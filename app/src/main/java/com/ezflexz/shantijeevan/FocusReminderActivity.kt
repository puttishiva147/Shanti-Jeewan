package com.ezflexz.shantijeevan

import android.app.*
import android.content.*
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.accessibility.AccessibilityManager
import android.widget.*
import androidx.activity.ComponentActivity
import java.util.Calendar

class FocusReminderActivity : ComponentActivity() {
    private val p by lazy { getSharedPreferences("focus_beta", MODE_PRIVATE) }
    private val chosen = linkedSetOf<String>()
    private var startMinute = p.getInt("start_minute", 15 * 60)
    private var endMinute = p.getInt("end_minute", 17 * 60)
    private lateinit var appList: LinearLayout
    private lateinit var message: EditText
    private lateinit var enabled: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chosen.addAll(p.getStringSet("packages", emptySet()) ?: emptySet())
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 24, 22, 28)
            setBackgroundColor(0xFFF8F8F6.toInt())
        }
        root.addView(Button(this).apply { text = "‹  BACK"; setOnClickListener { finish() } })
        root.addView(TextView(this).apply {
            text = "Focus & Rest Reminder  •  BETA"
            textSize = 23f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF202522.toInt()); setPadding(0, 18, 0, 8)
        })
        root.addView(TextView(this).apply {
            text = "Choose apps and a daily time window. The first time you open a selected app during that window, a 45-minute focus session begins."
            textSize = 14f; setTextColor(0xFF626B63.toInt())
        })
        enabled = Switch(this).apply {
            text = "Enable Focus Reminder"
            isChecked = p.getBoolean("enabled", false)
            setOnCheckedChangeListener { _, value -> p.edit().putBoolean("enabled", value).apply() }
        }
        root.addView(enabled)
        root.addView(Button(this).apply {
            text = "START TIME  •  ${fmt(startMinute)}"
            setOnClickListener { pickTime(startMinute) { startMinute = it; p.edit().putInt("start_minute", it).apply(); render() } }
        })
        root.addView(Button(this).apply {
            text = "END TIME  •  ${fmt(endMinute)}"
            setOnClickListener { pickTime(endMinute) { endMinute = it; p.edit().putInt("end_minute", it).apply(); render() } }
        })
        root.addView(TextView(this).apply {
            text = "SELECT APPS"; textSize = 12f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF737B74.toInt()); setPadding(0, 20, 0, 8)
        })
        appList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(appList) }
        val apps = packageManager.getInstalledApplications(0)
            .filter { it.packageName != packageName }
            .sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() }
        apps.forEach { app ->
            val cb = CheckBox(this).apply {
                text = packageManager.getApplicationLabel(app).toString()
                tag = app.packageName
                isChecked = chosen.contains(app.packageName)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) chosen.add(app.packageName) else chosen.remove(app.packageName)
                    p.edit().putStringSet("packages", chosen.toSet()).apply()
                }
            }
            appList.addView(cb)
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(TextView(this).apply {
            text = "REMINDER MESSAGE"; textSize = 12f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF737B74.toInt()); setPadding(0, 16, 0, 6)
        })
        message = EditText(this).apply {
            hint = "Example: Take a break and return to your priorities."
            setText(p.getString("message", "Take a break. Return to what matters."))
        }
        root.addView(message)
        root.addView(Button(this).apply {
            text = "SAVE MESSAGE"
            setOnClickListener { p.edit().putString("message", message.text.toString()).apply(); Toast.makeText(this@FocusReminderActivity, "Message saved", Toast.LENGTH_SHORT).show() }
        })
        root.addView(Button(this).apply {
            text = "ENABLE ACCESSIBILITY BLOCKER"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        root.addView(TextView(this).apply {
            text = "How it works: 10-minute break is available. If the break ends while you are still in a selected app, the reminder returns. If you leave that app, no popup appears. The session ends after 45 minutes. Call ringtone volume is temporarily set to 30% and restored afterwards."
            textSize = 12f; setTextColor(0xFF626B63.toInt()); setPadding(0, 12, 0, 0)
        })
        setContentView(root)
    }

    private fun pickTime(current: Int, done: (Int) -> Unit) {
        TimePickerDialog(this, { _, h, m -> done(h * 60 + m) }, current / 60, current % 60, false).show()
    }
    private fun fmt(value: Int) = String.format("%02d:%02d", value / 60, value % 60)
}
