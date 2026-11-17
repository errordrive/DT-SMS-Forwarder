package com.digitaltrainer.payments.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sms_logs",
    indices = [
        Index(value = ["from", "text", "sentStamp"], unique = true)
    ]
)
data class SmsLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val from: String,
    val text: String,
    val sentStamp: Long,
    val receivedStamp: Long,
    val sim: String,

    var status: String, // "SENT", "QUEUED", "FAILED"
    var attemptCount: Int = 0,
    var lastAttemptStamp: Long = 0L,
    var serverResponse: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_SENT = "SENT"
        const val STATUS_QUEUED = "QUEUED"
        const val STATUS_FAILED = "FAILED"
    }
}
