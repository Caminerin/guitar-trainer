package com.caminerin.guitartrainer.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

enum class DrumStyle(val displayName: String) {
    ROCK("Rock"),
    POP_BALLAD("Pop/Balada"),
    WALTZ("Vals 3/4"),
    REGGAE("Reggae"),
    FUNK("Funk"),
    COUNTRY("Country"),
    BLUES_SHUFFLE("Blues shuffle"),
    BOSSA_NOVA("Bossa nova"),
    METAL("Metal"),
    PUNK("Punk"),
    FOLK("Folk/Acustico"),
    RUMBA_FLAMENCA("Rumba flamenca")
}

enum class DrumHit {
    KICK_HARD, KICK_SOFT,
    SNARE_HARD, SNARE_SOFT, SNARE_CROSSSTICK, SNARE_RIMSHOT,
    HH_CLOSED, HH_OPEN, HH_HALF, HH_PEDAL,
    RIDE_NORMAL, RIDE_BELL,
    CRASH
}

data class DrumEvent(
    val hit: DrumHit,
    val positionInBeat: Float, // 0.0 = on the beat, 0.5 = halfway (8th note)
    val velocity: Float = 1.0f // 0.0..1.0
)

object DrumEngine {
    private const val SAMPLE_RATE = 22050
    /** Write audio in small chunks (~10ms) so stop() is responsive */
    private const val CHUNK_SAMPLES = 220 // ~10ms at 22050
    private var audioTrack: AudioTrack? = null
    /** Round-robin sample banks: each DrumHit has multiple variants */
    private val sampleBanks = mutableMapOf<DrumHit, List<ShortArray>>()
    /** Current round-robin index per hit — rotates on each play */
    private val rrIndex = mutableMapOf<DrumHit, Int>()
    private var initialized = false
    private val lock = Any()
    /** Reference count: tracks how many screens are using DrumEngine */
    private var refCount = 0

    /** Mutex serializes playLoop so only one loop writes to AudioTrack at a time */
    private val playMutex = Mutex()

    @Volatile var isPlaying = false
        private set

    /** Set this to change BPM in real-time while playing */
    @Volatile var liveBpm = 120

    /** Call when a screen starts using DrumEngine. Pairs with [releaseRef]. */
    fun addRef(context: Context) {
        synchronized(lock) {
            refCount++
        }
        init(context)
    }

    /** Call from DisposableEffect. Only truly releases when last screen leaves. */
    fun releaseRef() {
        synchronized(lock) {
            refCount--
            if (refCount <= 0) {
                refCount = 0
                doRelease()
            }
        }
    }

    fun init(context: Context) {
        synchronized(lock) {
            if (initialized) return
            loadSamples(context)
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            try {
                audioTrack = AudioTrack.Builder()
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
                    .setBufferSizeInBytes(minBuf.coerceAtLeast(SAMPLE_RATE * 2))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                audioTrack?.play()
            } catch (e: Exception) { android.util.Log.w("DrumEngine", "AudioTrack init failed", e) }
            initialized = true
        }
    }

    private fun loadSamples(context: Context) {
        // Each DrumHit maps to a base name; files are named {base}_1.wav, {base}_2.wav, {base}_3.wav
        val hitBaseMap = mapOf(
            DrumHit.KICK_HARD to "kick_hard",
            DrumHit.KICK_SOFT to "kick_soft",
            DrumHit.SNARE_HARD to "snare_hard",
            DrumHit.SNARE_SOFT to "snare_soft",
            DrumHit.SNARE_CROSSSTICK to "snare_crossstick",
            DrumHit.SNARE_RIMSHOT to "snare_rimshot",
            DrumHit.HH_CLOSED to "hh_closed",
            DrumHit.HH_OPEN to "hh_open",
            DrumHit.HH_HALF to "hh_half",
            DrumHit.HH_PEDAL to "hh_pedal",
            DrumHit.RIDE_NORMAL to "ride_normal",
            DrumHit.RIDE_BELL to "ride_bell",
            DrumHit.CRASH to "crash"
        )
        for ((hit, base) in hitBaseMap) {
            val variants = mutableListOf<ShortArray>()
            for (rr in 1..4) {
                try {
                    val pcm = readWavPcm(context.assets.open("drums/${base}_${rr}.wav"))
                    if (pcm != null && pcm.isNotEmpty()) variants.add(pcm)
                } catch (_: Exception) { /* no more round-robins for $base */ }
            }
            if (variants.isNotEmpty()) {
                sampleBanks[hit] = variants
                rrIndex[hit] = 0
            }
        }
    }

