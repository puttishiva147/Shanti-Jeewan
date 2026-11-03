package com.ezflexz.shantijeevan

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class SmartAlertsActivity : ComponentActivity() {
    private val p by lazy { getSharedPreferences("smart_alerts", MODE_PRIVATE) }
    private lateinit var page: LinearLayout
    private val contactPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.data?.let { savePickedContact(it) }
    }
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { render() }

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render() }

    private fun render() {
        page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22, 24, 22, 28); setBackgroundColor(0xFFF8F8F6.toInt()) }
        page.addView(Button(this).apply { text = "‹ BACK"; setOnClickListener { finish() } })
        page.addView(TextView(this).apply { text = "Smart Contact & Alerts"; textSize = 25f; setTextColor(0xFF202522.toInt()); setPadding(0,18,0,6) })
        page.addView(TextView(this).apply { text = "Choose contacts for gentle call and message alerts. Only selected contacts are processed."; textSize = 13f; setTextColor(0xFF68716A.toInt()) })
        page.addView(Switch(this).apply {
            text = "Smart Alerts  •  MASTER ON/OFF"; textSize = 16f
            isChecked = p.getBoolean("enabled", false)
            setOnCheckedChangeListener { _, checked ->
                p.edit().putBoolean("enabled", checked).apply()
                if (checked) requestNeededPermissions()
                render()
            }
        })
        val enabled = p.getBoolean("enabled", false)
        page.addView(TextView(this).apply { text = if (enabled) "Feature enabled. Android permissions and notification access may still be required." else "OFF — this module remains inactive."; setTextColor(0xFF68716A.toInt()); setPadding(0,8,0,12) })
        addToggle("Announce incoming calls", "call_announce")
        addToggle("Play app call ringtone", "call_sound")
        addToggle("Read SMS aloud", "sms_read")
        addToggle("Read WhatsApp aloud", "wa_read")
        addToggle("Play message tone", "message_sound")
        addToggle("Manual mute", "muted")
        page.addView(TextView(this).apply { text = "VOICE VOLUME"; textSize = 11f; setPadding(0,16,0,4); setTextColor(0xFF68716A.toInt()) })
        val volume = SeekBar(this).apply { max = 100; progress = p.getInt("voice_volume", 75); isEnabled = enabled; setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, user: Boolean) { if(user) p.edit().putInt("voice_volume",v).apply() }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        }) }
        page.addView(volume)
        page.addView(Button(this).apply { text = "ADD CONTACT"; isEnabled = enabled; setOnClickListener {
            if (ContextCompat.checkSelfPermission(this@SmartAlertsActivity, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
                permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
            else pickContact()
        } })
        val selected = p.getStringSet("contacts", emptySet())?.toList().orEmpty()
        if (selected.isEmpty()) page.addView(TextView(this).apply { text = "No contacts selected yet."; setPadding(0,12,0,12); setTextColor(0xFF68716A.toInt()) })
        selected.forEach { entry ->
            val parts = entry.split("|", limit=2); val number = parts.getOrElse(0){""}; val name = parts.getOrElse(1){number}
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12,12,12,12); setBackgroundColor(0xFFFFFFFF.toInt()) }
            row.addView(TextView(this).apply { text = "$name\n$number"; textSize = 14f; setTextColor(0xFF202522.toInt()) })
            row.addView(Button(this).apply { text = "Call ringtone: ${p.getString("call_tone_$number","Default")}"; isEnabled = enabled; setOnClickListener { chooseTone(number,name,true) } })\n            row.addView(Button(this).apply { text = "Message tone: ${p.getString("message_tone_$number","Default")}"; isEnabled = enabled; setOnClickListener { chooseTone(number,name,false) } })
            row.addView(Button(this).apply { text = "Remove"; setOnClickListener { val s=(p.getStringSet("contacts", emptySet()) ?: emptySet()).toMutableSet(); s.remove(entry); p.edit().putStringSet("contacts",s).apply(); render() } })
            page.addView(row)
        }
        page.addView(Button(this).apply { text = "ENABLE NOTIFICATION ACCESS"; isEnabled = enabled; setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } })
        page.addView(Button(this).apply { text = "SET SILENT HOURS"; isEnabled = enabled; setOnClickListener { chooseQuietHours() } })
        page.addView(TextView(this).apply {
            text = "Privacy: contact list is stored on this device. Message text is processed in memory only. WhatsApp reading depends on notification previews being visible. No WhatsApp database access."
            textSize = 11f; setTextColor(0xFF68716A.toInt()); setPadding(0,14,0,0)
        })
        setContentView(ScrollView(this).apply { isFillViewport=true; addView(page) })
    }

    private fun addToggle(label: String, key: String) {
        page.addView(Switch(this).apply {
            text = label; isChecked = p.getBoolean(key, key in listOf("call_announce","call_sound","sms_read","wa_read","message_sound"))
            isEnabled = p.getBoolean("enabled",false)
            setOnCheckedChangeListener { _, v -> p.edit().putBoolean(key,v).apply() }
        })
    }

    private fun requestNeededPermissions() {
        val missing = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_PHONE_STATE, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
            .filter { ContextCompat.checkSelfPermission(this,it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    private fun pickContact() { contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }

    private fun savePickedContact(uri: Uri) {
        contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME), null,null,null)?.use {
            if(it.moveToFirst()) {
                val number=it.getString(0)?.replace(Regex("[^+0-9]"),"") ?: return@use
                val name=it.getString(1) ?: number
                val set=(p.getStringSet("contacts", emptySet()) ?: emptySet()).toMutableSet()
                set.removeAll { item -> item.substringBefore("|")==number }
                set.add("$number|$name")
                p.edit().putStringSet("contacts",set).apply()
            }
        }
        render()
    }

    private fun chooseTone(number:String,name:String,isCall:Boolean) {
        val tones=arrayOf("Default","Soft Bell","Warm Chime","Quiet Pulse")
        val key=(if(isCall) "call_tone_" else "message_tone_")+number\n        val current=p.getString(key,"Default")
        android.app.AlertDialog.Builder(this).setTitle("$name sound").setSingleChoiceItems(tones,tones.indexOf(current).coerceAtLeast(0)) { d,which ->
            p.edit().putString("tone_$number",tones[which]).apply()
            SmartAlertSound.preview(this,which); d.dismiss(); render()
        }.setNegativeButton("Cancel",null).show()
    }

    private fun chooseQuietHours() {
        android.app.TimePickerDialog(this,{_,h,m ->
            val start=h*60+m
            android.app.TimePickerDialog(this,{_,eh,em ->
                p.edit().putInt("quiet_start",start).putInt("quiet_end",eh*60+em).apply()
                Toast.makeText(this,"Silent hours saved",Toast.LENGTH_SHORT).show()
            },22,0,true).show()
        },22,0,true).show()
    }
}
