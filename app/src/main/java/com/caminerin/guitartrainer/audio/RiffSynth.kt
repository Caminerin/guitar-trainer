package com.caminerin.guitartrainer.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Single-note guitar synthesizer for riff playback.
 * Uses Karplus-Strong with per-string tone shaping for realistic guitar sound.
 * Supports sound presets (clean, crunch, distortion, etc.).
 */
object RiffSynth {
    private const val SAMPLE_RATE = 44100
    private const val CROSSFADE_SAMPLES = 2205 // 50ms

    private val STANDARD_TUNING_HZ = doubleArrayOf(
        329.63, // string 1 (high E)
        246.94, // string 2 (B)
        196.00, // string 3 (G)
        146.83, // string 4 (D)
        110.00, // string 5 (A)
        82.41   // string 6 (low E)
    )

    private var streamTrack: AudioTrack? = null
    @Volatile private var running = false
    private var writerThread: Thread? = null

    @Volatile private var currentBuffer: FloatArray? = null
    @Volatile private var currentPos = 0
    @Volatile private var pendingSwap: FloatArray? = null

    private val lock = Object()

    data class NoteEvent(
        val string: Int,   // 1-6
        val fret: Int,
        val startMs: Long, // offset from buffer start
        val durationMs: Int
    )

    fun playSequence(notes: List<NoteEvent>, soundPreset: String = "clean") {
        if (notes.isEmpty()) return
        val totalMs = notes.maxOf { it.startMs + it.durationMs } + 200
        val totalSamples = (SAMPLE_RATE * totalMs / 1000).toInt()
        val buffer = FloatArray(totalSamples)

        for (note in notes) {
            val freq = noteFrequency(note.string, note.fret)
            val offsetSamples = (SAMPLE_RATE * note.startMs / 1000).toInt()
            val durSamples = (SAMPLE_RATE * note.durationMs / 1000).toInt()
            synthesizeNote(buffer, freq, note.string, offsetSamples, durSamples, soundPreset)
        }

        applyMasterProcessing(buffer, soundPreset)

        synchronized(lock) {
            if (!running) {
                currentBuffer = buffer
                currentPos = 0
                startStream()
            } else {
                val cur = currentBuffer
                val pos = currentPos
                if (cur != null && pos < cur.size) {
                    val remaining = cur.size - pos
                    val fadeLen = CROSSFADE_SAMPLES.coerceAtMost(remaining).coerceAtMost(buffer.size)
                    for (i in 0 until fadeLen) {
                        val progress = i.toFloat() / fadeLen
                        val fadeOut = kotlin.math.sqrt(1f - progress)
                        val fadeIn = kotlin.math.sqrt(progress)
                        buffer[i] = cur[pos + i] * fadeOut + buffer[i] * fadeIn
                    }
                }
                pendingSwap = buffer
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

    private fun noteFrequency(string: Int, fret: Int): Double {
        val openFreq = STANDARD_TUNING_HZ.getOrElse(string - 1) { 329.63 }
        return openFreq * Math.pow(2.0, fret / 12.0)
    }

    private fun synthesizeNote(
        output: FloatArray, freq: Double, string: Int,
        offset: Int, duration: Int, soundPreset: String
    ) {
        val period = (SAMPLE_RATE / freq).toInt().coerceAtLeast(2)
        val ring = FloatArray(period)
        val random = java.util.Random((freq * 1000).toLong())

        // Brightness varies by string and sound preset
        val brightnessPassCount = when {
            soundPreset.contains("distorsion") || soundPreset.contains("fuzz") -> when (string) {
                1, 2 -> 1; 3, 4 -> 1; else -> 0
            }
            soundPreset.contains("crunch") -> when (string) {
                1, 2 -> 2; 3, 4 -> 1; else -> 1
            }
            else -> when (string) { // clean
                1, 2 -> 3; 3, 4 -> 2; else -> 1
            }
        }

        // Initialize ring buffer with noise
        for (i in ring.indices) {
            ring[i] = (random.nextFloat() * 2f - 1f) * 0.9f
        }

        // Pre-filter for brightness
        for (pass in 0 until brightnessPassCount) {
            for (i in 1 until ring.size) {
                ring[i] = ring[i] * 0.5f + ring[i - 1] * 0.5f
            }
        }

        // Per-string decay and blend
        val decay = when (string) {
            1 -> 0.9990f; 2 -> 0.9987f; 3 -> 0.9983f
            4 -> 0.9980f; 5 -> 0.9975f; else -> 0.9970f
        }
        val blend = when (string) {
            1, 2 -> 0.40f; 3, 4 -> 0.45f; else -> 0.50f
        }

        val attackSamples = (period * 1.5).toInt().coerceAtMost(duration)
        var readIdx = 0
        val len = duration.coerceAtMost(output.size - offset)

        for (i in 0 until len) {
            val sample = ring[readIdx]
            val nextIdx = (readIdx + 1) % period
            ring[readIdx] = (sample * blend + ring[nextIdx] * (1f - blend)) * decay
            readIdx = nextIdx

            val outIdx = offset + i
            if (outIdx < output.size) {
                val attack = if (i < attackSamples) i.toFloat() / attackSamples else 1f
                // Fade out last 5% of duration for clean note end
                val fadeOutStart = (len * 0.95f).toInt()
                val release = if (i > fadeOutStart && len > fadeOutStart) {
                    1f - (i - fadeOutStart).toFloat() / (len - fadeOutStart)
                } else 1f
                output[outIdx] += sample * attack * release
            }
        }
    }

    private fun applyMasterProcessing(output: FloatArray, soundPreset: String) {
        // Apply distortion/overdrive if needed
        if (soundPreset.contains("distorsion") || soundPreset.contains("fuzz")) {
            val gain = if (soundPreset.contains("fuzz")) 4.0f else 2.5f
            for (i in output.indices) {
                val x = output[i] * gain
                output[i] = (2f / Math.PI.toFloat()) * kotlin.math.atan(x)
            }
        } else if (soundPreset.contains("crunch")) {
            val gain = 1.5f
            for (i in output.indices) {
                val x = output[i] * gain
                output[i] = if (x > 0) 1f - kotlin.math.exp(-x) else -(1f - kotlin.math.exp(x))
            }
        }

        // Body resonance (low-pass filter)
        val bodyMix = when {
            soundPreset.contains("clean") || soundPreset.contains("acoustic") -> 0.20f
            soundPreset.contains("surf") -> 0.15f
            else -> 0.10f
        }
        var prev = 0f
        val filterCoeff = 0.85f
        for (i in output.indices) {
            val dry = output[i]
            val filtered = prev + filterCoeff * (dry - prev)
            prev = filtered
            output[i] = dry * (1f - bodyMix) + filtered * bodyMix
        }

        // Soft master attack (3ms)
        val attackSamples = (SAMPLE_RATE * 0.003).toInt()
        for (i in 0 until attackSamples.coerceAtMost(output.size)) {
            output[i] *= i.toFloat() / attackSamples
        }

        // Normalize
        val peak = output.maxOfOrNull { kotlin.math.abs(it) } ?: 1f
        val scale = if (peak > 0.01f) 0.85f / peak else 1f
        for (i in output.indices) {
            output[i] = (output[i] * scale).coerceIn(-1f, 1f)
        }
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
}
