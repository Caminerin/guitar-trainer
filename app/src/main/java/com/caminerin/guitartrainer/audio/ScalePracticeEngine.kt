package com.caminerin.guitartrainer.audio

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Context about the current scale practice exercise.
 * The engine uses this to make smarter decisions about what note
 * the player intended.
 */
data class ScalePracticeContext(
    val rootNoteIndex: Int,
    val scaleNoteIndices: Set<Int>,
    val expectedNoteIndex: Int,
    val previousNoteIndex: Int?,
    val nextNoteIndex: Int?,
    val allowedMidiNotes: Set<Int>?,       // exact MIDI notes possible in position
    val allowedMidiRange: IntRange? = null  // min..max MIDI in position
)

enum class ScaleJudgement {
    EXPECTED,
    PREVIOUS_STILL_RINGING,
    NEXT_NOTE_EARLY,
    WRONG_SCALE_NOTE,
    OUT_OF_SCALE_NOTE,
    UNCERTAIN
}

data class ScaleEvaluation(
    val judgement: ScaleJudgement,
    val noteEvent: SegmentedNoteEvent,
    val correctedOctave: Int
)

/**
 * Scale practice engine: processes raw audio frames through the full pipeline
 * (noise gate → onset → MPM pitch → segmenter) and evaluates detected notes
 * against the exercise context.
 *
 * Architecture:
 *   AudioRecord reads buffer
 *   → AdaptiveNoiseGate decides if there's signal
 *   → SimpleOnsetDetector detects pluck
 *   → PitchDetector (MPM) estimates frequency
 *   → GuitarNoteSegmenter waits for stability after attack
 *   → ScalePracticeEngine.evaluate() compares against expected note
 */
class ScalePracticeEngine {

    private val noiseGate = AdaptiveNoiseGate()
    private val onsetDetector = SimpleOnsetDetector()
    private val segmenter = GuitarNoteSegmenter()

    /**
     * Process a raw audio frame. Returns a ScaleEvaluation if a note was
     * confirmed, null if still listening/collecting.
     */
    fun processFrame(
        buffer: FloatArray,
        samplesRead: Int,
        pitchResult: PitchDetector.PitchResult?,
        timestampMs: Long,
        context: ScalePracticeContext?
    ): ScaleEvaluation? {
        val rms = computeRms(buffer, samplesRead)

        // Adaptive noise gate
        val gateState = noiseGate.process(rms)
        if (!gateState.isOpen) return null

        // Onset detection
        val isOnset = onsetDetector.process(rms, timestampMs, gateState.noiseFloor)

        // Build pitch frame
        val isVoiced = pitchResult != null && pitchResult.confidence >= 0.50f
        val frame = PitchFrame(
            timestampMs = timestampMs,
            frequencyHz = pitchResult?.frequency,
            noteIndex = pitchResult?.noteIndex,
            octave = pitchResult?.octave,
            centsOff = pitchResult?.centsOff,
            confidence = pitchResult?.confidence ?: 0f,
            rms = rms,
            isVoiced = isVoiced,
            isOnset = isOnset
        )

        // Note segmentation (waits for stability after attack)
        val noteEvent = segmenter.process(frame) ?: return null

        // No context → just return the event with no evaluation
        if (context == null) {
            return ScaleEvaluation(
                ScaleJudgement.UNCERTAIN, noteEvent, noteEvent.octave
            )
        }

        // Evaluate against exercise context
        return evaluate(noteEvent, context)
    }

    /**
     * Evaluate a segmented note against the scale practice context.
     * Applies octave correction using fretboard position constraints.
     */
    fun evaluate(event: SegmentedNoteEvent, context: ScalePracticeContext): ScaleEvaluation {
        val detected = event.noteIndex
        val correctedOctave = correctOctave(event, context)

        val judgement = when {
            event.confidence < 0.55f -> ScaleJudgement.UNCERTAIN
            detected == context.expectedNoteIndex -> ScaleJudgement.EXPECTED
            detected == context.previousNoteIndex -> ScaleJudgement.PREVIOUS_STILL_RINGING
            detected == context.nextNoteIndex -> ScaleJudgement.NEXT_NOTE_EARLY
            detected in context.scaleNoteIndices -> ScaleJudgement.WRONG_SCALE_NOTE
            else -> ScaleJudgement.OUT_OF_SCALE_NOTE
        }

        return ScaleEvaluation(judgement, event, correctedOctave)
    }

    /**
     * Correct octave errors using fretboard position constraints.
     * If the detected pitch class matches expected but the octave is wrong
     * for the current position, try shifting to a plausible octave.
     */
    private fun correctOctave(event: SegmentedNoteEvent, context: ScalePracticeContext): Int {
        val detectedMidi = (event.octave + 1) * 12 + event.noteIndex
        val allowedMidi = context.allowedMidiNotes

        // If exact MIDI notes are known, pick the closest allowed one
        if (allowedMidi != null && allowedMidi.isNotEmpty()) {
            // Only correct if detected pitch class is in the scale
            if (event.noteIndex in context.scaleNoteIndices) {
                val sameClassInRange = allowedMidi.filter { it % 12 == event.noteIndex }
                if (sameClassInRange.isNotEmpty()) {
                    val closest = sameClassInRange.minByOrNull { abs(it - detectedMidi) }!!
                    return (closest / 12) - 1
                }
            }
        }

        // Fallback: use MIDI range
        val range = context.allowedMidiRange
        if (range != null && detectedMidi !in range) {
            var bestOctave = event.octave
            var bestDist = abs(detectedMidi - (range.first + range.last) / 2)
            for (shift in -2..2) {
                val tryOctave = event.octave + shift
                val tryMidi = (tryOctave + 1) * 12 + event.noteIndex
                if (tryMidi in range) {
                    val dist = abs(tryMidi - (range.first + range.last) / 2)
                    if (dist < bestDist) {
                        bestDist = dist
                        bestOctave = tryOctave
                    }
                }
            }
            return bestOctave
        }

        return event.octave
    }

    fun reset() {
        noiseGate.reset()
        onsetDetector.reset()
        segmenter.reset()
    }

    companion object {
        private fun computeRms(buffer: FloatArray, length: Int): Float {
            var sumSq = 0.0
            for (i in 0 until length) {
                sumSq += buffer[i] * buffer[i]
            }
            return kotlin.math.sqrt(sumSq / length).toFloat()
        }
    }
}
