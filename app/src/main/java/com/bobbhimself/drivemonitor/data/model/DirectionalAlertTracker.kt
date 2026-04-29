package com.bobbhimself.drivemonitor.data.model

class DirectionalAlertTracker {

    private val activeDirectionsByCategory = mutableMapOf<MotionCategory, AlertDirection>()

    fun start(
        category: MotionCategory,
        severity: AlertSeverity,
        lateralG: Float
    ): DirectionalAlert {
        val direction = DirectionalAlertMapper.directionFor(category, lateralG)
        activeDirectionsByCategory[category] = direction
        return DirectionalAlert(direction, severity)
    }

    fun escalate(
        category: MotionCategory,
        lateralG: Float
    ): DirectionalAlert {
        val direction = activeDirectionsByCategory.getOrPut(category) {
            DirectionalAlertMapper.directionFor(category, lateralG)
        }
        return DirectionalAlert(direction, AlertSeverity.ALERT)
    }

    fun finalize(category: MotionCategory): AlertDirection? =
        activeDirectionsByCategory.remove(category)

    fun clear() {
        activeDirectionsByCategory.clear()
    }
}
