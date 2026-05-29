package com.caminerin.guitartrainer.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.BufferedInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Realistic guitar strum engine using pre-rendered sample mixing.
 *
 * Loads the 156 real-guitar WAV samples (6 strings × 13 frets × 2 velocities)
 * into memory as raw PCM and mixes them in real time through a streaming
 * AudioTrack. Each strum is rendered as a single mixed buffer with per-string
 * timing, velocity curves, humanisation jitter, and crossfade to the previous
 * sound — giving a much more natural result than the SoundPool-based approach.
 */
object StrumEngine {

    /* ── constants ─────────────────────────────────────────────────── */
    private const val SAMPLE_RATE = 22050          // matches the WAV files
    private const val MAX_FRET_SAMPLE = 12         // highest fret with a sample
    private const val CROSSFADE_LEN = 512          // ~23 ms crossfade
    private const val FADE_OUT_LEN = 256           // ~12 ms fade-out on mute
    private const val NUM_STRINGS = 6

    /* ── types ─────────────────────────────────────────────────────── */
    enum class Direction { DOWN, UP, MUTE, DEAD, REST }

    /* ── sample storage ────────────────────────────────────────────── */
    private val samples = mutableMapOf<String, ShortArray>()
    @Volatile private var loaded = false

    /* ── AudioTrack streaming state ────────────────────────────────── */
    private var track: AudioTrack? = null
    @Volatile private var running = false
    private var writerThread: Thread? = null

    @Volatile private var currentBuf: ShortArray? = null
    @Volatile private var bufPos = 0
    @Volatile private var pendingBuf: ShortArray? = null
    private val lock = Object()

    /* ── humanisation RNG ──────────────────────────────────────────── */
    private val rng = java.util.Random()

    /* ================================================================
     *  PUBLIC API
     * ============================================================= */

    /** Call once from Activity with a valid Context. */
    fun init(context: Context) {
        if (loaded) return
        loadSamples(context)
        startStream()
        loaded = true
    }

    /**
     * Play a chord strum.
     *
     * @param frets        6-element list (low-E … high-E); null or <0 = muted string
     * @param direction    strum direction / type
     * @param velocity     overall loudness 0.0–1.0
     * @param durationMs   how long the chord should ring (capped by sample length)
     * @param muteGapMs    silence inserted before the new chord (simulates hand-lift)
     */
    fun strum(
        frets: List<Int?>,
        direction: Direction,
        velocity: Float = 0.8f,
        durationMs: Int = 1500,
        muteGapMs: Int = 25
    ) {
        if (!loaded) return
        if (direction == Direction.REST) return

        val rendered = when (direction) {
            Direction.MUTE -> renderPalmMute(frets, velocity)
            Direction.DEAD -> renderDeadStroke(frets, velocity)
            else -> renderStrum(frets, direction, velocity, durationMs)
        }

        val gapSamples = (SAMPLE_RATE * muteGapMs / 1000).coerceAtLeast(0)
        val buf = ShortArray(gapSamples + rendered.size)
        rendered.copyInto(buf, gapSamples)

        swapBuffer(buf)
    }

    /** Immediately silence everything. */
    fun mute() {
        synchronized(lock) {
            // Quick fade-out of current buffer to avoid click
            val cur = currentBuf
            val pos = bufPos
            if (cur != null && pos < cur.size) {
                val fadeLen = FADE_OUT_LEN.coerceAtMost(cur.size - pos)
                for (i in 0 until fadeLen) {
                    val gain = 1f - i.toFloat() / fadeLen
                    cur[pos + i] = (cur[pos + i] * gain).toInt().toShort()
                }
                // Zero out the rest
                for (i in (pos + fadeLen) until cur.size) cur[i] = 0
            }
            pendingBuf = null
        }
    }

    fun stop() {
        running = false
        synchronized(lock) { currentBuf = null; pendingBuf = null; bufPos = 0 }
        try { writerThread?.join(500) } catch (_: Exception) {}
        try { track?.stop(); track?.release() } catch (_: Exception) {}
        track = null; writerThread = null
    }

    fun release() {
        stop()
        samples.clear()
        loaded = false
    }

