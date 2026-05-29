package com.caminerin.guitartrainer.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.BufferedInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Realistic guitar strum engine.
 *
 * Uses the 156 real-guitar WAV samples (6 strings × 13 frets × 2 velocities)
 * loaded into memory as raw PCM. Each strum() call pre-renders a mixed buffer
 * (all strings with staggered timing, velocity curves, humanisation) and plays
 * it on a static-mode AudioTrack — simple, robust, low-latency.
 */
object StrumEngine {

    private const val SAMPLE_RATE = 22050
    private const val MAX_FRET_SAMPLE = 12
    private const val NUM_STRINGS = 6

    enum class Direction { DOWN, UP, MUTE, DEAD, REST }

    private val samples = mutableMapOf<String, ShortArray>()
    @Volatile private var samplesLoaded = false
    private val rng = java.util.Random()

    // Pool of active AudioTracks — old strums fade naturally while new ones start
    private val activeTracks = mutableListOf<AudioTrack>()
    private const val MAX_ACTIVE_TRACKS = 4

    private val audioAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val audioFormat = AudioFormat.Builder()
        .setSampleRate(SAMPLE_RATE)
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .build()

    /* ================================================================
     *  PUBLIC API
     * ============================================================= */

    fun init(context: Context) {
        if (samplesLoaded) return
        loadSamples(context)
        samplesLoaded = true
    }

    fun strum(
        frets: List<Int?>,
        direction: Direction,
        velocity: Float = 0.8f,
        durationMs: Int = 1500,
        muteGapMs: Int = 0
    ) {
        if (!samplesLoaded || direction == Direction.REST) return

        val rendered = when (direction) {
            Direction.MUTE -> renderPalmMute(frets, velocity)
            Direction.DEAD -> renderDeadStroke(frets, velocity)
            else -> renderStrum(frets, direction, velocity, durationMs)
        }

        if (rendered.isEmpty()) return

        // Evict oldest tracks if we're at capacity
        pruneOldTracks()

        playBuffer(rendered)
    }

    fun mute() { stopAll() }

    fun stop() { stopAll() }

    fun release() {
        stopAll()
        samples.clear()
        samplesLoaded = false
    }

    /* ================================================================
     *  PLAYBACK — overlapping static AudioTracks
     * ============================================================= */

