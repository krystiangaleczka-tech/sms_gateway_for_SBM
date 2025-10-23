package com.smsgateway.app.database

import kotlinx.coroutines.flow.Flow

/**
 * Repository dla operacji na wiadomościach SMS
 * Dostarcza abstrakcyjny dostęp do danych z dodatkową logiką biznesową
 */
class SmsRepository(private val smsDao: SmsDao) {
    
    // === PODSTAWOWE OPERACJE ===
    
    /**
     * Wstawia nową wiadomość SMS do bazy danych
     */
    suspend fun insertSms(smsMessage: SmsMessage): Long {
        return smsDao.insertSms(smsMessage)
    }
    
    /**
     * Aktualizuje istniejącą wiadomość SMS
     */
    suspend fun updateSms(smsMessage: SmsMessage) {
        smsDao.updateSms(smsMessage)
    }
    
    /**
     * Usuwa wiadomość SMS po ID
     */
    suspend fun deleteSms(id: Long) {
        smsDao.deleteSmsById(id)
    }
    
    /**
     * Pobiera wiadomość SMS po ID
     */
    suspend fun getSmsById(id: Long): SmsMessage? {
        return smsDao.getSmsById(id)
    }
    
    /**
     * Pobiera wszystkie wiadomości SMS jako Flow
     */
    fun getAllSms(): Flow<List<SmsMessage>> {
        return smsDao.getAllSms()
    }
    
    /**
     * Pobiera wszystkie wiadomości SMS (synchronicznie)
     */
    suspend fun getAllSmsSync(): List<SmsMessage> {
        return smsDao.getAllSmsSync()
    }
    
    // === OPERACJE NA STATUSIE ===
    
    /**
     * Aktualizuje status wiadomości SMS
     */
    suspend fun updateSmsStatus(id: Long, status: SmsStatus): Int {
        return smsDao.updateSmsStatus(id, status)
    }
    
    /**
     * Aktualizuje status i datę wysyłki
     */
    suspend fun updateSmsStatusWithSentTime(id: Long, status: SmsStatus, sentAt: Long): Int {
        return smsDao.updateSmsStatusWithSentTime(id, status, sentAt)
    }
    
    /**
     * Aktualizuje status, datę wysyłki i wiadomość błędu
     */
    suspend fun updateSmsStatusWithError(id: Long, status: SmsStatus, sentAt: Long, errorMessage: String?): Int {
        return smsDao.updateSmsStatusWithError(id, status, sentAt, errorMessage)
    }
    
    /**
     * Aktualizuje status z błędem i zwiększa licznik prób w jednej transakcji
     */
    suspend fun updateSmsStatusWithErrorAndIncrementRetry(id: Long, status: SmsStatus, sentAt: Long, errorMessage: String?) {
        smsDao.updateSmsStatusWithErrorAndIncrementRetry(id, status, sentAt, errorMessage)
    }
    
    /**
     * Pobiera wiadomości SMS z określonym statusem
     */
    suspend fun getSmsByStatus(status: SmsStatus): List<SmsMessage> {
        return smsDao.getSmsByStatus(status)
    }
    
    /**
     * Pobiera wiadomości SMS z określonym statusem jako Flow
     */
    fun getSmsByStatusFlow(status: SmsStatus): Flow<List<SmsMessage>> {
        return smsDao.getSmsByStatusFlow(status)
    }
    
    /**
     * Pobiera liczbę wiadomości według statusu
     */
    suspend fun getSmsCountByStatus(status: SmsStatus): Int {
        return smsDao.getSmsCountByStatus(status)
    }
    
    // === OPERACJE NA KOLEJCE ===
    
    /**
     * Pobiera wiadomości zaplanowane do wysyłki (status SCHEDULED)
     */
    suspend fun getScheduledForSending(timestamp: Long): List<SmsMessage> {
        return smsDao.getScheduledForSending(timestamp)
    }
    
    /**
     * Pobiera wiadomości w kolejce do wysyłki (status QUEUED)
     */
    suspend fun getQueuedSms(): List<SmsMessage> {
        return smsDao.getQueuedSms()
    }
    
    /**
     * Pobiera wiadomości, które nie powiodły się i można je ponowić
     */
    suspend fun getRetryableFailedSms(): List<SmsMessage> {
        return smsDao.getRetryableFailedSms()
    }
    
    /**
     * Pobiera następną wiadomość z kolejki (o najwyższym priorytecie)
     */
    suspend fun getNextQueuedMessage(): SmsMessage? {
        return smsDao.getNextQueuedMessage()
    }
    
