package com.bobbhimself.drivemonitor.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread

class MotionSensorManager(
    private val context: Context,
    private val onSensorData: (x: Float, y: Float, z: Float, timestampNanos: Long) -> Unit,
    private val onSensorError: (message: String) -> Unit
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private var handlerThread: HandlerThread? = null
    private var sensorHandler: Handler? = null

    /**
     * Registers the linear-acceleration listener.
     * Returns true if registration succeeded, false if the sensor is unavailable.
     * The caller must treat false as a critical failure and stop monitoring.
     */
    fun register(): Boolean {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        if (sensor == null) {
            onSensorError("TYPE_LINEAR_ACCELERATION not available on this device")
            return false
        }
        val ht = HandlerThread("MotionSensorThread").also { it.start() }
        handlerThread = ht
        sensorHandler = Handler(ht.looper)
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME, sensorHandler!!)
        return true
    }

    /**
     * Unregisters the listener and shuts down the sensor thread.
     * Safe to call multiple times.
     */
    fun unregister() {
        if (handlerThread == null) return
        sensorManager.unregisterListener(this)
        handlerThread?.quitSafely()
        handlerThread = null
        sensorHandler = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        // Extract values to locals before callback — SensorEvent.values is a recycled buffer.
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val ts = event.timestamp   // nanoseconds, system uptime clock
        onSensorData(x, y, z, ts)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Only surface genuine hardware failures. LOW/MEDIUM/HIGH fluctuations are ignored.
        // Unregistering on accuracy change is the service's responsibility, not ours.
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            onSensorError("Sensor accuracy unreliable (status=$accuracy)")
        }
    }
}
