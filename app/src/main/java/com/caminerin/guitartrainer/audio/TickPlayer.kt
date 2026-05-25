package com.caminerin.guitartrainer.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class TickPlayer {
    companion object {
        private const val SAMPLE_RATE = 44100
        private const val TICK_DURATION_MS = 30
        private const val TICK_SAMPLES = SAMPLE_RATE * TICK_DURATION_MS / 1000
    }

    private var track: AudioTrack? = null
    private val tickBuffer: ShortArray

    init {
        tickBuffer = ShortArray(TICK_SAMPLES)
        for (i in 0 until TICK_SAMPLES) {
            val t = i.toFloat() / SAMPLE_RATE
            val envelope = exp(-t * 60.0).toFloat()
            val sample = (sin(2.0 * PI * 1000.0 * t) * envelope * Short.MAX_VALUE * 0.8).toInt()
            tickBuffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        val bufSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(tickBuffer.size * 2)

        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track?.write(tickBuffer, 0, tickBuffer.size)
    }

    fun tick() {
        track?.let { t ->
            if (t.playState == AudioTrack.PLAYSTATE_PLAYING) {
                t.stop()
            }
            t.reloadStaticData()
            t.play()
        }
    }

    fun release() {
        track?.release()
        track = null
    }
}
