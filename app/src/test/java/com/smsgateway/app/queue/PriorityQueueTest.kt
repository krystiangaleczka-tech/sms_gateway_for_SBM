package com.smsgateway.app.queue

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicLong

class PriorityQueueTest {
    
    private lateinit var priorityQueue: PriorityQueue<String>
    
    @BeforeEach
    fun setUp() {
        priorityQueue = PriorityQueue()
    }
    
    @AfterEach
    fun tearDown() {
        priorityQueue.clear()
    }
    
    @Test
    fun `should add item with priority successfully`() = runTest {
        // Given
        val item = "test-item"
        val priority = SmsPriority.HIGH
        
        // When
        priorityQueue.add(item, priority)
        
        // Then
        assertEquals(1, priorityQueue.size())
        assertTrue(priorityQueue.contains(item))
    }
    
    @Test
    fun `should add multiple items with different priorities`() = runTest {
        // Given
        val items = mapOf(
            "low-item" to SmsPriority.LOW,
            "high-item" to SmsPriority.HIGH,
            "normal-item" to SmsPriority.NORMAL,
            "urgent-item" to SmsPriority.URGENT
        )
        
        // When
        items.forEach { (item, priority) ->
            priorityQueue.add(item, priority)
        }
        
        // Then
        assertEquals(4, priorityQueue.size())
        items.keys.forEach { item ->
            assertTrue(priorityQueue.contains(item))
        }
    }
    
    @Test
    fun `should poll items in priority order`() = runTest {
        // Given
        priorityQueue.add("low-item", SmsPriority.LOW)
        priorityQueue.add("high-item", SmsPriority.HIGH)
        priorityQueue.add("normal-item", SmsPriority.NORMAL)
        priorityQueue.add("urgent-item", SmsPriority.URGENT)
        
        // When
        val polledItems = mutableListOf<String>()
        while (priorityQueue.isNotEmpty()) {
            polledItems.add(priorityQueue.poll()!!)
        }
        
        // Then
        assertEquals(4, polledItems.size)
        assertEquals("urgent-item", polledItems[0])  // URGENT (4)
        assertEquals("high-item", polledItems[1])    // HIGH (3)
        assertEquals("normal-item", polledItems[2])  // NORMAL (2)
        assertEquals("low-item", polledItems[3])     // LOW (1)
    }
    
    @Test
    fun `should handle items with same priority in FIFO order`() = runTest {
        // Given
        priorityQueue.add("first-normal", SmsPriority.NORMAL)
        priorityQueue.add("second-normal", SmsPriority.NORMAL)
        priorityQueue.add("third-normal", SmsPriority.NORMAL)
        
        // When
        val polledItems = mutableListOf<String>()
        while (priorityQueue.isNotEmpty()) {
            polledItems.add(priorityQueue.poll()!!)
        }
        
        // Then
        assertEquals(3, polledItems.size)
        assertEquals("first-normal", polledItems[0])
        assertEquals("second-normal", polledItems[1])
        assertEquals("third-normal", polledItems[2])
    }
    
    @Test
    fun `should peek at next item without removing`() = runTest {
        // Given
        priorityQueue.add("low-item", SmsPriority.LOW)
        priorityQueue.add("high-item", SmsPriority.HIGH)
        
        // When
        val peekedItem = priorityQueue.peek()
        
        // Then
        assertEquals("high-item", peekedItem)
        assertEquals(2, priorityQueue.size()) // Item should not be removed
    }
    
    @Test
    fun `should return null when peeking empty queue`() = runTest {
        // Given
        assertTrue(priorityQueue.isEmpty())
        
        // When
        val peekedItem = priorityQueue.peek()
        
        // Then
        assertNull(peekedItem)
    }
    
    @Test
    fun `should return null when polling empty queue`() = runTest {
        // Given
        assertTrue(priorityQueue.isEmpty())
        
        // When
        val polledItem = priorityQueue.poll()
        
        // Then
        assertNull(polledItem)
    }
    
    @Test
    fun `should check if queue contains item`() = runTest {
        // Given
        val item = "test-item"
        priorityQueue.add(item, SmsPriority.NORMAL)
        
        // When
        val contains = priorityQueue.contains(item)
        val notContains = priorityQueue.contains("non-existent-item")
        
        // Then
        assertTrue(contains)
        assertFalse(notContains)
    }
    
    @Test
    fun `should remove item from queue`() = runTest {
        // Given
        val item = "test-item"
        priorityQueue.add(item, SmsPriority.NORMAL)
        assertEquals(1, priorityQueue.size())
        
        // When
        val removed = priorityQueue.remove(item)
        
        // Then
        assertTrue(removed)
        assertEquals(0, priorityQueue.size())
        assertFalse(priorityQueue.contains(item))
    }
    
