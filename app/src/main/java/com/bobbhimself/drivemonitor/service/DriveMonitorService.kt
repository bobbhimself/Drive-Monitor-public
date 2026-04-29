package com.bobbhimself.drivemonitor.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ServiceCompat
import com.bobbhimself.drivemonitor.DriveMonitorApp
import com.bobbhimself.drivemonitor.data.model.AlertSeverity
import com.bobbhimself.drivemonitor.data.model.DirectionalAlertTracker
import com.bobbhimself.drivemonitor.data.model.TripEvent
import com.bobbhimself.drivemonitor.sensors.MotionProcessor
import com.bobbhimself.drivemonitor.sensors.MotionSensorManager
import com.bobbhimself.drivemonitor.sensors.ThresholdEvaluator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DriveMonitorService : Service() {

    companion object {
        const val ACTION_START_MONITORING = "com.bobbhimself.drivemonitor.ACTION_START_MONITORING"
        const val ACTION_STOP_MONITORING  = "com.bobbhimself.drivemonitor.ACTION_STOP_MONITORING"
        private const val TAG = "DriveMonitorService"
        private const val SNACKBAR_CALIBRATION_FAILED =
            "Calibration failed — keep the phone still and try again"
        private const val SNACKBAR_SENSOR_UNAVAILABLE =
            "Motion sensor unavailable — cannot start monitoring"
    }

    private val alertManager get() = (application as DriveMonitorApp).alertManager
    private val monitoringStateRepository get() = (application as DriveMonitorApp).monitoringStateRepository
    private val tripLogRepository get() = (application as DriveMonitorApp).tripLogRepository

    // Dispatcher for finalized-event side effects (notification posting, future Room writes).
    // Keeps the sensor hot path (MotionSensorThread) free of blocking work.
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile private var isRunning = false
    @Volatile private var firstProcessedSampleLogged = false
    @Volatile private var latestLateralG = 0f
    private var motionSensorManager: MotionSensorManager? = null
    private var motionProcessor: MotionProcessor? = null
    private var thresholdEvaluator: ThresholdEvaluator? = null
    private val directionalAlertTracker = DirectionalAlertTracker()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")
        when (intent?.action) {
            ACTION_START_MONITORING -> handleStart()
            ACTION_STOP_MONITORING  -> handleStop()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "onDestroy")
    }

    private fun handleStart() {
        if (isRunning) {
            Log.d(TAG, "handleStart: already running, ignoring")
            return
        }
        isRunning = true
        latestLateralG = 0f
        directionalAlertTracker.clear()

        val stopIntent = Intent(this, DriveMonitorService::class.java).apply {
            action = ACTION_STOP_MONITORING
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )
        val notification = alertManager.buildOngoingNotification(stopPendingIntent)
        ServiceCompat.startForeground(
            this,
            alertManager.NOTIFICATION_ID_ONGOING,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        Log.d(TAG, "Entered foreground — beginning calibration")

        monitoringStateRepository.setCalibrating()

        val evaluator = ThresholdEvaluator(
            onEventStarted = { event ->
                val lateralG = latestLateralG
                mainHandler.post {
                    val alert = directionalAlertTracker.start(
                        category = event.category,
                        severity = event.severity,
                        lateralG = lateralG
                    )
                    monitoringStateRepository.upsertDirectionalAlert(alert)
                    when (event.severity) {
                        AlertSeverity.CAUTION -> alertManager.postCautionNotification(event.category)
                        AlertSeverity.ALERT   -> alertManager.postAlertNotification(event.category)
                    }
                }
            },
            onEventEscalated = { event ->
                val lateralG = latestLateralG
                mainHandler.post {
                    val alert = directionalAlertTracker.escalate(
                        category = event.category,
                        lateralG = lateralG
                    )
                    monitoringStateRepository.upsertDirectionalAlert(alert)
                    alertManager.postAlertNotification(event.category)
                }
            },
            onEventFinalized = { event ->
                mainHandler.post {
                    directionalAlertTracker.finalize(event.category)?.let { direction ->
                        monitoringStateRepository.removeDirectionalAlert(direction)
                    }
                    persistFinalizedEvent(event)
                }
            }
        )
        thresholdEvaluator = evaluator

        val processor = MotionProcessor(
            onCalibrationComplete = ::onCalibrationComplete,
            onCalibrationFailed   = ::onCalibrationFailed,
            onMotionData          = { longitudinalG, lateralG, verticalG, timestampMillis ->
                if (!firstProcessedSampleLogged) {
                    firstProcessedSampleLogged = true
                    Log.d(TAG, "First processed motion sample received")
                }
                latestLateralG = lateralG
                monitoringStateRepository.publishLiveTelemetry(longitudinalG, lateralG)
                thresholdEvaluator?.process(longitudinalG, lateralG, verticalG, timestampMillis)
            }
        )
        motionProcessor = processor

        val manager = MotionSensorManager(
            context       = this,
            onSensorData  = { x, y, z, ts -> motionProcessor?.processSample(x, y, z, ts) },
            onSensorError = ::onSensorError
        )
        motionSensorManager = manager

        // register() returns false and fires onSensorError if sensor is unavailable
        manager.register()
    }

    private fun handleStop() {
        mainHandler.removeCallbacksAndMessages(null)
        isRunning = false
        motionSensorManager?.unregister()
        motionSensorManager = null
        motionProcessor = null
        thresholdEvaluator = null
        firstProcessedSampleLogged = false
        latestLateralG = 0f
        directionalAlertTracker.clear()
        monitoringStateRepository.resetLiveTelemetry()
        monitoringStateRepository.setInactive()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "Stopped foreground and self")
    }

    private fun onCalibrationComplete() {
        if (!isRunning) return
        monitoringStateRepository.setActive()
        Log.d(TAG, "Calibration complete — monitoring active")
    }

    private fun onCalibrationFailed(message: String) {
        if (!isRunning) return
        Log.d(TAG, "Calibration failed: $message")
        monitoringStateRepository.emitUserMessage(SNACKBAR_CALIBRATION_FAILED)
        motionSensorManager?.unregister()
        motionSensorManager = null
        motionProcessor = null
        thresholdEvaluator = null
        isRunning = false
        latestLateralG = 0f
        directionalAlertTracker.clear()
        monitoringStateRepository.resetLiveTelemetry()
        monitoringStateRepository.setInactive()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun onSensorError(message: String) {
        if (!isRunning) return
        Log.d(TAG, "Sensor error: $message")
        monitoringStateRepository.emitUserMessage(SNACKBAR_SENSOR_UNAVAILABLE)
        motionSensorManager?.unregister()
        motionSensorManager = null
        motionProcessor = null
        thresholdEvaluator = null
        isRunning = false
        latestLateralG = 0f
        directionalAlertTracker.clear()
        monitoringStateRepository.resetLiveTelemetry()
        monitoringStateRepository.setInactive()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun persistFinalizedEvent(event: TripEvent) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                tripLogRepository.insert(event)
                Log.d(TAG, "Persisted finalized event: ${event.category} severity=${event.severity}")
            } catch (error: Exception) {
                Log.e(
                    TAG,
                    "Failed to persist finalized event: ${event.category} severity=${event.severity}",
                    error
                )
            }
        }
    }
}
