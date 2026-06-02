package com.caminerin.guitartrainer.audio

/**
 * Simple onset detector based on energy rise.
 * Detects when the player plucks a new string by looking for a sudden
 * increase in RMS energy above the noise floor.
 *
 * After onset, the caller should ignore ~45ms of transient (pick noise)
 * before evaluating pitch.
 */
class SimpleOnsetDetector(
    private val energyMultiplier: Float = 3.0f,
    private val riseMultiplier: Float = 1.5f,
    private val cooldownMs: Long = 90L
) {

    private var previousRms = 0f
    private var lastOnsetMs = 0L

    fun process(rms: Float, timestampMs: Long, noiseFloor: Float): Boolean {
        val delta = rms - previousRms
        previousRms = rms

        val enoughEnergy = rms > noiseFloor * energyMultiplier
        val suddenRise = delta > noiseFloor * riseMultiplier
        val cooldownOk = timestampMs - lastOnsetMs > cooldownMs

        return if (enoughEnergy && suddenRise && cooldownOk) {
            lastOnsetMs = timestampMs
            true
        } else {
            false
        }
    }

    fun reset() {
        previousRms = 0f
        lastOnsetMs = 0L
    }
}
