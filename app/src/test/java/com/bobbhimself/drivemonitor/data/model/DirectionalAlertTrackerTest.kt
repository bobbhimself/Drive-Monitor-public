package com.bobbhimself.drivemonitor.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DirectionalAlertTrackerTest {

    @Test
    fun start_mapsEventToDirectionalAlertAndStoresDirection() {
        val tracker = DirectionalAlertTracker()

        val alert = tracker.start(
            category = MotionCategory.TURNING,
            severity = AlertSeverity.CAUTION,
            lateralG = -0.32f
        )

        assertEquals(DirectionalAlert(AlertDirection.LEFT, AlertSeverity.CAUTION), alert)
        assertEquals(AlertDirection.LEFT, tracker.finalize(MotionCategory.TURNING))
    }

    @Test
    fun escalate_keepsStartedDirectionWhenCurrentLateralSignChanges() {
        val tracker = DirectionalAlertTracker()
        tracker.start(
            category = MotionCategory.TURNING,
            severity = AlertSeverity.CAUTION,
            lateralG = -0.32f
        )

        val alert = tracker.escalate(
            category = MotionCategory.TURNING,
            lateralG = 0.42f
        )

        assertEquals(DirectionalAlert(AlertDirection.LEFT, AlertSeverity.ALERT), alert)
    }

    @Test
    fun start_tracksConcurrentAccelerationAndTurningByCategory() {
        val tracker = DirectionalAlertTracker()

        val acceleration = tracker.start(
            category = MotionCategory.ACCELERATION,
            severity = AlertSeverity.CAUTION,
            lateralG = 0f
        )
        val turning = tracker.start(
            category = MotionCategory.TURNING,
            severity = AlertSeverity.ALERT,
            lateralG = 0.39f
        )

        assertEquals(DirectionalAlert(AlertDirection.FRONT, AlertSeverity.CAUTION), acceleration)
        assertEquals(DirectionalAlert(AlertDirection.RIGHT, AlertSeverity.ALERT), turning)
        assertEquals(AlertDirection.FRONT, tracker.finalize(MotionCategory.ACCELERATION))
        assertEquals(AlertDirection.RIGHT, tracker.finalize(MotionCategory.TURNING))
    }

    @Test
    fun finalize_returnsNullWhenCategoryIsNotActive() {
        val tracker = DirectionalAlertTracker()

        assertNull(tracker.finalize(MotionCategory.BRAKING))
    }
}
