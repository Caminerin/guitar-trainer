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
        private const val SMOOTH_WINDOW = 5
    }

    private val pitchDetector = PitchDetector(SAMPLE_RATE)
    val noteRecognizer = NoteRecognizer()
    val scalePracticeEngine = ScalePracticeEngine()
    private var audioRecord: AudioRecord? = null
    private var lastDetectionTimeMs = 0L
    private var useFloatFormat = true

    // --- Tuner/free mode: smoothed pitch for display ---
    private val _currentPitch = MutableStateFlow<PitchDetector.PitchResult?>(null)
    val currentPitch: StateFlow<PitchDetector.PitchResult?> = _currentPitch

    // --- Legacy NoteEvent for backward compat (tuner, other modes) ---
    private val _currentNoteEvent = MutableStateFlow<NoteEvent?>(null)
    val currentNoteEvent: StateFlow<NoteEvent?> = _currentNoteEvent

    // --- Scale practice: evaluated events from the full pipeline ---
    private val _currentScaleEvaluation = MutableStateFlow<ScaleEvaluation?>(null)
    val currentScaleEvaluation: StateFlow<ScaleEvaluation?> = _currentScaleEvaluation

    // --- Exercise context: set by CagedPracticeScreen, read by engine ---
    var practiceContext: ScalePracticeContext? = null

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
        } catch (e: SecurityException) {
            android.util.Log.w("AudioProcessor", "Mic permission denied", e)
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

                // --- RAW MPM DETECTION ---
                val rawResult = pitchDetector.detect(floatBuffer.copyOfRange(0, read))

                // --- TUNER PATH: smoothed pitch for display ---
                val smoothed = if (rawResult != null) smoothPitch(rawResult) else null
                if (smoothed != null) {
                    _currentPitch.value = smoothed
                    lastDetectionTimeMs = now
                } else {
                    val elapsed = now - lastDetectionTimeMs
                    if (elapsed > HOLD_DURATION_MS) {
                        _currentPitch.value = null
                    }
                }

                // --- LEGACY NOTE RECOGNIZER (for tuner/other modes) ---
                val noteEvent = noteRecognizer.processFrame(
                    floatBuffer, read, smoothed, now
                )
                if (noteEvent != null) {
                    _currentNoteEvent.value = noteEvent
                }

                // --- SCALE PRACTICE PATH: full pipeline ---
                val scaleEval = scalePracticeEngine.processFrame(
                    floatBuffer, read, rawResult, now, practiceContext
                )
                if (scaleEval != null) {
                    _currentScaleEvaluation.value = scaleEval
                }
            }
        }
    }

    // --- Pitch smoothing for tuner mode: median filter ---
    private val recentFrequencies = FloatArray(SMOOTH_WINDOW)
    private var smoothIndex = 0
    private var smoothFilled = 0
    private var lastStableFreq = 0f

    private fun smoothPitch(result: PitchDetector.PitchResult): PitchDetector.PitchResult? {
        val freq = result.frequency

        // Mild outlier rejection for tuner stability
        // Widened range to allow natural string changes (e.g. A2→E2 = 0.75x)
        if (lastStableFreq > 0f) {
            val ratio = freq / lastStableFreq
            if (ratio > 3.0f || ratio < 0.33f) return null
        }

        recentFrequencies[smoothIndex] = freq
        smoothIndex = (smoothIndex + 1) % SMOOTH_WINDOW
        if (smoothFilled < SMOOTH_WINDOW) smoothFilled++

        val sorted = recentFrequencies.copyOf(smoothFilled)
        sorted.sort()
        val medianFreq = sorted[smoothFilled / 2]
        lastStableFreq = medianFreq

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
}
