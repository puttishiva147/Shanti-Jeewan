package com.ezflexz.shantijeevan

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import android.view.View
import androidx.activity.ComponentActivity
import kotlin.concurrent.thread

class FocusReminderActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("focus_beta", MODE_PRIVATE) }
    private val selectedPackages = linkedSetOf<String>()
    private var startMinute = 900
    private var endMinute = 1020
    private var shift1Start = 900
    private var shift1End = 960
    private var shift2Start = 0
    private var shift2End = 240
    private lateinit var appButton: Button
    private lateinit var accessStatus: TextView

    private val ink = 0xFF202522.toInt()
    private val muted = 0xFF707970.toInt()
    private val paper = 0xFFF5F6F3.toInt()
    private val cardColor = 0xFFFFFFFF.toInt()
    private val accent = 0xFF34483B.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedPackages.addAll(prefs.getStringSet("packages", emptySet()) ?: emptySet())
        startMinute = prefs.getInt("start_minute", 900).coerceIn(0, 1439)
        endMinute = prefs.getInt("end_minute", 1020).coerceIn(0, 1439)
        shift1Start = prefs.getInt("shift1_start", 900).coerceIn(0, 1439)
        shift1End = prefs.getInt("shift1_end", 960).coerceIn(0, 1439)
        shift2Start = prefs.getInt("shift2_start", 0).coerceIn(0, 1439)
        shift2End = prefs.getInt("shift2_end", 240).coerceIn(0, 1439)
        render()
    }

    override fun onResume() { super.onResume(); if (::accessStatus.isInitialized) updatePermissionStatus() }

    private fun render() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(30))
            setBackgroundColor(paper)
        }
        page.addView(TextView(this).apply {
            text = "‹  BACK"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(ink); setPadding(0, dp(8), 0, dp(18))
            setOnClickListener { finish() }
        })
        page.addView(TextView(this).apply {
            text = "Focus & Rest"; textSize = 28f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(ink)
        })
        page.addView(TextView(this).apply {
            text = "Build healthier screen-time habits with gentle reminders."
            textSize = 14f; setTextColor(muted); setPadding(0, dp(6), 0, dp(18))
        })

        val main = card()
        main.addView(sectionTitle("FOCUS CONTROL"))
        main.addView(label("Start a 45-minute focus session when you open a selected app."))
        main.addView(toggleRow("Focus reminders", "Enable app-based focus sessions", "enabled"))
        main.addView(divider())
        main.addView(toggleRow("45-minute check-ins", "Popup with a spoken reminder", "periodic_checkin_enabled"))
        page.addView(main)

        val schedule = card()
        schedule.addView(sectionTitle("DAILY SCHEDULE"))
        schedule.addView(label("Choose when app-based focus sessions can begin."))
        val times = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        times.addView(timeButton("START", startMinute, true), LinearLayout.LayoutParams(0, dp(58), 1f).apply { marginEnd = dp(8) })
        times.addView(timeButton("END", endMinute, false), LinearLayout.LayoutParams(0, dp(58), 1f).apply { marginStart = dp(8) })
        schedule.addView(times)
        page.addView(schedule)

        val betaShifts = card()
        betaShifts.addView(sectionTitle("BETA FEATURES"))
        betaShifts.addView(TextView(this).apply {
            text = "Multiple focus shifts"
            textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ink)
            setPadding(0, 0, 0, dp(4))
        })
        betaShifts.addView(label("Set two separate active periods. Focus automatically pauses outside both shifts. Your existing Daily Schedule stays unchanged when this beta option is OFF."))
        betaShifts.addView(Switch(this).apply {
            text = "Enable multiple shifts (Beta)"
            isChecked = prefs.getBoolean("multi_shift_enabled", false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("multi_shift_enabled", checked).apply()
                Toast.makeText(this@FocusReminderActivity, if (checked) "Multiple shifts enabled" else "Using existing Daily Schedule", Toast.LENGTH_SHORT).show()
            }
        })
        betaShifts.addView(label("SHIFT 1"))
        val shift1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        shift1.addView(shiftTimeButton("START", shift1Start, 1, true), LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(6) })
        shift1.addView(shiftTimeButton("END", shift1End, 1, false), LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginStart = dp(6) })
        betaShifts.addView(shift1)
        betaShifts.addView(label("SHIFT 2"))
        val shift2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        shift2.addView(shiftTimeButton("START", shift2Start, 2, true), LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(6) })
        shift2.addView(shiftTimeButton("END", shift2End, 2, false), LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginStart = dp(6) })
        betaShifts.addView(shift2)
        betaShifts.addView(TextView(this).apply {
            text = "Example: 3:00 PM–4:00 PM, then 12:00 AM–4:00 AM."
            textSize = 11f; setTextColor(muted); setPadding(0, dp(8), 0, 0)
        })
        page.addView(betaShifts)

        val apps = card()
        apps.addView(sectionTitle("APPS TO FOCUS ON"))
        apps.addView(label("Select the apps where you want focus reminders."))
        appButton = Button(this).apply {
            isAllCaps = false; textSize = 14f
            setTextColor(accent); text = appButtonText()
            setOnClickListener { loadAppPicker() }
        }
        apps.addView(appButton, LinearLayout.LayoutParams(-1, dp(48)))
        page.addView(apps)

        val reminder = card()
        reminder.addView(sectionTitle("VOICE REMINDER"))
        reminder.addView(label("This message appears in the popup and is spoken aloud."))
        val message = EditText(this).apply {
            setText(prefs.getString("message", "Take a break. Return to what matters."))
            hint = "Write your reminder"
            textSize = 14f; setTextColor(ink); setHintTextColor(muted)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(0xFFF2F4F0.toInt())
            minLines = 2; maxLines = 4
        }
        reminder.addView(message)
        val save = Button(this).apply {
            text = "Save message"; isAllCaps = false; setTextColor(accent)
            setOnClickListener {
                prefs.edit().putString("message", message.text.toString().trim().ifBlank { "Take a break. Return to what matters." }).apply()
                Toast.makeText(this@FocusReminderActivity, "Reminder saved", Toast.LENGTH_SHORT).show()
            }
        }
        reminder.addView(save)
        page.addView(reminder)

        val permissions = card()
        permissions.addView(sectionTitle("REQUIRED ACCESS"))
        permissions.addView(label("Allow Shanti Jeewan to detect selected apps and show the focus popup."))
        accessStatus = TextView(this).apply { textSize = 13f; setPadding(0, dp(8), 0, dp(8)) }
        permissions.addView(accessStatus)
        permissions.addView(permissionButton("Enable Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS))
        permissions.addView(permissionButton("Allow sound & ringer control", Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        permissions.addView(permissionButton("Allow scheduled screen dimming", Settings.ACTION_MANAGE_WRITE_SETTINGS))
        permissions.addView(label("Screen dimming uses a fixed low brightness during active shifts and restores your previous brightness afterward. Android requires this one-time special access."))
        page.addView(permissions)

        page.addView(TextView(this).apply {
            text = "At each popup you can choose Mode ON or Mode OFF. Choosing OFF from the popup or Quick Settings restores Normal mode and full ringtone volume. Manual OFF stays in effect until you turn focus mode ON again."
            textSize = 12f; setTextColor(muted); setPadding(dp(4), dp(12), dp(4), 0)
        })
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(page) })
        updatePermissionStatus()
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(14))
        setBackgroundColor(cardColor)
        val lp = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        layoutParams = lp
    }

    private fun sectionTitle(value: String) = TextView(this).apply {
        text = value; textSize = 11f; letterSpacing = .08f
        typeface = Typeface.DEFAULT_BOLD; setTextColor(muted)
        setPadding(0, 0, 0, dp(8))
    }

    private fun label(value: String) = TextView(this).apply {
        text = value; textSize = 13f; setTextColor(muted)
        setPadding(0, 0, 0, dp(12))
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(0xFFE9ECE7.toInt())
        layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(6); bottomMargin = dp(8) }
    }

    private fun toggleRow(title: String, subtitle: String, key: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
        val texts = LinearLayout(this@FocusReminderActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@FocusReminderActivity).apply { text = title; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ink) })
            addView(TextView(this@FocusReminderActivity).apply { text = subtitle; textSize = 12f; setTextColor(muted); setPadding(0, dp(3), 0, 0) })
        }
        addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        addView(Switch(this@FocusReminderActivity).apply {
            isChecked = prefs.getBoolean(key, false)
            setOnCheckedChangeListener { _, checked ->
                val edit = prefs.edit().putBoolean(key, checked)
                if (key == "periodic_checkin_enabled") {
                    if (checked) edit.putLong("next_checkin_at", System.currentTimeMillis() + 45L * 60L * 1000L)
                    else edit.remove("next_checkin_at")
                }
                if (key == "enabled" && !checked) {
                    edit.remove("session_until").remove("break_until")
                }
                edit.apply()
            }
        })
    }

    private fun timeButton(caption: String, value: Int, isStart: Boolean) = Button(this).apply {
        isAllCaps = false; textSize = 13f; setTextColor(accent)
        text = "$caption   ${time(value)}"
        setOnClickListener { pickTime(isStart) }
    }

    private fun permissionButton(title: String, action: String) = Button(this).apply {
        text = title; isAllCaps = false; textSize = 13f; setTextColor(accent)
        setOnClickListener {
            try { val i = Intent(action); if (action == Settings.ACTION_MANAGE_WRITE_SETTINGS) i.data = android.net.Uri.parse("package:$packageName"); startActivity(i) }
            catch (_: Throwable) { Toast.makeText(this@FocusReminderActivity, "Setting unavailable on this device", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun updatePermissionStatus() {
        val component = ComponentName(this, FocusAppMonitorService::class.java)
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.split(':')?.any { it.equals(component.flattenToString(), true) } == true
        accessStatus.text = if (enabled) "✓ Accessibility is enabled" else "• Accessibility is not enabled"
        accessStatus.setTextColor(if (enabled) accent else 0xFF9A6333.toInt())
    }

    private fun appButtonText() = "Choose apps  ·  ${selectedPackages.size} selected"

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
                if (apps.isEmpty()) { Toast.makeText(this, "No apps found", Toast.LENGTH_LONG).show(); return@runOnUiThread }
                val pkgs = apps.map { it.activityInfo.packageName }
                val labels = apps.map { it.loadLabel(packageManager)?.toString() ?: it.activityInfo.packageName }.toTypedArray()
                val checked = BooleanArray(pkgs.size) { selectedPackages.contains(pkgs[it]) }
                AlertDialog.Builder(this).setTitle("Choose apps")
                    .setMultiChoiceItems(labels, checked) { _, which, yes ->
                        if (yes) selectedPackages.add(pkgs[which]) else selectedPackages.remove(pkgs[which])
                    }
                    .setPositiveButton("Save") { _, _ ->
                        prefs.edit().putStringSet("packages", selectedPackages.toSet()).apply()
                        appButton.text = appButtonText()
                    }.setNegativeButton("Cancel", null).show()
            }
        }
    }

    private fun pickTime(start: Boolean) {
        val current = if (start) startMinute else endMinute
        TimePickerDialog(this, { _, h, m ->
            val value = h * 60 + m
            if (start) { startMinute = value; prefs.edit().putInt("start_minute", value).apply() }
            else { endMinute = value; prefs.edit().putInt("end_minute", value).apply() }
            render()
        }, current / 60, current % 60, false).show()
    }

    private fun time(value: Int) = String.format("%02d:%02d", value / 60, value % 60)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun shiftTimeButton(caption: String, value: Int, shift: Int, isStart: Boolean) = Button(this).apply {
        isAllCaps = false; textSize = 12f; setTextColor(accent)
        text = "$caption   ${time(value)}"
        setOnClickListener {
            TimePickerDialog(this@FocusReminderActivity, { _, h, m ->
                val chosen = h * 60 + m
                when (shift) {
                    1 -> if (isStart) shift1Start = chosen else shift1End = chosen
                    2 -> if (isStart) shift2Start = chosen else shift2End = chosen
                }
                val edit = prefs.edit()
                when (shift) {
                    1 -> edit.putInt(if (isStart) "shift1_start" else "shift1_end", chosen)
                    2 -> edit.putInt(if (isStart) "shift2_start" else "shift2_end", chosen)
                }
                edit.apply()
                render()
            }, value / 60, value % 60, false).show()
        }
    }


}