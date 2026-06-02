package com.caminerin.guitartrainer.audio

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * MPM (McLeod Pitch Method) pitch detection algorithm.
 *
 * Designed specifically for musical instruments — finds the true fundamental
 * frequency by computing the Normalized Square Difference Function (NSDF) and
 * picking the first peak above a threshold relative to the global maximum.
 *
 * Advantages over YIN for guitar:
 * - Picks the FIRST strong NSDF peak → resolves to fundamental, not harmonics
 * - "Clarity" metric (peak height 0..1) is a reliable confidence measure
 * - Fewer octave errors on low guitar strings
 *
 * Based on: McLeod & Wyvill (2005) "A Smarter Way to Find Pitch"
 */
class PitchDetector(
    private val sampleRate: Int,
    private val smallCutoff: Double = 0.5,
    private val cutoff: Double = 0.93
) {

    data class PitchResult(
        val frequency: Float,
        val noteName: String,
        val noteIndex: Int,
        val octave: Int,
        val centsOff: Float,
        val confidence: Float
    ) {
        val fullNoteName: String get() = "$noteName$octave"
    }

    fun detect(buffer: FloatArray): PitchResult? {
        val n = buffer.size
        val nsdf = normalizedSquareDifference(buffer, n)

        // Find key maxima: peaks after positive zero crossings
        val peaks = findKeyMaxima(nsdf, n)
        if (peaks.isEmpty()) return null

        // Find the highest peak value (global max clarity)
        val globalMaxClarity = peaks.maxOf { nsdf[it] }
        if (globalMaxClarity < smallCutoff) return null

        // Pick the first peak above cutoff * globalMaxClarity (MPM's key insight)
        val threshold = cutoff * globalMaxClarity
        val selectedTau = peaks.firstOrNull { nsdf[it] >= threshold } ?: return null

        // Parabolic interpolation for sub-sample accuracy
        val refinedTau = parabolicInterpolation(nsdf, selectedTau, n)
        if (refinedTau <= 0f) return null

        val frequency = sampleRate.toFloat() / refinedTau
        if (frequency < 60f || frequency > 1500f) return null

        val clarity = nsdf[selectedTau].toFloat().coerceIn(0f, 1f)
        return frequencyToNote(frequency, clarity)
    }

    /**
     * Compute the Normalized Square Difference Function (NSDF).
     * NSDF(τ) = 2 * r(τ) / (m(τ))
     * where r(τ) is the autocorrelation and m(τ) is the normalizing term.
     * Range: -1 to 1. Peaks near 1 = strong periodicity at lag τ.
     */
    private fun normalizedSquareDifference(buffer: FloatArray, n: Int): DoubleArray {
        val nsdf = DoubleArray(n)
        for (tau in 0 until n) {
            var acf = 0.0   // autocorrelation at lag tau
            var m = 0.0     // normalizing energy term
            val limit = n - tau
            for (i in 0 until limit) {
                val xi = buffer[i].toDouble()
                val xj = buffer[i + tau].toDouble()
                acf += xi * xj
                m += xi * xi + xj * xj
            }
            nsdf[tau] = if (m > 0.0) 2.0 * acf / m else 0.0
        }
        return nsdf
    }

    /**
     * Find key maxima of the NSDF: local peaks that appear after the NSDF
     * crosses zero from negative to positive. These represent candidate
     * periods (the first strong one is usually the fundamental).
     */
    private fun findKeyMaxima(nsdf: DoubleArray, n: Int): List<Int> {
        val peaks = mutableListOf<Int>()
        val minTau = (sampleRate / 1500f).toInt().coerceAtLeast(2)  // ~1500 Hz max
        val maxTau = (sampleRate / 60f).toInt().coerceAtMost(n - 2) // ~60 Hz min

        var positiveZeroCrossing = false
        var peakTau = minTau
        var peakVal = Double.NEGATIVE_INFINITY

        for (tau in minTau..maxTau) {
            // Detect positive zero crossing
            if (nsdf[tau] > 0 && nsdf[tau - 1] <= 0) {
                positiveZeroCrossing = true
                peakTau = tau
                peakVal = nsdf[tau]
            }

            // Track the peak within this positive lobe
            if (positiveZeroCrossing && nsdf[tau] > peakVal) {
                peakTau = tau
                peakVal = nsdf[tau]
            }

            // End of positive lobe → save the peak
            if (positiveZeroCrossing && nsdf[tau] <= 0) {
                if (peakVal > smallCutoff) {
                    peaks.add(peakTau)
                }
                positiveZeroCrossing = false
                peakVal = Double.NEGATIVE_INFINITY
            }
        }

        // Capture last peak if NSDF stays positive
        if (positiveZeroCrossing && peakVal > smallCutoff) {
            peaks.add(peakTau)
        }

        return peaks
    }

    private fun parabolicInterpolation(nsdf: DoubleArray, tau: Int, n: Int): Float {
        if (tau < 1 || tau >= n - 1) return tau.toFloat()

        val s0 = nsdf[tau - 1]
        val s1 = nsdf[tau]
        val s2 = nsdf[tau + 1]

        val denominator = 2.0 * s1 - s2 - s0
        if (abs(denominator) < 1e-12) return tau.toFloat()

        val adjustment = (s2 - s0) / (2.0 * denominator)
        return if (abs(adjustment) < 1.0) {
            tau + adjustment.toFloat()
        } else {
            tau.toFloat()
        }
    }

    companion object {
        private const val A4_FREQUENCY = 440.0
        private const val SEMITONES_PER_OCTAVE = 12
        private val NOTE_NAMES = arrayOf(
            "C", "C#", "D", "D#", "E", "F",
            "F#", "G", "G#", "A", "A#", "B"
        )

        fun frequencyToNote(frequency: Float, confidence: Float = 1f): PitchResult {
            val semitonesFromA4 = SEMITONES_PER_OCTAVE *
                ln(frequency.toDouble() / A4_FREQUENCY) / ln(2.0)
            val roundedSemitones = semitonesFromA4.roundToInt()
            val centsOff = ((semitonesFromA4 - roundedSemitones) * 100).toFloat()

            val midiNote = 69 + roundedSemitones
            val noteIndex = ((midiNote % SEMITONES_PER_OCTAVE) +
                SEMITONES_PER_OCTAVE) % SEMITONES_PER_OCTAVE
            val octave = (midiNote / SEMITONES_PER_OCTAVE) - 1

            return PitchResult(
                frequency = frequency,
                noteName = NOTE_NAMES[noteIndex],
                noteIndex = noteIndex,
                octave = octave,
                centsOff = centsOff,
                confidence = confidence
            )
        }

        fun frequencyToMidi(freq: Float): Float {
            return (69f + 12f * (ln(freq / 440f) / ln(2f)))
        }
    }
}
