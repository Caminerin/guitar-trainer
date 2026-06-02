package com.caminerin.guitartrainer.audio

/**
 * Frame-level pitch data produced by the detector pipeline.
 * One PitchFrame per audio window (~23ms at hop=1024/44100Hz).
 */
data class PitchFrame(
    val timestampMs: Long,
    val frequencyHz: Float?,
    val noteIndex: Int?,
    val octave: Int?,
    val centsOff: Float?,
    val confidence: Float,
    val rms: Float,
    val isVoiced: Boolean,
    val isOnset: Boolean
)

/**
 * A confirmed note event: the pitch has been stable for enough time
 * after an onset. This is what the practice engine should evaluate.
 */
data class SegmentedNoteEvent(
    val noteIndex: Int,
    val octave: Int,
    val medianFrequencyHz: Float,
    val medianCents: Float,
    val confidence: Float,
    val startMs: Long,
    val endMs: Long,
    val stableFrames: Int
)

/**
 * Guitar note segmenter: waits for pitch stability after an onset (pluck)
 * before confirming a note. This is the key difference from raw frame-by-frame
 * detection — it treats the pick transient as noise and only emits a note
 * once enough consistent frames have accumulated.
 *
 * Flow:
 *   onset detected → ignore attack transient → collect stable frames
 *   → confirm when enough frames agree on the same pitch class
 */
class GuitarNoteSegmenter(
    private val attackIgnoreMs: Long = 45L,
    private val minStableMs: Long = 80L,
    private val minFrames: Int = 4,
    private val minConfidence: Float = 0.65f,
    private val windowMs: Long = 200L
) {

    private var activeOnsetMs: Long? = null
    private val candidateFrames = mutableListOf<PitchFrame>()
    private var lastEmittedNoteIndex = -1
    private var lastEmittedMs = 0L

    fun process(frame: PitchFrame): SegmentedNoteEvent? {
        // New onset: reset candidates and start collecting
        if (frame.isOnset) {
            activeOnsetMs = frame.timestampMs
            candidateFrames.clear()
            return null
        }

        val onsetMs = activeOnsetMs
        // No active onset and no previous context — need an onset first
        // But also allow "stable pitch" confirmation without onset for sustained notes
        if (onsetMs == null) {
            // Sustained pitch mode: if we get consistent voiced frames, accept them
            if (!frame.isVoiced || frame.frequencyHz == null || frame.noteIndex == null) {
                candidateFrames.clear()
                return null
            }
            candidateFrames.add(frame)
            candidateFrames.removeAll { frame.timestampMs - it.timestampMs > windowMs }
            return tryConfirm(frame.timestampMs, frame.timestampMs)
        }

        // Still in attack transient — skip
        if (frame.timestampMs - onsetMs < attackIgnoreMs) return null

        // Unvoiced frame — don't add but don't reset yet
        if (!frame.isVoiced || frame.frequencyHz == null || frame.noteIndex == null) {
            return null
        }

        candidateFrames.add(frame)
        // Sliding window: remove frames older than windowMs
        candidateFrames.removeAll { frame.timestampMs - it.timestampMs > windowMs }

        return tryConfirm(onsetMs, frame.timestampMs)
    }

    private fun tryConfirm(onsetMs: Long, currentMs: Long): SegmentedNoteEvent? {
        if (candidateFrames.size < minFrames) return null

        // Group by pitch class (noteIndex)
        val grouped = candidateFrames
            .filter { it.noteIndex != null }
            .groupBy { it.noteIndex!! }
        if (grouped.isEmpty()) return null

        val bestGroup = grouped.maxByOrNull { it.value.size } ?: return null
        val frames = bestGroup.value

        if (frames.size < minFrames) return null

        // Check temporal duration
        val duration = frames.last().timestampMs - frames.first().timestampMs
        if (duration < minStableMs) return null

        // Check average confidence
        val avgConf = frames.map { it.confidence }.average().toFloat()
        if (avgConf < minConfidence) return null

        // Compute median frequency
        val freqs = frames.mapNotNull { it.frequencyHz }.sorted()
        if (freqs.isEmpty()) return null
        val medianFreq = freqs[freqs.size / 2]

        // Compute median cents
        val cents = frames.mapNotNull { it.centsOff }.sorted()
        val medianCents = if (cents.isNotEmpty()) cents[cents.size / 2] else 0f

        // Determine octave by majority vote
        val octave = frames.mapNotNull { it.octave }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }?.key ?: return null

        val noteIndex = bestGroup.key

        // Avoid rapid re-emission of the same note
        if (noteIndex == lastEmittedNoteIndex && currentMs - lastEmittedMs < 120L) {
            return null
        }

        lastEmittedNoteIndex = noteIndex
        lastEmittedMs = currentMs

        // Clear state for next note
        activeOnsetMs = null
        candidateFrames.clear()

        return SegmentedNoteEvent(
            noteIndex = noteIndex,
            octave = octave,
            medianFrequencyHz = medianFreq,
            medianCents = medianCents,
            confidence = avgConf,
            startMs = onsetMs,
            endMs = currentMs,
            stableFrames = frames.size
        )
    }

    fun reset() {
        activeOnsetMs = null
        candidateFrames.clear()
        lastEmittedNoteIndex = -1
        lastEmittedMs = 0L
    }
}
