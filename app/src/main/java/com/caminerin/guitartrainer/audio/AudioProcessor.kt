package com.caminerin.guitartrainer.audio

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class AudioProcessor(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val BUFFER_SIZE_SAMPLES = 4096
        private const val HOLD_DURATION_MS = 800L
        private const val LOW_FREQ_THRESHOLD = 150f
        private const val LOW_FREQ_BUFFER = 4096
        private const val HIGH_FREQ_BUFFER = 2048
        private const val SIGNAL_RMS_THRESHOLD = 0.0015
        private const val SMOOTH_WINDOW = 5
    }

    private val pitchDetector = PitchDetector(SAMPLE_RATE)
    val noteRecognizer = NoteRecognizer()
    private var audioRecord: AudioRecord? = null
    private var lastDetectionTimeMs = 0L
    private var useFloatFormat = true

    private val _currentPitch = MutableStateFlow<PitchDetector.PitchResult?>(null)
    val currentPitch: StateFlow<PitchDetector.PitchResult?> = _currentPitch

    private val _currentNoteEvent = MutableStateFlow<NoteEvent?>(null)
    val currentNoteEvent: StateFlow<NoteEvent?> = _currentNoteEvent

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun startListening() = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext
        if (_isListening.value) return@withContext
        resetSmoothing()

        try {
            val record = tryCreateRecord(AudioFormat.ENCODING_PCM_FLOAT)
            if (record != null) {
                useFloatFormat = true
                runRecordingLoop(record)
            } else {
                val record16 = tryCreateRecord(AudioFormat.ENCODING_PCM_16BIT)
                if (record16 != null) {
                    useFloatFormat = false
                    runRecordingLoop(record16)
                }
            }
        } catch (_: SecurityException) {
            // Permission revoked during recording
        } finally {
            releaseAudioRecord()
        }
    }

    private fun tryCreateRecord(encoding: Int): AudioRecord? {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, encoding)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) return null
        val bytesPerSample = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) Float.SIZE_BYTES else Short.SIZE_BYTES
        val bufferSize = maxOf(minBufferSize, BUFFER_SIZE_SAMPLES * bytesPerSample)
        return try {
            val record = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, encoding, bufferSize)
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                null
            } else record
        } catch (_: Exception) { null }
    }

    private suspend fun runRecordingLoop(record: AudioRecord) = withContext(Dispatchers.IO) {
        audioRecord = record
        record.startRecording()
        _isListening.value = true

        val floatBuffer = FloatArray(BUFFER_SIZE_SAMPLES)
        val shortBuffer = if (!useFloatFormat) ShortArray(BUFFER_SIZE_SAMPLES) else null

        while (isActive && _isListening.value) {
            val read = if (useFloatFormat) {
                record.read(floatBuffer, 0, BUFFER_SIZE_SAMPLES, AudioRecord.READ_BLOCKING)
            } else {
                val r = record.read(shortBuffer!!, 0, BUFFER_SIZE_SAMPLES)
                if (r > 0) {
                    for (i in 0 until r) floatBuffer[i] = shortBuffer[i].toFloat() / Short.MAX_VALUE
                }
                r
            }
            if (read > 0) {
                val now = System.currentTimeMillis()
                val detected = if (hasSignal(floatBuffer, read)) {
                    detectAdaptive(floatBuffer, read)
                } else {
                    resetSmoothing()
                    null
                }

                // Raw pitch for tuner/free mode (unchanged behavior)
                if (detected != null) {
                    _currentPitch.value = detected
                    lastDetectionTimeMs = now
                } else {
                    val elapsed = now - lastDetectionTimeMs
                    if (elapsed > HOLD_DURATION_MS) {
                        _currentPitch.value = null
                    }
                }

                // Smart note recognition (onset + hysteresis + noise gate)
                val noteEvent = noteRecognizer.processFrame(
                    floatBuffer, read, detected, now
                )
                if (noteEvent != null) {
                    _currentNoteEvent.value = noteEvent
                }
            }
        }
    }

    private fun detectAdaptive(buffer: FloatArray, samplesRead: Int): PitchDetector.PitchResult? {
        // Always try the full buffer first for best accuracy on low frequencies
        val fullWindow = buffer.copyOfRange(0, LOW_FREQ_BUFFER.coerceAtMost(samplesRead))
        val fullResult = pitchDetector.detect(fullWindow)

        if (fullResult != null) {
            return smoothPitch(fullResult)
        }

        // Fallback to smaller buffer for higher frequencies only
        val highWindow = buffer.copyOfRange(0, HIGH_FREQ_BUFFER.coerceAtMost(samplesRead))
        val quickResult = pitchDetector.detect(highWindow)
        return if (quickResult != null) smoothPitch(quickResult) else null
    }

    // --- Pitch smoothing: median filter + outlier rejection ---
    private val recentFrequencies = FloatArray(SMOOTH_WINDOW)
    private var smoothIndex = 0
    private var smoothFilled = 0
    private var lastStableFreq = 0f

    private fun smoothPitch(result: PitchDetector.PitchResult): PitchDetector.PitchResult? {
        val freq = result.frequency

        // Outlier rejection: if we have a stable frequency, reject extreme jumps
        if (lastStableFreq > 0f) {
            val ratio = freq / lastStableFreq
            if (ratio > 1.8f || ratio < 0.55f) {
                // Likely a harmonic or octave error — reject this frame
                return null
            }
        }

        // Add to rolling buffer
        recentFrequencies[smoothIndex] = freq
        smoothIndex = (smoothIndex + 1) % SMOOTH_WINDOW
        if (smoothFilled < SMOOTH_WINDOW) smoothFilled++

        // Compute median
        val sorted = recentFrequencies.copyOf(smoothFilled)
        sorted.sort()
        val medianFreq = sorted[smoothFilled / 2]
        lastStableFreq = medianFreq

        // Return result with median-smoothed frequency
        return PitchDetector.frequencyToNote(medianFreq, result.confidence)
    }

    fun stopListening() {
        _isListening.value = false
        resetSmoothing()
    }

    private fun resetSmoothing() {
        smoothIndex = 0
        smoothFilled = 0
        lastStableFreq = 0f
    }

    private fun releaseAudioRecord() {
        audioRecord?.let { record ->
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
            } catch (_: IllegalStateException) {
                // Already released
            }
        }
        audioRecord = null
        _isListening.value = false
    }

    private fun hasSignal(buffer: FloatArray, length: Int): Boolean {
        var sumSquares = 0.0
        for (i in 0 until length) {
            sumSquares += buffer[i] * buffer[i]
        }
        val rms = kotlin.math.sqrt(sumSquares / length)
        return rms > SIGNAL_RMS_THRESHOLD
    }
}
