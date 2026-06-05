package com.caminerin.guitartrainer.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.BufferedInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Sample-based guitar synthesizer for riff playback.
 * Uses real guitar WAV samples (assets/samples/) with DSP-based technique effects
 * (bend, tremolo, palm mute, hammer-on, pull-off, staccato, sustain).
 * Falls back to Karplus-Strong synthesis when samples are unavailable.
 */
object RiffSynth {
    private const val SAMPLE_RATE = 44100
    private const val SAMPLE_FILE_RATE = 22050
    private const val CROSSFADE_SAMPLES = 2205

    private val STANDARD_TUNING_HZ = doubleArrayOf(
        329.63, 246.94, 196.00, 146.83, 110.00, 82.41
    )
    private val IS_WOUND = booleanArrayOf(false, false, false, true, true, true)
    private val BODY_MODES = doubleArrayOf(98.0, 204.0, 390.0)

    private var streamTrack: AudioTrack? = null
    @Volatile private var running = false
    private var writerThread: Thread? = null

    @Volatile private var currentBuffer: FloatArray? = null
    @Volatile private var currentPos = 0
    @Volatile private var pendingSwap: FloatArray? = null

    private val lock = Object()

    // Sample cache: "s{1-6}_f{00-12}_{soft|hard}" -> FloatArray at SAMPLE_RATE (upsampled)
    private val sampleCache = mutableMapOf<String, FloatArray>()
    @Volatile private var samplesLoaded = false

    data class NoteEvent(
        val string: Int,
        val fret: Int,
        val startMs: Long,
        val durationMs: Int,
        val technique: String = ""
    )

    @Volatile private var initContext: Context? = null

    /**
     * Register the context for lazy sample loading.
     * Samples are loaded on-demand when first needed by playSequence().
     */
    fun init(context: Context) {
        initContext = context.applicationContext
    }

    /** Load a specific sample on demand (if needed in the future). */
    private fun loadSampleIfNeeded(key: String): FloatArray? {
        sampleCache[key]?.let { return it }
        val ctx = initContext ?: return null
        return try {
            val pcm = loadWavAsFloat(ctx.assets.open("samples/$key.wav"))
            val upsampled = upsample2x(pcm)
            sampleCache[key] = upsampled
            upsampled
        } catch (e: Exception) { android.util.Log.w("RiffSynth", "Failed to load sample on demand", e); null }
    }

    private fun loadWavAsFloat(inputStream: java.io.InputStream): FloatArray {
        val bis = BufferedInputStream(inputStream)
        val bytes = bis.readBytes()
        bis.close()
        // Find "data" chunk — skip WAV header
        var dataOffset = -1
        for (i in 0 until bytes.size - 4) {
            if (bytes[i] == 'd'.code.toByte() && bytes[i + 1] == 'a'.code.toByte() &&
                bytes[i + 2] == 't'.code.toByte() && bytes[i + 3] == 'a'.code.toByte()
            ) {
                dataOffset = i + 8 // skip "data" + 4-byte size
                break
            }
        }
        if (dataOffset < 0) return FloatArray(0)

        val numSamples = (bytes.size - dataOffset) / 2 // 16-bit = 2 bytes per sample
        val result = FloatArray(numSamples)
        val buf = ByteBuffer.wrap(bytes, dataOffset, numSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until numSamples) {
            result[i] = buf.getShort().toFloat() / 32768f
        }
        return result
    }

    private fun upsample2x(input: FloatArray): FloatArray {
        if (input.isEmpty()) return input
        val output = FloatArray(input.size * 2)
        for (i in input.indices) {
            output[i * 2] = input[i]
            val next = if (i + 1 < input.size) input[i + 1] else input[i]
            output[i * 2 + 1] = (input[i] + next) * 0.5f
        }
        return output
    }

