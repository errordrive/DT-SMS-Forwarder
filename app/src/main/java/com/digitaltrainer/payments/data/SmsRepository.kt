package com.digitaltrainer.payments.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.digitaltrainer.payments.api.NetworkClient
import com.digitaltrainer.payments.api.SmsGatewayRequest
import com.digitaltrainer.payments.util.NotificationHelper
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

    /**
     * Inserts incoming SMS with deduplication by (from, text, sentStamp).
     * Returns the created or existing SmsLog, and a boolean indicating if it is newly created.
     */
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
     * Forwards an SmsLog to the configured server.
     * Retries via WorkManager on failure (§3).
     */
    suspend fun forwardLog(smsLog: SmsLog): Result<String> = withContext(Dispatchers.IO) {
        val url = prefManager.webhookUrl
        val key = prefManager.gatewayKey

        val request = SmsGatewayRequest(
            from = smsLog.from,
            text = smsLog.text,
            sentStamp = smsLog.sentStamp,
            receivedStamp = smsLog.receivedStamp,
            sim = smsLog.sim
        )

        try {
            val response = NetworkClient.api.forwardSms(
                url = url,
                key = key,
                gatewayKeyHeader = key,
                body = request
            )

            val code = response.code()
            // Success = any HTTP 2xx (§3)
            if (response.isSuccessful) {
                val responseText = response.body()?.string() ?: "OK"
                smsLog.status = SmsLog.STATUS_SENT
                smsLog.serverResponse = "HTTP $code: $responseText"
                smsLog.lastAttemptStamp = System.currentTimeMillis()
                dao.update(smsLog)

                // Show notification for forwarded payment (§5)
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

    suspend fun getPendingLogs(): List<SmsLog> = withContext(Dispatchers.IO) {
        dao.getPendingLogs()
    }

    suspend fun testConnection(url: String, key: String): Result<Pair<Int, String>> = withContext(Dispatchers.IO) {
        val dummy = SmsGatewayRequest(
            from = "bKash",
            text = "TEST_PING",
            sentStamp = System.currentTimeMillis(),
            receivedStamp = System.currentTimeMillis(),
            sim = "undetected"
        )
        try {
            val response = NetworkClient.api.forwardSms(
                url = url,
                key = key,
                gatewayKeyHeader = key,
                body = dummy
            )
            val code = response.code()
            val body = if (response.isSuccessful) {
                response.body()?.string() ?: "OK"
            } else {
                response.errorBody()?.string() ?: "Error code $code"
            }
            if (response.isSuccessful) {
                Result.success(Pair(code, body))
            } else {
                Result.failure(Exception("HTTP $code: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
