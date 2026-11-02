package com.ezflexz.shantijeevan

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.activity.ComponentActivity
import kotlin.concurrent.thread

class FocusReminderActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("focus_beta", MODE_PRIVATE) }
    private val selectedPackages = linkedSetOf<String>()
    private var startMinute = 900
    private var endMinute = 1020
    private lateinit var appButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            selectedPackages.addAll(prefs.getStringSet("packages", emptySet()) ?: emptySet())
            startMinute = prefs.getInt("start_minute", 900).coerceIn(0, 1439)
            endMinute = prefs.getInt("end_minute", 1020).coerceIn(0, 1439)
            render()
        } catch (t: Throwable) { safeError(t) }
    }

    private fun render() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 22, 24, 28)
            setBackgroundColor(0xFFF8F8F6.toInt())
        }
        page.addView(Button(this).apply { text = "‹ BACK"; setOnClickListener { finish() } })
        page.addView(TextView(this).apply {
            text = "Focus & Rest Reminder • BETA"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF202522.toInt())
            setPadding(0, 18, 0, 8)
        })
        page.addView(TextView(this).apply {
            text = "Choose apps and a daily time window. Opening a selected app during this window starts a 45-minute focus session."
            textSize = 14f
            setTextColor(0xFF626B63.toInt())
        })
        page.addView(Switch(this).apply {
            text = "Enable Focus Reminder"
            isChecked = prefs.getBoolean("enabled", false)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("enabled", checked).apply() }
        })
        page.addView(Button(this).apply {
            text = "START TIME • ${time(startMinute)}"
            setOnClickListener { pickTime(true) }
        })
        page.addView(Button(this).apply {
            text = "END TIME • ${time(endMinute)}"
            setOnClickListener { pickTime(false) }
        })
        page.addView(TextView(this).apply {
            text = "SELECT APPS"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF737B74.toInt())
            setPadding(0, 18, 0, 8)
        })
        appButton = Button(this).apply { setOnClickListener { loadAppPicker() } }
        updateAppButton()
        page.addView(appButton)
        page.addView(TextView(this).apply {
            text = "REMINDER MESSAGE"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF737B74.toInt())
            setPadding(0, 16, 0, 6)
        })
        val message = EditText(this).apply {
            hint = "Take a break and return to your priorities."
            setText(prefs.getString("message", "Take a break. Return to what matters."))
        }
        page.addView(message)
        page.addView(Button(this).apply {
            text = "SAVE MESSAGE"
            setOnClickListener {
                prefs.edit().putString("message", message.text.toString()).apply()
                Toast.makeText(this@FocusReminderActivity, "Message saved", Toast.LENGTH_SHORT).show()
            }
        })
        page.addView(Button(this).apply {
            text = "ENABLE ACCESSIBILITY BLOCKER"
            setOnClickListener {
                try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                catch (_: Throwable) { Toast.makeText(this@FocusReminderActivity, "Could not open Accessibility settings.", Toast.LENGTH_LONG).show() }
            }
        })
        page.addView(TextView(this).apply {
            text = "A 10-minute break is available. The reminder returns only if you are still using a selected app when the break ends. The session ends after 45 minutes. Ringtone volume is temporarily reduced to 30% and restored afterwards."
            textSize = 12f
            setTextColor(0xFF626B63.toInt())
            setPadding(0, 12, 0, 0)
        })
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(page) })
    }

    private fun loadAppPicker() {
        Toast.makeText(this, "Loading apps…", Toast.LENGTH_SHORT).show()
        thread {
            val apps = try {
                packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .filter { it.activityInfo?.packageName != null && it.activityInfo.packageName != packageName }
                    .distinctBy { it.activityInfo.packageName }
                    .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
            } catch (_: Throwable) { emptyList<android.content.pm.ResolveInfo>() }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (apps.isEmpty()) {
                    Toast.makeText(this, "No launchable apps found.", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                try {
                    val pkgs = apps.map { it.activityInfo.packageName }
                    val labels = apps.map { it.loadLabel(packageManager)?.toString() ?: it.activityInfo.packageName }.toTypedArray()
                    val checked = BooleanArray(pkgs.size) { selectedPackages.contains(pkgs[it]) }
                    AlertDialog.Builder(this).setTitle("Choose apps")
                        .setMultiChoiceItems(labels, checked) { _, which, yes ->
                            if (yes) selectedPackages.add(pkgs[which]) else selectedPackages.remove(pkgs[which])
                        }
                        .setPositiveButton("SAVE") { _, _ ->
                            prefs.edit().putStringSet("packages", selectedPackages.toSet()).apply()
                            updateAppButton()
                        }
                        .setNegativeButton("CANCEL", null).show()
                } catch (_: Throwable) {
                    Toast.makeText(this, "App picker could not open.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateAppButton() {
        appButton.text = "CHOOSE APPS • ${selectedPackages.size} SELECTED"
    }

    private fun pickTime(start: Boolean) {
        val current = if (start) startMinute else endMinute
        TimePickerDialog(this, { _, h, m ->
            val value = h * 60 + m
            if (start) {
                startMinute = value
                prefs.edit().putInt("start_minute", value).apply()
            } else {
                endMinute = value
                prefs.edit().putInt("end_minute", value).apply()
            }
            render()
        }, current / 60, current % 60, false).show()
    }

    private fun time(value: Int) = String.format("%02d:%02d", value / 60, value % 60)

    private fun safeError(t: Throwable) {
        try {
            setContentView(TextView(this).apply {
                text = "Focus page error: ${t.javaClass.simpleName}\n${t.localizedMessage ?: "Unknown"}\nTap to go back."
                textSize = 16f
                setPadding(28, 40, 28, 40)
                setOnClickListener { finish() }
            })
        } catch (_: Throwable) { finish() }
    }
}