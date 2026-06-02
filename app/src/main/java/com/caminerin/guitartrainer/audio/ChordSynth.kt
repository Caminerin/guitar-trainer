package com.caminerin.guitartrainer.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool

/**
 * Guitar chord synthesizer with dual engine:
 * - SoundPool: plays pre-generated WAV samples for frets 0-12 (realistic tone)
 * - AudioTrack + Karplus-Strong: fallback for frets > 12 or when samples unavailable
 *
 * Samples are per-string, per-fret, per-velocity (soft/hard).
 * Strum is simulated by playing 6 string samples with staggered delays.
 */
object ChordSynth {
    private const val SAMPLE_RATE = 44100
    private const val SAMPLE_FILE_RATE = 22050
    private const val CROSSFADE_SAMPLES = 4410
    private val STANDARD_TUNING_HZ = doubleArrayOf(82.41, 110.0, 146.83, 196.0, 246.94, 329.63)
    private val STRUM_DELAYS_MS = intArrayOf(0, 7, 7, 7, 7, 7) // ~35ms total strum

    // AudioTrack streaming state (fallback engine)
    private var streamTrack: AudioTrack? = null
    @Volatile private var running = false
    private var writerThread: Thread? = null
    @Volatile private var currentBuffer: FloatArray? = null
    @Volatile private var currentPos = 0
    @Volatile private var pendingSwap: FloatArray? = null
    private val lock = Object()

    // SoundPool state (sample engine)
    private var soundPool: SoundPool? = null
    private var samplesLoaded = false
    // Key: "s{1-6}_f{00-12}_{soft|hard}" -> SoundPool ID
    private val sampleIds = mutableMapOf<String, Int>()
    private var loadedCount = 0
    private var totalToLoad = 0

    enum class StrokeType { DOWN, UP, MUTE }

