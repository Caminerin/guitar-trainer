package com.caminerin.guitartrainer.audio

/**
 * Helper to compute which MIDI notes are physically possible in a given
 * fretboard position. Used by ScalePracticeEngine to reject implausible
 * octaves and filter detections.
 */
object FretboardConstraint {

    // Standard guitar tuning: string 6 (low E) to string 1 (high E)
    // Index 0 = string 1 (high E = MIDI 64), Index 5 = string 6 (low E = MIDI 40)
    val STANDARD_TUNING_MIDI = intArrayOf(64, 59, 55, 50, 45, 40)

    /**
     * Compute all MIDI notes playable in a scale within a fret position.
     *
     * @param minFret start of position (inclusive)
     * @param maxFret end of position (inclusive)
     * @param scaleNoteIndices pitch classes in the scale (0-11)
     * @param tuning MIDI notes for open strings (default: standard tuning)
     * @return set of exact MIDI note numbers possible in this position
     */
    fun allowedMidiNotes(
        minFret: Int,
        maxFret: Int,
        scaleNoteIndices: Set<Int>,
        tuning: IntArray = STANDARD_TUNING_MIDI
    ): Set<Int> {
        val result = mutableSetOf<Int>()
        for (openMidi in tuning) {
            // Include open string (fret 0) if it's before the position
            val startFret = if (minFret <= 0) 0 else minFret
            for (fret in startFret..maxFret) {
                val midi = openMidi + fret
                if (midi % 12 in scaleNoteIndices) {
                    result.add(midi)
                }
            }
        }
        return result
    }

    /**
     * Get the MIDI range (min..max) for a fretboard position.
     */
    fun midiRange(
        minFret: Int,
        maxFret: Int,
        tuning: IntArray = STANDARD_TUNING_MIDI
    ): IntRange {
        val minMidi = tuning.minOrNull()!! + minFret.coerceAtLeast(0)
        val maxMidi = tuning.maxOrNull()!! + maxFret
        return minMidi..maxMidi
    }
}
