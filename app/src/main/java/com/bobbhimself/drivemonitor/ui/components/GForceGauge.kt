package com.bobbhimself.drivemonitor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bobbhimself.drivemonitor.sensors.ThresholdConfig
import com.bobbhimself.drivemonitor.ui.theme.DriveMonitorTheme
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val GaugeTrackColor = Color(0xFFB5C0CB)
private val GaugeCautionColor = Color(0xFFF0C02D)
private val GaugeAlertColor = Color(0xFFCF2424)
private val GaugeNeedleColor = Color(0xFF17212B)
private const val GaugeStartAngle = 180f
private const val GaugeSweepAngle = 180f
private const val GaugeMaxRotation = DisplayAxisMapper.MAX_NEEDLE_ROTATION_DEGREES
private const val GaugeCautionStartRotation =
    DisplayAxisMapper.CAUTION_ZONE_START_ROTATION_DEGREES
private const val GaugeAlertStartRotation =
    DisplayAxisMapper.ALERT_ZONE_START_ROTATION_DEGREES

@Composable
fun GForceGauge(
    valueG: Float,
    label: String,
    thresholds: GaugeThresholds,
    accessibilityLabelPrefix: String,
    modifier: Modifier = Modifier
) {
    val displayValue = formatDisplayValue(valueG)
    val valueColor = valueZoneColor(valueG, thresholds)

    Surface(
        shape = MaterialTheme.shapes.small,
        color = Color(0xFFF8FAFC),
        tonalElevation = 1.dp,
        modifier = modifier.semantics {
            contentDescription = "$accessibilityLabelPrefix: $displayValue"
        }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            GaugeDial(
                valueG = valueG,
                thresholds = thresholds,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.42f)
            )
            Text(
                text = displayValue,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                color = Color(0xFF4F5D68)
            )
        }
    }
}

