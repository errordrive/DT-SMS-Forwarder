package com.digitaltrainer.payments.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.digitaltrainer.payments.data.PreferenceManager
import com.digitaltrainer.payments.service.ForwarderForegroundService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val prefManager = PreferenceManager(context)
            if (prefManager.isServiceRunning) {
                ForwarderForegroundService.start(context)
            }
        }
    }
}
