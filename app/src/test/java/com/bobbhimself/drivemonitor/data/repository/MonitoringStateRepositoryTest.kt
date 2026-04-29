package com.bobbhimself.drivemonitor.data.repository

import com.bobbhimself.drivemonitor.data.model.MonitoringState
import com.bobbhimself.drivemonitor.data.model.AlertDirection
import com.bobbhimself.drivemonitor.data.model.AlertSeverity
import com.bobbhimself.drivemonitor.data.model.DirectionalAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringStateRepositoryTest {

    @Test
    fun liveTelemetry_defaultsToZeroWithNoActiveAlerts() {
        val repository = MonitoringStateRepository()

        val telemetry = repository.liveTelemetryState.value

        assertEquals(0f, telemetry.longitudinalG)
        assertEquals(0f, telemetry.lateralG)
        assertTrue(telemetry.activeAlerts.isEmpty())
    }

    @Test
    fun publishLiveTelemetry_updatesCurrentGValues() {
        val repository = MonitoringStateRepository()

        repository.publishLiveTelemetry(longitudinalG = 0.21f, lateralG = -0.14f)

        val telemetry = repository.liveTelemetryState.value
        assertEquals(0.21f, telemetry.longitudinalG)
        assertEquals(-0.14f, telemetry.lateralG)
        assertTrue(telemetry.activeAlerts.isEmpty())
    }

    @Test
    fun resetLiveTelemetry_restoresZeroValuesAndEmptyActiveAlerts() {
        val repository = MonitoringStateRepository()
        repository.publishLiveTelemetry(longitudinalG = 0.21f, lateralG = -0.14f)
        repository.upsertDirectionalAlert(
            DirectionalAlert(AlertDirection.FRONT, AlertSeverity.CAUTION)
        )

        repository.resetLiveTelemetry()

        val telemetry = repository.liveTelemetryState.value
        assertEquals(0f, telemetry.longitudinalG)
        assertEquals(0f, telemetry.lateralG)
        assertTrue(telemetry.activeAlerts.isEmpty())
    }

    @Test
    fun monitoringState_transitionsAreUnchanged() {
        val repository = MonitoringStateRepository()

        repository.setCalibrating()
        assertEquals(MonitoringState.CALIBRATING, repository.monitoringState.value)

        repository.setActive()
        assertEquals(MonitoringState.ACTIVE, repository.monitoringState.value)

        repository.setInactive()
        assertEquals(MonitoringState.INACTIVE, repository.monitoringState.value)
    }

    @Test
    fun upsertDirectionalAlert_addsAlertWithoutChangingCurrentGValues() {
        val repository = MonitoringStateRepository()
        repository.publishLiveTelemetry(longitudinalG = 0.21f, lateralG = -0.14f)

        repository.upsertDirectionalAlert(
            DirectionalAlert(AlertDirection.FRONT, AlertSeverity.CAUTION)
        )

        val telemetry = repository.liveTelemetryState.value
        assertEquals(0.21f, telemetry.longitudinalG)
        assertEquals(-0.14f, telemetry.lateralG)
        assertEquals(
            listOf(DirectionalAlert(AlertDirection.FRONT, AlertSeverity.CAUTION)),
            telemetry.activeAlerts
        )
    }

    @Test
    fun upsertDirectionalAlert_replacesSeverityForExistingDirection() {
        val repository = MonitoringStateRepository()

        repository.upsertDirectionalAlert(
            DirectionalAlert(AlertDirection.FRONT, AlertSeverity.CAUTION)
        )
        repository.upsertDirectionalAlert(
            DirectionalAlert(AlertDirection.FRONT, AlertSeverity.ALERT)
        )

        assertEquals(
            listOf(DirectionalAlert(AlertDirection.FRONT, AlertSeverity.ALERT)),
            repository.liveTelemetryState.value.activeAlerts
        )
    }

    @Test
    fun upsertDirectionalAlert_allowsMultipleDirections() {
        val repository = MonitoringStateRepository()

        repository.upsertDirectionalAlert(
            DirectionalAlert(AlertDirection.FRONT, AlertSeverity.CAUTION)
        )
        repository.upsertDirectionalAlert(
            DirectionalAlert(AlertDirection.LEFT, AlertSeverity.ALERT)
        )

        assertEquals(
            listOf(
                DirectionalAlert(AlertDirection.FRONT, AlertSeverity.CAUTION),
                DirectionalAlert(AlertDirection.LEFT, AlertSeverity.ALERT)
            ),
            repository.liveTelemetryState.value.activeAlerts
        )
    }

    @Test
    fun removeDirectionalAlert_removesOnlyMatchingDirection() {
        val repository = MonitoringStateRepository()
        repository.upsertDirectionalAlert(
            DirectionalAlert(AlertDirection.FRONT, AlertSeverity.CAUTION)
        )
        repository.upsertDirectionalAlert(
            DirectionalAlert(AlertDirection.LEFT, AlertSeverity.ALERT)
        )

        repository.removeDirectionalAlert(AlertDirection.FRONT)

        assertEquals(
            listOf(DirectionalAlert(AlertDirection.LEFT, AlertSeverity.ALERT)),
            repository.liveTelemetryState.value.activeAlerts
        )
    }
}
