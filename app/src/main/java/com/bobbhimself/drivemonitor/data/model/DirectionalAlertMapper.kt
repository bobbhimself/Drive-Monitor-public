package com.bobbhimself.drivemonitor.data.model

object DirectionalAlertMapper {

    fun directionFor(category: MotionCategory, lateralG: Float): AlertDirection =
        when (category) {
            MotionCategory.ACCELERATION -> AlertDirection.FRONT
            MotionCategory.BRAKING -> AlertDirection.REAR
            MotionCategory.TURNING -> turningDirectionFor(lateralG)
        }

    private fun turningDirectionFor(lateralG: Float): AlertDirection =
        if (lateralG < 0f) AlertDirection.LEFT else AlertDirection.RIGHT
}
