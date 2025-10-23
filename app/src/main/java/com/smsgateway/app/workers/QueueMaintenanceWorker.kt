package com.smsgateway.app.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.smsgateway.app.database.AppDatabase
import com.smsgateway.app.database.SmsMessage
import com.smsgateway.app.database.SmsStatus
import com.smsgateway.app.queue.SmsQueueService
import com.smsgateway.app.health.HealthChecker
import com.smsgateway.app.queue.QueueStats
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * Worker odpowiedzialny za konserwację i czyszczenie kolejki SMS
 * Uruchamiany okresowo (codziennie) do maintenance'u systemu
 * Odpowiada za czyszczenie starych wiadomości, optymalizację kolejki i generowanie raportów
 */
@HiltWorker
class QueueMaintenanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val smsQueueService: SmsQueueService,
    private val healthChecker: HealthChecker
) : CoroutineWorker(applicationContext, workerParams) {
    
    private val logger = LoggerFactory.getLogger(QueueMaintenanceWorker::class.java)
    
    companion object {
        // Konfiguracja maintenance'u
        const val OLD_MESSAGES_THRESHOLD_DAYS = 30L
        const val FAILED_MESSAGES_THRESHOLD_DAYS = 7L
        const val SENT_MESSAGES_THRESHOLD_DAYS = 14L
        
        // Tagi dla identyfikacji zadania
        const val MAINTENANCE_TAG = "queue-maintenance"
        const val MAINTENANCE_WORK_NAME = "QueueMaintenanceWorker"
    }
    
    override suspend fun doWork(): Result {
        return try {
            logger.info("QueueMaintenanceWorker started - performing queue maintenance")
            
            // Sprawdzenie zdrowia systemu
            val healthStatus = healthChecker.performHealthCheck()
            logger.info("System health status: ${healthStatus.overallStatus}")
            
            // Pobranie statystyk przed maintenance'em
            val beforeStats = smsQueueService.getQueueStats()
            logger.info("Queue stats before maintenance: $beforeStats")
            
            // Wykonanie operacji maintenance'u
            val maintenanceResults = MaintenanceResults()
            
            // 1. Czyszczenie starych wiadomości SENT
            maintenanceResults.sentCleaned = cleanOldSentMessages()
            
            // 2. Czyszczenie starych wiadomości FAILED
            maintenanceResults.failedCleaned = cleanOldFailedMessages()
            
            // 3. Czyszczenie wygaśniętych wiadomości SCHEDULED
            maintenanceResults.expiredCleaned = cleanExpiredScheduledMessages()
            
            // 4. Optymalizacja pozycji w kolejce
            maintenanceResults.positionsOptimized = optimizeQueuePositions()
            
            // 5. Czyszczenie porzuconych wiadomości SENDING
            maintenanceResults.abandonedCleaned = cleanAbandonedSendingMessages()
            
            // 6. Generowanie raportu o stanie kolejki
            val queueReport = generateQueueReport(beforeStats, maintenanceResults)
            
            // 7. Sprawdzenie czy wymagane są dodatkowe akcje
            performAdditionalMaintenance(maintenanceResults)
            
            // Pobranie statystyk po maintenance'u
            val afterStats = smsQueueService.getQueueStats()
            logger.info("Queue stats after maintenance: $afterStats")
            
            // Logowanie wyników
            logMaintenanceResults(beforeStats, afterStats, maintenanceResults)
            
            logger.info("QueueMaintenanceWorker completed successfully")
            
            Result.success()
            
        } catch (e: Exception) {
            logger.error("QueueMaintenanceWorker failed", e)
            Result.failure()
        }
    }
    
    /**
     * Czyści stare wiadomości o statusie SENT
     */
    private suspend fun cleanOldSentMessages(): Int {
        return try {
            val database = AppDatabase.getDatabase(applicationContext)
            val smsRepository = database.smsDao().let { 
                com.smsgateway.app.database.SmsRepository(it) 
            }
            
            val thresholdTime = System.currentTimeMillis() - 
                TimeUnit.DAYS.toMillis(SENT_MESSAGES_THRESHOLD_DAYS)
            
            val oldSentMessages = smsRepository.getOldSentMessages(thresholdTime)
            var cleanedCount = 0
            
            for (message in oldSentMessages) {
                try {
                    smsRepository.deleteSms(message.id)
                    cleanedCount++
                } catch (e: Exception) {
                    logger.error("Error deleting old sent message ID: ${message.id}", e)
                }
            }
            
            logger.info("Cleaned $cleanedCount old sent messages (older than $SENT_MESSAGES_THRESHOLD_DAYS days)")
            cleanedCount
            
        } catch (e: Exception) {
            logger.error("Error cleaning old sent messages", e)
            0
        }
    }
    
    /**
     * Czyści stare wiadomości o statusie FAILED
     */
    private suspend fun cleanOldFailedMessages(): Int {
        return try {
            val database = AppDatabase.getDatabase(applicationContext)
            val smsRepository = database.smsDao().let { 
                com.smsgateway.app.database.SmsRepository(it) 
            }
            
            val thresholdTime = System.currentTimeMillis() - 
                TimeUnit.DAYS.toMillis(FAILED_MESSAGES_THRESHOLD_DAYS)
            
            val oldFailedMessages = smsRepository.getOldFailedMessages(thresholdTime)
            var cleanedCount = 0
            
            for (message in oldFailedMessages) {
                try {
                    smsRepository.deleteSms(message.id)
                    cleanedCount++
                } catch (e: Exception) {
                    logger.error("Error deleting old failed message ID: ${message.id}", e)
                }
            }
            
            logger.info("Cleaned $cleanedCount old failed messages (older than $FAILED_MESSAGES_THRESHOLD_DAYS days)")
            cleanedCount
            
        } catch (e: Exception) {
            logger.error("Error cleaning old failed messages", e)
            0
        }
    }
    
    /**
     * Czyści wygaśnięte wiadomości o statusie SCHEDULED
     */
    private suspend fun cleanExpiredScheduledMessages(): Int {
        return try {
            val database = AppDatabase.getDatabase(applicationContext)
            val smsRepository = database.smsDao().let { 
                com.smsgateway.app.database.SmsRepository(it) 
            }
            
            val expiredMessages = smsRepository.getExpiredScheduledMessages()
            var cleanedCount = 0
            
            for (message in expiredMessages) {
                try {
                    // Aktualizacja statusu na FAILED z informacją o wygaśnięciu
                    smsRepository.updateSmsStatusWithError(
                        message.id,
                        SmsStatus.FAILED,
                        System.currentTimeMillis(),
                        "Message expired - scheduled time passed too long ago"
                    )
                    cleanedCount++
                } catch (e: Exception) {
                    logger.error("Error updating expired scheduled message ID: ${message.id}", e)
                }
            }
            
            logger.info("Marked $cleanedCount expired scheduled messages as failed")
            cleanedCount
            
        } catch (e: Exception) {
            logger.error("Error cleaning expired scheduled messages", e)
            0
        }
    }
    
    /**
     * Optymalizuje pozycje w kolejce po usunięciach
     */
    private suspend fun optimizeQueuePositions(): Int {
        return try {
            val database = AppDatabase.getDatabase(applicationContext)
            val smsRepository = database.smsDao().let { 
                com.smsgateway.app.database.SmsRepository(it) 
            }
            
            // Reorganizacja pozycji w kolejce
            smsRepository.reorganizeQueuePositions()
            
            logger.info("Optimized queue positions")
            1
            
        } catch (e: Exception) {
            logger.error("Error optimizing queue positions", e)
            0
        }
    }
    
    /**
     * Czyści porzucone wiadomości o statusie SENDING
     * (wiadomości, które zbyt długo są w stanie SENDING)
     */
    private suspend fun cleanAbandonedSendingMessages(): Int {
        return try {
            val database = AppDatabase.getDatabase(applicationContext)
            val smsRepository = database.smsDao().let { 
                com.smsgateway.app.database.SmsRepository(it) 
            }
            
            // Wiadomości w stanie SENDING dłużej niż 1 godzinę
            val thresholdTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)
            
            val abandonedMessages = smsRepository.getAbandonedSendingMessages(thresholdTime)
            var cleanedCount = 0
            
            for (message in abandonedMessages) {
                try {
                    // Przygotowanie do ponowienia
                    val updatedMessage = message.copy(
                        status = SmsStatus.FAILED,
                        errorMessage = "Message abandoned - sending timeout",
                        retryCount = message.retryCount + 1
                    )
                    
                    smsRepository.updateSmsMessage(updatedMessage)
                    cleanedCount++
                } catch (e: Exception) {
                    logger.error("Error updating abandoned sending message ID: ${message.id}", e)
                }
            }
            
            logger.info("Processed $cleanedCount abandoned sending messages")
            cleanedCount
            
        } catch (e: Exception) {
            logger.error("Error cleaning abandoned sending messages", e)
            0
        }
    }
    
    /**
     * Generuje raport o stanie kolejki
     */
    private suspend fun generateQueueReport(
        beforeStats: QueueStats,
        results: MaintenanceResults
    ): QueueReport {
        return try {
            val database = AppDatabase.getDatabase(applicationContext)
            val smsRepository = database.smsDao().let { 
                com.smsgateway.app.database.SmsRepository(it) 
            }
            
            val totalMessages = smsRepository.getTotalMessageCount()
            val healthStatus = healthChecker.performHealthCheck()
            
            QueueReport(
                timestamp = System.currentTimeMillis(),
                totalMessages = totalMessages,
                queueStats = beforeStats,
                maintenanceResults = results,
                healthStatus = healthStatus,
                recommendations = generateRecommendations(beforeStats, results, healthStatus)
            )
        } catch (e: Exception) {
            logger.error("Error generating queue report", e)
            QueueReport.empty()
        }
    }
    
    /**
     * Generuje rekomendacje na podstawie stanu systemu
     */
    private fun generateRecommendations(
        stats: QueueStats,
        results: MaintenanceResults,
        healthStatus: com.smsgateway.app.health.SystemHealth
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        // Rekomendacje na podstawie rozmiaru kolejki
        if (stats.queuedMessages > 100) {
            recommendations.add("Queue size is high (${stats.queuedMessages}), consider increasing processing capacity")
        }
        
        // Rekomendacje na podstawie wskaźnika błędów
        if (stats.errorRate > 0.1) {
            recommendations.add("High error rate detected (${(stats.errorRate * 100).toInt()}%), investigate sending issues")
        }
        
        // Rekomendacje na podstawie wyników maintenance'u
        if (results.failedCleaned > 50) {
            recommendations.add("High number of failed messages cleaned (${results.failedCleaned}), check error patterns")
        }
        
        // Rekomendacje na podstawie zdrowia systemu
        if (healthStatus.hasWarnings()) {
            recommendations.addAll(healthStatus.getRecommendations())
        }
        
        return recommendations
    }
    
    /**
     * Wykonuje dodatkowe akcje maintenance'u w razie potrzeby
     */
    private suspend fun performAdditionalMaintenance(results: MaintenanceResults) {
        try {
            // Jeśli wyczyszczono dużo wiadomości, wykonaj dodatkową optymalizację
            if (results.getTotalCleaned() > 100) {
                logger.info("High number of messages cleaned, performing additional optimization")
                
                // Tutaj można dodać dodatkowe operacje, np:
                // - Optymalizację bazy danych
                // - Generowanie alertów
                // - Archiwizację danych
            }
            
            // Jeśli wykryto dużo porzuconych wiadomości, sprawdź stan WorkManager
            if (results.abandonedCleaned > 10) {
                logger.warn("High number of abandoned messages detected, checking WorkManager health")
                // Tutaj można dodać diagnostykę WorkManager
            }
            
        } catch (e: Exception) {
            logger.error("Error performing additional maintenance", e)
        }
    }
    
    /**
     * Loguje wyniki maintenance'u
     */
    private fun logMaintenanceResults(
        beforeStats: QueueStats,
        afterStats: QueueStats,
        results: MaintenanceResults
    ) {
        logger.info(
            "=== Queue Maintenance Results ===\n" +
            "Before: ${beforeStats.totalMessages} total messages\n" +
            "After: ${afterStats.totalMessages} total messages\n" +
            "Cleaned: ${results.getTotalCleaned()} messages\n" +
            " - Sent: ${results.sentCleaned}\n" +
            " - Failed: ${results.failedCleaned}\n" +
            " - Expired: ${results.expiredCleaned}\n" +
            " - Abandoned: ${results.abandonedCleaned}\n" +
            "Optimized: ${results.positionsOptimized} queue operations\n" +
            "================================"
        )
    }
    
    /**
     * Klasa przechowująca wyniki operacji maintenance'u
     */
    data class MaintenanceResults(
        var sentCleaned: Int = 0,
        var failedCleaned: Int = 0,
        var expiredCleaned: Int = 0,
        var abandonedCleaned: Int = 0,
        var positionsOptimized: Int = 0
    ) {
        fun getTotalCleaned(): Int = sentCleaned + failedCleaned + expiredCleaned + abandonedCleaned
    }
    
    /**
     * Klasa reprezentująca raport o stanie kolejki
     */
    data class QueueReport(
        val timestamp: Long,
        val totalMessages: Int,
        val queueStats: QueueStats,
        val maintenanceResults: MaintenanceResults,
        val healthStatus: com.smsgateway.app.health.SystemHealth,
        val recommendations: List<String>
    ) {
        companion object {
            fun empty(): QueueReport {
                return QueueReport(
                    timestamp = System.currentTimeMillis(),
                    totalMessages = 0,
                    queueStats = QueueStats.empty(),
                    maintenanceResults = MaintenanceResults(),
                    healthStatus = com.smsgateway.app.health.SystemHealth.createUnhealthy("Unknown"),
                    recommendations = emptyList()
                )
            }
        }
    }
}