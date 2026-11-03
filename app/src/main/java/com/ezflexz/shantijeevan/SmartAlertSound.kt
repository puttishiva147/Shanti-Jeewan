package com.ezflexz.shantijeevan

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

object SmartAlertSound {
    fun preview(context: Context, preset: Int = 0, ringtone: Boolean = false) { play(context, preset, ringtone) }
    fun play(context: Context, preset: Int = 0, ringtone: Boolean = false) {
        thread(name="smart-alert-tone") {
            val sampleRate=22050
            val notes=when(preset) {
                1 -> listOf(660.0,880.0,990.0)
                2 -> listOf(523.25,659.25,783.99)
                3 -> listOf(440.0,554.37)
                else -> if(ringtone) listOf(523.25,659.25,783.99,659.25) else listOf(880.0,1174.66)
            }
            val duration=if(ringtone) 520 else 170
            val repeats=if(ringtone) 4 else 1
            val samples=ArrayList<Short>()
            repeat(repeats) {
                notes.forEach { frequency ->
                    val count=sampleRate*duration/1000
                    for(i in 0 until count) {
                        val envelope=minOf(1.0,i/500.0,(count-i)/1800.0).coerceAtLeast(0.0)
                        val wave=sin(2.0*PI*frequency*i/sampleRate)*envelope*0.18
                        samples.add((wave*Short.MAX_VALUE).toInt().toShort())
                    }
                }
                if(ringtone) repeat(sampleRate/5) { samples.add(0) }
            }
            val data=ShortArray(samples.size) { samples[it] }
            val track=AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(if(ringtone) AudioAttributes.USAGE_NOTIFICATION_RINGTONE else AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(data.size*2).setTransferMode(AudioTrack.MODE_STATIC).build()
            try { track.write(data,0,data.size); track.play(); Thread.sleep((data.size*1000L/sampleRate)+100) } catch (_:Throwable) {} finally { track.release() }
        }
    }
}
