package com.bobbhimself.drivemonitor.sensors

import android.util.Log
import com.bobbhimself.drivemonitor.data.model.AlertSeverity
import com.bobbhimself.drivemonitor.data.model.MotionCategory
import com.bobbhimself.drivemonitor.data.model.TripEvent
import kotlin.math.abs

class ThresholdEvaluator(
    private val onEventStarted: (TripEvent) -> Unit,
    private val onEventEscalated: (TripEvent) -> Unit,
    private val onEventFinalized: (TripEvent) -> Unit
) {

    private companion object {
        const val TAG = "ThresholdEvaluator"
    }

    private enum class LifecycleState { IDLE, CANDIDATE, ACTIVE, COOLDOWN }

    private class CategoryTracker(val category: MotionCategory) {
        var state: LifecycleState = LifecycleState.IDLE
        var eventTimestampMillis: Long = 0L      // timestamp at start of CANDIDATE
        var candidateStartMillis: Long = 0L      // monotonic ms; persistence window start
        var highestSeverity: AlertSeverity = AlertSeverity.CAUTION
        var quietWindowStartMillis: Long = 0L    // monotonic ms; quiet window start (0 = not quieting)
        var cooldownStartMillis: Long = 0L       // monotonic ms; cooldown start
        var bumpDetected: Boolean = false         // vertical spike seen during CANDIDATE window
    }

    private val accelerationTracker = CategoryTracker(MotionCategory.ACCELERATION)
    private val brakingTracker      = CategoryTracker(MotionCategory.BRAKING)
    private val turningTracker      = CategoryTracker(MotionCategory.TURNING)

    fun process(
        longitudinalG: Float,
        lateralG: Float,
        verticalG: Float,
        timestampMillis: Long
    ) {
        // Acceleration: positive longitudinalG — mutually exclusive with braking per sample
        // Braking: negative longitudinalG — mutually exclusive with acceleration per sample
        // Turning: absolute lateralG (left and right treated identically)
        val accelerating = longitudinalG > ThresholdConfig.ACCELERATION_CAUTION_G
        val braking      = longitudinalG < -ThresholdConfig.BRAKING_CAUTION_G
        val turning      = abs(lateralG) > ThresholdConfig.TURNING_CAUTION_G

        val accelerationAlert = longitudinalG > ThresholdConfig.ACCELERATION_ALERT_G
        val brakingAlert      = longitudinalG < -ThresholdConfig.BRAKING_ALERT_G
        val turningAlert      = abs(lateralG) > ThresholdConfig.TURNING_ALERT_G

        evaluateCategory(accelerationTracker, accelerating, accelerationAlert, verticalG, timestampMillis)
        evaluateCategory(brakingTracker,      braking,      brakingAlert,      verticalG, timestampMillis)
        evaluateCategory(turningTracker,      turning,      turningAlert,      verticalG, timestampMillis)
    }

    private fun evaluateCategory(
        tracker: CategoryTracker,
        exceedsCaution: Boolean,
        exceedsAlert: Boolean,
        verticalG: Float,
        timestampMillis: Long
    ) {
        when (tracker.state) {
            LifecycleState.IDLE -> {
                if (exceedsCaution) {
                    tracker.state = LifecycleState.CANDIDATE
                    tracker.candidateStartMillis = timestampMillis
                    tracker.eventTimestampMillis = System.currentTimeMillis()
                    tracker.highestSeverity = AlertSeverity.CAUTION
                    tracker.bumpDetected = false
                    Log.i(TAG,"IDLE->CANDIDATE: ${tracker.category}")
                }
            }
            LifecycleState.CANDIDATE -> {
                // Track vertical spikes for bump rejection
                if (abs(verticalG) > ThresholdConfig.BUMP_VERTICAL_THRESHOLD_G) {
                    tracker.bumpDetected = true
                }
                if (!exceedsCaution) {
                    // Signal dropped before persistence window completed — reject
                    if (tracker.bumpDetected) {
                        Log.i(TAG,"CANDIDATE rejected (bump): ${tracker.category}")
                    }
                    tracker.state = LifecycleState.IDLE
                    return
                }
                if (timestampMillis - tracker.candidateStartMillis >= ThresholdConfig.PERSISTENCE_WINDOW_MS) {
                    tracker.state = LifecycleState.ACTIVE
                    tracker.quietWindowStartMillis = 0L
                    if (exceedsAlert) {
                        tracker.highestSeverity = AlertSeverity.ALERT
                    }
                    Log.i(TAG,"CANDIDATE->ACTIVE: ${tracker.category} severity=${tracker.highestSeverity}")
                    onEventStarted(
                        TripEvent(
                            timestampUtcMillis = tracker.eventTimestampMillis,
                            category = tracker.category,
                            severity = tracker.highestSeverity
                        )
                    )
                }
            }
            LifecycleState.ACTIVE -> {
                if (exceedsCaution) {
                    // Signal still above threshold — reset quiet window
                    tracker.quietWindowStartMillis = 0L
                    // Escalate severity if alert threshold reached (never downgrade)
                    if (exceedsAlert && tracker.highestSeverity != AlertSeverity.ALERT) {
                        tracker.highestSeverity = AlertSeverity.ALERT
                        Log.i(TAG,"Escalated to ALERT: ${tracker.category}")
                        onEventEscalated(
                            TripEvent(
                                timestampUtcMillis = tracker.eventTimestampMillis,
                                category = tracker.category,
                                severity = AlertSeverity.ALERT
                            )
                        )
                    }
                } else {
                    // Signal dropped below threshold
                    if (tracker.quietWindowStartMillis == 0L) {
                        // Start quiet window
                        tracker.quietWindowStartMillis = timestampMillis
                    } else if (timestampMillis - tracker.quietWindowStartMillis >= ThresholdConfig.QUIET_WINDOW_MS) {
                        // Quiet window expired — finalize event
                        finalizeEvent(tracker, timestampMillis)
                    }
                }
            }
            LifecycleState.COOLDOWN -> {
                if (timestampMillis - tracker.cooldownStartMillis >= ThresholdConfig.COOLDOWN_WINDOW_MS) {
                    tracker.state = LifecycleState.IDLE
                    Log.i(TAG,"COOLDOWN->IDLE: ${tracker.category}")
                }
            }
        }
    }

    private fun finalizeEvent(tracker: CategoryTracker, timestampMillis: Long) {
        Log.i(TAG,"ACTIVE->COOLDOWN (finalized): ${tracker.category} severity=${tracker.highestSeverity}")
        onEventFinalized(
            TripEvent(
                timestampUtcMillis = tracker.eventTimestampMillis,
                category = tracker.category,
                severity = tracker.highestSeverity
            )
        )
        tracker.state = LifecycleState.COOLDOWN
        tracker.cooldownStartMillis = timestampMillis
    }
}
