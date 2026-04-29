package com.bobbhimself.drivemonitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bobbhimself.drivemonitor.data.model.AlertDirection
import com.bobbhimself.drivemonitor.data.model.AlertSeverity
import com.bobbhimself.drivemonitor.data.model.DirectionalAlert
import com.bobbhimself.drivemonitor.data.model.LiveTelemetryState
import com.bobbhimself.drivemonitor.data.model.MonitoringState
import com.bobbhimself.drivemonitor.sensors.ThresholdConfig
import com.bobbhimself.drivemonitor.ui.components.GForceGauge
import com.bobbhimself.drivemonitor.ui.components.GaugeThresholds
import com.bobbhimself.drivemonitor.ui.components.TripControls
import com.bobbhimself.drivemonitor.ui.components.TruckAlertView
import com.bobbhimself.drivemonitor.ui.theme.DriveMonitorTheme

@Composable
fun MainScreen(
    state: MonitoringState,
    liveTelemetryState: LiveTelemetryState,
    onStartTrip: () -> Unit,
    onEndTrip: () -> Unit,
    onViewLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayTelemetry = displayTelemetryFor(state, liveTelemetryState)

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(16.dp)
            .testTag("main-screen")
    ) {
        MainHeader(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("header-row")
        )
        TruckAlertView(
            activeAlerts = displayTelemetry.activeAlerts,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("truck-panel")
        )
        GaugeRow(
            telemetryState = displayTelemetry,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("gauge-row")
        )
        TripControls(
            state = state,
            onStartTrip = onStartTrip,
            onEndTrip = onEndTrip,
            onViewLog = onViewLog,
            modifier = Modifier.testTag("control-area")
        )
    }
}

@Composable
private fun MainHeader(
    state: MonitoringState,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Text(
            text = "Drive Monitor",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        MonitoringStatusIndicator(state = state)
    }
}

@Composable
private fun GaugeRow(
    telemetryState: LiveTelemetryState,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
    ) {
        GForceGauge(
            valueG = telemetryState.longitudinalG,
            label = "Braking / Acceleration",
            thresholds = longitudinalThresholds(),
            accessibilityLabelPrefix = "Acceleration and braking G force",
            modifier = Modifier
                .weight(1f)
                .testTag("longitudinal-gauge")
        )
        GForceGauge(
            valueG = telemetryState.lateralG,
            label = "Left / Right",
            thresholds = lateralThresholds(),
            accessibilityLabelPrefix = "Left and right G force",
            modifier = Modifier
                .weight(1f)
                .testTag("lateral-gauge")
        )
    }
}

private fun displayTelemetryFor(
    state: MonitoringState,
    liveTelemetryState: LiveTelemetryState
): LiveTelemetryState =
    if (state == MonitoringState.ACTIVE) {
        liveTelemetryState
    } else {
        LiveTelemetryState()
    }

private fun longitudinalThresholds(): GaugeThresholds =
    GaugeThresholds(
        negativeCautionG = -ThresholdConfig.BRAKING_CAUTION_G,
        negativeAlertG = -ThresholdConfig.BRAKING_ALERT_G,
        positiveCautionG = ThresholdConfig.ACCELERATION_CAUTION_G,
        positiveAlertG = ThresholdConfig.ACCELERATION_ALERT_G
    )

private fun lateralThresholds(): GaugeThresholds =
    GaugeThresholds(
        negativeCautionG = -ThresholdConfig.TURNING_CAUTION_G,
        negativeAlertG = -ThresholdConfig.TURNING_ALERT_G,
        positiveCautionG = ThresholdConfig.TURNING_CAUTION_G,
        positiveAlertG = ThresholdConfig.TURNING_ALERT_G
    )

@Preview(showBackground = true, name = "Inactive")
@Composable
private fun PreviewMainScreenInactive() {
    DriveMonitorTheme {
        MainScreen(
            state = MonitoringState.INACTIVE,
            liveTelemetryState = LiveTelemetryState(),
            onStartTrip = {},
            onEndTrip = {},
            onViewLog = {}
        )
    }
}

@Preview(showBackground = true, name = "Calibrating")
@Composable
private fun PreviewMainScreenCalibrating() {
    DriveMonitorTheme {
        MainScreen(
            state = MonitoringState.CALIBRATING,
            liveTelemetryState = LiveTelemetryState(),
            onStartTrip = {},
            onEndTrip = {},
            onViewLog = {}
        )
    }
}

@Preview(showBackground = true, name = "Active No Alerts")
@Composable
private fun PreviewMainScreenActiveNoAlerts() {
    DriveMonitorTheme {
        MainScreen(
            state = MonitoringState.ACTIVE,
            liveTelemetryState = LiveTelemetryState(
                longitudinalG = 0.12f,
                lateralG = -0.08f
            ),
            onStartTrip = {},
            onEndTrip = {},
            onViewLog = {}
        )
    }
}

@Preview(showBackground = true, name = "Active Caution")
@Composable
private fun PreviewMainScreenActiveCaution() {
    DriveMonitorTheme {
        MainScreen(
            state = MonitoringState.ACTIVE,
            liveTelemetryState = LiveTelemetryState(
                longitudinalG = ThresholdConfig.ACCELERATION_CAUTION_G,
                lateralG = -ThresholdConfig.TURNING_CAUTION_G,
                activeAlerts = listOf(
                    DirectionalAlert(AlertDirection.FRONT, AlertSeverity.CAUTION),
                    DirectionalAlert(AlertDirection.LEFT, AlertSeverity.CAUTION)
                )
            ),
            onStartTrip = {},
            onEndTrip = {},
            onViewLog = {}
        )
    }
}

@Preview(showBackground = true, name = "Active Alert")
@Composable
private fun PreviewMainScreenActiveAlert() {
    DriveMonitorTheme {
        MainScreen(
            state = MonitoringState.ACTIVE,
            liveTelemetryState = LiveTelemetryState(
                longitudinalG = -ThresholdConfig.BRAKING_ALERT_G,
                lateralG = ThresholdConfig.TURNING_ALERT_G,
                activeAlerts = listOf(
                    DirectionalAlert(AlertDirection.REAR, AlertSeverity.ALERT),
                    DirectionalAlert(AlertDirection.RIGHT, AlertSeverity.ALERT)
                )
            ),
            onStartTrip = {},
            onEndTrip = {},
            onViewLog = {}
        )
    }
}

@Preview(showBackground = true, name = "Active Multiple Alerts")
@Composable
private fun PreviewMainScreenActiveMultipleAlerts() {
    DriveMonitorTheme {
        MainScreen(
            state = MonitoringState.ACTIVE,
            liveTelemetryState = LiveTelemetryState(
                longitudinalG = ThresholdConfig.ACCELERATION_ALERT_G,
                lateralG = ThresholdConfig.TURNING_CAUTION_G,
                activeAlerts = listOf(
                    DirectionalAlert(AlertDirection.FRONT, AlertSeverity.ALERT),
                    DirectionalAlert(AlertDirection.REAR, AlertSeverity.CAUTION),
                    DirectionalAlert(AlertDirection.LEFT, AlertSeverity.CAUTION),
                    DirectionalAlert(AlertDirection.RIGHT, AlertSeverity.ALERT)
                )
            ),
            onStartTrip = {},
            onEndTrip = {},
            onViewLog = {}
        )
    }
}
