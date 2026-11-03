package com.ezflexz.shantijeevan

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.app.Notification

class SmartAlertNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if(sbn==null||!SmartAlertEngine.enabled(this)||SmartAlertEngine.muted(this)) return
        val pkg=sbn.packageName ?: return
        val isWhatsApp=pkg=="com.whatsapp"||pkg=="com.whatsapp.w4b"
        val extras=sbn.notification.extras
        val title=extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text=extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?.takeIf { it.isNotBlank() } ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if(title.isBlank()||text.isBlank()||text.equals("New message",true)) return
        val p=getSharedPreferences("smart_alerts",MODE_PRIVATE)
        val allowed=if(isWhatsApp) p.getBoolean("wa_read",true) else p.getBoolean("sms_read",true)
        val selected=SmartAlertEngine.selectedName(this,title) ?: return
        if(allowed) SmartAlertEngine.alert(this,title,text,true,p.getBoolean("message_sound",true))
    }
}
