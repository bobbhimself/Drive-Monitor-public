package com.bobbhimself.drivemonitor.sensors

import kotlin.math.abs
import kotlin.math.sqrt

class MotionProcessor(
    private val onCalibrationComplete: () -> Unit,
    private val onCalibrationFailed: (message: String) -> Unit,
    private val onMotionData: (longitudinalG: Float, lateralG: Float, verticalG: Float, timestampMillis: Long) -> Unit
) {

    companion object {
        private const val STANDARD_GRAVITY = 9.80665f
    }

    private var calibrating = true

    // Calibration sample storage (raw axes, G-converted)
    private val calibrationSamplesX = ArrayList<Float>(120)
    private val calibrationSamplesY = ArrayList<Float>(120)
    private val calibrationSamplesZ = ArrayList<Float>(120)
    private var calibrationStartNanos = 0L

    // Baselines (normalized axes — set during calibration finalization)
    private var baselineLateral = 0f
    private var baselineVertical = 0f
    private var baselineLongitudinal = 0f

    // EMA state (normalized axes — initialized to baselines after calibration)
    private var filteredLateral = 0f
    private var filteredVertical = 0f
    private var filteredLongitudinal = 0f

    fun processSample(x: Float, y: Float, z: Float, timestampNanos: Long) {
        if (calibrating) {
            collectCalibrationSample(x, y, z, timestampNanos)
        } else {
            processNormal(x, y, z, timestampNanos)
        }
    }

    private fun collectCalibrationSample(x: Float, y: Float, z: Float, timestampNanos: Long) {
        val xG = x / STANDARD_GRAVITY
        val yG = y / STANDARD_GRAVITY
        val zG = z / STANDARD_GRAVITY

        if (calibrationSamplesX.isEmpty()) {
            calibrationStartNanos = timestampNanos
        }

        calibrationSamplesX.add(xG)
        calibrationSamplesY.add(yG)
        calibrationSamplesZ.add(zG)

        val elapsedNanos = timestampNanos - calibrationStartNanos
        if (elapsedNanos >= ThresholdConfig.CALIBRATION_DURATION_MS * 1_000_000L) {
            finalizeCalibration()
        }
    }

    private fun finalizeCalibration() {
        calibrating = false

        val count = calibrationSamplesX.size

        // Step 1: minimum sample count
        if (count < ThresholdConfig.CALIBRATION_MIN_SAMPLES) {
            calibrationSamplesX.clear()
            calibrationSamplesY.clear()
            calibrationSamplesZ.clear()
            onCalibrationFailed(
                "Calibration failed: insufficient samples ($count collected, ${ThresholdConfig.CALIBRATION_MIN_SAMPLES} required)"
            )
            return
        }

        // Step 2: compute means and validate RMS stability per axis
        val meanX = meanOf(calibrationSamplesX)
        val meanY = meanOf(calibrationSamplesY)
        val meanZ = meanOf(calibrationSamplesZ)

        val rmsX = rmsDeviation(calibrationSamplesX, meanX)
        val rmsY = rmsDeviation(calibrationSamplesY, meanY)
        val rmsZ = rmsDeviation(calibrationSamplesZ, meanZ)

        if (rmsX > ThresholdConfig.CALIBRATION_STABILITY_RMS_G) {
            clearCalibrationSamples()
            onCalibrationFailed("Calibration failed: X-axis RMS too high (${rmsX}g)")
            return
        }
        if (rmsY > ThresholdConfig.CALIBRATION_STABILITY_RMS_G) {
            clearCalibrationSamples()
            onCalibrationFailed("Calibration failed: Y-axis RMS too high (${rmsY}g)")
            return
        }
        if (rmsZ > ThresholdConfig.CALIBRATION_STABILITY_RMS_G) {
            clearCalibrationSamples()
            onCalibrationFailed("Calibration failed: Z-axis RMS too high (${rmsZ}g)")
            return
        }

        // Step 3: validate max deviation from running mean per axis
        if (!validateMaxDeviation(calibrationSamplesX, "X") ||
            !validateMaxDeviation(calibrationSamplesY, "Y") ||
            !validateMaxDeviation(calibrationSamplesZ, "Z")
        ) {
            return
        }

        // Step 4: raw baselines (already computed as means above)
        // Step 5: convert to normalized-axis baselines
        baselineLateral = meanX.toFloat()
        baselineVertical = meanY.toFloat()
        baselineLongitudinal = (-meanZ).toFloat()

        // Step 6: initialize EMA state to baselines
        filteredLateral = baselineLateral
        filteredVertical = baselineVertical
        filteredLongitudinal = baselineLongitudinal

        // Step 7: clean up and signal success
        clearCalibrationSamples()
        onCalibrationComplete()
    }

    private fun processNormal(x: Float, y: Float, z: Float, timestampNanos: Long) {
        // 1. G conversion
        val xG = x / STANDARD_GRAVITY
        val yG = y / STANDARD_GRAVITY
        val zG = z / STANDARD_GRAVITY

        // 2-3. Axis mapping + sign normalization
        val lateral = xG
        val vertical = yG
        val longitudinal = -zG

        // 4. EMA filtering
        val alpha = ThresholdConfig.EMA_ALPHA
        val oneMinusAlpha = 1f - alpha
        filteredLateral = alpha * lateral + oneMinusAlpha * filteredLateral
        filteredVertical = alpha * vertical + oneMinusAlpha * filteredVertical
        filteredLongitudinal = alpha * longitudinal + oneMinusAlpha * filteredLongitudinal

        // 5. Baseline subtraction
        var correctedLateral = filteredLateral - baselineLateral
        var correctedVertical = filteredVertical - baselineVertical
        var correctedLongitudinal = filteredLongitudinal - baselineLongitudinal

        // 6. Deadband suppression
        if (abs(correctedLateral) < ThresholdConfig.DEADBAND_G) correctedLateral = 0f
        if (abs(correctedVertical) < ThresholdConfig.DEADBAND_G) correctedVertical = 0f
        if (abs(correctedLongitudinal) < ThresholdConfig.DEADBAND_G) correctedLongitudinal = 0f

        // 7. Output
        val timestampMillis = timestampNanos / 1_000_000
        onMotionData(correctedLongitudinal, correctedLateral, correctedVertical, timestampMillis)
    }

    private fun meanOf(samples: List<Float>): Double {
        var sum = 0.0
        for (sample in samples) {
            sum += sample
        }
        return sum / samples.size
    }

    private fun rmsDeviation(samples: List<Float>, mean: Double): Float {
        var sumSquared = 0.0
        for (sample in samples) {
            val diff = sample - mean
            sumSquared += diff * diff
        }
        return sqrt(sumSquared / samples.size).toFloat()
    }

    private fun validateMaxDeviation(samples: List<Float>, axisName: String): Boolean {
        var runningSum = 0.0
        for (i in samples.indices) {
            runningSum += samples[i]
            val runningMean = runningSum / (i + 1)
            if (abs(samples[i] - runningMean) > ThresholdConfig.CALIBRATION_MAX_DEVIATION_G) {
                clearCalibrationSamples()
                onCalibrationFailed(
                    "Calibration failed: $axisName-axis sample deviated too far from running mean"
                )
                return false
            }
        }
        return true
    }

    private fun clearCalibrationSamples() {
        calibrationSamplesX.clear()
        calibrationSamplesY.clear()
        calibrationSamplesZ.clear()
    }
}
