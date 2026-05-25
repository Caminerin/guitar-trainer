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
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT
        private const val BUFFER_SIZE_SAMPLES = 4096
        private const val HOLD_DURATION_MS = 2000L
    }

    private val pitchDetector = PitchDetector(SAMPLE_RATE)
    private var audioRecord: AudioRecord? = null
    private var lastDetectionTimeMs = 0L

    private val _currentPitch = MutableStateFlow<PitchDetector.PitchResult?>(null)
    val currentPitch: StateFlow<PitchDetector.PitchResult?> = _currentPitch

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

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
        )
        val bufferSize = maxOf(
            minBufferSize,
            BUFFER_SIZE_SAMPLES * Float.SIZE_BYTES
        )

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            ).also { record ->
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    record.release()
                    return@withContext
                }

                record.startRecording()
                _isListening.value = true

                val buffer = FloatArray(BUFFER_SIZE_SAMPLES)
                while (isActive && _isListening.value) {
                    val read = record.read(
                        buffer, 0, BUFFER_SIZE_SAMPLES,
                        AudioRecord.READ_BLOCKING
                    )
                    if (read > 0 && hasSignal(buffer, read)) {
                        val detected = pitchDetector.detect(buffer)
                        if (detected != null) {
                            _currentPitch.value = detected
                            lastDetectionTimeMs = System.currentTimeMillis()
                        }
                    } else if (read > 0) {
                        val elapsed = System.currentTimeMillis() - lastDetectionTimeMs
                        if (elapsed > HOLD_DURATION_MS) {
                            _currentPitch.value = null
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            // Permission revoked during recording
        } finally {
            releaseAudioRecord()
        }
    }

    fun stopListening() {
        _isListening.value = false
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
        return rms > 0.01
    }
}
