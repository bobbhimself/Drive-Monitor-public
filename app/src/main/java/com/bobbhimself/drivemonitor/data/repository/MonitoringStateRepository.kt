package com.bobbhimself.drivemonitor.data.repository

import com.bobbhimself.drivemonitor.data.model.MonitoringState
import com.bobbhimself.drivemonitor.data.model.AlertDirection
import com.bobbhimself.drivemonitor.data.model.DirectionalAlert
import com.bobbhimself.drivemonitor.data.model.LiveTelemetryState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class MonitoringStateRepository {

    private val _state = MutableStateFlow(MonitoringState.INACTIVE)
    val monitoringState: StateFlow<MonitoringState> = _state.asStateFlow()

    private val _liveTelemetryState = MutableStateFlow(LiveTelemetryState())
    val liveTelemetryState: StateFlow<LiveTelemetryState> = _liveTelemetryState.asStateFlow()

    private val _userMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val userMessages: SharedFlow<String> = _userMessages.asSharedFlow()

    fun setActive() {
        _state.value = MonitoringState.ACTIVE
    }

    fun setCalibrating() {
        _state.value = MonitoringState.CALIBRATING
    }

    fun setInactive() {
        _state.value = MonitoringState.INACTIVE
    }

    fun publishLiveTelemetry(longitudinalG: Float, lateralG: Float) {
        _liveTelemetryState.value = _liveTelemetryState.value.copy(
            longitudinalG = longitudinalG,
            lateralG = lateralG
        )
    }

    fun upsertDirectionalAlert(alert: DirectionalAlert) {
        val current = _liveTelemetryState.value
        val alertsWithoutDirection = current.activeAlerts.filterNot {
            it.direction == alert.direction
        }
        _liveTelemetryState.value = current.copy(
            activeAlerts = alertsWithoutDirection + alert
        )
    }

    fun removeDirectionalAlert(direction: AlertDirection) {
        val current = _liveTelemetryState.value
        _liveTelemetryState.value = current.copy(
            activeAlerts = current.activeAlerts.filterNot {
                it.direction == direction
            }
        )
    }

    fun resetLiveTelemetry() {
        _liveTelemetryState.value = LiveTelemetryState()
    }

    fun emitUserMessage(message: String) {
        _userMessages.tryEmit(message)
    }
}
