package com.caminerin.guitartrainer.audio

/**
 * Adaptive noise gate that adjusts to ambient noise floor automatically.
 * Uses the 20th percentile of recent RMS values as the noise floor estimate.
 *
 * This replaces the fixed SIGNAL_RMS_THRESHOLD with a dynamic one that
 * works across different devices and environments.
 */
class AdaptiveNoiseGate(
    private val historySize: Int = 100,
    private val initialNoiseFloor: Float = 0.001f,
    private val openMultiplier: Float = 3.0f,
    private val closeMultiplier: Float = 1.8f,
    private val smoothingFactor: Float = 0.02f
) {

    data class GateState(
        val noiseFloor: Float,
        val isOpen: Boolean,
        val signalStrength: Float  // 0..1 how far above noise floor
    )

    private val rmsHistory = ArrayDeque<Float>()
    private var noiseFloor = initialNoiseFloor
    private var wasOpen = false

    fun process(rms: Float): GateState {
        rmsHistory.addLast(rms)
        if (rmsHistory.size > historySize) rmsHistory.removeFirst()

        // Update noise floor from percentile
        if (rmsHistory.size >= 5) {
            val sorted = rmsHistory.sorted()
            val p20 = sorted[(sorted.size * 0.2f).toInt().coerceIn(0, sorted.lastIndex)]
            noiseFloor = (1f - smoothingFactor) * noiseFloor + smoothingFactor * p20
            noiseFloor = noiseFloor.coerceAtLeast(initialNoiseFloor)
        }

        // Hysteresis: open threshold higher than close threshold
        val threshold = if (wasOpen) {
            noiseFloor * closeMultiplier
        } else {
            noiseFloor * openMultiplier
        }

        val isOpen = rms > threshold
        wasOpen = isOpen

        val signalStrength = if (noiseFloor > 0f) {
            ((rms - noiseFloor) / noiseFloor).coerceIn(0f, 10f) / 10f
        } else 0f

        return GateState(noiseFloor, isOpen, signalStrength)
    }

    fun reset() {
        rmsHistory.clear()
        noiseFloor = initialNoiseFloor
        wasOpen = false
    }
}