    @Test
    fun `should return false when removing non-existent item`() = runTest {
        // Given
        val item = "non-existent-item"
        
        // When
        val removed = priorityQueue.remove(item)
        
        // Then
        assertFalse(removed)
    }
    
    @Test
    fun `should clear all items from queue`() = runTest {
        // Given
        priorityQueue.add("item1", SmsPriority.LOW)
        priorityQueue.add("item2", SmsPriority.NORMAL)
        priorityQueue.add("item3", SmsPriority.HIGH)
        assertEquals(3, priorityQueue.size())
        
        // When
        priorityQueue.clear()
        
        // Then
        assertEquals(0, priorityQueue.size())
        assertTrue(priorityQueue.isEmpty())
    }
    
    @Test
    fun `should return correct queue size`() = runTest {
        // Given
        assertEquals(0, priorityQueue.size())
        
        // When
        priorityQueue.add("item1", SmsPriority.LOW)
        assertEquals(1, priorityQueue.size())
        
        priorityQueue.add("item2", SmsPriority.NORMAL)
        assertEquals(2, priorityQueue.size())
        
        priorityQueue.poll()
        assertEquals(1, priorityQueue.size())
        
        priorityQueue.clear()
        
        // Then
        assertEquals(0, priorityQueue.size())
    }
    
    @Test
    fun `should check if queue is empty`() = runTest {
        // Given
        assertTrue(priorityQueue.isEmpty())
        
        // When
        priorityQueue.add("item", SmsPriority.NORMAL)
        
        // Then
        assertFalse(priorityQueue.isEmpty())
        
        // When
        priorityQueue.poll()
        
        // Then
        assertTrue(priorityQueue.isEmpty())
    }
    
    @Test
    fun `should check if queue is not empty`() = runTest {
        // Given
        assertFalse(priorityQueue.isNotEmpty())
        
        // When
        priorityQueue.add("item", SmsPriority.NORMAL)
        
        // Then
        assertTrue(priorityQueue.isNotEmpty())
        
        // When
        priorityQueue.poll()
        
        // Then
        assertFalse(priorityQueue.isNotEmpty())
    }
    
    @Test
    fun `should convert queue to list`() = runTest {
        // Given
        priorityQueue.add("low-item", SmsPriority.LOW)
        priorityQueue.add("high-item", SmsPriority.HIGH)
        priorityQueue.add("normal-item", SmsPriority.NORMAL)
        
        // When
        val list = priorityQueue.toList()
        
        // Then
        assertEquals(3, list.size)
        assertTrue(list.contains("low-item"))
        assertTrue(list.contains("high-item"))
        assertTrue(list.contains("normal-item"))
    }
    
