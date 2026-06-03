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
    private val smallCutoff: Double = 0.3,
    private val cutoff: Double = 0.90
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
        if (frequency < 50f || frequency > 1500f) return null

        val clarity = nsdf[selectedTau].toFloat().coerceIn(0f, 1f)
        return frequencyToNote(frequency, clarity)
    }

    /**
     * Compute the Normalized Square Difference Function (NSDF) using FFT.
     * NSDF(τ) = 2 * r(τ) / (m(τ))
     * where r(τ) is the autocorrelation and m(τ) is the normalizing term.
     *
     * The autocorrelation r(τ) is computed via FFT: r = IFFT(|FFT(x)|²),
     * giving O(n log n) complexity instead of O(n²).
     * The normalizing term m(τ) is computed incrementally in O(n).
     */
    private fun normalizedSquareDifference(buffer: FloatArray, n: Int): DoubleArray {
        // Compute autocorrelation via FFT (zero-padded to avoid circular artifacts)
        val fftSize = nextPowerOf2(2 * n)
        val re = DoubleArray(fftSize)
        val im = DoubleArray(fftSize)
        for (i in 0 until n) re[i] = buffer[i].toDouble()

        fft(re, im, false)

        // |FFT(x)|² (power spectrum)
        for (i in 0 until fftSize) {
            re[i] = re[i] * re[i] + im[i] * im[i]
            im[i] = 0.0
        }

        fft(re, im, true)
        // re[tau] now contains the unnormalized autocorrelation r(tau)

        // Build normalizing term m(τ) incrementally:
        // m(τ) = m(τ-1) - x[τ-1]² - x[n-τ]²
        val nsdf = DoubleArray(n)
        var m = 0.0
        for (i in 0 until n) m += buffer[i].toDouble() * buffer[i].toDouble()
        m *= 2.0 // m(0) = 2 * Σ x[i]²

        for (tau in 0 until n) {
            nsdf[tau] = if (m > 0.0) 2.0 * re[tau] / m else 0.0
            // Update m for next tau
            if (tau < n - 1) {
                val xt = buffer[tau].toDouble()
                val xn = buffer[n - 1 - tau].toDouble()
                m -= xt * xt + xn * xn
            }
        }
        return nsdf
    }

    /** Next power of 2 >= n */
    private fun nextPowerOf2(n: Int): Int {
        var v = 1
        while (v < n) v = v shl 1
        return v
    }

    /** In-place Cooley-Tukey FFT (or inverse FFT when inverse=true) */
    private fun fft(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
        val n = re.size
        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var tmp = re[i]; re[i] = re[j]; re[j] = tmp
                tmp = im[i]; im[i] = im[j]; im[j] = tmp
            }
        }
        // FFT butterflies
        var len = 2
        while (len <= n) {
            val angle = 2.0 * Math.PI / len * if (inverse) -1.0 else 1.0
            val wRe = Math.cos(angle)
            val wIm = Math.sin(angle)
            var i = 0
            while (i < n) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * curRe - im[i + k + len / 2] * curIm
                    val vIm = re[i + k + len / 2] * curIm + im[i + k + len / 2] * curRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm
                    val newCurRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = newCurRe
                }
                i += len
            }
            len = len shl 1
        }
        if (inverse) {
            val invN = 1.0 / n
            for (i in 0 until n) {
                re[i] *= invN
                im[i] *= invN
            }
        }
    }

    /**
     * Find key maxima of the NSDF: local peaks that appear after the NSDF
     * crosses zero from negative to positive. These represent candidate
     * periods (the first strong one is usually the fundamental).
     */
    private fun findKeyMaxima(nsdf: DoubleArray, n: Int): List<Int> {
        val peaks = mutableListOf<Int>()
        val minTau = (sampleRate / 1500f).toInt().coerceAtLeast(2)  // ~1500 Hz max
        val maxTau = (sampleRate / 50f).toInt().coerceAtMost(n - 2) // ~50 Hz min (covers detuned low E)

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
