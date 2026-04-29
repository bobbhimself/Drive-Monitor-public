package com.bobbhimself.drivemonitor.sensors

import com.bobbhimself.drivemonitor.data.model.AlertSeverity
import com.bobbhimself.drivemonitor.data.model.MotionCategory
import com.bobbhimself.drivemonitor.data.model.TripEvent
import org.junit.Assert.assertEquals
import org.junit.Test

private const val G = 9.80665f
private const val STEP_NS = 10_000_000L      // 10ms in nanoseconds
private val START_NS = ThresholdConfig.CALIBRATION_DURATION_MS * 1_000_000L + 10_000_000L  // 10ms gap after calibration end

private data class Pipeline(
    val processor: MotionProcessor,
    val finalized: MutableList<TripEvent>
)

class SyntheticPipelineTest {

    /**
     * Feeds [count] identical (x, y, z) samples to [processor] evenly across the calibration
     * window plus 1 ns. The last sample's elapsed time crosses the CALIBRATION_DURATION_MS
     * threshold, triggering finalizeCalibration() on that call. Copied from MotionProcessorTest.
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

    /**
     * Builds a fully wired MotionProcessor → ThresholdEvaluator pipeline with calibration
     * already complete (50 stable zero samples). Returns the processor and the list that
     * accumulates all finalized events.
     */
    private fun buildPipeline(): Pipeline {
        val finalized = mutableListOf<TripEvent>()
        val evaluator = ThresholdEvaluator(
            onEventStarted   = {},
            onEventEscalated = {},
            onEventFinalized = { finalized.add(it) }
        )
        val processor = MotionProcessor(
            onCalibrationComplete = {},
            onCalibrationFailed   = { msg -> throw AssertionError("Test setup: calibration failed unexpectedly: $msg") },
            onMotionData          = { lo, la, ve, ts -> evaluator.process(lo, la, ve, ts) }
        )
        feedCalibrationSamples(processor, 50)
        return Pipeline(processor, finalized)
    }

    /**
     * Feeds [count] post-calibration samples at [stepNanos] intervals starting at [startNanos].
     * Returns the next start timestamp (startNanos + count * stepNanos) for chaining.
     */
    private fun feedSamplesThrough(
        processor: MotionProcessor,
        x: Float = 0f,
        y: Float = 0f,
        z: Float = 0f,
        startNanos: Long,
        count: Int,
        stepNanos: Long = STEP_NS
    ): Long {
        for (i in 0 until count) {
            processor.processSample(x, y, z, startNanos + i * stepNanos)
        }
        return startNanos + count * stepNanos
    }

    // 8.4.2
    @Test
    fun cleanBrakingEvent() {
        val (processor, finalized) = buildPipeline()
        // z=+0.80g raw → longitudinalG converges to -0.80g (braking alert level)
        // Caution exceeded at ~30ms; ACTIVE at ~230ms; feed for 400ms to be safely past ACTIVE
        var t = feedSamplesThrough(processor, z = +0.80f * G, startNanos = START_NS, count = 40)
        // Zero → EMA drops below caution in ~50ms; quiet window (750ms) expires in ~800ms
        feedSamplesThrough(processor, startNanos = t, count = 90)

        assertEquals("exactly one braking event", 1, finalized.size)
        assertEquals("category is BRAKING", MotionCategory.BRAKING, finalized[0].category)
        assertEquals("severity is ALERT", AlertSeverity.ALERT, finalized[0].severity)
    }

    // 8.4.3
    @Test
    fun cleanAccelerationEvent() {
        val (processor, finalized) = buildPipeline()
        // z=-0.80g raw → longitudinalG converges to +0.80g (acceleration alert level)
        var t = feedSamplesThrough(processor, z = -0.80f * G, startNanos = START_NS, count = 40)
        feedSamplesThrough(processor, startNanos = t, count = 90)

        assertEquals("exactly one acceleration event", 1, finalized.size)
        assertEquals("category is ACCELERATION", MotionCategory.ACCELERATION, finalized[0].category)
        assertEquals("severity is ALERT", AlertSeverity.ALERT, finalized[0].severity)
    }

    // 8.4.4
    @Test
    fun sustainedTurning() {
        val (processor, finalized) = buildPipeline()
        // x=+0.80g raw → lateralG converges to +0.80g (turning alert level)
        var t = feedSamplesThrough(processor, x = +0.80f * G, startNanos = START_NS, count = 40)
        feedSamplesThrough(processor, startNanos = t, count = 90)

        assertEquals("exactly one turning event", 1, finalized.size)
        assertEquals("category is TURNING", MotionCategory.TURNING, finalized[0].category)
        assertEquals("severity is ALERT", AlertSeverity.ALERT, finalized[0].severity)
    }