    @Test
    fun `should handle concurrent operations safely`() = runTest {
        // Given
        val numThreads = 10
        val itemsPerThread = 100
        val totalItems = numThreads * itemsPerThread
        val counter = AtomicLong(0)
        
        // When
        val threads = (1..numThreads).map { threadId ->
            Thread {
                repeat(itemsPerThread) {
                    val item = "thread-${threadId}-item-$it"
                    val priority = SmsPriority.values()[it % SmsPriority.values().size]
                    priorityQueue.add(item, priority)
                    counter.incrementAndGet()
                }
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        
        // Then
        assertEquals(totalItems.toLong(), counter.get())
        assertEquals(totalItems, priorityQueue.size())
    }
    
    @Test
    fun `should handle concurrent poll operations safely`() = runTest {
        // Given
        val numItems = 1000
        repeat(numItems) { i ->
            priorityQueue.add("item-$i", SmsPriority.NORMAL)
        }
        
        val numThreads = 5
        val polledItems = mutableListOf<String>()
        val polledCount = AtomicLong(0)
        
        // When
        val threads = (1..numThreads).map {
            Thread {
                while (true) {
                    val item = priorityQueue.poll()
                    if (item != null) {
                        synchronized(polledItems) {
                            polledItems.add(item)
                        }
                        polledCount.incrementAndGet()
                    } else {
                        break
                    }
                }
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        
        // Then
        assertEquals(numItems.toLong(), polledCount.get())
        assertEquals(numItems, polledItems.size)
        assertEquals(0, priorityQueue.size())
    }
    
    @Test
    fun `should maintain priority order with mixed operations`() = runTest {
        // Given
        priorityQueue.add("item1", SmsPriority.NORMAL)
        priorityQueue.add("item2", SmsPriority.HIGH)
        
        // When - Mix operations
        val polled1 = priorityQueue.poll()  // Should be item2 (HIGH)
        priorityQueue.add("item3", SmsPriority.URGENT)
        priorityQueue.add("item4", SmsPriority.LOW)
        val polled2 = priorityQueue.poll()  // Should be item3 (URGENT)
        priorityQueue.remove("item1")        // Remove remaining NORMAL item
        priorityQueue.add("item5", SmsPriority.NORMAL)
        
        // Then
        assertEquals("item2", polled1)
        assertEquals("item3", polled2)
        
        val remainingItems = mutableListOf<String>()
        while (priorityQueue.isNotEmpty()) {
            remainingItems.add(priorityQueue.poll()!!)
        }
        
        assertEquals(3, remainingItems.size)
        assertEquals("item5", remainingItems[0])  // NORMAL (2)
        assertEquals("item4", remainingItems[1])  // LOW (1)
        // item1 was removed
    }
    
    @Test
    fun `should handle large number of items efficiently`() = runTest {
        // Given
        val numItems = 10000
        
        // When
        val startTime = System.currentTimeMillis()
        repeat(numItems) { i ->
            val priority = SmsPriority.values()[i % SmsPriority.values().size]
            priorityQueue.add("item-$i", priority)
        }
        val addTime = System.currentTimeMillis() - startTime
        
        // Then
        assertEquals(numItems, priorityQueue.size())
        assertTrue(addTime < 1000) // Should complete within 1 second
        
        // Test polling performance
        val pollStartTime = System.currentTimeMillis()
        val polledItems = mutableListOf<String>()
        while (priorityQueue.isNotEmpty()) {
            polledItems.add(priorityQueue.poll()!!)
        }
        val pollTime = System.currentTimeMillis() - pollStartTime
        
        assertEquals(numItems, polledItems.size)
        assertTrue(pollTime < 1000) // Should complete within 1 second
    }
    
    @Test
    fun `should provide iterator that respects priority order`() = runTest {
        // Given
        priorityQueue.add("low-item", SmsPriority.LOW)
        priorityQueue.add("high-item", SmsPriority.HIGH)
        priorityQueue.add("normal-item", SmsPriority.NORMAL)
        
        // When
        val iterator = priorityQueue.iterator()
        val iteratedItems = mutableListOf<String>()
        
        while (iterator.hasNext()) {
            iteratedItems.add(iterator.next())
        }
        
        // Then
        assertEquals(3, iteratedItems.size)
        // Iterator should provide items in priority order
        assertTrue(iteratedItems.indexOf("high-item") < iteratedItems.indexOf("normal-item"))
        assertTrue(iteratedItems.indexOf("normal-item") < iteratedItems.indexOf("low-item"))
    }
    
    @Test
    fun `should handle iterator remove operation`() = runTest {
        // Given
        priorityQueue.add("item1", SmsPriority.NORMAL)
        priorityQueue.add("item2", SmsPriority.HIGH)
        priorityQueue.add("item3", SmsPriority.LOW)
        
        // When
        val iterator = priorityQueue.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item == "item2") {
                iterator.remove()
            }
        }
        
        // Then
        assertEquals(2, priorityQueue.size())
        assertFalse(priorityQueue.contains("item2"))
        assertTrue(priorityQueue.contains("item1"))
        assertTrue(priorityQueue.contains("item3"))
    }
    
    @Test
    fun `should handle null items gracefully`() = runTest {
        // When & Then
        assertThrows<IllegalArgumentException> {
            priorityQueue.add(null, SmsPriority.NORMAL)
        }
        
        assertThrows<IllegalArgumentException> {
            priorityQueue.contains(null)
        }
        
        assertThrows<IllegalArgumentException> {
            priorityQueue.remove(null)
        }
    }
    
    @Test
    fun `should provide queue statistics`() = runTest {
        // Given
        priorityQueue.add("item1", SmsPriority.LOW)
        priorityQueue.add("item2", SmsPriority.NORMAL)
        priorityQueue.add("item3", SmsPriority.HIGH)
        priorityQueue.add("item4", SmsPriority.URGENT)
        priorityQueue.add("item5", SmsPriority.NORMAL)
        
        // When
        val stats = priorityQueue.getStatistics()
        
        // Then
        assertEquals(4, stats.size)
        assertEquals(1, stats.countByPriority[SmsPriority.LOW])
        assertEquals(2, stats.countByPriority[SmsPriority.NORMAL])
        assertEquals(1, stats.countByPriority[SmsPriority.HIGH])
        assertEquals(1, stats.countByPriority[SmsPriority.URGENT])
        assertEquals(SmsPriority.URGENT, stats.highestPriority)
        assertEquals(SmsPriority.LOW, stats.lowestPriority)
    }
    
    @Test
    fun `should handle empty queue statistics`() = runTest {
        // Given
        assertTrue(priorityQueue.isEmpty())
        
        // When
        val stats = priorityQueue.getStatistics()
        
        // Then
        assertEquals(0, stats.size)
        assertTrue(stats.countByPriority.isEmpty())
        assertNull(stats.highestPriority)
        assertNull(stats.lowestPriority)
    }
    
    // Test data class for statistics
    data class QueueStatistics(
        val size: Int,
        val countByPriority: Map<SmsPriority, Int>,
        val highestPriority: SmsPriority?,
        val lowestPriority: SmsPriority?
    )
}