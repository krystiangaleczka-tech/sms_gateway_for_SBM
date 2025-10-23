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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.slf4j.LoggerFactory
import java.lang.Exception

/**
 * Worker wysyłający wiadomości SMS
 * Uruchamiany w zaplanowanym czasie przez SmsSchedulerWorker
 */
@HiltWorker
class SmsSenderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val smsManagerWrapper: SmsManagerWrapper
) : CoroutineWorker(context, workerParams) {
    
    private val logger = LoggerFactory.getLogger(SmsSenderWorker::class.java)
    
    override suspend fun doWork(): Result {
        val smsId = inputData.getLong("sms_id", -1)
        
        if (smsId == -1L) {
            logger.error("Invalid SMS ID provided to SmsSenderWorker")
            return Result.failure()
        }
        
        return try {
            logger.info("SmsSenderWorker started for SMS ID: $smsId")
            
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
                "Sending SMS to ${smsMessage.phoneNumber} with content: \"${smsMessage.messageContent}\""
            )
            
            // Wysyłka SMS
            val result = sendSmsMessage(smsMessage)
            
            // Aktualizacja statusu na podstawie wyniku
            if (result.success) {
                smsRepository.updateSmsStatusWithSentTime(
                    smsId,
                    SmsStatus.SENT,
                    System.currentTimeMillis(),
                    null
                )
                
                logger.info("SMS ID: $smsId sent successfully")
                return Result.success()
            } else {
                // Sprawdzenie czy można ponowić próbę
                val canRetry = smsMessage.retryCount < smsMessage.maxRetries
                
                if (canRetry) {
                    // Zwiększenie licznika prób
                    smsRepository.incrementRetryCount(smsId)
                    
                    // Aktualizacja statusu na FAILED z informacją o błędzie
                    smsRepository.updateSmsStatusWithError(
                        smsId,
                        SmsStatus.FAILED,
                        System.currentTimeMillis(),
                        result.errorMessage
                    )
                    
                    logger.warn(
                        "SMS ID: $smsId failed (attempt ${smsMessage.retryCount + 1}/${smsMessage.maxRetries}): ${result.errorMessage}"
                    )
                    
                    // Zwróć retry, aby WorkManager spróbował ponownie
                    return Result.retry()
                } else {
                    // Osiągnięto maksymalną liczbę prób
                    smsRepository.updateSmsStatusWithErrorAndIncrementRetry(
                        smsId,
                        SmsStatus.FAILED,
                        System.currentTimeMillis(),
                        result.errorMessage
                    )
                    
                    logger.error(
                        "SMS ID: $smsId failed permanently after ${smsMessage.maxRetries} attempts: ${result.errorMessage}"
                    )
                    
                    return Result.failure()
                }
            }
            
        } catch (e: Exception) {
            logger.error("SmsSenderWorker failed for SMS ID: $smsId", e)
            Result.failure()
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
     * Klasa danych reprezentująca wynik wysyłki SMS
     */
    data class SmsResult(
        val success: Boolean,
        val errorMessage: String? = null
    )
}