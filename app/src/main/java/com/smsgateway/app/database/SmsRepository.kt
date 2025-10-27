package com.smsgateway.app.database

import kotlinx.coroutines.flow.Flow

/**
 * Klasa danych dla statystyk statusów
 */
data class StatusCount(
    val status: String,
    val count: Int
)

/**
 * Klasa danych dla wyników paginacji
 */
data class PaginatedResult<T>(
    val data: List<T>,
    val total: Int,
    val page: Int,
    val limit: Int,
    val totalPages: Int,
    val hasNextPage: Boolean,
    val hasPreviousPage: Boolean
)

/**
 * Repozytorium dla operacji na wiadomościach SMS
 * Implementuje logikę biznesową związaną z zarządzaniem wiadomościami
 */
class SmsRepository(private val smsDao: SmsDao) {
    
    /**
     * Wstawia nową wiadomość SMS do bazy danych
     */
    suspend fun insertSms(smsMessage: SmsMessage): Long {
        return smsDao.insertSms(smsMessage)
    }
    
    /**
     * Wstawia wiele wiadomości SMS do bazy danych
     */
    suspend fun insertMultipleSms(smsMessages: List<SmsMessage>): List<Long> {
        return smsDao.insertMultipleSms(smsMessages)
    }
    
    /**
     * Aktualizuje istniejącą wiadomość SMS
     */
    suspend fun updateSms(smsMessage: SmsMessage) {
        smsDao.updateSms(smsMessage)
    }
    
    /**
     * Usuwa wiadomość SMS z bazy danych
     */
    suspend fun deleteSms(smsMessage: SmsMessage) {
        smsDao.deleteSms(smsMessage)
    }
    
    /**
     * Usuwa wiadomość SMS po ID
     */
    suspend fun deleteSmsById(id: Long): Int {
        return smsDao.deleteSmsById(id)
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
    
    /**
     * Pobiera wiadomości SMS z paginacją
     */
    suspend fun getSmsWithPagination(page: Int, limit: Int, status: String? = null): PaginatedResult<SmsMessage> {
        val offset = (page - 1) * limit
        
        val messages = if (status != null) {
            smsDao.getSmsWithPaginationAndStatus(status, limit, offset)
        } else {
            smsDao.getSmsWithPagination(limit, offset)
        }
        
        val totalCount = if (status != null) {
            smsDao.getSmsCountByStatusString(status)
        } else {
            smsDao.getSmsTotalCount()
        }
        
        val totalPages = (totalCount + limit - 1) / limit // Math.ceil(totalCount / limit)
        
        return PaginatedResult(
            data = messages,
            total = totalCount,
            page = page,
            limit = limit,
            totalPages = totalPages,
            hasNextPage = page < totalPages,
            hasPreviousPage = page > 1
        )
    }
    
    /**
     * Pobiera wiadomości SMS z paginacją i filtrowaniem po statusie
     */
    suspend fun getSmsWithPaginationAndStatus(status: String, limit: Int, offset: Int): List<SmsMessage> {
        return smsDao.getSmsWithPaginationAndStatus(status, limit, offset)
    }
    
    /**
     * Pobiera całkowitą liczbę wiadomości
     */
    suspend fun getSmsTotalCount(): Int {
        return smsDao.getSmsTotalCount()
    }
    
    /**
     * Pobiera liczbę wiadomości według statusu
     */
    suspend fun getSmsCountByStatusString(status: String): Int {
        return smsDao.getSmsCountByStatusString(status)
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
        return smsDao.updateSmsStatusWithErrorAndIncrementRetry(id, status, sentAt, errorMessage)
    }
    
    /**
     * Przenosi wiadomość do kolejki i aktualizuje czas planowania
     */
    suspend fun queueSmsMessage(id: Long, scheduledAt: Long) {
        updateSmsStatus(id, SmsStatus.QUEUED)
    }
    
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
     * Pobiera liczbę wiadomości według statusu
     */
    suspend fun getSmsCountByStatus(status: SmsStatus): Int {
        return smsDao.getSmsCountByStatus(status)
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
}