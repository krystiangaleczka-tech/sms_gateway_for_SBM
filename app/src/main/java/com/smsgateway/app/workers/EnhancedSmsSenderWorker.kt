package com.smsgateway.app.workers

import android.content.Context
import android.telephony.SmsManager
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.smsgateway.app.database.AppDatabase
import com.smsgateway.app.database.SmsMessage
import com.smsgateway.app.database.SmsStatus
import com.smsgateway.app.utils.SmsManagerWrapper
import com.smsgateway.app.retry.RetryService
import com.smsgateway.app.health.HealthChecker
import com.smsgateway.app.queue.SmsQueueService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.slf4j.LoggerFactory
import java.lang.Exception
import java.util.concurrent.TimeUnit

/**
 * Enhanced Worker wysyłający wiadomości SMS
 * Uruchamiany w zaplanowanym czasie przez EnhancedSmsSchedulerWorker
 * Zintegrowany z RetryService, HealthChecker i nowym systemem obsługi błędów
 */
@HiltWorker
class EnhancedSmsSenderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val smsManagerWrapper: SmsManagerWrapper,
    private val retryService: RetryService,
    private val healthChecker: HealthChecker,
    private val smsQueueService: SmsQueueService
) : CoroutineWorker(applicationContext, workerParams) {
    
    private val logger = LoggerFactory.getLogger(EnhancedSmsSenderWorker::class.java)
    
    override suspend fun doWork(): Result {
        val smsId = inputData.getLong("sms_id", -1)
        
        if (smsId == -1L) {
            logger.error("Invalid SMS ID provided to EnhancedSmsSenderWorker")
            return Result.failure()
        }
        
        return try {
            logger.info("EnhancedSmsSenderWorker started for SMS ID: $smsId")
            
            // Sprawdzenie zdrowia systemu przed wysyłką
            val healthStatus = healthChecker.performHealthCheck()
            logger.info("System health status before sending: ${healthStatus.overallStatus}")
            
            if (healthStatus.hasCriticalIssues()) {
                logger.warn("System has critical issues, postponing SMS sending: ${healthStatus.getIssues()}")
                return Result.retry()
            }
            
            // Pobranie bazy danych
            val database = AppDatabase.getDatabase(applicationContext)
            val smsRepository = database.smsDao().let { 
                com.smsgateway.app.database.SmsRepository(it) 
            }
            
            // Pobranie wiadomości SMS
            val smsMessage = smsRepository.getSmsById(smsId)
            
            if (smsMessage == null) {
                logger.error("SMS with ID $smsId not found in database")
                return Result.failure()
            }
            
            // Walidacja wiadomości
            val validationResult = validateSmsMessage(smsMessage)
            if (!validationResult.isValid) {
                logger.error("SMS validation failed for ID $smsId: ${validationResult.error}")
                markAsFailed(smsRepository, smsMessage, validationResult.error ?: "Validation failed")
                return Result.failure()
            }
            
            // Sprawdzenie statusu wiadomości
            if (smsMessage.status != SmsStatus.SCHEDULED) {
                logger.warn(
                    "SMS ID: $smsId has unexpected status: ${smsMessage.status}. " +
                    "Expected: SCHEDULED"
                )
                return Result.success()
            }
            
            // Aktualizacja statusu na SENDING
            smsRepository.updateSmsStatus(smsId, SmsStatus.SENDING)
            
            logger.info(
                "Sending SMS to ${smsMessage.phoneNumber} with content: \"${smsMessage.messageContent}\", " +
                "Priority: ${smsMessage.priority}, " +
                "Retry strategy: ${smsMessage.retryStrategy}"
            )
            
            // Wysyłka SMS
            val result = sendSmsMessage(smsMessage)
            
            // Aktualizacja statusu na podstawie wyniku
            if (result.success) {
                handleSuccess(smsRepository, smsMessage)
                return Result.success()
            } else {
                return handleFailure(smsRepository, smsMessage, result.errorMessage ?: "Unknown error")
            }
            
        } catch (e: Exception) {
            logger.error("EnhancedSmsSenderWorker failed for SMS ID: $smsId", e)
            Result.failure()
        }
    }
    
    /**
     * Waliduje wiadomość SMS przed wysyłką
     */
    private fun validateSmsMessage(smsMessage: SmsMessage): ValidationResult {
        return try {
            // Sprawdzenie numeru telefonu
            if (smsMessage.phoneNumber.isBlank()) {
                return ValidationResult(false, "Phone number is empty")
            }
            
            if (!isValidPhoneNumber(smsMessage.phoneNumber)) {
                return ValidationResult(false, "Invalid phone number format: ${smsMessage.phoneNumber}")
            }
            
            // Sprawdzenie treści wiadomości
            if (smsMessage.messageContent.isBlank()) {
                return ValidationResult(false, "Message content is empty")
            }
            
            if (smsMessage.messageContent.length > 160) {
                logger.warn("Message ID: ${smsMessage.id} exceeds 160 characters (${smsMessage.messageContent.length})")
                // Nie blokujemy wysyłki, tylko ostrzegamy
            }
            
            // Sprawdzenie priorytetu
            if (smsMessage.priority == null) {
                logger.warn("Message ID: ${smsMessage.id} has no priority set")
            }
            
            // Sprawdzenie strategii retry
            if (smsMessage.retryStrategy == null) {
                logger.warn("Message ID: ${smsMessage.id} has no retry strategy set")
            }
            
            ValidationResult(true)
        } catch (e: Exception) {
            logger.error("Error validating SMS message ID: ${smsMessage.id}", e)
            ValidationResult(false, "Validation error: ${e.message}")
        }
    }
    
    /**
     * Sprawdza czy numer telefonu jest prawidłowy
     */
    private fun isValidPhoneNumber(phoneNumber: String): Boolean {
        // Prosta walidacja numeru telefonu
        val phoneRegex = Regex("^\\+?[0-9]{9,15}$")
        return phoneRegex.matches(phoneNumber.replace(Regex("[\\s\\-\\(\\)]"), ""))
    }
    
    /**
     * Obsługuje pomyślną wysyłkę SMS
     */
    private suspend fun handleSuccess(
        smsRepository: com.smsgateway.app.database.SmsRepository,
        smsMessage: SmsMessage
    ) {
        try {
            smsRepository.updateSmsStatusWithSentTime(
                smsMessage.id,
                SmsStatus.SENT,
                System.currentTimeMillis(),
                null
            )
            
            logger.info("SMS ID: ${smsMessage.id} sent successfully")
            
            // Publikacja zdarzenia sukcesu (do implementacji z EventPublisher)
            publishSmsEvent(smsMessage, "SENT", null)
            
        } catch (e: Exception) {
            logger.error("Error handling success for SMS ID: ${smsMessage.id}", e)
        }
    }
    
    /**
     * Obsługuje nieudaną wysyłkę SMS
     */
    private suspend fun handleFailure(
        smsRepository: com.smsgateway.app.database.SmsRepository,
        smsMessage: SmsMessage,
        errorMessage: String
    ): Result {
        return try {
            // Sprawdzenie czy błąd jest retryable
            val isRetryable = retryService.shouldRetry(smsMessage.retryCount, errorMessage)
            
            if (isRetryable && smsMessage.retryCount < smsMessage.maxRetries) {
                // Przygotowanie wiadomości do ponowienia
                val updatedMessage = retryService.prepareMessageForRetry(smsMessage)
                
                // Aktualizacja wiadomości w bazie danych
                smsRepository.updateSmsMessage(updatedMessage)
                
                logger.warn(
                    "SMS ID: ${smsMessage.id} failed (attempt ${smsMessage.retryCount + 1}/${smsMessage.maxRetries}): $errorMessage"
                )
                
                // Publikacja zdarzenia błędu
                publishSmsEvent(smsMessage, "FAILED_RETRY", errorMessage)
                
                // Zwróć retry, aby WorkManager spróbował ponownie
                Result.retry()
            } else {
                // Osiągnięto maksymalną liczbę prób lub błąd nie jest retryable
                markAsFailed(smsRepository, smsMessage, errorMessage)
                
                logger.error(
                    "SMS ID: ${smsMessage.id} failed permanently after ${smsMessage.maxRetries} attempts: $errorMessage"
                )
                
                // Publikacja zdarzenia permanentnej porażki
                publishSmsEvent(smsMessage, "FAILED_PERMANENT", errorMessage)
                
                Result.failure()
            }
        } catch (e: Exception) {
            logger.error("Error handling failure for SMS ID: ${smsMessage.id}", e)
            markAsFailed(smsRepository, smsMessage, "Error handling failure: ${e.message}")
            Result.failure()
        }
    }
    
    /**
     * Oznacza wiadomość jako permanentnie nieudaną
     */
    private suspend fun markAsFailed(
        smsRepository: com.smsgateway.app.database.SmsRepository,
        smsMessage: SmsMessage,
        errorMessage: String
    ) {
        try {
            smsRepository.updateSmsStatusWithErrorAndIncrementRetry(
                smsMessage.id,
                SmsStatus.FAILED,
                System.currentTimeMillis(),
                errorMessage
            )
        } catch (e: Exception) {
            logger.error("Error marking SMS as failed: ${smsMessage.id}", e)
        }
    }
    
    /**
     * Wysyła wiadomość SMS za pomocą SmsManagerWrapper
     */
    private fun sendSmsMessage(smsMessage: SmsMessage): SmsResult {
        return try {
            // Użycie wrapper'a na SmsManager
            smsManagerWrapper.sendTextMessage(
                smsMessage.phoneNumber,
                smsMessage.messageContent
            )
            
            SmsResult(success = true)
        } catch (e: Exception) {
            logger.error("Failed to send SMS to ${smsMessage.phoneNumber}", e)
            SmsResult(
                success = false,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * Publikuje zdarzenie związane z SMS (do integracji z EventPublisher)
     */
    private suspend fun publishSmsEvent(
        smsMessage: SmsMessage,
        eventType: String,
        errorMessage: String?
    ) {
        try {
            // Tutaj można zintegrować z EventPublisher (do implementacji)
            logger.info(
                "SMS Event published - " +
                "ID: ${smsMessage.id}, " +
                "Type: $eventType, " +
                "Phone: ${smsMessage.phoneNumber}, " +
                "Priority: ${smsMessage.priority}" +
                if (errorMessage != null) ", Error: $errorMessage" else ""
            )
        } catch (e: Exception) {
            logger.error("Error publishing SMS event for ID: ${smsMessage.id}", e)
        }
    }
    
    /**
     * Klasa danych reprezentująca wynik walidacji
     */
    data class ValidationResult(
        val isValid: Boolean,
        val error: String? = null
    )
    
    /**
     * Klasa danych reprezentująca wynik wysyłki SMS
     */
    data class SmsResult(
        val success: Boolean,
        val errorMessage: String? = null,
        val errorCode: String? = null
    )
}