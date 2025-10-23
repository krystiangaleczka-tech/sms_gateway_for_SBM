package com.smsgateway.app.workers

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.testing.TestWorkerBuilder
import com.smsgateway.app.database.AppDatabase
import com.smsgateway.app.database.SmsMessage
import com.smsgateway.app.database.SmsStatus
import com.smsgateway.app.utils.SmsManagerWrapper
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Test

import org.junit.Assert.*
import org.junit.Before

/**
 * Testy jednostkowe dla SmsSenderWorker
 */
class SmsSenderWorkerTest {
    
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var smsManagerWrapper: SmsManagerWrapper
    private lateinit var worker: SmsSenderWorker
    
    @Before
    fun setup() {
        // Mockowanie zależności
        context = mockk()
        database = mockk()
        smsManagerWrapper = mockk()
        
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
        
        worker = spyk(SmsSenderWorker(context, params, smsManagerWrapper))
    }
    
    @Test
    fun `doWork should return success when SMS is sent successfully`() = runTest {
        // Przygotowanie danych testowych
        val smsId = 1L
        val scheduledMessage = SmsMessage(
            id = smsId,
            phoneNumber = "+48123456789",
            messageContent = "Test message",
            status = SmsStatus.SCHEDULED,
            scheduledAt = System.currentTimeMillis(),
            sentAt = null,
            retryCount = 0,
            maxRetries = 3,
            errorMessage = null
        )
        
        // Mockowanie zapytań do bazy danych
        val smsDao = database.smsDao()
        val smsRepository = com.smsgateway.app.database.SmsRepository(smsDao)
        
        every { smsRepository.getSmsById(smsId) } returns scheduledMessage
        every { smsRepository.updateSmsStatus(smsId, SmsStatus.SENDING) } just Runs
        every { smsRepository.updateSmsStatusWithSentTime(smsId, SmsStatus.SENT, any(), null) } just Runs
        
        // Mockowanie SmsManagerWrapper
        every { smsManagerWrapper.sendTextMessage(any(), any()) } just Runs
        
        // Mockowanie danych wejściowych
        val inputData = androidx.work.Data.Builder()
            .putLong("sms_id", smsId)
            .build()
        
        val testWorker = TestWorkerBuilder<SmsSenderWorker>(
            context,
            SmsSenderWorker::class.java,
            inputData
        ).setWorkerFactory { worker }
            .build()
        
        // Wykonanie testu
        val result = testWorker.doWork()
        
        // Weryfikacja wyniku
        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        
        // Weryfikacja interakcji
        verify { smsRepository.updateSmsStatus(smsId, SmsStatus.SENDING) }
        verify { smsManagerWrapper.sendTextMessage(scheduledMessage.phoneNumber, scheduledMessage.messageContent) }
        verify { smsRepository.updateSmsStatusWithSentTime(smsId, SmsStatus.SENT, any(), null) }
    }
    
    @Test
    fun `doWork should return retry when SMS sending fails and retries are available`() = runTest {
        // Przygotowanie danych testowych
        val smsId = 1L
        val scheduledMessage = SmsMessage(
            id = smsId,
            phoneNumber = "+48123456789",
            messageContent = "Test message",
            status = SmsStatus.SCHEDULED,
            scheduledAt = System.currentTimeMillis(),
            sentAt = null,
            retryCount = 0,
            maxRetries = 3,
            errorMessage = null
        )
        
        // Mockowanie zapytań do bazy danych
        val smsDao = database.smsDao()
        val smsRepository = com.smsgateway.app.database.SmsRepository(smsDao)
        
        every { smsRepository.getSmsById(smsId) } returns scheduledMessage
        every { smsRepository.updateSmsStatus(smsId, SmsStatus.SENDING) } just Runs
        every { smsRepository.incrementRetryCount(smsId) } just Runs
        every { smsRepository.updateSmsStatusWithError(smsId, SmsStatus.FAILED, any(), any()) } just Runs
        
        // Mockowanie SmsManagerWrapper - rzucenie wyjątku
        every { smsManagerWrapper.sendTextMessage(any(), any()) } throws 
            SmsManagerWrapper.SmsException("Network error")
        
        // Mockowanie danych wejściowych
        val inputData = androidx.work.Data.Builder()
            .putLong("sms_id", smsId)
            .build()
        
        val testWorker = TestWorkerBuilder<SmsSenderWorker>(
            context,
            SmsSenderWorker::class.java,
            inputData
        ).setWorkerFactory { worker }
            .build()
        
        // Wykonanie testu
        val result = testWorker.doWork()
        
        // Weryfikacja wyniku
        assertEquals(androidx.work.ListenableWorker.Result.retry(), result)
        
        // Weryfikacja interakcji
        verify { smsRepository.updateSmsStatus(smsId, SmsStatus.SENDING) }
        verify { smsManagerWrapper.sendTextMessage(scheduledMessage.phoneNumber, scheduledMessage.messageContent) }
        verify { smsRepository.incrementRetryCount(smsId) }
        verify { smsRepository.updateSmsStatusWithError(smsId, SmsStatus.FAILED, any(), any()) }
    }
    
