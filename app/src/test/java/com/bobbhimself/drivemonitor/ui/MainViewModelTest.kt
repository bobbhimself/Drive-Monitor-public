package com.bobbhimself.drivemonitor.ui

import com.bobbhimself.drivemonitor.DriveMonitorApp
import com.bobbhimself.drivemonitor.data.repository.MonitoringStateRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class MainViewModelTest {

    @Test
    fun liveTelemetryState_exposesRepositoryTelemetry() {
        val application = DriveMonitorApp()
        application.monitoringStateRepository = MonitoringStateRepository()
        val viewModel = MainViewModel(application)

        application.monitoringStateRepository.publishLiveTelemetry(
            longitudinalG = 0.32f,
            lateralG = -0.18f
        )

        val telemetry = viewModel.liveTelemetryState.value
        assertEquals(0.32f, telemetry.longitudinalG)
        assertEquals(-0.18f, telemetry.lateralG)
    }
}
