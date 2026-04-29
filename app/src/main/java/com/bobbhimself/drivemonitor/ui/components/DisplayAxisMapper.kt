package com.bobbhimself.drivemonitor.ui.components

import kotlin.math.abs

data class GaugeThresholds(
    val negativeCautionG: Float,
    val negativeAlertG: Float,
    val positiveCautionG: Float,
    val positiveAlertG: Float
)

object DisplayAxisMapper {

    const val MAX_NEEDLE_ROTATION_DEGREES = 90f
    const val CAUTION_ZONE_START_ROTATION_DEGREES = MAX_NEEDLE_ROTATION_DEGREES * 0.60f
    const val ALERT_ZONE_START_ROTATION_DEGREES = MAX_NEEDLE_ROTATION_DEGREES * 0.85f
    private const val LATERAL_DISPLAY_SIGN = 1f
    private const val ALERT_ZONE_START_RATIO = 0.85f

    fun needleRotationDegrees(
        valueG: Float,
        thresholds: GaugeThresholds
    ): Float {
        val caution = if (valueG < 0f) {
            abs(thresholds.negativeCautionG)
        } else {
            thresholds.positiveCautionG
        }
        val alert = if (valueG < 0f) {
            abs(thresholds.negativeAlertG)
        } else {
            thresholds.positiveAlertG
        }
        val sign = if (valueG < 0f) -1f else 1f

        return sign * scaledRotationDegrees(
            magnitudeG = abs(valueG),
            cautionG = caution,
            alertG = alert
        )
    }

    fun lateralNeedleRotationDegrees(
        lateralG: Float,
        alertLimitG: Float
    ): Float {
        val sign = if (lateralG < 0f) -1f else 1f

        return sign * LATERAL_DISPLAY_SIGN * scaledRotationDegrees(
            magnitudeG = abs(lateralG),
            cautionG = 0f,
            alertG = abs(alertLimitG)
        )
    }

    private fun scaledRotationDegrees(
        magnitudeG: Float,
        cautionG: Float,
        alertG: Float
    ): Float {
        if (magnitudeG <= 0f || alertG <= 0f) return 0f

        val safeCautionG = cautionG.coerceIn(0f, alertG)
        val gaugeMaxG = alertG / ALERT_ZONE_START_RATIO
        val rotation = when {
            safeCautionG <= 0f || alertG <= safeCautionG -> {
                magnitudeG / gaugeMaxG * MAX_NEEDLE_ROTATION_DEGREES
            }
            magnitudeG <= safeCautionG -> {
                magnitudeG / safeCautionG * CAUTION_ZONE_START_ROTATION_DEGREES
            }
            magnitudeG <= alertG -> {
                CAUTION_ZONE_START_ROTATION_DEGREES +
                    (magnitudeG - safeCautionG) /
                    (alertG - safeCautionG) *
                    (ALERT_ZONE_START_ROTATION_DEGREES - CAUTION_ZONE_START_ROTATION_DEGREES)
            }
            else -> {
                ALERT_ZONE_START_ROTATION_DEGREES +
                    (magnitudeG - alertG) /
                    (gaugeMaxG - alertG) *
                    (MAX_NEEDLE_ROTATION_DEGREES - ALERT_ZONE_START_ROTATION_DEGREES)
            }
        }

        return rotation.coerceIn(0f, MAX_NEEDLE_ROTATION_DEGREES)
    }
}
