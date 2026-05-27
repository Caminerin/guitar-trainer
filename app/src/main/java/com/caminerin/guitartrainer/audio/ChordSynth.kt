package com.caminerin.guitartrainer.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Guitar chord synthesizer using enhanced Karplus-Strong plucked string algorithm.
 * Uses a single persistent streaming AudioTrack with smooth crossfade for seamless transitions.
 *
 * Improvements over basic KS:
 * - String-dependent decay (bass strings ring longer)
 * - Realistic strum timing with variable delay per string
 * - Body resonance simulation via low-pass filtering
 * - Extended crossfade (100ms) with equal-power curve for seamless chord changes
 * - Pre-attack warmth: soft onset per string to avoid pick noise
 */
object ChordSynth {
    private const val SAMPLE_RATE = 44100
    private const val CROSSFADE_SAMPLES = 4410 // 100ms crossfade for very smooth transitions
    private val STANDARD_TUNING_HZ = doubleArrayOf(82.41, 110.0, 146.83, 196.0, 246.94, 329.63)

    // Strum delay per string (samples) — varies to simulate natural strumming
    // Bass strings are hit first, with increasing delay toward treble
    private val STRUM_DELAYS = intArrayOf(0, 180, 340, 480, 600, 700) // ~0-16ms spread

    private var streamTrack: AudioTrack? = null
    @Volatile private var running = false
    private var writerThread: Thread? = null

    @Volatile private var currentBuffer: FloatArray? = null
    @Volatile private var currentPos = 0
    @Volatile private var pendingBuffer: FloatArray? = null

    private val lock = Object()

