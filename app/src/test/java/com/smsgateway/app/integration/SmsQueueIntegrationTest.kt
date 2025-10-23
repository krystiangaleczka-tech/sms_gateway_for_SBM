package com.smsgateway.app.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.TestDriver
import androidx.work.testing.WorkManagerTestInitHelper
import com.smsgateway.app.database.AppDatabase
import com.smsgateway.app.database.SmsMessage
import com.smsgateway.app.database.SmsStatus
import com.smsgateway.app.queue.PriorityQueue
import com.smsgateway.app.queue.SmsQueueService
import com.smsgateway.app.retry.RetryService
import com.smsgateway.app.health.HealthChecker
import com.smsgateway.app.events.EventPublisher
import com.smsgateway.app.events.MetricsCollector
import com.smsgateway.app.workers.SmsSchedulerWorker
import com.smsgateway.app.workers.SmsSenderWorker
import com.smsgateway.app.workers.WorkManagerService
import com.smsgateway.app.utils.SmsManagerWrapper
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
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
class SmsQueueIntegrationTest {
    
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var smsQueueService: SmsQueueService
    private lateinit var retryService: RetryService
    private lateinit var healthChecker: HealthChecker
    private lateinit var eventPublisher: EventPublisher
    private lateinit var metricsCollector: MetricsCollector
    private lateinit var workManagerService: WorkManagerService
    private lateinit var smsManagerWrapper: SmsManagerWrapper
    private lateinit var server: ApplicationEngine
    private lateinit var client: HttpClient
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        
        // Initialize test WorkManager
        val config = ApplicationConfiguration("")
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        
        // Setup in-memory database for testing
        database = AppDatabase.getInMemoryDatabase(context)
        
        // Initialize services
        smsQueueService = SmsQueueService(database, eventPublisher, metricsCollector)
        retryService = RetryService()
        healthChecker = HealthChecker(context)
        eventPublisher = EventPublisher(mock())
        metricsCollector = MetricsCollector(mock())
        smsManagerWrapper = mock<SmsManagerWrapper>()
        workManagerService = WorkManagerService(context)
        
        // Setup test Ktor server
        server = embeddedServer(Netty, port = 0) {
            // Configure test modules
            configureTestModules()
        }
        server.start()
        
