package com.digitaltrainer.payments.util

object UrlHelper {

    private val DEVICE_ID_REGEX = Regex("^[a-z0-9-]+$")

    /**
     * Validates and sanitizes device ID:
     * - Trims leading/trailing whitespace
     * - Lowercases all characters
     * - Checks for non-empty
     * - Enforces format: lowercase letters, numbers, and dashes only
     */
    fun sanitizeAndValidateDeviceId(raw: String?): Result<String> {
        val sanitized = raw?.trim()?.lowercase() ?: ""
        if (sanitized.isEmpty()) {
            return Result.failure(IllegalArgumentException("Device ID is required"))
        }
        if (!DEVICE_ID_REGEX.matches(sanitized)) {
            return Result.failure(
                IllegalArgumentException("Device ID must contain only lowercase letters, numbers, and dashes (e.g. phone1)")
            )
        }
        return Result.success(sanitized)
    }

    /**
     * Builds the full webhook URL (§3):
     * `base + "?key=" + <GATEWAY_KEY> + "&device=" + <DEVICE_ID>`
     * If the base URL already contains a `?`, uses `&` for the first parameter instead.
     */
    fun buildGatewayUrl(baseUrl: String, key: String, deviceId: String): String {
        val trimmedBase = baseUrl.trim()
        val sep = if (trimmedBase.contains("?")) "&" else "?"
        val cleanDevice = deviceId.trim().lowercase()
        return "$trimmedBase${sep}key=$key&device=$cleanDevice"
    }

    /**
     * Derives heartbeat ping endpoint URL from webhook base URL (§1 & §4):
     * e.g. `https://digital-trainer-api.a2nnajmul.workers.dev/api/devices/ping`
     */
    fun buildPingUrl(webhookBaseUrl: String, key: String, deviceId: String): String {
        val trimmed = webhookBaseUrl.trim()
        val cleanDevice = deviceId.trim().lowercase()
        val pingBase = if (trimmed.contains("/api/sms/gateway")) {
            trimmed.replace("/api/sms/gateway", "/api/devices/ping")
        } else {
            val schemeEnd = trimmed.indexOf("://")
            val hostStart = if (schemeEnd != -1) schemeEnd + 3 else 0
            val pathStart = trimmed.indexOf('/', hostStart)
            val domain = if (pathStart != -1) trimmed.substring(0, pathStart) else trimmed
            "$domain/api/devices/ping"
        }

        val sep = if (pingBase.contains("?")) "&" else "?"
        return "$pingBase${sep}key=$key&device=$cleanDevice"
    }
}
