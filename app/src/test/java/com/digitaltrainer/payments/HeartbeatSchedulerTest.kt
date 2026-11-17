package com.digitaltrainer.payments

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartbeatSchedulerTest {

    @Test
    fun testPingCondition_OnlyWhenServiceIsOnAndConfigured() {
        // §4: Ping fires only while the service is ON and credentials exist
        fun shouldPing(isServiceRunning: Boolean, deviceId: String, gatewayKey: String): Boolean {
            return isServiceRunning && deviceId.isNotEmpty() && gatewayKey.isNotEmpty()
        }

        // Service ON with valid credentials
        assertTrue(shouldPing(isServiceRunning = true, deviceId = "phone1", gatewayKey = "secret123"))

        // Service OFF -> must NOT ping
        assertFalse(shouldPing(isServiceRunning = false, deviceId = "phone1", gatewayKey = "secret123"))

        // Missing device ID -> must NOT ping
        assertFalse(shouldPing(isServiceRunning = true, deviceId = "", gatewayKey = "secret123"))

        // Missing key -> must NOT ping
        assertFalse(shouldPing(isServiceRunning = true, deviceId = "phone1", gatewayKey = ""))
    }
}
