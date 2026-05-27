package com.caminerin.guitartrainer.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Guitar chord synthesizer using Karplus-Strong plucked string algorithm.
 * Uses a single persistent streaming AudioTrack with crossfade for smooth transitions.
 */
object ChordSynth {
    private const val SAMPLE_RATE = 44100
    private const val CROSSFADE_SAMPLES = 2200 // ~50ms crossfade
    private val STANDARD_TUNING_HZ = doubleArrayOf(82.41, 110.0, 146.83, 196.0, 246.94, 329.63)

    // Strum delay between strings (samples) — simulates pick sweep
    private const val STRUM_DELAY_SAMPLES = 220 // ~5ms per string

    private var streamTrack: AudioTrack? = null
    @Volatile private var running = false
    private var writerThread: Thread? = null

    // Buffer for the currently-playing chord samples
    @Volatile private var currentBuffer: FloatArray? = null
    @Volatile private var currentPos = 0

    // Queue for the next chord (crossfade target)
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
            .setBufferSizeInBytes(minBuf.coerceAtLeast(8192))
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
                    val cur = currentBuffer
                    if (cur == null) {
                        // Silence
                        chunk.fill(0f)
                    } else {
                        val pending = pendingBuffer
                        if (pending != null) {
                            // Crossfade from current to pending
                            for (i in 0 until chunkSize) {
                                val crossfadeProgress = (currentPos + i).toFloat() / CROSSFADE_SAMPLES
                                if (crossfadeProgress >= 1f) {
                                    // Fully transitioned
                                    val newPos = i
                                    chunk[i] = if (newPos < pending.size) pending[newPos] else 0f
                                } else {
                                    val fadeOut = 1f - crossfadeProgress
                                    val fadeIn = crossfadeProgress
                                    val oldVal = if (currentPos + i < cur.size) cur[currentPos + i] else 0f
                                    val newVal = if (i < pending.size) pending[i] else 0f
                                    chunk[i] = oldVal * fadeOut + newVal * fadeIn
                                }
                            }
                            // Switch to the new buffer
                            currentBuffer = pending
                            currentPos = chunkSize.coerceAtMost(pending.size)
                            pendingBuffer = null
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

                // If nothing left to play and no pending, wind down
                synchronized(lock) {
                    if (currentBuffer == null && pendingBuffer == null) {
                        // Keep stream alive briefly in case a new chord arrives soon
                    }
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
     * Generate guitar-like sound using Karplus-Strong algorithm.
     * Each string is plucked with a slight strum delay for realism.
     */
    private fun generateGuitarChord(frets: List<Int?>, durationMs: Int): FloatArray? {
        data class StringInfo(val freq: Double, val strumOffset: Int)

        val strings = mutableListOf<StringInfo>()
        var strIdx = 0
        for (s in 0 until 6) {
            val fret = frets.getOrNull(s) ?: continue
            if (fret < 0) continue
            val freq = STANDARD_TUNING_HZ[s] * Math.pow(2.0, fret / 12.0)
            strings.add(StringInfo(freq, strIdx * STRUM_DELAY_SAMPLES))
            strIdx++
        }
        if (strings.isEmpty()) return null

        val totalOffset = if (strings.size > 1) strings.last().strumOffset else 0
        val numSamples = SAMPLE_RATE * durationMs / 1000 + totalOffset
        val output = FloatArray(numSamples)

        for (str in strings) {
            karplusStrong(output, str.freq, numSamples - str.strumOffset, str.strumOffset)
        }

        // Normalize and apply master envelope
        val peak = output.maxOfOrNull { kotlin.math.abs(it) } ?: 1f
        val scale = if (peak > 0.01f) 0.85f / peak else 1f
        for (i in output.indices) {
            output[i] = (output[i] * scale).coerceIn(-1f, 1f)
        }

        return output
    }

    /**
     * Karplus-Strong plucked string synthesis.
     * Fills a delay-line ring buffer with noise, then feeds it back through a low-pass filter.
     */
    private fun karplusStrong(
        output: FloatArray,
        freq: Double,
        length: Int,
        offset: Int
    ) {
        val period = (SAMPLE_RATE / freq).toInt().coerceAtLeast(2)
        val ring = FloatArray(period)

        // Initialize with filtered noise burst (band-limited for more warmth)
        val random = java.util.Random(System.nanoTime())
        for (i in ring.indices) {
            ring[i] = (random.nextFloat() * 2f - 1f) * 0.9f
        }
        // Pre-filter the noise for a warmer tone
        for (pass in 0..1) {
            for (i in 1 until ring.size) {
                ring[i] = ring[i] * 0.5f + ring[i - 1] * 0.5f
            }
        }

        var readIdx = 0
        // Decay factor: slightly less than 1.0 for natural string decay
        val decay = 0.996f
        // Damping blend: higher = brighter, lower = warmer
        val blend = 0.48f

        for (i in 0 until length) {
            val sample = ring[readIdx]
            val nextIdx = (readIdx + 1) % period
            // Averaging low-pass filter + decay
            ring[readIdx] = (sample * blend + ring[nextIdx] * (1f - blend)) * decay

            val outIdx = offset + i
            if (outIdx < output.size) {
                output[outIdx] += sample
            }
            readIdx = nextIdx
        }
    }
}