    /* ================================================================
     *  STRUM RENDERING
     * ============================================================= */

    private fun renderStrum(
        frets: List<Int?>,
        direction: Direction,
        velocity: Float,
        durationMs: Int
    ): ShortArray {
        val vel = velocity.coerceIn(0.15f, 1.0f)

        // Collect active strings with per-string velocity
        data class StringHit(val idx: Int, val fret: Int, val vol: Float)

        val hits = mutableListOf<StringHit>()
        for (s in 0 until NUM_STRINGS) {
            val fret = frets.getOrNull(s) ?: continue
            if (fret < 0) continue
            hits.add(StringHit(s, fret, vel))
        }
        if (hits.isEmpty()) return ShortArray(0)

        // Order by strum direction
        val ordered = if (direction == Direction.UP) hits.reversed() else hits

        // Apply per-string velocity curve
        val curved = ordered.mapIndexed { strumPos, hit ->
            hit.copy(vol = hit.vol * velocityCurve(strumPos, ordered.size, direction))
        }

        // Strum speed depends on velocity
        val totalStrumMs = when {
            vel > 0.85f -> 18f + rng.nextFloat() * 8f    // aggressive: 18-26 ms
            vel > 0.6f  -> 30f + rng.nextFloat() * 15f   // medium: 30-45 ms
            vel > 0.35f -> 45f + rng.nextFloat() * 20f   // gentle: 45-65 ms
            else        -> 60f + rng.nextFloat() * 25f    // very soft: 60-85 ms
        }
        val perStringMs = totalStrumMs / curved.size.coerceAtLeast(1)

        // Calculate maximum sample length we'll use
        val maxSampleLen = (SAMPLE_RATE * durationMs / 1000).coerceAtMost(
            samples.values.firstOrNull()?.size ?: (SAMPLE_RATE * 3 / 2)
        )
        val totalStrumSamples = (totalStrumMs * SAMPLE_RATE / 1000).toInt()
        val outputLen = totalStrumSamples + maxSampleLen
        val mix = FloatArray(outputLen)

        // Render each string into the mix
        for ((strumPos, hit) in curved.withIndex()) {
            val idealDelay = (strumPos * perStringMs * SAMPLE_RATE / 1000).toInt()
            val jitter = (rng.nextGaussian() * 1.2).toInt().coerceIn(-3, 3)
            val delay = (idealDelay + jitter).coerceAtLeast(0)

            val sample = getSample(hit.idx, hit.fret, hit.vol)
            val useLen = maxSampleLen.coerceAtMost(sample.size)

            for (i in 0 until useLen) {
                val outIdx = delay + i
                if (outIdx < mix.size) {
                    mix[outIdx] += (sample[i].toFloat() / 32768f) * hit.vol
                }
            }
        }

        // Apply natural decay envelope over the tail
        applyDecay(mix, vel)

        return normaliseToShort(mix, vel)
    }

    /** Palm-mute: play the chord with heavy damping — short, thuddy. */
    private fun renderPalmMute(frets: List<Int?>, velocity: Float): ShortArray {
        val raw = renderStrum(frets, Direction.DOWN, velocity, 300)
        // Apply aggressive decay
        val decaySamples = (SAMPLE_RATE * 0.08).toInt()  // 80 ms ring
        for (i in raw.indices) {
            if (i > decaySamples) {
                val fade = Math.exp(-(i - decaySamples).toDouble() / (SAMPLE_RATE * 0.04)).toFloat()
                raw[i] = (raw[i] * fade).toInt().coerceIn(-32768, 32767).toShort()
            }
        }
        // Low-pass filter for muffled tone
        var prev = 0f
        for (i in raw.indices) {
            val cur = raw[i].toFloat()
            val filtered = prev + 0.4f * (cur - prev)
            prev = filtered
            raw[i] = filtered.toInt().coerceIn(-32768, 32767).toShort()
        }
        return raw
    }

