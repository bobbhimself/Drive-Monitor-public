package com.bobbhimself.drivemonitor.sensors

import com.bobbhimself.drivemonitor.data.model.AlertSeverity
import com.bobbhimself.drivemonitor.data.model.MotionCategory
import com.bobbhimself.drivemonitor.data.model.TripEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThresholdEvaluatorTest {

    private val ABOVE_CAUTION_ACCEL = ThresholdConfig.ACCELERATION_CAUTION_G + 0.05f  // 0.33f
    private val ABOVE_ALERT_ACCEL   = ThresholdConfig.ACCELERATION_ALERT_G   + 0.05f  // 0.40f
    private val ABOVE_CAUTION_BRAKE = -(ThresholdConfig.BRAKING_CAUTION_G    + 0.05f) // -0.34f
    private val ABOVE_CAUTION_TURN  = ThresholdConfig.TURNING_CAUTION_G      + 0.05f  // 0.33f
    private val ABOVE_BUMP          = ThresholdConfig.BUMP_VERTICAL_THRESHOLD_G + 0.10f // 0.50f
    private val BELOW_CAUTION       = 0.0f

    /**
     * Feeds identical samples every [stepMs] ms from [fromMs] to [toMs] inclusive.
     */
    private fun feedSamples(
        evaluator: ThresholdEvaluator,
        longitudinalG: Float = 0f,
        lateralG: Float = 0f,
        verticalG: Float = 0f,
        fromMs: Long,
        toMs: Long,
        stepMs: Long = 10L
    ) {
        var t = fromMs
        while (t <= toMs) {
            evaluator.process(longitudinalG, lateralG, verticalG, t)
            t += stepMs
        }
    }

    // 8.3.1
    @Test
    fun persistenceGate_rejectsShortSpike() {
        val startedEvents = mutableListOf<TripEvent>()
        val finalizedEvents = mutableListOf<TripEvent>()
        val evaluator = ThresholdEvaluator(
            onEventStarted   = { startedEvents.add(it) },
            onEventEscalated = {},
            onEventFinalized = { finalizedEvents.add(it) }
        )

        // Above-caution signal for < 200ms, then drop below
        feedSamples(evaluator, longitudinalG = ABOVE_CAUTION_ACCEL, fromMs = 0, toMs = 190)
        evaluator.process(BELOW_CAUTION, 0f, 0f, 199)

        assertTrue("No event started on short spike", startedEvents.isEmpty())
        assertTrue("No event finalized on short spike", finalizedEvents.isEmpty())
    }

    // 8.3.2
    @Test
    fun persistenceGate_passesSustainedSignal() {
        val startedEvents = mutableListOf<TripEvent>()
        val evaluator = ThresholdEvaluator(
            onEventStarted   = { startedEvents.add(it) },
            onEventEscalated = {},
            onEventFinalized = {}
        )

        // Candidate at t=0; t=200 satisfies 200-0 >= 200ms persistence window
        feedSamples(evaluator, longitudinalG = ABOVE_CAUTION_ACCEL, fromMs = 0, toMs = 200)

        assertEquals("onEventStarted fires after persistence window", 1, startedEvents.size)
        assertEquals("category is ACCELERATION", MotionCategory.ACCELERATION, startedEvents[0].category)
        assertEquals("severity is CAUTION", AlertSeverity.CAUTION, startedEvents[0].severity)
    }

    // 8.3.3
    @Test
    fun quietWindow_finalizesEvent() {
        val startedEvents  = mutableListOf<TripEvent>()
        val finalizedEvents = mutableListOf<TripEvent>()
        val evaluator = ThresholdEvaluator(
            onEventStarted   = { startedEvents.add(it) },
            onEventEscalated = {},
            onEventFinalized = { finalizedEvents.add(it) }
        )

        feedSamples(evaluator, longitudinalG = ABOVE_CAUTION_ACCEL, fromMs = 0, toMs = 200)
        assertEquals("Event started after persistence window", 1, startedEvents.size)

        // Drop signal; quiet window starts at t=210, expires at t=960 (750ms elapsed)
        feedSamples(evaluator, longitudinalG = BELOW_CAUTION, fromMs = 210, toMs = 970)

        assertEquals("onEventFinalized fires after quiet window", 1, finalizedEvents.size)
        assertEquals("finalized category is ACCELERATION", MotionCategory.ACCELERATION, finalizedEvents[0].category)
    }

    // 8.3.4
    @Test
    fun severityEscalation() {
        val startedEvents   = mutableListOf<TripEvent>()
        val escalatedEvents = mutableListOf<TripEvent>()
        val finalizedEvents = mutableListOf<TripEvent>()
        val evaluator = ThresholdEvaluator(
            onEventStarted   = { startedEvents.add(it) },
            onEventEscalated = { escalatedEvents.add(it) },
            onEventFinalized = { finalizedEvents.add(it) }
        )

        // Reach ACTIVE at CAUTION level
        feedSamples(evaluator, longitudinalG = ABOVE_CAUTION_ACCEL, fromMs = 0, toMs = 200)
        assertEquals("Started at CAUTION", AlertSeverity.CAUTION, startedEvents[0].severity)

        // Escalate to ALERT during ACTIVE
        evaluator.process(ABOVE_ALERT_ACCEL, 0f, 0f, 210)

        assertEquals("onEventEscalated fires once", 1, escalatedEvents.size)
        assertEquals("escalated severity is ALERT", AlertSeverity.ALERT, escalatedEvents[0].severity)
        assertEquals("escalated category is ACCELERATION", MotionCategory.ACCELERATION, escalatedEvents[0].category)

        // Drop and let quiet window expire → finalize at t=970
        feedSamples(evaluator, longitudinalG = BELOW_CAUTION, fromMs = 220, toMs = 980)

        assertEquals("Finalized event has ALERT severity (never downgrades)", AlertSeverity.ALERT, finalizedEvents[0].severity)
    }

    // 8.3.5
    @Test
    fun cooldown_suppressesRefire() {
        val startedEvents = mutableListOf<TripEvent>()
        val evaluator = ThresholdEvaluator(
            onEventStarted   = { startedEvents.add(it) },
            onEventEscalated = {},
            onEventFinalized = {}
        )

        // Complete a full lifecycle: ACTIVE at t=200, finalize at t=960, COOLDOWN starts at t=960
        feedSamples(evaluator, longitudinalG = ABOVE_CAUTION_ACCEL, fromMs = 0, toMs = 200)
        feedSamples(evaluator, longitudinalG = BELOW_CAUTION, fromMs = 210, toMs = 970)
        assertEquals("One event started in first lifecycle", 1, startedEvents.size)

        // Attempt refire within cooldown: 1380 - 960 = 420ms < 2000ms
        feedSamples(evaluator, longitudinalG = ABOVE_CAUTION_ACCEL, fromMs = 980, toMs = 1380)

        assertEquals("No new event starts during cooldown", 1, startedEvents.size)
    }

    // 8.3.6
    @Test
    fun signedLongitudinal_accelerationVsBraking() {
        val startedAccel = mutableListOf<TripEvent>()
        val accelEvaluator = ThresholdEvaluator(
            onEventStarted   = { startedAccel.add(it) },
            onEventEscalated = {},
            onEventFinalized = {}
        )
        feedSamples(accelEvaluator, longitudinalG = ABOVE_CAUTION_ACCEL, fromMs = 0, toMs = 200)

        assertEquals("Positive longitudinalG fires ACCELERATION", 1, startedAccel.size)
        assertEquals(MotionCategory.ACCELERATION, startedAccel[0].category)

        val startedBrake = mutableListOf<TripEvent>()
        val brakeEvaluator = ThresholdEvaluator(
            onEventStarted   = { startedBrake.add(it) },
            onEventEscalated = {},
            onEventFinalized = {}
        )
        feedSamples(brakeEvaluator, longitudinalG = ABOVE_CAUTION_BRAKE, fromMs = 0, toMs = 200)

        assertEquals("Negative longitudinalG fires BRAKING", 1, startedBrake.size)
        assertEquals(MotionCategory.BRAKING, startedBrake[0].category)
    }

    // 8.3.7
    @Test
    fun absoluteLateral_bothDirectionsAreTurning() {
        val startedPos = mutableListOf<TripEvent>()
        val posEvaluator = ThresholdEvaluator(
            onEventStarted   = { startedPos.add(it) },
            onEventEscalated = {},
            onEventFinalized = {}
        )
        feedSamples(posEvaluator, lateralG = ABOVE_CAUTION_TURN, fromMs = 0, toMs = 200)

        assertEquals("Positive lateralG fires TURNING", 1, startedPos.size)
        assertEquals(MotionCategory.TURNING, startedPos[0].category)

        val startedNeg = mutableListOf<TripEvent>()
        val negEvaluator = ThresholdEvaluator(
            onEventStarted   = { startedNeg.add(it) },
            onEventEscalated = {},
            onEventFinalized = {}
        )
        feedSamples(negEvaluator, lateralG = -ABOVE_CAUTION_TURN, fromMs = 0, toMs = 200)

        assertEquals("Negative lateralG fires TURNING", 1, startedNeg.size)
        assertEquals(MotionCategory.TURNING, startedNeg[0].category)
    }

    // 8.3.8
    @Test
    fun bumpRejection_shortSpikeWithVertical() {
        val startedEvents = mutableListOf<TripEvent>()
        val evaluator = ThresholdEvaluator(
            onEventStarted   = { startedEvents.add(it) },
            onEventEscalated = {},
            onEventFinalized = {}
        )

        // Above-caution longitudinalG + above-bump verticalG, then drop before 200ms
        feedSamples(
            evaluator,
            longitudinalG = ABOVE_CAUTION_ACCEL,
            verticalG     = ABOVE_BUMP,
            fromMs        = 0,
            toMs          = 150
        )
        evaluator.process(BELOW_CAUTION, 0f, 0f, 190)

        assertTrue("Candidate with bump rejected before persistence window — no event started", startedEvents.isEmpty())
    }

    // 8.3.9
    @Test
    fun sustainedManeuverDuringBump_passesGate() {
        val startedEvents = mutableListOf<TripEvent>()
        val evaluator = ThresholdEvaluator(
            onEventStarted   = { startedEvents.add(it) },
            onEventEscalated = {},
            onEventFinalized = {}
        )

        // Same above-caution + above-bump, but sustained through 200ms
        feedSamples(
            evaluator,
            longitudinalG = ABOVE_CAUTION_ACCEL,
            verticalG     = ABOVE_BUMP,
            fromMs        = 0,
            toMs          = 200
        )

        assertEquals("Sustained maneuver with bump still passes persistence gate", 1, startedEvents.size)
        assertEquals(MotionCategory.ACCELERATION, startedEvents[0].category)
    }

    // 8.3.10
    @Test
    fun concurrentIndependentCategories() {
        val startedEvents = mutableListOf<TripEvent>()
        val evaluator = ThresholdEvaluator(
            onEventStarted   = { startedEvents.add(it) },
            onEventEscalated = {},
            onEventFinalized = {}
        )

        // Both acceleration and turning trackers candidate at t=0 and activate at t=200
        feedSamples(
            evaluator,
            longitudinalG = ABOVE_CAUTION_ACCEL,
            lateralG      = ABOVE_CAUTION_TURN,
            fromMs        = 0,
            toMs          = 200
        )

        assertEquals("Both ACCELERATION and TURNING events fire", 2, startedEvents.size)
        val categories = startedEvents.map { it.category }.toSet()
        assertTrue("ACCELERATION category present", MotionCategory.ACCELERATION in categories)
        assertTrue("TURNING category present", MotionCategory.TURNING in categories)
    }
}
