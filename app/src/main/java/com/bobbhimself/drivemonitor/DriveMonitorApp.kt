package com.bobbhimself.drivemonitor

import android.app.Application
import androidx.room.Room
import com.bobbhimself.drivemonitor.alerts.AlertManager
import com.bobbhimself.drivemonitor.data.local.AppDatabase
import com.bobbhimself.drivemonitor.data.repository.MonitoringStateRepository
import com.bobbhimself.drivemonitor.data.repository.TripLogRepository

class DriveMonitorApp : Application() {

    lateinit var monitoringStateRepository: MonitoringStateRepository
    lateinit var alertManager: AlertManager
    lateinit var appDatabase: AppDatabase
    lateinit var tripLogRepository: TripLogRepository

    override fun onCreate() {
        super.onCreate()
        monitoringStateRepository = MonitoringStateRepository()
        alertManager = AlertManager(this)
        appDatabase = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "drive_monitor.db"
        ).build()
        tripLogRepository = TripLogRepository(appDatabase.tripEventDao())
    }
}
