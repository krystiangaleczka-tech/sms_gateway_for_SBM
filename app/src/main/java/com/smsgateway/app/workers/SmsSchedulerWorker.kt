package com.smsgateway.app.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.smsgateway.app.database.AppDatabase
import com.smsgateway.app.database.SmsMessage
import com.smsgateway.app.database.SmsStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * Worker okresowy sprawdzający wiadomości SMS w kolejce
 * Uruchamiany co 15 minut, planuje zadania wysyłki SMS
 */
@HiltWorker
class SmsSchedulerWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val workManagerService: WorkManagerService
) : CoroutineWorker(context, workerParams) {
    
    private val logger = LoggerFactory.getLogger(SmsSchedulerWorker::class.java)
    
    override suspend fun doWork(): Result {
        return try {
            logger.info("SmsSchedulerWorker started - checking SMS queue")
            
            // Pobranie bazy danych
            val database = AppDatabase.getDatabase(applicationContext)
            val smsRepository = database.smsDao().let { 
                com.smsgateway.app.database.SmsRepository(it) 
            }
            
            // Sprawdzenie wiadomości w statusie QUEUED
            val queuedMessages = smsRepository.getQueuedSms()
            logger.info("Found ${queuedMessages.size} queued messages")
            
            var scheduledCount = 0
            val currentTime = System.currentTimeMillis()
            
            for (message in queuedMessages) {
                try {
                    when (message.status) {
                        SmsStatus.QUEUED -> {
                            // Sprawdź czy czas planowania minął
                            val scheduledTime = message.scheduledAt ?: 0
                            
                            if (scheduledTime <= currentTime) {
                                // Zaktualizuj status na SCHEDULED
                                smsRepository.updateSmsStatus(
                                    message.id, 
                                    SmsStatus.SCHEDULED
                                )
                                
                                // Pobierz zaktualizowaną wiadomość
                                val updatedMessage = smsRepository.getSmsById(message.id)
                                if (updatedMessage != null) {
                                    // Zaplanuj wysyłkę SMS
                                    workManagerService.scheduleSmsSending(updatedMessage)
                                    scheduledCount++
                                    
                                    logger.info(
                                        "Scheduled SMS sending for message ID: ${message.id}, " +
                                        "Phone: ${message.phoneNumber}, " +
                                        "Scheduled at: ${updatedMessage.scheduledAt}"
                                    )
                                }
                            } else {
                                // Oblicz pozostały czas do planowania
                                val remainingTime = scheduledTime - currentTime
                                val remainingMinutes = TimeUnit.MILLISECONDS.toMinutes(remainingTime)
                                
                                logger.debug(
                                    "Message ID: ${message.id} will be scheduled in $remainingMinutes minutes"
                                )
                            }
                        }
                        
                        SmsStatus.SCHEDULED -> {
                            // Sprawdź czy zadanie wysyłki jest już zaplanowane
                            val workInfo = workManagerService.getSmsWorkStatus(message.id)
                            
                            if (workInfo == null || workInfo.state.isFinished) {
                                // Zaplanuj ponownie, jeśli zadanie nie istnieje lub zostało zakończone
                                workManagerService.scheduleSmsSending(message)
                                scheduledCount++
                                
                                logger.info(
                                    "Rescheduled SMS sending for message ID: ${message.id}"
                                )
                            }
                        }
                        
                        else -> {
                            // Inne statusy są ignorowane przez schedulera
                            logger.debug(
                                "Ignoring message ID: ${message.id} with status: ${message.status}"
                            )
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Error processing message ID: ${message.id}", e)
                }
            }
            
            // Sprawdzenie wiadomości, które można ponowić po błędzie
            val retryableMessages = smsRepository.getRetryableFailedSms()
            logger.info("Found ${retryableMessages.size} retryable failed messages")
            
            for (message in retryableMessages) {
                try {
                    // Oblicz opóźnienie ponowienia (eksponencjalne backoff)
                    val retryDelay = calculateRetryDelay(message.retryCount)
                    val newScheduledTime = currentTime + retryDelay
                    
                    // Zaktualizuj status i czas planowania
                    smsRepository.updateSmsStatusWithSentTime(
                        message.id,
                        SmsStatus.SCHEDULED,
                        newScheduledTime,
                        message.errorMessage
                    )
                    
                    // Pobierz zaktualizowaną wiadomość
                    val updatedMessage = smsRepository.getSmsById(message.id)
                    if (updatedMessage != null) {
                        // Zaplanuj ponowienie wysyłki
                        workManagerService.scheduleSmsSending(updatedMessage)
                        scheduledCount++
                        
                        logger.info(
                            "Scheduled retry for message ID: ${message.id}, " +
                            "Retry count: ${message.retryCount}, " +
                            "Delay: ${TimeUnit.MILLISECONDS.toMinutes(retryDelay)} minutes"
                        )
                    }
                } catch (e: Exception) {
                    logger.error("Error scheduling retry for message ID: ${message.id}", e)
                }
            }
            
            logger.info("SmsSchedulerWorker completed - scheduled $scheduledCount messages")
            
            // Zwróć sukces, jeśli co najmniej jedna wiadomość została zaplanowana
            if (scheduledCount > 0) {
                Result.success()
            } else {
                Result.success()
            }
            
        } catch (e: Exception) {
            logger.error("SmsSchedulerWorker failed", e)
            Result.failure()
        }
    }
    
    /**
     * Oblicza opóźnienie ponowienia na podstawie liczby prób
     * Używa eksponencjalnego backoff: 5min, 15min, 45min
     */
    private fun calculateRetryDelay(retryCount: Int): Long {
        val baseDelayMinutes = 5L
        val maxDelayMinutes = 60L
        
        val delayMinutes = (baseDelayMinutes * kotlin.math.pow(3.0, retryCount.toDouble())).toLong()
        
        return kotlin.math.min(delayMinutes, maxDelayMinutes) * 60 * 1000 // Konwersja na milisekundy
    }
}