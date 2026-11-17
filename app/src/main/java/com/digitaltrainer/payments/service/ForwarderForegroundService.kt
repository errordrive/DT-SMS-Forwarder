package com.digitaltrainer.payments.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.digitaltrainer.payments.data.PreferenceManager
import com.digitaltrainer.payments.data.SmsRepository
import com.digitaltrainer.payments.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ForwarderForegroundService : Service() {

    private lateinit var notificationHelper: NotificationHelper
    private lateinit var prefManager: PreferenceManager
    private lateinit var repository: SmsRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var pingJob: Job? = null

    companion object {
        private const val ACTION_START = "com.digitaltrainer.payments.action.START"
        private const val ACTION_STOP = "com.digitaltrainer.payments.action.STOP"
        private const val PING_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes (§4)

        fun start(context: Context) {
            val intent = Intent(context, ForwarderForegroundService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ForwarderForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        prefManager = PreferenceManager(this)
        repository = SmsRepository(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopPingLoop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = notificationHelper.buildForegroundNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID_FOREGROUND,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID_FOREGROUND, notification)
        }

        startPingLoop()
        repository.scheduleHeartbeatBackupWorker()

        return START_STICKY
    }

    private fun startPingLoop() {
        if (pingJob?.isActive == true) return

        pingJob = serviceScope.launch {
            while (isActive) {
                // Only ping while service is running and credentials exist (§4)
                if (prefManager.isServiceRunning &&
                    prefManager.deviceId.isNotEmpty() &&
                    prefManager.gatewayKey.isNotEmpty()
                ) {
                    repository.pingHeartbeat()
                }
                delay(PING_INTERVAL_MS)
            }
        }
    }

    private fun stopPingLoop() {
        pingJob?.cancel()
        pingJob = null
        repository.cancelHeartbeatBackupWorker()
    }

    override fun onDestroy() {
        stopPingLoop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
