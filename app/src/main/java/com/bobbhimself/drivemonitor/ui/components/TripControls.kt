package com.bobbhimself.drivemonitor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bobbhimself.drivemonitor.data.model.MonitoringState
import com.bobbhimself.drivemonitor.ui.theme.DriveMonitorTheme

@Composable
fun TripControls(
    state: MonitoringState,
    onStartTrip: () -> Unit,
    onEndTrip: () -> Unit,
    onViewLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        val primaryLabel = if (state == MonitoringState.INACTIVE) {
            "Start Trip"
        } else {
            "End Trip"
        }
        Button(
            onClick = if (state == MonitoringState.INACTIVE) onStartTrip else onEndTrip,
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 44.dp)
        ) {
            Text(primaryLabel)
        }
        OutlinedButton(
            onClick = onViewLog,
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 44.dp)
        ) {
            Text("View Log")
        }
    }
}

@Preview(showBackground = true, name = "Trip Controls Inactive")
@Composable
private fun PreviewTripControlsInactive() {
    DriveMonitorTheme {
        TripControls(
            state = MonitoringState.INACTIVE,
            onStartTrip = {},
            onEndTrip = {},
            onViewLog = {}
        )
    }
}

@Preview(showBackground = true, name = "Trip Controls Active")
@Composable
private fun PreviewTripControlsActive() {
    DriveMonitorTheme {
        TripControls(
            state = MonitoringState.ACTIVE,
            onStartTrip = {},
            onEndTrip = {},
            onViewLog = {}
        )
    }
}
