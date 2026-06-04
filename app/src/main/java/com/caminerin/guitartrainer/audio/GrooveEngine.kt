package com.caminerin.guitartrainer.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

/**
 * GrooveEngine — Pattern-based drum machine for the Groove Trainer feature.
 * Loads patterns from JSON assets (converted from Hydrogen drum machine, CC BY-SA).
 * Supports: complexity levels, fills, humanization, swing, silence bars, tempo progression.
 */
object GrooveEngine {
    private const val SAMPLE_RATE = 22050
    private var audioTrack: AudioTrack? = null
    private val samples = mutableMapOf<DrumHit, ShortArray>()
    private var initialized = false

    @Volatile var isPlaying = false
        private set

    // Pattern library
    private val categories = mutableListOf<GrooveCategory>()
    private var indexLoaded = false

    // Humanization state: per-bar drift tendency
    private var driftTendency = 0f // accumulates slight timing bias like a real drummer

    // Stereo panning map: -1.0 = full left, 0.0 = center, 1.0 = full right
    private val PAN_MAP = mapOf(
        DrumHit.KICK_HARD to 0.0f, DrumHit.KICK_SOFT to 0.0f,
        DrumHit.SNARE_HARD to 0.08f, DrumHit.SNARE_SOFT to 0.08f,
        DrumHit.SNARE_CROSSSTICK to 0.1f, DrumHit.SNARE_RIMSHOT to 0.08f,
        DrumHit.HH_CLOSED to -0.7f, DrumHit.HH_OPEN to -0.7f,
        DrumHit.HH_HALF to -0.7f, DrumHit.HH_PEDAL to -0.5f,
        DrumHit.RIDE_NORMAL to 0.6f, DrumHit.RIDE_BELL to 0.55f,
        DrumHit.CRASH to -0.4f
    )