    /**
     * Pobiera maksymalną pozycję w kolejce dla danego priorytetu
     */
    suspend fun getMaxQueuePosition(priority: Int): Int? {
        return smsDao.getMaxQueuePosition(priority)
    }
    
    /**
     * Aktualizuje pozycję w kolejce
     */
    suspend fun updateQueuePosition(id: Long, position: Int?): Int {
        return smsDao.updateQueuePosition(id, position)
    }
    
    /**
     * Pobiera wiadomości w kolejce dla danego priorytetu
     */
    suspend fun getQueuedMessagesByPriority(priority: Int): List<SmsMessage> {
        return smsDao.getQueuedMessagesByPriority(priority)
    }
    
    /**
     * Pobiera liczbę wiadomości w kolejce
     */
    suspend fun getQueuedMessagesCount(): Int {
        return smsDao.getQueuedMessagesCount()
    }
    
    /**
     * Pobiera liczbę wiadomości w kolejce dla danego priorytetu
     */
    suspend fun getQueuedMessagesCountByPriority(priority: Int): Int {
        return smsDao.getQueuedMessagesCountByPriority(priority)
    }
    
    /**
     * Aktualizuje priorytet i pozycję wiadomości
     */
    suspend fun updatePriorityAndPosition(id: Long, priority: SmsPriority, position: Int): Int {
        return smsDao.updatePriorityAndPosition(id, priority, position)
    }
    
    /**
     * Czyści kolejkę dla danego priorytetu
     */
    suspend fun clearQueueByPriority(priority: Int): Int {
        return smsDao.clearQueueByPriority(priority)
    }
    
    /**
     * Czyści całą kolejkę
     */
    suspend fun clearEntireQueue(): Int {
        return smsDao.clearEntireQueue()
    }
    
    /**
     * Reorganizuje pozycje w kolejce - naprawia luki po usunięciach
     */
    suspend fun reorganizeQueuePositions(): Int {
        return smsDao.reorganizeQueuePositions()
    }
    
    // === OPERACJE NA WIADOMOŚCIACH ZAPLANOWANYCH ===
    
    /**
     * Pobiera zaplanowane wiadomości przed określonym czasem
     */
    suspend fun getScheduledMessagesBefore(timestamp: Long): List<SmsMessage> {
        return smsDao.getScheduledMessagesBefore(timestamp)
    }
    
    /**
     * Pobiera wygaśnięte wiadomości zaplanowane (status SCHEDULED)
     */
    suspend fun getExpiredScheduledMessages(): List<SmsMessage> {
        val currentTime = System.currentTimeMillis()
        val expiredThreshold = currentTime - TimeUnit.DAYS.toMillis(1) // Wiadomości starsze niż 1 dzień
        
        return smsDao.getMessagesByStatusAndTimeRange(SmsStatus.SCHEDULED, 0L, expiredThreshold)
            .filter { it.scheduledAt != null && it.scheduledAt < expiredThreshold }
    }
    
    // === DODATKOWE METODY ===
    
    /**
     * Zwiększa licznik prób ponowienia
     */
    suspend fun incrementRetryCount(id: Long): Int {
        return smsDao.incrementRetryCount(id)
    }
    
    /**
     * Pobiera wiadomości z ostatnich N dni
     */
    suspend fun getSmsFromLastDays(timestamp: Long): List<SmsMessage> {
        return smsDao.getSmsFromLastDays(timestamp)
    }
    
    /**
     * Pobiera statystyki wiadomości z ostatnich 24 godzin
     */
    suspend fun getSmsStatsLast24Hours(timestamp: Long): List<StatusCount> {
        return smsDao.getSmsStatsLast24Hours(timestamp)
    }
    
    /**
     * Czyści stare wiadomości (starsze niż określony timestamp)
     */
    suspend fun cleanupOldMessages(timestamp: Long): Int {
        return smsDao.cleanupOldMessages(timestamp)
    }
    
    /**
     * Pobiera wiadomości według statusu i zakresu czasowego
     */
    suspend fun getMessagesByStatusAndTimeRange(status: SmsStatus, startTime: Long, endTime: Long): List<SmsMessage> {
        return smsDao.getMessagesByStatusAndTimeRange(status, startTime, endTime)
    }
    
    /**
     * Usuwa wiadomości według statusu
     */
    suspend fun deleteByStatus(status: SmsStatus): Int {
        return smsDao.deleteByStatus(status)
    }
    
    /**
     * Pobiera całkowitą liczbę wiadomości
     */
    suspend fun getTotalMessageCount(): Int {
        return smsDao.getMessageCount()
    }
    
    /**
     * Pobiera wiadomości według statusu
     */
    suspend fun getMessagesByStatus(status: SmsStatus): List<SmsMessage> {
        return smsDao.getMessagesByStatus(status)
    }
    
