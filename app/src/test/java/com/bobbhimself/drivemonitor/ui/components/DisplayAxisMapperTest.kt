package com.bobbhimself.drivemonitor.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayAxisMapperTest {

    private val thresholds = GaugeThresholds(
        negativeCautionG = -0.13f,
        negativeAlertG = -0.26f,
        positiveCautionG = 0.24f,
        positiveAlertG = 0.31f
    )

    @Test
    fun zero_mapsToStraightUpNeedle() {
        assertEquals(
            0f,
            DisplayAxisMapper.needleRotationDegrees(valueG = 0f, thresholds = thresholds),
            0.001f
        )
    }

    @Test
    fun accelerationCaution_mapsToPositiveYellowGreyTransition() {
        assertEquals(
            54f,
            DisplayAxisMapper.needleRotationDegrees(
                valueG = 0.24f,
                thresholds = thresholds
            ),
            0.001f
        )
    }

    @Test
    fun accelerationAlert_mapsToPositiveRedYellowTransition() {
        assertEquals(
            76.5f,
            DisplayAxisMapper.needleRotationDegrees(
                valueG = 0.31f,
                thresholds = thresholds
            ),
            0.001f
        )
    }

    @Test
    fun brakingCaution_mapsToNegativeYellowGreyTransition() {
        assertEquals(
            -54f,
            DisplayAxisMapper.needleRotationDegrees(
                valueG = -0.13f,
                thresholds = thresholds
            ),
            0.001f
        )
    }

    @Test
    fun brakingAlert_mapsToNegativeRedYellowTransition() {
        assertEquals(
            -76.5f,
            DisplayAxisMapper.needleRotationDegrees(
                valueG = -0.26f,
                thresholds = thresholds
            ),
            0.001f
        )
    }

    @Test
    fun turningThresholds_mapToConfiguredZoneTransitions() {
        val turningThresholds = GaugeThresholds(
            negativeCautionG = -0.26f,
            negativeAlertG = -0.35f,
            positiveCautionG = 0.26f,
            positiveAlertG = 0.35f
        )

        assertEquals(
            -54f,
            DisplayAxisMapper.needleRotationDegrees(
                valueG = -0.26f,
                thresholds = turningThresholds
            ),
            0.001f
        )
        assertEquals(
            -76.5f,
            DisplayAxisMapper.needleRotationDegrees(
                valueG = -0.35f,
                thresholds = turningThresholds
            ),
            0.001f
        )
        assertEquals(
            54f,
            DisplayAxisMapper.needleRotationDegrees(
                valueG = 0.26f,
                thresholds = turningThresholds
            ),
            0.001f
        )
        assertEquals(
            76.5f,
            DisplayAxisMapper.needleRotationDegrees(
                valueG = 0.35f,
                thresholds = turningThresholds
            ),
            0.001f
        )
    }

    @Test
    fun valuesBetweenCautionAndAlert_interpolateWithinYellowZone() {
        assertEquals(
            65.25f,
            DisplayAxisMapper.needleRotationDegrees(
                valueG = 0.275f,
                thresholds = thresholds
            ),
            0.001f
        )
    }

    @Test
    fun valuesAboveAlert_interpolateWithinRedZone() {
        assertEquals(
            90f,
            DisplayAxisMapper.needleRotationDegrees(
                valueG = 0.31f / 0.85f,
                thresholds = thresholds
            ),
            0.001f
        )
    }

    @Test
    fun positiveMovementStillMapsNeedleRight() {
        val rotation = DisplayAxisMapper.needleRotationDegrees(
            valueG = 0.24f,
            thresholds = thresholds
        )

        assertEquals(54f, rotation, 0.001f)
    }

    @Test
    fun negativeMovementStillMapsNeedleLeft() {
        val rotation = DisplayAxisMapper.needleRotationDegrees(
            valueG = -0.13f,
            thresholds = thresholds
        )

        assertEquals(-54f, rotation, 0.001f)
    }

    @Test
    fun valuesClampAtGaugeLimits() {
        assertEquals(
            90f,
            DisplayAxisMapper.needleRotationDegrees(valueG = 1.5f, thresholds = thresholds),
            0.001f
        )
        assertEquals(
            -90f,
            DisplayAxisMapper.needleRotationDegrees(valueG = -1.5f, thresholds = thresholds),
            0.001f
        )
    }

    @Test
    fun lateralDisplayKeepsNegativeLeftAndPositiveRight() {
        assertEquals(
            -76.5f,
            DisplayAxisMapper.lateralNeedleRotationDegrees(
                lateralG = -0.35f,
                alertLimitG = 0.35f
            ),
            0.001f
        )
        assertEquals(
            76.5f,
            DisplayAxisMapper.lateralNeedleRotationDegrees(
                lateralG = 0.35f,
                alertLimitG = 0.35f
            ),
            0.001f
        )
    }
}
