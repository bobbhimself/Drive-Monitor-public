package com.bobbhimself.drivemonitor.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bobbhimself.drivemonitor.DriveMonitorApp
import com.bobbhimself.drivemonitor.data.export.LogXlsxExporter
import com.bobbhimself.drivemonitor.data.model.TripEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LogViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as DriveMonitorApp).tripLogRepository

    val events: StateFlow<List<TripEvent>> = repo.events
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _exportResults = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val exportResults: SharedFlow<Boolean> = _exportResults

    private val _clearResults = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val clearResults: SharedFlow<Boolean> = _clearResults

    fun exportXlsx(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val templateBytes = getApplication<Application>()
                    .assets.open("drive_monitor_template.xltx").readBytes()
                val xlsx = LogXlsxExporter.buildXlsx(templateBytes, events.value)
                contentResolver.openOutputStream(uri)?.use { it.write(xlsx) }
                    ?: error("openOutputStream returned null")
                _exportResults.emit(true)
            } catch (e: Exception) {
                Log.e("LogViewModel", "XLSX export failed", e)
                _exportResults.emit(false)
            }
        }
    }

    fun clearLog() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.clearLog()
                _clearResults.emit(true)
            } catch (e: Exception) {
                Log.e("LogViewModel", "Clear log failed", e)
                _clearResults.emit(false)
            }
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LogViewModel(application as DriveMonitorApp) as T
    }
}