    /** Get the next round-robin sample for a hit */
    private fun nextSample(hit: DrumHit): ShortArray? {
        val bank = sampleBanks[hit] ?: return null
        val idx = rrIndex[hit] ?: 0
        rrIndex[hit] = (idx + 1) % bank.size
        return bank[idx]
    }

    /** Read WAV PCM data by properly locating the 'data' chunk (not assuming 44-byte header) */
    private fun readWavPcm(input: InputStream): ShortArray? {
        val bytes = input.use { it.readBytes() }
        if (bytes.size < 12) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        // Verify RIFF header
        if (bytes[0] != 'R'.code.toByte() || bytes[1] != 'I'.code.toByte()) return null
        // Find "data" chunk
        var pos = 12 // skip RIFF header (12 bytes)
        var dataStart = -1
        var dataSize = 0
        while (pos + 8 <= bytes.size) {
            val chunkId = String(bytes, pos, 4, Charsets.US_ASCII)
            buf.position(pos + 4)
            val chunkSize = buf.int
            if (chunkId == "data") {
                dataStart = pos + 8
                dataSize = chunkSize
                break
            }
            pos += 8 + chunkSize
            if (chunkSize % 2 != 0) pos++ // WAV chunks are word-aligned
        }
        if (dataStart < 0 || dataStart >= bytes.size) return null
        val actualSize = minOf(dataSize, bytes.size - dataStart)
        val numShorts = actualSize / 2
        if (numShorts <= 0) return null
        buf.position(dataStart)
        val pcm = ShortArray(numShorts)
        for (i in 0 until numShorts) {
            pcm[i] = buf.short
        }
        return pcm
    }

    fun getPattern(style: DrumStyle, beatsPerMeasure: Int): List<List<DrumEvent>> {
        return when (style) {
            DrumStyle.ROCK -> rockPattern(beatsPerMeasure)
            DrumStyle.POP_BALLAD -> popBalladPattern(beatsPerMeasure)
            DrumStyle.WALTZ -> waltzPattern()
            DrumStyle.REGGAE -> reggaePattern(beatsPerMeasure)
            DrumStyle.FUNK -> funkPattern(beatsPerMeasure)
            DrumStyle.COUNTRY -> countryPattern(beatsPerMeasure)
            DrumStyle.BLUES_SHUFFLE -> bluesShufflePattern(beatsPerMeasure)
            DrumStyle.BOSSA_NOVA -> bossaNovaPattern(beatsPerMeasure)
            DrumStyle.METAL -> metalPattern(beatsPerMeasure)
            DrumStyle.PUNK -> punkPattern(beatsPerMeasure)
            DrumStyle.FOLK -> folkPattern(beatsPerMeasure)
            DrumStyle.RUMBA_FLAMENCA -> rumbaFlamencaPattern(beatsPerMeasure)
        }
    }

