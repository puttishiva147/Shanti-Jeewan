package com.ezflexz.shantijeevan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmartAlertSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(!SmartAlertEngine.enabled(context)||SmartAlertEngine.muted(context)) return
        val p=context.getSharedPreferences("smart_alerts",Context.MODE_PRIVATE)
        if(!p.getBoolean("sms_read",true)) return
        val messages=try { Telephony.Sms.Intents.getMessagesFromIntent(intent) } catch (_:Throwable) { return }
        messages.forEach { sms ->
            val address=sms.originatingAddress ?: return@forEach
            val body=sms.messageBody ?: return@forEach
            if(SmartAlertEngine.selectedName(context,address)!=null)
                SmartAlertEngine.alert(context,address,body,true,p.getBoolean("message_sound",true))
        }
    }
}
