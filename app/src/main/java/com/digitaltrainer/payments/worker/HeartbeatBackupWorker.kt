package com.digitaltrainer.payments.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.digitaltrainer.payments.data.PreferenceManager
import com.digitaltrainer.payments.data.SmsRepository

class HeartbeatBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefManager = PreferenceManager(applicationContext)

        // Only ping if service switch is ON (§4)
        if (!prefManager.isServiceRunning) {
            return Result.success()
        }

        if (prefManager.deviceId.isEmpty() || prefManager.gatewayKey.isEmpty()) {
            return Result.success()
        }

        val repository = SmsRepository(applicationContext)
        val result = repository.pingHeartbeat()

        return if (result.isSuccess) {
            Result.success()
        } else {
            // Backup worker failure does not trigger retry spam
            Result.success()
        }
    }
}