    /** Dead stroke: percussive "chk" — muted strings hit for rhythm. */
    private fun renderDeadStroke(frets: List<Int?>, velocity: Float): ShortArray {
        val vel = velocity.coerceIn(0.3f, 1.0f)
        // Very short noise burst simulating muted string slap
        val len = (SAMPLE_RATE * 0.04).toInt()  // 40 ms
        val buf = ShortArray(len)
        val amp = (vel * 12000).toInt()
        for (i in buf.indices) {
            val noise = (rng.nextFloat() * 2f - 1f) * amp
            val env = if (i < len / 4) i.toFloat() / (len / 4) else 1f - (i - len / 4).toFloat() / (len * 3 / 4)
            buf[i] = (noise * env.coerceAtLeast(0f)).toInt().coerceIn(-32768, 32767).toShort()
        }
        // Low-pass for thud
        var prev: Short = 0
        for (i in 1 until buf.size) {
            buf[i] = ((buf[i] * 0.35f + prev * 0.65f).toInt()).coerceIn(-32768, 32767).toShort()
            prev = buf[i]
        }
        return buf
    }

    /* ================================================================
     *  PER-STRING HELPERS
     * ============================================================= */

    /**
     * Velocity curve: models how a pick transfers energy across strings.
     * In a down-strum the bass strings receive more force; in an up-strum
     * the treble strings do.
     */
    private fun velocityCurve(strumPos: Int, total: Int, dir: Direction): Float {
        if (total <= 1) return 1f
        val t = strumPos.toFloat() / (total - 1)  // 0 = first string hit, 1 = last
        // First string hit gets full power, last gets ~75-85%
        val base = 1f - 0.20f * t
        // Add slight random variation per string
        val jitter = 1f + (rng.nextFloat() - 0.5f) * 0.08f
        return (base * jitter).coerceIn(0.4f, 1.1f)
    }

    /**
     * Look up the best available sample for a string/fret/velocity combo.
     * For frets > 12 we pitch-shift the fret-12 sample via linear interpolation.
     */
    private fun getSample(stringIdx: Int, fret: Int, velocity: Float): ShortArray {
        val velLayer = if (velocity > 0.5f) "hard" else "soft"
        val clampedFret = fret.coerceIn(0, MAX_FRET_SAMPLE)
        val key = "s${stringIdx + 1}_f${String.format("%02d", clampedFret)}_$velLayer"
        val raw = samples[key]

        if (raw == null) {
            // Try the other velocity layer as fallback
            val altLayer = if (velLayer == "hard") "soft" else "hard"
            val altKey = "s${stringIdx + 1}_f${String.format("%02d", clampedFret)}_$altLayer"
            val alt = samples[altKey] ?: return ShortArray(0)
            return if (fret > MAX_FRET_SAMPLE) pitchShift(alt, fret - MAX_FRET_SAMPLE) else alt
        }

        return if (fret > MAX_FRET_SAMPLE) pitchShift(raw, fret - MAX_FRET_SAMPLE) else raw
    }

