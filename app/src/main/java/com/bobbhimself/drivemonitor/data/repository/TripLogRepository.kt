package com.bobbhimself.drivemonitor.data.repository

import com.bobbhimself.drivemonitor.data.local.TripEventDao
import com.bobbhimself.drivemonitor.data.local.TripEventEntity
import com.bobbhimself.drivemonitor.data.model.TripEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TripLogRepository(private val dao: TripEventDao) {

    val events: Flow<List<TripEvent>> = dao.getAllNewestFirst().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun insert(event: TripEvent) {
        dao.insert(event.toEntity())
    }

    suspend fun clearLog() {
        dao.deleteAll()
    }
}

private fun TripEventEntity.toDomain(): TripEvent = TripEvent(
    timestampUtcMillis = timestampUtcMillis,
    category = category,
    severity = severity
)

private fun TripEvent.toEntity(): TripEventEntity = TripEventEntity(
    timestampUtcMillis = timestampUtcMillis,
    category = category,
    severity = severity
)
