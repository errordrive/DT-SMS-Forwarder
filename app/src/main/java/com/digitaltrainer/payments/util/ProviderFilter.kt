package com.digitaltrainer.payments.util

import com.digitaltrainer.payments.R

object ProviderFilter {
    const val SENDER_BKASH = "bKash"
    const val SENDER_NAGAD = "NAGAD"
    const val SENDER_ROCKET = "16216"

    val ALLOWED_SENDERS: Set<String> = setOf(
        SENDER_BKASH,
        SENDER_NAGAD,
        SENDER_ROCKET
    )

    /**
     * Strict exact match filter. Returns true only for exact match
     * with bKash, NAGAD, or 16216. All other senders must be dropped silently.
     */
    fun isAllowed(sender: String?): Boolean {
        if (sender == null) return false
        return ALLOWED_SENDERS.contains(sender)
    }

    fun getDisplayName(sender: String): String {
        return when (sender) {
            SENDER_BKASH -> "bKash"
            SENDER_NAGAD -> "Nagad"
            SENDER_ROCKET -> "Rocket"
            else -> sender
        }
    }

    fun getLogoDrawableRes(sender: String): Int {
        return when (sender) {
            SENDER_BKASH -> R.drawable.bkash
            SENDER_NAGAD -> R.drawable.nagad
            SENDER_ROCKET -> R.drawable.rocket
            else -> R.drawable.ic_launcher
        }
    }

    fun getProviderColorRes(sender: String): Int {
        return when (sender) {
            SENDER_BKASH -> R.color.provider_bkash
            SENDER_NAGAD -> R.color.provider_nagad
            SENDER_ROCKET -> R.color.provider_rocket
            else -> R.color.primary
        }
    }
}
