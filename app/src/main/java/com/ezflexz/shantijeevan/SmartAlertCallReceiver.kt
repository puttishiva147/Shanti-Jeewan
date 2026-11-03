package com.ezflexz.shantijeevan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class SmartAlertCallReceiver : BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action!=TelephonyManager.ACTION_PHONE_STATE_CHANGED||!SmartAlertEngine.enabled(context)) return
        val state=intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        if(state==TelephonyManager.EXTRA_STATE_RINGING) {
            val number=intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: return
            val p=context.getSharedPreferences("smart_alerts",Context.MODE_PRIVATE)
            val now=System.currentTimeMillis()
            if(SmartAlertEngine.selectedName(context,number)!=null && (p.getString("last_call_number","")!=number || now-p.getLong("last_call_at",0L)>30000L)) {\n                p.edit().putString("last_call_number",number).putLong("last_call_at",now).apply()
                SmartAlertEngine.alert(context,number,"",p.getBoolean("call_announce",true),p.getBoolean("call_sound",true),true)
            }
        }
    }
}
