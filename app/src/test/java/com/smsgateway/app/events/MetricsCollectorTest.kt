package com.smsgateway.app.events

import io.ktor.server.application.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.mockito.kotlin.*
import java.util.concurrent.ConcurrentHashMap

class MetricsCollectorTest {
    
    private lateinit var metricsCollector: MetricsCollector
    private lateinit var mockApplication: Application
    
    @BeforeEach
    fun setUp() {
        mockApplication = mock()
        metricsCollector = MetricsCollector(mockApplication)
    }
    
    @AfterEach
    fun tearDown() {
        metricsCollector.shutdown()
    }
    
    @Test
    fun `should increment counter metric successfully`() = runTest {
        // Given
        val metricName = "test.counter"
        val tags = mapOf("tag1" to "value1", "tag2" to "value2")
        
        // When
        metricsCollector.incrementCounter(metricName, tags)
        
        // Then
        val value = metricsCollector.getCounter(metricName, tags)
        assertEquals(1L, value)
    }
    
    @Test
    fun `should increment counter multiple times`() = runTest {
        // Given
        val metricName = "test.counter"
        val tags = mapOf("tag1" to "value1")
        
        // When
        metricsCollector.incrementCounter(metricName, tags)
        metricsCollector.incrementCounter(metricName, tags)
        metricsCollector.incrementCounter(metricName, tags)
        
        // Then
        val value = metricsCollector.getCounter(metricName, tags)
        assertEquals(3L, value)
    }
    
    @Test
    fun `should handle counters with different tags separately`() = runTest {
        // Given
        val metricName = "test.counter"
        val tags1 = mapOf("type" to "A")
        val tags2 = mapOf("type" to "B")
        
        // When
        metricsCollector.incrementCounter(metricName, tags1)
        metricsCollector.incrementCounter(metricName, tags1)
        metricsCollector.incrementCounter(metricName, tags2)
        
        // Then
        assertEquals(2L, metricsCollector.getCounter(metricName, tags1))
        assertEquals(1L, metricsCollector.getCounter(metricName, tags2))
    }
    
    @Test
    fun `should record gauge metric successfully`() = runTest {
        // Given
        val metricName = "test.gauge"
        val value = 42.5
        val tags = mapOf("unit" to "seconds")
        
        // When
        metricsCollector.setGauge(metricName, value, tags)
        
        // Then
        val recordedValue = metricsCollector.getGauge(metricName, tags)
        assertEquals(value, recordedValue, 0.001)
    }
    
    @Test
    fun `should update gauge value`() = runTest {
        // Given
        val metricName = "test.gauge"
        val tags = mapOf("unit" to "seconds")
        
        // When
        metricsCollector.setGauge(metricName, 10.0, tags)
        metricsCollector.setGauge(metricName, 20.5, tags)
        
        // Then
        val recordedValue = metricsCollector.getGauge(metricName, tags)
        assertEquals(20.5, recordedValue, 0.001)
    }
    
    @Test
    fun `should record timer metric successfully`() = runTest {
        // Given
        val metricName = "test.timer"
        val duration = 150L
        val tags = mapOf("operation" to "send_sms")
        
        // When
        metricsCollector.recordTimer(metricName, duration, tags)
        
        // Then
        val stats = metricsCollector.getTimerStats(metricName, tags)
        assertEquals(1L, stats.count)
        assertEquals(duration.toDouble(), stats.average, 0.001)
        assertEquals(duration.toDouble(), stats.min, 0.001)
        assertEquals(duration.toDouble(), stats.max, 0.001)
    }
    
    @Test
    fun `should calculate timer statistics correctly`() = runTest {
        // Given
        val metricName = "test.timer"
        val tags = mapOf("operation" to "send_sms")
        val durations = listOf(100L, 200L, 300L, 400L, 500L)
        
        // When
        durations.forEach { metricsCollector.recordTimer(metricName, it, tags) }
        
        // Then
        val stats = metricsCollector.getTimerStats(metricName, tags)
        assertEquals(5L, stats.count)
        assertEquals(300.0, stats.average, 0.001) // (100+200+300+400+500)/5
        assertEquals(100.0, stats.min, 0.001)
        assertEquals(500.0, stats.max, 0.001)
    }
    
    @Test
    fun `should record histogram metric successfully`() = runTest {
        // Given
        val metricName = "test.histogram"
        val value = 25.5
        val buckets = listOf(10.0, 20.0, 30.0, 40.0)
        val tags = mapOf("type" to "response_size")
        
        // When
        metricsCollector.recordHistogram(metricName, value, buckets, tags)
        
        // Then
        val bucketCounts = metricsCollector.getHistogramBuckets(metricName, tags)
        assertEquals(0L, bucketCounts[10.0])    // <= 10
        assertEquals(0L, bucketCounts[20.0])    // <= 20
        assertEquals(1L, bucketCounts[30.0])    // <= 30 (25.5 falls here)
        assertEquals(1L, bucketCounts[40.0])    // <= 40
    }
    
