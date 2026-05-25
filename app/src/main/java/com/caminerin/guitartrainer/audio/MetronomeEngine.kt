package com.caminerin.guitartrainer.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

enum class MetronomeSound(val displayName: String) {
    CLICK("Click clásico"),
    WOODBLOCK("Woodblock"),
    RIMSHOT("Rimshot"),
    HIHAT("Hi-hat"),
    BEEP("Bip electrónico")
}

data class MetronomeConfig(
    val bpm: Int = 120,
    val beatsPerMeasure: Int = 4,
    val subdivision: Int = 1,
    val sound: MetronomeSound = MetronomeSound.CLICK,
    val trainingEnabled: Boolean = false,
    val trainingIntervalBeats: Int = 4,
    val trainingBpmChange: Int = 5,
    val trainingMaxBpm: Int = 200,
    val trainingMinBpm: Int = 40,
    val timerEnabled: Boolean = false,
    val timerMeasures: Int = 0,
    val timerSeconds: Int = 0
)

class MetronomeEngine {

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val CLICK_DURATION_SAMPLES = 1323 // 30ms
        private const val ACCENT_DURATION_SAMPLES = 1764 // 40ms
        private const val SUB_DURATION_SAMPLES = 882 // 20ms
    }

    private val _currentBeat = MutableStateFlow(0)
    val currentBeat: StateFlow<Int> = _currentBeat

    private val _currentSubBeat = MutableStateFlow(0)
    val currentSubBeat: StateFlow<Int> = _currentSubBeat

    private val _currentMeasure = MutableStateFlow(0)
    val currentMeasure: StateFlow<Int> = _currentMeasure

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentBpm = MutableStateFlow(120)
    val currentBpm: StateFlow<Int> = _currentBpm

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds

    private fun generateClick(frequency: Float, durationSamples: Int, volume: Float): ShortArray {
        val samples = ShortArray(durationSamples)
        for (i in 0 until durationSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val envelope = exp(-t * 50.0).toFloat() * volume
            val sample = (sin(2.0 * PI * frequency * t) * envelope * Short.MAX_VALUE).toInt()
            samples[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    private fun generateNoise(durationSamples: Int, volume: Float): ShortArray {
        val samples = ShortArray(durationSamples)
        for (i in 0 until durationSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val envelope = exp(-t * 80.0).toFloat() * volume
            val noise = (Math.random() * 2.0 - 1.0).toFloat()
            val sample = (noise * envelope * Short.MAX_VALUE).toInt()
            samples[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    private fun getClickSamples(sound: MetronomeSound, isAccent: Boolean): ShortArray {
        val vol = if (isAccent) 1.0f else 0.7f
        val dur = if (isAccent) ACCENT_DURATION_SAMPLES else CLICK_DURATION_SAMPLES
        return when (sound) {
            MetronomeSound.CLICK -> generateClick(if (isAccent) 1000f else 800f, dur, vol)
            MetronomeSound.WOODBLOCK -> generateClick(if (isAccent) 1400f else 1200f, dur, vol)
            MetronomeSound.RIMSHOT -> generateClick(if (isAccent) 500f else 400f, dur, vol)
            MetronomeSound.HIHAT -> generateNoise(dur, vol * 0.7f)
            MetronomeSound.BEEP -> generateClick(if (isAccent) 1200f else 1000f, dur, vol)
        }
    }

    private fun getSubClickSamples(sound: MetronomeSound): ShortArray {
        return when (sound) {
            MetronomeSound.CLICK -> generateClick(1200f, SUB_DURATION_SAMPLES, 0.4f)
            MetronomeSound.WOODBLOCK -> generateClick(1800f, SUB_DURATION_SAMPLES, 0.4f)
            MetronomeSound.RIMSHOT -> generateClick(600f, SUB_DURATION_SAMPLES, 0.4f)
            MetronomeSound.HIHAT -> generateNoise(SUB_DURATION_SAMPLES, 0.3f)
            MetronomeSound.BEEP -> generateClick(1500f, SUB_DURATION_SAMPLES, 0.4f)
        }
    }

    private fun buildBeatAudio(
        bpm: Int,
        subdivision: Int,
        sound: MetronomeSound,
        isAccent: Boolean
    ): ShortArray {
        val totalSamplesPerBeat = (SAMPLE_RATE * 60.0 / bpm).roundToInt()
        val samplesPerSub = totalSamplesPerBeat / subdivision
        val result = ShortArray(totalSamplesPerBeat)

        for (sub in 0 until subdivision) {
            val click = if (sub == 0) {
                getClickSamples(sound, isAccent)
            } else {
                getSubClickSamples(sound)
            }
            val offset = sub * samplesPerSub
            for (i in click.indices) {
                if (offset + i < result.size) {
                    result[offset + i] = click[i]
                }
            }
        }
        return result
    }

    suspend fun start(config: MetronomeConfig) = withContext(Dispatchers.IO) {
        if (_isPlaying.value) return@withContext

        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
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
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(bufferSize * 4, SAMPLE_RATE * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track.play()
        _isPlaying.value = true
        _currentBeat.value = 0
        _currentSubBeat.value = 0
        _currentMeasure.value = 0
        _currentBpm.value = config.bpm
        _elapsedSeconds.value = 0

        var activeBpm = config.bpm
        var beatInMeasure = 0
        var measuresCompleted = 0
        val startTimeMs = System.currentTimeMillis()

        while (isActive && _isPlaying.value) {
            val isAccent = (beatInMeasure == 0)

            _currentBeat.value = beatInMeasure

            val beatAudio = buildBeatAudio(activeBpm, config.subdivision, config.sound, isAccent)
            track.write(beatAudio, 0, beatAudio.size)

            beatInMeasure = (beatInMeasure + 1) % config.beatsPerMeasure

            if (beatInMeasure == 0) {
                measuresCompleted++
                _currentMeasure.value = measuresCompleted

                if (config.trainingEnabled && measuresCompleted % config.trainingIntervalBeats == 0) {
                    activeBpm = (activeBpm + config.trainingBpmChange)
                        .coerceIn(config.trainingMinBpm, config.trainingMaxBpm)
                    _currentBpm.value = activeBpm
                }

                if (config.timerEnabled && config.timerMeasures > 0 &&
                    measuresCompleted >= config.timerMeasures) {
                    break
                }
            }

            _elapsedSeconds.value = ((System.currentTimeMillis() - startTimeMs) / 1000).toInt()

            if (config.timerEnabled && config.timerSeconds > 0 &&
                _elapsedSeconds.value >= config.timerSeconds) {
                break
            }
        }

        track.stop()
        track.release()
        _isPlaying.value = false
    }

    fun stop() {
        _isPlaying.value = false
    }
}