    /**
     * Play a drum loop. Reads [liveBpm] each beat so tempo changes take effect immediately.
     * Writes audio in small chunks (~10ms) so [stop] responds within ~10ms.
     * Uses a Mutex to guarantee only one loop writes to AudioTrack at any time.
     */
    suspend fun playLoop(
        context: Context,
        style: DrumStyle,
        bpm: Int,
        beatsPerMeasure: Int = 4,
        onBeat: ((Int) -> Unit)? = null
    ) {
        stop() // signal any existing loop to exit
        playMutex.withLock {
            // Guaranteed: no other loop is writing to AudioTrack now
            init(context)
            liveBpm = bpm
            isPlaying = true
            try { audioTrack?.flush() } catch (_: Exception) { }
            withContext(Dispatchers.Default) {
                try {
                    val pattern = getPattern(style, beatsPerMeasure)
                    while (coroutineContext.isActive && isPlaying) {
                        for ((beatIdx, events) in pattern.withIndex()) {
                            if (!coroutineContext.isActive || !isPlaying) break
                            onBeat?.invoke(beatIdx)
                            // Read BPM live each beat
                            val currentBpm = liveBpm.coerceIn(30, 300)
                            val beatDurationMs = 60_000.0 / currentBpm
                            val beatSamples = (SAMPLE_RATE * beatDurationMs / 1000.0).toInt()
                            val buffer = ShortArray(beatSamples)
                            for (event in events) {
                                val sample = nextSample(event.hit) ?: continue
                                val offsetSamples = (event.positionInBeat * beatSamples).toInt()
                                mixInto(buffer, sample, offsetSamples, event.velocity)
                            }
                            // Write in small chunks for responsive stop
                            var written = 0
                            val track = audioTrack ?: break
                            while (written < buffer.size && isPlaying && coroutineContext.isActive) {
                                val remaining = buffer.size - written
                                val chunkSize = minOf(CHUNK_SAMPLES, remaining)
                                try {
                                    track.write(buffer, written, chunkSize)
                                } catch (_: Exception) { break }
                                written += chunkSize
                            }
                        }
                    }
                } catch (_: Exception) { }
            }
            // Clean up when loop exits
            try { audioTrack?.flush() } catch (_: Exception) { }
        }
    }

    fun stop() {
        isPlaying = false
        // Do NOT flush here — the mutex-holding loop will flush on exit.
        // Flushing here would race with write() on the audio thread.
    }

    fun release() {
        // Legacy release — forces immediate cleanup regardless of refCount
        synchronized(lock) { refCount = 0 }
        stop()
        doRelease()
    }

    private fun doRelease() {
        synchronized(lock) {
            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (_: Exception) { }
            audioTrack = null
            sampleBanks.clear()
            rrIndex.clear()
            initialized = false
        }
    }

