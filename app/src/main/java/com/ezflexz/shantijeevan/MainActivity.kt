package com.ezflexz.shantijeevan

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("shanti", MODE_PRIVATE) }
    private lateinit var status: TextView
    private lateinit var activeSwitch: Switch
    private lateinit var nameField: EditText
    private lateinit var messageField: EditText
    private lateinit var intervalSpinner: Spinner
    private lateinit var radiusSpinner: Spinner
    private var settingsScreen = false

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            saveCurrentLocation()
        } else {
            Toast.makeText(this, "Location permission is required.", Toast.LENGTH_LONG).show()
            if (::activeSwitch.isInitialized) activeSwitch.isChecked = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildHomeScreen()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized && !settingsScreen) refreshStatus()
    }

    private fun buildHomeScreen() {
        settingsScreen = false
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(30), dp(24), dp(24))
            setBackgroundColor(0xFFF8F8F6.toInt())
        }
        page.addView(TextView(this).apply {
            text = "◒"
            textSize = 46f
            gravity = Gravity.CENTER
            setTextColor(0xFF252A27.toInt())
        }, full())
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
        footer.addView(ImageView(this).apply {
            setImageResource(R.drawable.ezflexz_mark)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(46), dp(46)))
        footer.addView(TextView(this).apply {
            text = "EZFLEXZ"
            textSize = 12f
            letterSpacing = .22f
            gravity = Gravity.CENTER
            setTextColor(0xFF353B36.toInt())
        }, full())
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

        page.addView(label("HOME LOCATION"))
        page.addView(actionButton("SET CURRENT LOCATION AS HOME") {
            if (hasForegroundLocation()) saveCurrentLocation()
            else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }, full())
        page.addView(actionButton("BACKGROUND LOCATION PERMISSION") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            Toast.makeText(this, "Choose Allow all the time for Location.", Toast.LENGTH_LONG).show()
        }, full())
        page.addView(label("SILENT MODE"))
        page.addView(actionButton("ALLOW SILENT MODE ACCESS") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }, full())

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

        page.addView(label("HOME DETECTION RADIUS"))
        radiusSpinner = spinner(
            listOf("100 metres", "150 metres", "200 metres", "300 metres"),
            listOf(100, 150, 200, 300).indexOf(prefs.getInt("radius", 150)).coerceAtLeast(0)
        )
        page.addView(radiusSpinner, full())
        page.addView(actionButton("SAVE SETTINGS") {
            saveSettings()
            Toast.makeText(this, "Settings saved.", Toast.LENGTH_SHORT).show()
            buildSettingsScreen()
        }, full().apply { topMargin = dp(22) })
    }

    private fun pauseAutomation() {
        LocationServices.getGeofencingClient(this).removeGeofences(geofencePendingIntent())
        androidx.work.WorkManager.getInstance(this).cancelUniqueWork("home-reminder")
        androidx.work.WorkManager.getInstance(this).cancelUniqueWork("home-reminder-first")
        prefs.edit().putBoolean("active", false).apply()
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
        val metres = listOf(100, 150, 200, 300)[radiusSpinner.selectedItemPosition]
        prefs.edit()
            .putString("name", nameField.text.toString().trim().ifBlank { "User" })
            .putString("message", messageField.text.toString().trim().ifBlank { "Aapka phone Silent Mode mein hai." })
            .putInt("interval", minutes)
            .putInt("radius", metres)
            .apply()
    }
    private fun hasForegroundLocation() =
        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun saveCurrentLocation() {
        if (!hasForegroundLocation()) return
        LocationServices.getFusedLocationProviderClient(this).lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    prefs.edit().putString("lat", location.latitude.toString())
                        .putString("lng", location.longitude.toString()).apply()
                    Toast.makeText(this, "Home location saved.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Location unavailable. Turn on GPS and try again.", Toast.LENGTH_LONG).show()
                }
            }
    }
    private fun activateHomeAutomation() {
        val lat = prefs.getString("lat", null)?.toDoubleOrNull()
        val lng = prefs.getString("lng", null)?.toDoubleOrNull()
        if (lat == null || lng == null) {
            Toast.makeText(this, "First set your home location in Settings.", Toast.LENGTH_LONG).show()
            activeSwitch.isChecked = false
            return
        }
        if (!hasForegroundLocation()) {
            activeSwitch.isChecked = false
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        if (Build.VERSION.SDK_INT >= 29 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            activeSwitch.isChecked = false
            Toast.makeText(this, "Allow background location from Settings first.", Toast.LENGTH_LONG).show()
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        if (!manager.isNotificationPolicyAccessGranted) {
            activeSwitch.isChecked = false
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            Toast.makeText(this, "Grant Silent Mode access, then turn ON again.", Toast.LENGTH_LONG).show()
            return
        }
        val fence = Geofence.Builder()
            .setRequestId("SHANTI_HOME")
            .setCircularRegion(lat, lng, prefs.getInt("radius", 150).toFloat())
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .setLoiteringDelay(60_000)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(fence).build()
        try {
            LocationServices.getGeofencingClient(this).addGeofences(request, geofencePendingIntent())
                .addOnSuccessListener {
                    prefs.edit().putBoolean("active", true).apply()
                    refreshStatus()
                    Toast.makeText(this, "Shanti Jeewan is ON.", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    activeSwitch.isChecked = false
                    Toast.makeText(this, "Could not activate: \${it.localizedMessage}", Toast.LENGTH_LONG).show()
                }
        } catch (e: SecurityException) {
            activeSwitch.isChecked = false
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
        status.text = (if (active) "Automation is active" else "Automation is off") + "\n" +
            (if (saved) "Home location saved" else "Home location not set")
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
}