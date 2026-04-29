package com.bobbhimself.drivemonitor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.bobbhimself.drivemonitor.data.model.AlertSeverity
import com.bobbhimself.drivemonitor.data.model.MotionCategory

@Entity(tableName = "trip_events")
data class TripEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampUtcMillis: Long,
    val category: MotionCategory,
    val severity: AlertSeverity
)
