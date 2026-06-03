package com.caminerin.guitartrainer.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

class TickPlayer {
    companion object {
        private const val SAMPLE_RATE = 44100
        private const val CLICK_SAMPLES = 1323 // 30ms
        private const val SUB_CLICK_SAMPLES = 882 // 20ms
    }

    private var track: AudioTrack? = null

    // Match MetronomeEngine click sound parameters for consistency
    private val mainClick: ShortArray = generateClick(1000f, CLICK_SAMPLES, 1.0f)
    private val subClick: ShortArray = generateClick(800f, SUB_CLICK_SAMPLES, 0.7f)

    init {
        track = try {
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val t = AudioTrack.Builder()
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
                .setBufferSizeInBytes(minBuf.coerceAtLeast(CLICK_SAMPLES * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            t.play()
            t
        } catch (e: Exception) {
            android.util.Log.w("TickPlayer", "AudioTrack init failed", e)
            null
        }
    }

    /**
     * Build and write one full beat with subdivisions.
     * The total length equals one beat at the given BPM.
     */
    fun playBeat(bpm: Int, subdivision: Int = 1) {
        val t = track ?: return
        val totalSamples = (SAMPLE_RATE * 60.0 / bpm).roundToInt()
        val samplesPerSub = totalSamples / subdivision.coerceAtLeast(1)
        val buffer = ShortArray(totalSamples)

        for (sub in 0 until subdivision) {
            val click = if (sub == 0) mainClick else subClick
            val offset = sub * samplesPerSub
            for (i in click.indices) {
                if (offset + i < buffer.size) {
                    buffer[offset + i] = click[i]
                }
            }
        }
        t.write(buffer, 0, buffer.size)
    }

    /** Simple tick — non-blocking so late ticks are dropped, not queued */
    fun tick() {
        val t = track ?: return
        t.flush()
        t.write(mainClick, 0, mainClick.size, AudioTrack.WRITE_NON_BLOCKING)
    }

    fun release() {
        track?.stop()
        track?.release()
        track = null
    }

    private fun generateClick(freq: Float, samples: Int, volume: Float): ShortArray {
        val buf = ShortArray(samples)
        for (i in 0 until samples) {
            val time = i.toFloat() / SAMPLE_RATE
            // Match MetronomeEngine envelope (decay 80 for consistency)
            val envelope = exp(-time * 80.0).toFloat() * volume
            val sample = (sin(2.0 * PI * freq * time) * envelope * Short.MAX_VALUE).toInt()
            buf[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buf
    }
}
