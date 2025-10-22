package com.smsgateway.app.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object dla operacji na wiadomościach SMS
 */
@Dao
interface SmsDao {
    
    /**
     * Wstawia nową wiadomość SMS do bazy danych
     */
    @Insert
    suspend fun insertSms(smsMessage: SmsMessage): Long
    
    /**
     * Wstawia wiele wiadomości SMS do bazy danych
     */
    @Insert
    suspend fun insertMultipleSms(smsMessages: List<SmsMessage>): List<Long>
    
    /**
     * Aktualizuje istniejącą wiadomość SMS
     */
    @Update
    suspend fun updateSms(smsMessage: SmsMessage)
    
    /**
     * Usuwa wiadomość SMS z bazy danych
     */
    @Delete
    suspend fun deleteSms(smsMessage: SmsMessage)
    
    /**
     * Usuwa wiadomość SMS po ID
     */
    @Query("DELETE FROM sms_messages WHERE id = :id")
    suspend fun deleteSmsById(id: Long): Int
    
    /**
     * Pobiera wiadomość SMS po ID
     */
    @Query("SELECT * FROM sms_messages WHERE id = :id")
    suspend fun getSmsById(id: Long): SmsMessage?
    
    /**
     * Pobiera wszystkie wiadomości SMS jako Flow
     */
    @Query("SELECT * FROM sms_messages ORDER BY created_at DESC")
    fun getAllSms(): Flow<List<SmsMessage>>
    
    /**
     * Pobiera wszystkie wiadomości SMS (synchronicznie)
     */
    @Query("SELECT * FROM sms_messages ORDER BY created_at DESC")
    suspend fun getAllSmsSync(): List<SmsMessage>
    
    /**
     * Pobiera wiadomości SMS z określonym statusem
     */
    @Query("SELECT * FROM sms_messages WHERE status = :status ORDER BY created_at DESC")
    suspend fun getSmsByStatus(status: SmsStatus): List<SmsMessage>
    
    /**
     * Pobiera wiadomości SMS z określonym statusem jako Flow
     */
    @Query("SELECT * FROM sms_messages WHERE status = :status ORDER BY created_at DESC")
    fun getSmsByStatusFlow(status: SmsStatus): Flow<List<SmsMessage>>
    
    /**
     * Pobiera wiadomości zaplanowane do wysyłki (status SCHEDULED)
     */
    @Query("SELECT * FROM sms_messages WHERE status = 'SCHEDULED' AND scheduled_at <= :timestamp ORDER BY scheduled_at ASC")
    suspend fun getScheduledForSending(timestamp: Long): List<SmsMessage>
    
    /**
     * Pobiera wiadomości w kolejce do wysyłki (status QUEUED)
     */
    @Query("SELECT * FROM sms_messages WHERE status = 'QUEUED' ORDER BY created_at ASC")
    suspend fun getQueuedSms(): List<SmsMessage>
    
    /**
     * Pobiera wiadomości, które nie powiodły się i można je ponowić
     */
    @Query("SELECT * FROM sms_messages WHERE status = 'FAILED' AND retry_count < max_retries ORDER BY created_at ASC")
    suspend fun getRetryableFailedSms(): List<SmsMessage>
    
    /**
     * Aktualizuje status wiadomości SMS
     */
    @Query("UPDATE sms_messages SET status = :status WHERE id = :id")
    suspend fun updateSmsStatus(id: Long, status: SmsStatus): Int
    
    /**
     * Aktualizuje status i datę wysyłki
     */
    @Query("UPDATE sms_messages SET status = :status, sent_at = :sentAt WHERE id = :id")
    suspend fun updateSmsStatusWithSentTime(id: Long, status: SmsStatus, sentAt: Long): Int
    
    /**
     * Aktualizuje status, datę wysyłki i wiadomość błędu
     */
    @Query("UPDATE sms_messages SET status = :status, sent_at = :sentAt, error_message = :errorMessage WHERE id = :id")
    suspend fun updateSmsStatusWithError(id: Long, status: SmsStatus, sentAt: Long, errorMessage: String?): Int
    
    /**
     * Zwiększa licznik prób ponowienia
     */
    @Query("UPDATE sms_messages SET retry_count = retry_count + 1 WHERE id = :id")
    suspend fun incrementRetryCount(id: Long): Int
    
    /**
     * Pobiera wiadomości z ostatnich N dni
     */
    @Query("SELECT * FROM sms_messages WHERE created_at >= :timestamp ORDER BY created_at DESC")
    suspend fun getSmsFromLastDays(timestamp: Long): List<SmsMessage>
    
    /**
     * Pobiera liczbę wiadomości według statusu
     */
    @Query("SELECT COUNT(*) FROM sms_messages WHERE status = :status")
    suspend fun getSmsCountByStatus(status: SmsStatus): Int
    
    /**
     * Pobiera statystyki wiadomości z ostatnich 24 godzin
     */
    @Query("""
        SELECT status, COUNT(*) as count 
        FROM sms_messages 
        WHERE created_at >= :timestamp 
        GROUP BY status
    """)
    suspend fun getSmsStatsLast24Hours(timestamp: Long): List<StatusCount>
    
    /**
     * Czyści stare wiadomości (starsze niż określony timestamp)
     */
    @Query("DELETE FROM sms_messages WHERE created_at < :timestamp AND status IN ('SENT', 'CANCELLED')")
    suspend fun cleanupOldMessages(timestamp: Long): Int
}

/**
 * Klasa danych dla statystyk statusów
 */
data class StatusCount(
    val status: String,
    val count: Int
)