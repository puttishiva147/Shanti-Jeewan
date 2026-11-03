package com.ezflexz.shantijeevan

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Calendar
import java.util.Locale

object SmartAlertEngine {
    fun enabled(context: Context)=context.getSharedPreferences("smart_alerts",Context.MODE_PRIVATE).getBoolean("enabled",false)
    fun muted(context: Context):Boolean {
        val p=context.getSharedPreferences("smart_alerts",Context.MODE_PRIVATE)
        if(!enabled(context)||p.getBoolean("muted",false)) return true
        val start=p.getInt("quiet_start",-1); val end=p.getInt("quiet_end",-1)
        if(start<0||end<0||start==end) return false
        val c=Calendar.getInstance(); val now=c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE)
        return if(start<end) now in start until end else now>=start||now<end
    }
    fun selectedName(context:Context, identity:String):String? {
        val p=context.getSharedPreferences("smart_alerts",Context.MODE_PRIVATE)
        if(!enabled(context)||identity.isBlank()) return null
        val normalized=identity.filter { it.isDigit() || it=='+' }
        val entries=p.getStringSet("contacts",emptySet()) ?: emptySet()
        return entries.firstNotNullOfOrNull { item ->
            val parts=item.split("|",limit=2); val number=parts.getOrElse(0){""}; val name=parts.getOrElse(1){number}
            val match=if(normalized.length>=7) normalized.takeLast(10)==number.filter(Char::isDigit).takeLast(10)
                else identity.contains(name,true)||name.contains(identity,true)
            if(match) name else null
        }
    }
    private fun tonePreset(context:Context, identity:String, ringtone:Boolean):Int {
        val p=context.getSharedPreferences("smart_alerts",Context.MODE_PRIVATE)
        val entries=p.getStringSet("contacts",emptySet()) ?: emptySet()
        val digits=identity.filter { it.isDigit() }
        val item=entries.firstOrNull { entry ->
            val number=entry.substringBefore("|").filter { it.isDigit() }
            if(digits.length>=7) digits.takeLast(10)==number.takeLast(10)
            else entry.substringAfter("|", "").contains(identity,true)
        } ?: return 0
        val number=item.substringBefore("|")
        val key=(if(ringtone) "call_tone_" else "message_tone_")+number
        return when(p.getString(key,"Default")) { "Soft Bell"->1; "Warm Chime"->2; "Quiet Pulse"->3; else->0 }
    }

    fun alert(context:Context, identity:String, message:String, read:Boolean, tone:Boolean, ringtone:Boolean=false) {
        if(muted(context)) return
        val name=selectedName(context,identity) ?: return
        val p=context.getSharedPreferences("smart_alerts",Context.MODE_PRIVATE)
        if(tone) SmartAlertSound.play(context, tonePreset(context,identity,ringtone),ringtone)
        if(read) {
            val text=if(message.isBlank()) "Incoming call from $name" else "$name says: $message"
            lateinit var tts: TextToSpeech\n            tts=TextToSpeech(context) { status ->
                if(status==TextToSpeech.SUCCESS) {
                    ttsLocale(tts)
                    tts.setSpeechRate(0.94f)
                    tts.setVolume((p.getInt("voice_volume",75)/100f).coerceIn(0f,1f))
                    tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"smart-alert-${System.currentTimeMillis()}")
                }
            }
        }
    }
    private fun ttsLocale(tts:TextToSpeech) { try { tts.language=Locale.getDefault() } catch (_:Throwable) {} }
}
