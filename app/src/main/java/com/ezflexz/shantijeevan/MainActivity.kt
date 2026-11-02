package com.ezflexz.shantijeevan

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.location.Geocoder
import java.util.Locale
import kotlin.concurrent.thread
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("shanti", MODE_PRIVATE) }
    private lateinit var status: TextView
    private lateinit var activeSwitch: Switch
    private lateinit var nameField: EditText
    private lateinit var messageField: EditText
    private lateinit var intervalSpinner: Spinner
    private lateinit var radiusSpinner: Spinner
    private lateinit var voiceVolumeSeek: SeekBar
    private var settingsScreen = false
    private val nearbyWifiPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) saveConnectedHomeWifi()
        else Toast.makeText(this, "Nearby Wi-Fi permission is needed to save your home network.", Toast.LENGTH_LONG).show()
    }

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            saveCurrentLocation()
            if (!prefs.getBoolean("setup_complete", false)) buildSetupScreen()
        } else {
            Toast.makeText(this, "Location permission is required.", Toast.LENGTH_LONG).show()
            if (::activeSwitch.isInitialized) activeSwitch.isChecked = false
        }
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (!prefs.getBoolean("setup_complete", false)) buildSetupScreen() else activateHomeAutomation()
        } else {
            Toast.makeText(this, "Arrival confirmation notification is required. Allow notifications and turn ON again.", Toast.LENGTH_LONG).show()
            if (::activeSwitch.isInitialized) activeSwitch.isChecked = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (prefs.getBoolean("setup_complete", false)) buildHomeScreen() else buildSetupScreen()
        if (intent.getBooleanExtra("QUICK_TILE_TOGGLE", false)) {
            window.decorView.post {
                if (!prefs.getBoolean("setup_complete", false)) {
                    Toast.makeText(this, "Complete Shanti Jeewan setup first.", Toast.LENGTH_LONG).show()
                } else if (::activeSwitch.isInitialized) {
                    activeSwitch.isChecked = !prefs.getBoolean("active", false)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!prefs.getBoolean("setup_complete", false)) {
            if (!settingsScreen) buildSetupScreen()
        } else if (::status.isInitialized && !settingsScreen) refreshStatus()
    }

    private fun setupStep(page: LinearLayout, icon: String, title: String, detail: String, done: Boolean, button: String, action: () -> Unit) {
        val item = card().apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.START }
        item.addView(TextView(this).apply { text = icon + "  " + title + if (done) "   ✓ DONE" else "   REQUIRED"; textSize = 16f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); setTextColor(if (done) 0xFF287A48.toInt() else 0xFF202522.toInt()) }, full())
        item.addView(TextView(this).apply { text = detail; textSize = 13f; setTextColor(0xFF68716A.toInt()); setPadding(0, dp(7), 0, dp(10)) }, full())
        item.addView(actionButton(if (done) "✓ COMPLETED" else button, action).apply { isEnabled = !done }, full())
        page.addView(item, full().apply { bottomMargin = dp(12) })
    }

    private fun buildSetupScreen() {
        settingsScreen = false
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(26), dp(22), dp(30)); setBackgroundColor(0xFFF8F8F6.toInt()) }
        page.addView(ImageView(this).apply { setImageResource(R.drawable.ic_shanti); scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(dp(68), dp(68)).apply { gravity = Gravity.CENTER })
        page.addView(TextView(this).apply { text = "Welcome to Shanti Jeewan"; textSize = 25f; gravity = Gravity.CENTER; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); setTextColor(0xFF202522.toInt()); setPadding(0, dp(10), 0, dp(5)) }, full())
        page.addView(TextView(this).apply { text = "Complete these steps before opening the app."; textSize = 14f; gravity = Gravity.CENTER; setTextColor(0xFF737B74.toInt()); setPadding(0, 0, 0, dp(18)) }, full())
        val locationDone = prefs.getString("lat", null) != null && hasForegroundLocation()
        setupStep(page, "📍", "1. Home location", "Allow location and save your current home location using GPS.", locationDone, "ALLOW LOCATION & DETECT HOME") { if (hasForegroundLocation()) saveCurrentLocation() else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
        val backgroundDone = Build.VERSION.SDK_INT < 29 || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        setupStep(page, "🛰️", "2. Background location", "Choose Allow all the time to detect arrival and exit while the app is closed.", backgroundDone, "SET BACKGROUND LOCATION") { openBackgroundLocationSettings() }
        val silentDone = getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
        setupStep(page, "🔕", "3. Silent Mode access", "Allow Shanti Jeewan in Android Modes access / Do Not Disturb access.", silentDone, "OPEN MODES ACCESS") { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
        val notificationsDone = Build.VERSION.SDK_INT < 33 || ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        setupStep(page, "🔔", "4. Arrival notification", "Allow notifications for the home detected + Silent mode confirmation.", notificationsDone, "ALLOW NOTIFICATIONS") { if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else buildSetupScreen() }
        val allDone = locationDone && backgroundDone && silentDone && notificationsDone
        page.addView(actionButton("FINISH SETUP  →") {
            val ready = prefs.getString("lat", null) != null && hasForegroundLocation() && (Build.VERSION.SDK_INT < 29 || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) && getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted && (Build.VERSION.SDK_INT < 33 || ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
            if (ready) { prefs.edit().putBoolean("setup_complete", true).apply(); buildHomeScreen() } else { Toast.makeText(this, "Please complete all four steps first.", Toast.LENGTH_LONG).show(); buildSetupScreen() }
        }.apply { isEnabled = allDone; alpha = if (allDone) 1f else 0.55f }, full().apply { topMargin = dp(8) })
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(page) })
    }

    private fun buildHomeScreen() {
        settingsScreen = false
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(30), dp(24), dp(24))
            setBackgroundColor(0xFFF8F8F6.toInt())
        }
        val appBrand = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        }
        appBrand.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_shanti)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(52), dp(52)))
        appBrand.addView(TextView(this).apply {
            text = "EZFLEXZ"
            textSize = 16f
            letterSpacing = .22f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setTextColor(0xFF303842.toInt())
            setPadding(dp(10), 0, 0, 0)
        }, LinearLayout.LayoutParams(-2, -2))
        appBrand.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF246BFE.toInt())
            }
            elevation = dp(2).toFloat()
        }, LinearLayout.LayoutParams(dp(8), dp(8)).apply {
            leftMargin = dp(7)
            gravity = Gravity.CENTER_VERTICAL
        })
        page.addView(appBrand, full())
        page.addView(TextView(this).apply {
            text = "Shanti Jeewan"
            textSize = 28f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setTextColor(0xFF202522.toInt())
        }, full())
        page.addView(TextView(this).apply {
            text = "A little peace, automatically."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFF777E78.toInt())
        }, full())
        val brandLockup = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, 0)
        }
        brandLockup.addView(TextView(this).apply {
            text = "EZFLEXZ"
            textSize = 11f
            letterSpacing = .24f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setTextColor(0xFF707A8C.toInt())
        }, LinearLayout.LayoutParams(-2, -2))
        brandLockup.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF246BFE.toInt())
            }
            elevation = dp(2).toFloat()
        }, LinearLayout.LayoutParams(dp(7), dp(7)).apply {
            leftMargin = dp(6)
            gravity = Gravity.CENTER_VERTICAL
        })
        page.addView(brandLockup, full())

        val controlCard = card()
        controlCard.addView(TextView(this).apply {
            text = "HOME AUTOMATION"
            textSize = 11f
            letterSpacing = .12f
            gravity = Gravity.CENTER
            setTextColor(0xFF737B74.toInt())
        }, full())
        activeSwitch = Switch(this).apply {
            text = if (prefs.getBoolean("active", false)) "  ON" else "  OFF"
            textSize = 22f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            isChecked = prefs.getBoolean("active", false)
            setPadding(0, dp(14), 0, dp(14))
            setOnCheckedChangeListener { _, checked ->
                text = if (checked) "  ON" else "  OFF"
                if (checked) activateHomeAutomation() else pauseAutomation()
            }
        }
        controlCard.addView(activeSwitch, full().apply { gravity = Gravity.CENTER })
        status = TextView(this).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFF626B63.toInt())
            setPadding(0, dp(10), 0, 0)
        }
        controlCard.addView(status, full())
        page.addView(controlCard, full().apply { topMargin = dp(34) })

        page.addView(actionButton("FOCUS & REST REMINDER  •  BETA") {
            startActivity(Intent(this, FocusReminderActivity::class.java))
        }, full().apply { topMargin = dp(22) })

        page.addView(actionButton("SETTINGS") { buildSettingsScreen() },
            full().apply { topMargin = dp(22) })
        page.addView(TextView(this).apply {
            text = "Your phone becomes silent when you arrive home."
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFF858B85.toInt())
            setPadding(0, dp(18), 0, 0)
        }, full())

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(36), 0, 0)
        }
        footer.addView(TextView(this).apply {
            text = "Powered by"
            textSize = 11f
            setTextColor(0xFF929891.toInt())
            gravity = Gravity.CENTER
        }, full())
        val poweredBrand = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, 0)
        }
        poweredBrand.addView(TextView(this).apply {
            text = "EZFLEXZ"
            textSize = 15f
            letterSpacing = .24f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setTextColor(0xFF252D3A.toInt())
        }, LinearLayout.LayoutParams(-2, -2))
        poweredBrand.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF246BFE.toInt())
            }
            elevation = dp(2).toFloat()
        }, LinearLayout.LayoutParams(dp(8), dp(8)).apply {
            leftMargin = dp(7)
            gravity = Gravity.CENTER_VERTICAL
        })
        footer.addView(poweredBrand, full())
        page.addView(footer, full())
        setContentView(ScrollView(this).apply { addView(page) })
        refreshStatus()
    }

    private fun buildSettingsScreen() {
        settingsScreen = true
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(28))
            setBackgroundColor(0xFFF8F8F6.toInt())
        }
        page.addView(actionButton("‹   BACK") { buildHomeScreen() }, full())
        page.addView(TextView(this).apply {
            text = "Settings"
            textSize = 27f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(0xFF202522.toInt())
            setPadding(0, dp(20), 0, dp(12))
        }, full())

        page.addView(label("APP DISGUISE"))
        page.addView(TextView(this).apply {
            text = "Choose a different home-screen icon and app name. Your Shanti Jeewan features stay the same."
            textSize = 13f
            setTextColor(0xFF68716A.toInt())
            setPadding(0, dp(4), 0, dp(10))
        }, full())
        page.addView(actionButton("CHOOSE APP ICON") { showAppDisguisePicker() }, full())
        page.addView(TextView(this).apply {
            text = "Launcher changes may take a few seconds. The installed app can still appear in Android Settings."
            textSize = 11f
            setTextColor(0xFF858B85.toInt())
            setPadding(0, dp(7), 0, dp(16))
        }, full())

        page.addView(label("HOME LOCATION"))
        val savedLocationInfo = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFF354039.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(0xFFFFFFFF.toInt())
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), 0xFFE4E9E4.toInt())
            }
        }
        page.addView(savedLocationInfo, full())
        updateSavedLocationInfo(savedLocationInfo)
        page.addView(actionButton(if (prefs.getString("lat", null) != null) "UPDATE HOME LOCATION" else "PICK CURRENT LOCATION AS HOME") {
            if (hasForegroundLocation()) saveCurrentLocation()
            else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }, full())

        page.addView(label("HOME WI-FI FALLBACK"))
        val savedWifi = prefs.getString("home_wifi_ssid", null)
        val savedWifiFingerprint = prefs.getString("home_wifi_fingerprint", null)
        page.addView(TextView(this).apply {
            text = if (savedWifi.isNullOrBlank() && savedWifiFingerprint.isNullOrBlank()) "Not configured. Connect to your home Wi-Fi, then save it here. Router fingerprint helps detect it when Location is OFF."
                else "Saved home Wi-Fi: ${savedWifi ?: "Router/LAN fingerprint"}\nLocation-OFF fingerprint: ${if (savedWifiFingerprint != null) "Saved" else "Not available"}"
            textSize = 13f
            setTextColor(0xFF626B63.toInt())
            setPadding(0, 0, 0, dp(8))
        }, full())
        page.addView(actionButton("SAVE CURRENT WI-FI AS HOME") { saveConnectedHomeWifi() }, full())
        page.addView(actionButton("CLEAR SAVED HOME WI-FI") {
            prefs.edit().remove("home_wifi_ssid").remove("home_wifi_fingerprint").putBoolean("wifi_fallback_enabled", false).apply()
            Toast.makeText(this, "Saved home Wi-Fi cleared.", Toast.LENGTH_SHORT).show()
            buildSettingsScreen()
        }, full())

        page.addView(label("CUSTOM HOME LOCATION"))
        page.addView(TextView(this).apply {
            text = "Set a different home point manually using GPS coordinates. This works even when you are not physically at that location."
            textSize = 12f
            setTextColor(0xFF737B74.toInt())
            setPadding(0, 0, 0, dp(8))
        }, full())
        val customLatitude = EditText(this).apply {
            hint = "Latitude (e.g. 19.1550)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            setSingleLine(true)
            setText(prefs.getString("lat", ""))
        }
        page.addView(customLatitude, full())
        val customLongitude = EditText(this).apply {
            hint = "Longitude (e.g. 72.8490)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            setSingleLine(true)
            setText(prefs.getString("lng", ""))
        }
        page.addView(customLongitude, full())
        page.addView(actionButton("SAVE CUSTOM HOME LOCATION") {
            val latitude = customLatitude.text.toString().trim().toDoubleOrNull()
            val longitude = customLongitude.text.toString().trim().toDoubleOrNull()
            if (latitude == null || longitude == null || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
                Toast.makeText(this, "Enter valid latitude and longitude.", Toast.LENGTH_LONG).show()
            } else {
                prefs.edit().putString("lat", latitude.toString()).putString("lng", longitude.toString()).apply()
                resolveAndSaveAddress(latitude, longitude) { if (settingsScreen) buildSettingsScreen() }
                Toast.makeText(this, "Custom home location saved.", Toast.LENGTH_LONG).show()
                buildSettingsScreen()
            }
        }, full())

        page.addView(TextView(this).apply {
            val allowed = Build.VERSION.SDK_INT < 29 ||
                ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
            text = if (allowed) "✓ Background location allowed" else "Required to detect arrival/exit when the app is closed."
            textSize = 13f
            setTextColor(0xFF626B63.toInt())
            setPadding(0, dp(8), 0, dp(8))
        }, full())
        page.addView(actionButton("GRANT BACKGROUND LOCATION") {
            openBackgroundLocationSettings()
        }, full())
        page.addView(label("SILENT MODE"))
        page.addView(TextView(this).apply {
            text = if (getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted) "✓ Silent Mode access granted" else "Required to switch Silent Mode on and restore your previous mode."
            textSize = 13f
            setTextColor(0xFF626B63.toInt())
            setPadding(0, 0, 0, dp(8))
        }, full())
        page.addView(actionButton("OPEN SILENT MODE PERMISSION") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }, full())

        page.addView(label("WHEN YOU ARRIVE HOME"))
        page.addView(TextView(this).apply {
            text = "Choose what your phone should do automatically after entering the home area."
            textSize = 12f
            setTextColor(0xFF737B74.toInt())
        }, full())
        val arrivalModeSpinner = spinner(
            listOf("🔕 Silent", "📳 Vibrate", "🔉 Low sound (25%)"),
            listOf("silent", "vibrate", "low_sound").indexOf(prefs.getString("arrival_mode", "silent")).let { if (it < 0) 0 else it }
        )
        page.addView(arrivalModeSpinner, full())

        page.addView(label("VOICE REMINDER"))
        nameField = EditText(this).apply {
            hint = "Your name"
            setText(prefs.getString("name", "Shiva"))
            setSingleLine(true)
        }
        page.addView(nameField, full())
        messageField = EditText(this).apply {
            hint = "Reminder message"
            setText(prefs.getString("message", "Aapka phone Silent Mode mein hai."))
            minLines = 2
        }
        page.addView(messageField, full())

        page.addView(label("REMINDER INTERVAL"))
        intervalSpinner = spinner(
            listOf("15 minutes", "20 minutes", "30 minutes", "45 minutes", "60 minutes"),
            listOf(15, 20, 30, 45, 60).indexOf(prefs.getInt("interval", 20)).coerceAtLeast(0)
        )
        page.addView(intervalSpinner, full())

        page.addView(label("VOICE VOLUME"))
        val volumeLabel = TextView(this).apply {
            text = "Reminder voice: " + prefs.getInt("voice_volume", 45) + "%"
            textSize = 13f
            setTextColor(0xFF626B63.toInt())
        }
        page.addView(volumeLabel, full())
        voiceVolumeSeek = SeekBar(this).apply {
            max = 100
            progress = prefs.getInt("voice_volume", 45)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    volumeLabel.text = "Reminder voice: " + progress + "%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        page.addView(voiceVolumeSeek, full())
        page.addView(actionButton("TEST VOICE REMINDER") {
            saveSettings()
            prefs.edit().putInt("voice_volume", voiceVolumeSeek.progress).apply()
            val spoken = nameField.text.toString().ifBlank { "User" } + ", " +
                messageField.text.toString().ifBlank { "Aapka phone Silent Mode mein hai." }
            thread { ReminderWorker.speak(this, spoken) }
        }, full())

        page.addView(label("HOME ARRIVAL ZONE"))
        page.addView(TextView(this).apply {
            text = "Choose how close you must be to your saved home entrance / parking point before the phone changes mode. Smaller zones are more precise but GPS may fluctuate."
            textSize = 12f
            setTextColor(0xFF737B74.toInt())
            setPadding(0, 0, 0, dp(8))
        }, full())
        radiusSpinner = spinner(
            listOf("25 metres — very close to entrance", "40 metres — close", "60 metres — nearby", "100 metres — wider area"),
            listOf(25, 40, 60, 100).indexOf(prefs.getInt("radius", 30)).let { if (it < 0) 0 else it }
        )
        page.addView(radiusSpinner, full())
        page.addView(actionButton("SAVE SETTINGS") {
            saveSettings()
            prefs.edit().putString("arrival_mode", listOf("silent", "vibrate", "low_sound")[arrivalModeSpinner.selectedItemPosition]).apply()
            Toast.makeText(this, "Settings saved.", Toast.LENGTH_SHORT).show()
            buildSettingsScreen()
        }, full().apply { topMargin = dp(22) })
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(page)
        })
    }

    private fun saveConnectedHomeWifi() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            nearbyWifiPermission.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
            return
        }
        try {
            val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val ssid = wifi.connectionInfo?.ssid?.trim('"')
                ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
            val fingerprint = HomeWifiReceiver.getWifiFingerprint(this)
            if (ssid == null && fingerprint == null) {
                Toast.makeText(this, "Home Wi-Fi details unavailable. Connect to home Wi-Fi, turn Location ON temporarily, then save again.", Toast.LENGTH_LONG).show()
                return
            }
            prefs.edit()
                .putString("home_wifi_ssid", ssid)
                .putString("home_wifi_fingerprint", fingerprint)
                .putBoolean("wifi_fallback_enabled", true)
                .apply()
            val label = ssid ?: "Router/LAN fingerprint saved"
            Toast.makeText(this, "Home Wi-Fi saved: $label", Toast.LENGTH_LONG).show()
            buildSettingsScreen()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Android blocked Wi-Fi details. Allow Nearby devices and try again.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not save home Wi-Fi: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openBackgroundLocationSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        startActivity(intent)
    }

    private fun pauseAutomation() {
        LocationServices.getGeofencingClient(this).removeGeofences(geofencePendingIntent())
        androidx.work.WorkManager.getInstance(this).cancelUniqueWork("home-reminder")
        androidx.work.WorkManager.getInstance(this).cancelUniqueWork("home-reminder-first")
        if (prefs.getBoolean("inside_home", false)) HomeWifiReceiver.restorePreviousMode(this)
        prefs.edit().putBoolean("active", false).putBoolean("inside_home", false)
            .putString("last_event", "Automation paused — previous phone mode restored")
            .putLong("last_event_time", System.currentTimeMillis()).apply()
        if (::status.isInitialized) refreshStatus()
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(22), dp(24), dp(22), dp(24))
        background = GradientDrawable().apply {
            setColor(0xFFFFFFFF.toInt())
            cornerRadius = dp(22).toFloat()
            setStroke(dp(1), 0xFFE9ECE8.toInt())
        }
        elevation = dp(2).toFloat()
    }
    private fun full() = LinearLayout.LayoutParams(-1, -2)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun label(value: String) = TextView(this).apply {
        text = value
        textSize = 11f
        letterSpacing = .08f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(0xFF727A73.toInt())
        setPadding(2, dp(20), 2, dp(6))
    }
    private fun spinner(items: List<String>, selected: Int) = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items)
        setSelection(selected)
    }
    private fun actionButton(title: String, action: () -> Unit) = Button(this).apply {
        text = title
        isAllCaps = false
        textSize = 14f
        setOnClickListener { action() }
    }
    private fun saveSettings() {
        val minutes = listOf(15, 20, 30, 45, 60)[intervalSpinner.selectedItemPosition]
        val metres = listOf(25, 40, 60, 100)[radiusSpinner.selectedItemPosition]
        prefs.edit()
            .putString("name", nameField.text.toString().trim().ifBlank { "User" })
            .putString("message", messageField.text.toString().trim().ifBlank { "Aapka phone Silent Mode mein hai." })
            .putInt("interval", minutes)
            .putInt("radius", metres)
            .putInt("voice_volume", if (::voiceVolumeSeek.isInitialized) voiceVolumeSeek.progress else prefs.getInt("voice_volume", 45))
            .apply()
    }
    private fun hasForegroundLocation() =
        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun saveCurrentLocation() {
        if (!hasForegroundLocation()) return
        Toast.makeText(this, "Getting a fresh GPS location…", Toast.LENGTH_SHORT).show()
        val client = LocationServices.getFusedLocationProviderClient(this)
        val token = CancellationTokenSource()
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    prefs.edit().putString("lat", location.latitude.toString())
                        .putString("lng", location.longitude.toString())
                        .putFloat("location_accuracy", location.accuracy)
                        .putLong("location_saved_at", System.currentTimeMillis()).apply()
                    resolveAndSaveAddress(location.latitude, location.longitude) { if (settingsScreen) buildSettingsScreen() }
                    Toast.makeText(this, "Fresh GPS location saved (±" + location.accuracy.toInt() + " m).", Toast.LENGTH_LONG).show()
                    if (!prefs.getBoolean("setup_complete", false)) buildSetupScreen() else if (settingsScreen) buildSettingsScreen()
                } else {
                    Toast.makeText(this, "GPS fix unavailable. Turn on Location and try again outdoors.", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Could not get GPS location: " + it.localizedMessage, Toast.LENGTH_LONG).show()
            }
    }
    private fun updateSavedLocationInfo(view: TextView) {
        val lat = prefs.getString("lat", null)
        val lng = prefs.getString("lng", null)
        val address = prefs.getString("home_address", null)
        view.text = if (lat == null || lng == null) {
            "No home location saved yet. Pick your current location or enter custom coordinates below."
        } else {
            "Location: " + (address ?: "Address lookup pending") +
                "\nLatitude: " + lat + "\nLongitude: " + lng +
                "\nGPS accuracy: ±" + prefs.getFloat("location_accuracy", 0f).toInt() + " m" +
                "\nSaved: " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(prefs.getLong("location_saved_at", 0L)))
        }
    }

    private fun resolveAndSaveAddress(latitude: Double, longitude: Double, onDone: () -> Unit = {}) {
        thread {
            var addressText: String? = null
            try {
                if (Build.VERSION.SDK_INT >= 33) {
                    Geocoder(this, Locale.getDefault()).getFromLocation(latitude, longitude, 1) { results ->
                        addressText = results.firstOrNull()?.getAddressLine(0)
                        runOnUiThread {
                            if (!addressText.isNullOrBlank()) prefs.edit().putString("home_address", addressText).apply()
                            onDone()
                        }
                    }
                    return@thread
                } else {
                    @Suppress("DEPRECATION")
                    val result = Geocoder(this, Locale.getDefault()).getFromLocation(latitude, longitude, 1)
                    addressText = result?.firstOrNull()?.getAddressLine(0)
                }
            } catch (_: Exception) {}
            runOnUiThread {
                if (!addressText.isNullOrBlank()) prefs.edit().putString("home_address", addressText).apply()
                onDone()
            }
        }
    }

    private fun activateHomeAutomation() {
        val lat = prefs.getString("lat", null)?.toDoubleOrNull()
        val lng = prefs.getString("lng", null)?.toDoubleOrNull()
        if (lat == null || lng == null) {
            activeSwitch.isChecked = false
            Toast.makeText(this, "First set your home location in Settings.", Toast.LENGTH_LONG).show()
            return
        }
        val locationManager = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
        val locationOn = locationManager.isLocationEnabled
        val wifiFallback = prefs.getBoolean("wifi_fallback_enabled", false) &&
            (!prefs.getString("home_wifi_ssid", null).isNullOrBlank() ||
             !prefs.getString("home_wifi_fingerprint", null).isNullOrBlank())
        if (!locationOn && !wifiFallback) {
            activeSwitch.isChecked = false
            Toast.makeText(this, "Location is OFF. Turn it ON or save your home Wi-Fi in Settings first.", Toast.LENGTH_LONG).show()
            return
        }
        if (locationOn && !hasForegroundLocation()) {
            activeSwitch.isChecked = false
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        if (locationOn && Build.VERSION.SDK_INT >= 29 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            activeSwitch.isChecked = false
            Toast.makeText(this, "Select Location permission > Allow all the time, then turn ON again.", Toast.LENGTH_LONG).show()
            openBackgroundLocationSettings()
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        if (!manager.isNotificationPolicyAccessGranted) {
            activeSwitch.isChecked = false
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            Toast.makeText(this, "Grant Silent Mode access, then turn ON again.", Toast.LENGTH_LONG).show()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            activeSwitch.isChecked = false
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        // Mark ON before the asynchronous geofence request so the UI and receivers
        // agree on the state. If the user switches OFF while it is pending, the
        // success callback must not silently turn automation back ON.
        prefs.edit().putBoolean("active", true).apply()
        // Evaluate saved router on activation even when GPS is enabled.
        HomeWifiReceiver.evaluate(this)
        if (!locationOn) {
            HomeWifiReceiver.evaluate(this)
            refreshStatus()
            Toast.makeText(this, "Automation ON — home Wi-Fi fallback selected. Android may hide the Wi-Fi name while Location is OFF.", Toast.LENGTH_LONG).show()
            return
        }

        val fence = Geofence.Builder()
            .setRequestId("SHANTI_HOME")
            .setCircularRegion(lat, lng, prefs.getInt("radius", 30).toFloat())
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .setNotificationResponsiveness(0)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(fence).build()
        try {
            LocationServices.getGeofencingClient(this).addGeofences(request, geofencePendingIntent())
                .addOnSuccessListener {
                    if (!prefs.getBoolean("active", false)) {
                        LocationServices.getGeofencingClient(this).removeGeofences(geofencePendingIntent())
                        return@addOnSuccessListener
                    }
                    refreshStatus()
                    Toast.makeText(this, "Shanti Jeewan is ON — home detection active.", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    if (prefs.getBoolean("active", false)) {
                        prefs.edit().putBoolean("active", false).apply()
                        activeSwitch.setOnCheckedChangeListener(null)
                        activeSwitch.isChecked = false
                        activeSwitch.text = "  OFF"
                        activeSwitch.setOnCheckedChangeListener { _, checked ->
                            activeSwitch.text = if (checked) "  ON" else "  OFF"
                            if (checked) activateHomeAutomation() else pauseAutomation()
                        }
                    }
                    refreshStatus()
                    Toast.makeText(this, "Could not activate geofence: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                }
        } catch (e: SecurityException) {
            prefs.edit().putBoolean("active", false).apply()
            activeSwitch.setOnCheckedChangeListener(null)
            activeSwitch.isChecked = false
            activeSwitch.text = "  OFF"
            activeSwitch.setOnCheckedChangeListener { _, checked ->
                activeSwitch.text = if (checked) "  ON" else "  OFF"
                if (checked) activateHomeAutomation() else pauseAutomation()
            }
            Toast.makeText(this, "Location permission missing.", Toast.LENGTH_LONG).show()
        }
    }
    private fun geofencePendingIntent(): android.app.PendingIntent {
        val intent = Intent(this, HomeGeofenceReceiver::class.java)
        return android.app.PendingIntent.getBroadcast(this, 120, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE)
    }
    private fun refreshStatus() {
        if (!::status.isInitialized) return
        val saved = prefs.getString("lat", null) != null
        val active = prefs.getBoolean("active", false)
        val backgroundAllowed = Build.VERSION.SDK_INT < 29 ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        val silentAllowed = getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
        val lastEvent = prefs.getString("last_event", "No arrival or exit event received yet")
        val eventTime = prefs.getLong("last_event_time", 0L)
        val eventStamp = if (eventTime > 0L) java.text.DateFormat.getTimeInstance().format(java.util.Date(eventTime)) else "—"
        status.text = (if (active) "Geofence registered" else "Automation is off") + "\n" +
            (if (saved) "Home location saved" else "Home location not set") + "\n" +
            "Background location: ${if (backgroundAllowed) "Allowed" else "Needed"}  •  Silent access: ${if (silentAllowed) "Allowed" else "Needed"}" +
            "\nLast event: $lastEvent ($eventStamp)\n"
        if (::activeSwitch.isInitialized && activeSwitch.isChecked != active) {
            activeSwitch.setOnCheckedChangeListener(null)
            activeSwitch.isChecked = active
            activeSwitch.text = if (active) "  ON" else "  OFF"
            activeSwitch.setOnCheckedChangeListener { _, checked ->
                activeSwitch.text = if (checked) "  ON" else "  OFF"
                if (checked) activateHomeAutomation() else pauseAutomation()
            }
        }
    }

    private fun showAppDisguisePicker() {
        val options = arrayOf("Shanti Jeewan", "YouTube", "Instagram", "Facebook", "WhatsApp", "Chrome", "Gmail", "Calculator", "Calendar", "Maps", "Photos", "Music", "Clock", "Settings", "Files", "Contacts", "Notes", "Weather", "Telegram", "Snapchat", "Drive", "Browser")
        val current = prefs.getInt("launcher_choice", 0)
        AlertDialog.Builder(this)
            .setTitle("Choose home-screen icon")
            .setSingleChoiceItems(options, current) { dialog, which ->
                applyLauncherChoice(which)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyLauncherChoice(choice: Int) {
        val pm = packageManager
        val flags = PackageManager.DONT_KILL_APP
        try {
            for (i in 0..21) {
                val component = ComponentName(this, "com.ezflexz.shantijeevan.Launcher$i")
                pm.setComponentEnabledSetting(
                    component,
                    if (i == choice) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    flags
                )
            }
            prefs.edit().putInt("launcher_choice", choice).apply()
            Toast.makeText(this, "Home-screen icon updated. If it doesn't refresh, reopen your launcher.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not update launcher icon: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }


}