    // 8.4.5
    @Test
    fun thresholdChatter_noEventSpam() {
        val (processor, finalized) = buildPipeline()
        // Oscillate: 5 samples on (50ms) / 5 samples off (50ms), repeat 20 times.
        // EMA analysis: CANDIDATE starts at burst sample 3 (~30ms in), but drops below
        // caution at zero sample 3 (~30ms into off phase). Total above-caution per cycle
        // ≈ 80ms << 200ms persistence window. No event can fire.
        var t = START_NS
        repeat(20) {
            t = feedSamplesThrough(processor, z = +0.80f * G, startNanos = t, count = 5)
            t = feedSamplesThrough(processor, startNanos = t, count = 5)
        }

        assertEquals("persistence gate prevents any event during rapid chatter", 0, finalized.size)
    }

    // 8.4.6
    @Test
    fun pothole_spikeRejected() {
        val (processor, finalized) = buildPipeline()
        // 10 samples (100ms) with both braking signal and vertical spike above bump threshold.
        // Bump flag is set (verticalG exceeds 0.40g by ~40ms into burst), but the signal
        // drops before the 200ms persistence window completes → CANDIDATE rejected.
        var t = feedSamplesThrough(
            processor,
            y = +0.80f * G,  // vertical → verticalG above bump threshold (0.40g) by sample 4
            z = +0.80f * G,  // braking signal
            startNanos = START_NS,
            count = 10
        )
        feedSamplesThrough(processor, startNanos = t, count = 30)

        assertEquals("brief spike with vertical rejected — no event finalized", 0, finalized.size)
    }

    // 8.4.7
    @Test
    fun sustainedManeuverDuringBump_passes() {
        val (processor, finalized) = buildPipeline()
        // Same as braking test but with concurrent vertical spike. Bump flag is set, but
        // the braking signal persists through the 200ms window → ACTIVE fires normally.
        var t = feedSamplesThrough(
            processor,
            y = +0.80f * G,  // vertical spike present throughout
            z = +0.80f * G,  // sustained braking
            startNanos = START_NS,
            count = 40
        )
        feedSamplesThrough(processor, startNanos = t, count = 90)

        assertEquals("sustained braking event finalizes despite vertical spike", 1, finalized.size)
        assertEquals("category is BRAKING", MotionCategory.BRAKING, finalized[0].category)
        assertEquals("severity is ALERT", AlertSeverity.ALERT, finalized[0].severity)
    }

    // 8.4.8
    @Test
    fun severityEscalation() {
        val (processor, finalized) = buildPipeline()
        // Phase 1: x=+0.34g → lateralG converges to 0.34g (< 0.35g TURNING_ALERT_G → CAUTION).
        // Caution exceeded at ~100ms; ACTIVE at ~300ms. Feed 35 samples (350ms) to be in ACTIVE.
        var t = feedSamplesThrough(processor, x = +0.34f * G, startNanos = START_NS, count = 35)
        // Phase 2: x=+0.80g → EMA immediately exceeds 0.35g alert on first sample → escalation.
        t = feedSamplesThrough(processor, x = +0.80f * G, startNanos = t, count = 5)
        // Phase 3: zero → EMA drops below caution in ~60ms; quiet window (750ms) expires in ~810ms.
        feedSamplesThrough(processor, startNanos = t, count = 90)

        assertEquals("one event escalated to alert", 1, finalized.size)
        assertEquals("category is TURNING", MotionCategory.TURNING, finalized[0].category)
        assertEquals("finalized at ALERT severity (no downgrade)", AlertSeverity.ALERT, finalized[0].severity)
    }

    // 8.4.9
    @Test
    fun cooldown_suppressesRefire() {
        val (processor, finalized) = buildPipeline()
        // Phase 1: first turning event — ACTIVE and finalized.
        var t = feedSamplesThrough(processor, x = +0.80f * G, startNanos = START_NS, count = 40)
        // Phase 2: zero — quiet window expires, event finalized, COOLDOWN starts (~t=3200ms).
        // COOLDOWN expires at ~t=5200ms.
        t = feedSamplesThrough(processor, startNanos = t, count = 90)
        // Phase 3: second above-caution signal during COOLDOWN (t=3310–3700ms, well before 5200ms).
        // COOLDOWN state ignores new signals — no CANDIDATE starts.
        feedSamplesThrough(processor, x = +0.80f * G, startNanos = t, count = 40)

        assertEquals("cooldown suppresses refire — still only one finalized event", 1, finalized.size)
    }
}