    fun playChord(frets: List<Int?>, durationMs: Int = 1200) {
        val samples = generateGuitarChord(frets, durationMs)
        if (samples == null) return

        synchronized(lock) {
            if (!running) {
                currentBuffer = samples
                currentPos = 0
                startStream()
            } else {
                // Reset crossfade position for smooth blend
                currentPos = 0
                pendingBuffer = samples
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            running = false
            pendingBuffer = null
            currentBuffer = null
        }
        try {
            writerThread?.join(500)
        } catch (_: Exception) {}
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
            .setBufferSizeInBytes(minBuf.coerceAtLeast(16384))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        streamTrack = track
        running = true
        track.play()

        writerThread = Thread {
            val chunkSize = 1024
            val chunk = FloatArray(chunkSize)
            var crossfadeIdx = 0

            while (running) {
                synchronized(lock) {
                    // If current finished but a new chord is pending, switch immediately
                    if (currentBuffer == null && pendingBuffer != null) {
                        currentBuffer = pendingBuffer
                        pendingBuffer = null
                        currentPos = 0
                        crossfadeIdx = 0
                    }

                    val cur = currentBuffer
                    if (cur == null) {
                        chunk.fill(0f)
                    } else {
                        val pending = pendingBuffer
                        if (pending != null) {
                            // Equal-power crossfade for smooth perceptual transition
                            for (i in 0 until chunkSize) {
                                val progress = (crossfadeIdx + i).toFloat() / CROSSFADE_SAMPLES
                                if (progress >= 1f) {
                                    val newPos = crossfadeIdx + i - CROSSFADE_SAMPLES
                                    chunk[i] = if (newPos < pending.size) pending[newPos] else 0f
                                } else {
                                    // Equal-power: use sqrt-based curve
                                    val fadeOut = kotlin.math.sqrt(1f - progress)
                                    val fadeIn = kotlin.math.sqrt(progress)
                                    val oldVal = if (currentPos + i < cur.size) cur[currentPos + i] else 0f
                                    val newVal = if (i < pending.size) pending[i] else 0f
                                    chunk[i] = oldVal * fadeOut + newVal * fadeIn
                                }
                            }
                            crossfadeIdx += chunkSize
                            if (crossfadeIdx >= CROSSFADE_SAMPLES) {
                                // Transition complete
                                currentBuffer = pending
                                currentPos = crossfadeIdx - CROSSFADE_SAMPLES + chunkSize
                                pendingBuffer = null
                                crossfadeIdx = 0
                            } else {
                                currentPos += chunkSize
                            }
                        } else {
                            // Normal playback
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
                }

                try {
                    track.write(chunk, 0, chunkSize, AudioTrack.WRITE_BLOCKING)
                } catch (_: Exception) {
                    break
                }
            }

            try {
                track.stop()
                track.release()
            } catch (_: Exception) {}
        }.apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    /**
     * Generate realistic guitar chord using enhanced Karplus-Strong.
     * Each string has individual characteristics based on its position.
     */
    private fun generateGuitarChord(frets: List<Int?>, durationMs: Int): FloatArray? {
        data class StringInfo(val freq: Double, val stringIndex: Int, val strumOffset: Int)

        val strings = mutableListOf<StringInfo>()
        var strIdx = 0
        for (s in 0 until 6) {
            val fret = frets.getOrNull(s) ?: continue
            if (fret < 0) continue
            val freq = STANDARD_TUNING_HZ[s] * Math.pow(2.0, fret / 12.0)
            val offset = STRUM_DELAYS.getOrElse(strIdx) { strIdx * 150 }
            strings.add(StringInfo(freq, s, offset))
            strIdx++
        }
        if (strings.isEmpty()) return null

        val totalOffset = if (strings.isNotEmpty()) strings.maxOf { it.strumOffset } else 0
        val numSamples = SAMPLE_RATE * durationMs / 1000 + totalOffset
        val output = FloatArray(numSamples)

        for (str in strings) {
            karplusStrongEnhanced(
                output = output,
                freq = str.freq,
                length = numSamples - str.strumOffset,
                offset = str.strumOffset,
                stringIndex = str.stringIndex
            )
        }

        // Body resonance: gentle low-pass filter simulating guitar body
        applyBodyResonance(output)

        // Soft master attack to prevent any initial click (5ms ramp)
        val attackSamples = (SAMPLE_RATE * 0.005).toInt()
        for (i in 0 until attackSamples.coerceAtMost(output.size)) {
            output[i] *= i.toFloat() / attackSamples
        }

        // Normalize
        val peak = output.maxOfOrNull { kotlin.math.abs(it) } ?: 1f
        val scale = if (peak > 0.01f) 0.82f / peak else 1f
        for (i in output.indices) {
            output[i] = (output[i] * scale).coerceIn(-1f, 1f)
        }

        return output
    }

    /**
     * Enhanced Karplus-Strong with string-dependent characteristics.
     * - Bass strings: longer decay, warmer tone (more filtering)
     * - Treble strings: shorter decay, brighter tone
     * - All strings: soft onset to avoid pick click
     */
    private fun karplusStrongEnhanced(
        output: FloatArray,
        freq: Double,
        length: Int,
        offset: Int,
        stringIndex: Int
    ) {
        val period = (SAMPLE_RATE / freq).toInt().coerceAtLeast(2)
        val ring = FloatArray(period)

        // Initialize with shaped noise — different character per string
        val random = java.util.Random((freq * 1000).toLong())

        // Bass strings get more low-frequency content, treble strings more brightness
        val brightnessPassCount = when (stringIndex) {
            0, 1 -> 3    // E, A: warm (more filtering)
            2, 3 -> 2    // D, G: medium
            else -> 1    // B, e: bright (less filtering)
        }

        for (i in ring.indices) {
            ring[i] = (random.nextFloat() * 2f - 1f) * 0.85f
        }

        // Shape the noise excitation
        for (pass in 0 until brightnessPassCount) {
            for (i in 1 until ring.size) {
                ring[i] = ring[i] * 0.5f + ring[i - 1] * 0.5f
            }
        }

        // String-dependent decay: bass strings ring much longer
        val decay = when (stringIndex) {
            0 -> 0.9985f   // Low E: very long sustain
            1 -> 0.9982f   // A
            2 -> 0.9978f   // D
            3 -> 0.9975f   // G
            4 -> 0.9970f   // B
            else -> 0.9965f // High e: shorter sustain
        }

        // Damping blend: bass strings warmer (lower blend), treble brighter
        val blend = when (stringIndex) {
            0, 1 -> 0.42f
            2, 3 -> 0.47f
            else -> 0.52f
        }

        // Soft onset: ramp up the first few cycles to avoid pick click
        val onsetSamples = (period * 2.5).toInt()

        var readIdx = 0
        for (i in 0 until length) {
            val sample = ring[readIdx]
            val nextIdx = (readIdx + 1) % period

            // Two-point averaging filter with decay
            ring[readIdx] = (sample * blend + ring[nextIdx] * (1f - blend)) * decay

            val outIdx = offset + i
            if (outIdx < output.size) {
                // Apply soft onset envelope per string
                val onset = if (i < onsetSamples) i.toFloat() / onsetSamples else 1f
                output[outIdx] += sample * onset
            }
            readIdx = nextIdx
        }
    }

    /**
     * Simulates guitar body resonance with a simple one-pole low-pass filter.
     * This adds warmth and blends the strings together more naturally.
     */
    private fun applyBodyResonance(output: FloatArray) {
        // Mix dry signal with a filtered (body) version
        val bodyMix = 0.15f // 15% body resonance
        var prev = 0f
        val filterCoeff = 0.85f // Low-pass cutoff ~ 700Hz equivalent

        for (i in output.indices) {
            val dry = output[i]
            val filtered = prev + filterCoeff * (dry - prev)
            prev = filtered
            output[i] = dry * (1f - bodyMix) + filtered * bodyMix
        }
    }
}