    private fun mixInto(buffer: ShortArray, sample: ShortArray, offset: Int, velocity: Float) {
        for (i in sample.indices) {
            val idx = offset + i
            if (idx >= buffer.size) break
            val mixed = buffer[idx].toInt() + (sample[i].toInt() * velocity).toInt()
            buffer[idx] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    // ==================== PATTERN DEFINITIONS ====================
    // Each pattern returns a list of beats, each beat has a list of DrumEvents.
    // positionInBeat: 0.0 = on beat, 0.5 = 8th-note offbeat, 0.25/0.75 = 16th notes

    private fun rockPattern(beats: Int): List<List<DrumEvent>> {
        val pattern = mutableListOf<List<DrumEvent>>()
        for (b in 0 until beats) {
            val events = mutableListOf<DrumEvent>()
            events.add(DrumEvent(DrumHit.HH_CLOSED, 0f))
            events.add(DrumEvent(DrumHit.HH_CLOSED, 0.5f))
            when (b) {
                0 -> { events.add(DrumEvent(DrumHit.KICK_HARD, 0f)); if (b == 0) events.add(DrumEvent(DrumHit.CRASH, 0f, 0.5f)) }
                1 -> events.add(DrumEvent(DrumHit.SNARE_HARD, 0f))
                2 -> events.add(DrumEvent(DrumHit.KICK_HARD, 0f))
                3 -> events.add(DrumEvent(DrumHit.SNARE_HARD, 0f))
                else -> if (b % 2 == 0) events.add(DrumEvent(DrumHit.KICK_HARD, 0f))
                       else events.add(DrumEvent(DrumHit.SNARE_HARD, 0f))
            }
            pattern.add(events)
        }
        return pattern
    }

    private fun popBalladPattern(beats: Int): List<List<DrumEvent>> {
        val pattern = mutableListOf<List<DrumEvent>>()
        for (b in 0 until beats) {
            val events = mutableListOf<DrumEvent>()
            events.add(DrumEvent(DrumHit.HH_CLOSED, 0f, 0.6f))
            events.add(DrumEvent(DrumHit.HH_CLOSED, 0.5f, 0.4f))
            when (b) {
                0 -> events.add(DrumEvent(DrumHit.KICK_SOFT, 0f))
                1 -> events.add(DrumEvent(DrumHit.SNARE_CROSSSTICK, 0f))
                2 -> { events.add(DrumEvent(DrumHit.KICK_SOFT, 0f)); events.add(DrumEvent(DrumHit.KICK_SOFT, 0.5f)) }
                3 -> events.add(DrumEvent(DrumHit.SNARE_CROSSSTICK, 0f))
                else -> if (b % 2 == 0) events.add(DrumEvent(DrumHit.KICK_SOFT, 0f))
                       else events.add(DrumEvent(DrumHit.SNARE_CROSSSTICK, 0f))
            }
            pattern.add(events)
        }
        return pattern
    }

    private fun waltzPattern(): List<List<DrumEvent>> {
        return listOf(
            listOf(DrumEvent(DrumHit.KICK_HARD, 0f), DrumEvent(DrumHit.CRASH, 0f, 0.3f)),
            listOf(DrumEvent(DrumHit.HH_CLOSED, 0f, 0.6f)),
            listOf(DrumEvent(DrumHit.HH_CLOSED, 0f, 0.6f))
        )
    }

    private fun reggaePattern(beats: Int): List<List<DrumEvent>> {
        val pattern = mutableListOf<List<DrumEvent>>()
        for (b in 0 until beats) {
            val events = mutableListOf<DrumEvent>()
            events.add(DrumEvent(DrumHit.HH_CLOSED, 0.5f)) // offbeat hh
            when (b) {
                0 -> events.add(DrumEvent(DrumHit.KICK_HARD, 0f))
                2 -> events.add(DrumEvent(DrumHit.KICK_HARD, 0f))
                3 -> events.add(DrumEvent(DrumHit.SNARE_RIMSHOT, 0f))
                else -> if (b % 2 == 1) events.add(DrumEvent(DrumHit.SNARE_RIMSHOT, 0f))
            }
            pattern.add(events)
        }
        return pattern
    }

    private fun funkPattern(beats: Int): List<List<DrumEvent>> {
        val pattern = mutableListOf<List<DrumEvent>>()
        for (b in 0 until beats) {
            val events = mutableListOf<DrumEvent>()
            events.add(DrumEvent(DrumHit.HH_CLOSED, 0f))
            events.add(DrumEvent(DrumHit.HH_OPEN, 0.5f))
            when (b) {
                0 -> events.add(DrumEvent(DrumHit.KICK_HARD, 0f))
                1 -> { events.add(DrumEvent(DrumHit.SNARE_HARD, 0f)); events.add(DrumEvent(DrumHit.KICK_HARD, 0.75f)) }
                2 -> events.add(DrumEvent(DrumHit.KICK_HARD, 0.5f))
                3 -> events.add(DrumEvent(DrumHit.SNARE_HARD, 0f))
                else -> if (b % 2 == 0) events.add(DrumEvent(DrumHit.KICK_HARD, 0f))
                       else events.add(DrumEvent(DrumHit.SNARE_HARD, 0f))
            }
            pattern.add(events)
        }
        return pattern
    }

    private fun countryPattern(beats: Int): List<List<DrumEvent>> {
        val pattern = mutableListOf<List<DrumEvent>>()
        for (b in 0 until beats) {
            val events = mutableListOf<DrumEvent>()
            events.add(DrumEvent(DrumHit.HH_CLOSED, 0f))
            events.add(DrumEvent(DrumHit.HH_CLOSED, 0.5f))
            when (b) {
                0 -> events.add(DrumEvent(DrumHit.KICK_HARD, 0f))
                1 -> events.add(DrumEvent(DrumHit.SNARE_CROSSSTICK, 0f))
                2 -> { events.add(DrumEvent(DrumHit.KICK_HARD, 0f)); events.add(DrumEvent(DrumHit.KICK_HARD, 0.5f)) }
                3 -> events.add(DrumEvent(DrumHit.SNARE_CROSSSTICK, 0f))
                else -> if (b % 2 == 0) events.add(DrumEvent(DrumHit.KICK_HARD, 0f))
                       else events.add(DrumEvent(DrumHit.SNARE_CROSSSTICK, 0f))
            }
            pattern.add(events)
        }
        return pattern
    }

    private fun bluesShufflePattern(beats: Int): List<List<DrumEvent>> {
        val pattern = mutableListOf<List<DrumEvent>>()
        for (b in 0 until beats) {
            val events = mutableListOf<DrumEvent>()
            events.add(DrumEvent(DrumHit.HH_CLOSED, 0f))
            events.add(DrumEvent(DrumHit.HH_CLOSED, 0.67f)) // shuffle feel (triplet)
            when (b) {
                0 -> events.add(DrumEvent(DrumHit.KICK_HARD, 0f))
                1 -> events.add(DrumEvent(DrumHit.SNARE_HARD, 0f))
                2 -> { events.add(DrumEvent(DrumHit.KICK_HARD, 0f)); events.add(DrumEvent(DrumHit.KICK_HARD, 0.67f)) }
                3 -> events.add(DrumEvent(DrumHit.SNARE_HARD, 0f))
                else -> if (b % 2 == 0) events.add(DrumEvent(DrumHit.KICK_HARD, 0f))
                       else events.add(DrumEvent(DrumHit.SNARE_HARD, 0f))
            }
            pattern.add(events)
        }
        return pattern
    }

    private fun bossaNovaPattern(beats: Int): List<List<DrumEvent>> {
        val pattern = mutableListOf<List<DrumEvent>>()
        for (b in 0 until beats) {
            val events = mutableListOf<DrumEvent>()
            events.add(DrumEvent(DrumHit.HH_CLOSED, 0f, 0.5f))
            events.add(DrumEvent(DrumHit.HH_CLOSED, 0.5f, 0.5f))
            when (b) {
                0 -> events.add(DrumEvent(DrumHit.KICK_SOFT, 0f))
                1 -> events.add(DrumEvent(DrumHit.SNARE_CROSSSTICK, 0.5f))
                2 -> events.add(DrumEvent(DrumHit.KICK_SOFT, 0.5f))
                3 -> events.add(DrumEvent(DrumHit.SNARE_CROSSSTICK, 0f))
                else -> if (b % 2 == 0) events.add(DrumEvent(DrumHit.KICK_SOFT, 0f))
                       else events.add(DrumEvent(DrumHit.SNARE_CROSSSTICK, 0f))
            }
            pattern.add(events)
        }
        return pattern
    }

    private fun metalPattern(beats: Int): List<List<DrumEvent>> {
        val pattern = mutableListOf<List<DrumEvent>>()
        for (b in 0 until beats) {
            val events = mutableListOf<DrumEvent>()
            // Double bass + 16th note hihat
            events.add(DrumEvent(DrumHit.HH_CLOSED, 0f))
            events.add(DrumEvent(DrumHit.HH_CLOSED, 0.25f, 0.7f))
            events.add(DrumEvent(DrumHit.HH_CLOSED, 0.5f))
            events.add(DrumEvent(DrumHit.HH_CLOSED, 0.75f, 0.7f))
            events.add(DrumEvent(DrumHit.KICK_HARD, 0f))
            events.add(DrumEvent(DrumHit.KICK_HARD, 0.5f))
            when (b) {
                0 -> events.add(DrumEvent(DrumHit.CRASH, 0f, 0.5f))
                1 -> events.add(DrumEvent(DrumHit.SNARE_HARD, 0f))
                3 -> events.add(DrumEvent(DrumHit.SNARE_HARD, 0f))
                else -> {}
            }
            pattern.add(events)
        }
        return pattern
    }

    private fun punkPattern(beats: Int): List<List<DrumEvent>> {
        val pattern = mutableListOf<List<DrumEvent>>()
        for (b in 0 until beats) {
            val events = mutableListOf<DrumEvent>()
            events.add(DrumEvent(DrumHit.HH_CLOSED, 0f))
            events.add(DrumEvent(DrumHit.HH_CLOSED, 0.25f, 0.8f))
            events.add(DrumEvent(DrumHit.HH_CLOSED, 0.5f))
            events.add(DrumEvent(DrumHit.HH_CLOSED, 0.75f, 0.8f))
            when (b) {
                0 -> events.add(DrumEvent(DrumHit.KICK_HARD, 0f))
                1 -> events.add(DrumEvent(DrumHit.SNARE_HARD, 0f))
                2 -> events.add(DrumEvent(DrumHit.KICK_HARD, 0f))
                3 -> events.add(DrumEvent(DrumHit.SNARE_HARD, 0f))
                else -> if (b % 2 == 0) events.add(DrumEvent(DrumHit.KICK_HARD, 0f))
                       else events.add(DrumEvent(DrumHit.SNARE_HARD, 0f))
            }
            pattern.add(events)
        }
        return pattern
    }

    private fun folkPattern(beats: Int): List<List<DrumEvent>> {
        val pattern = mutableListOf<List<DrumEvent>>()
        for (b in 0 until beats) {
            val events = mutableListOf<DrumEvent>()
            events.add(DrumEvent(DrumHit.HH_CLOSED, 0f, 0.5f))
            events.add(DrumEvent(DrumHit.HH_CLOSED, 0.5f, 0.3f))
            when (b) {
                0 -> events.add(DrumEvent(DrumHit.KICK_SOFT, 0f))
                1 -> events.add(DrumEvent(DrumHit.SNARE_CROSSSTICK, 0f))
                2 -> events.add(DrumEvent(DrumHit.KICK_SOFT, 0f))
                3 -> events.add(DrumEvent(DrumHit.SNARE_CROSSSTICK, 0f))
                else -> if (b % 2 == 0) events.add(DrumEvent(DrumHit.KICK_SOFT, 0f))
                       else events.add(DrumEvent(DrumHit.SNARE_CROSSSTICK, 0f))
            }
            pattern.add(events)
        }
        return pattern
    }

    private fun rumbaFlamencaPattern(beats: Int): List<List<DrumEvent>> {
        val pattern = mutableListOf<List<DrumEvent>>()
        for (b in 0 until beats) {
            val events = mutableListOf<DrumEvent>()
            // Rumba flamenca: syncopated cajón-like pattern
            when (b) {
                0 -> {
                    events.add(DrumEvent(DrumHit.KICK_HARD, 0f))
                    events.add(DrumEvent(DrumHit.HH_CLOSED, 0f, 0.4f))
                    events.add(DrumEvent(DrumHit.HH_CLOSED, 0.5f, 0.4f))
                }
                1 -> {
                    events.add(DrumEvent(DrumHit.SNARE_RIMSHOT, 0f))
                    events.add(DrumEvent(DrumHit.HH_CLOSED, 0.5f, 0.4f))
                    events.add(DrumEvent(DrumHit.KICK_SOFT, 0.75f))
                }
                2 -> {
                    events.add(DrumEvent(DrumHit.KICK_HARD, 0f))
                    events.add(DrumEvent(DrumHit.HH_CLOSED, 0.25f, 0.4f))
                    events.add(DrumEvent(DrumHit.SNARE_RIMSHOT, 0.5f))
                }
                3 -> {
                    events.add(DrumEvent(DrumHit.HH_CLOSED, 0f, 0.4f))
                    events.add(DrumEvent(DrumHit.KICK_SOFT, 0.25f))
                    events.add(DrumEvent(DrumHit.SNARE_RIMSHOT, 0.5f))
                    events.add(DrumEvent(DrumHit.HH_CLOSED, 0.75f, 0.4f))
                }
                else -> {
                    if (b % 2 == 0) events.add(DrumEvent(DrumHit.KICK_HARD, 0f))
                    else events.add(DrumEvent(DrumHit.SNARE_RIMSHOT, 0f))
                    events.add(DrumEvent(DrumHit.HH_CLOSED, 0.5f, 0.4f))
                }
            }
            pattern.add(events)
        }
        return pattern
    }
}
