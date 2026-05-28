package com.caminerin.guitartrainer.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Guitar chord synthesizer using enhanced Karplus-Strong plucked string algorithm.
 * Supports velocity-dependent tone, palm mutes, and strum variation.
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

    @Volatile private var currentBuffer: FloatArray? = null
    @Volatile private var currentPos = 0
    @Volatile private var pendingSwap: FloatArray? = null

    private val lock = Object()

    enum class StrokeType { DOWN, UP, MUTE }

    fun playChord(
        frets: List<Int?>,
        durationMs: Int = 1200,
        upStrum: Boolean = false,
        velocity: Float = 1.0f
    ) {
        val strokeType = if (upStrum) StrokeType.UP else StrokeType.DOWN
        playStroke(frets, durationMs, strokeType, velocity)
    }

    fun playStroke(
        frets: List<Int?>,
        durationMs: Int = 1200,
        strokeType: StrokeType = StrokeType.DOWN,
        velocity: Float = 1.0f
    ) {
        val newSamples = generateGuitarChord(frets, durationMs, strokeType, velocity) ?: return

        synchronized(lock) {
            if (!running) {
                currentBuffer = newSamples
                currentPos = 0
                startStream()
            } else {
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

    private fun generateGuitarChord(
        frets: List<Int?>,
        durationMs: Int,
        strokeType: StrokeType,
        velocity: Float
    ): FloatArray? {
        data class StringInfo(val freq: Double, val stringIndex: Int, val strumOffset: Int)

        val activeStrings = mutableListOf<Pair<Int, Double>>()
        for (s in 0 until 6) {
            val fret = frets.getOrNull(s) ?: continue
            if (fret < 0) continue
            val freq = STANDARD_TUNING_HZ[s] * Math.pow(2.0, fret / 12.0)
            activeStrings.add(s to freq)
        }
        if (activeStrings.isEmpty()) return null

        val ordered = if (strokeType == StrokeType.UP) activeStrings.reversed() else activeStrings
        // Velocity affects strum speed: soft = wider delays, hard = tighter
        val delayScale = when {
            velocity < 0.4f -> 1.6f
            velocity < 0.6f -> 1.3f
            velocity > 0.9f -> 0.85f
            else -> 1.0f
        }
        val strings = ordered.mapIndexed { strIdx, (s, freq) ->
            val baseOffset = STRUM_DELAYS.getOrElse(strIdx) { strIdx * 150 }
            StringInfo(freq, s, (baseOffset * delayScale).toInt())
        }

        val isMute = strokeType == StrokeType.MUTE
        val effectiveDuration = if (isMute) (durationMs / 4).coerceIn(50, 200) else durationMs

        val totalOffset = if (strings.isNotEmpty()) strings.maxOf { it.strumOffset } else 0
        val numSamples = SAMPLE_RATE * effectiveDuration / 1000 + totalOffset
        val output = FloatArray(numSamples)

        for (str in strings) {
            karplusStrongEnhanced(
                output, str.freq, numSamples - str.strumOffset, str.strumOffset,
                str.stringIndex, velocity, isMute
            )
        }

        applyBodyResonance(output, velocity)

        // Attack envelope — harder velocity = sharper attack
        val attackMs = if (velocity > 0.8f) 0.002 else 0.005
        val attackSamples = (SAMPLE_RATE * attackMs).toInt()
        for (i in 0 until attackSamples.coerceAtMost(output.size)) {
            output[i] *= i.toFloat() / attackSamples
        }

        // Mute: fast exponential decay
        if (isMute) {
            val muteDecaySamples = (SAMPLE_RATE * 0.03).toInt()
            for (i in output.indices) {
                if (i > muteDecaySamples) {
                    val decay = kotlin.math.exp(-(i - muteDecaySamples).toFloat() / (SAMPLE_RATE * 0.02f))
                    output[i] *= decay
                }
            }
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
        output: FloatArray, freq: Double, length: Int, offset: Int,
        stringIndex: Int, velocity: Float, isMute: Boolean
    ) {
        val period = (SAMPLE_RATE / freq).toInt().coerceAtLeast(2)
        val ring = FloatArray(period)

        val random = java.util.Random((freq * 1000).toLong())
        // Velocity-dependent brightness: harder = brighter (fewer smoothing passes)
        val baseBrightness = when (stringIndex) {
            0, 1 -> 3; 2, 3 -> 2; else -> 1
        }
        val brightnessPassCount = when {
            velocity > 0.85f -> (baseBrightness - 1).coerceAtLeast(0)
            velocity < 0.5f -> baseBrightness + 1
            else -> baseBrightness
        }

        // Muted strings: much more dampened
        val muteExtra = if (isMute) 2 else 0

        for (i in ring.indices) {
            ring[i] = (random.nextFloat() * 2f - 1f) * 0.85f
        }
        for (pass in 0 until brightnessPassCount + muteExtra) {
            for (i in 1 until ring.size) {
                ring[i] = ring[i] * 0.5f + ring[i - 1] * 0.5f
            }
        }

        // Velocity-dependent decay: muted = very fast decay, soft = slightly faster
        val baseDecay = when (stringIndex) {
            0 -> 0.9985f; 1 -> 0.9982f; 2 -> 0.9978f
            3 -> 0.9975f; 4 -> 0.9970f; else -> 0.9965f
        }
        val decay = when {
            isMute -> baseDecay - 0.01f
            velocity < 0.5f -> baseDecay - 0.001f
            else -> baseDecay
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

    private fun applyBodyResonance(output: FloatArray, velocity: Float) {
        // More body resonance at lower velocities (softer, warmer tone)
        val bodyMix = if (velocity < 0.5f) 0.22f else 0.15f
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
