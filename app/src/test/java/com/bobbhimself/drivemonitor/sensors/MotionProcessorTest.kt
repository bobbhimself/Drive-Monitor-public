package com.bobbhimself.drivemonitor.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val G = 9.80665f

private data class MotionData(
    val longitudinalG: Float,
    val lateralG: Float,
    val verticalG: Float,
    val timestampMillis: Long
)

class MotionProcessorTest {

    /**
     * Feeds [count] identical (x, y, z) samples to [processor] evenly across the calibration
     * window plus 1 ns. The last sample's elapsed time crosses the CALIBRATION_DURATION_MS
     * threshold, triggering finalizeCalibration() on that call.
     */
    private fun feedCalibrationSamples(
        processor: MotionProcessor,
        count: Int,
        x: Float = 0f,
        y: Float = 0f,
        z: Float = 0f
    ) {
        val endNanos = ThresholdConfig.CALIBRATION_DURATION_MS * 1_000_000L + 1L
        for (i in 0 until count) {
            val t = i.toLong() * endNanos / (count - 1)
            processor.processSample(x, y, z, t)
        }
    }

    // 8.2.1
    @Test
    fun calibrationSuccess() {
        var completeCalled = false
        var failureMsg: String? = null
        val motionEvents = mutableListOf<MotionData>()

        val p = MotionProcessor(
            onCalibrationComplete = { completeCalled = true },
            onCalibrationFailed = { failureMsg = it },
            onMotionData = { lo, la, ve, ts -> motionEvents.add(MotionData(lo, la, ve, ts)) }
        )

        feedCalibrationSamples(p, 50)

        assertTrue("onCalibrationComplete should have fired", completeCalled)
        assertNull("onCalibrationFailed should not have fired", failureMsg)

        p.processSample(0f, 0f, 0f, 3_000_000_000L)

        assertEquals("onMotionData should fire after calibration", 1, motionEvents.size)
    }

    // 8.2.2
    @Test
    fun calibrationFailure_insufficientSamples() {
        var failureMsg: String? = null

        val p = MotionProcessor(
            onCalibrationComplete = { },
            onCalibrationFailed = { failureMsg = it },
            onMotionData = { _, _, _, _ -> }
        )

        // count < CALIBRATION_MIN_SAMPLES; last sample triggers finalization
        feedCalibrationSamples(p, ThresholdConfig.CALIBRATION_MIN_SAMPLES - 5)

        assertNotNull("onCalibrationFailed should have fired", failureMsg)
        assertTrue(
            "failure message should mention 'insufficient'",
            failureMsg!!.contains("insufficient")
        )
    }

    // 8.2.3
    @Test
    fun calibrationFailure_rmsTooHigh() {
        var failureMsg: String? = null

        val p = MotionProcessor(
            onCalibrationComplete = { },
            onCalibrationFailed = { failureMsg = it },
            onMotionData = { _, _, _, _ -> }
        )

        // Alternating ±0.1g on X — RMS = 0.1g >> CALIBRATION_STABILITY_RMS_G(0.02g)
        val endNanos = ThresholdConfig.CALIBRATION_DURATION_MS * 1_000_000L + 1L
        for (i in 0 until 50) {
            val t = i.toLong() * endNanos / 49
            val rawX = if (i % 2 == 0) 0.1f * G else -0.1f * G
            p.processSample(rawX, 0f, 0f, t)
        }

        assertNotNull("onCalibrationFailed should have fired", failureMsg)
        assertTrue(
            "failure message should mention RMS",
            failureMsg!!.contains("RMS too high")
        )
    }

    // 8.2.4
    @Test
    fun calibrationFailure_maxDeviationExceeded() {
        var failureMsg: String? = null

        val p = MotionProcessor(
            onCalibrationComplete = { },
            onCalibrationFailed = { failureMsg = it },
            onMotionData = { _, _, _, _ -> }
        )

        // 49 zero samples then 1 spike of 0.06g on X
        // RMS X ≈ 0.0083g < 0.02g → passes RMS check
        // validateMaxDeviation at index 49: abs(0.06 - 0.0012) = 0.0588 > 0.05 → fail
        val endNanos = ThresholdConfig.CALIBRATION_DURATION_MS * 1_000_000L + 1L
        for (i in 0 until 50) {
            val t = i.toLong() * endNanos / 49
            val rawX = if (i < 49) 0f else 0.06f * G
            p.processSample(rawX, 0f, 0f, t)
        }

        assertNotNull("onCalibrationFailed should have fired", failureMsg)
        assertTrue(
            "failure message should mention deviation",
            failureMsg!!.contains("deviated too far")
        )
    }

