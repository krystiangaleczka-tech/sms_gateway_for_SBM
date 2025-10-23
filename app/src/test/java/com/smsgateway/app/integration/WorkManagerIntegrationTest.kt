package com.smsgateway.app.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.*
import androidx.work.testing.TestDriver
import androidx.work.testing.WorkManagerTestInitHelper
import com.smsgateway.app.database.AppDatabase
import com.smsgateway.app.database.SmsMessage
import com.smsgateway.app.database.SmsStatus
import com.smsgateway.app.workers.SmsSchedulerWorker
import com.smsgateway.app.workers.SmsSenderWorker
import com.smsgateway.app.workers.WorkManagerService
import com.smsgateway.app.utils.SmsManagerWrapper
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.mockito.Mockito.*
import org.mockito.kotlin.*
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WorkManagerIntegrationTest {
    
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var workManager: WorkManager
    private lateinit var testDriver: TestDriver
    private lateinit var workManagerService: WorkManagerService
    private lateinit var smsManagerWrapper: SmsManagerWrapper
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        
        // Initialize test WorkManager
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.ERROR)
            .build()
        
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        
        workManager = WorkManager.getInstance(context)
        testDriver = WorkManagerTestInitHelper.getTestDriver(context)
        
        // Setup in-memory database for testing
        database = AppDatabase.getInMemoryDatabase(context)
        
        // Initialize services
        smsManagerWrapper = mock<SmsManagerWrapper>()
        workManagerService = WorkManagerService(context)
    }
    
    @After
    fun tearDown() {
        database.close()
        workManager.cancelAllWork()
    }
    
    @Test
    fun `should schedule SMS with WorkManager correctly`() = runTest {
        // Given
        val smsMessage = createTestSmsMessage(
            phoneNumber = "+48123456789",
            message = "Test scheduled SMS",
            scheduledAt = System.currentTimeMillis() + 5000 // 5 seconds from now
        )
        
        val messageId = database.smsDao().insert(smsMessage)
        
        // When - Schedule SMS with WorkManager
        val workRequest = workManagerService.scheduleSms(messageId, smsMessage.scheduledAt ?: 0)
        
        // Then - Verify work is scheduled
        val workInfo = workManager.getWorkInfoById(workRequest.id).get()
        assertEquals(WorkInfo.State.ENQUEUED, workInfo.state)
        assertTrue(workInfo.tags.contains("sms-scheduler"))
    }
    
    @Test
    fun `should execute SmsSchedulerWorker correctly`() = runTest {
        // Given
        val smsMessage = createTestSmsMessage(
            phoneNumber = "+48123456789",
            message = "Test scheduled SMS",
            scheduledAt = System.currentTimeMillis() - 1000 // Past time for immediate execution
        )
        
        val messageId = database.smsDao().insert(smsMessage)
        
        val inputData = workDataOf(
            "sms_id" to messageId
        )
        
        val request = OneTimeWorkRequestBuilder<SmsSchedulerWorker>()
            .setInputData(inputData)
            .build()
        
        // When - Execute worker
        workManager.enqueue(request).result.get()
        
        // Wait for worker to complete
        Thread.sleep(1000)
        
        // Then - Verify SMS was moved to queue
        val updatedMessage = database.smsDao().getMessageById(messageId)
        assertNotNull(updatedMessage)
        assertEquals(SmsStatus.QUEUED, updatedMessage?.status)
    }
    
    @Test
    fun `should execute SmsSenderWorker correctly`() = runTest {
        // Given
        val smsMessage = createTestSmsMessage(
            phoneNumber = "+48123456789",
            message = "Test SMS to send",
            status = SmsStatus.QUEUED
        )
        
        val messageId = database.smsDao().insert(smsMessage)
        
        // Mock successful SMS sending
        whenever(smsManagerWrapper.sendSms(any(), any())).thenReturn(true)
        
        val inputData = workDataOf(
            "sms_id" to messageId
        )
        
        val request = OneTimeWorkRequestBuilder<SmsSenderWorker>()
            .setInputData(inputData)
            .build()
        
        // When - Execute worker
        workManager.enqueue(request).result.get()
        
        // Wait for worker to complete
        Thread.sleep(1000)
        
        // Then - Verify SMS was sent
        val updatedMessage = database.smsDao().getMessageById(messageId)
        assertNotNull(updatedMessage)
        assertEquals(SmsStatus.SENT, updatedMessage?.status)
        
        // Verify SMS manager was called
        verify(smsManagerWrapper).sendSms(smsMessage.phoneNumber, smsMessage.messageContent)
    }
    
    @Test
    fun `should handle SMS sending failure and schedule retry`() = runTest {
        // Given
        val smsMessage = createTestSmsMessage(
            phoneNumber = "+48123456789",
            message = "Test SMS that will fail",
            status = SmsStatus.QUEUED,
            retryCount = 0,
            maxRetries = 3
        )
        
        val messageId = database.smsDao().insert(smsMessage)
        
        // Mock SMS sending failure
        whenever(smsManagerWrapper.sendSms(any(), any())).thenReturn(false)
        
        val inputData = workDataOf(
            "sms_id" to messageId
        )
        
        val request = OneTimeWorkRequestBuilder<SmsSenderWorker>()
            .setInputData(inputData)
            .build()
        
        // When - Execute worker
        workManager.enqueue(request).result.get()
        
        // Wait for worker to complete
        Thread.sleep(1000)
        
        // Then - Verify SMS was marked as failed and retry was scheduled
        val updatedMessage = database.smsDao().getMessageById(messageId)
        assertNotNull(updatedMessage)
        assertEquals(SmsStatus.FAILED, updatedMessage?.status)
        assertEquals(1, updatedMessage?.retryCount)
        
        // Verify retry work was scheduled
        val workInfos = workManager.getWorkInfosByTag("sms-retry").get()
        assertTrue(workInfos.isNotEmpty())
    }
    
    @Test
    fun `should handle SMS retry correctly`() = runTest {
        // Given
        val smsMessage = createTestSmsMessage(
            phoneNumber = "+48123456789",
            message = "Test SMS retry",
            status = SmsStatus.FAILED,
            retryCount = 1,
            maxRetries = 3
        )
        
        val messageId = database.smsDao().insert(smsMessage)
        
        // Mock successful SMS sending on retry
        whenever(smsManagerWrapper.sendSms(any(), any())).thenReturn(true)
        
        val inputData = workDataOf(
            "sms_id" to messageId,
            "is_retry" to true
        )
        
        val request = OneTimeWorkRequestBuilder<SmsSenderWorker>()
            .setInputData(inputData)
            .build()
        
        // When - Execute retry worker
        workManager.enqueue(request).result.get()
        
        // Wait for worker to complete
        Thread.sleep(1000)
        
        // Then - Verify SMS was sent on retry
        val updatedMessage = database.smsDao().getMessageById(messageId)
        assertNotNull(updatedMessage)
        assertEquals(SmsStatus.SENT, updatedMessage?.status)
        assertEquals(2, updatedMessage?.retryCount) // Incremented during retry
    }
    
    @Test
    fun `should stop retrying after max retries exceeded`() = runTest {
        // Given
        val smsMessage = createTestSmsMessage(
            phoneNumber = "+48123456789",
            message = "Test SMS with max retries exceeded",
            status = SmsStatus.FAILED,
            retryCount = 3,
            maxRetries = 3
        )
        
        val messageId = database.smsDao().insert(smsMessage)
        
        val inputData = workDataOf(
            "sms_id" to messageId,
            "is_retry" to true
        )
        
        val request = OneTimeWorkRequestBuilder<SmsSenderWorker>()
            .setInputData(inputData)
            .build()
        
        // When - Execute retry worker
        workManager.enqueue(request).result.get()
        
        // Wait for worker to complete
        Thread.sleep(1000)
        
        // Then - Verify SMS was not retried and remains failed
        val updatedMessage = database.smsDao().getMessageById(messageId)
        assertNotNull(updatedMessage)
        assertEquals(SmsStatus.FAILED, updatedMessage?.status)
        assertEquals(3, updatedMessage?.retryCount) // Not incremented
        
        // Verify no retry work was scheduled
        val workInfos = workManager.getWorkInfosByTag("sms-retry").get()
        assertEquals(0, workInfos.size)
    }
    
    @Test
    fun `should handle periodic queue maintenance worker`() = runTest {
        // Given
        // Add old messages that should be cleaned up
        val oldMessage = createTestSmsMessage(
            phoneNumber = "+48123456789",
            message = "Old message",
            status = SmsStatus.SENT,
            createdAt = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30) - 1000
        )
        
        val messageId = database.smsDao().insert(oldMessage)
        
        // When - Schedule periodic maintenance worker
        val maintenanceRequest = workManagerService.scheduleMaintenanceWork()
        
        // Then - Verify work is scheduled
        val workInfo = workManager.getWorkInfoById(maintenanceRequest.id).get()
        assertEquals(WorkInfo.State.ENQUEUED, workInfo.state)
        assertTrue(workInfo.tags.contains("queue-maintenance"))
        
        // Simulate worker execution (in real scenario, this would be triggered by WorkManager)
        workManagerService.performMaintenance()
        
        // Verify old message was cleaned up
        val cleanedMessage = database.smsDao().getMessageById(messageId)
        assertNull(cleanedMessage)
    }
    
    @Test
    fun `should handle SMS cancellation in WorkManager`() = runTest {
        // Given
        val smsMessage = createTestSmsMessage(
            phoneNumber = "+48123456789",
            message = "SMS to be cancelled",
            scheduledAt = System.currentTimeMillis() + 30000 // 30 seconds from now
        )
        
        val messageId = database.smsDao().insert(smsMessage)
        
        // Schedule SMS
        val workRequest = workManagerService.scheduleSms(messageId, smsMessage.scheduledAt ?: 0)
        
        // When - Cancel SMS
        val cancelled = workManagerService.cancelSms(messageId)
        
        // Then - Verify cancellation
        assertTrue(cancelled)
        
        val workInfo = workManager.getWorkInfoById(workRequest.id).get()
        assertEquals(WorkInfo.State.CANCELLED, workInfo.state)
        
        // Verify message status is CANCELLED
        val updatedMessage = database.smsDao().getMessageById(messageId)
        assertNotNull(updatedMessage)
        assertEquals(SmsStatus.CANCELLED, updatedMessage?.status)
    }
    
    @Test
    fun `should handle concurrent SMS workers correctly`() = runTest {
        // Given
        val numMessages = 5
        val messageIds = mutableListOf<Long>()
        
        // Create multiple SMS messages
        repeat(numMessages) { i ->
            val smsMessage = createTestSmsMessage(
                phoneNumber = "+48123456789",
                message = "Concurrent SMS $i",
                status = SmsStatus.QUEUED
            )
            
            val messageId = database.smsDao().insert(smsMessage)
            messageIds.add(messageId)
        }
        
        // Mock successful SMS sending
        whenever(smsManagerWrapper.sendSms(any(), any())).thenReturn(true)
        
        // When - Schedule multiple workers concurrently
        val workRequests = messageIds.map { messageId ->
            val inputData = workDataOf("sms_id" to messageId)
            OneTimeWorkRequestBuilder<SmsSenderWorker>()
                .setInputData(inputData)
                .build()
        }
        
        workManager.enqueue(workRequests).result.get()
        
        // Wait for all workers to complete
        Thread.sleep(2000)
        
        // Then - Verify all messages were sent
        messageIds.forEach { messageId ->
            val updatedMessage = database.smsDao().getMessageById(messageId)
            assertNotNull(updatedMessage)
            assertEquals(SmsStatus.SENT, updatedMessage?.status)
        }
        
        // Verify SMS manager was called for each message
        verify(smsManagerWrapper, times(numMessages)).sendSms(any(), any())
    }
    
    @Test
    fun `should handle constraints correctly`() = runTest {
        // Given
        val smsMessage = createTestSmsMessage(
            phoneNumber = "+48123456789",
            message = "SMS with constraints",
            status = SmsStatus.QUEUED
        )
        
        val messageId = database.smsDao().insert(smsMessage)
        
        // Create work request with constraints
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        
        val inputData = workDataOf("sms_id" to messageId)
        
        val request = OneTimeWorkRequestBuilder<SmsSenderWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .build()
        
        // When - Schedule work with constraints
        workManager.enqueue(request).result.get()
        
        // Then - Verify work is scheduled but not immediately executed due to constraints
        val workInfo = workManager.getWorkInfoById(request.id).get()
        assertEquals(WorkInfo.State.ENQUEUED, workInfo.state)
        
        // In real scenario, work would execute when constraints are met
        // For testing, we can simulate constraint satisfaction
        testDriver.setAllConstraintsMet(request.id)
        
        // Wait for constraint check
        Thread.sleep(500)
        
        // Verify work is still enqueued (constraints would be checked by system)
        val workInfoAfterConstraints = workManager.getWorkInfoById(request.id).get()
        assertEquals(WorkInfo.State.ENQUEUED, workInfoAfterConstraints.state)
    }
    
    @Test
    fun `should handle work manager chaining correctly`() = runTest {
        // Given
        val smsMessage = createTestSmsMessage(
            phoneNumber = "+48123456789",
            message = "SMS with chained work",
            status = SmsStatus.QUEUED
        )
        
        val messageId = database.smsDao().insert(smsMessage)
        
        // Mock successful SMS sending
        whenever(smsManagerWrapper.sendSms(any(), any())).thenReturn(true)
        
        // When - Create work chain
        val smsWork = OneTimeWorkRequestBuilder<SmsSenderWorker>()
            .setInputData(workDataOf("sms_id" to messageId))
            .build()
        
        val notificationWork = OneTimeWorkRequestBuilder<TestNotificationWorker>()
            .build()
        
        val continuation = workManager.beginWith(smsWork)
            .then(notificationWork)
        continuation.enqueue()
        
        // Wait for chain to complete
        Thread.sleep(2000)
        
        // Then - Verify both works completed
        val smsWorkInfo = workManager.getWorkInfoById(smsWork.id).get()
        assertEquals(WorkInfo.State.SUCCEEDED, smsWorkInfo.state)
        
        val notificationWorkInfo = workManager.getWorkInfoById(notificationWork.id).get()
        assertEquals(WorkInfo.State.SUCCEEDED, notificationWorkInfo.state)
        
        // Verify SMS was sent
        val updatedMessage = database.smsDao().getMessageById(messageId)
        assertNotNull(updatedMessage)
        assertEquals(SmsStatus.SENT, updatedMessage?.status)
    }
    
    // Helper functions
    private fun createTestSmsMessage(
        phoneNumber: String = "+48123456789",
        message: String = "Test message",
        status: SmsStatus = SmsStatus.PENDING,
        scheduledAt: Long? = null,
        retryCount: Int = 0,
        maxRetries: Int = 3,
        createdAt: Long = System.currentTimeMillis()
    ): SmsMessage {
        return SmsMessage(
            id = 0,
            phoneNumber = phoneNumber,
            messageContent = message,
            status = status,
            priority = com.smsgateway.app.models.SmsPriority.NORMAL,
            createdAt = createdAt,
            scheduledAt = scheduledAt,
            sentAt = null,
            errorMessage = null,
            retryCount = retryCount,
            maxRetries = maxRetries,
            retryStrategy = com.smsgateway.app.models.RetryStrategy.EXPONENTIAL_BACKOFF,
            queuePosition = null
        )
    }
    
    // Test worker for notification work in chain
    class TestNotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            // Simulate notification work
            Thread.sleep(100)
            return Result.success()
        }
    }
}