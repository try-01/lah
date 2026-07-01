package com.screenm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.screenm.local.SessionManager
import com.screenm.model.DeviceInfo

class ScreenCaptureService : Service() {
    private lateinit var sessionManager: SessionManager

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> handleStop()
            null -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        val mediaProjectionIntent =
            if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(EXTRA_MEDIA_PROJECTION, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_MEDIA_PROJECTION)
            }
                ?: run {
                    Log.e(TAG, "Missing MediaProjection intent")
                    stopSelf()
                    return
                }

        val targetIp =
            intent.getStringExtra(EXTRA_TARGET_IP) ?: run {
                Log.e(TAG, "Missing target IP")
                stopSelf()
                return
            }

        val device =
            DeviceInfo(
                id = intent.getStringExtra(EXTRA_TARGET_MAC) ?: targetIp,
                name = intent.getStringExtra(EXTRA_TARGET_NAME) ?: "Samsung TV",
                ipAddress = targetIp,
                macAddress = intent.getStringExtra(EXTRA_TARGET_MAC) ?: "",
                port = intent.getIntExtra(EXTRA_TARGET_PORT, 8001),
            )

        sessionManager.startSession(mediaProjectionIntent, device)
    }

    private fun handleStop() {
        sessionManager.stopSession()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Screen Mirroring",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Screen mirroring is active"
                    setShowBadge(false)
                }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("screenM")
            .setContentText("Screen mirroring is active")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sessionManager.stopSession()
        super.onDestroy()
    }

    companion object {
        const val TAG = "ScreenCaptureService"
        const val ACTION_START = "com.screenm.action.START"
        const val ACTION_STOP = "com.screenm.action.STOP"
        const val EXTRA_MEDIA_PROJECTION = "media_projection"
        const val EXTRA_TARGET_IP = "target_ip"
        const val EXTRA_TARGET_MAC = "target_mac"
        const val EXTRA_TARGET_PORT = "target_port"
        const val EXTRA_TARGET_NAME = "target_name"
        const val BROADCAST_STATE = "com.screenm.state.CHANGED"

        private const val CHANNEL_ID = "screen_mirroring"
        private const val NOTIFICATION_ID = 1001
    }
}
