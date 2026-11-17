package com.digitaltrainer.payments.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsLogDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(smsLog: SmsLog): Long

    @Update
    suspend fun update(smsLog: SmsLog)

    @Query("SELECT * FROM sms_logs WHERE status != 'SENT' AND attemptCount < 10 ORDER BY createdAt ASC")
    suspend fun getPendingLogs(): List<SmsLog>

    @Query("SELECT * FROM sms_logs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SmsLog?

    @Query("SELECT * FROM sms_logs ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 50): Flow<List<SmsLog>>

    @Query("SELECT COUNT(*) FROM sms_logs WHERE `from` = :sender AND status = 'SENT' AND createdAt >= :startOfDay AND createdAt <= :endOfDay")
    fun getTodayCount(sender: String, startOfDay: Long, endOfDay: Long): Flow<Int>

    @Query("SELECT createdAt FROM sms_logs WHERE status = 'SENT' ORDER BY createdAt DESC LIMIT 1")
    fun getLastForwardedStamp(): Flow<Long?>

    @Query("SELECT * FROM sms_logs WHERE `from` = :from AND text = :text AND sentStamp = :sentStamp LIMIT 1")
    suspend fun findDuplicate(from: String, text: String, sentStamp: Long): SmsLog?
}
