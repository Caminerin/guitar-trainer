package com.caminerin.guitartrainer.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
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
    private const val SAMPLE_RATE = 44100
    private var audioTrack: AudioTrack? = null
    private val samples = mutableMapOf<DrumHit, ShortArray>()
    private var initialized = false

    @Volatile var isPlaying = false
        private set

    fun init(context: Context) {
        if (initialized) return
        loadSamples(context)

        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
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
        initialized = true
    }

    private fun loadSamples(context: Context) {
        val fileMap = mapOf(
            DrumHit.KICK_HARD to "drums/kick_hard.wav",
            DrumHit.KICK_SOFT to "drums/kick_soft.wav",
            DrumHit.SNARE_HARD to "drums/snare_hard.wav",
            DrumHit.SNARE_SOFT to "drums/snare_soft.wav",
            DrumHit.SNARE_CROSSSTICK to "drums/snare_crossstick.wav",
            DrumHit.SNARE_RIMSHOT to "drums/snare_rimshot.wav",
            DrumHit.HH_CLOSED to "drums/hh_closed.wav",
            DrumHit.HH_OPEN to "drums/hh_open.wav",
            DrumHit.HH_HALF to "drums/hh_half.wav",
            DrumHit.HH_PEDAL to "drums/hh_pedal.wav",
            DrumHit.RIDE_NORMAL to "drums/ride_normal.wav",
            DrumHit.RIDE_BELL to "drums/ride_bell.wav",
            DrumHit.CRASH to "drums/crash.wav"
        )
        for ((hit, file) in fileMap) {
            try {
                val pcm = readWavPcm(context.assets.open(file))
                if (pcm != null) samples[hit] = pcm
            } catch (_: Exception) { }
        }
    }

    private fun readWavPcm(input: InputStream): ShortArray? {
        val bytes = input.use { it.readBytes() }
        if (bytes.size < 44) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(44) // skip WAV header
        val numShorts = (bytes.size - 44) / 2
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

    suspend fun playLoop(
        context: Context,
        style: DrumStyle,
        bpm: Int,
        beatsPerMeasure: Int = 4,
        onBeat: ((Int) -> Unit)? = null
    ) {
        init(context)
        isPlaying = true
        withContext(Dispatchers.Default) {
            val pattern = getPattern(style, beatsPerMeasure)
            while (coroutineContext.isActive && isPlaying) {
                for ((beatIdx, events) in pattern.withIndex()) {
                    if (!coroutineContext.isActive || !isPlaying) break
                    onBeat?.invoke(beatIdx)
                    val beatDurationMs = 60_000.0 / bpm
                    val beatSamples = (SAMPLE_RATE * beatDurationMs / 1000.0).toInt()
                    val buffer = ShortArray(beatSamples)
                    for (event in events) {
                        val sample = samples[event.hit] ?: continue
                        val offsetSamples = (event.positionInBeat * beatSamples).toInt()
                        mixInto(buffer, sample, offsetSamples, event.velocity)
                    }
                    audioTrack?.write(buffer, 0, buffer.size)
                }
            }
        }
    }

    fun stop() {
        isPlaying = false
        audioTrack?.flush()
    }

    fun release() {
        stop()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        samples.clear()
        initialized = false
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
