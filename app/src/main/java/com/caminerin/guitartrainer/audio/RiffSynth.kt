package com.caminerin.guitartrainer.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Enhanced single-note guitar synthesizer for riff playback.
 * Uses extended Karplus-Strong with harmonic series initialization,
 * wound/plain string modeling, pick transients, two-stage decay,
 * and body resonance for realistic guitar tone.
 */
object RiffSynth {
    private const val SAMPLE_RATE = 44100
    private const val CROSSFADE_SAMPLES = 2205

    private val STANDARD_TUNING_HZ = doubleArrayOf(
        329.63, 246.94, 196.00, 146.83, 110.00, 82.41
    )

    // Wound strings (D,A,E = indices 3,4,5) have more inharmonicity and duller attack
    private val IS_WOUND = booleanArrayOf(false, false, false, true, true, true)

    // Guitar body resonance frequencies (Hz) — typical dreadnought modes
    private val BODY_MODES = doubleArrayOf(98.0, 204.0, 390.0)

    private var streamTrack: AudioTrack? = null
    @Volatile private var running = false
    private var writerThread: Thread? = null

    @Volatile private var currentBuffer: FloatArray? = null
    @Volatile private var currentPos = 0
    @Volatile private var pendingSwap: FloatArray? = null

    private val lock = Object()

