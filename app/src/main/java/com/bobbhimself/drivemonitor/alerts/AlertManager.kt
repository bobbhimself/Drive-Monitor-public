package com.bobbhimself.drivemonitor.alerts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.bobbhimself.drivemonitor.R
import com.bobbhimself.drivemonitor.data.model.MotionCategory

class AlertManager(private val context: Context) {

    // Channel IDs
    private val CHANNEL_ONGOING     = "drive_monitoring_ongoing"
    private val CHANNEL_CAUTION     = "drive_monitoring_caution_events"
    private val CHANNEL_ALERT       = "drive_monitoring_alert_events"
    private val CHANNEL_CAUTION_V2  = "drive_monitoring_caution_events_v2"
    private val CHANNEL_ALERT_V2    = "drive_monitoring_alert_events_v2"

    // Notification IDs
    val NOTIFICATION_ID_ONGOING       = 1000
    val NOTIFICATION_ID_BRAKING       = 2001
    val NOTIFICATION_ID_ACCELERATION  = 2002
    val NOTIFICATION_ID_TURNING       = 2003

    // Ongoing notification content
    private val ONGOING_TITLE         = "Drive Monitor"
    private val ONGOING_BODY          = "Trip monitoring is active"
    private val ACTION_LABEL_END_TRIP = "End Trip"

    // Braking notification content
    private val BRAKING_CAUTION_TITLE = "Braking \u2014 Caution"
    private val BRAKING_CAUTION_BODY  = "Hard braking detected"
    private val BRAKING_ALERT_TITLE   = "Braking \u2014 Alert"
    private val BRAKING_ALERT_BODY    = "Severe braking detected"

    // Acceleration notification content
    private val ACCELERATION_CAUTION_TITLE = "Acceleration \u2014 Caution"
    private val ACCELERATION_CAUTION_BODY  = "Hard acceleration detected"
    private val ACCELERATION_ALERT_TITLE   = "Acceleration \u2014 Alert"
    private val ACCELERATION_ALERT_BODY    = "Severe acceleration detected"

    // Turning notification content
    private val TURNING_CAUTION_TITLE = "Turning \u2014 Caution"
    private val TURNING_CAUTION_BODY  = "Hard turn detected"
    private val TURNING_ALERT_TITLE   = "Turning \u2014 Alert"
    private val TURNING_ALERT_BODY    = "Severe turn detected"

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    private fun createChannels() {
        val ongoing = NotificationChannel(
            CHANNEL_ONGOING,
            "Ongoing Monitoring",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val cautionUri = Uri.parse("android.resource://${context.packageName}/raw/sound_caution")
        val caution = NotificationChannel(
            CHANNEL_CAUTION_V2,
            "Caution Events",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            setSound(cautionUri, audioAttrs)
            enableVibration(false)
            setShowBadge(false)
        }

        val alertUri = Uri.parse("android.resource://${context.packageName}/raw/sound_alert")
        val alert = NotificationChannel(
            CHANNEL_ALERT_V2,
            "Alert Events",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(alertUri, audioAttrs)
            enableVibration(true)
            setShowBadge(false)
        }

        notificationManager.createNotificationChannels(listOf(ongoing, caution, alert))

        // Clean up old channels that used system-default sounds
        notificationManager.deleteNotificationChannel(CHANNEL_CAUTION)
        notificationManager.deleteNotificationChannel(CHANNEL_ALERT)
    }

    fun postCautionNotification(category: MotionCategory) {
        val (id, title, body) = when (category) {
            MotionCategory.BRAKING      -> Triple(NOTIFICATION_ID_BRAKING,     BRAKING_CAUTION_TITLE,      BRAKING_CAUTION_BODY)
            MotionCategory.ACCELERATION -> Triple(NOTIFICATION_ID_ACCELERATION, ACCELERATION_CAUTION_TITLE, ACCELERATION_CAUTION_BODY)
            MotionCategory.TURNING      -> Triple(NOTIFICATION_ID_TURNING,      TURNING_CAUTION_TITLE,      TURNING_CAUTION_BODY)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_CAUTION_V2)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(id, notification)
    }

    fun postAlertNotification(category: MotionCategory) {
        val (id, title, body) = when (category) {
            MotionCategory.BRAKING      -> Triple(NOTIFICATION_ID_BRAKING,     BRAKING_ALERT_TITLE,      BRAKING_ALERT_BODY)
            MotionCategory.ACCELERATION -> Triple(NOTIFICATION_ID_ACCELERATION, ACCELERATION_ALERT_TITLE, ACCELERATION_ALERT_BODY)
            MotionCategory.TURNING      -> Triple(NOTIFICATION_ID_TURNING,      TURNING_ALERT_TITLE,      TURNING_ALERT_BODY)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT_V2)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(id, notification)
    }

    fun buildOngoingNotification(stopAction: PendingIntent): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(ONGOING_TITLE)
            .setContentText(ONGOING_BODY)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, ACTION_LABEL_END_TRIP, stopAction)
            .build()
    }
}
