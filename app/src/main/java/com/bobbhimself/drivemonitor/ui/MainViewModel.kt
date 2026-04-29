package com.bobbhimself.drivemonitor.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bobbhimself.drivemonitor.DriveMonitorApp
import com.bobbhimself.drivemonitor.data.model.LiveTelemetryState
import com.bobbhimself.drivemonitor.data.model.MonitoringState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as DriveMonitorApp).monitoringStateRepository
    val monitoringState: StateFlow<MonitoringState> = repo.monitoringState
    val liveTelemetryState: StateFlow<LiveTelemetryState> = repo.liveTelemetryState
    val userMessages: SharedFlow<String> = repo.userMessages

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = application as DriveMonitorApp
            return MainViewModel(app) as T
        }
    }
}
