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
     * Pobiera wiadomości SMS z paginacją
     */
    @Query("SELECT * FROM sms_messages ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getSmsWithPagination(limit: Int, offset: Int): List<SmsMessage>
    
    /**
     * Pobiera wiadomości SMS z paginacją i filtrowaniem po statusie
     */
    @Query("SELECT * FROM sms_messages WHERE status = :status ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getSmsWithPaginationAndStatus(status: String, limit: Int, offset: Int): List<SmsMessage>
    
    /**
     * Pobiera całkowitą liczbę wiadomości
     */
    @Query("SELECT COUNT(*) FROM sms_messages")
    suspend fun getSmsTotalCount(): Int
    
    /**
     * Pobiera liczbę wiadomości według statusu
     */
    @Query("SELECT COUNT(*) FROM sms_messages WHERE status = :status")
    suspend fun getSmsCountByStatusString(status: String): Int
    
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
     * Aktualizuje status z błędem i zwiększa licznik prób w jednej transakcji
     */
    @Transaction
    suspend fun updateSmsStatusWithErrorAndIncrementRetry(id: Long, status: SmsStatus, sentAt: Long, errorMessage: String?) {
        updateSmsStatusWithError(id, status, sentAt, errorMessage)
        incrementRetryCount(id)
    }
    
    /**
     * Przenosi wiadomość do kolejki i aktualizuje czas planowania
     */
    @Transaction
    suspend fun queueSmsMessage(id: Long, scheduledAt: Long) {
        // Ta metoda będzie rozbudowana w przyszłości
        updateSmsStatus(id, SmsStatus.QUEUED)
    }
    
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
    
    // === METODY DLA KOLEJKI PRIORYTETOWEJ ===
    
    /**
     * Pobiera maksymalną pozycję w kolejce dla danego priorytetu
     */
    @Query("SELECT MAX(queue_position) FROM sms_messages WHERE priority = :priority AND status = 'QUEUED'")
    suspend fun getMaxQueuePosition(priority: Int): Int?
    
    /**
     * Aktualizuje pozycję w kolejce
     */
    @Query("UPDATE sms_messages SET queue_position = :position WHERE id = :id")
    suspend fun updateQueuePosition(id: Long, position: Int?): Int
    
    /**
     * Pobiera następną wiadomość z kolejki (o najwyższym priorytecie)
     */
    @Query("""
        SELECT * FROM sms_messages
        WHERE status = 'QUEUED'
        ORDER BY priority DESC, queue_position ASC, created_at ASC
        LIMIT 1
    """)
    suspend fun getNextQueuedMessage(): SmsMessage?
    
    /**
     * Pobiera wiadomości w kolejce dla danego priorytetu
     */
    @Query("""
        SELECT * FROM sms_messages
        WHERE status = 'QUEUED' AND priority = :priority
        ORDER BY queue_position ASC, created_at ASC
    """)
    suspend fun getQueuedMessagesByPriority(priority: Int): List<SmsMessage>
    
    /**
     * Pobiera liczbę wiadomości w kolejce
     */
    @Query("SELECT COUNT(*) FROM sms_messages WHERE status = 'QUEUED'")
    suspend fun getQueuedMessagesCount(): Int
    
    /**
     * Pobiera liczbę wiadomości w kolejce dla danego priorytetu
     */
    @Query("SELECT COUNT(*) FROM sms_messages WHERE status = 'QUEUED' AND priority = :priority")
    suspend fun getQueuedMessagesCountByPriority(priority: Int): Int
    
    /**
     * Aktualizuje priorytet i pozycję wiadomości
     */
    @Query("UPDATE sms_messages SET priority = :priority, queue_position = :position WHERE id = :id")
    suspend fun updatePriorityAndPosition(id: Long, priority: SmsPriority, position: Int): Int
    
    /**
     * Czyści kolejkę dla danego priorytetu
     */
    @Query("UPDATE sms_messages SET status = 'CANCELLED', queue_position = NULL WHERE status = 'QUEUED' AND priority = :priority")
    suspend fun clearQueueByPriority(priority: Int): Int
    
    /**
     * Czyści całą kolejkę
     */
    @Query("UPDATE sms_messages SET status = 'CANCELLED', queue_position = NULL WHERE status = 'QUEUED'")
    suspend fun clearEntireQueue(): Int
    
    /**
     * Reorganizuje pozycje w kolejce - naprawia luki po usunięciach
     */
    @Transaction
    suspend fun reorganizeQueuePositions(): Int {
        val allQueuedMessages = getAllQueuedMessagesForReorganization()
        var updatedCount = 0
        
        // Grupuj po priorytecie i przypisz nowe pozycje
        val groupedByPriority = allQueuedMessages.groupBy { it.priority }
        
        for ((priority, messages) in groupedByPriority) {
            val priorityOffset = (5 - priority.value) * 10000
            messages.sortedBy { it.createdAt }.forEachIndexed { index, message ->
                val newPosition = priorityOffset + index + 1
                if (message.queuePosition != newPosition) {
                    updateQueuePosition(message.id, newPosition)
                    updatedCount++
                }
            }
        }
        
        return updatedCount
    }
    
    /**
     * Pobiera wszystkie wiadomości w kolejce do reorganizacji
     */
    @Query("SELECT * FROM sms_messages WHERE status = 'QUEUED' ORDER BY priority DESC, created_at ASC")
    suspend fun getAllQueuedMessagesForReorganization(): List<SmsMessage>
    
    /**
     * Pobiera czas najstarszej wiadomości w kolejce
     */
    @Query("SELECT MIN(created_at) FROM sms_messages WHERE status = 'QUEUED'")
    suspend fun getOldestQueuedMessageTime(): Long?
    
    /**
     * Pobiera czas najnowszej wiadomości w kolejce
     */
    @Query("SELECT MAX(created_at) FROM sms_messages WHERE status = 'QUEUED'")
    suspend fun getNewestQueuedMessageTime(): Long?
    
    /**
     * Pobiera metryki kolejki dla monitoringu
     */
    @Query("""
        SELECT
            priority,
            COUNT(*) as count,
            MIN(created_at) as oldest_time,
            MAX(created_at) as newest_time
        FROM sms_messages
        WHERE status = 'QUEUED'
        GROUP BY priority
        ORDER BY priority DESC
    """)
    suspend fun getQueueMetrics(): List<QueueMetrics>
    
    // === DODATKOWE METODY DLA SMS QUEUE SERVICE ===
    
    /**
     * Pobiera zaplanowane wiadomości przed określonym czasem
     */
    @Query("SELECT * FROM sms_messages WHERE status = 'SCHEDULED' AND scheduled_at <= :timestamp ORDER BY priority DESC, created_at ASC")
    suspend fun getScheduledMessagesBefore(timestamp: Long): List<SmsMessage>
    
    /**
     * Pobiera wiadomości według statusu i zakresu czasowego
     */
    @Query("SELECT * FROM sms_messages WHERE status = :status AND created_at BETWEEN :startTime AND :endTime ORDER BY created_at DESC")
    suspend fun getMessagesByStatusAndTimeRange(status: SmsStatus, startTime: Long, endTime: Long): List<SmsMessage>
    
    /**
     * Usuwa wiadomości według statusu
     */
    @Query("DELETE FROM sms_messages WHERE status = :status")
    suspend fun deleteByStatus(status: SmsStatus): Int
    
    /**
     * Pobiera całkowitą liczbę wiadomości
     */
    @Query("SELECT COUNT(*) FROM sms_messages")
    suspend fun getMessageCount(): Int
    
    /**
     * Pobiera wiadomości według statusu
     */
    @Query("SELECT * FROM sms_messages WHERE status = :status ORDER BY created_at DESC")
    suspend fun getMessagesByStatus(status: SmsStatus): List<SmsMessage>
    
    /**
     * Pobiera pojedynczą wiadomość po ID
     */
    @Query("SELECT * FROM sms_messages WHERE id = :id")
    suspend fun getMessageById(id: Long): SmsMessage?
    
    /**
     * Wstawia nową wiadomość i zwraca ID
     */
    @Insert
    suspend fun insert(smsMessage: SmsMessage): Long
    
    /**
     * Aktualizuje status wiadomości SMS (alias dla updateSmsStatus)
     */
    @Query("UPDATE sms_messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: SmsStatus): Int
}

/**
 * Klasa danych dla metryk kolejki
 */
data class QueueMetrics(
    val priority: Int,
    val count: Int,
    val oldestTime: Long?,
    val newestTime: Long?
)
