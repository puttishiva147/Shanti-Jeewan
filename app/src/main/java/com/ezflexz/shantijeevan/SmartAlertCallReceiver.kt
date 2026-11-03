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
            if(SmartAlertEngine.selectedName(context,number)!=null) {
                SmartAlertEngine.alert(context,number,"",p.getBoolean("call_announce",true),p.getBoolean("call_sound",true),true)
            }
        }
    }
}
