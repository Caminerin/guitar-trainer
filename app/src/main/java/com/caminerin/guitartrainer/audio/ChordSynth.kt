package com.caminerin.guitartrainer.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Guitar chord synthesizer using enhanced Karplus-Strong plucked string algorithm.
 * Crossfade is baked into the buffer itself for glitch-free transitions.
 */
object ChordSynth {
    private const val SAMPLE_RATE = 44100
    private const val CROSSFADE_SAMPLES = 4410 // 100ms
    private val STANDARD_TUNING_HZ = doubleArrayOf(82.41, 110.0, 146.83, 196.0, 246.94, 329.63)
    private val STRUM_DELAYS = intArrayOf(0, 180, 340, 480, 600, 700)

    private var streamTrack: AudioTrack? = null
    @Volatile private var running = false
    private var writerThread: Thread? = null

    // Simple model: one buffer playing, one buffer queued
    @Volatile private var currentBuffer: FloatArray? = null
    @Volatile private var currentPos = 0
    @Volatile private var pendingSwap: FloatArray? = null

    private val lock = Object()

    fun playChord(frets: List<Int?>, durationMs: Int = 1200, upStrum: Boolean = false, velocity: Float = 1.0f) {
        val newSamples = generateGuitarChord(frets, durationMs, upStrum, velocity) ?: return

        synchronized(lock) {
            if (!running) {
                currentBuffer = newSamples
                currentPos = 0
                startStream()
            } else {
                // Bake the crossfade: blend tail of current into head of new
                val cur = currentBuffer
                val pos = currentPos
                if (cur != null && pos < cur.size) {
                    val remaining = cur.size - pos
                    val fadeLen = CROSSFADE_SAMPLES.coerceAtMost(remaining).coerceAtMost(newSamples.size)
                    for (i in 0 until fadeLen) {
                        val progress = i.toFloat() / fadeLen
                        val fadeOut = kotlin.math.sqrt(1f - progress)
                        val fadeIn = kotlin.math.sqrt(progress)
                        newSamples[i] = cur[pos + i] * fadeOut + newSamples[i] * fadeIn
                    }
                }
                // Swap: the writer thread will pick this up seamlessly
                pendingSwap = newSamples
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            running = false
            pendingSwap = null
            currentBuffer = null
        }
        try { writerThread?.join(500) } catch (_: Exception) {}
        try {
            streamTrack?.stop()
            streamTrack?.release()
        } catch (_: Exception) {}
        streamTrack = null
        writerThread = null
    }

    private fun startStream() {
        if (running) return

        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT
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
            .setBufferSizeInBytes(minBuf.coerceAtLeast(16384))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        streamTrack = track
        running = true
        track.play()

        writerThread = Thread {
            val chunkSize = 1024
            val chunk = FloatArray(chunkSize)

            while (running) {
                synchronized(lock) {
                    // Check if a new buffer is ready (crossfade already baked in)
                    val swap = pendingSwap
                    if (swap != null) {
                        currentBuffer = swap
                        currentPos = 0
                        pendingSwap = null
                    }

                    val cur = currentBuffer
                    if (cur == null) {
                        chunk.fill(0f)
                    } else {
                        for (i in 0 until chunkSize) {
                            val pos = currentPos + i
                            chunk[i] = if (pos < cur.size) cur[pos] else 0f
                        }
                        currentPos += chunkSize
                        if (currentPos >= cur.size) {
                            currentBuffer = null
                        }
                    }
                }

                try {
                    track.write(chunk, 0, chunkSize, AudioTrack.WRITE_BLOCKING)
                } catch (_: Exception) { break }
            }

            try { track.stop(); track.release() } catch (_: Exception) {}
        }.apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun generateGuitarChord(frets: List<Int?>, durationMs: Int, upStrum: Boolean = false, velocity: Float = 1.0f): FloatArray? {
        data class StringInfo(val freq: Double, val stringIndex: Int, val strumOffset: Int)

        val activeStrings = mutableListOf<Pair<Int, Double>>() // (stringIndex, freq)
        for (s in 0 until 6) {
            val fret = frets.getOrNull(s) ?: continue
            if (fret < 0) continue
            val freq = STANDARD_TUNING_HZ[s] * Math.pow(2.0, fret / 12.0)
            activeStrings.add(s to freq)
        }
        if (activeStrings.isEmpty()) return null

        // For up-strum: reverse the strum order (treble first)
        val ordered = if (upStrum) activeStrings.reversed() else activeStrings
        // Softer strums have wider string delays (slower, lighter strum)
        val delayScale = if (velocity < 0.6f) 1.4f else 1.0f
        val strings = ordered.mapIndexed { strIdx, (s, freq) ->
            val baseOffset = STRUM_DELAYS.getOrElse(strIdx) { strIdx * 150 }
            StringInfo(freq, s, (baseOffset * delayScale).toInt())
        }

        val totalOffset = if (strings.isNotEmpty()) strings.maxOf { it.strumOffset } else 0
        val numSamples = SAMPLE_RATE * durationMs / 1000 + totalOffset
        val output = FloatArray(numSamples)

        for (str in strings) {
            karplusStrongEnhanced(output, str.freq, numSamples - str.strumOffset, str.strumOffset, str.stringIndex)
        }

        // Body resonance
        applyBodyResonance(output)

        // Soft master attack (5ms)
        val attackSamples = (SAMPLE_RATE * 0.005).toInt()
        for (i in 0 until attackSamples.coerceAtMost(output.size)) {
            output[i] *= i.toFloat() / attackSamples
        }

        // Normalize with velocity
        val clampedVelocity = velocity.coerceIn(0.2f, 1.0f)
        val peak = output.maxOfOrNull { kotlin.math.abs(it) } ?: 1f
        val scale = if (peak > 0.01f) 0.82f * clampedVelocity / peak else 1f
        for (i in output.indices) {
            output[i] = (output[i] * scale).coerceIn(-1f, 1f)
        }

        return output
    }

    private fun karplusStrongEnhanced(
        output: FloatArray, freq: Double, length: Int, offset: Int, stringIndex: Int
    ) {
        val period = (SAMPLE_RATE / freq).toInt().coerceAtLeast(2)
        val ring = FloatArray(period)

        val random = java.util.Random((freq * 1000).toLong())
        val brightnessPassCount = when (stringIndex) {
            0, 1 -> 3; 2, 3 -> 2; else -> 1
        }

        for (i in ring.indices) {
            ring[i] = (random.nextFloat() * 2f - 1f) * 0.85f
        }
        for (pass in 0 until brightnessPassCount) {
            for (i in 1 until ring.size) {
                ring[i] = ring[i] * 0.5f + ring[i - 1] * 0.5f
            }
        }

        val decay = when (stringIndex) {
            0 -> 0.9985f; 1 -> 0.9982f; 2 -> 0.9978f
            3 -> 0.9975f; 4 -> 0.9970f; else -> 0.9965f
        }
        val blend = when (stringIndex) {
            0, 1 -> 0.42f; 2, 3 -> 0.47f; else -> 0.52f
        }

        val onsetSamples = (period * 2.5).toInt()
        var readIdx = 0

        for (i in 0 until length) {
            val sample = ring[readIdx]
            val nextIdx = (readIdx + 1) % period
            ring[readIdx] = (sample * blend + ring[nextIdx] * (1f - blend)) * decay

            val outIdx = offset + i
            if (outIdx < output.size) {
                val onset = if (i < onsetSamples) i.toFloat() / onsetSamples else 1f
                output[outIdx] += sample * onset
            }
            readIdx = nextIdx
        }
    }

    private fun applyBodyResonance(output: FloatArray) {
        val bodyMix = 0.15f
        var prev = 0f
        val filterCoeff = 0.85f
        for (i in output.indices) {
            val dry = output[i]
            val filtered = prev + filterCoeff * (dry - prev)
            prev = filtered
            output[i] = dry * (1f - bodyMix) + filtered * bodyMix
        }
    }
}
