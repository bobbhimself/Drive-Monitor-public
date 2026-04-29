package com.bobbhimself.drivemonitor.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bobbhimself.drivemonitor.data.model.AlertSeverity
import com.bobbhimself.drivemonitor.data.model.MotionCategory
import com.bobbhimself.drivemonitor.data.model.TripEvent
import com.bobbhimself.drivemonitor.ui.theme.DriveMonitorTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private fun formatTimestamp(utcMillis: Long): String {
    val dateSdf = SimpleDateFormat("yyyy-MM-dd h:mm:ss", Locale.getDefault())
    dateSdf.timeZone = TimeZone.getDefault()
    val amPmSdf = SimpleDateFormat("a z", Locale.getDefault())
    amPmSdf.timeZone = TimeZone.getDefault()
    val centiseconds = (utcMillis % 1000) / 10
    return "${dateSdf.format(Date(utcMillis))}.%02d ${amPmSdf.format(Date(utcMillis))}".format(centiseconds)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    events: List<TripEvent>,
    onNavigateBack: () -> Unit,
    onExport: (Uri) -> Unit,
    onClearLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri: Uri? ->
        uri?.let { onExport(it) }
    }

    var showClearConfirmDialog by remember { mutableStateOf(false) }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear log?") },
            text = { Text("This will permanently delete all recorded events.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearLog()
                    showClearConfirmDialog = false
                }) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Trip Log") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Event list area
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (events.isEmpty()) {
                    Text(
                        text = "No events recorded.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(events) { event ->
                            val timestamp = formatTimestamp(event.timestampUtcMillis)
                            val category = event.category.name
                                .lowercase()
                                .replaceFirstChar { it.uppercaseChar() }
                            val severity = event.severity.name
                                .lowercase()
                                .replaceFirstChar { it.uppercaseChar() }
                            Text(
                                text = "$timestamp — $category $severity",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Action buttons — suppressed when log is empty
            if (events.isNotEmpty()) {
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showClearConfirmDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear Log")
                    }
                    Button(
                        onClick = { exportLauncher.launch("drive_monitor_log.xlsx") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Export")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewLogScreenEmpty() {
    DriveMonitorTheme {
        LogScreen(events = emptyList(), onNavigateBack = {}, onExport = {}, onClearLog = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewLogScreenWithEvents() {
    DriveMonitorTheme {
        LogScreen(
            events = listOf(
                TripEvent(System.currentTimeMillis(), MotionCategory.BRAKING, AlertSeverity.ALERT),
                TripEvent(System.currentTimeMillis() - 5000, MotionCategory.TURNING, AlertSeverity.CAUTION),
                TripEvent(System.currentTimeMillis() - 10000, MotionCategory.ACCELERATION, AlertSeverity.CAUTION),
            ),
            onNavigateBack = {},
            onExport = {},
            onClearLog = {}
        )
    }
}