    @Test
    fun `doWork should return failure when SMS sending fails and max retries reached`() = runTest {
        // Przygotowanie danych testowych
        val smsId = 1L
        val scheduledMessage = SmsMessage(
            id = smsId,
            phoneNumber = "+48123456789",
            messageContent = "Test message",
            status = SmsStatus.SCHEDULED,
            scheduledAt = System.currentTimeMillis(),
            sentAt = null,
            retryCount = 2, // Blisko maksymalnej liczby prób
            maxRetries = 3,
            errorMessage = null
        )
        
        // Mockowanie zapytań do bazy danych
        val smsDao = database.smsDao()
        val smsRepository = com.smsgateway.app.database.SmsRepository(smsDao)
        
        every { smsRepository.getSmsById(smsId) } returns scheduledMessage
        every { smsRepository.updateSmsStatus(smsId, SmsStatus.SENDING) } just Runs
        every { smsRepository.updateSmsStatusWithErrorAndIncrementRetry(smsId, SmsStatus.FAILED, any(), any()) } just Runs
        
        // Mockowanie SmsManagerWrapper - rzucenie wyjątku
        every { smsManagerWrapper.sendTextMessage(any(), any()) } throws 
            SmsManagerWrapper.SmsException("Network error")
        
        // Mockowanie danych wejściowych
        val inputData = androidx.work.Data.Builder()
            .putLong("sms_id", smsId)
            .build()
        
        val testWorker = TestWorkerBuilder<SmsSenderWorker>(
            context,
            SmsSenderWorker::class.java,
            inputData
        ).setWorkerFactory { worker }
            .build()
        
        // Wykonanie testu
        val result = testWorker.doWork()
        
        // Weryfikacja wyniku
        assertEquals(androidx.work.ListenableWorker.Result.failure(), result)
        
        // Weryfikacja interakcji
        verify { smsRepository.updateSmsStatus(smsId, SmsStatus.SENDING) }
        verify { smsManagerWrapper.sendTextMessage(scheduledMessage.phoneNumber, scheduledMessage.messageContent) }
        verify { smsRepository.updateSmsStatusWithErrorAndIncrementRetry(smsId, SmsStatus.FAILED, any(), any()) }
    }
    
    @Test
    fun `doWork should return failure when SMS message is not found`() = runTest {
        // Przygotowanie danych testowych
        val smsId = 1L
        
        // Mockowanie zapytań do bazy danych
        val smsDao = database.smsDao()
        val smsRepository = com.smsgateway.app.database.SmsRepository(smsDao)
        
        every { smsRepository.getSmsById(smsId) } returns null
        
        // Mockowanie danych wejściowych
        val inputData = androidx.work.Data.Builder()
            .putLong("sms_id", smsId)
            .build()
        
        val testWorker = TestWorkerBuilder<SmsSenderWorker>(
            context,
            SmsSenderWorker::class.java,
            inputData
        ).setWorkerFactory { worker }
            .build()
        
        // Wykonanie testu
        val result = testWorker.doWork()
        
        // Weryfikacja wyniku
        assertEquals(androidx.work.ListenableWorker.Result.failure(), result)
        
        // Weryfikacja braku interakcji z SmsManagerWrapper
        verify(exactly = 0) { smsManagerWrapper.sendTextMessage(any(), any()) }
    }
    
    @Test
    fun `doWork should return success when SMS message has unexpected status`() = runTest {
        // Przygotowanie danych testowych
        val smsId = 1L
        val sentMessage = SmsMessage(
            id = smsId,
            phoneNumber = "+48123456789",
            messageContent = "Test message",
            status = SmsStatus.SENT, // Już wysłane
            scheduledAt = System.currentTimeMillis(),
            sentAt = System.currentTimeMillis(),
            retryCount = 0,
            maxRetries = 3,
            errorMessage = null
        )
        
        // Mockowanie zapytań do bazy danych
        val smsDao = database.smsDao()
        val smsRepository = com.smsgateway.app.database.SmsRepository(smsDao)
        
        every { smsRepository.getSmsById(smsId) } returns sentMessage
        
        // Mockowanie danych wejściowych
        val inputData = androidx.work.Data.Builder()
            .putLong("sms_id", smsId)
            .build()
        
        val testWorker = TestWorkerBuilder<SmsSenderWorker>(
            context,
            SmsSenderWorker::class.java,
            inputData
        ).setWorkerFactory { worker }
            .build()
        
        // Wykonanie testu
        val result = testWorker.doWork()
        
        // Weryfikacja wyniku
        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        
        // Weryfikacja braku interakcji z SmsManagerWrapper
        verify(exactly = 0) { smsManagerWrapper.sendTextMessage(any(), any()) }
    }
    
    @Test
    fun `doWork should return failure when invalid SMS ID is provided`() = runTest {
        // Mockowanie danych wejściowych z nieprawidłowym ID
        val inputData = androidx.work.Data.Builder()
            .putLong("sms_id", -1L)
            .build()
        
        val testWorker = TestWorkerBuilder<SmsSenderWorker>(
            context,
            SmsSenderWorker::class.java,
            inputData
        ).setWorkerFactory { worker }
            .build()
        
        // Wykonanie testu
        val result = testWorker.doWork()
        
        // Weryfikacja wyniku
        assertEquals(androidx.work.ListenableWorker.Result.failure(), result)
        
        // Weryfikacja braku interakcji z bazą danych
        verify(exactly = 0) { database.smsDao() }
    }
    
    @Test
    fun `doWork should return failure when exception occurs during processing`() = runTest {
        // Przygotowanie danych testowych
        val smsId = 1L
        
        // Mockowanie rzucenia wyjątku
        val smsDao = database.smsDao()
        val smsRepository = com.smsgateway.app.database.SmsRepository(smsDao)
        
        every { smsRepository.getSmsById(smsId) } throws RuntimeException("Database error")
        
        // Mockowanie danych wejściowych
        val inputData = androidx.work.Data.Builder()
            .putLong("sms_id", smsId)
            .build()
        
        val testWorker = TestWorkerBuilder<SmsSenderWorker>(
            context,
            SmsSenderWorker::class.java,
            inputData
        ).setWorkerFactory { worker }
            .build()
        
        // Wykonanie testu
        val result = testWorker.doWork()
        
        // Weryfikacja wyniku
        assertEquals(androidx.work.ListenableWorker.Result.failure(), result)
    }
}