    @Test
    fun `should handle multiple histogram values`() = runTest {
        // Given
        val metricName = "test.histogram"
        val buckets = listOf(10.0, 20.0, 30.0, 40.0)
        val tags = mapOf("type" to "response_size")
        val values = listOf(5.0, 15.0, 25.0, 35.0)
        
        // When
        values.forEach { metricsCollector.recordHistogram(metricName, it, buckets, tags) }
        
        // Then
        val bucketCounts = metricsCollector.getHistogramBuckets(metricName, tags)
        assertEquals(1L, bucketCounts[10.0])    // 5.0
        assertEquals(2L, bucketCounts[20.0])    // 5.0, 15.0
        assertEquals(3L, bucketCounts[30.0])    // 5.0, 15.0, 25.0
        assertEquals(4L, bucketCounts[40.0])    // All values
    }
    
    @Test
    fun `should get all metrics of specific type`() = runTest {
        // Given
        val tags1 = mapOf("type" to "A")
        val tags2 = mapOf("type" to "B")
        
        metricsCollector.incrementCounter("test.metric", tags1)
        metricsCollector.incrementCounter("test.metric", tags2)
        metricsCollector.incrementCounter("other.metric", tags1)
        
        // When
        val testMetrics = metricsCollector.getAllCounters("test.metric")
        
        // Then
        assertEquals(2, testMetrics.size)
        assertTrue(testMetrics.containsKey(tags1))
        assertTrue(testMetrics.containsKey(tags2))
        assertEquals(1L, testMetrics[tags1])
        assertEquals(1L, testMetrics[tags2])
    }
    
    @Test
    fun `should clear metrics successfully`() = runTest {
        // Given
        val metricName = "test.counter"
        val tags = mapOf("type" to "test")
        metricsCollector.incrementCounter(metricName, tags)
        
        // Verify metric exists
        assertEquals(1L, metricsCollector.getCounter(metricName, tags))
        
        // When
        metricsCollector.clearMetric(metricName)
        
        // Then
        assertEquals(0L, metricsCollector.getCounter(metricName, tags))
    }
    
    @Test
    fun `should clear all metrics successfully`() = runTest {
        // Given
        metricsCollector.incrementCounter("counter1", mapOf("type" to "A"))
        metricsCollector.setGauge("gauge1", 10.0, mapOf("type" to "A"))
        metricsCollector.recordTimer("timer1", 100L, mapOf("type" to "A"))
        
        // Verify metrics exist
        assertEquals(1L, metricsCollector.getCounter("counter1", mapOf("type" to "A")))
        assertEquals(10.0, metricsCollector.getGauge("gauge1", mapOf("type" to "A")), 0.001)
        assertEquals(1L, metricsCollector.getTimerStats("timer1", mapOf("type" to "A")).count)
        
        // When
        metricsCollector.clearAll()
        
        // Then
        assertEquals(0L, metricsCollector.getCounter("counter1", mapOf("type" to "A")))
        assertEquals(0.0, metricsCollector.getGauge("gauge1", mapOf("type" to "A")), 0.001)
        assertEquals(0L, metricsCollector.getTimerStats("timer1", mapOf("type" to "A")).count)
    }
    
    @Test
    fun `should export metrics in Prometheus format`() = runTest {
        // Given
        val tags = mapOf("method" to "POST", "endpoint" to "/api/sms")
        metricsCollector.incrementCounter("http_requests_total", tags)
        metricsCollector.setGauge("active_connections", 25.0, mapOf())
        metricsCollector.recordTimer("request_duration_ms", 150L, tags)
        
        // When
        val prometheusFormat = metricsCollector.exportPrometheus()
        
        // Then
        assertTrue(prometheusFormat.contains("# TYPE http_requests_total counter"))
        assertTrue(prometheusFormat.contains("http_requests_total{method=\"POST\",endpoint=\"/api/sms\"} 1"))
        assertTrue(prometheusFormat.contains("# TYPE active_connections gauge"))
        assertTrue(prometheusFormat.contains("active_connections 25.0"))
        assertTrue(prometheusFormat.contains("# TYPE request_duration_ms summary"))
        assertTrue(prometheusFormat.contains("request_duration_ms_count{method=\"POST\",endpoint=\"/api/sms\"} 1"))
    }
    