    fun playSequence(notes: List<NoteEvent>, soundPreset: String = "clean") {
        if (notes.isEmpty()) return
        val totalMs = notes.maxOf { it.startMs + it.durationMs } + 50
        val totalSamples = (SAMPLE_RATE * totalMs / 1000).toInt()
        val buffer = FloatArray(totalSamples)

        for (note in notes) {
            val offsetSamples = (SAMPLE_RATE * note.startMs / 1000).toInt()
            val durSamples = (SAMPLE_RATE * note.durationMs / 1000).toInt()

            // Prefer real guitar samples; fall back to Karplus-Strong synthesis
            if (!renderSampleNoteIfAvailable(output = buffer, note = note, offset = offsetSamples, duration = durSamples, soundPreset = soundPreset)) {
                val freq = noteFrequency(note.string, note.fret)
                synthesizeNote(buffer, freq, note.string, offsetSamples, durSamples, soundPreset, note.technique)
            }
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

    /**
     * Render a note using real guitar samples with DSP-based technique effects.
     * For frets > 12, pitch-shifts the fret 12 sample by adjusting the read rate.
     */
    private fun renderSampleNoteIfAvailable(
        output: FloatArray,
        note: NoteEvent,
        offset: Int,
        duration: Int,
        soundPreset: String
    ): Boolean {
        val technique = note.technique
        val isPalmMute = technique == "palm_mute"
        val isTremolo = technique == "tremolo"
        val isBend = technique == "bend"
        val isLegato = technique == "hammer_on" || technique == "pull_off" || technique == "pull"
        val isStaccato = technique == "staccato"
        val isSustain = technique == "sustain"

        val effectiveDuration = when {
            isStaccato -> (duration * 0.4).toInt().coerceAtLeast(SAMPLE_RATE / 20)
            else -> duration
        }

        // Select sample: use soft velocity for legato, hard otherwise
        val velLayer = if (isLegato) "soft" else "hard"
        val sampleFret = note.fret.coerceAtMost(12)
        val key = "s${note.string}_f${String.format("%02d", sampleFret)}_$velLayer"
        val sampleData = loadSampleIfNeeded(key)
            ?: loadSampleIfNeeded("s${note.string}_f${String.format("%02d", sampleFret)}_hard")
            ?: return false

        // Playback rate: 1.0 for frets 0-12, pitch-shift up for frets > 12
        val baseRate = if (note.fret > 12) {
            Math.pow(2.0, (note.fret - 12).toDouble() / 12.0)
        } else 1.0

        // Bend: glide from base pitch to +2 semitones
        val bendRateTarget = if (isBend) baseRate * Math.pow(2.0, 2.0 / 12.0) else baseRate
        val bendStartFrac = 0.08
        val bendEndFrac = 0.45

        // Tremolo parameters
        val tremoloRate = 7.0 * 2.0 * Math.PI / SAMPLE_RATE
        val tremoloDepth = 0.55f

        // Decay envelope for natural note ending
        val decayRate = when {
            isPalmMute -> 4.0f / SAMPLE_RATE
            isStaccato -> 3.0f / SAMPLE_RATE
            isSustain -> 0.8f / SAMPLE_RATE
            else -> 1.5f / SAMPLE_RATE
        }

        // Attack envelope
        val attackSamples = when {
            isLegato -> (SAMPLE_RATE * 0.008).toInt()
            isPalmMute -> (SAMPLE_RATE * 0.002).toInt()
            else -> (SAMPLE_RATE * 0.001).toInt()
        }

        val len = effectiveDuration.coerceAtMost(output.size - offset)
        var sampleIdx = 0.0 // fractional index into sampleData

        // Palm mute low-pass filter state
        var lpfState = 0f
        val palmMuteLpf = if (isPalmMute) 0.3f else 1.0f

        for (i in 0 until len) {
            // Calculate current playback rate (for pitch shifting and bends)
            val currentRate = if (isBend) {
                val progress = i.toDouble() / len
                when {
                    progress < bendStartFrac -> baseRate
                    progress < bendEndFrac -> {
                        val bendProgress = (progress - bendStartFrac) / (bendEndFrac - bendStartFrac)
                        val smooth = 0.5 - 0.5 * Math.cos(Math.PI * bendProgress)
                        baseRate + (bendRateTarget - baseRate) * smooth
                    }
                    else -> bendRateTarget
                }
            } else baseRate

            // Read sample with linear interpolation; loop sustain tail for long notes
            val idx = sampleIdx.toInt()
            val loopStart = (sampleData.size * 2 / 3).coerceAtLeast(1)
            val loopLen = sampleData.size - loopStart
            val effectiveIdx = if (idx < sampleData.size) idx
                else loopStart + ((idx - sampleData.size) % loopLen)
            val sample = if (effectiveIdx < sampleData.size - 1) {
                val frac = (sampleIdx - sampleIdx.toInt()).toFloat()
                sampleData[effectiveIdx] * (1f - frac) + sampleData[effectiveIdx + 1] * frac
            } else if (effectiveIdx < sampleData.size) {
                sampleData[effectiveIdx]
            } else {
                0f
            }

            sampleIdx += currentRate

            // Apply palm mute low-pass
            var processed = if (isPalmMute) {
                lpfState = lpfState + palmMuteLpf * (sample - lpfState)
                lpfState
            } else {
                sample
            }

            // Attack envelope
            if (i < attackSamples) {
                val t = i.toFloat() / attackSamples
                processed *= t * t
            }

            // Decay envelope — gentle amplitude reduction over note duration
            val decayEnv = Math.exp((-decayRate * i).toDouble()).toFloat()
            processed *= decayEnv

            // Release envelope
            val releaseRatio = if (isStaccato) 0.75f else 0.93f
            val fadeOutStart = (len * releaseRatio).toInt()
            if (i > fadeOutStart && len > fadeOutStart) {
                val t = (i - fadeOutStart).toFloat() / (len - fadeOutStart)
                processed *= (1f - t) * (1f - t)
            }

            // Tremolo: amplitude modulation
            if (isTremolo) {
                processed *= 1f - tremoloDepth * (0.5f + 0.5f * kotlin.math.cos(tremoloRate * i).toFloat())
            }

            val outIdx = offset + i
            if (outIdx < output.size) {
                output[outIdx] += processed
            }
        }
        return true
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

    fun release() {
        stop()
        sampleCache.clear()
        samplesLoaded = false
    }

    private fun noteFrequency(string: Int, fret: Int): Double {
        val openFreq = STANDARD_TUNING_HZ.getOrElse(string - 1) { 329.63 }
        return openFreq * Math.pow(2.0, fret / 12.0)
    }

    // --- Karplus-Strong synthesis (fallback when no sample is available) ---

    private fun synthesizeNote(
        output: FloatArray, freq: Double, string: Int,
        offset: Int, duration: Int, soundPreset: String, technique: String = ""
    ) {
        val isPalmMute = technique == "palm_mute"
        val isTremolo = technique == "tremolo"
        val isBend = technique == "bend"
        val isLegato = technique == "hammer_on" || technique == "pull_off" || technique == "pull"
        val isStaccato = technique == "staccato"
        val isSustain = technique == "sustain"

        val effectiveDuration = when {
            isStaccato -> (duration * 0.4).toInt().coerceAtLeast(SAMPLE_RATE / 20)
            else -> duration
        }

        val period = (SAMPLE_RATE / freq).toInt().coerceAtLeast(2)
        val ring = FloatArray(period)
        val random = java.util.Random((freq * 1000).toLong())
        val stringIdx = (string - 1).coerceIn(0, 5)
        val wound = IS_WOUND[stringIdx]

        // Guitar-like harmonic spectrum: stronger fundamental, faster harmonic roll-off
        // Wound strings have fewer bright harmonics; plain strings are brighter
        val numHarmonics = if (wound) 10 else 14
        val harmonicDecay = if (wound) 0.55 else 0.65
        for (i in ring.indices) {
            var sample = 0.0
            val phase = 2.0 * Math.PI * i / period
            for (h in 1..numHarmonics) {
                // Stronger fundamental, steeper drop for natural guitar timbre
                val amplitude = Math.pow(harmonicDecay, (h - 1).toDouble())
                val evenSuppression = if (wound && h % 2 == 0) 0.4 else 1.0
                // Slight inharmonicity for realism (strings are slightly stiff)
                val inharmonicity = 1.0 + 0.0001 * h * h
                sample += amplitude * evenSuppression * Math.sin(h * inharmonicity * phase)
            }
            // Less noise for cleaner guitar tone
            val noiseAmount = if (isLegato) 0.03f else if (isPalmMute) 0.12f else 0.06f
            sample += (random.nextFloat() * 2f - 1f) * noiseAmount
            ring[i] = sample.toFloat()
        }

        val ringPeak = ring.maxOfOrNull { kotlin.math.abs(it) } ?: 1f
        if (ringPeak > 0.01f) {
            val ringScale = 0.85f / ringPeak
            for (i in ring.indices) ring[i] *= ringScale
        }

        // Low-pass smoothing passes: more passes = warmer/darker tone
        val brightnessPassCount = when {
            isPalmMute -> when (string) { 1, 2 -> 6; 3, 4 -> 5; else -> 4 }
            soundPreset.contains("distorsion") || soundPreset.contains("fuzz") -> when (string) {
                1, 2 -> 1; 3, 4 -> 1; else -> 0
            }
            soundPreset.contains("crunch") -> when (string) {
                1, 2 -> 2; 3, 4 -> 1; else -> 1
            }
            soundPreset.contains("acoustic") || soundPreset.contains("clean") -> when (string) {
                1, 2 -> 1; 3 -> 1; else -> 0
            }
            else -> when (string) { 1, 2 -> 2; 3, 4 -> 1; else -> 0 }
        }
        for (pass in 0 until brightnessPassCount) {
            for (i in 1 until ring.size) {
                ring[i] = ring[i] * 0.5f + ring[i - 1] * 0.5f
            }
        }

        // Longer sustain for clean/acoustic — guitar strings ring longer without distortion
        val cleanBoost = if (soundPreset.contains("clean") || soundPreset.contains("acoustic")) 1.0004f else 1f
        val baseDecay = when (string) {
            1 -> 0.9994f * cleanBoost; 2 -> 0.9992f * cleanBoost; 3 -> 0.9990f * cleanBoost
            4 -> 0.9987f * cleanBoost; 5 -> 0.9984f * cleanBoost; else -> 0.9980f * cleanBoost
        }
        val decay = when {
            isPalmMute -> baseDecay * 0.9965f
            isStaccato -> baseDecay * 0.998f
            isSustain -> baseDecay * 1.0003f
            else -> baseDecay
        }

        // Blend factor: higher = warmer tone (more low-pass in feedback loop)
        val blend = when {
            isPalmMute -> when (string) { 1, 2 -> 0.48f; 3, 4 -> 0.50f; else -> 0.52f }
            wound -> when (string) { 4 -> 0.49f; 5 -> 0.51f; else -> 0.53f }
            else -> when (string) { 1 -> 0.42f; 2 -> 0.44f; else -> 0.46f }
        }

        // Pick attack transient — shorter and subtler for clean guitar
        val pickTransientSamples = if (isLegato) 0 else (SAMPLE_RATE * 0.004).toInt()
        val pickTransientAmplitude = when {
            isPalmMute -> 0.25f
            soundPreset.contains("acoustic") -> 0.35f
            soundPreset.contains("clean") -> 0.28f
            soundPreset.contains("distorsion") || soundPreset.contains("fuzz") -> 0.25f
            else -> 0.35f
        }

        val attackSamples = when {
            isLegato -> (period * 5).coerceAtMost(effectiveDuration)
            isPalmMute -> (period * 2).coerceAtMost(effectiveDuration)
            else -> (SAMPLE_RATE * 0.002).toInt().coerceAtMost(effectiveDuration)
        }

        var readIdx = 0
        val len = effectiveDuration.coerceAtMost(output.size - offset)

        val bendTargetFreq = if (isBend) freq * Math.pow(2.0, 2.0 / 12.0) else freq
        val bendStartSample = if (isBend) (len * 0.08).toInt() else len
        val bendEndSample = if (isBend) (len * 0.45).toInt() else len

        val tremoloRate = 7.0 * 2.0 * Math.PI / SAMPLE_RATE
        val tremoloDepth = 0.55f

        var currentPeriod = period
        var fractionalIdx = 0.0

        val fastDecayEnd = (len * 0.15).toInt()
        val fastDecayFactor = 0.9997f

        for (i in 0 until len) {
            if (isBend && i in bendStartSample..bendEndSample) {
                val bendProgress = (i - bendStartSample).toDouble() / (bendEndSample - bendStartSample).coerceAtLeast(1)
                val smoothProgress = 0.5 - 0.5 * Math.cos(Math.PI * bendProgress)
                val currentFreq = freq + (bendTargetFreq - freq) * smoothProgress
                currentPeriod = (SAMPLE_RATE / currentFreq).toInt().coerceAtLeast(2)
            }

            val sample = ring[readIdx % ring.size]
            val nextIdx = (readIdx + 1) % ring.size

            val currentDecay = if (i < fastDecayEnd) decay * fastDecayFactor else decay
            ring[readIdx % ring.size] = (sample * blend + ring[nextIdx] * (1f - blend)) * currentDecay

            if (isBend && currentPeriod != period) {
                fractionalIdx += period.toDouble() / currentPeriod
                readIdx = (fractionalIdx.toInt()) % ring.size
            } else {
                readIdx = (readIdx + 1) % ring.size
            }

            val outIdx = offset + i
            if (outIdx < output.size) {
                val attack = if (i < attackSamples) {
                    val t = i.toFloat() / attackSamples
                    t * t
                } else 1f

                val pickTransient = if (i < pickTransientSamples && pickTransientSamples > 0) {
                    val t = i.toFloat() / pickTransientSamples
                    val env = (1f - t) * (1f - t)
                    pickTransientAmplitude * env * (random.nextFloat() * 2f - 1f)
                } else 0f

                val releaseRatio = if (isStaccato) 0.75f else 0.93f
                val fadeOutStart = (len * releaseRatio).toInt()
                val release = if (i > fadeOutStart && len > fadeOutStart) {
                    val t = (i - fadeOutStart).toFloat() / (len - fadeOutStart)
                    (1f - t) * (1f - t)
                } else 1f

                val tremoloEnv = if (isTremolo) {
                    1f - tremoloDepth * (0.5f + 0.5f * kotlin.math.cos(tremoloRate * i).toFloat())
                } else 1f

                output[outIdx] += (sample + pickTransient) * attack * release * tremoloEnv
            }
        }
    }

    private fun applyMasterProcessing(output: FloatArray, soundPreset: String) {
        if (soundPreset.contains("distorsion") || soundPreset.contains("fuzz")) {
            val gain = if (soundPreset.contains("fuzz")) 5.0f else 3.0f
            for (i in output.indices) {
                val x = output[i] * gain
                output[i] = if (x >= 0f) {
                    (2f / Math.PI.toFloat()) * kotlin.math.atan(x * 1.2f)
                } else {
                    (2f / Math.PI.toFloat()) * kotlin.math.atan(x * 0.9f)
                }
            }
        } else if (soundPreset.contains("crunch")) {
            val gain = 1.8f
            for (i in output.indices) {
                val x = output[i] * gain
                output[i] = if (x > 0) 1f - kotlin.math.exp(-x) else -(1f - kotlin.math.exp(x))
            }
        }

        // Body resonance — more for acoustic/clean to simulate guitar body
        val bodyMix = when {
            soundPreset.contains("acoustic") -> 0.32f
            soundPreset.contains("clean") -> 0.25f
            soundPreset.contains("surf") -> 0.22f
            else -> 0.08f
        }

        if (bodyMix > 0.01f) {
            val bodyBuffer = FloatArray(output.size)
            for (modeFreq in BODY_MODES) {
                val omega = 2.0 * Math.PI * modeFreq / SAMPLE_RATE
                val cosOmega = Math.cos(omega).toFloat()
                val r = 0.98f
                var y1 = 0f
                var y2 = 0f
                for (i in output.indices) {
                    val y = output[i] + 2f * r * cosOmega * y1 - r * r * y2
                    bodyBuffer[i] += y * 0.33f
                    y2 = y1
                    y1 = y
                }
            }
            for (i in output.indices) {
                output[i] = output[i] * (1f - bodyMix) + bodyBuffer[i] * bodyMix
            }
        }

        val lpfCoeff = when {
            soundPreset.contains("distorsion") || soundPreset.contains("fuzz") -> 0.75f
            soundPreset.contains("crunch") -> 0.80f
            soundPreset.contains("surf") -> 0.70f
            else -> 0.88f
        }
        var lpfPrev = 0f
        for (i in output.indices) {
            lpfPrev = lpfPrev + lpfCoeff * (output[i] - lpfPrev)
            output[i] = output[i] * 0.6f + lpfPrev * 0.4f
        }

        if (soundPreset.contains("surf")) {
            val delaySamples = (SAMPLE_RATE * 0.035).toInt()
            val feedback = 0.4f
            val reverbMix = 0.3f
            val delayLine = FloatArray(delaySamples)
            var delayIdx = 0
            for (i in output.indices) {
                val delayed = delayLine[delayIdx]
                val reverbSample = output[i] + delayed * feedback
                delayLine[delayIdx] = reverbSample
                delayIdx = (delayIdx + 1) % delaySamples
                output[i] = output[i] * (1f - reverbMix) + delayed * reverbMix
            }
        }

        val attackSamples = (SAMPLE_RATE * 0.002).toInt()
        for (i in 0 until attackSamples.coerceAtMost(output.size)) {
            output[i] *= i.toFloat() / attackSamples
        }

        val peak = output.maxOfOrNull { kotlin.math.abs(it) } ?: 1f
        val scale = if (peak > 0.01f) 0.88f / peak else 1f
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
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
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
                } catch (e: Exception) { android.util.Log.w("RiffSynth", "AudioTrack write error", e); break }
            }
            try { track.stop(); track.release() } catch (_: Exception) {}
        }.apply {
            start()
        }
    }
}