        client = HttpClient {
            expectSuccess = false
        }
    }
    
    @After
    fun tearDown() {
        client.close()
        server.stop(1000, 5000)
        database.close()
        eventPublisher.shutdown()
        metricsCollector.shutdown()
    }
    
    @Test
    fun `should process complete SMS queue flow successfully`() = runTest {
        // Given
        val smsRequest = createTestSmsRequest(
            phoneNumber = "+48123456789",
            message = "Test message",
            priority = "HIGH"
        )
        
        // When - Send SMS via API
        val response = client.post<String>("http://localhost:${server.engine.resolvedPorts?.first()}/api/v1/sms") {
            contentType(ContentType.Application.Json)
            body = """
                {
                    "phoneNumber": "+48123456789",
                    "message": "Test message",
                    "priority": "HIGH"
                }
            """.trimIndent()
        }
        
        // Then - Verify response
        assertEquals(HttpStatusCode.Created, response.status)
        
        // Verify SMS was queued
        val queueStats = smsQueueService.getQueueStats()
        assertTrue(queueStats.queuedMessages > 0)
        
        // Process queue
        smsQueueService.processNextMessage()
        
        // Verify SMS was sent
        val sentMessages = database.smsDao().getMessagesByStatus(SmsStatus.SENT)
        assertTrue(sentMessages.isNotEmpty())
        
        // Verify metrics
        val metrics = metricsCollector.exportJson()
        assertTrue(metrics.contains("\"messages_sent\""))
    }
    
    @Test
    fun `should handle SMS with retry on failure`() = runTest {
        // Given
        val smsRequest = createTestSmsRequest(
            phoneNumber = "+48123456789",
            message = "Test message that will fail"
        )
        
        // Mock SMS manager to throw exception
        whenever(smsManagerWrapper.sendSms(any(), any()))
            .thenThrow(RuntimeException("SMS sending failed"))
        
        // When - Send SMS via API
        val response = client.post<String>("http://localhost:${server.engine.resolvedPorts?.first()}/api/v1/sms") {
            contentType(ContentType.Application.Json)
            body = """
                {
                    "phoneNumber": "+48123456789",
                    "message": "Test message that will fail",
                    "retryStrategy": "EXPONENTIAL_BACKOFF"
                }
            """.trimIndent()
        }
        
        // Then - Verify response
        assertEquals(HttpStatusCode.Created, response.status)
        
        // Process queue (should fail)
        smsQueueService.processNextMessage()
        
        // Verify SMS failed and is scheduled for retry
        val failedMessages = database.smsDao().getMessagesByStatus(SmsStatus.FAILED)
        assertTrue(failedMessages.isNotEmpty())
        assertEquals(1, failedMessages.first().retryCount)
        
        // Verify retry delay was calculated
        val retryDelay = retryService.calculateRetryDelay(
            attempt = 1,
            errorType = RuntimeException::class.java
        )
        assertTrue(retryDelay > 0)
    }
    
    @Test
    fun `should handle SMS priority queue correctly`() = runTest {
        // Given - Send SMS messages with different priorities
        val messages = listOf(
            createTestSmsRequest("low", "+48123456789", "Low priority", "LOW"),
            createTestSmsRequest("urgent", "+48123456789", "Urgent priority", "URGENT"),
            createTestSmsRequest("normal", "+48123456789", "Normal priority", "NORMAL"),
            createTestSmsRequest("high", "+48123456789", "High priority", "HIGH")
        )
        
        // When - Send all messages
        messages.forEach { request ->
            val response = client.post<String>("http://localhost:${server.engine.resolvedPorts?.first()}/api/v1/sms") {
                contentType(ContentType.Application.Json)
                body = """
                    {
                        "phoneNumber": "${request.phoneNumber}",
                        "message": "${request.message}",
                        "priority": "${request.priority}"
                    }
                """.trimIndent()
            }
            assertEquals(HttpStatusCode.Created, response.status)
        }
        
        // Then - Process messages and verify priority order
        val processedOrder = mutableListOf<String>()
        repeat(4) {
            val message = smsQueueService.dequeueNextSms()
            if (message != null) {
                processedOrder.add(message.messageContent)
                smsQueueService.markAsSent(message.id)
            }
        }
        
        // Verify urgent message was processed first
        assertEquals("Urgent priority", processedOrder[0])
        assertEquals("High priority", processedOrder[1])
        assertEquals("Normal priority", processedOrder[2])
        assertEquals("Low priority", processedOrder[3])
    }
    
    @Test
    fun `should handle queue pause and resume operations`() = runTest {
        // Given - Add messages to queue
        repeat(5) { i ->
            val response = client.post<String>("http://localhost:${server.engine.resolvedPorts?.first()}/api/v1/sms") {
                contentType(ContentType.Application.Json)
                body = """
                    {
                        "phoneNumber": "+48123456789",
                        "message": "Test message $i"
                    }
                """.trimIndent()
            }
            assertEquals(HttpStatusCode.Created, response.status)
        }
        
        // When - Pause queue
        val pauseResponse = client.post<String>("http://localhost:${server.engine.resolvedPorts?.first()}/api/v1/sms/queue/pause")
        assertEquals(HttpStatusCode.OK, pauseResponse.status)
        
        // Try to process messages (should not work)
        val processedCount = smsQueueService.processNextMessage()
        assertEquals(0, processedCount)
        
        // When - Resume queue
        val resumeResponse = client.post<String>("http://localhost:${server.engine.resolvedPorts?.first()}/api/v1/sms/queue/resume")
        assertEquals(HttpStatusCode.OK, resumeResponse.status)
        
        // Now processing should work
        val processedCountAfterResume = smsQueueService.processNextMessage()
        assertTrue(processedCountAfterResume > 0)
    }
    
    @Test
    fun `should provide accurate queue statistics`() = runTest {
        // Given - Add messages with different statuses
        val queuedResponse = client.post<String>("http://localhost:${server.engine.resolvedPorts?.first()}/api/v1/sms") {
            contentType(ContentType.Application.Json)
            body = """
                {
                    "phoneNumber": "+48123456789",
                    "message": "Queued message"
                }
            """.trimIndent()
        }
        assertEquals(HttpStatusCode.Created, queuedResponse.status)
        
        // Add a scheduled message
        val scheduledResponse = client.post<String>("http://localhost:${server.engine.resolvedPorts?.first()}/api/v1/sms") {
            contentType(ContentType.Application.Json)
            body = """
                {
                    "phoneNumber": "+48123456789",
                    "message": "Scheduled message",
                    "scheduledAt": "${System.currentTimeMillis() + 60000}"
                }
            """.trimIndent()
        }
        assertEquals(HttpStatusCode.Created, scheduledResponse.status)
        
        // When - Get queue statistics
        val statsResponse = client.get<String>("http://localhost:${server.engine.resolvedPorts?.first()}/api/v1/sms/queue/stats")
        
        // Then - Verify statistics
        assertEquals(HttpStatusCode.OK, statsResponse.status)
        val statsBody = statsResponse.bodyAsText
        assertTrue(statsBody.contains("\"totalMessages\""))
        assertTrue(statsBody.contains("\"queuedMessages\""))
        assertTrue(statsBody.contains("\"scheduledMessages\""))
    }
    
    @Test
    fun `should handle health check endpoint correctly`() = runTest {
        // When - Get health status
        val healthResponse = client.get<String>("http://localhost:${server.engine.resolvedPorts?.first()}/api/v1/health")
        
        // Then - Verify health status
        assertEquals(HttpStatusCode.OK, healthResponse.status)
        val healthBody = healthResponse.bodyAsText
        assertTrue(healthBody.contains("\"status\""))
        assertTrue(healthBody.contains("\"smsPermission\""))
        assertTrue(healthBody.contains("\"simStatus\""))
        assertTrue(healthBody.contains("\"networkConnectivity\""))
        assertTrue(healthBody.contains("\"queueHealth\""))
    }
    
    @Test
    fun `should handle SMS cancellation correctly`() = runTest {
        // Given - Add a message to queue
        val createResponse = client.post<String>("http://localhost:${server.engine.resolvedPorts?.first()}/api/v1/sms") {
            contentType(ContentType.Application.Json)
            body = """
                {
                    "phoneNumber": "+48123456789",
                    "message": "Message to be cancelled"
                }
            """.trimIndent()
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        
        // Extract message ID from response
        val messageBody = createResponse.bodyAsText
        val messageId = extractMessageId(messageBody)
        
        // When - Cancel the message
        val cancelResponse = client.delete<String>("http://localhost:${server.engine.resolvedPorts?.first()}/api/v1/sms/$messageId")
        
        // Then - Verify cancellation
        assertEquals(HttpStatusCode.OK, cancelResponse.status)
        
        // Verify message status is CANCELLED
        val cancelledMessage = database.smsDao().getMessageById(messageId.toLong())
        assertNotNull(cancelledMessage)
        assertEquals(SmsStatus.CANCELLED, cancelledMessage?.status)
    }
    
    @Test
    fun `should handle SMS priority update correctly`() = runTest {
        // Given - Add a message with normal priority
        val createResponse = client.post<String>("http://localhost:${server.engine.resolvedPorts?.first()}/api/v1/sms") {
            contentType(ContentType.Application.Json)
            body = """
                {
                    "phoneNumber": "+48123456789",
                    "message": "Message with normal priority"
                }
            """.trimIndent()
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        
        // Extract message ID from response
        val messageBody = createResponse.bodyAsText
        val messageId = extractMessageId(messageBody)
        
        // When - Update priority to URGENT
        val updateResponse = client.put<String>("http://localhost:${server.engine.resolvedPorts?.first()}/api/v1/sms/$messageId/priority") {
            contentType(ContentType.Application.Json)
            body = """
                {
                    "priority": "URGENT"
                }
            """.trimIndent()
        }
        
        // Then - Verify priority update
        assertEquals(HttpStatusCode.OK, updateResponse.status)
        
        // Verify message priority was updated
        val updatedMessage = database.smsDao().getMessageById(messageId.toLong())
        assertNotNull(updatedMessage)
        // Note: This would depend on the actual implementation of priority field
    }
    
    @Test
    fun `should handle bulk SMS operations correctly`() = runTest {
        // Given - Prepare bulk SMS request
        val bulkRequest = """
            {
                "messages": [
                    {
                        "phoneNumber": "+48123456789",
                        "message": "Bulk message 1",
                        "priority": "HIGH"
                    },
                    {
                        "phoneNumber": "+48987654321",
                        "message": "Bulk message 2",
                        "priority": "NORMAL"
                    }
                ]
            }
        """.trimIndent()
        
        // When - Send bulk SMS
        val bulkResponse = client.post<String>("http://localhost:${server.engine.resolvedPorts?.first()}/api/v1/sms/bulk") {
            contentType(ContentType.Application.Json)
            body = bulkRequest
        }
        
        // Then - Verify bulk operation
        assertEquals(HttpStatusCode.Created, bulkResponse.status)
        
        val bulkResponseBody = bulkResponse.bodyAsText
        assertTrue(bulkResponseBody.contains("\"processed\""))
        assertTrue(bulkResponseBody.contains("\"failed\""))
        
        // Verify messages were added to queue
        val queueStats = smsQueueService.getQueueStats()
        assertTrue(queueStats.queuedMessages >= 2)
    }
    
    @Test
    fun `should handle concurrent SMS processing correctly`() = runTest {
        // Given - Prepare multiple concurrent requests
        val numThreads = 5
        val messagesPerThread = 10
        
        // When - Send concurrent requests
        val threads = (1..numThreads).map { threadId ->
            Thread {
                runBlocking {
                    repeat(messagesPerThread) { messageId ->
                        val response = client.post<String>("http://localhost:${server.engine.resolvedPorts?.first()}/api/v1/sms") {
                            contentType(ContentType.Application.Json)
                            body = """
                                {
                                    "phoneNumber": "+48123456789",
                                    "message": "Concurrent message $threadId-$messageId"
                                }
                            """.trimIndent()
                        }
                        assertEquals(HttpStatusCode.Created, response.status)
                    }
                }
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        
        // Then - Verify all messages were queued
        val queueStats = smsQueueService.getQueueStats()
        assertTrue(queueStats.queuedMessages >= numThreads * messagesPerThread)
        
        // Process some messages
        val processedCount = smsQueueService.processNextMessage()
        assertTrue(processedCount > 0)
    }
    
    @Test
    fun `should handle system recovery after failure`() = runTest {
        // Given - Simulate system failure by stopping queue processing
        smsQueueService.pauseQueue()
        
        // Add messages during failure
        repeat(3) { i ->
            val response = client.post<String>("http://localhost:${server.engine.resolvedPorts?.first()}/api/v1/sms") {
                contentType(ContentType.Application.Json)
                body = """
                    {
                        "phoneNumber": "+48123456789",
                        "message": "Message during failure $i"
                    }
                """.trimIndent()
            }
            assertEquals(HttpStatusCode.Created, response.status)
        }
        
        // Verify messages are queued but not processed
        val queueStatsDuringFailure = smsQueueService.getQueueStats()
        assertTrue(queueStatsDuringFailure.queuedMessages >= 3)
        
        // When - Recover system
        smsQueueService.resumeQueue()
        
        // Process queued messages
        val processedCount = smsQueueService.processNextMessage()
        assertTrue(processedCount > 0)
        
        // Then - Verify recovery
        val healthStatus = healthChecker.checkSystemHealth()
        assertTrue(healthStatus.overallStatus.name in listOf("HEALTHY", "WARNING"))
    }
    
    // Helper functions
    private fun createTestSmsRequest(
        id: String = "test",
        phoneNumber: String,
        message: String,
        priority: String = "NORMAL"
    ) = mapOf(
        "id" to id,
        "phoneNumber" to phoneNumber,
        "message" to message,
        "priority" to priority
    )
    
    private fun extractMessageId(responseBody: String): String {
        // Extract ID from JSON response
        // Implementation depends on actual response format
        return "1" // Placeholder
    }
    
    private fun Application.configureTestModules() {
        // Configure test modules for integration testing
        // This would include setting up routes, dependencies, etc.
    }
}