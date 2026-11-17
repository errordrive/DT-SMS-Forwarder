package com.digitaltrainer.payments.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.digitaltrainer.payments.data.PreferenceManager
import com.digitaltrainer.payments.data.SmsLog
import com.digitaltrainer.payments.data.SmsRepository
import com.digitaltrainer.payments.service.ForwarderForegroundService
import com.digitaltrainer.payments.util.ProviderFilter
import com.digitaltrainer.payments.util.UrlHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefManager = PreferenceManager(application)
    private val repository = SmsRepository(application)

    private val _isServiceRunning = MutableLiveData(prefManager.isServiceRunning)
    val isServiceRunning: LiveData<Boolean> = _isServiceRunning

    private val _serverOnlineState = MutableLiveData(prefManager.lastOnlineState)
    val serverOnlineState: LiveData<String> = _serverOnlineState

    private val _lastPingTime = MutableLiveData(prefManager.lastPingTime)
    val lastPingTime: LiveData<Long> = _lastPingTime

    val recentLogs: Flow<List<SmsLog>> = repository.getRecentLogs(50)
    val lastForwardedStamp: Flow<Long?> = repository.getLastForwardedStamp()

    val bkashTodayCount: Flow<Int> = repository.getTodayCount(ProviderFilter.SENDER_BKASH)
    val nagadTodayCount: Flow<Int> = repository.getTodayCount(ProviderFilter.SENDER_NAGAD)
    val rocketTodayCount: Flow<Int> = repository.getTodayCount(ProviderFilter.SENDER_ROCKET)

    fun refreshPingStatus() {
        _serverOnlineState.value = prefManager.lastOnlineState
        _lastPingTime.value = prefManager.lastPingTime
    }

    fun setServiceRunning(running: Boolean) {
        prefManager.isServiceRunning = running
        _isServiceRunning.value = running
        if (running) {
            ForwarderForegroundService.start(getApplication())
            repository.scheduleRetryWork()
        } else {
            ForwarderForegroundService.stop(getApplication())
            _serverOnlineState.value = PreferenceManager.STATE_OFFLINE
            prefManager.lastOnlineState = PreferenceManager.STATE_OFFLINE
        }
    }

    fun getWebhookUrl(): String = prefManager.webhookUrl

    fun getGatewayKey(): String = prefManager.gatewayKey

    fun getDeviceId(): String = prefManager.deviceId

    fun generateNewDeviceId(): String {
        val newId = prefManager.generateDefaultDeviceId()
        prefManager.deviceId = newId
        return newId
    }

    fun saveSettings(url: String, key: String, deviceId: String): Result<Unit> {
        val deviceIdValidation = UrlHelper.sanitizeAndValidateDeviceId(deviceId)
        if (deviceIdValidation.isFailure) {
            return Result.failure(deviceIdValidation.exceptionOrNull() ?: IllegalArgumentException("Invalid Device ID"))
        }

        val sanitizedDevice = deviceIdValidation.getOrThrow()
        prefManager.webhookUrl = url
        prefManager.gatewayKey = key
        prefManager.deviceId = sanitizedDevice
        return Result.success(Unit)
    }

    fun testConnection(
        url: String,
        key: String,
        deviceId: String,
        onResult: (gatewayMsg: String, heartbeatMsg: String, isSuccess: Boolean) -> Unit
    ) {
        val deviceValidation = UrlHelper.sanitizeAndValidateDeviceId(deviceId)
        if (deviceValidation.isFailure) {
            onResult("Device ID required", "Device ID required", false)
            return
        }
        val validDeviceId = deviceValidation.getOrThrow()

        viewModelScope.launch {
            val (gatewayResult, heartbeatResult) = repository.testConnection(url, key, validDeviceId)

            val gatewayMsg = if (gatewayResult.isSuccess) {
                "Gateway: OK"
            } else {
                "Gateway: failed (${gatewayResult.exceptionOrNull()?.message})"
            }

            val heartbeatMsg = if (heartbeatResult.isSuccess) {
                "Heartbeat: OK (online)"
            } else {
                "Heartbeat: failed (${heartbeatResult.exceptionOrNull()?.message})"
            }

            val isSuccess = gatewayResult.isSuccess && heartbeatResult.isSuccess

            if (heartbeatResult.isSuccess) {
                _serverOnlineState.value = PreferenceManager.STATE_ONLINE
                _lastPingTime.value = System.currentTimeMillis()
            }

            onResult(gatewayMsg, heartbeatMsg, isSuccess)
        }
    }
}
