package com.bobbhimself.drivemonitor.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DirectionalAlertMapperTest {

    @Test
    fun acceleration_mapsToFront() {
        assertEquals(
            AlertDirection.FRONT,
            DirectionalAlertMapper.directionFor(MotionCategory.ACCELERATION, lateralG = 0.4f)
        )
    }

    @Test
    fun braking_mapsToRear() {
        assertEquals(
            AlertDirection.REAR,
            DirectionalAlertMapper.directionFor(MotionCategory.BRAKING, lateralG = -0.4f)
        )
    }

    @Test
    fun positiveLateralTurning_mapsToRight() {
        assertEquals(
            AlertDirection.RIGHT,
            DirectionalAlertMapper.directionFor(MotionCategory.TURNING, lateralG = 0.4f)
        )
    }

    @Test
    fun negativeLateralTurning_mapsToLeft() {
        assertEquals(
            AlertDirection.LEFT,
            DirectionalAlertMapper.directionFor(MotionCategory.TURNING, lateralG = -0.4f)
        )
    }
}
