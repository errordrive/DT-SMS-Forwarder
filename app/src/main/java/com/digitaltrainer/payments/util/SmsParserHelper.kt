package com.digitaltrainer.payments.util

import java.util.regex.Pattern

object SmsParserHelper {

    // Regex patterns for amounts
    private val BKASH_AMOUNT_PATTERN = Pattern.compile("""received\s+Tk\s*([\d,]+\.?\d*)""", Pattern.CASE_INSENSITIVE)
    private val NAGAD_AMOUNT_PATTERN = Pattern.compile("""Amount:\s*Tk\s*([\d,]+\.?\d*)""", Pattern.CASE_INSENSITIVE)
    private val ROCKET_AMOUNT_PATTERN = Pattern.compile("""Tk\s*([\d,]+\.?\d*)\s*received""", Pattern.CASE_INSENSITIVE)
    private val GENERIC_TK_PATTERN = Pattern.compile("""(?:Tk|৳)\s*([\d,]+\.?\d*)""", Pattern.CASE_INSENSITIVE)

    /**
     * Extracts amount string (e.g. "৳100.00") from SMS text.
     * Never returns the full SMS text.
     */
    fun extractAmount(sender: String, text: String): String {
        val pattern = when (sender) {
            ProviderFilter.SENDER_BKASH -> BKASH_AMOUNT_PATTERN
            ProviderFilter.SENDER_NAGAD -> NAGAD_AMOUNT_PATTERN
            ProviderFilter.SENDER_ROCKET -> ROCKET_AMOUNT_PATTERN
            else -> GENERIC_TK_PATTERN
        }

        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            val amount = matcher.group(1)?.trim() ?: ""
            return "৳$amount"
        }

        val fallbackMatcher = GENERIC_TK_PATTERN.matcher(text)
        if (fallbackMatcher.find()) {
            val amount = fallbackMatcher.group(1)?.trim() ?: ""
            return "৳$amount"
        }

        return ""
    }

    /**
     * Creates notification title / text per §5:
     * "Payment SMS forwarded — bKash ৳100.00"
     */
    fun buildNotificationText(sender: String, text: String): String {
        val providerName = ProviderFilter.getDisplayName(sender)
        val amount = extractAmount(sender, text)
        return if (amount.isNotEmpty()) {
            "Payment SMS forwarded — $providerName $amount"
        } else {
            "Payment SMS forwarded — $providerName"
        }
    }
}