    // 8.2.5
    @Test
    fun axisMappingAndSignNormalization() {
        val motionEvents = mutableListOf<MotionData>()

        val p = MotionProcessor(
            onCalibrationComplete = { },
            onCalibrationFailed = { },
            onMotionData = { lo, la, ve, ts -> motionEvents.add(MotionData(lo, la, ve, ts)) }
        )

        feedCalibrationSamples(p, 50)
        motionEvents.clear()

        // x=+10g raw: lateral = xG → should be positive; others zero (deadband suppresses 0)
        p.processSample(10f * G, 0f, 0f, 3_000_000_000L)
        val afterX = motionEvents.last()
        assertTrue("x maps to lateralG (positive)", afterX.lateralG > 0f)
        assertEquals("x does not affect longitudinalG", 0f, afterX.longitudinalG, 0.001f)
        assertEquals("x does not affect verticalG", 0f, afterX.verticalG, 0.001f)

        // z=+10g raw over many samples: longitudinal = -zG → must converge to negative
        for (i in 0 until 50) {
            p.processSample(0f, 0f, 10f * G, 4_000_000_000L + i)
        }
        val afterZ = motionEvents.last()
        assertTrue("z maps to longitudinalG with sign inversion (negative)", afterZ.longitudinalG < 0f)
    }

    // 8.2.6
    @Test
    fun emaFiltering() {
        val motionEvents = mutableListOf<MotionData>()

        val p = MotionProcessor(
            onCalibrationComplete = { },
            onCalibrationFailed = { },
            onMotionData = { lo, la, ve, ts -> motionEvents.add(MotionData(lo, la, ve, ts)) }
        )

        // Calibrate at zero: filteredLateral initialized to 0
        feedCalibrationSamples(p, 50)
        motionEvents.clear()

        // Single step with x = +1g raw
        // EMA: filteredLateral = 0.20 * 1.0 + 0.80 * 0.0 = 0.20g (not instant jump to 1g)
        p.processSample(1f * G, 0f, 0f, 3_000_000_000L)

        assertEquals(1, motionEvents.size)
        assertEquals(
            "EMA step 1 should yield alpha * input = 0.20 * 1g = 0.2f",
            0.2f,
            motionEvents[0].lateralG,
            0.001f
        )
    }

    // 8.2.7
    @Test
    fun baselineSubtraction() {
        val motionEvents = mutableListOf<MotionData>()

        val p = MotionProcessor(
            onCalibrationComplete = { },
            onCalibrationFailed = { },
            onMotionData = { lo, la, ve, ts -> motionEvents.add(MotionData(lo, la, ve, ts)) }
        )

        // Calibrate at 0.5g on X: all 50 samples identical → RMS=0, deviation=0 → success
        // baselineLateral = 0.5g; filteredLateral initialized to 0.5g
        feedCalibrationSamples(p, 50, x = 0.5f * G)
        motionEvents.clear()

        // 30 post-calibration samples at x=0.7g
        // EMA converges from 0.5g toward 0.7g; after 30 steps ≈ 0.6998g
        // corrected = 0.6998 - 0.5 = 0.1998g ≈ 0.2g (not 0.7g)
        for (i in 0 until 30) {
            p.processSample(0.7f * G, 0f, 0f, 3_000_000_000L + i)
        }

        assertEquals(
            "lateralG should be ~0.2g (0.7g filtered − 0.5g baseline), not 0.7g",
            0.2f,
            motionEvents.last().lateralG,
            0.02f
        )
    }

    // 8.2.8
    @Test
    fun deadbandSuppression() {
        val motionEvents = mutableListOf<MotionData>()

        val p = MotionProcessor(
            onCalibrationComplete = { },
            onCalibrationFailed = { },
            onMotionData = { lo, la, ve, ts -> motionEvents.add(MotionData(lo, la, ve, ts)) }
        )

        feedCalibrationSamples(p, 50)
        motionEvents.clear()

        // x=0.01g raw: EMA step 1 → filteredLateral = 0.2 * 0.01g = 0.002g
        // 0.002g < DEADBAND_G(0.03g) → clamped to literal 0f
        p.processSample(0.01f * G, 0f, 0f, 3_000_000_000L)

        assertEquals("onMotionData should fire even when output is deadbanded", 1, motionEvents.size)
        assertEquals("lateralG below deadband should be exactly 0f", 0f, motionEvents[0].lateralG)
        assertEquals("longitudinalG should be 0f", 0f, motionEvents[0].longitudinalG)
        assertEquals("verticalG should be 0f", 0f, motionEvents[0].verticalG)
    }
}
