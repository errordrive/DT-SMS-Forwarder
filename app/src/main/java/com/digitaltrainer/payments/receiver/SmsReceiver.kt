package com.digitaltrainer.payments.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager
import android.util.Log
import com.digitaltrainer.payments.data.PreferenceManager
import com.digitaltrainer.payments.data.SmsLog
import com.digitaltrainer.payments.data.SmsRepository
import com.digitaltrainer.payments.util.ProviderFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages: Array<SmsMessage>? = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            return
        }

        // Detect SIM slot (§3: "sim1", "sim2", or "undetected")
        val sim = detectSimSlot(context, intent)
        val receivedStamp = System.currentTimeMillis()

        // Group multipart messages by originating sender
        val senderMap = mutableMapOf<String, StringBuilder>()
        var sentStamp = 0L

        for (sms in messages) {
            val sender = sms.originatingAddress ?: continue
            // Strict filter (§4): drop silently if not in allowed list
            if (!ProviderFilter.isAllowed(sender)) {
                continue
            }
            if (!senderMap.containsKey(sender)) {
                senderMap[sender] = StringBuilder()
            }
            senderMap[sender]?.append(sms.messageBody ?: "")
            if (sentStamp == 0L) {
                sentStamp = sms.timestampMillis
            }
        }

        if (senderMap.isEmpty()) {
            // No allowed SMS in this broadcast
            return
        }

        if (sentStamp == 0L) {
            sentStamp = receivedStamp
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefManager = PreferenceManager(appContext)
                val repository = SmsRepository(appContext)
                val isServiceOn = prefManager.isServiceRunning

                for ((sender, bodyBuilder) in senderMap) {
                    val fullText = bodyBuilder.toString()
                    val initialStatus = if (isServiceOn) SmsLog.STATUS_QUEUED else SmsLog.STATUS_QUEUED

                    val (savedLog, isNew) = repository.saveIncomingSms(
                        from = sender,
                        text = fullText,
                        sentStamp = sentStamp,
                        receivedStamp = receivedStamp,
                        sim = sim,
                        initialStatus = initialStatus
                    )

                    // Only forward if newly queued and service is ON (§5)
                    if (isNew && isServiceOn) {
                        repository.forwardLog(savedLog)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing incoming SMS: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun detectSimSlot(context: Context, intent: Intent): String {
        try {
            val bundle = intent.extras ?: return "undetected"

            // Look for subscription ID
            val subId = when {
                bundle.containsKey("subscription") -> bundle.getInt("subscription", -1)
                bundle.containsKey("android.telephony.extra.SUBSCRIPTION_INDEX") ->
                    bundle.getInt("android.telephony.extra.SUBSCRIPTION_INDEX", -1)
                bundle.containsKey("sub_id") -> bundle.getInt("sub_id", -1)
                else -> -1
            }

            if (subId != -1) {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val slotIndex = subscriptionManager?.let { sm ->
                    try {
                        val activeInfo = sm.getActiveSubscriptionInfo(subId)
                        activeInfo?.simSlotIndex
                    } catch (e: SecurityException) {
                        null
                    }
                }
                if (slotIndex != null) {
                    return if (slotIndex == 0) "sim1" else if (slotIndex == 1) "sim2" else "undetected"
                }
            }

            // Direct slot index check
            val slot = when {
                bundle.containsKey("slot") -> bundle.getInt("slot", -1)
                bundle.containsKey("simSlot") -> bundle.getInt("simSlot", -1)
                bundle.containsKey("simId") -> bundle.getInt("simId", -1)
                bundle.containsKey("phone") -> bundle.getInt("phone", -1)
                else -> -1
            }

            return when (slot) {
                0 -> "sim1"
                1 -> "sim2"
                else -> "undetected"
            }
        } catch (e: Exception) {
            return "undetected"
        }
    }
}
