package com.smsgateway.app.workers

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.testing.TestWorkerBuilder
import com.smsgateway.app.database.AppDatabase
import com.smsgateway.app.database.SmsMessage
import com.smsgateway.app.database.SmsStatus
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

import org.junit.Assert.*
import org.junit.Before

/**
 * Testy jednostkowe dla SmsSchedulerWorker
 */
class SmsSchedulerWorkerTest {
    
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var workManagerService: WorkManagerService
    private lateinit var worker: SmsSchedulerWorker
    
    @Before
    fun setup() {
        // Mockowanie zależności
        context = mockk()
        database = mockk()
        workManagerService = mockk()
        
        // Konfiguracja mocków
        every { AppDatabase.getDatabase(any()) } returns database
        every { database.smsDao() } returns mockk()
        
        // Tworzenie worker
        val params = WorkerParameters(
            TestWorkerBuilder.UUID,
            mockk(),
            mockk(),
            mockk(),
            0,
            emptyList(),
            mockk()
        )
        
        worker = spyk(SmsSchedulerWorker(context, params, workManagerService))
    }
    
    @Test
    fun `doWork should return success when processing queued messages`() = runTest {
        // Przygotowanie danych testowych
        val queuedMessage = SmsMessage(
            id = 1,
            phoneNumber = "+48123456789",
            messageContent = "Test message",
            status = SmsStatus.QUEUED,
            scheduledAt = System.currentTimeMillis() - 1000, // W przeszłości
            sentAt = null,
            retryCount = 0,
            maxRetries = 3,
            errorMessage = null
        )
        
        // Mockowanie zapytań do bazy danych
        val smsDao = database.smsDao()
        val smsRepository = com.smsgateway.app.database.SmsRepository(smsDao)
        
        every { smsRepository.getQueuedSms() } returns listOf(queuedMessage)
        every { smsRepository.getSmsById(queuedMessage.id) } returns queuedMessage.copy(status = SmsStatus.SCHEDULED)
        every { smsRepository.updateSmsStatus(queuedMessage.id, SmsStatus.SCHEDULED) } just Runs
        
        // Mockowanie WorkManagerService
        every { workManagerService.scheduleSmsSending(any()) } just Runs
        
        // Wykonanie testu
        val result = worker.doWork()
        
        // Weryfikacja wyniku
        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        
        // Weryfikacja interakcji
        verify { smsRepository.updateSmsStatus(queuedMessage.id, SmsStatus.SCHEDULED) }
        verify { workManagerService.scheduleSmsSending(any()) }
    }
    
    @Test
    fun `doWork should skip messages with future scheduled time`() = runTest {
        // Przygotowanie danych testowych
        val futureMessage = SmsMessage(
            id = 1,
            phoneNumber = "+48123456789",
            messageContent = "Future message",
            status = SmsStatus.QUEUED,
            scheduledAt = System.currentTimeMillis() + 3600000, // Za godzinę
            sentAt = null,
            retryCount = 0,
            maxRetries = 3,
            errorMessage = null
        )
        
        // Mockowanie zapytań do bazy danych
        val smsDao = database.smsDao()
        val smsRepository = com.smsgateway.app.database.SmsRepository(smsDao)
        
        every { smsRepository.getQueuedSms() } returns listOf(futureMessage)
        
        // Wykonanie testu
        val result = worker.doWork()
        
        // Weryfikacja wyniku
        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        
        // Weryfikacja braku interakcji z WorkManagerService
        verify(exactly = 0) { workManagerService.scheduleSmsSending(any()) }
    }
    
    @Test
    fun `doWork should handle retryable failed messages`() = runTest {
        // Przygotowanie danych testowych
        val failedMessage = SmsMessage(
            id = 1,
            phoneNumber = "+48123456789",
            messageContent = "Failed message",
            status = SmsStatus.FAILED,
            scheduledAt = System.currentTimeMillis() - 3600000, // Godzinę temu
            sentAt = System.currentTimeMillis() - 1800000, // 30 minut temu
            retryCount = 1,
            maxRetries = 3,
            errorMessage = "Network error"
        )
        
        // Mockowanie zapytań do bazy danych
        val smsDao = database.smsDao()
        val smsRepository = com.smsgateway.app.database.SmsRepository(smsDao)
        
        every { smsRepository.getQueuedSms() } returns emptyList()
        every { smsRepository.getRetryableFailedSms() } returns listOf(failedMessage)
        every { smsRepository.updateSmsStatusWithSentTime(any(), any(), any(), any()) } just Runs
        
        // Mockowanie WorkManagerService
        every { workManagerService.scheduleSmsSending(any()) } just Runs
        
        // Wykonanie testu
        val result = worker.doWork()
        
        // Weryfikacja wyniku
        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        
        // Weryfikacja interakcji
        verify { smsRepository.updateSmsStatusWithSentTime(
            failedMessage.id,
            SmsStatus.SCHEDULED,
            any(),
            failedMessage.errorMessage
        ) }
        verify { workManagerService.scheduleSmsSending(any()) }
    }
    
    @Test
    fun `doWork should return failure when exception occurs`() = runTest {
        // Mockowanie rzucenia wyjątku
        val smsDao = database.smsDao()
        val smsRepository = com.smsgateway.app.database.SmsRepository(smsDao)
        
        every { smsRepository.getQueuedSms() } throws RuntimeException("Database error")
        
        // Wykonanie testu
        val result = worker.doWork()
        
        // Weryfikacja wyniku
        assertEquals(androidx.work.ListenableWorker.Result.failure(), result)
    }
    
    @Test
    fun `calculateRetryDelay should use exponential backoff`() {
        // Użycie metody prywatnej przez refleksję
        val method = SmsSchedulerWorker::class.java.getDeclaredMethod(
            "calculateRetryDelay",
            Int::class.java
        )
        method.isAccessible = true
        
        // Testowanie różnych wartości retryCount
        val delay0 = method.invoke(worker, 0) as Long
        val delay1 = method.invoke(worker, 1) as Long
        val delay2 = method.invoke(worker, 2) as Long
        
        // Weryfikacja eksponencjalnego wzrostu (5min, 15min, 45min)
        assertEquals(5 * 60 * 1000L, delay0) // 5 minut
        assertEquals(15 * 60 * 1000L, delay1) // 15 minut
        assertEquals(45 * 60 * 1000L, delay2) // 45 minut
    }
    
    @Test
    fun `calculateRetryDelay should cap at maximum delay`() {
        // Użycie metody prywatnej przez refleksję
        val method = SmsSchedulerWorker::class.java.getDeclaredMethod(
            "calculateRetryDelay",
            Int::class.java
        )
        method.isAccessible = true
        
        // Testowanie dużej wartości retryCount
        val delay10 = method.invoke(worker, 10) as Long
        
        // Weryfikacja ograniczenia do maksymalnego opóźnienia (60 minut)
        assertEquals(60 * 60 * 1000L, delay10) // 60 minut
    }
}