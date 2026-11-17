package com.digitaltrainer.payments.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.digitaltrainer.payments.data.PreferenceManager
import com.digitaltrainer.payments.data.SmsLog
import com.digitaltrainer.payments.data.SmsRepository
import com.digitaltrainer.payments.service.ForwarderForegroundService
import com.digitaltrainer.payments.util.ProviderFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefManager = PreferenceManager(application)
    private val repository = SmsRepository(application)

    private val _isServiceRunning = MutableLiveData(prefManager.isServiceRunning)
    val isServiceRunning: LiveData<Boolean> = _isServiceRunning

    val recentLogs: Flow<List<SmsLog>> = repository.getRecentLogs(50)
    val lastForwardedStamp: Flow<Long?> = repository.getLastForwardedStamp()

    val bkashTodayCount: Flow<Int> = repository.getTodayCount(ProviderFilter.SENDER_BKASH)
    val nagadTodayCount: Flow<Int> = repository.getTodayCount(ProviderFilter.SENDER_NAGAD)
    val rocketTodayCount: Flow<Int> = repository.getTodayCount(ProviderFilter.SENDER_ROCKET)

    fun setServiceRunning(running: Boolean) {
        prefManager.isServiceRunning = running
        _isServiceRunning.value = running
        if (running) {
            ForwarderForegroundService.start(getApplication())
            // Trigger retry for any queued items
            repository.scheduleRetryWork()
        } else {
            ForwarderForegroundService.stop(getApplication())
        }
    }

    fun getWebhookUrl(): String = prefManager.webhookUrl

    fun getGatewayKey(): String = prefManager.gatewayKey

    fun saveSettings(url: String, key: String) {
        prefManager.webhookUrl = url
        prefManager.gatewayKey = key
    }

    fun testConnection(
        url: String,
        key: String,
        onResult: (isSuccess: Boolean, message: String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.testConnection(url, key)
            if (result.isSuccess) {
                val (code, body) = result.getOrThrow()
                onResult(true, "HTTP $code: $body")
            } else {
                val err = result.exceptionOrNull()?.message ?: "Unknown error"
                onResult(false, err)
            }
        }
    }
}
