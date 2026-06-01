package com.caminerin.guitartrainer.audio

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Intelligent note recognition layer between raw pitch detection and the UI.
 *
 * Implements 7 improvements over raw frame-by-frame pitch comparison:
 * 1. Onset detection (detects when the player plucks a new string)
 * 2. Hysteresis (doesn't change note on a single stray frame)
 * 3. Transient skip (ignores first ~40ms after attack — pick noise)
 * 4. Exercise context (evaluates against expected notes, not all 12)
 * 5. Octave correction (E3 vs E4 doesn't matter if pitch class matches)
 * 6. Adaptive noise gate (adjusts to ambient noise automatically)
 * 7. NoteEvent model (clean events instead of raw per-frame data)
 */

// ── Models ──────────────────────────────────────────────────────────────

data class NoteEvent(
    val noteIndex: Int,       // 0-11 pitch class (C=0, C#=1, ... B=11)
    val octave: Int,
    val frequency: Float,
    val confidence: Float,
    val timestampMs: Long,
    val stableFrames: Int
)

data class ExerciseContext(
    val scaleNoteIndices: Set<Int>,   // valid pitch classes for this exercise
    val expectedNoteIndex: Int,       // the note we expect next (0-11)
    val previousNoteIndex: Int,       // the note that came before (-1 if none)
    val minMidi: Int = 28,            // E2 lowest guitar note
    val maxMidi: Int = 84             // C6 practical upper bound
)

enum class RecognitionResult {
    EXPECTED_NOTE,
    PREVIOUS_NOTE,
    WRONG_SCALE_NOTE,
    OUT_OF_SCALE_NOTE,
    NOISE,
    UNCERTAIN
}

data class EvaluatedNote(
    val event: NoteEvent,
    val result: RecognitionResult,
    val correctedOctave: Int    // octave after guitar-aware correction
)

// ── NoteRecognizer ──────────────────────────────────────────────────────

class NoteRecognizer {

    companion object {
        // Onset detection
        private const val ONSET_RMS_RATIO = 2.5f
        private const val MIN_ONSET_RMS = 0.008f

        // Transient skip: ignore this many ms after onset
        private const val TRANSIENT_SKIP_MS = 40L

        // Hysteresis: require this many consecutive stable frames
        private const val MIN_STABLE_FRAMES = 3

        // Adaptive noise gate
        private const val NOISE_FLOOR_HISTORY = 30
        private const val NOISE_GATE_MULTIPLIER = 2.8f
        private const val INITIAL_NOISE_FLOOR = 0.002f

        // Confidence
        private const val MIN_CONFIDENCE = 0.55f
    }

    // ── State ───────────────────────────────────────────────────────────

    // Onset detection state
    private var previousRms = 0f
    private var onsetDetectedMs = 0L
    private var inTransient = false

    // Hysteresis state
    private var currentStableNote = -1
    private var currentStableOctave = -1
    private var stableFrameCount = 0
    private var candidateNote = -1
    private var candidateOctave = -1
    private var candidateFrames = 0

    // Adaptive noise gate state
    private val rmsHistory = FloatArray(NOISE_FLOOR_HISTORY)
    private var rmsHistoryIndex = 0
    private var rmsHistoryFilled = 0
    private var noiseFloor = INITIAL_NOISE_FLOOR

    // Last emitted event (to avoid duplicates)
    private var lastEmittedNoteIndex = -1
    private var lastEmittedTimeMs = 0L

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Process a raw audio frame. Call this every time AudioProcessor reads a buffer.
     * Returns a NoteEvent if a stable note was recognized, null otherwise.
     *
     * @param buffer raw audio samples (normalized -1..1)
     * @param samplesRead number of valid samples in buffer
     * @param pitchResult result from PitchDetector (may be null if no pitch found)
     * @param timestampMs current time in milliseconds
     */
    fun processFrame(
        buffer: FloatArray,
        samplesRead: Int,
        pitchResult: PitchDetector.PitchResult?,
        timestampMs: Long
    ): NoteEvent? {
        val rms = computeRms(buffer, samplesRead)

        // ── 6. Adaptive noise gate ──
        updateNoiseFloor(rms)
        val effectiveThreshold = noiseFloor * NOISE_GATE_MULTIPLIER
        if (rms < effectiveThreshold) {
            // Silence — reset candidates but keep stable note for hold duration
            previousRms = rms
            candidateNote = -1
            candidateFrames = 0
            return null
        }

        // ── 1. Onset detection ──
        val isOnset = detectOnset(rms, timestampMs)

        // ── 3. Transient skip ──
        if (inTransient) {
            if (timestampMs - onsetDetectedMs < TRANSIENT_SKIP_MS) {
                previousRms = rms
                return null  // still in transient, skip this frame
            }
            inTransient = false
        }

        previousRms = rms

        // No pitch detected by YIN
        if (pitchResult == null || pitchResult.confidence < MIN_CONFIDENCE) {
            return null
        }

        val detectedNote = pitchResult.noteIndex
        val detectedOctave = pitchResult.octave

        // ── 2. Hysteresis ──
        return applyHysteresis(detectedNote, detectedOctave, pitchResult, timestampMs, isOnset)
    }

    /**
     * Evaluate a NoteEvent against the current exercise context.
     * Applies octave correction (point 5) and exercise-aware classification (point 4).
     */
    fun evaluate(event: NoteEvent, context: ExerciseContext): EvaluatedNote {
        val noteIndex = event.noteIndex

        // ── 5. Octave correction ──
        val correctedOctave = correctOctave(event, context)

        // ── 4. Exercise context ──
        val result = when {
            noteIndex == context.expectedNoteIndex -> RecognitionResult.EXPECTED_NOTE
            noteIndex == context.previousNoteIndex -> RecognitionResult.PREVIOUS_NOTE
            context.scaleNoteIndices.contains(noteIndex) -> RecognitionResult.WRONG_SCALE_NOTE
            else -> RecognitionResult.OUT_OF_SCALE_NOTE
        }

        return EvaluatedNote(event, result, correctedOctave)
    }

    /**
     * Reset all internal state. Call when starting a new exercise or switching modes.
     */
    fun reset() {
        previousRms = 0f
        onsetDetectedMs = 0L
        inTransient = false
        currentStableNote = -1
        currentStableOctave = -1
        stableFrameCount = 0
        candidateNote = -1
        candidateOctave = -1
        candidateFrames = 0
        rmsHistoryIndex = 0
        rmsHistoryFilled = 0
        noiseFloor = INITIAL_NOISE_FLOOR
        lastEmittedNoteIndex = -1
        lastEmittedTimeMs = 0L
    }

    // ── Private implementation ──────────────────────────────────────────

    private fun computeRms(buffer: FloatArray, length: Int): Float {
        var sumSq = 0.0
        for (i in 0 until length) {
            sumSq += buffer[i] * buffer[i]
        }
        return sqrt(sumSq / length).toFloat()
    }

    /**
     * Onset detection: a new note attack is a sharp rise in RMS energy.
     */
    private fun detectOnset(rms: Float, timestampMs: Long): Boolean {
        if (previousRms <= 0.0001f) return false
        val ratio = rms / previousRms
        val isOnset = ratio > ONSET_RMS_RATIO && rms > MIN_ONSET_RMS
        if (isOnset) {
            onsetDetectedMs = timestampMs
            inTransient = true
            // Reset hysteresis on new attack — allow note change
            candidateNote = -1
            candidateFrames = 0
        }
        return isOnset
    }

    /**
     * Hysteresis: require MIN_STABLE_FRAMES consecutive frames with the same note
     * before accepting a note change. On onset, reset and allow faster acceptance.
     */
    private fun applyHysteresis(
        detectedNote: Int,
        detectedOctave: Int,
        pitchResult: PitchDetector.PitchResult,
        timestampMs: Long,
        isOnset: Boolean
    ): NoteEvent? {
        // Same note as current stable → reinforce
        if (detectedNote == currentStableNote) {
            stableFrameCount++
            candidateNote = -1
            candidateFrames = 0
            return null  // already emitted this note
        }

        // Different note — track as candidate
        if (detectedNote == candidateNote && detectedOctave == candidateOctave) {
            candidateFrames++
        } else {
            candidateNote = detectedNote
            candidateOctave = detectedOctave
            candidateFrames = 1
        }

        // Accept candidate if stable enough
        val requiredFrames = if (isOnset) 2 else MIN_STABLE_FRAMES
        if (candidateFrames >= requiredFrames) {
            currentStableNote = candidateNote
            currentStableOctave = candidateOctave
            stableFrameCount = candidateFrames
            candidateNote = -1
            candidateFrames = 0

            // Avoid emitting the same note twice in rapid succession
            if (currentStableNote == lastEmittedNoteIndex &&
                timestampMs - lastEmittedTimeMs < 150L
            ) {
                return null
            }

            lastEmittedNoteIndex = currentStableNote
            lastEmittedTimeMs = timestampMs

            return NoteEvent(
                noteIndex = currentStableNote,
                octave = currentStableOctave,
                frequency = pitchResult.frequency,
                confidence = pitchResult.confidence,
                timestampMs = timestampMs,
                stableFrames = stableFrameCount
            )
        }

        return null
    }

    /**
     * Adaptive noise gate: track the ambient noise floor using a running
     * percentile of recent RMS values.
     */
    private fun updateNoiseFloor(rms: Float) {
        rmsHistory[rmsHistoryIndex] = rms
        rmsHistoryIndex = (rmsHistoryIndex + 1) % NOISE_FLOOR_HISTORY
        if (rmsHistoryFilled < NOISE_FLOOR_HISTORY) rmsHistoryFilled++

        if (rmsHistoryFilled >= 5) {
            // Use 20th percentile as noise floor estimate
            val sorted = rmsHistory.copyOf(rmsHistoryFilled)
            sorted.sort()
            val p20Index = (rmsHistoryFilled * 0.2f).toInt().coerceIn(0, rmsHistoryFilled - 1)
            noiseFloor = sorted[p20Index].coerceAtLeast(INITIAL_NOISE_FLOOR)
        }
    }

    /**
     * Octave correction: if the detected pitch class matches expected but the
     * octave seems wrong for the guitar position, correct it.
     */
    private fun correctOctave(event: NoteEvent, context: ExerciseContext): Int {
        val detectedMidi = noteToMidi(event.noteIndex, event.octave)

        // If within reasonable guitar range, keep as-is
        if (detectedMidi in context.minMidi..context.maxMidi) {
            return event.octave
        }

        // Try shifting octave to fit within range
        var bestOctave = event.octave
        var bestDist = abs(detectedMidi - (context.minMidi + context.maxMidi) / 2)
        for (shift in -2..2) {
            val tryOctave = event.octave + shift
            val tryMidi = noteToMidi(event.noteIndex, tryOctave)
            if (tryMidi in context.minMidi..context.maxMidi) {
                val dist = abs(tryMidi - (context.minMidi + context.maxMidi) / 2)
                if (dist < bestDist) {
                    bestDist = dist
                    bestOctave = tryOctave
                }
            }
        }
        return bestOctave
    }

    private fun noteToMidi(noteIndex: Int, octave: Int): Int {
        return (octave + 1) * 12 + noteIndex
    }
}
