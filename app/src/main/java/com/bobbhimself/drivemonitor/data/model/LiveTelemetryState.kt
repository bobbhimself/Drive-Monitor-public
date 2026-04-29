package com.bobbhimself.drivemonitor.data.model

data class LiveTelemetryState(
    val longitudinalG: Float = 0f,
    val lateralG: Float = 0f,
    val activeAlerts: List<DirectionalAlert> = emptyList()
)

data class DirectionalAlert(
    val direction: AlertDirection,
    val severity: AlertSeverity
)

enum class AlertDirection {
    FRONT,
    REAR,
    LEFT,
    RIGHT
}
