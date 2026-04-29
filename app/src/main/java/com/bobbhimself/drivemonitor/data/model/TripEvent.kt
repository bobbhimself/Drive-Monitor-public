package com.bobbhimself.drivemonitor.data.model

data class TripEvent(
    val timestampUtcMillis: Long,
    val category: MotionCategory,
    val severity: AlertSeverity
)
