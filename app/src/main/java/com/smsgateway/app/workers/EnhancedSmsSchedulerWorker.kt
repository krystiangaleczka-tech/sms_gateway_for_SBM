package com.smsgateway.app.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.smsgateway.app.database.AppDatabase
import com.smsgateway.app.database.SmsMessage
import com.smsgateway.app.database.SmsStatus
import com.smsgateway.app.queue.SmsQueueService
import com.smsgateway.app.retry.RetryService
import com.smsgateway.app.health.HealthChecker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * Enhanced Worker okresowy sprawdzający wiadomości SMS w kolejce priorytetowej
 * Uruchamiany co 15 minut, planuje zadania wysyłki SMS z uwzględnieniem priorytetów
 * Zintegrowany z SmsQueueService, RetryService i HealthChecker
 */
@HiltWorker
class EnhancedSmsSchedulerWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val smsQueueService: SmsQueueService,
    private val retryService: RetryService,
    private val workManagerService: WorkManagerService,
    private val healthChecker: HealthChecker
) : CoroutineWorker(context, workerParams) {
    
    private val logger = LoggerFactory.getLogger(EnhancedSmsSchedulerWorker::class.java)
    
    override suspend fun doWork(): Result {
        return try {
            logger.info("EnhancedSmsSchedulerWorker started - checking SMS queue with priorities")
            
            // Sprawdzenie zdrowia systemu przed rozpoczęciem przetwarzania
            val healthStatus = healthChecker.performHealthCheck()
            logger.info("System health status: ${healthStatus.overallStatus}")
            
            if (healthStatus.hasCriticalIssues()) {
                logger.warn("System has critical issues, skipping SMS processing: ${healthStatus.getIssues()}")
                return Result.retry()
            }
            
            // Sprawdzenie czy kolejka jest aktywna
            if (!smsQueueService.isQueueActive()) {
                logger.info("SMS queue is paused, skipping processing")
                return Result.success()
            }
            
            // Pobranie statystyk kolejki
            val queueStats = smsQueueService.getQueueStats()
            logger.info("Queue stats: $queueStats")
            
            // Przetwarzanie zaplanowanych wiadomości
            val scheduledProcessedCount = processScheduledMessages()
            
            // Przetwarzanie wiadomości do ponowienia
            val retryProcessedCount = processRetryMessages()
            
            // Przetwarzanie wiadomości w kolejce
            val queueProcessedCount = processQueuedMessages()
            
            val totalProcessed = scheduledProcessedCount + retryProcessedCount + queueProcessedCount
            
            logger.info(
                "EnhancedSmsSchedulerWorker completed - " +
                "scheduled: $scheduledProcessedCount, " +
                "retry: $retryProcessedCount, " +
                "queue: $queueProcessedCount, " +
                "total: $totalProcessed"
            )
            
            // Publikacja metryk
            publishMetrics(totalProcessed, queueStats, healthStatus)
            
            Result.success()
            
        } catch (e: Exception) {
            logger.error("EnhancedSmsSchedulerWorker failed", e)
            Result.failure()
        }
    }
    
    /**
     * Przetwarza zaplanowane wiadomości (status SCHEDULED)
     */
    private suspend fun processScheduledMessages(): Int {
        return try {
            // Pobranie bazy danych
            val database = AppDatabase.getDatabase(applicationContext)
            val smsRepository = database.smsDao().let { 
                com.smsgateway.app.database.SmsRepository(it) 
            }
            
            // Pobranie zaplanowanych wiadomości
            val scheduledMessages = smsRepository.getScheduledSms()
            logger.info("Found ${scheduledMessages.size} scheduled messages")
            
            var processedCount = 0
            val currentTime = System.currentTimeMillis()
            
            for (message in scheduledMessages) {
                try {
                    // Sprawdź czy czas planowania minął
                    val scheduledTime = message.scheduledAt ?: 0
                    
                    if (scheduledTime <= currentTime) {
                        // Zaplanuj wysyłkę SMS
                        workManagerService.scheduleSmsSending(message)
                        processedCount++
                        
                        logger.info(
                            "Scheduled SMS sending for message ID: ${message.id}, " +
                            "Priority: ${message.priority}, " +
                            "Phone: ${message.phoneNumber}"
                        )
                    } else {
                        // Oblicz pozostały czas do planowania
                        val remainingTime = scheduledTime - currentTime
                        val remainingMinutes = TimeUnit.MILLISECONDS.toMinutes(remainingTime)
                        
                        logger.debug(
                            "Message ID: ${message.id} will be scheduled in $remainingMinutes minutes"
                        )
                    }
                } catch (e: Exception) {
                    logger.error("Error processing scheduled message ID: ${message.id}", e)
                }
            }
            
            processedCount
        } catch (e: Exception) {
            logger.error("Error processing scheduled messages", e)
            0
        }
    }
    
    /**
     * Przetwarza wiadomości do ponowienia (status FAILED)
     */
    private suspend fun processRetryMessages(): Int {
        return try {
            // Pobranie bazy danych
            val database = AppDatabase.getDatabase(applicationContext)
            val smsRepository = database.smsDao().let { 
                com.smsgateway.app.database.SmsRepository(it) 
            }
            
            // Pobranie wiadomości, które można ponowić
            val retryableMessages = smsRepository.getRetryableFailedSms()
            logger.info("Found ${retryableMessages.size} retryable failed messages")
            
            var processedCount = 0
            val currentTime = System.currentTimeMillis()
            
            for (message in retryableMessages) {
                try {
                    // Sprawdzenie czy można ponowić próbę
                    if (!retryService.shouldRetry(message.retryCount, message.errorMessage)) {
                        logger.info(
                            "Message ID: ${message.id} should not be retried, " +
                            "retry count: ${message.retryCount}, " +
                            "error: ${message.errorMessage}"
                        )
                        continue
                    }
                    
                    // Obliczenie czasu ponowienia
                    val retryPolicy = retryService.getRetryPolicy(message.retryStrategy)
                    val nextRetryTime = retryService.getNextRetryTime(
                        message.retryCount,
                        message.errorMessage,
                        retryPolicy
                    )
                    
                    if (nextRetryTime <= currentTime) {
                        // Przygotowanie wiadomości do ponowienia
                        val preparedMessage = retryService.prepareMessageForRetry(message)
                        
                        // Aktualizacja w bazie danych
                        smsRepository.updateSmsMessage(preparedMessage)
                        
                        // Zaplanowanie ponowienia
                        workManagerService.scheduleSmsSending(preparedMessage)
                        processedCount++
                        
                        logger.info(
                            "Scheduled retry for message ID: ${message.id}, " +
                            "Retry count: ${message.retryCount + 1}, " +
                            "Strategy: ${message.retryStrategy}, " +
                            "Priority: ${message.priority}"
                        )
                    } else {
                        val remainingTime = nextRetryTime - currentTime
                        val remainingMinutes = TimeUnit.MILLISECONDS.toMinutes(remainingTime)
                        
                        logger.debug(
                            "Message ID: ${message.id} will be retried in $remainingMinutes minutes"
                        )
                    }
                } catch (e: Exception) {
                    logger.error("Error scheduling retry for message ID: ${message.id}", e)
                }
            }
            
            processedCount
        } catch (e: Exception) {
            logger.error("Error processing retry messages", e)
            0
        }
    }
    
    /**
     * Przetwarza wiadomości w kolejce (status QUEUED)
     */
    private suspend fun processQueuedMessages(): Int {
        return try {
            var processedCount = 0
            val maxMessagesPerCycle = 50 // Limit wiadomości przetwarzanych w jednym cyklu
            
            // Przetwarzanie wiadomości z kolejki priorytetowej
            repeat(maxMessagesPerCycle) {
                val message = smsQueueService.dequeueNextSms()
                if (message != null) {
                    try {
                        // Zaplanuj wysyłkę SMS
                        workManagerService.scheduleSmsSending(message)
                        processedCount++
                        
                        logger.info(
                            "Dequeued and scheduled SMS for message ID: ${message.id}, " +
                            "Priority: ${message.priority}, " +
                            "Queue position: ${message.queuePosition}"
                        )
                    } catch (e: Exception) {
                        logger.error("Error processing queued message ID: ${message.id}", e)
                        // Przywróć wiadomość do kolejki w przypadku błędu
                        smsQueueService.enqueueSms(message, message.priority)
                    }
                } else {
                    // Brak wiadomości w kolejce
                    return@repeat
                }
            }
            
            processedCount
        } catch (e: Exception) {
            logger.error("Error processing queued messages", e)
            0
        }
    }
    
    /**
     * Publikuje metryki dotyczące przetwarzania wiadomości
     */
    private suspend fun publishMetrics(
        processedCount: Int,
        queueStats: com.smsgateway.app.queue.QueueStats,
        healthStatus: com.smsgateway.app.health.SystemHealth
    ) {
        try {
            // Tutaj można zintegrować z MetricsCollector (do implementacji)
            logger.info(
                "Metrics published - " +
                "processed: $processedCount, " +
                "queue size: ${queueStats.totalMessages}, " +
                "health: ${healthStatus.overallStatus}"
            )
        } catch (e: Exception) {
            logger.error("Error publishing metrics", e)
        }
    }
    
    /**
     * Sprawdza czy system jest w stanie przetwarzać wiadomości
     */
    private fun isSystemReadyForProcessing(healthStatus: com.smsgateway.app.health.SystemHealth): Boolean {
        return when (healthStatus.overallStatus) {
            com.smsgateway.app.health.HealthStatus.HEALTHY -> true
            com.smsgateway.app.health.HealthStatus.WARNING -> true
            com.smsgateway.app.health.HealthStatus.CRITICAL -> false
            com.smsgateway.app.health.HealthStatus.DOWN -> false
        }
    }
    
    /**
     * Oblicza maksymalną liczbę wiadomości do przetworzenia w jednym cyklu
     * na podstawie stanu systemu i obciążenia kolejki
     */
    private fun calculateMaxMessagesPerCycle(
        healthStatus: com.smsgateway.app.health.SystemHealth,
        queueStats: com.smsgateway.app.queue.QueueStats
    ): Int {
        val baseLimit = 50
        
        return when (healthStatus.overallStatus) {
            com.smsgateway.app.health.HealthStatus.HEALTHY -> baseLimit
            com.smsgateway.app.health.HealthStatus.WARNING -> baseLimit / 2
            com.smsgateway.app.health.HealthStatus.CRITICAL -> baseLimit / 4
            com.smsgateway.app.health.HealthStatus.DOWN -> 0
        }
    }
}