    private fun playBuffer(buf: ShortArray) {
        try {
            val bufBytes = buf.size * 2
            val track = AudioTrack.Builder()
                .setAudioAttributes(audioAttrs)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(buf, 0, buf.size)
            track.setNotificationMarkerPosition(buf.size)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack?) {
                    try { t?.stop(); t?.release() } catch (_: Exception) {}
                    synchronized(activeTracks) { activeTracks.remove(t) }
                }
                override fun onPeriodicNotification(t: AudioTrack?) {}
            })
            synchronized(activeTracks) { activeTracks.add(track) }
            track.play()
        } catch (_: Exception) {}
    }

    private fun pruneOldTracks() {
        synchronized(activeTracks) {
            // Remove already-finished tracks
            activeTracks.removeAll { t ->
                try {
                    t.playState != AudioTrack.PLAYSTATE_PLAYING
                } catch (_: Exception) { true }
            }
            // If still too many, stop the oldest ones
            while (activeTracks.size >= MAX_ACTIVE_TRACKS) {
                val oldest = activeTracks.removeFirstOrNull() ?: break
                try { oldest.stop(); oldest.release() } catch (_: Exception) {}
            }
        }
    }

    private fun stopAll() {
        synchronized(activeTracks) {
            for (t in activeTracks) {
                try { t.stop(); t.release() } catch (_: Exception) {}
            }
            activeTracks.clear()
        }
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

        data class StringHit(val stringIdx: Int, val fret: Int, val volume: Float)

        val hits = mutableListOf<StringHit>()
        for (s in 0 until NUM_STRINGS) {
            val fret = frets.getOrNull(s) ?: continue
            if (fret < 0) continue
            hits.add(StringHit(s, fret, vel))
        }
        if (hits.isEmpty()) return ShortArray(0)

        // Strum order: down = low-to-high, up = high-to-low
        val ordered = if (direction == Direction.UP) hits.reversed() else hits

        // Per-string velocity curve
        val curved = ordered.mapIndexed { strumPos, hit ->
            hit.copy(volume = hit.volume * velocityCurve(strumPos, ordered.size))
        }

        // Strum speed varies with velocity
        val totalStrumMs = when {
            vel > 0.85f -> 18f + rng.nextFloat() * 8f
            vel > 0.6f  -> 30f + rng.nextFloat() * 15f
            vel > 0.35f -> 45f + rng.nextFloat() * 20f
            else        -> 60f + rng.nextFloat() * 25f
        }
        val perStringMs = totalStrumMs / curved.size.coerceAtLeast(1)

        // Output buffer size
        val maxSampleLen = (SAMPLE_RATE * durationMs / 1000).coerceAtMost(
            samples.values.firstOrNull()?.size ?: (SAMPLE_RATE * 3 / 2)
        )
        val strumDelaySamples = (totalStrumMs * SAMPLE_RATE / 1000).toInt()
        val outputLen = strumDelaySamples + maxSampleLen
        val mix = FloatArray(outputLen)

        for ((strumPos, hit) in curved.withIndex()) {
            val idealDelay = (strumPos * perStringMs * SAMPLE_RATE / 1000).toInt()
            val jitter = (rng.nextGaussian() * 1.2).toInt().coerceIn(-3, 3)
            val delay = (idealDelay + jitter).coerceAtLeast(0)

            val sample = getSample(hit.stringIdx, hit.fret, hit.volume)
            val useLen = maxSampleLen.coerceAtMost(sample.size)

            for (i in 0 until useLen) {
                val outIdx = delay + i
                if (outIdx < mix.size) {
                    mix[outIdx] += (sample[i].toFloat() / 32768f) * hit.volume
                }
            }
        }

        applyDecay(mix, vel)
        return normaliseToShort(mix, vel)
    }

    private fun renderPalmMute(frets: List<Int?>, velocity: Float): ShortArray {
        val raw = renderStrum(frets, Direction.DOWN, velocity, 300)
        if (raw.isEmpty()) return raw
        val decaySamples = (SAMPLE_RATE * 0.08).toInt()
        for (i in raw.indices) {
            if (i > decaySamples) {
                val fade = Math.exp(-(i - decaySamples).toDouble() / (SAMPLE_RATE * 0.04)).toFloat()
                raw[i] = (raw[i] * fade).toInt().coerceIn(-32768, 32767).toShort()
            }
        }
        var prev = 0f
        for (i in raw.indices) {
            val current = raw[i].toFloat()
            val filtered = prev + 0.4f * (current - prev)
            prev = filtered
            raw[i] = filtered.toInt().coerceIn(-32768, 32767).toShort()
        }
        return raw
    }

    private fun renderDeadStroke(frets: List<Int?>, velocity: Float): ShortArray {
        val vel = velocity.coerceIn(0.3f, 1.0f)
        val len = (SAMPLE_RATE * 0.04).toInt()
        val buf = ShortArray(len)
        val amp = (vel * 12000).toInt()
        for (i in buf.indices) {
            val noise = (rng.nextFloat() * 2f - 1f) * amp
            val env = if (i < len / 4) i.toFloat() / (len / 4)
                      else 1f - (i - len / 4).toFloat() / (len * 3 / 4)
            buf[i] = (noise * env.coerceAtLeast(0f)).toInt().coerceIn(-32768, 32767).toShort()
        }
        var prev: Short = 0
        for (i in 1 until buf.size) {
            buf[i] = ((buf[i] * 0.35f + prev * 0.65f).toInt()).coerceIn(-32768, 32767).toShort()
            prev = buf[i]
        }
        return buf
    }

    /* ================================================================
     *  HELPERS
     * ============================================================= */

    private fun velocityCurve(strumPos: Int, total: Int): Float {
        if (total <= 1) return 1f
        val t = strumPos.toFloat() / (total - 1)
        val base = 1f - 0.20f * t
        val jitter = 1f + (rng.nextFloat() - 0.5f) * 0.08f
        return (base * jitter).coerceIn(0.4f, 1.1f)
    }

    private fun getSample(stringIdx: Int, fret: Int, velocity: Float): ShortArray {
        val velLayer = if (velocity > 0.5f) "hard" else "soft"
        val clampedFret = fret.coerceIn(0, MAX_FRET_SAMPLE)
        val key = "s${stringIdx + 1}_f${String.format("%02d", clampedFret)}_$velLayer"
        val raw = samples[key]

        if (raw == null) {
            val altLayer = if (velLayer == "hard") "soft" else "hard"
            val altKey = "s${stringIdx + 1}_f${String.format("%02d", clampedFret)}_$altLayer"
            val alt = samples[altKey] ?: return ShortArray(0)
            return if (fret > MAX_FRET_SAMPLE) pitchShift(alt, fret - MAX_FRET_SAMPLE) else alt
        }
        return if (fret > MAX_FRET_SAMPLE) pitchShift(raw, fret - MAX_FRET_SAMPLE) else raw
    }

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

    private fun applyDecay(mix: FloatArray, velocity: Float) {
        val sustainFraction = if (velocity > 0.7f) 0.7f else 0.5f
        val sustainEnd = (mix.size * sustainFraction).toInt()
        val decayLen = mix.size - sustainEnd
        for (i in sustainEnd until mix.size) {
            val t = (i - sustainEnd).toFloat() / decayLen
            mix[i] *= Math.exp(-3.0 * t).toFloat()
        }
    }

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
        val assetManager = context.assets
        val files = try { assetManager.list("samples") ?: emptyArray() } catch (_: Exception) { emptyArray() }
        for (file in files) {
            if (!file.endsWith(".wav")) continue
            val key = file.removeSuffix(".wav")
            try {
                val stream = BufferedInputStream(assetManager.open("samples/$file"))
                val bytes = stream.readBytes()
                stream.close()
                val pcm = extractPcm16(bytes) ?: continue
                samples[key] = pcm
            } catch (_: Exception) {}
        }
    }

    private fun extractPcm16(wav: ByteArray): ShortArray? {
        if (wav.size < 44) return null
        var dataOffset = 12
        while (dataOffset < wav.size - 8) {
            val chunkId = String(wav, dataOffset, 4, Charsets.US_ASCII)
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
            if (chunkSize % 2 != 0) dataOffset++
        }
        return null
    }
}
