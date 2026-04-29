package com.bobbhimself.drivemonitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bobbhimself.drivemonitor.service.DriveMonitorService
import com.bobbhimself.drivemonitor.ui.DriveMonitorLaunchGate
import com.bobbhimself.drivemonitor.ui.LogScreen
import com.bobbhimself.drivemonitor.ui.LogViewModel
import com.bobbhimself.drivemonitor.ui.MainScreen
import com.bobbhimself.drivemonitor.ui.MainViewModel
import com.bobbhimself.drivemonitor.ui.theme.DriveMonitorTheme
import kotlinx.coroutines.launch

private const val PREFS_NAME = "drive_monitor_prefs"
private const val PREF_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
private const val SNACKBAR_NOTIFICATION_DENIED =
    "Notifications are disabled — enable in Settings for driving alerts"
private const val SNACKBAR_EXPORT_SUCCESS = "Export saved successfully"
private const val SNACKBAR_EXPORT_FAILED = "Export failed — try again"
private const val SNACKBAR_CLEAR_SUCCESS = "Log cleared"
private const val SNACKBAR_CLEAR_FAILED = "Clear failed — try again"

class MainActivity : ComponentActivity() {

    private lateinit var mainViewModel: MainViewModel
    private lateinit var logViewModel: LogViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel = ViewModelProvider(
            this,
            MainViewModel.Factory(application)
        )[MainViewModel::class.java]
        logViewModel = ViewModelProvider(
            this,
            LogViewModel.Factory(application)
        )[LogViewModel::class.java]

        enableEdgeToEdge()
        setContent {
            DriveMonitorTheme {
                DriveMonitorLaunchGate {
                    val navController = rememberNavController()
                    val monitoringState by mainViewModel.monitoringState.collectAsState()
                    val liveTelemetryState by mainViewModel.liveTelemetryState.collectAsState()
                    val events by logViewModel.events.collectAsState()
                    val snackbarHostState = remember { SnackbarHostState() }
                    val coroutineScope = rememberCoroutineScope()

                    val notificationPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        if (!isGranted) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(SNACKBAR_NOTIFICATION_DENIED)
                            }
                        }
                    }

                    LaunchedEffect(Unit) {
                        mainViewModel.userMessages.collect { message ->
                            snackbarHostState.showSnackbar(message)
                        }
                    }

                    LaunchedEffect(Unit) {
                        logViewModel.exportResults.collect { success ->
                            val message = if (success) SNACKBAR_EXPORT_SUCCESS else SNACKBAR_EXPORT_FAILED
                            snackbarHostState.showSnackbar(message)
                        }
                    }

                    LaunchedEffect(Unit) {
                        logViewModel.clearResults.collect { success ->
                            val message = if (success) SNACKBAR_CLEAR_SUCCESS else SNACKBAR_CLEAR_FAILED
                            snackbarHostState.showSnackbar(message)
                        }
                    }

                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val alreadyGranted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                                    PackageManager.PERMISSION_GRANTED
                            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                            val alreadyRequested = prefs.getBoolean(PREF_NOTIFICATION_PERMISSION_REQUESTED, false)
                            if (!alreadyGranted && !alreadyRequested) {
                                prefs.edit().putBoolean(PREF_NOTIFICATION_PERMISSION_REQUESTED, true).apply()
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    }

                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        modifier = Modifier.fillMaxSize()
                    ) { _ ->
                        NavHost(
                            navController = navController,
                            startDestination = "main",
                            modifier = Modifier.fillMaxSize()
                        ) {
                            composable("main") {
                                MainScreen(
                                    state = monitoringState,
                                    liveTelemetryState = liveTelemetryState,
                                    onStartTrip = {
                                        val intent = android.content.Intent(
                                            this@MainActivity,
                                            DriveMonitorService::class.java
                                        ).apply {
                                            action = DriveMonitorService.ACTION_START_MONITORING
                                        }
                                        startForegroundService(intent)
                                    },
                                    onEndTrip = {
                                        val intent = android.content.Intent(
                                            this@MainActivity,
                                            DriveMonitorService::class.java
                                        ).apply {
                                            action = DriveMonitorService.ACTION_STOP_MONITORING
                                        }
                                        startService(intent)
                                    },
                                    onViewLog = { navController.navigate("log") }
                                )
                            }
                            composable("log") {
                                LogScreen(
                                    events = events,
                                    onNavigateBack = { navController.popBackStack() },
                                    onExport = { uri -> logViewModel.exportXlsx(contentResolver, uri) },
                                    onClearLog = { logViewModel.clearLog() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
