package com.ezflexz.shantijeevan

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
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
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("shanti", MODE_PRIVATE) }
    private lateinit var status: TextView
    private lateinit var nameField: EditText
    private lateinit var messageField: EditText
    private lateinit var intervalSpinner: Spinner
    private lateinit var radiusSpinner: Spinner

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            saveCurrentLocation()
        } else {
            Toast.makeText(this, "Location permission is required.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        refreshStatus()
    }

    private fun buildScreen() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(26, 24, 26, 24)
            setBackgroundColor(0xFFF7F8FC.toInt())
        }
        page.addView(TextView(this).apply {
            text = "◒"
            textSize = 48f
            gravity = Gravity.CENTER
            setTextColor(0xFF111827.toInt())
        }, full())
        page.addView(TextView(this).apply {
            text = "Shanti Jeewan"
            textSize = 29f
            gravity = Gravity.CENTER
            setTextColor(0xFF101522.toInt())
        }, full())
        page.addView(TextView(this).apply {
            text = "A little peace, automatically."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFF687084.toInt())
        }, full())
        status = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF263247.toInt())
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFFE9EDF5.toInt())
        }
        page.addView(status, full().apply { topMargin = 20; bottomMargin = 10 })

        page.addView(label("Your name"))
        nameField = EditText(this).apply {
            hint = "Shiva"
            setText(prefs.getString("name", "Shiva"))
            setSingleLine(true)
        }
        page.addView(nameField, full())

        page.addView(label("Voice reminder"))
        messageField = EditText(this).apply {
            hint = "Aapka phone Silent Mode mein hai."
            setText(prefs.getString("message", "Aapka phone Silent Mode mein hai."))
            minLines = 2
        }
        page.addView(messageField, full())

        page.addView(label("Reminder interval"))
        intervalSpinner = spinner(listOf("15 minutes", "20 minutes", "30 minutes", "45 minutes", "60 minutes"),
            listOf(15,20,30,45,60).indexOf(prefs.getInt("interval",20)).coerceAtLeast(0))
        page.addView(intervalSpinner, full())

        page.addView(label("Home detection radius"))
        radiusSpinner = spinner(listOf("100 metres", "150 metres", "200 metres", "300 metres"),
            listOf(100,150,200,300).indexOf(prefs.getInt("radius",150)).coerceAtLeast(0))
        page.addView(radiusSpinner, full())

        page.addView(actionButton("SAVE SETTINGS") {
            saveSettings()
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        }, full().apply { topMargin = 12 })
        page.addView(actionButton("SET CURRENT LOCATION AS HOME") {
            if (hasForegroundLocation()) saveCurrentLocation()
            else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }, full())
        page.addView(actionButton("ALLOW BACKGROUND LOCATION") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")))
            Toast.makeText(this, "Set Location permission to Allow all the time.", Toast.LENGTH_LONG).show()
        }, full())
        page.addView(actionButton("ALLOW SILENT MODE ACCESS") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }, full())
        page.addView(actionButton("ACTIVATE SHANTI JEEWAN") {
            saveSettings()
            activateHomeAutomation()
        }, full())
        page.addView(actionButton("PAUSE AUTOMATION") {
            LocationServices.getGeofencingClient(this).removeGeofences(geofencePendingIntent())
            androidx.work.WorkManager.getInstance(this).cancelUniqueWork("home-reminder")
            prefs.edit().putBoolean("active", false).apply()
            refreshStatus()
        }, full())
        page.addView(TextView(this).apply {
            text = "Powered by"
            gravity = Gravity.CENTER
            textSize = 11f
            setTextColor(0xFF7A8292.toInt())
            setPadding(0, 22, 0, 2)
        }, full())
        page.addView(ImageView(this).apply {
            setImageResource(R.drawable.ezflexz_mark)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(64, 64).apply { gravity = Gravity.CENTER })
        page.addView(TextView(this).apply {
            text = "EZFLEXZ"
            gravity = Gravity.CENTER
            textSize = 14f
            letterSpacing = .28f
            setTextColor(0xFF111827.toInt())
        }, full())
        setContentView(ScrollView(this).apply { addView(page) })
    }

    private fun full() = LinearLayout.LayoutParams(-1, -2)
    private fun label(value: String) = TextView(this).apply {
        text = value
        textSize = 13f
        setTextColor(0xFF39445A.toInt())
        setPadding(2, 13, 2, 4)
    }
    private fun spinner(items: List<String>, selected: Int) = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items)
        setSelection(selected)
    }
    private fun actionButton(textValue: String, action: () -> Unit) = Button(this).apply {
        text = textValue
        setOnClickListener { action() }
    }
    private fun saveSettings() {
        val minutes = listOf(15,20,30,45,60)[intervalSpinner.selectedItemPosition]
        val metres = listOf(100,150,200,300)[radiusSpinner.selectedItemPosition]
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
                    refreshStatus()
                } else {
                    Toast.makeText(this, "Location unavailable. Turn on GPS and try again.", Toast.LENGTH_LONG).show()
                }
            }
    }
    private fun activateHomeAutomation() {
        val lat = prefs.getString("lat", null)?.toDoubleOrNull()
        val lng = prefs.getString("lng", null)?.toDoubleOrNull()
        if (lat == null || lng == null) {
            Toast.makeText(this, "First save your home location.", Toast.LENGTH_LONG).show()
            return
        }
        if (!hasForegroundLocation()) {
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        if (Build.VERSION.SDK_INT >= 29 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Allow background location: tap the background-location button and choose Allow all the time.", Toast.LENGTH_LONG).show()
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        if (!manager.isNotificationPolicyAccessGranted) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            Toast.makeText(this, "Grant Silent Mode access, then tap Activate again.", Toast.LENGTH_LONG).show()
            return
        }
        val fence = Geofence.Builder()
            .setRequestId("SHANTI_HOME")
            .setCircularRegion(lat, lng, prefs.getInt("radius",150).toFloat())
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
                    Toast.makeText(this, "Shanti Jeewan activated.", Toast.LENGTH_LONG).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Could not activate: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Location permission missing: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
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
        val allowed = getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
        status.text = "Automation: ${if (prefs.getBoolean("active", false)) "ACTIVE" else "PAUSED"}\n" +
            "Home location: ${if (saved) "Saved" else "Not set"}\n" +
            "Silent Mode permission: ${if (allowed) "Granted" else "Needed"}"
    }
}