@Composable
private fun GaugeDial(
    valueG: Float,
    thresholds: GaugeThresholds,
    modifier: Modifier = Modifier
) {
    val needleRotation = DisplayAxisMapper.needleRotationDegrees(valueG, thresholds)
    val labels = listOf(
        GaugeMark(thresholds.negativeAlertG, -GaugeAlertStartRotation, GaugeAlertColor),
        GaugeMark(thresholds.negativeCautionG, -GaugeCautionStartRotation, Color(0xFF755900)),
        GaugeMark(0f, 0f, GaugeNeedleColor),
        GaugeMark(thresholds.positiveCautionG, GaugeCautionStartRotation, Color(0xFF755900)),
        GaugeMark(thresholds.positiveAlertG, GaugeAlertStartRotation, GaugeAlertColor)
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokeWidth = size.minDimension * 0.075f
            val radius = size.width * 0.42f
            val center = Offset(size.width / 2f, size.height * 0.82f)
            val arcRect = Rect(
                center = center,
                radius = radius
            )

            drawArc(
                color = GaugeTrackColor,
                startAngle = GaugeStartAngle,
                sweepAngle = GaugeSweepAngle,
                useCenter = false,
                topLeft = arcRect.topLeft,
                size = Size(arcRect.width, arcRect.height),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawGaugeZone(
                arcRect,
                -GaugeAlertStartRotation,
                -GaugeCautionStartRotation,
                GaugeCautionColor,
                strokeWidth
            )
            drawGaugeZone(
                arcRect,
                GaugeCautionStartRotation,
                GaugeAlertStartRotation,
                GaugeCautionColor,
                strokeWidth
            )
            drawGaugeZone(
                arcRect,
                -GaugeMaxRotation,
                -GaugeAlertStartRotation,
                GaugeAlertColor,
                strokeWidth
            )
            drawGaugeZone(
                arcRect,
                GaugeAlertStartRotation,
                GaugeMaxRotation,
                GaugeAlertColor,
                strokeWidth
            )

            for (degrees in listOf(-90f, -67.5f, -45f, -22.5f, 0f, 22.5f, 45f, 67.5f, 90f)) {
                val isMajor = degrees in listOf(-90f, -45f, 0f, 45f, 90f)
                drawTick(center, radius, degrees, isMajor, strokeWidth)
            }

            labels.forEach { mark ->
                drawGaugeLabel(center, radius * 0.72f, mark)
            }

            val needleEnd = pointOnGauge(center, radius * 0.78f, needleRotation)
            drawLine(
                color = GaugeNeedleColor,
                start = center,
                end = needleEnd,
                strokeWidth = strokeWidth * 0.34f,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = GaugeNeedleColor,
                radius = strokeWidth * 0.62f,
                center = center
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGaugeZone(
    arcRect: Rect,
    fromRotation: Float,
    toRotation: Float,
    color: Color,
    strokeWidth: Float
) {
    val start = GaugeStartAngle + 90f + fromRotation
    val sweep = toRotation - fromRotation
    drawArc(
        color = color,
        startAngle = start,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = arcRect.topLeft,
        size = Size(arcRect.width, arcRect.height),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTick(
    center: Offset,
    radius: Float,
    rotationDegrees: Float,
    major: Boolean,
    strokeWidth: Float
) {
    val outer = pointOnGauge(center, radius * 1.02f, rotationDegrees)
    val inner = pointOnGauge(center, radius * if (major) 0.86f else 0.91f, rotationDegrees)
    drawLine(
        color = GaugeNeedleColor,
        start = inner,
        end = outer,
        strokeWidth = if (major) strokeWidth * 0.22f else strokeWidth * 0.13f,
        cap = StrokeCap.Round
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGaugeLabel(
    center: Offset,
    radius: Float,
    mark: GaugeMark
) {
    val point = pointOnGauge(center, radius, mark.rotationDegrees)
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = mark.color.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = size.minDimension * 0.07f
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
            )
        }
        drawText(mark.label, point.x, point.y + paint.textSize * 0.32f, paint)
    }
}

private fun pointOnGauge(
    center: Offset,
    radius: Float,
    rotationDegrees: Float
): Offset {
    val angleRadians = Math.toRadians((270.0 + rotationDegrees).coerceIn(180.0, 360.0))
    return Offset(
        x = center.x + (cos(angleRadians) * radius).toFloat(),
        y = center.y + (sin(angleRadians) * radius).toFloat()
    )
}

private data class GaugeMark(
    val valueG: Float,
    val rotationDegrees: Float,
    val color: Color
) {
    val label: String = formatGaugeMark(valueG)
}

private fun formatGaugeMark(valueG: Float): String =
    if (valueG == 0f) {
        "0"
    } else {
        "%+.2f".format(valueG)
    }

private fun valueZoneColor(valueG: Float, thresholds: GaugeThresholds): Color {
    val magnitude = abs(valueG)
    val cautionThreshold = if (valueG < 0f) abs(thresholds.negativeCautionG)
                           else thresholds.positiveCautionG
    val alertThreshold = if (valueG < 0f) abs(thresholds.negativeAlertG)
                         else thresholds.positiveAlertG
    return when {
        magnitude >= alertThreshold -> GaugeAlertColor
        magnitude >= cautionThreshold -> GaugeCautionColor
        else -> Color.Black
    }
}

private fun formatDisplayValue(valueG: Float): String =
    if (valueG == 0f) {
        "0.00g"
    } else {
        "%+.2fg".format(valueG)
    }

private fun Color.toArgb(): Int =
    android.graphics.Color.argb(
        (alpha * 255).roundToInt(),
        (red * 255).roundToInt(),
        (green * 255).roundToInt(),
        (blue * 255).roundToInt()
    )

@Preview(showBackground = true, name = "Longitudinal Zero")
@Composable
private fun PreviewLongitudinalZeroGauge() {
    DriveMonitorTheme {
        GForceGauge(
            valueG = 0f,
            label = "Braking / Acceleration",
            thresholds = GaugeThresholds(
                negativeCautionG = -ThresholdConfig.BRAKING_CAUTION_G,
                negativeAlertG = -ThresholdConfig.BRAKING_ALERT_G,
                positiveCautionG = ThresholdConfig.ACCELERATION_CAUTION_G,
                positiveAlertG = ThresholdConfig.ACCELERATION_ALERT_G
            ),
            accessibilityLabelPrefix = "Acceleration and braking G force",
            modifier = Modifier.size(width = 180.dp, height = 150.dp)
        )
    }
}

@Preview(showBackground = true, name = "Longitudinal Acceleration")
@Composable
private fun PreviewAccelerationGauge() {
    DriveMonitorTheme {
        GForceGauge(
            valueG = ThresholdConfig.ACCELERATION_CAUTION_G,
            label = "Braking / Acceleration",
            thresholds = GaugeThresholds(
                negativeCautionG = -ThresholdConfig.BRAKING_CAUTION_G,
                negativeAlertG = -ThresholdConfig.BRAKING_ALERT_G,
                positiveCautionG = ThresholdConfig.ACCELERATION_CAUTION_G,
                positiveAlertG = ThresholdConfig.ACCELERATION_ALERT_G
            ),
            accessibilityLabelPrefix = "Acceleration and braking G force",
            modifier = Modifier.size(width = 180.dp, height = 150.dp)
        )
    }
}

@Preview(showBackground = true, name = "Longitudinal Braking")
@Composable
private fun PreviewBrakingGauge() {
    DriveMonitorTheme {
        GForceGauge(
            valueG = -ThresholdConfig.BRAKING_ALERT_G,
            label = "Braking / Acceleration",
            thresholds = GaugeThresholds(
                negativeCautionG = -ThresholdConfig.BRAKING_CAUTION_G,
                negativeAlertG = -ThresholdConfig.BRAKING_ALERT_G,
                positiveCautionG = ThresholdConfig.ACCELERATION_CAUTION_G,
                positiveAlertG = ThresholdConfig.ACCELERATION_ALERT_G
            ),
            accessibilityLabelPrefix = "Acceleration and braking G force",
            modifier = Modifier.size(width = 180.dp, height = 150.dp)
        )
    }
}

@Preview(showBackground = true, name = "Lateral Left")
@Composable
private fun PreviewLeftTurnGauge() {
    DriveMonitorTheme {
        GForceGauge(
            valueG = -ThresholdConfig.TURNING_CAUTION_G,
            label = "Left / Right",
            thresholds = GaugeThresholds(
                negativeCautionG = -ThresholdConfig.TURNING_CAUTION_G,
                negativeAlertG = -ThresholdConfig.TURNING_ALERT_G,
                positiveCautionG = ThresholdConfig.TURNING_CAUTION_G,
                positiveAlertG = ThresholdConfig.TURNING_ALERT_G
            ),
            accessibilityLabelPrefix = "Left and right G force",
            modifier = Modifier.size(width = 180.dp, height = 150.dp)
        )
    }
}

@Preview(showBackground = true, name = "Lateral Right")
@Composable
private fun PreviewRightTurnGauge() {
    DriveMonitorTheme {
        GForceGauge(
            valueG = ThresholdConfig.TURNING_ALERT_G,
            label = "Left / Right",
            thresholds = GaugeThresholds(
                negativeCautionG = -ThresholdConfig.TURNING_CAUTION_G,
                negativeAlertG = -ThresholdConfig.TURNING_ALERT_G,
                positiveCautionG = ThresholdConfig.TURNING_CAUTION_G,
                positiveAlertG = ThresholdConfig.TURNING_ALERT_G
            ),
            accessibilityLabelPrefix = "Left and right G force",
            modifier = Modifier.size(width = 180.dp, height = 150.dp)
        )
    }
}
