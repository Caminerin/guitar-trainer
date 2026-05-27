package com.caminerin.guitartrainer.audio

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * YIN pitch detection algorithm.
 *
 * Detects the fundamental frequency (pitch) of a monophonic audio signal.
 * Based on: de Cheveigné & Kawahara (2002) "YIN, a fundamental frequency
 * estimator for speech and music".
 */
class PitchDetector(
    private val sampleRate: Int,
    private val threshold: Double = 0.20
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
        val halfLen = buffer.size / 2
        val diff = differenceFunction(buffer, halfLen)
        val cmndf = cumulativeMeanNormalizedDifference(diff, halfLen)
        val tauEstimate = absoluteThreshold(cmndf, halfLen) ?: return null
        val refinedTau = parabolicInterpolation(cmndf, tauEstimate, halfLen)

        val frequency = sampleRate.toFloat() / refinedTau
        if (frequency < 50f || frequency > 2000f) return null

        val confidence = 1f - (cmndf[tauEstimate].coerceIn(0.0, 1.0)).toFloat()
        return frequencyToNote(frequency, confidence)
    }

    private fun differenceFunction(buffer: FloatArray, halfLen: Int): DoubleArray {
        val diff = DoubleArray(halfLen)
        for (tau in 1 until halfLen) {
            var sum = 0.0
            for (i in 0 until halfLen) {
                val delta = (buffer[i] - buffer[i + tau]).toDouble()
                sum += delta * delta
            }
            diff[tau] = sum
        }
        return diff
    }

    private fun cumulativeMeanNormalizedDifference(
        diff: DoubleArray,
        halfLen: Int
    ): DoubleArray {
        val cmndf = DoubleArray(halfLen)
        cmndf[0] = 1.0
        var runningSum = 0.0
        for (tau in 1 until halfLen) {
            runningSum += diff[tau]
            cmndf[tau] = if (runningSum != 0.0) {
                diff[tau] * tau / runningSum
            } else {
                1.0
            }
        }
        return cmndf
    }

    private fun absoluteThreshold(cmndf: DoubleArray, halfLen: Int): Int? {
        var tau = 2
        while (tau < halfLen) {
            if (cmndf[tau] < threshold) {
                while (tau + 1 < halfLen && cmndf[tau + 1] < cmndf[tau]) {
                    tau++
                }
                return tau
            }
            tau++
        }
        return null
    }

    private fun parabolicInterpolation(
        cmndf: DoubleArray,
        tau: Int,
        halfLen: Int
    ): Float {
        if (tau < 1 || tau >= halfLen - 1) return tau.toFloat()

        val s0 = cmndf[tau - 1]
        val s1 = cmndf[tau]
        val s2 = cmndf[tau + 1]

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
    }
}
