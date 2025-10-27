package com.smsgateway.app.queue

import com.smsgateway.app.database.SmsMessage
import com.smsgateway.app.database.SmsRepository
import com.smsgateway.app.database.SmsStatus
import com.smsgateway.app.health.HealthChecker
import com.smsgateway.app.metrics.MetricsCollector
import com.smsgateway.app.retry.RetryService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Serwis do zarządzania kolejką SMS-ów
 * 
 * Odpowiada za:
 * - Dodawanie SMS-ów do kolejki
 * - Przetwarzanie kolejki
 * - Ponawianie nieudanych wysyłek
 * - Monitorowanie stanu kolejki
 */
class SmsQueueService(
    private val smsRepository: SmsRepository,
    private val retryService: RetryService,
    private val metricsCollector: MetricsCollector,
    private val healthChecker: HealthChecker
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _queueState = MutableStateFlow(QueueState.IDLE)
    val queueState: StateFlow<QueueState> = _queueState.asStateFlow()
    
    private val _queueSize = MutableStateFlow(0)
    val queueSize: StateFlow<Int> = _queueSize.asStateFlow()
    
    /**
     * Stany kolejki
     */
    enum class QueueState {
        IDLE,
        PROCESSING,
        PAUSED,
        ERROR
    }
    
    /**
     * Dodaje SMS do kolejki
     */
    fun addToQueue(smsMessage: SmsMessage) {
        scope.launch {
            try {
                // Ustaw status na QUEUED
                val queuedSms = smsMessage.copy(status = SmsStatus.QUEUED)
                smsRepository.insertSms(queuedSms)
                
                // Aktualizuj metryki
                metricsCollector.incrementQueuedSms()
                updateQueueSize()
                
                // Rozpocznij przetwarzanie jeśli kolejka jest bezczynna
                if (_queueState.value == QueueState.IDLE) {
                    startProcessing()
                }
            } catch (e: Exception) {
                metricsCollector.incrementError("queue_add")
                throw e
            }
        }
    }
    
    /**
     * Rozpoczyna przetwarzanie kolejki
     */
    private fun startProcessing() {
        if (_queueState.value != QueueState.IDLE) return
        
        _queueState.value = QueueState.PROCESSING
        
        scope.launch {
            try {
                while (true) {
                    // Sprawdź zdrowie systemu
                    if (healthChecker.getSystemHealth()["status"] != "HEALTHY") {
                        _queueState.value = QueueState.PAUSED
                        break
                    }
                    
                    // Pobierz następny SMS do przetworzenia
                    val nextSms = getNextQueuedSms()
                    if (nextSms == null) {
                        _queueState.value = QueueState.IDLE
                        break
                    }
                    
                    // Przetwórz SMS
                    processSms(nextSms)
                }
            } catch (e: Exception) {
                _queueState.value = QueueState.ERROR
                metricsCollector.incrementError("queue_processing")
            }
        }
    }
    
    /**
     * Przetwarza pojedynczy SMS
     */
    private suspend fun processSms(smsMessage: SmsMessage) {
        try {
            // Zaktualizuj status na PROCESSING
            val processingSms = smsMessage.copy(status = SmsStatus.SENDING)
            smsRepository.updateSms(processingSms)
            
            // Wykonaj operację wysyłki z ponawianiem
            val result = retryService.executeWithRetry {
                sendSms(smsMessage)
            }
            
            if (result.success) {
                // Sukces - zaktualizuj status na SENT
                val sentSms = smsMessage.copy(
                    status = SmsStatus.SENT,
                    sentAt = System.currentTimeMillis()
                )
                smsRepository.updateSms(sentSms)
                metricsCollector.incrementSentSms()
            } else {
                // Błąd - zaktualizuj status na FAILED
                val failedSms = smsMessage.copy(
                    status = SmsStatus.FAILED,
                    errorMessage = result.error?.message
                )
                smsRepository.updateSms(failedSms)
                metricsCollector.incrementFailedSms()
            }
            
            updateQueueSize()
        } catch (e: Exception) {
            // Błąd krytyczny - zaktualizuj status na FAILED
            val failedSms = smsMessage.copy(
                status = SmsStatus.FAILED,
                errorMessage = e.message
            )
            smsRepository.updateSms(failedSms)
            metricsCollector.incrementFailedSms()
            updateQueueSize()
        }
    }
    
    /**
     * Wysyła SMS (implementacja zależna od systemu)
     */
    private suspend fun sendSms(unusedSmsMessage: SmsMessage): Boolean {
        // Tutaj powinna być implementacja wysyłki SMS
        // Na razie zwracamy true dla celów demonstracyjnych
        return true
    }
    
    /**
     * Pobiera następny SMS z kolejki
     */
    private suspend fun getNextQueuedSms(): SmsMessage? {
        return try {
            smsRepository.getQueuedSms().firstOrNull()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Aktualizuje rozmiar kolejki
     */
    private suspend fun updateQueueSize() {
        try {
            val size = smsRepository.getQueuedSms().size
            _queueSize.value = size
        } catch (e: Exception) {
            // Ignoruj błędy przy aktualizacji rozmiaru
        }
    }
    
    /**
     * Wstrzymuje przetwarzanie kolejki
     */
    fun pauseProcessing() {
        _queueState.value = QueueState.PAUSED
    }
    
    /**
     * Wznawia przetwarzanie kolejki
     */
    fun resumeProcessing() {
        if (_queueState.value == QueueState.PAUSED) {
            _queueState.value = QueueState.IDLE
            startProcessing()
        }
    }
    
    /**
     * Czyści kolejkę z nieudanych SMS-ów
     */
    fun clearFailedMessages() {
        scope.launch {
            try {
                val failedMessages = smsRepository.getAllSmsSync()
                    .filter { it.status == SmsStatus.FAILED }
                
                failedMessages.forEach { failedMessage: SmsMessage ->
                    smsRepository.deleteSms(failedMessage)
                }
                
                updateQueueSize()
            } catch (e: Exception) {
                metricsCollector.incrementError("queue_clear_failed")
            }
        }
    }
    
    /**
     * Ponawia wysyłkę nieudanych SMS-ów
     */
    fun retryFailedMessages() {
        scope.launch {
            try {
                val failedMessages = smsRepository.getAllSmsSync()
                    .filter { it.status == SmsStatus.FAILED }
                
                failedMessages.forEach { failedMessage: SmsMessage ->
                    val queuedMessage = failedMessage.copy(
                        status = SmsStatus.QUEUED,
                        errorMessage = null
                    )
                    smsRepository.updateSms(queuedMessage)
                }
                
                updateQueueSize()
                
                // Rozpocznij przetwarzanie jeśli jest bezczynne
                if (_queueState.value == QueueState.IDLE) {
                    startProcessing()
                }
            } catch (e: Exception) {
                metricsCollector.incrementError("queue_retry_failed")
            }
        }
    }
}