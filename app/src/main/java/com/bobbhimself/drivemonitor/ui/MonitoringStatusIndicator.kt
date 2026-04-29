package com.bobbhimself.drivemonitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bobbhimself.drivemonitor.data.model.MonitoringState
import com.bobbhimself.drivemonitor.ui.theme.DriveMonitorTheme

private val COLOR_INACTIVE = Color.Red
private val COLOR_CALIBRATING = Color(0xFFFFC107)
private val COLOR_ACTIVE = Color(0xFF4CAF50)

private const val LABEL_INACTIVE = "Inactive"
private const val LABEL_CALIBRATING = "Calibrating"
private const val LABEL_ACTIVE = "Active"

@Composable
fun MonitoringStatusIndicator(
    state: MonitoringState,
    modifier: Modifier = Modifier
) {
    val (color, label) = when (state) {
        MonitoringState.INACTIVE -> COLOR_INACTIVE to LABEL_INACTIVE
        MonitoringState.CALIBRATING -> COLOR_CALIBRATING to LABEL_CALIBRATING
        MonitoringState.ACTIVE -> COLOR_ACTIVE to LABEL_ACTIVE
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.semantics {
            contentDescription = "Monitoring status: $label"
        }
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(color = color, shape = CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label)
    }
}

@Preview(showBackground = true, name = "Inactive")
@Composable
private fun PreviewInactive() {
    DriveMonitorTheme {
        MonitoringStatusIndicator(state = MonitoringState.INACTIVE)
    }
}

@Preview(showBackground = true, name = "Calibrating")
@Composable
private fun PreviewCalibrating() {
    DriveMonitorTheme {
        MonitoringStatusIndicator(state = MonitoringState.CALIBRATING)
    }
}

@Preview(showBackground = true, name = "Active")
@Composable
private fun PreviewActive() {
    DriveMonitorTheme {
        MonitoringStatusIndicator(state = MonitoringState.ACTIVE)
    }
}