    @Test
    fun `should export metrics in JSON format`() = runTest {
        // Given
        val tags = mapOf("type" to "sms")
        metricsCollector.incrementCounter("messages_sent", tags)
        metricsCollector.setGauge("queue_size", 10.0, tags)
        
        // When
        val jsonFormat = metricsCollector.exportJson()
        
        // Then
        assertTrue(jsonFormat.contains("\"counters\""))
        assertTrue(jsonFormat.contains("\"gauges\""))
        assertTrue(jsonFormat.contains("\"messages_sent\""))
        assertTrue(jsonFormat.contains("\"queue_size\""))
        assertTrue(jsonFormat.contains("\"type\":\"sms\""))
    }
    
    @Test
    fun `should handle metric name validation`() = runTest {
        // Given
        val invalidNames = listOf("", "invalid name", "invalid@name", "123name")
        
        // When & Then
        invalidNames.forEach { name ->
            assertThrows(IllegalArgumentException::class.java) {
                metricsCollector.incrementCounter(name)
            }
        }
    }
    
    @Test
    fun `should handle tag key and value validation`() = runTest {
        // Given
        val invalidTags = mapOf(
            "" to "value",           // Empty key
            "key" to "",             // Empty value
            "invalid key" to "value", // Space in key
            "key" to "invalid value"  // Space in value
        )
        
        // When & Then
        invalidTags.forEach { (key, value) ->
            assertThrows(IllegalArgumentException::class.java) {
                metricsCollector.incrementCounter("test.metric", mapOf(key to value))
            }
        }
    }
    
    @Test
    fun `should handle concurrent metric updates safely`() = runTest {
        // Given
        val metricName = "concurrent.counter"
        val tags = mapOf("type" to "test")
        val numThreads = 10
        val incrementsPerThread = 100
        
        // When
        val threads = (1..numThreads).map { threadId ->
            Thread {
                repeat(incrementsPerThread) {
                    metricsCollector.incrementCounter(metricName, tags)
                }
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        
        // Then
        val expectedValue = (numThreads * incrementsPerThread).toLong()
        assertEquals(expectedValue, metricsCollector.getCounter(metricName, tags))
    }
    
    @Test
    fun `should provide metric summary`() = runTest {
        // Given
        val tags1 = mapOf("type" to "A")
        val tags2 = mapOf("type" to "B")
        
        metricsCollector.incrementCounter("counter1", tags1)
        metricsCollector.incrementCounter("counter1", tags2)
        metricsCollector.setGauge("gauge1", 10.0, tags1)
        metricsCollector.recordTimer("timer1", 100L, tags1)
        
        // When
        val summary = metricsCollector.getMetricSummary()
        
        // Then
        assertTrue(summary.containsKey("counters"))
        assertTrue(summary.containsKey("gauges"))
        assertTrue(summary.containsKey("timers"))
        assertEquals(2, summary["counters"])
        assertEquals(1, summary["gauges"])
        assertEquals(1, summary["timers"])
    }
    
    @Test
    fun `should handle metric expiration`() = runTest {
        // Given
        val metricName = "expiring.counter"
        val tags = mapOf("type" to "test")
        metricsCollector.incrementCounter(metricName, tags)
        
        // When
        metricsCollector.setMetricExpiration(metricName, 100L) // 100ms expiration
        Thread.sleep(150) // Wait longer than expiration
        
        // Then
        assertEquals(0L, metricsCollector.getCounter(metricName, tags))
    }
    
    @Test
    fun `should reset specific metric type`() = runTest {
        // Given
        val tags = mapOf("type" to "test")
        metricsCollector.incrementCounter("counter1", tags)
        metricsCollector.incrementCounter("counter2", tags)
        metricsCollector.setGauge("gauge1", 10.0, tags)
        
        // When
        metricsCollector.resetCounters()
        
        // Then
        assertEquals(0L, metricsCollector.getCounter("counter1", tags))
        assertEquals(0L, metricsCollector.getCounter("counter2", tags))
        assertEquals(10.0, metricsCollector.getGauge("gauge1", tags), 0.001) // Should not be affected
    }
    
    @Test
    fun `should handle metrics with same name but different types`() = runTest {
        // Given
        val metricName = "same.name"
        val tags = mapOf("type" to "test")
        
        // When
        metricsCollector.incrementCounter(metricName, tags)
        metricsCollector.setGauge(metricName, 10.0, tags)
        
        // Then
        // Should handle both metrics separately
        assertEquals(1L, metricsCollector.getCounter(metricName, tags))
        assertEquals(10.0, metricsCollector.getGauge(metricName, tags), 0.001)
    }
    
    // Test data classes
    data class TimerStats(
        val count: Long = 0,
        val sum: Double = 0.0,
        val average: Double = 0.0,
        val min: Double = 0.0,
        val max: Double = 0.0
    )
    
    data class MetricSummary(
        val counters: Int = 0,
        val gauges: Int = 0,
        val timers: Int = 0,
        val histograms: Int = 0
    )
}