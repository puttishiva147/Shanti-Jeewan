package com.ezflexz.shantijeevan

import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.graphics.Typeface
import android.widget.*
import androidx.activity.ComponentActivity

class FocusReminderActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("focus_beta", MODE_PRIVATE) }
    private val selectedPackages = linkedSetOf<String>()
    private var startMinute = 15 * 60
    private var endMinute = 17 * 60

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            selectedPackages.addAll(prefs.getStringSet("packages", emptySet()) ?: emptySet())
            startMinute = prefs.getInt("start_minute", 15 * 60)
            endMinute = prefs.getInt("end_minute", 17 * 60)
            showScreen()
        } catch (error: Exception) {
            showSafeError(error)
        }
    }

    private fun showScreen() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 22, 24, 28)
            setBackgroundColor(0xFFF8F8F6.toInt())
        }
        page.addView(Button(this).apply {
            text = "‹  BACK"
            setOnClickListener { finish() }
        })
        page.addView(TextView(this).apply {
            text = "Focus & Rest Reminder • BETA"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF202522.toInt())
            setPadding(0, 18, 0, 8)
        })
        page.addView(TextView(this).apply {
            text = "Choose apps and a daily time window. Opening a selected app during that window starts a 45-minute focus session."
            textSize = 14f
            setTextColor(0xFF626B63.toInt())
        })

        val enable = Switch(this).apply {
            text = "Enable Focus Reminder"
            isChecked = prefs.getBoolean("enabled", false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("enabled", checked).apply()
            }
        }
        page.addView(enable)

        page.addView(Button(this).apply {
            text = "START TIME • ${formatTime(startMinute)}"
            setOnClickListener { chooseTime(startMinute, true) }
        })
        page.addView(Button(this).apply {
            text = "END TIME • ${formatTime(endMinute)}"
            setOnClickListener { chooseTime(endMinute, false) }
        })

        page.addView(TextView(this).apply {
            text = "SELECT APPS"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF737B74.toInt())
            setPadding(0, 18, 0, 8)
        })

        val appContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scrollApps = ScrollView(this).apply {
            addView(appContainer)
        }
        page.addView(scrollApps, LinearLayout.LayoutParams(-1, 360))

        try {
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val launchableApps = packageManager.queryIntentActivities(launcherIntent, 0)
                .filter { it.activityInfo.packageName != packageName }
                .distinctBy { it.activityInfo.packageName }
                .sortedBy { it.loadLabel(packageManager).toString().lowercase() }

            if (launchableApps.isEmpty()) {
                appContainer.addView(TextView(this).apply {
                    text = "No launchable apps found. Check app visibility permission."
                    textSize = 13f
                    setTextColor(0xFF626B63.toInt())
                })
            }

            launchableApps.forEach { info ->
                val pkg = info.activityInfo.packageName
                val check = CheckBox(this).apply {
                    text = info.loadLabel(packageManager)?.toString() ?: pkg
                    isChecked = selectedPackages.contains(pkg)
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) selectedPackages.add(pkg) else selectedPackages.remove(pkg)
                        prefs.edit().putStringSet("packages", selectedPackages.toSet()).apply()
                    }
                }
                appContainer.addView(check)
            }
        } catch (error: Exception) {
            appContainer.addView(TextView(this).apply {
                text = "Could not load installed apps: ${error.javaClass.simpleName}"
                textSize = 13f
                setTextColor(0xFFB3261E.toInt())
            })
        }

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
                try {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (error: Exception) {
                    Toast.makeText(this@FocusReminderActivity, "Could not open Accessibility settings.", Toast.LENGTH_LONG).show()
                }
            }
        })
        page.addView(TextView(this).apply {
            text = "A 10-minute break is available. The reminder returns only if you are still using a selected app when the break ends. The session ends after 45 minutes. Ringtone volume is temporarily reduced to 30% and restored afterwards."
            textSize = 12f
            setTextColor(0xFF626B63.toInt())
            setPadding(0, 12, 0, 0)
        })

        val pageScroll = ScrollView(this).apply {
            isFillViewport = true
            addView(page)
        }
        setContentView(pageScroll)
    }

    private fun chooseTime(current: Int, isStart: Boolean) {
        TimePickerDialog(this, { _, hour, minute ->
            val value = hour * 60 + minute
            if (isStart) {
                startMinute = value
                prefs.edit().putInt("start_minute", value).apply()
            } else {
                endMinute = value
                prefs.edit().putInt("end_minute", value).apply()
            }
            showScreen()
        }, current / 60, current % 60, false).show()
    }

    private fun formatTime(value: Int) =
        String.format("%02d:%02d", value / 60, value % 60)

    private fun showSafeError(error: Exception) {
        val message = "Focus & Rest page could not load. Error: ${error.javaClass.simpleName}\n${error.localizedMessage ?: "No extra details"}"
        setContentView(TextView(this).apply {
            text = message + "\n\nTap Back to return."
            textSize = 16f
            setPadding(28, 40, 28, 40)
            setTextColor(0xFF202522.toInt())
            setOnClickListener { finish() }
        })
    }
}