    /**
     * Pobiera pojedynczą wiadomość po ID
     */
    suspend fun getMessageById(id: Long): SmsMessage? {
        return smsDao.getMessageById(id)
    }
    
    /**
     * Wstawia nową wiadomość i zwraca ID
     */
    suspend fun insert(smsMessage: SmsMessage): Long {
        return smsDao.insert(smsMessage)
    }
    
    // === METODY DLA QUEUE MAINTENANCE WORKER ===
    
    /**
     * Pobiera stare wiadomości o statusie SENT
     */
    suspend fun getOldSentMessages(thresholdTime: Long): List<SmsMessage> {
        return smsDao.getMessagesByStatusAndTimeRange(SmsStatus.SENT, 0L, thresholdTime)
    }
    
    /**
     * Pobiera stare wiadomości o statusie FAILED
     */
    suspend fun getOldFailedMessages(thresholdTime: Long): List<SmsMessage> {
        return smsDao.getMessagesByStatusAndTimeRange(SmsStatus.FAILED, 0L, thresholdTime)
    }
    
    /**
     * Pobiera porzucone wiadomości o statusie SENDING
     */
    suspend fun getAbandonedSendingMessages(thresholdTime: Long): List<SmsMessage> {
        return smsDao.getMessagesByStatusAndTimeRange(SmsStatus.SENDING, 0L, thresholdTime)
    }
    
    /**
     * Aktualizuje wiadomość SMS (pełna aktualizacja)
     */
    suspend fun updateSmsMessage(smsMessage: SmsMessage) {
        smsDao.updateSms(smsMessage)
    }
    
    /**
     * Pobiera metryki kolejki dla monitoringu
     */
    suspend fun getQueueMetrics(): List<QueueMetrics> {
        return smsDao.getQueueMetrics()
    }
    
    /**
     * Pobiera czas najstarszej wiadomości w kolejce
     */
    suspend fun getOldestQueuedMessageTime(): Long? {
        return smsDao.getOldestQueuedMessageTime()
    }
    
    /**
     * Pobiera czas najnowszej wiadomości w kolejce
     */
    suspend fun getNewestQueuedMessageTime(): Long? {
        return smsDao.getNewestQueuedMessageTime()
    }
    
    /**
     * Pobiera statystyki kolejki
     */
    suspend fun getQueueStatistics(): QueueStatistics {
        val totalMessages = getTotalMessageCount()
        val queuedMessages = getQueuedMessagesCount()
        val sentMessages = getSmsCountByStatus(SmsStatus.SENT)
        val failedMessages = getSmsCountByStatus(SmsStatus.FAILED)
        val scheduledMessages = getSmsCountByStatus(SmsStatus.SCHEDULED)
        val sendingMessages = getSmsCountByStatus(SmsStatus.SENDING)
        
        val oldestMessageTime = getOldestQueuedMessageTime()
        val newestMessageTime = getNewestQueuedMessageTime()
        
        val averageWaitTime = if (oldestMessageTime != null) {
            System.currentTimeMillis() - oldestMessageTime
        } else {
            0L
        }
        
        val errorRate = if (totalMessages > 0) {
            (failedMessages.toDouble() / totalMessages.toDouble())
        } else {
            0.0
        }
        
        return QueueStatistics(
            totalMessages = totalMessages,
            queuedMessages = queuedMessages,
            sentMessages = sentMessages,
            failedMessages = failedMessages,
            scheduledMessages = scheduledMessages,
            sendingMessages = sendingMessages,
            oldestMessageTime = oldestMessageTime,
            newestMessageTime = newestMessageTime,
            averageWaitTime = averageWaitTime,
            errorRate = errorRate
        )
    }
}

/**
 * Klasa danych dla statystyk kolejki
 */
data class QueueStatistics(
    val totalMessages: Int,
    val queuedMessages: Int,
    val sentMessages: Int,
    val failedMessages: Int,
    val scheduledMessages: Int,
    val sendingMessages: Int,
    val oldestMessageTime: Long?,
    val newestMessageTime: Long?,
    val averageWaitTime: Long,
    val errorRate: Double
)

/**
 * Klasa pomocnicza do konwersji TimeUnit
 */
object TimeUnit {
    fun toMillis(days: Long): Long = days * 24 * 60 * 60 * 1000L
    fun toHours(millis: Long): Long = millis / (60 * 60 * 1000L)
    fun toMinutes(millis: Long): Long = millis / (60 * 1000L)
    fun toSeconds(millis: Long): Long = millis / 1000L
}

/**
 * Klasa danych dla licznika statusów
 */
data class StatusCount(
    val status: String,
    val count: Int
)