    fun init(context: Context) {
        if (initialized) return
        loadSamples(context)
        if (!indexLoaded) loadPatternIndex(context)

        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT
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
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(minBuf.coerceAtLeast(SAMPLE_RATE * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack?.play()
        initialized = true
    }

    private fun loadSamples(context: Context) {
        val fileMap = mapOf(
            DrumHit.KICK_HARD to "drums/kick_hard_1.wav",
            DrumHit.KICK_SOFT to "drums/kick_soft_1.wav",
            DrumHit.SNARE_HARD to "drums/snare_hard_1.wav",
            DrumHit.SNARE_SOFT to "drums/snare_soft_1.wav",
            DrumHit.SNARE_CROSSSTICK to "drums/snare_crossstick_1.wav",
            DrumHit.SNARE_RIMSHOT to "drums/snare_rimshot_1.wav",
            DrumHit.HH_CLOSED to "drums/hh_closed_1.wav",
            DrumHit.HH_OPEN to "drums/hh_open_1.wav",
            DrumHit.HH_HALF to "drums/hh_half_1.wav",
            DrumHit.HH_PEDAL to "drums/hh_pedal_1.wav",
            DrumHit.RIDE_NORMAL to "drums/ride_normal_1.wav",
            DrumHit.RIDE_BELL to "drums/ride_bell_1.wav",
            DrumHit.CRASH to "drums/crash_1.wav"
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
        if (bytes.size < 12) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes[0] != 'R'.code.toByte() || bytes[1] != 'I'.code.toByte()) return null

        var pos = 12
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
            if (chunkSize % 2 != 0) pos++
        }
        if (dataStart < 0 || dataStart >= bytes.size) return null
        val actualSize = minOf(dataSize, bytes.size - dataStart)
        val numShorts = actualSize / 2
        val pcm = ShortArray(numShorts)
        buf.position(dataStart)
        for (i in 0 until numShorts) {
            pcm[i] = buf.short
        }
        return pcm
    }

    // ==================== PATTERN LOADING ====================

    private fun loadPatternIndex(context: Context) {
        try {
            val indexJson = context.assets.open("grooves/index.json").bufferedReader().readText()
            val index = JSONObject(indexJson)
            val cats = index.getJSONArray("categories")
            categories.clear()
            for (i in 0 until cats.length()) {
                val cat = cats.getJSONObject(i)
                categories.add(GrooveCategory(
                    id = cat.getString("id"),
                    displayName = cat.getString("displayName"),
                    patternCount = cat.getInt("patternCount"),
                    fillCount = cat.getInt("fillCount")
                ))
            }
            indexLoaded = true
        } catch (e: Exception) {
            Log.w("GrooveEngine", "Failed to load pattern index", e)
        }
    }

    fun getCategories(): List<GrooveCategory> = categories.toList()

    fun loadCategoryPatterns(context: Context, categoryId: String): GrooveCategoryData? {
        return try {
            val json = context.assets.open("grooves/$categoryId.json").bufferedReader().readText()
            val obj = JSONObject(json)
            val patterns = mutableListOf<GroovePattern>()
            val fills = mutableListOf<GroovePattern>()

            val patternsArr = obj.getJSONArray("patterns")
            for (i in 0 until patternsArr.length()) {
                patterns.add(parsePattern(patternsArr.getJSONObject(i)))
            }
            val fillsArr = obj.getJSONArray("fills")
            for (i in 0 until fillsArr.length()) {
                fills.add(parsePattern(fillsArr.getJSONObject(i)))
            }

            GrooveCategoryData(
                id = categoryId,
                displayName = obj.getString("displayName"),
                patterns = patterns,
                fills = fills
            )
        } catch (_: Exception) { null }
    }

    private fun parsePattern(obj: JSONObject): GroovePattern {
        val events = mutableListOf<GrooveEvent>()
        val eventsArr = obj.getJSONArray("events")
        for (i in 0 until eventsArr.length()) {
            val e = eventsArr.getJSONObject(i)
            val hit = try { DrumHit.valueOf(e.getString("hit")) } catch (_: Exception) { null }
            if (hit != null) {
                events.add(GrooveEvent(
                    hit = hit,
                    step = e.getDouble("step").toFloat(),
                    velocity = e.getDouble("velocity").toFloat(),
                    probability = if (e.has("probability")) e.getDouble("probability").toFloat() else 1f
                ))
            }
        }
        return GroovePattern(
            name = obj.getString("name"),
            stepsPerBar = if (obj.has("stepsPerBar")) obj.getInt("stepsPerBar") else 16,
            events = events,
            isFill = if (obj.has("isFill")) obj.getBoolean("isFill") else false
        )
    }

    // ==================== COMPLEXITY FILTERING ====================

    private val LEVEL1_HITS = setOf(DrumHit.KICK_HARD, DrumHit.KICK_SOFT, DrumHit.SNARE_HARD, DrumHit.SNARE_SOFT)
    private val LEVEL2_HITS = setOf(DrumHit.KICK_HARD, DrumHit.KICK_SOFT, DrumHit.SNARE_HARD, DrumHit.SNARE_SOFT, DrumHit.SNARE_CROSSSTICK, DrumHit.SNARE_RIMSHOT, DrumHit.HH_CLOSED, DrumHit.HH_HALF, DrumHit.HH_PEDAL)

    fun filterByComplexity(events: List<GrooveEvent>, level: Int): List<GrooveEvent> {
        return when (level) {
            1 -> events.filter { it.hit in LEVEL1_HITS }
            2 -> events.filter { it.hit in LEVEL2_HITS }
            3 -> events
            4 -> addGhostNotes(events)
            5 -> addGhostNotes(events, dense = true)
            else -> events
        }
    }

    private fun addGhostNotes(events: List<GrooveEvent>, dense: Boolean = false): List<GrooveEvent> {
        val result = events.toMutableList()
        val existingSteps = events.filter { it.hit == DrumHit.SNARE_HARD || it.hit == DrumHit.SNARE_SOFT }.map { it.step }.toSet()
        val steps = if (dense) listOf(1f, 3f, 5f, 7f, 9f, 11f, 13f, 15f) else listOf(3f, 7f, 11f, 15f)
        for (step in steps) {
            if (step !in existingSteps && Random.nextFloat() < if (dense) 0.4f else 0.25f) {
                result.add(GrooveEvent(DrumHit.SNARE_SOFT, step, velocity = 0.2f + Random.nextFloat() * 0.15f))
            }
        }
        return result.sortedBy { it.step }
    }

    // ==================== HUMANIZATION ====================

    enum class Feel { TIGHT, NATURAL, LOOSE }

    private fun humanizeVelocity(velocity: Float, feel: Feel, beat: Int, barCount: Int): Float {
        val range = when (feel) {
            Feel.TIGHT -> 0.02f
            Feel.NATURAL -> 0.08f
            Feel.LOOSE -> 0.15f
        }
        // Per-beat accent: beat 1 slightly stronger, odd beats slightly weaker
        val beatAccent = when (beat % 4) {
            0 -> 0.04f   // downbeat: slightly louder
            2 -> 0.01f   // backbeat: tiny boost
            else -> -0.03f // off-beats: slightly softer
        }
        // Per-bar micro-variation: each bar sounds slightly different
        val barVariation = ((barCount * 7 + beat * 13) % 17).toFloat() / 17f * 0.04f - 0.02f
        return (velocity + (Random.nextFloat() - 0.5f) * range + beatAccent + barVariation).coerceIn(0.1f, 1.0f)
    }

    private fun humanizeTiming(feel: Feel, step: Float): Float {
        // Drift tendency: drummer gradually pushes/pulls, then corrects (like a real human)
        driftTendency += (Random.nextFloat() - 0.52f) * 0.01f // slight pull-back bias
        driftTendency = driftTendency.coerceIn(-0.04f, 0.04f)
        // On downbeats, correct drift (drummer re-syncs)
        if (step.toInt() == 0) driftTendency *= 0.3f
        val randomComponent = when (feel) {
            Feel.TIGHT -> 0f
            Feel.NATURAL -> (Random.nextFloat() - 0.5f) * 0.05f
            Feel.LOOSE -> (Random.nextFloat() - 0.5f) * 0.10f
        }
        return randomComponent + driftTendency
    }

    /**
     * Micro-pitch variation: resample at a slightly different rate to avoid
     * the "machine gun" effect of identical samples. Variation is ±2%.
     */
    private fun pitchShiftSample(sample: ShortArray, variation: Float): ShortArray {
        val rate = 1.0f + variation // e.g., 0.98 to 1.02
        val newLen = (sample.size / rate).toInt()
        if (newLen <= 0) return sample
        val result = ShortArray(newLen)
        for (i in result.indices) {
            val srcPos = i * rate
            val srcIdx = srcPos.toInt()
            val frac = srcPos - srcIdx
            if (srcIdx + 1 < sample.size) {
                result[i] = (sample[srcIdx] * (1f - frac) + sample[srcIdx + 1] * frac).toInt().toShort()
            } else if (srcIdx < sample.size) {
                result[i] = sample[srcIdx]
            }
        }
        return result
    }

    /**
     * Brightness filter: softer hits sound darker (low-pass).
     * Simple 1-pole filter: y[n] = alpha * x[n] + (1-alpha) * y[n-1]
     */
    private fun applyBrightnessFilter(sample: ShortArray, velocity: Float): ShortArray {
        if (velocity > 0.7f) return sample // hard hits: no filter
        val alpha = 0.4f + velocity * 0.6f // 0.4 at vel=0, 1.0 at vel=1
        val result = ShortArray(sample.size)
        var prev = 0f
        for (i in sample.indices) {
            prev = alpha * sample[i].toFloat() + (1f - alpha) * prev
            result[i] = prev.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return result
    }

    /**
     * Simple early reflections: mix a delayed, attenuated copy to add room feel.
     */
    private fun addRoomReflection(buffer: ShortArray, stereoBuffer: ShortArray) {
        val delaySamples = (SAMPLE_RATE * 0.012).toInt() // 12ms early reflection
        val amount = 0.08f // subtle
        for (i in buffer.indices) {
            val srcIdx = i - delaySamples
            if (srcIdx >= 0) {
                val reflection = (buffer[srcIdx] * amount).toInt()
                val lIdx = i * 2
                val rIdx = i * 2 + 1
                if (rIdx < stereoBuffer.size) {
                    stereoBuffer[lIdx] = (stereoBuffer[lIdx] + reflection).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    stereoBuffer[rIdx] = (stereoBuffer[rIdx] + reflection).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
            }
        }
    }

    // ==================== PLAYBACK ====================

    data class PlayConfig(
        val bpm: Int = 100,
        val pattern: GroovePattern,
        val fills: List<GroovePattern> = emptyList(), // multiple fills for rotation
        val complexityLevel: Int = 3,
        val feel: Feel = Feel.NATURAL,
        val swing: Float = 0f, // 0.0 = straight, 0.5 = shuffle, 0.67 = heavy shuffle
        val fillEveryBars: Int = 0, // 0 = no auto fill, 4/8/12 = fill every N bars
        val silenceEveryBars: Int = 0, // 0 = no silence, e.g. 4 = play 4, silence 1
        val silenceDurationBars: Int = 1,
        val countIn: Boolean = false,
        val volumes: Map<String, Float> = emptyMap(), // "kick", "snare", "hihat", "ride" -> 0..1
        val tempoProgression: TempoProgression? = null
    )

    data class TempoProgression(
        val targetBpm: Int,
        val bpmIncrement: Int = 5,
        val barsPerStep: Int = 8
    )

    suspend fun playGroove(
        context: Context,
        config: PlayConfig,
        onBeat: ((bar: Int, beat: Int) -> Unit)? = null,
        onBpmChange: ((Int) -> Unit)? = null
    ) {
        stop()
        init(context)
        isPlaying = true
        lastHiHatOpenEnd = 0
        filteredEventsCache.clear()
        withContext(Dispatchers.Default) {
            var currentBpm = config.bpm
            var barCount = 0
            var barsAtCurrentTempo = 0
            val stepsPerBar = config.pattern.stepsPerBar
            val beatsPerBar = 4

            // Count-in: play 4 clicks
            if (config.countIn) {
                playCountIn(currentBpm)
            }

            while (coroutineContext.isActive && isPlaying) {
                // Determine if this bar is silence
                val isSilence = config.silenceEveryBars > 0 &&
                    barCount > 0 &&
                    (barCount % (config.silenceEveryBars + config.silenceDurationBars)) >= config.silenceEveryBars

                // Determine if this bar is a fill (rotate through available fills)
                val isFill = !isSilence && config.fills.isNotEmpty() && config.fillEveryBars > 0 &&
                    barCount > 0 && (barCount + 1) % config.fillEveryBars == 0

                val patternToUse = when {
                    isSilence -> null
                    isFill -> {
                        val fillIdx = (barCount / config.fillEveryBars) % config.fills.size
                        config.fills[fillIdx]
                    }
                    else -> config.pattern
                }

                // Render one bar
                val barDurationMs = (60_000.0 / currentBpm) * beatsPerBar
                val barSamples = (SAMPLE_RATE * barDurationMs / 1000.0).toInt()
                if (barSamples > barBuffer.size) barBuffer = ShortArray(barSamples)
                val buffer = barBuffer
                java.util.Arrays.fill(buffer, 0, barSamples, 0)

                // Mono mix buffer for room reflection source
                val monoBuffer = ShortArray(barSamples)
                // Stereo interleaved buffer: L,R,L,R,...
                val stereoBuffer = ShortArray(barSamples * 2)

                if (patternToUse != null) {
                    lastHiHatOpenEnd = 0
                    val cacheKey = System.identityHashCode(patternToUse).toLong() * 10 + config.complexityLevel
                    val sortedEvents = filteredEventsCache.getOrPut(cacheKey) {
                        filterByComplexity(patternToUse.events, config.complexityLevel).sortedBy { it.step }
                    }
                    for (event in sortedEvents) {
                        if (event.probability < 1f && Random.nextFloat() > event.probability) continue

                        // Hi-hat variation: subtle chance to swap closed↔half for realism
                        val actualHit = if (config.feel != Feel.TIGHT && event.hit == DrumHit.HH_CLOSED && Random.nextFloat() < 0.08f) {
                            DrumHit.HH_HALF
                        } else if (config.feel == Feel.LOOSE && event.hit == DrumHit.HH_HALF && Random.nextFloat() < 0.05f) {
                            DrumHit.HH_CLOSED
                        } else event.hit

                        var step = event.step
                        if (config.swing > 0f && step.toInt() % 2 == 1) {
                            step += config.swing * 0.5f
                        }
                        step += humanizeTiming(config.feel, event.step)

                        val sampleOffset = ((step / stepsPerBar) * barSamples).toInt()
                            .coerceIn(0, barSamples - 1)
                        val beatForAccent = (event.step.toInt() / (stepsPerBar / 4)).coerceIn(0, 3)

                        val velocity = humanizeVelocity(event.velocity, config.feel, beatForAccent, barCount) *
                            getVolumeForHit(actualHit, config.volumes)

                        val rawSample = samples[actualHit] ?: continue
                        // Micro-pitch variation: ±2% per hit
                        val pitchVar = (Random.nextFloat() - 0.5f) * 0.04f
                        val pitched = pitchShiftSample(rawSample, pitchVar)
                        // Brightness filter: soft hits sound darker
                        val processed = applyBrightnessFilter(pitched, velocity)
                        // Mix into mono buffer (for choke logic)
                        mixWithChoke(monoBuffer, actualHit, processed, sampleOffset, velocity)

                        // Pan to stereo
                        val pan = PAN_MAP[actualHit] ?: 0f
                        val leftGain = ((1f - pan) / 2f).coerceIn(0f, 1f)
                        val rightGain = ((1f + pan) / 2f).coerceIn(0f, 1f)
                        for (i in processed.indices) {
                            val idx = sampleOffset + i
                            if (idx >= barSamples) break
                            val sval = (processed[i].toInt() * velocity).toInt()
                            val lIdx = idx * 2
                            val rIdx = idx * 2 + 1
                            if (rIdx < stereoBuffer.size) {
                                stereoBuffer[lIdx] = (stereoBuffer[lIdx] + (sval * leftGain).toInt())
                                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                                stereoBuffer[rIdx] = (stereoBuffer[rIdx] + (sval * rightGain).toInt())
                                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                            }
                        }
                    }
                    // Add room reflection
                    addRoomReflection(monoBuffer, stereoBuffer)
                }

                // Beat callbacks + write stereo audio
                for (beat in 0 until beatsPerBar) {
                    if (!coroutineContext.isActive || !isPlaying) break
                    try { onBeat?.invoke(barCount, beat) } catch (_: Exception) { }
                    val beatSamples = barSamples / beatsPerBar
                    val startIdx = beat * beatSamples * 2 // stereo: 2 shorts per sample
                    val endIdx = (((beat + 1) * beatSamples) * 2).coerceAtMost(stereoBuffer.size)
                    val beatBuffer = stereoBuffer.copyOfRange(startIdx, endIdx)
                    try { audioTrack?.write(beatBuffer, 0, beatBuffer.size) } catch (_: Exception) { break }
                }

                barCount++
                barsAtCurrentTempo++

                // Tempo progression
                if (config.tempoProgression != null && barsAtCurrentTempo >= config.tempoProgression.barsPerStep) {
                    if (currentBpm < config.tempoProgression.targetBpm) {
                        currentBpm = (currentBpm + config.tempoProgression.bpmIncrement)
                            .coerceAtMost(config.tempoProgression.targetBpm)
                        barsAtCurrentTempo = 0
                        onBpmChange?.invoke(currentBpm)
                    }
                }
            }
        }
    }

    private fun playCountIn(bpm: Int) {
        val beatSamples = (SAMPLE_RATE * 60.0 / bpm).toInt()
        val click = samples[DrumHit.HH_CLOSED] ?: return
        for (i in 0 until 4) {
            val stereo = ShortArray(beatSamples * 2)
            for (j in click.indices) {
                if (j >= beatSamples) break
                val sval = (click[j].toInt() * 0.8f).toInt()
                stereo[j * 2] = sval.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                stereo[j * 2 + 1] = sval.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            audioTrack?.write(stereo, 0, stereo.size)
        }
    }

    private fun getVolumeForHit(hit: DrumHit, volumes: Map<String, Float>): Float {
        val group = when (hit) {
            DrumHit.KICK_HARD, DrumHit.KICK_SOFT -> "kick"
            DrumHit.SNARE_HARD, DrumHit.SNARE_SOFT, DrumHit.SNARE_CROSSSTICK, DrumHit.SNARE_RIMSHOT -> "snare"
            DrumHit.HH_CLOSED, DrumHit.HH_OPEN, DrumHit.HH_HALF, DrumHit.HH_PEDAL -> "hihat"
            DrumHit.RIDE_NORMAL, DrumHit.RIDE_BELL -> "ride"
            DrumHit.CRASH -> "crash"
        }
        return volumes[group] ?: 1.0f
    }

    /**
     * Mix sample into buffer with hi-hat choke support.
     * When a closed hi-hat plays, it cuts any ringing open hi-hat.
     */
    private var lastHiHatOpenEnd = 0
    private var barBuffer = ShortArray(SAMPLE_RATE * 3) // reusable bar buffer (~3s max)
    private val filteredEventsCache = mutableMapOf<Long, List<GrooveEvent>>()

    private fun mixInto(buffer: ShortArray, sample: ShortArray, offset: Int, velocity: Float) {
        for (i in sample.indices) {
            val idx = offset + i
            if (idx >= buffer.size) break
            val mixed = buffer[idx].toInt() + (sample[i].toInt() * velocity).toInt()
            buffer[idx] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun mixWithChoke(
        buffer: ShortArray,
        hit: DrumHit,
        sample: ShortArray,
        offset: Int,
        velocity: Float
    ) {
        // Hi-hat choke: closed HH cuts open HH resonance
        if (hit == DrumHit.HH_CLOSED || hit == DrumHit.HH_PEDAL) {
            // Fade out any open HH ringing in the buffer from offset
            if (lastHiHatOpenEnd > offset) {
                val fadeLen = (SAMPLE_RATE * 0.005).toInt() // 5ms fade
                for (i in 0 until fadeLen) {
                    val idx = offset + i
                    if (idx >= buffer.size || idx >= lastHiHatOpenEnd) break
                    val fade = 1f - (i.toFloat() / fadeLen)
                    buffer[idx] = (buffer[idx] * fade).toInt().toShort()
                }
                // Zero out the rest
                for (idx in (offset + fadeLen) until lastHiHatOpenEnd.coerceAtMost(buffer.size)) {
                    buffer[idx] = 0
                }
                lastHiHatOpenEnd = 0
            }
        }

        // Track open HH end position
        if (hit == DrumHit.HH_OPEN) {
            lastHiHatOpenEnd = offset + sample.size
        }

        mixInto(buffer, sample, offset, velocity)
    }

    fun stop() {
        isPlaying = false
        driftTendency = 0f
        try {
            val track = audioTrack
            if (track != null && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                val fadeLen = (SAMPLE_RATE * 0.02).toInt() // 20ms fade
                val fadeBuffer = ShortArray(fadeLen * 2) // stereo
                track.write(fadeBuffer, 0, fadeBuffer.size)
                track.flush()
            }
        } catch (_: Exception) { }
    }

    fun release() {
        stop()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) { }
        audioTrack = null
        samples.clear()
        initialized = false
    }
}

// ==================== DATA CLASSES ====================

data class GrooveCategory(
    val id: String,
    val displayName: String,
    val patternCount: Int,
    val fillCount: Int
)

data class GrooveCategoryData(
    val id: String,
    val displayName: String,
    val patterns: List<GroovePattern>,
    val fills: List<GroovePattern>
)

data class GroovePattern(
    val name: String,
    val stepsPerBar: Int = 16,
    val events: List<GrooveEvent>,
    val isFill: Boolean = false
)

data class GrooveEvent(
    val hit: DrumHit,
    val step: Float,
    val velocity: Float = 0.8f,
    val probability: Float = 1f
)
