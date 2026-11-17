package com.digitaltrainer.payments

import android.app.Application
import com.digitaltrainer.payments.data.PreferenceManager
import com.digitaltrainer.payments.service.ForwarderForegroundService
import com.digitaltrainer.payments.util.NotificationHelper

class DTApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize notification channels
        NotificationHelper(this)

        // Ensure service runs if configured to ON
        val prefManager = PreferenceManager(this)
        if (prefManager.isServiceRunning) {
            ForwarderForegroundService.start(this)
        }
    }
}