    /**
     * Initialize sample engine. Call once from Activity/Application with context.
     * Loads 156 WAV samples into SoundPool for low-latency playback.
     */
    fun init(context: Context) {
        if (soundPool != null) return

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val pool = SoundPool.Builder()
            .setMaxStreams(12) // up to 12 simultaneous sounds (2 chords overlapping)
            .setAudioAttributes(attrs)
            .build()

        pool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) {
                loadedCount++
                if (loadedCount >= totalToLoad) {
                    samplesLoaded = true
                }
            }
        }

        soundPool = pool

        // Load all samples from assets/samples/
        val assetManager = context.assets
        try {
            val files = assetManager.list("samples") ?: emptyArray()
            totalToLoad = files.size
            for (file in files) {
                if (!file.endsWith(".wav")) continue
                val key = file.removeSuffix(".wav")
                try {
                    val afd = assetManager.openFd("samples/$file")
                    val id = pool.load(afd, 1)
                    sampleIds[key] = id
                    afd.close()
                } catch (_: Exception) {
                    // Skip missing sample
                }
            }
        } catch (_: Exception) {
            // No samples directory — fallback to synthesis
        }
    }

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
        // Try sample-based playback first
        if (samplesLoaded && canUseSamples(frets)) {
            playSampledChord(frets, strokeType, velocity)
            return
        }
        // Fallback to Karplus-Strong synthesis
        playSynthesized(frets, durationMs, strokeType, velocity)
    }

    private fun canUseSamples(frets: List<Int?>): Boolean {
        return frets.all { it == null || it < 0 || it <= 12 }
    }

    private fun playSampledChord(
        frets: List<Int?>,
        strokeType: StrokeType,
        velocity: Float
    ) {
        val pool = soundPool ?: return
        val clampedVel = velocity.coerceIn(0.2f, 1.0f)
        val velLayer = if (clampedVel > 0.6f) "hard" else "soft"
        val isMute = strokeType == StrokeType.MUTE

        // Build ordered string list
        val activeStrings = mutableListOf<Pair<Int, Int>>() // stringIdx (0-5), fret
        for (s in 0 until 6) {
            val fret = frets.getOrNull(s) ?: continue
            if (fret < 0) continue
            activeStrings.add(s to fret)
        }
        if (activeStrings.isEmpty()) return

        // Reverse for upstrum
        val ordered = if (strokeType == StrokeType.UP) activeStrings.reversed() else activeStrings

        // Velocity-dependent strum speed
        val delayScale = when {
            clampedVel < 0.4f -> 1.8f
            clampedVel < 0.6f -> 1.3f
            clampedVel > 0.9f -> 0.7f
            else -> 1.0f
        }

        // Play each string with staggered delay
        val strumThread = Thread {
            for ((strumIdx, pair) in ordered.withIndex()) {
                val (stringIdx, fret) = pair
                val key = "s${stringIdx + 1}_f${String.format("%02d", fret)}_$velLayer"
                val sampleId = sampleIds[key] ?: continue

                // Volume: velocity scaling + slight per-string variation
                val vol = clampedVel.coerceIn(0.3f, 1.0f)
                // Rate: 1.0 = normal pitch. Muted strokes get slightly lower rate for thump
                val rate = if (isMute) 0.95f else 1.0f
                // Priority: first string gets highest priority
                val priority = ordered.size - strumIdx

                pool.play(sampleId, vol, vol, priority, 0, rate)

                // Strum delay between strings
                if (strumIdx < ordered.size - 1) {
                    val delayMs = (STRUM_DELAYS_MS.getOrElse(strumIdx + 1) { 60 } * delayScale).toLong()
                    if (delayMs > 0) {
                        try { Thread.sleep(delayMs) } catch (_: InterruptedException) { break }
                    }
                }
            }

            // For muted strokes: stop all sounds after short duration
            if (isMute) {
                try { Thread.sleep(80) } catch (_: InterruptedException) {}
                pool.autoPause()
                try { Thread.sleep(20) } catch (_: InterruptedException) {}
                pool.autoResume()
            }
        }
        strumThread.priority = Thread.MAX_PRIORITY
        strumThread.start()
    }

    // --- Karplus-Strong fallback (same as before) ---

    private fun playSynthesized(
        frets: List<Int?>,
        durationMs: Int,
        strokeType: StrokeType,
        velocity: Float
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
        // Stop any playing samples
        try { soundPool?.autoPause() } catch (_: Exception) {}
    }

    fun release() {
        stop()
        try {
            soundPool?.release()
        } catch (_: Exception) {}
        soundPool = null
        sampleIds.clear()
        samplesLoaded = false
        loadedCount = 0
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

    private val STRUM_DELAYS_SYNTH = intArrayOf(0, 180, 340, 480, 600, 700)

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
        val delayScale = when {
            velocity < 0.4f -> 1.6f
            velocity < 0.6f -> 1.3f
            velocity > 0.9f -> 0.85f
            else -> 1.0f
        }
        val strings = ordered.mapIndexed { strIdx, (s, freq) ->
            val baseOffset = STRUM_DELAYS_SYNTH.getOrElse(strIdx) { strIdx * 150 }
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

        val attackMs = if (velocity > 0.8f) 0.002 else 0.005
        val attackSamples = (SAMPLE_RATE * attackMs).toInt()
        for (i in 0 until attackSamples.coerceAtMost(output.size)) {
            output[i] *= i.toFloat() / attackSamples
        }

        if (isMute) {
            val muteDecaySamples = (SAMPLE_RATE * 0.03).toInt()
            for (i in output.indices) {
                if (i > muteDecaySamples) {
                    val decay = kotlin.math.exp(-(i - muteDecaySamples).toFloat() / (SAMPLE_RATE * 0.02f))
                    output[i] *= decay
                }
            }
        }

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
        val baseBrightness = when (stringIndex) {
            0, 1 -> 3; 2, 3 -> 2; else -> 1
        }
        val brightnessPassCount = when {
            velocity > 0.85f -> (baseBrightness - 1).coerceAtLeast(0)
            velocity < 0.5f -> baseBrightness + 1
            else -> baseBrightness
        }

        val muteExtra = if (isMute) 2 else 0

        for (i in ring.indices) {
            ring[i] = (random.nextFloat() * 2f - 1f) * 0.85f
        }
        for (pass in 0 until brightnessPassCount + muteExtra) {
            for (i in 1 until ring.size) {
                ring[i] = ring[i] * 0.5f + ring[i - 1] * 0.5f
            }
        }

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
