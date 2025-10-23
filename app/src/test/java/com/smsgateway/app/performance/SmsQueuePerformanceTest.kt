package com.smsgateway.app.performance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smsgateway.app.database.AppDatabase
import com.smsgateway.app.database.SmsMessage
import com.smsgateway.app.database.SmsStatus
import com.smsgateway.app.queue.PriorityQueue
import com.smsgateway.app.queue.SmsQueueService
import com.smsgateway.app.retry.RetryService
import com.smsgateway.app.events.EventPublisher
import com.smsgateway.app.events.MetricsCollector
import com.smsgateway.app.models.SmsPriority
import com.smsgateway.app.models.RetryStrategy
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
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class SmsQueuePerformanceTest {
    
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var smsQueueService: SmsQueueService
    private lateinit var priorityQueue: PriorityQueue
    private lateinit var retryService: RetryService
    private lateinit var eventPublisher: EventPublisher
    private lateinit var metricsCollector: MetricsCollector
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        
        // Setup in-memory database for testing
        database = AppDatabase.getInMemoryDatabase(context)
        
        // Initialize services
        priorityQueue = PriorityQueue(database)
        eventPublisher = mock()
        metricsCollector = mock()
        smsQueueService = SmsQueueService(database, eventPublisher, metricsCollector)
        retryService = RetryService()
    }
    
    @After
    fun tearDown() {
        database.close()
        eventPublisher.shutdown()
        metricsCollector.shutdown()
    }
    
    @Test
    fun `should handle large number of SMS in queue efficiently`() = runTest {
        // Given
        val numMessages = 1000
        val messages = mutableListOf<SmsMessage>()
        
        // Create test messages
        repeat(numMessages) { i ->
            val message = createTestSmsMessage(
                phoneNumber = "+48123456789",
                message = "Performance test message $i",
                priority = when (i % 4) {
                    0 -> SmsPriority.URGENT
                    1 -> SmsPriority.HIGH
                    2 -> SmsPriority.NORMAL
                    else -> SmsPriority.LOW
                }
            )
            messages.add(message)
        }
        
        // When - Measure insertion time
        val insertionTime = measureTimeMillis {
            messages.forEach { message ->
                val messageId = database.smsDao().insert(message)
                smsQueueService.enqueueSms(messageId, message.priority)
            }
        }
        
        // Then - Verify performance
        assertTrue("Insertion should take less than 5 seconds", insertionTime < 5000)
        
        val queueStats = smsQueueService.getQueueStats()
        assertEquals(numMessages, queueStats.totalMessages)
        assertEquals(numMessages, queueStats.queuedMessages)
        
        // Measure processing time
        val processingTime = measureTimeMillis {
            var processedCount = 0
            while (processedCount < numMessages) {
                val processed = smsQueueService.processNextMessage()
                processedCount += processed
            }
        }
        
        assertTrue("Processing should take less than 10 seconds", processingTime < 10000)
        assertTrue("Should process all messages", processedCount == numMessages)
    }
    
    @Test
    fun `should handle concurrent queue operations efficiently`() = runTest {
        // Given
        val numThreads = 10
        val messagesPerThread = 100
        val totalMessages = numThreads * messagesPerThread
        
        // When - Perform concurrent operations
        val totalTime = measureTimeMillis {
            val threads = (1..numThreads).map { threadId ->
                Thread {
                    runBlocking {
                        repeat(messagesPerThread) { messageId ->
                            val message = createTestSmsMessage(
                                phoneNumber = "+48123456789",
                                message = "Concurrent test $threadId-$messageId",
                                priority = SmsPriority.NORMAL
                            )
                            
                            val dbId = database.smsDao().insert(message)
                            smsQueueService.enqueueSms(dbId, message.priority)
                        }
                    }
                }
            }
            
            threads.forEach { it.start() }
            threads.forEach { it.join() }
            
            // Process all messages
            var processedCount = 0
            while (processedCount < totalMessages) {
                val processed = smsQueueService.processNextMessage()
                processedCount += processed
            }
        }
        
        // Then - Verify performance
        assertTrue("Concurrent operations should take less than 15 seconds", totalTime < 15000)
        
        val queueStats = smsQueueService.getQueueStats()
        assertEquals(totalMessages, queueStats.totalMessages)
        assertEquals(totalMessages, queueStats.sentMessages)
    }
    
    @Test
    fun `should handle priority queue operations efficiently`() = runTest {
        // Given
        val numMessages = 10000
        val priorityDistribution = mapOf(
            SmsPriority.URGENT to 1000,
            SmsPriority.HIGH to 2000,
            SmsPriority.NORMAL to 5000,
            SmsPriority.LOW to 2000
        )
        
        val messages = mutableListOf<SmsMessage>()
        
        // Create messages with different priorities
        priorityDistribution.forEach { (priority, count) ->
            repeat(count) { i ->
                val message = createTestSmsMessage(
                    phoneNumber = "+48123456789",
                    message = "Priority test $priority-$i",
                    priority = priority
                )
                messages.add(message)
            }
        }
        
        // Shuffle messages to simulate random arrival
        messages.shuffle()
        
        // When - Measure queue operations
        val enqueueTime = measureTimeMillis {
            messages.forEach { message ->
                val messageId = database.smsDao().insert(message)
                priorityQueue.add(messageId, message.priority)
            }
        }
        
        // Then - Verify enqueue performance
        assertTrue("Enqueue should take less than 3 seconds", enqueueTime < 3000)
        assertEquals(numMessages, priorityQueue.size())
        
        // Measure dequeue performance
        val dequeueTime = measureTimeMillis {
            val processedOrder = mutableListOf<SmsPriority>()
            repeat(numMessages) {
                val messageId = priorityQueue.poll()
                if (messageId != null) {
                    val message = database.smsDao().getMessageById(messageId)
                    if (message != null) {
                        processedOrder.add(message.priority)
                    }
                }
            }
            
            // Verify priority order
            for (i in 1 until processedOrder.size) {
                assertTrue(
                    "Messages should be in priority order",
                    processedOrder[i-1].value >= processedOrder[i].value
                )
            }
        }
        
        // Verify dequeue performance
        assertTrue("Dequeue should take less than 5 seconds", dequeueTime < 5000)
        assertEquals(0, priorityQueue.size())
    }
    
    @Test
    fun `should handle retry operations efficiently`() = runTest {
        // Given
        val numMessages = 1000
        val messages = mutableListOf<SmsMessage>()
        
        // Create messages that will fail and need retry
        repeat(numMessages) { i ->
            val message = createTestSmsMessage(
                phoneNumber = "+48123456789",
                message = "Retry test message $i",
                status = SmsStatus.FAILED,
                retryCount = 0,
                maxRetries = 3,
                retryStrategy = RetryStrategy.EXPONENTIAL_BACKOFF
            )
            messages.add(message)
        }
        
        // Insert messages
        messages.forEach { message ->
            database.smsDao().insert(message)
        }
        
        // When - Measure retry operations
        val retryTime = measureTimeMillis {
            messages.forEach { message ->
                val retryDelay = retryService.calculateRetryDelay(
                    attempt = message.retryCount,
                    errorType = RuntimeException::class.java
                )
                
                // Verify retry delay is reasonable
                assertTrue("Retry delay should be reasonable", retryDelay > 0)
                assertTrue("Retry delay should not be too long", retryDelay < 300000) // 5 minutes max
                
                // Simulate retry logic
                if (message.retryCount < message.maxRetries) {
                    database.smsDao().updateRetryCount(message.id, message.retryCount + 1)
                    database.smsDao().updateStatus(message.id, SmsStatus.QUEUED)
                }
            }
        }
        
        // Then - Verify retry performance
        assertTrue("Retry operations should take less than 2 seconds", retryTime < 2000)
    }
    
    @Test
    fun `should handle database operations efficiently`() = runTest {
        // Given
        val numMessages = 5000
        val batchSize = 100
        
        // When - Measure batch database operations
        val batchInsertTime = measureTimeMillis {
            val messages = mutableListOf<SmsMessage>()
            
            repeat(batchSize) { i ->
                val message = createTestSmsMessage(
                    phoneNumber = "+48123456789",
                    message = "Batch test message $i",
                    priority = SmsPriority.NORMAL
                )
                messages.add(message)
            }
            
            // Insert in batches
            repeat(numMessages / batchSize) { batchIndex ->
                messages.forEach { message ->
                    database.smsDao().insert(message.copy(
                        messageContent = "Batch test message ${batchIndex * batchSize + message.id}"
                    ))
                }
            }
        }
        
        // Then - Verify batch insert performance
        assertTrue("Batch insert should take less than 10 seconds", batchInsertTime < 10000)
        
        // Measure query performance
        val queryTime = measureTimeMillis {
            val allMessages = database.smsDao().getAllMessages()
            assertEquals(numMessages, allMessages.size)
            
            val queuedMessages = database.smsDao().getMessagesByStatus(SmsStatus.PENDING)
            assertEquals(numMessages, queuedMessages.size)
            
            // Test pagination
            val pageSize = 100
            repeat(numMessages / pageSize) { pageIndex ->
                val page = database.smsDao().getMessagesPaginated(pageIndex * pageSize, pageSize)
                assertEquals(pageSize, page.size)
            }
        }
        
        // Verify query performance
        assertTrue("Query operations should take less than 5 seconds", queryTime < 5000)
        
        // Measure update performance
        val updateTime = measureTimeMillis {
            repeat(numMessages) { i ->
                database.smsDao().updateStatus((i + 1).toLong(), SmsStatus.SENT)
            }
        }
        
        // Verify update performance
        assertTrue("Update operations should take less than 8 seconds", updateTime < 8000)
    }
    
    @Test
    fun `should handle memory usage efficiently`() = runTest {
        // Given
        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()
        
        val numMessages = 10000
        val messages = mutableListOf<SmsMessage>()
        
        // Create messages
        repeat(numMessages) { i ->
            val message = createTestSmsMessage(
                phoneNumber = "+48123456789",
                message = "Memory test message $i with some additional content to increase memory usage",
                priority = SmsPriority.NORMAL
            )
            messages.add(message)
        }
        
        // When - Process messages
        val memoryAfterCreation = runtime.totalMemory() - runtime.freeMemory()
        
        // Process messages
        messages.forEach { message ->
            val messageId = database.smsDao().insert(message)
            smsQueueService.enqueueSms(messageId, message.priority)
        }
        
        val memoryAfterEnqueue = runtime.totalMemory() - runtime.freeMemory()
        
        // Process all messages
        var processedCount = 0
        while (processedCount < numMessages) {
            val processed = smsQueueService.processNextMessage()
            processedCount += processed
        }
        
        val memoryAfterProcessing = runtime.totalMemory() - runtime.freeMemory()
        
        // Clear references
        messages.clear()
        System.gc()
        Thread.sleep(100)
        
        val memoryAfterGc = runtime.totalMemory() - runtime.freeMemory()
        
        // Then - Verify memory usage
        val memoryIncrease = memoryAfterEnqueue - initialMemory
        val memoryPerMessage = memoryIncrease / numMessages
        
        assertTrue("Memory per message should be reasonable (< 1KB)", memoryPerMessage < 1024)
        
        // Memory should be released after processing
        assertTrue("Memory should be released after processing", memoryAfterGc < memoryAfterProcessing)
    }
    
    @Test
    fun `should handle metrics collection efficiently`() = runTest {
        // Given
        val numOperations = 10000
        
        // When - Measure metrics collection overhead
        val metricsTime = measureTimeMillis {
            repeat(numOperations) { i ->
                metricsCollector.incrementCounter("messages_processed")
                metricsCollector.recordGauge("queue_size", i.toLong())
                metricsCollector.recordTimer("processing_time", i.toLong())
                metricsCollector.recordHistogram("message_size", i.toLong())
                
                if (i % 1000 == 0) {
                    metricsCollector.exportJson()
                    metricsCollector.exportPrometheus()
                }
            }
        }
        
        // Then - Verify metrics performance
        assertTrue("Metrics collection should take less than 3 seconds", metricsTime < 3000)
        
        // Verify metrics were recorded
        val jsonExport = metricsCollector.exportJson()
        assertTrue("JSON export should contain metrics", jsonExport.contains("messages_processed"))
        assertTrue("JSON export should contain metrics", jsonExport.contains("queue_size"))
        
        val prometheusExport = metricsCollector.exportPrometheus()
        assertTrue("Prometheus export should contain metrics", prometheusExport.contains("messages_processed"))
        assertTrue("Prometheus export should contain metrics", prometheusExport.contains("queue_size"))
    }
    
    @Test
    fun `should handle event publishing efficiently`() = runTest {
        // Given
        val numEvents = 5000
        val eventsReceived = mutableListOf<String>()
        
        // Register event handler
        eventPublisher.subscribe("sms_sent") { event ->
            synchronized(eventsReceived) {
                eventsReceived.add(event.toString())
            }
        }
        
        // When - Measure event publishing overhead
        val publishTime = measureTimeMillis {
            repeat(numEvents) { i ->
                eventPublisher.publish("sms_sent", mapOf(
                    "messageId" to i,
                    "phoneNumber" to "+48123456789",
                    "timestamp" to System.currentTimeMillis()
                ))
            }
        }
        
        // Wait for all events to be processed
        Thread.sleep(1000)
        
        // Then - Verify event performance
        assertTrue("Event publishing should take less than 2 seconds", publishTime < 2000)
        assertEquals(numEvents, eventsReceived.size)
        
        // Verify event statistics
        val stats = eventPublisher.getStatistics()
        assertEquals(numEvents, stats.totalPublished)
        assertEquals(numEvents, stats.totalDelivered)
        assertEquals(0, stats.totalFailed)
    }
    
    @Test
    fun `should handle large message content efficiently`() = runTest {
        // Given
        val baseMessage = "This is a large message for performance testing. ".repeat(10)
        val numMessages = 1000
        val messages = mutableListOf<SmsMessage>()
        
        // Create messages with large content
        repeat(numMessages) { i ->
            val message = createTestSmsMessage(
                phoneNumber = "+48123456789",
                message = "$baseMessage Message ID: $i",
                priority = SmsPriority.NORMAL
            )
            messages.add(message)
        }
        
        // When - Measure processing time for large messages
        val processingTime = measureTimeMillis {
            messages.forEach { message ->
                val messageId = database.smsDao().insert(message)
                smsQueueService.enqueueSms(messageId, message.priority)
            }
            
            // Process all messages
            var processedCount = 0
            while (processedCount < numMessages) {
                val processed = smsQueueService.processNextMessage()
                processedCount += processed
            }
        }
        
        // Then - Verify large message performance
        assertTrue("Large message processing should take less than 15 seconds", processingTime < 15000)
        assertEquals(numMessages, smsQueueService.getQueueStats().totalMessages)
        assertEquals(numMessages, smsQueueService.getQueueStats().sentMessages)
    }
    
    @Test
    fun `should handle stress test with mixed operations`() = runTest {
        // Given
        val duration = 10000L // 10 seconds
        val numThreads = 5
        val operationsPerThread = 100
        
        val startTime = System.currentTimeMillis()
        val operationsCompleted = mutableListOf<Int>()
        
        // When - Perform mixed operations under stress
        val threads = (1..numThreads).map { threadId ->
            Thread {
                var operations = 0
                while (System.currentTimeMillis() - startTime < duration && operations < operationsPerThread) {
                    try {
                        when (operations % 4) {
                            0 -> {
                                // Enqueue operation
                                val message = createTestSmsMessage(
                                    phoneNumber = "+48123456789",
                                    message = "Stress test $threadId-$operations",
                                    priority = SmsPriority.NORMAL
                                )
                                val messageId = database.smsDao().insert(message)
                                smsQueueService.enqueueSms(messageId, message.priority)
                            }
                            1 -> {
                                // Process operation
                                smsQueueService.processNextMessage()
                            }
                            2 -> {
                                // Stats operation
                                smsQueueService.getQueueStats()
                            }
                            else -> {
                                // Metrics operation
                                metricsCollector.incrementCounter("stress_test_operations")
                            }
                        }
                        operations++
                    } catch (e: Exception) {
                        // Log exception but continue
                        println("Exception in stress test: ${e.message}")
                    }
                }
                synchronized(operationsCompleted) {
                    operationsCompleted.add(operations)
                }
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        
        // Then - Verify stress test results
        val totalOperations = operationsCompleted.sum()
        assertTrue("Should complete significant number of operations", totalOperations > 100)
        
        val actualDuration = System.currentTimeMillis() - startTime
        val operationsPerSecond = totalOperations * 1000 / actualDuration
        
        assertTrue("Should maintain reasonable throughput (> 10 ops/sec)", operationsPerSecond > 10)
        
        // Verify system is still responsive
        val finalStats = smsQueueService.getQueueStats()
        assertNotNull(finalStats)
    }
    
    // Helper functions
    private fun createTestSmsMessage(
        phoneNumber: String = "+48123456789",
        message: String = "Test message",
        priority: SmsPriority = SmsPriority.NORMAL,
        status: SmsStatus = SmsStatus.PENDING,
        retryCount: Int = 0,
        maxRetries: Int = 3,
        retryStrategy: RetryStrategy = RetryStrategy.EXPONENTIAL_BACKOFF,
        createdAt: Long = System.currentTimeMillis()
    ): SmsMessage {
        return SmsMessage(
            id = 0,
            phoneNumber = phoneNumber,
            messageContent = message,
            status = status,
            priority = priority,
            createdAt = createdAt,
            scheduledAt = null,
            sentAt = null,
            errorMessage = null,
            retryCount = retryCount,
            maxRetries = maxRetries,
            retryStrategy = retryStrategy,
            queuePosition = null
        )
    }
}