package com.bobbhimself.drivemonitor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TripEventDao {
    @Insert
    suspend fun insert(entity: TripEventEntity)

    @Query("SELECT * FROM trip_events ORDER BY timestampUtcMillis DESC")
    fun getAllNewestFirst(): Flow<List<TripEventEntity>>

    @Query("DELETE FROM trip_events")
    suspend fun deleteAll()
}