    data class NoteEvent(
        val string: Int,
        val fret: Int,
        val startMs: Long,
        val durationMs: Int,
        val technique: String = ""
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
            synthesizeNote(buffer, freq, note.string, offsetSamples, durSamples, soundPreset, note.technique)
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
        offset: Int, duration: Int, soundPreset: String, technique: String = ""
    ) {
        val isPalmMute = technique == "palm_mute"
        val isTremolo = technique == "tremolo"
        val isBend = technique == "bend"
        val isLegato = technique == "hammer_on" || technique == "pull_off"
        val isStaccato = technique == "staccato"

        val effectiveDuration = when {
            isStaccato -> (duration * 0.4).toInt().coerceAtLeast(SAMPLE_RATE / 20)
            else -> duration
        }

        val period = (SAMPLE_RATE / freq).toInt().coerceAtLeast(2)
        val ring = FloatArray(period)
        val random = java.util.Random((freq * 1000).toLong())
        val stringIdx = (string - 1).coerceIn(0, 5)
        val wound = IS_WOUND[stringIdx]

        // --- Harmonic series initialization ---
        // Real strings vibrate with harmonics at integer multiples of fundamental.
        // Wound strings have stronger odd harmonics; plain strings are more even.
        val numHarmonics = if (wound) 12 else 16
        val harmonicDecay = if (wound) 0.7 else 0.85
        for (i in ring.indices) {
            var sample = 0.0
            val phase = 2.0 * Math.PI * i / period
            for (h in 1..numHarmonics) {
                val amplitude = Math.pow(harmonicDecay, (h - 1).toDouble())
                // Wound strings: suppress even harmonics slightly for metallic character
                val evenSuppression = if (wound && h % 2 == 0) 0.6 else 1.0
                sample += amplitude * evenSuppression * Math.sin(h * phase)
            }
            // Add controlled noise for pick excitation
            val noiseAmount = if (isLegato) 0.05f else if (isPalmMute) 0.15f else 0.12f
            sample += (random.nextFloat() * 2f - 1f) * noiseAmount
            ring[i] = sample.toFloat()
        }

        // Normalize ring buffer
        val ringPeak = ring.maxOfOrNull { kotlin.math.abs(it) } ?: 1f
        if (ringPeak > 0.01f) {
            val ringScale = 0.85f / ringPeak
            for (i in ring.indices) ring[i] *= ringScale
        }

        // --- Pre-filtering for brightness ---
        val brightnessPassCount = when {
            isPalmMute -> when (string) { 1, 2 -> 6; 3, 4 -> 5; else -> 4 }
            soundPreset.contains("distorsion") || soundPreset.contains("fuzz") -> when (string) {
                1, 2 -> 1; 3, 4 -> 1; else -> 0
            }
            soundPreset.contains("crunch") -> when (string) {
                1, 2 -> 2; 3, 4 -> 1; else -> 1
            }
            soundPreset.contains("acoustic") -> when (string) {
                1, 2 -> 1; else -> 0
            }
            else -> when (string) { 1, 2 -> 2; 3, 4 -> 1; else -> 0 }
        }
        for (pass in 0 until brightnessPassCount) {
            for (i in 1 until ring.size) {
                ring[i] = ring[i] * 0.5f + ring[i - 1] * 0.5f
            }
        }

        // --- Two-stage decay ---
        // Guitar strings have fast initial decay (energy leaving string) then slow sustain
        val baseDecay = when (string) {
            1 -> 0.9992f; 2 -> 0.9990f; 3 -> 0.9987f
            4 -> 0.9984f; 5 -> 0.9980f; else -> 0.9976f
        }
        val decay = when {
            isPalmMute -> baseDecay * 0.9965f
            isStaccato -> baseDecay * 0.998f
            else -> baseDecay
        }

        // Blend factor (allpass coefficient) — wound strings need more
        val blend = when {
            isPalmMute -> when (string) { 1, 2 -> 0.48f; 3, 4 -> 0.50f; else -> 0.52f }
            wound -> when (string) { 4 -> 0.47f; 5 -> 0.50f; else -> 0.52f }
            else -> when (string) { 1 -> 0.38f; 2 -> 0.40f; else -> 0.43f }
        }

        // --- Pick transient ---
        // Short burst of bright noise at the start simulating pick hitting string
        val pickTransientSamples = if (isLegato) 0 else (SAMPLE_RATE * 0.003).toInt()
        val pickTransientAmplitude = when {
            isPalmMute -> 0.3f
            soundPreset.contains("acoustic") -> 0.5f
            soundPreset.contains("distorsion") || soundPreset.contains("fuzz") -> 0.25f
            else -> 0.4f
        }

        // Legato: smoother attack; normal: sharp pick attack
        val attackSamples = when {
            isLegato -> (period * 5).coerceAtMost(effectiveDuration)
            isPalmMute -> (period * 2).coerceAtMost(effectiveDuration)
            else -> (SAMPLE_RATE * 0.002).toInt().coerceAtMost(effectiveDuration)
        }

        var readIdx = 0
        val len = effectiveDuration.coerceAtMost(output.size - offset)

        // Bend parameters
        val bendTargetFreq = if (isBend) freq * Math.pow(2.0, 2.0 / 12.0) else freq
        val bendStartSample = if (isBend) (len * 0.08).toInt() else len
        val bendEndSample = if (isBend) (len * 0.45).toInt() else len

        // Tremolo parameters
        val tremoloRate = 7.0 * 2.0 * Math.PI / SAMPLE_RATE
        val tremoloDepth = 0.55f

        var currentPeriod = period
        var fractionalIdx = 0.0

        // Two-stage decay: faster in first 15% of note
        val fastDecayEnd = (len * 0.15).toInt()
        val fastDecayFactor = 0.9997f

        for (i in 0 until len) {
            // Bend: interpolate period smoothly
            if (isBend && i in bendStartSample..bendEndSample) {
                val bendProgress = (i - bendStartSample).toDouble() / (bendEndSample - bendStartSample).coerceAtLeast(1)
                // Smooth ease-in-out curve for natural bend feel
                val smoothProgress = 0.5 - 0.5 * Math.cos(Math.PI * bendProgress)
                val currentFreq = freq + (bendTargetFreq - freq) * smoothProgress
                currentPeriod = (SAMPLE_RATE / currentFreq).toInt().coerceAtLeast(2)
            }

            val sample = ring[readIdx % ring.size]
            val nextIdx = (readIdx + 1) % ring.size

            // Apply two-stage decay
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
                // Attack envelope
                val attack = if (i < attackSamples) {
                    val t = i.toFloat() / attackSamples
                    t * t // quadratic for snappier attack
                } else 1f

                // Pick transient: burst of high-freq content at note start
                val pickTransient = if (i < pickTransientSamples && pickTransientSamples > 0) {
                    val t = i.toFloat() / pickTransientSamples
                    val env = (1f - t) * (1f - t) // fast decay
                    pickTransientAmplitude * env * (random.nextFloat() * 2f - 1f)
                } else 0f

                // Release envelope
                val releaseRatio = if (isStaccato) 0.75f else 0.93f
                val fadeOutStart = (len * releaseRatio).toInt()
                val release = if (i > fadeOutStart && len > fadeOutStart) {
                    val t = (i - fadeOutStart).toFloat() / (len - fadeOutStart)
                    (1f - t) * (1f - t) // quadratic fade for smoother end
                } else 1f

                // Tremolo: amplitude modulation
                val tremoloEnv = if (isTremolo) {
                    1f - tremoloDepth * (0.5f + 0.5f * kotlin.math.cos(tremoloRate * i).toFloat())
                } else 1f

                output[outIdx] += (sample + pickTransient) * attack * release * tremoloEnv
            }
        }
    }

    private fun applyMasterProcessing(output: FloatArray, soundPreset: String) {
        // --- Distortion/overdrive ---
        if (soundPreset.contains("distorsion") || soundPreset.contains("fuzz")) {
            val gain = if (soundPreset.contains("fuzz")) 5.0f else 3.0f
            for (i in output.indices) {
                val x = output[i] * gain
                // Asymmetric soft clipping for more natural overdrive
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

        // --- Guitar body resonance ---
        // Simulate resonance at guitar body frequencies using cascaded biquad filters
        val bodyMix = when {
            soundPreset.contains("acoustic") -> 0.28f
            soundPreset.contains("clean") -> 0.18f
            soundPreset.contains("surf") -> 0.22f
            else -> 0.08f
        }

        if (bodyMix > 0.01f) {
            val bodyBuffer = FloatArray(output.size)
            for (modeFreq in BODY_MODES) {
                // Simple resonant filter per body mode
                val omega = 2.0 * Math.PI * modeFreq / SAMPLE_RATE
                val cosOmega = Math.cos(omega).toFloat()
                val r = 0.98f // resonance sharpness
                var y1 = 0f
                var y2 = 0f
                for (i in output.indices) {
                    val y = output[i] + 2f * r * cosOmega * y1 - r * r * y2
                    bodyBuffer[i] += y * 0.33f // scale down since we sum 3 modes
                    y2 = y1
                    y1 = y
                }
            }
            for (i in output.indices) {
                output[i] = output[i] * (1f - bodyMix) + bodyBuffer[i] * bodyMix
            }
        }

        // --- Cabinet/room simulation (low-pass) ---
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

        // --- Surf reverb ---
        if (soundPreset.contains("surf")) {
            val delaySamples = (SAMPLE_RATE * 0.035).toInt() // 35ms spring reverb
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

        // --- Soft master attack (2ms) ---
        val attackSamples = (SAMPLE_RATE * 0.002).toInt()
        for (i in 0 until attackSamples.coerceAtMost(output.size)) {
            output[i] *= i.toFloat() / attackSamples
        }

        // --- Normalize ---
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
