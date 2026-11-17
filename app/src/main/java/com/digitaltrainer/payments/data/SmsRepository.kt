package com.digitaltrainer.payments.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.digitaltrainer.payments.api.NetworkClient
import com.digitaltrainer.payments.api.SmsGatewayRequest
import com.digitaltrainer.payments.util.NotificationHelper
import com.digitaltrainer.payments.util.UrlHelper
import com.digitaltrainer.payments.worker.HeartbeatBackupWorker
import com.digitaltrainer.payments.worker.SmsRetryWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

class SmsRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.smsLogDao()
    private val prefManager = PreferenceManager(context)
    private val notificationHelper = NotificationHelper(context)

    fun getRecentLogs(limit: Int = 50): Flow<List<SmsLog>> = dao.getRecentLogs(limit)

    fun getLastForwardedStamp(): Flow<Long?> = dao.getLastForwardedStamp()

    fun getTodayCount(sender: String): Flow<Int> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfDay = calendar.timeInMillis

        return dao.getTodayCount(sender, startOfDay, endOfDay)
    }

    suspend fun saveIncomingSms(
        from: String,
        text: String,
        sentStamp: Long,
        receivedStamp: Long,
        sim: String,
        initialStatus: String = SmsLog.STATUS_QUEUED
    ): Pair<SmsLog, Boolean> = withContext(Dispatchers.IO) {
        val existing = dao.findDuplicate(from, text, sentStamp)
        if (existing != null) {
            return@withContext Pair(existing, false)
        }

        val newLog = SmsLog(
            from = from,
            text = text,
            sentStamp = sentStamp,
            receivedStamp = receivedStamp,
            sim = sim,
            status = initialStatus
        )
        val id = dao.insert(newLog)
        val savedLog = if (id > 0) newLog.copy(id = id) else (dao.findDuplicate(from, text, sentStamp) ?: newLog)
        Pair(savedLog, true)
    }

    /**
     * Forwards an SmsLog to the server (§3 & §5).
     * Passes key & device query parameters, header, and body field.
     */
    suspend fun forwardLog(smsLog: SmsLog): Result<String> = withContext(Dispatchers.IO) {
        val rawBaseUrl = prefManager.webhookUrl
        val key = prefManager.gatewayKey
        val deviceId = prefManager.deviceId

        val request = SmsGatewayRequest(
            from = smsLog.from,
            text = smsLog.text,
            sentStamp = smsLog.sentStamp,
            receivedStamp = smsLog.receivedStamp,
            sim = smsLog.sim,
            device = deviceId
        )

        try {
            val response = NetworkClient.api.forwardSms(
                url = rawBaseUrl,
                key = key,
                device = deviceId,
                gatewayKeyHeader = key,
                body = request
            )

            val code = response.code()
            if (response.isSuccessful) {
                val responseText = response.body()?.string() ?: "OK"
                smsLog.status = SmsLog.STATUS_SENT
                smsLog.serverResponse = "HTTP $code: $responseText"
                smsLog.lastAttemptStamp = System.currentTimeMillis()
                dao.update(smsLog)

                // Successfully reaching server also confirms phone is online
                prefManager.lastPingTime = System.currentTimeMillis()
                prefManager.lastOnlineState = PreferenceManager.STATE_ONLINE

                // Notification per §5
                notificationHelper.showPaymentForwardedNotification(smsLog.from, smsLog.text)

                Result.success(responseText)
            } else {
                val errorBody = response.errorBody()?.string() ?: "HTTP error $code"
                smsLog.status = if (smsLog.attemptCount >= 9) SmsLog.STATUS_FAILED else SmsLog.STATUS_QUEUED
                smsLog.attemptCount++
                smsLog.lastAttemptStamp = System.currentTimeMillis()
                smsLog.serverResponse = "HTTP $code: $errorBody"
                dao.update(smsLog)

                scheduleRetryWork()
                Result.failure(Exception("HTTP $code: $errorBody"))
            }
        } catch (e: Exception) {
            smsLog.status = if (smsLog.attemptCount >= 9) SmsLog.STATUS_FAILED else SmsLog.STATUS_QUEUED
            smsLog.attemptCount++
            smsLog.lastAttemptStamp = System.currentTimeMillis()
            smsLog.serverResponse = "Connection Error: ${e.message}"
            dao.update(smsLog)

            scheduleRetryWork()
            Result.failure(e)
        }
    }

    /**
     * Heartbeat ping every 5 minutes (§4).
     * GET /api/devices/ping?key=<GATEWAY_KEY>&device=<DEVICE_ID>
     */
    suspend fun pingHeartbeat(): Result<Boolean> = withContext(Dispatchers.IO) {
        val baseUrl = prefManager.webhookUrl
        val key = prefManager.gatewayKey
        val deviceId = prefManager.deviceId

        if (deviceId.isEmpty() || key.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Device ID or Gateway Key missing"))
        }

        val pingUrl = UrlHelper.buildPingUrl(baseUrl, key, deviceId)

        try {
            val response = NetworkClient.api.pingHeartbeat(
                url = pingUrl,
                key = key,
                device = deviceId,
                gatewayKeyHeader = key
            )

            val code = response.code()
            if (response.isSuccessful) {
                prefManager.lastPingTime = System.currentTimeMillis()
                prefManager.lastOnlineState = PreferenceManager.STATE_ONLINE
                Result.success(true)
            } else {
                // Per §4: keep showing last known state on single failed ping
                val err = response.errorBody()?.string() ?: "HTTP $code"
                Result.failure(Exception("HTTP $code: $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun scheduleRetryWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val retryRequest = OneTimeWorkRequestBuilder<SmsRetryWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "sms_retry_worker",
            ExistingWorkPolicy.REPLACE,
            retryRequest
        )
    }

    /**
     * Schedules 15-minute backup ping worker as backup only (§4).
     */
    fun scheduleHeartbeatBackupWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val backupPingRequest = PeriodicWorkRequestBuilder<HeartbeatBackupWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "heartbeat_backup_worker",
            ExistingPeriodicWorkPolicy.KEEP,
            backupPingRequest
        )
    }

    fun cancelHeartbeatBackupWorker() {
        WorkManager.getInstance(context).cancelUniqueWork("heartbeat_backup_worker")
    }

    suspend fun getPendingLogs(): List<SmsLog> = withContext(Dispatchers.IO) {
        dao.getPendingLogs()
    }

    /**
     * Dual test (§7):
     * 1. Gateway POST test
     * 2. Heartbeat GET ping test
     */
    suspend fun testConnection(
        url: String,
        key: String,
        deviceId: String
    ): Pair<Result<String>, Result<String>> = withContext(Dispatchers.IO) {
        // 1. Gateway POST
        val dummy = SmsGatewayRequest(
            from = "bKash",
            text = "TEST_PING",
            sentStamp = System.currentTimeMillis(),
            receivedStamp = System.currentTimeMillis(),
            sim = "undetected",
            device = deviceId
        )

        val gatewayResult = try {
            val response = NetworkClient.api.forwardSms(
                url = url,
                key = key,
                device = deviceId,
                gatewayKeyHeader = key,
                body = dummy
            )
            val code = response.code()
            val body = if (response.isSuccessful) {
                response.body()?.string() ?: "OK"
            } else {
                response.errorBody()?.string() ?: "HTTP $code"
            }
            if (response.isSuccessful) {
                Result.success("OK")
            } else {
                Result.failure(Exception("HTTP $code: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }

        // 2. Heartbeat GET
        val pingUrl = UrlHelper.buildPingUrl(url, key, deviceId)
        val heartbeatResult = try {
            val pingResponse = NetworkClient.api.pingHeartbeat(
                url = pingUrl,
                key = key,
                device = deviceId,
                gatewayKeyHeader = key
            )
            val code = pingResponse.code()
            val body = if (pingResponse.isSuccessful) {
                pingResponse.body()?.string() ?: "OK"
            } else {
                pingResponse.errorBody()?.string() ?: "HTTP $code"
            }
            if (pingResponse.isSuccessful) {
                Result.success("OK (online)")
            } else {
                Result.failure(Exception("HTTP $code: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }

        Pair(gatewayResult, heartbeatResult)
    }
}
