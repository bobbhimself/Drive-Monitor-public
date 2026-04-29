package com.bobbhimself.drivemonitor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bobbhimself.drivemonitor.R
import com.bobbhimself.drivemonitor.data.model.AlertDirection
import com.bobbhimself.drivemonitor.data.model.AlertSeverity
import com.bobbhimself.drivemonitor.data.model.DirectionalAlert
import com.bobbhimself.drivemonitor.ui.theme.DriveMonitorTheme

private val TruckPanelColor = Color(0xFFEFF3F6)
private val CautionArcColor = Color(0xFFF0C02D)
private val AlertArcColor = Color(0xFFCF2424)

@Composable
fun TruckAlertView(
    activeAlerts: List<DirectionalAlert>,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(TruckPanelColor, RoundedCornerShape(8.dp))
            .semantics {
                contentDescription = truckAlertContentDescription(activeAlerts)
            }
    ) {
        Image(
            painter = painterResource(id = R.drawable.truck),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 42.dp, vertical = 22.dp)
        )
        TruckAlertArcs(activeAlerts = activeAlerts, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun TruckAlertArcs(
    activeAlerts: List<DirectionalAlert>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.045f
        activeAlerts.forEach { alert ->
            val color = when (alert.severity) {
                AlertSeverity.CAUTION -> CautionArcColor
                AlertSeverity.ALERT -> AlertArcColor
            }
            when (alert.direction) {
                AlertDirection.FRONT -> drawDirectionalArc(
                    color = color,
                    topLeft = Offset(size.width * 0.31f, size.height * 0.055f),
                    arcSize = Size(size.width * 0.38f, size.height * 0.20f),
                    startAngle = 205f,
                    sweepAngle = 130f,
                    strokeWidth = strokeWidth
                )
                AlertDirection.REAR -> drawDirectionalArc(
                    color = color,
                    topLeft = Offset(size.width * 0.31f, size.height * 0.745f),
                    arcSize = Size(size.width * 0.38f, size.height * 0.20f),
                    startAngle = 25f,
                    sweepAngle = 130f,
                    strokeWidth = strokeWidth
                )
                AlertDirection.LEFT -> drawDirectionalArc(
                    color = color,
                    topLeft = Offset(size.width * 0.05f, size.height * 0.34f),
                    arcSize = Size(size.width * 0.22f, size.height * 0.32f),
                    startAngle = 115f,
                    sweepAngle = 130f,
                    strokeWidth = strokeWidth
                )
                AlertDirection.RIGHT -> drawDirectionalArc(
                    color = color,
                    topLeft = Offset(size.width * 0.73f, size.height * 0.34f),
                    arcSize = Size(size.width * 0.22f, size.height * 0.32f),
                    startAngle = 295f,
                    sweepAngle = 130f,
                    strokeWidth = strokeWidth
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDirectionalArc(
    color: Color,
    topLeft: Offset,
    arcSize: Size,
    startAngle: Float,
    sweepAngle: Float,
    strokeWidth: Float
) {
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        alpha = 0.94f
    )
}

private fun truckAlertContentDescription(activeAlerts: List<DirectionalAlert>): String =
    if (activeAlerts.isEmpty()) {
        "Truck alert view: no active directional alerts"
    } else {
        "Truck alert view: " + activeAlerts.joinToString { alert ->
            "${alert.direction.label()} ${alert.severity.label()}"
        }
    }

private fun AlertDirection.label(): String =
    when (this) {
        AlertDirection.FRONT -> "front"
        AlertDirection.REAR -> "rear"
        AlertDirection.LEFT -> "left"
        AlertDirection.RIGHT -> "right"
    }

private fun AlertSeverity.label(): String =
    when (this) {
        AlertSeverity.CAUTION -> "caution"
        AlertSeverity.ALERT -> "alert"
    }

@Preview(showBackground = true, name = "Truck No Alerts")
@Composable
private fun PreviewTruckNoAlerts() {
    DriveMonitorTheme {
        TruckAlertView(
            activeAlerts = emptyList(),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.86f)
        )
    }
}

@Preview(showBackground = true, name = "Truck Front Caution")
@Composable
private fun PreviewTruckFrontCaution() {
    DriveMonitorTheme {
        TruckAlertView(
            activeAlerts = listOf(
                DirectionalAlert(AlertDirection.FRONT, AlertSeverity.CAUTION)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.86f)
        )
    }
}

@Preview(showBackground = true, name = "Truck Rear Alert")
@Composable
private fun PreviewTruckRearAlert() {
    DriveMonitorTheme {
        TruckAlertView(
            activeAlerts = listOf(
                DirectionalAlert(AlertDirection.REAR, AlertSeverity.ALERT)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.86f)
        )
    }
}

@Preview(showBackground = true, name = "Truck Left Caution")
@Composable
private fun PreviewTruckLeftCaution() {
    DriveMonitorTheme {
        TruckAlertView(
            activeAlerts = listOf(
                DirectionalAlert(AlertDirection.LEFT, AlertSeverity.CAUTION)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.86f)
        )
    }
}

@Preview(showBackground = true, name = "Truck Right Alert")
@Composable
private fun PreviewTruckRightAlert() {
    DriveMonitorTheme {
        TruckAlertView(
            activeAlerts = listOf(
                DirectionalAlert(AlertDirection.RIGHT, AlertSeverity.ALERT)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.86f)
        )
    }
}

@Preview(showBackground = true, name = "Truck Multiple Alerts")
@Composable
private fun PreviewTruckMultipleAlerts() {
    DriveMonitorTheme {
        TruckAlertView(
            activeAlerts = listOf(
                DirectionalAlert(AlertDirection.FRONT, AlertSeverity.ALERT),
                DirectionalAlert(AlertDirection.REAR, AlertSeverity.CAUTION),
                DirectionalAlert(AlertDirection.LEFT, AlertSeverity.CAUTION),
                DirectionalAlert(AlertDirection.RIGHT, AlertSeverity.ALERT)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.86f)
        )
    }
}
