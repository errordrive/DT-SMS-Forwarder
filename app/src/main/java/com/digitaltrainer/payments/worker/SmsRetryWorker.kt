package com.digitaltrainer.payments.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.digitaltrainer.payments.data.PreferenceManager
import com.digitaltrainer.payments.data.SmsRepository

class SmsRetryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefManager = PreferenceManager(applicationContext)

        // If the service switch is OFF, keep queued items and exit
        if (!prefManager.isServiceRunning) {
            return Result.success()
        }

        val repository = SmsRepository(applicationContext)
        val pendingLogs = repository.getPendingLogs()

        if (pendingLogs.isEmpty()) {
            return Result.success()
        }

        var anyFailed = false

        for (log in pendingLogs) {
            val result = repository.forwardLog(log)
            if (result.isFailure) {
                anyFailed = true
            }
        }

        return if (anyFailed && runAttemptCount < 10) {
            Result.retry()
        } else {
            Result.success()
        }
    }
}
