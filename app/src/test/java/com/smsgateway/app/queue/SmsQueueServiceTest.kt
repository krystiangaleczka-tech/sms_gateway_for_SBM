package com.smsgateway.app.queue

import com.smsgateway.app.database.SmsRepository
import com.smsgateway.app.retry.RetryService
import com.smsgateway.app.health.HealthChecker
import com.smsgateway.app.events.MetricsCollector
import com.smsgateway.app.database.SmsMessage
import com.smsgateway.app.database.SmsStatus
import com.smsgateway.app.database.SmsPriority
import com.smsgateway.app.database.RetryStrategy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.mockito.kotlin.*
import java.util.UUID

class SmsQueueServiceTest {
    
    private lateinit var smsQueueService: SmsQueueService
    private lateinit var mockRepository: SmsRepository
    private lateinit var mockRetryService: RetryService
    private lateinit var mockMetricsCollector: MetricsCollector
    private lateinit var mockHealthChecker: HealthChecker
    
    @BeforeEach
    fun setUp() {
        mockRepository = mock()
        mockRetryService = mock()
        mockMetricsCollector = mock()
        mockHealthChecker = mock()
        
        smsQueueService = SmsQueueService(
            mockRepository,
            mockRetryService,
            mockMetricsCollector,
            mockHealthChecker
        )
    }
    
    @AfterEach
    fun tearDown() {
        // Cleanup if needed
    }
    
    @Test
    fun `should enqueue SMS with priority`() = runTest {
        // Given
        val smsRequest = createTestSmsRequest()
        val expectedSmsMessage = createTestSmsMessage(
            id = 1L,
            priority = SmsPriority.HIGH,
            status = SmsStatus.QUEUED
        )
        
        whenever(mockRepository.save(any())).thenReturn(expectedSmsMessage)
        
        // When
        val result = smsQueueService.enqueueSms(smsRequest, SmsPriority.HIGH)
        
        // Then
        assertNotNull(result)
        assertEquals(SmsPriority.HIGH, result.priority)
        assertEquals(SmsStatus.QUEUED, result.status)
        assertEquals(smsRequest.phoneNumber, result.phoneNumber)
        assertEquals(smsRequest.messageContent, result.messageContent)
        
        verify(mockRepository).save(any())
        verify(mockMetricsCollector).recordEvent(eq("sms.enqueued"), any())
    }
    
    @Test
    fun `should dequeue next SMS by priority`() = runTest {
        // Given
        val highPrioritySms = createTestSmsMessage(
            id = 1L,
            priority = SmsPriority.HIGH,
            status = SmsStatus.QUEUED
        )
        val normalPrioritySms = createTestSmsMessage(
            id = 2L,
            priority = SmsPriority.NORMAL,
            status = SmsStatus.QUEUED
        )
        
        val queuedMessages = listOf(normalPrioritySms, highPrioritySms)
        whenever(mockRepository.findByStatus(SmsStatus.QUEUED)).thenReturn(queuedMessages)
        
        // When
        val result = smsQueueService.dequeueNextSms()
        
        // Then
        assertNotNull(result)
        assertEquals(SmsPriority.HIGH, result!!.priority) // High priority should be first
        assertEquals(1L, result.id)
        
        verify(mockRepository).findByStatus(SmsStatus.QUEUED)
        verify(mockRepository).updateStatus(1L, SmsStatus.SENDING)
        verify(mockMetricsCollector).recordEvent(eq("sms.dequeued"), any())
    }
    
    @Test
    fun `should return null when no SMS in queue`() = runTest {
        // Given
        whenever(mockRepository.findByStatus(SmsStatus.QUEUED)).thenReturn(emptyList())
        
        // When
        val result = smsQueueService.dequeueNextSms()
        
        // Then
        assertNull(result)
        verify(mockRepository).findByStatus(SmsStatus.QUEUED)
        verify(mockMetricsCollector, never()).recordEvent(any(), any())
    }
    
