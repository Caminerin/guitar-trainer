package com.caminerin.guitartrainer.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object ChordSynth {
    private const val SAMPLE_RATE = 44100
    private val STANDARD_TUNING_HZ = doubleArrayOf(82.41, 110.0, 146.83, 196.0, 246.94, 329.63)

    fun playChord(frets: List<Int?>, durationMs: Int = 1200) {
        Thread {
            try {
                val numSamples = SAMPLE_RATE * durationMs / 1000
                val samples = FloatArray(numSamples)

                val activeStrings = mutableListOf<Double>()
                for (s in 0 until 6) {
                    val fret = frets.getOrNull(s) ?: continue
                    if (fret < 0) continue
                    val freq = STANDARD_TUNING_HZ[s] * Math.pow(2.0, fret / 12.0)
                    activeStrings.add(freq)
                }

                if (activeStrings.isEmpty()) return@Thread

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / SAMPLE_RATE
                    var sample = 0.0
                    val envelope = exp(-t * 2.5)

                    for (freq in activeStrings) {
                        val fundamental = sin(2.0 * PI * freq * t)
                        val harmonic2 = 0.5 * sin(2.0 * PI * freq * 2 * t)
                        val harmonic3 = 0.25 * sin(2.0 * PI * freq * 3 * t)
                        val harmonic4 = 0.12 * sin(2.0 * PI * freq * 4 * t)
                        sample += (fundamental + harmonic2 + harmonic3 + harmonic4) * envelope
                    }

                    samples[i] = (sample / activeStrings.size * 0.8).toFloat()
                        .coerceIn(-1f, 1f)
                }

                val bufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_FLOAT
                )

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize.coerceAtLeast(numSamples * 4))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(samples, 0, numSamples, AudioTrack.WRITE_BLOCKING)
                track.play()

                Thread.sleep(durationMs.toLong() + 100)
                track.stop()
                track.release()
            } catch (_: Exception) { }
        }.start()
    }
}