    /** Shift pitch up by `semitones` using linear interpolation resampling. */
    private fun pitchShift(src: ShortArray, semitones: Int): ShortArray {
        val ratio = Math.pow(2.0, semitones / 12.0)
        val newLen = (src.size / ratio).toInt()
        val out = ShortArray(newLen)
        for (i in out.indices) {
            val srcPos = i * ratio
            val idx = srcPos.toInt()
            val frac = (srcPos - idx).toFloat()
            val s0 = if (idx < src.size) src[idx].toFloat() else 0f
            val s1 = if (idx + 1 < src.size) src[idx + 1].toFloat() else s0
            out[i] = (s0 + (s1 - s0) * frac).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    /** Apply a natural-feeling decay envelope. */
    private fun applyDecay(mix: FloatArray, velocity: Float) {
        // Sustain length depends on velocity — harder strums ring longer
        val sustainFraction = if (velocity > 0.7f) 0.7f else 0.5f
        val sustainEnd = (mix.size * sustainFraction).toInt()
        val decayLen = mix.size - sustainEnd

        for (i in sustainEnd until mix.size) {
            val t = (i - sustainEnd).toFloat() / decayLen
            // Exponential decay feels more natural than linear
            val env = Math.exp(-3.0 * t).toFloat()
            mix[i] *= env
        }
    }

    /** Normalise a float mix buffer to 16-bit short, with peak limiting. */
    private fun normaliseToShort(mix: FloatArray, velocity: Float): ShortArray {
        val peak = mix.maxOfOrNull { kotlin.math.abs(it) } ?: 1f
        val targetPeak = 0.82f * velocity.coerceIn(0.3f, 1.0f)
        val scale = if (peak > 0.001f) targetPeak / peak else 1f
        val out = ShortArray(mix.size)
        for (i in mix.indices) {
            out[i] = (mix[i] * scale * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    /* ================================================================
     *  SAMPLE LOADING
     * ============================================================= */

    private fun loadSamples(context: Context) {
        val am = context.assets
        val files = try { am.list("samples") ?: emptyArray() } catch (_: Exception) { emptyArray() }

        for (file in files) {
            if (!file.endsWith(".wav")) continue
            val key = file.removeSuffix(".wav")
            try {
                val stream = BufferedInputStream(am.open("samples/$file"))
                val bytes = stream.readBytes()
                stream.close()
                val pcm = extractPcm16(bytes) ?: continue
                samples[key] = pcm
            } catch (_: Exception) { /* skip */ }
        }
    }

    /** Extract raw 16-bit PCM data from a WAV byte array, skipping the 44-byte header. */
    private fun extractPcm16(wav: ByteArray): ShortArray? {
        if (wav.size < 44) return null
        // Find "data" chunk
        var dataOffset = 12
        while (dataOffset < wav.size - 8) {
            val chunkId = String(wav, dataOffset, 4)
            val chunkSize = ByteBuffer.wrap(wav, dataOffset + 4, 4)
                .order(ByteOrder.LITTLE_ENDIAN).int
            if (chunkId == "data") {
                val pcmStart = dataOffset + 8
                val pcmLen = chunkSize.coerceAtMost(wav.size - pcmStart)
                val shorts = ShortArray(pcmLen / 2)
                ByteBuffer.wrap(wav, pcmStart, pcmLen)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer()
                    .get(shorts)
                return shorts
            }
            dataOffset += 8 + chunkSize
            if (chunkSize % 2 != 0) dataOffset++ // padding byte
        }
        return null
    }

    /* ================================================================
     *  AUDIOTRACK STREAMING
     * ============================================================= */

    private fun startStream() {
        if (running) return

        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val t = AudioTrack.Builder()
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
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(minBuf.coerceAtLeast(8192))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track = t
        running = true
        t.play()

        writerThread = Thread {
            val chunk = ShortArray(512)
            while (running) {
                synchronized(lock) {
                    val swap = pendingBuf
                    if (swap != null) {
                        currentBuf = swap
                        bufPos = 0
                        pendingBuf = null
                    }

                    val cur = currentBuf
                    if (cur == null || bufPos >= cur.size) {
                        chunk.fill(0)
                    } else {
                        for (i in chunk.indices) {
                            val pos = bufPos + i
                            chunk[i] = if (pos < cur.size) cur[pos] else 0
                        }
                        bufPos += chunk.size
                        if (bufPos >= cur.size) currentBuf = null
                    }
                }

                try {
                    t.write(chunk, 0, chunk.size)
                } catch (_: Exception) { break }
            }
            try { t.stop(); t.release() } catch (_: Exception) {}
        }.apply {
            priority = Thread.MAX_PRIORITY
            isDaemon = true
            start()
        }
    }

    /** Crossfade the new buffer with whatever is currently playing, then swap. */
    private fun swapBuffer(newBuf: ShortArray) {
        synchronized(lock) {
            val cur = currentBuf
            val pos = bufPos
            if (cur != null && pos < cur.size) {
                val remaining = cur.size - pos
                val fadeLen = CROSSFADE_LEN.coerceAtMost(remaining).coerceAtMost(newBuf.size)
                for (i in 0 until fadeLen) {
                    val progress = i.toFloat() / fadeLen
                    val fadeOut = 1f - progress
                    val fadeIn = progress
                    val mixed = cur[pos + i] * fadeOut + newBuf[i] * fadeIn
                    newBuf[i] = mixed.toInt().coerceIn(-32768, 32767).toShort()
                }
            }
            pendingBuf = newBuf
        }
    }
}