    @Test
    fun `should get queue statistics`() = runTest {
        // Given
        val queuedMessages = listOf(
            createTestSmsMessage(id = 1L, status = SmsStatus.QUEUED),
            createTestSmsMessage(id = 2L, status = SmsStatus.QUEUED),
            createTestSmsMessage(id = 3L, status = SmsStatus.QUEUED)
        )
        val scheduledMessages = listOf(
            createTestSmsMessage(id = 4L, status = SmsStatus.SCHEDULED),
            createTestSmsMessage(id = 5L, status = SmsStatus.SCHEDULED)
        )
        val sendingMessages = listOf(
            createTestSmsMessage(id = 6L, status = SmsStatus.SENDING)
        )
        val sentMessages = listOf(
            createTestSmsMessage(id = 7L, status = SmsStatus.SENT),
            createTestSmsMessage(id = 8L, status = SmsStatus.SENT)
        )
        val failedMessages = listOf(
            createTestSmsMessage(id = 9L, status = SmsStatus.FAILED)
        )
        
        whenever(mockRepository.findByStatus(SmsStatus.QUEUED)).thenReturn(queuedMessages)
        whenever(mockRepository.findByStatus(SmsStatus.SCHEDULED)).thenReturn(scheduledMessages)
        whenever(mockRepository.findByStatus(SmsStatus.SENDING)).thenReturn(sendingMessages)
        whenever(mockRepository.findByStatus(SmsStatus.SENT)).thenReturn(sentMessages)
        whenever(mockRepository.findByStatus(SmsStatus.FAILED)).thenReturn(failedMessages)
        
        // When
        val stats = smsQueueService.getQueueStats()
        
        // Then
        assertEquals(3, stats.queuedMessages)
        assertEquals(2, stats.scheduledMessages)
        assertEquals(1, stats.sendingMessages)
        assertEquals(2, stats.sentMessages)
        assertEquals(1, stats.failedMessages)
        assertEquals(9, stats.totalMessages)
        
        verify(mockRepository).findByStatus(SmsStatus.QUEUED)
        verify(mockRepository).findByStatus(SmsStatus.SCHEDULED)
        verify(mockRepository).findByStatus(SmsStatus.SENDING)
        verify(mockRepository).findByStatus(SmsStatus.SENT)
        verify(mockRepository).findByStatus(SmsStatus.FAILED)
    }
    
    @Test
    fun `should pause queue processing`() = runTest {
        // When
        smsQueueService.pauseQueue()
        
        // Then
        assertTrue(smsQueueService.isQueuePaused())
        verify(mockMetricsCollector).recordEvent(eq("queue.paused"), any())
    }
    
    @Test
    fun `should resume queue processing`() = runTest {
        // Given
        smsQueueService.pauseQueue()
        
        // When
        smsQueueService.resumeQueue()
        
        // Then
        assertFalse(smsQueueService.isQueuePaused())
        verify(mockMetricsCollector).recordEvent(eq("queue.resumed"), any())
    }
    
    @Test
    fun `should not dequeue when queue is paused`() = runTest {
        // Given
        val queuedMessage = createTestSmsMessage(
            id = 1L,
            priority = SmsPriority.HIGH,
            status = SmsStatus.QUEUED
        )
        
        whenever(mockRepository.findByStatus(SmsStatus.QUEUED)).thenReturn(listOf(queuedMessage))
        smsQueueService.pauseQueue()
        
        // When
        val result = smsQueueService.dequeueNextSms()
        
        // Then
        assertNull(result) // Should return null when paused
        verify(mockRepository).findByStatus(SmsStatus.QUEUED)
        verify(mockRepository, never()).updateStatus(any(), any())
        verify(mockMetricsCollector, never()).recordEvent(eq("sms.dequeued"), any())
    }
    
    @Test
    fun `should mark SMS as sent`() = runTest {
        // Given
        val smsId = 1L
        
        // When
        smsQueueService.markSmsAsSent(smsId)
        
        // Then
        verify(mockRepository).updateStatus(smsId, SmsStatus.SENT)
        verify(mockMetricsCollector).recordEvent(eq("sms.sent"), any())
    }
    
    @Test
    fun `should mark SMS as failed with retry`() = runTest {
        // Given
        val smsId = 1L
        val errorMessage = "Network error"
        val maxRetries = 3
        val retryDelay = 5000L
        
        whenever(mockRetryService.calculateRetryDelay(1, "Network error")).thenReturn(retryDelay)
        whenever(mockRetryService.shouldRetry(1, "Network error")).thenReturn(true)
        
        // When
        smsQueueService.markSmsAsFailed(smsId, errorMessage, maxRetries)
        
        // Then
        verify(mockRepository).markAsFailed(smsId, errorMessage)
        verify(mockRetryService).calculateRetryDelay(1, errorMessage)
        verify(mockRetryService).shouldRetry(1, errorMessage)
        verify(mockRepository).scheduleRetry(smsId, retryDelay)
        verify(mockMetricsCollector).recordEvent(eq("sms.failed"), any())
    }
    
    @Test
    fun `should not retry when max retries exceeded`() = runTest {
        // Given
        val smsId = 1L
        val errorMessage = "Network error"
        val maxRetries = 3
        
        val failedSms = createTestSmsMessage(
            id = smsId,
            status = SmsStatus.FAILED,
            retryCount = 3
        )
        
        whenever(mockRepository.findById(smsId)).thenReturn(failedSms)
        whenever(mockRetryService.shouldRetry(3, errorMessage)).thenReturn(false)
        
        // When
        smsQueueService.markSmsAsFailed(smsId, errorMessage, maxRetries)
        
        // Then
        verify(mockRepository).markAsFailed(smsId, errorMessage)
        verify(mockRepository, never()).scheduleRetry(any(), any())
        verify(mockMetricsCollector).recordEvent(eq("sms.failed.permanent"), any())
    }
    
