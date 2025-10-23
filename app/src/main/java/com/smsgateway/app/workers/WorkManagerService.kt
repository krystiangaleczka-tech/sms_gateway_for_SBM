package com.smsgateway.app.workers

import android.content.Context
import androidx.work.*
import com.smsgateway.app.database.SmsMessage
import java.util.concurrent.TimeUnit

/**
 * Serwis abstrakujący operacje na WorkManager
 * Umożliwia planowanie zadań związanych z wysyłką SMS
 */
class WorkManagerService(private val context: Context) {
    
    private val workManager = WorkManager.getInstance(context)
    
    companion object {
        // Unikalne nazwy dla zadań WorkManager
        const val SCHEDULER_WORK_NAME = "SmsSchedulerWorker"
        const val SENDER_WORK_PREFIX = "SmsSenderWorker_"
        
        // Tagi dla identyfikacji zadań
        const val SMS_WORK_TAG = "sms-work"
        const val SCHEDULER_TAG = "sms-scheduler"
        const val SENDER_TAG = "sms-sender"
    }
    
    /**
     * Uruchamia okresowego SmsSchedulerWorker
     * Sprawdza wiadomości w kolejce co 15 minut
     */
    fun startPeriodicScheduler() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        
        val periodicWorkRequest = PeriodicWorkRequestBuilder<SmsSchedulerWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag(SCHEDULER_TAG)
            .addTag(SMS_WORK_TAG)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            SCHEDULER_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWorkRequest
        )
    }
    
    /**
     * Planuje wysyłkę konkretnego SMS
     * Tworzy OneTimeWorkRequest dla SmsSenderWorker
     */
    fun scheduleSmsSending(smsMessage: SmsMessage) {
        val inputData = workDataOf(
            "sms_id" to smsMessage.id
        )
        
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        
        val delay = calculateDelay(smsMessage)
        
        val workRequest = OneTimeWorkRequestBuilder<SmsSenderWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(SENDER_TAG)
            .addTag(SMS_WORK_TAG)
            .addTag("${SENDER_TAG}_${smsMessage.id}")
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        
        workManager.enqueueUniqueWork(
            "${SENDER_WORK_PREFIX}${smsMessage.id}",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }
    
    /**
     * Anuluje zaplanowane zadanie dla konkretnego SMS
     */
    fun cancelSmsSending(smsId: Long) {
        workManager.cancelUniqueWork("${SENDER_WORK_PREFIX}$smsId")
    }
    
    /**
     * Anuluje wszystkie zadania związane z SMS
     */
    fun cancelAllSmsWork() {
        workManager.cancelAllWorkByTag(SMS_WORK_TAG)
    }
    
    /**
     * Zatrzymuje okresowego schedulera
     */
    fun stopPeriodicScheduler() {
        workManager.cancelUniqueWork(SCHEDULER_WORK_NAME)
    }
    
    /**
     * Oblicza opóźnienie dla zadania wysyłki SMS
     */
    private fun calculateDelay(smsMessage: SmsMessage): Long {
        val currentTime = System.currentTimeMillis()
        val scheduledTime = smsMessage.scheduledAt ?: return 0
        
        return if (scheduledTime > currentTime) {
            scheduledTime - currentTime
        } else {
            0 // Wyślij natychmiast, jeśli czas minął
        }
    }
    
    /**
     * Sprawdza status zadania dla konkretnego SMS
     */
    suspend fun getSmsWorkStatus(smsId: Long): WorkInfo? {
        return workManager.getWorkInfoById("${SENDER_WORK_PREFIX}$smsId").await()
    }
    
    /**
     * Pobiera informacje o wszystkich aktywnych zadaniach SMS
     */
    suspend fun getAllSmsWorkInfo(): List<WorkInfo> {
        return workManager.getWorkInfosByTag(SMS_WORK_TAG).await()
    }
}