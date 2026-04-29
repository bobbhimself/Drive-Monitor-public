package com.bobbhimself.drivemonitor.data.local

import androidx.room.TypeConverter
import com.bobbhimself.drivemonitor.data.model.AlertSeverity
import com.bobbhimself.drivemonitor.data.model.MotionCategory

class Converters {
    @TypeConverter
    fun fromMotionCategory(value: MotionCategory): String = value.name

    @TypeConverter
    fun toMotionCategory(value: String): MotionCategory = MotionCategory.valueOf(value)

    @TypeConverter
    fun fromAlertSeverity(value: AlertSeverity): String = value.name

    @TypeConverter
    fun toAlertSeverity(value: String): AlertSeverity = AlertSeverity.valueOf(value)
}