    @Test
    fun `should get SMS by ID`() = runTest {
        // Given
        val smsId = 1L
        val expectedSms = createTestSmsMessage(id = smsId)
        
        whenever(mockRepository.findById(smsId)).thenReturn(expectedSms)
        
        // When
        val result = smsQueueService.getSmsById(smsId)
        
        // Then
        assertNotNull(result)
        assertEquals(expectedSms, result)
        verify(mockRepository).findById(smsId)
    }
    
    @Test
    fun `should return null when SMS not found`() = runTest {
        // Given
        val smsId = 999L
        
        whenever(mockRepository.findById(smsId)).thenReturn(null)
        
        // When
        val result = smsQueueService.getSmsById(smsId)
        
        // Then
        assertNull(result)
        verify(mockRepository).findById(smsId)
    }
    
    @Test
    fun `should get SMS by phone number`() = runTest {
        // Given
        val phoneNumber = "+48123456789"
        val expectedMessages = listOf(
            createTestSmsMessage(id = 1L, phoneNumber = phoneNumber),
            createTestSmsMessage(id = 2L, phoneNumber = phoneNumber)
        )
        
        whenever(mockRepository.findByPhoneNumber(phoneNumber)).thenReturn(expectedMessages)
        
        // When
        val result = smsQueueService.getSmsByPhoneNumber(phoneNumber)
        
        // Then
        assertEquals(2, result.size)
        assertEquals(expectedMessages, result)
        verify(mockRepository).findByPhoneNumber(phoneNumber)
    }
    
    @Test
    fun `should cancel SMS`() = runTest {
        // Given
        val smsId = 1L
        
        // When
        smsQueueService.cancelSms(smsId)
        
        // Then
        verify(mockRepository).updateStatus(smsId, SmsStatus.CANCELED)
        verify(mockMetricsCollector).recordEvent(eq("sms.canceled"), any())
    }
    
    @Test
    fun `should update SMS priority`() = runTest {
        // Given
        val smsId = 1L
        val newPriority = SmsPriority.URGENT
        
        // When
        smsQueueService.updateSmsPriority(smsId, newPriority)
        
        // Then
        verify(mockRepository).updatePriority(smsId, newPriority)
        verify(mockMetricsCollector).recordEvent(eq("sms.priority.updated"), any())
    }
    
    @Test
    fun `should get retry count for SMS`() = runTest {
        // Given
        val smsId = 1L
        val expectedSms = createTestSmsMessage(
            id = smsId,
            retryCount = 2
        )
        
        whenever(mockRepository.findById(smsId)).thenReturn(expectedSms)
        
        // When
        val result = smsQueueService.getRetryCount(smsId)
        
        // Then
        assertEquals(2, result)
        verify(mockRepository).findById(smsId)
    }
    
    @Test
    fun `should return 0 retry count for non-existent SMS`() = runTest {
        // Given
        val smsId = 999L
        
        whenever(mockRepository.findById(smsId)).thenReturn(null)
        
        // When
        val result = smsQueueService.getRetryCount(smsId)
        
        // Then
        assertEquals(0, result)
        verify(mockRepository).findById(smsId)
    }
    
    // Helper methods
    private fun createTestSmsRequest() = com.smsgateway.app.models.dto.SmsRequest(
        phoneNumber = "+48123456789",
        messageContent = "Test message",
        scheduledTime = null,
        priority = SmsPriority.NORMAL,
        retryStrategy = RetryStrategy.EXPONENTIAL_BACKOFF,
        metadata = mapOf("test" to "true")
    )
    
    private fun createTestSmsMessage(
        id: Long,
        phoneNumber: String = "+48123456789",
        messageContent: String = "Test message",
        status: SmsStatus = SmsStatus.PENDING,
        priority: SmsPriority = SmsPriority.NORMAL,
        retryCount: Int = 0,
        maxRetries: Int = 3,
        retryStrategy: RetryStrategy = RetryStrategy.EXPONENTIAL_BACKOFF
    ) = SmsMessage(
        id = id,
        phoneNumber = phoneNumber,
        messageContent = messageContent,
        status = status,
        priority = priority,
        createdAt = System.currentTimeMillis(),
        scheduledAt = null,
        sentAt = null,
        errorMessage = null,
        retryCount = retryCount,
        maxRetries = maxRetries,
        retryStrategy = retryStrategy,
        queuePosition = null
    )
}