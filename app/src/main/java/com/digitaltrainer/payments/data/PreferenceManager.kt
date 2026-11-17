package com.digitaltrainer.payments.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PreferenceManager(context: Context) {

    private val sharedPreferences: SharedPreferences = createEncryptedPrefs(context)

    companion object {
        private const val TAG = "PreferenceManager"
        private const val PREF_FILE_NAME = "dt_sms_forwarder_secure_prefs"
        private const val FALLBACK_PREF_FILE_NAME = "dt_sms_forwarder_prefs"

        private const val KEY_SERVICE_RUNNING = "key_service_running"
        private const val KEY_WEBHOOK_URL = "key_webhook_url"
        private const val KEY_GATEWAY_KEY = "key_gateway_key"
        private const val KEY_DEVICE_ID = "key_device_id"
        private const val KEY_LAST_PING_TIME = "key_last_ping_time"
        private const val KEY_LAST_ONLINE_STATE = "key_last_online_state"

        const val DEFAULT_WEBHOOK_URL = "https://digital-trainer-api.a2nnajmul.workers.dev/api/sms/gateway"

        const val STATE_UNKNOWN = "UNKNOWN"
        const val STATE_ONLINE = "ONLINE"
        const val STATE_OFFLINE = "OFFLINE"

        private fun createEncryptedPrefs(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    context,
                    PREF_FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                Log.w(TAG, "EncryptedSharedPreferences initialization failed, falling back to standard prefs: ${e.message}")
                context.getSharedPreferences(FALLBACK_PREF_FILE_NAME, Context.MODE_PRIVATE)
            }
        }
    }

    var isServiceRunning: Boolean
        get() = sharedPreferences.getBoolean(KEY_SERVICE_RUNNING, false)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_SERVICE_RUNNING, value).apply()
        }

    var webhookUrl: String
        get() = sharedPreferences.getString(KEY_WEBHOOK_URL, DEFAULT_WEBHOOK_URL) ?: DEFAULT_WEBHOOK_URL
        set(value) {
            val trimmed = value.trim()
            val finalUrl = if (trimmed.isNotEmpty()) trimmed else DEFAULT_WEBHOOK_URL
            sharedPreferences.edit().putString(KEY_WEBHOOK_URL, finalUrl).apply()
        }

    var gatewayKey: String
        get() = sharedPreferences.getString(KEY_GATEWAY_KEY, "") ?: ""
        set(value) {
            sharedPreferences.edit().putString(KEY_GATEWAY_KEY, value.trim()).apply()
        }

    var deviceId: String
        get() {
            var id = sharedPreferences.getString(KEY_DEVICE_ID, "")?.trim()?.lowercase() ?: ""
            if (id.isEmpty()) {
                // Auto-generate clean unique device ID on first install/launch: e.g. phone-a7b3
                id = generateDefaultDeviceId()
                sharedPreferences.edit().putString(KEY_DEVICE_ID, id).apply()
            }
            return id
        }
        set(value) {
            sharedPreferences.edit().putString(KEY_DEVICE_ID, value.trim().lowercase()).apply()
        }

    fun generateDefaultDeviceId(): String {
        val randomSuffix = java.util.UUID.randomUUID().toString().replace("-", "").take(4).lowercase()
        return "phone-$randomSuffix"
    }

    var lastPingTime: Long
        get() = sharedPreferences.getLong(KEY_LAST_PING_TIME, 0L)
        set(value) {
            sharedPreferences.edit().putLong(KEY_LAST_PING_TIME, value).apply()
        }

    var lastOnlineState: String
        get() = sharedPreferences.getString(KEY_LAST_ONLINE_STATE, STATE_UNKNOWN) ?: STATE_UNKNOWN
        set(value) {
            sharedPreferences.edit().putString(KEY_LAST_ONLINE_STATE, value).apply()
        }
}
