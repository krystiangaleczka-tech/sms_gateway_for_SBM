package com.smsgateway.app.events

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * MetricsCollector - zbiera i agreguje metryki wydajnościowe systemu
 * Monitoruje kluczowe wskaźniki KPI, generuje raporty i alerty
 * Obsługuje czasowe okna agregacji i przechowywanie historycznych danych
 */
@Singleton
class MetricsCollector @Inject constructor(
    private val eventPublisher: EventPublisher
) : EventSubscriber<PerformanceMetricEvent> {
    
    private val logger = LoggerFactory.getLogger(MetricsCollector::class.java)
    
    // Scope dla asynchronicznej agregacji
    private val metricsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Liczniki dla różnych typów metryk
    private val counters = ConcurrentHashMap<String, LongAdder>()
    private val gauges = ConcurrentHashMap<String, AtomicLong>()
    private val timers = ConcurrentHashMap<String, MetricTimer>()
    
    // Historyczne dane dla metryk
    private val historicalData = ConcurrentHashMap<String, CircularBuffer<MetricSnapshot>>()
    
    // Ostatni czas aktualizacji
    private val lastUpdateTimes = ConcurrentHashMap<String, Long>()
    
    // Wartości progowe dla alertów
    private val thresholds = ConcurrentHashMap<String, MetricThreshold>()
    
    // Konfiguracja agregacji
    private data class AggregationConfig(
        val windowSize: Int = 100,      // Rozmiar okna czasowego
        val aggregationPeriod: Long = 60000L  // Okres agregacji w ms (1 minuta)
    )
    
    private val aggregationConfig = AggregationConfig()
    
    init {
        // Rejestracja jako subskrybent zdarzeń metryk
        eventPublisher.subscribe(this)
        
        // Uruchomienie okresowej agregacji
        startPeriodicAggregation()
        
        logger.info("MetricsCollector initialized")
    }
    
    override suspend fun onEvent(event: PerformanceMetricEvent) {
        when (event.metricType) {
            MetricType.COUNTER -> incrementCounter(event.metricName, event.value.toLong())
            MetricType.GAUGE -> setGauge(event.metricName, event.value.toLong())
            MetricType.TIMER -> recordTimer(event.metricName, event.value.toLong())
            MetricType.HISTOGRAM -> recordHistogram(event.metricName, event.value.toDouble())
        }
        
        // Sprawdzenie progów alertów
        checkThresholds(event.metricName, event.value)
    }
    
    override fun getEventType(): Class<PerformanceMetricEvent> {
        return PerformanceMetricEvent::class.java
    }
    
    /**
     * Zwiększa licznik o podaną wartość
     */
    fun incrementCounter(name: String, value: Long = 1L) {
        counters.getOrPut(name) { LongAdder() }.add(value)
        updateLastUpdateTime(name)
        
        logger.trace("Counter incremented: $name by $value")
    }
    
    /**
     * Ustawia wartość gauge
     */
    fun setGauge(name: String, value: Long) {
        gauges.getOrPut(name) { AtomicLong() }.set(value)
        updateLastUpdateTime(name)
        
        logger.trace("Gauge set: $name to $value")
    }
    
    /**
     * Rejestruje czas operacji
     */
    fun recordTimer(name: String, durationMs: Long) {
        timers.getOrPut(name) { MetricTimer() }.record(durationMs)
        updateLastUpdateTime(name)
        
        logger.trace("Timer recorded: $name = ${durationMs}ms")
    }
    
    /**
     * Rejestruje wartość w histogramie
     */
    fun recordHistogram(name: String, value: Double) {
        // Implementacja histogramu za pomocą liczników dla bucketów
        val bucketName = "${name}_bucket"
        val bucketSize = determineBucketSize(value)
        val bucketKey = "${bucketName}_${bucketSize}"
        
        counters.getOrPut(bucketKey) { LongAdder() }.add(1)
        counters.getOrPut("${name}_count") { LongAdder() }.add(1)
        counters.getOrPut("${name}_sum") { LongAdder() }.add(value.toLong())
        
        updateLastUpdateTime(name)
        
        logger.trace("Histogram recorded: $name = $value (bucket: $bucketSize)")
    }
    
    /**
     * Pobiera wartość licznika
     */
    fun getCounter(name: String): Long {
        return counters[name]?.sum() ?: 0L
    }
    
    /**
     * Pobiera wartość gauge
     */
    fun getGauge(name: String): Long {
        return gauges[name]?.get() ?: 0L
    }
    
    /**
     * Pobiera statystyki timera
     */
    fun getTimerStats(name: String): TimerStats? {
        return timers[name]?.getStats()
    }
    
    /**
     * Pobiera statystyki histogramu
     */
    fun getHistogramStats(name: String): HistogramStats? {
        val count = getCounter("${name}_count")
        val sum = getCounter("${name}_sum")
        
        if (count == 0L) return null
        
        // Obliczanie percentyli na podstawie bucketów
        val buckets = mutableListOf<HistogramBucket>()
        var totalCount = 0L
        
        // Zebranie wszystkich bucketów
        counters.keys.filter { it.startsWith("${name}_bucket_") }.forEach { bucketKey ->
            val bucketValue = bucketKey.substringAfterLast("_").toDouble()
            val bucketCount = counters[bucketKey]?.sum() ?: 0L
            totalCount += bucketCount
            buckets.add(HistogramBucket(bucketValue, bucketCount, totalCount))
        }
        
        buckets.sortBy { it.upperBound }
        
        // Obliczanie percentyli
        val p50 = calculatePercentile(buckets, 0.5, count)
        val p90 = calculatePercentile(buckets, 0.9, count)
        val p95 = calculatePercentile(buckets, 0.95, count)
        val p99 = calculatePercentile(buckets, 0.99, count)
        
        return HistogramStats(
            count = count,
            sum = sum.toDouble(),
            average = sum.toDouble() / count,
            p50 = p50,
            p90 = p90,
            p95 = p95,
            p99 = p99,
            buckets = buckets
        )
    }
    
    /**
     * Pobiera wszystkie metryki w formie mapy
     */
    fun getAllMetrics(): Map<String, Any> {
        val allMetrics = mutableMapOf<String, Any>()
        
        // Liczniki
        counters.forEach { (name, adder) ->
            allMetrics["counter_$name"] = adder.sum()
        }
        
        // Gauges
        gauges.forEach { (name, gauge) ->
            allMetrics["gauge_$name"] = gauge.get()
        }
        
        // Timery
        timers.forEach { (name, timer) ->
            val stats = timer.getStats()
            allMetrics["timer_${name}_count"] = stats.count
            allMetrics["timer_${name}_sum"] = stats.sum
            allMetrics["timer_${name}_average"] = stats.average
            allMetrics["timer_${name}_min"] = stats.min
            allMetrics["timer_${name}_max"] = stats.max
            allMetrics["timer_${name}_p50"] = stats.p50
            allMetrics["timer_${name}_p90"] = stats.p90
            allMetrics["timer_${name}_p95"] = stats.p95
            allMetrics["timer_${name}_p99"] = stats.p99
        }
        
        return allMetrics
    }
    
    /**
     * Pobiera metryki dla kolejki SMS
     */
    fun getQueueMetrics(): QueueMetrics {
        val totalMessages = getCounter("sms_total")
        val queuedMessages = getGauge("sms_queued")
        val sendingMessages = getGauge("sms_sending")
        val sentMessages = getCounter("sms_sent")
        val failedMessages = getCounter("sms_failed")
        
        val processingTimeStats = getTimerStats("sms_processing_time")
        val averageProcessingTime = processingTimeStats?.average ?: 0.0
        
        // Obliczanie przepustowości na godzinę
        val currentTime = System.currentTimeMillis()
        val oneHourAgo = currentTime - 3600000L
        val sentInLastHour = getCounterInPeriod("sms_sent", oneHourAgo, currentTime)
        
        val errorRate = if (totalMessages > 0) {
            failedMessages.toDouble() / totalMessages.toDouble()
        } else {
            0.0
        }
        
        return QueueMetrics(
            totalMessages = totalMessages.toInt(),
            queuedMessages = queuedMessages.toInt(),
            scheduledMessages = getGauge("sms_scheduled").toInt(),
            sendingMessages = sendingMessages.toInt(),
            sentMessages = sentMessages.toInt(),
            failedMessages = failedMessages.toInt(),
            averageProcessingTime = averageProcessingTime.toLong(),
            throughputPerHour = sentInLastHour.toInt(),
            errorRate = errorRate
        )
    }
    
    /**
     * Pobiera metryki wydajnościowe systemu
     */
    fun getPerformanceMetrics(): PerformanceMetrics {
        val cpuUsage = getGauge("system_cpu_usage")
        val memoryUsage = getGauge("system_memory_usage")
        val diskUsage = getGauge("system_disk_usage")
        val networkIO = getCounter("system_network_io")
        
        val databaseStats = getTimerStats("database_query_time")
        val apiStats = getTimerStats("api_request_time")
        
        return PerformanceMetrics(
            cpuUsagePercent = cpuUsage,
            memoryUsagePercent = memoryUsage,
            diskUsagePercent = diskUsage,
            networkIOBytes = networkIO,
            databaseAverageQueryTime = databaseStats?.average ?: 0.0,
            apiAverageResponseTime = apiStats?.average ?: 0.0
        )
    }
    
    /**
     * Ustawia próg alertu dla metryki
     */
    fun setThreshold(name: String, threshold: MetricThreshold) {
        thresholds[name] = threshold
        logger.info("Threshold set for metric: $name")
    }
    
    /**
     * Usuwa próg alertu
     */
    fun removeThreshold(name: String) {
        thresholds.remove(name)
        logger.info("Threshold removed for metric: $name")
    }
    
    /**
     * Resetuje wszystkie metryki
     */
    fun resetAllMetrics() {
        counters.clear()
        gauges.clear()
        timers.clear()
        historicalData.clear()
        lastUpdateTimes.clear()
        
        logger.info("All metrics reset")
    }
    
    /**
     * Generuje raport metryk
     */
    fun generateReport(): MetricsReport {
        val timestamp = System.currentTimeMillis()
        val queueMetrics = getQueueMetrics()
        val performanceMetrics = getPerformanceMetrics()
        val allMetrics = getAllMetrics()
        
        return MetricsReport(
            timestamp = timestamp,
            queueMetrics = queueMetrics,
            performanceMetrics = performanceMetrics,
            allMetrics = allMetrics,
            thresholds = thresholds.toMap()
        )
    }
    
    /**
     * Uruchamia okresową agregację metryk
     */
    private fun startPeriodicAggregation() {
        metricsScope.launch {
            while (isActive) {
                delay(aggregationConfig.aggregationPeriod)
                aggregateMetrics()
            }
        }
    }
    
    /**
     * Agreguje metryki i zapisuje migawki
     */
    private suspend fun aggregateMetrics() {
        val timestamp = System.currentTimeMillis()
        
        // Agregacja liczników
        counters.forEach { (name, adder) ->
            val value = adder.sum()
            recordSnapshot(name, MetricType.COUNTER, value, timestamp)
        }
        
        // Agregacja gauges
        gauges.forEach { (name, gauge) ->
            val value = gauge.get()
            recordSnapshot(name, MetricType.GAUGE, value, timestamp)
        }
        
        // Agregacja timerów
        timers.forEach { (name, timer) ->
            val stats = timer.getStats()
            recordSnapshot("${name}_average", MetricType.GAUGE, stats.average.toLong(), timestamp)
            recordSnapshot("${name}_p95", MetricType.GAUGE, stats.p95.toLong(), timestamp)
        }
        
        logger.debug("Metrics aggregation completed at $timestamp")
    }
    
    /**
     * Zapisuje migawkę metryki
     */
    private fun recordSnapshot(name: String, type: MetricType, value: Long, timestamp: Long) {
        val buffer = historicalData.getOrPut(name) { CircularBuffer(aggregationConfig.windowSize) }
        buffer.add(MetricSnapshot(timestamp, type, value))
    }
    
    /**
     * Aktualizuje ostatni czas modyfikacji metryki
     */
    private fun updateLastUpdateTime(name: String) {
        lastUpdateTimes[name] = System.currentTimeMillis()
    }
    
    /**
     * Sprawdza progi i generuje alerty
     */
    private suspend fun checkThresholds(metricName: String, value: Double) {
        val threshold = thresholds[metricName] ?: return
        
        val alertLevel = when {
            value >= threshold.critical -> AlertLevel.CRITICAL
            value >= threshold.warning -> AlertLevel.WARNING
            value >= threshold.info -> AlertLevel.INFO
            else -> null
        }
        
        if (alertLevel != null) {
            val alert = AlertEvent(
                metricName = metricName,
                currentValue = value,
                threshold = threshold,
                alertLevel = alertLevel,
                timestamp = System.currentTimeMillis(),
                message = "Metric $metricName value $value exceeded ${alertLevel.name.lowercase()} threshold of ${threshold.getThresholdForLevel(alertLevel)}"
            )
            
            eventPublisher.publishAsync(alert)
            logger.warn("Alert triggered: ${alert.toLogString()}")
        }
    }
    
    /**
     * Określa rozmiar bucketa dla histogramu
     */
    private fun determineBucketSize(value: Double): Double {
        // Prosta implementacja - w praktyce może być bardziej zaawansowana
        return when {
            value < 10 -> 5.0
            value < 50 -> 10.0
            value < 100 -> 25.0
            value < 500 -> 50.0
            value < 1000 -> 100.0
            value < 5000 -> 500.0
            value < 10000 -> 1000.0
            else -> 5000.0
        }
    }
    
    /**
     * Oblicza percentyl dla histogramu
     */
    private fun calculatePercentile(buckets: List<HistogramBucket>, percentile: Double, totalCount: Long): Double {
        if (buckets.isEmpty() || totalCount == 0L) return 0.0
        
        val targetCount = (totalCount * percentile).toLong()
        
        for (bucket in buckets) {
            if (bucket.cumulativeCount >= targetCount) {
                return bucket.upperBound
            }
        }
        
        return buckets.last().upperBound
    }
    
    /**
     * Pobiera licznik w określonym okresie (uproszczona implementacja)
     */
    private fun getCounterInPeriod(name: String, startTime: Long, endTime: Long): Long {
        // W praktycznej implementacji wymagałoby to historii czasowej
        // Tutaj zwracamy aktualną wartość jako uproszczenie
        return getCounter(name)
    }
    
    /**
     * Zamyka MetricsCollector i czyści zasoby
     */
    fun shutdown() {
        metricsScope.cancel()
        logger.info("MetricsCollector shutdown")
    }
}

/**
 * Timer do mierzenia czasu operacji
 */
class MetricTimer {
    private val count = LongAdder()
    private val sum = LongAdder()
    private val min = AtomicLong(Long.MAX_VALUE)
    private val max = AtomicLong(Long.MIN_VALUE)
    private val values = CircularBuffer<Long>(1000) // Ostatnie 1000 wartości dla percentyli
    
    /**
     * Rejestruje czas operacji
     */
    fun record(durationMs: Long) {
        count.increment()
        sum.add(durationMs)
        min.updateAndGet { minOf(it, durationMs) }
        max.updateAndGet { maxOf(it, durationMs) }
        values.add(durationMs)
    }
    
    /**
     * Pobiera statystyki timera
     */
    fun getStats(): TimerStats {
        val countValue = count.sum()
        if (countValue == 0L) {
            return TimerStats(0, 0, 0.0, 0, 0, 0.0, 0.0, 0.0, 0.0)
        }
        
        val sumValue = sum.sum()
        val minValue = if (min.get() == Long.MAX_VALUE) 0L else min.get()
        val maxValue = if (max.get() == Long.MIN_VALUE) 0L else max.get()
        val average = sumValue.toDouble() / countValue
        
        // Obliczanie percentyli
        val sortedValues = values.toList().sorted()
        val p50 = calculatePercentile(sortedValues, 0.5)
        val p90 = calculatePercentile(sortedValues, 0.9)
        val p95 = calculatePercentile(sortedValues, 0.95)
        val p99 = calculatePercentile(sortedValues, 0.99)
        
        return TimerStats(
            count = countValue,
            sum = sumValue,
            average = average,
            min = minValue,
            max = maxValue,
            p50 = p50,
            p90 = p90,
            p95 = p95,
            p99 = p99
        )
    }
    
    private fun calculatePercentile(sortedValues: List<Long>, percentile: Double): Double {
        if (sortedValues.isEmpty()) return 0.0
        
        val index = (sortedValues.size * percentile).toInt().coerceAtMost(sortedValues.size - 1)
        return sortedValues[index].toDouble()
    }
}

/**
 * Bufor kołowy do przechowywania ostatnich wartości
 */
class CircularBuffer<T>(private val size: Int) {
    private val buffer = arrayOfNulls<Any>(size)
    private var head = 0
    private var count = 0
    
    /**
     * Dodaje element do bufora
     */
    fun add(item: T) {
        buffer[head] = item as Any
        head = (head + 1) % size
        if (count < size) count++
    }
    
    /**
     * Pobiera wszystkie elementy z bufora
     */
    @Suppress("UNCHECKED_CAST")
    fun toList(): List<T> {
        val result = mutableListOf<T>()
        
        for (i in 0 until count) {
            val index = (head - count + i + size) % size
            buffer[index]?.let { result.add(it as T) }
        }
        
        return result
    }
    
    /**
     * Czyści bufor
     */
    fun clear() {
        head = 0
        count = 0
        for (i in buffer.indices) {
            buffer[i] = null
        }
    }
}

/**
 * Typy metryk
 */
enum class MetricType {
    COUNTER,    // Licznik - tylko rośnie
    GAUGE,      // Wskaźnik - może rosnąć i maleć
    TIMER,      // Timer - mierzy czas
    HISTOGRAM   // Histogram - rozkład wartości
}

/**
 * Poziomy alertów
 */
enum class AlertLevel {
    INFO,
    WARNING,
    CRITICAL
}

/**
 * Próg alertu dla metryki
 */
data class MetricThreshold(
    val info: Double,
    val warning: Double,
    val critical: Double
) {
    fun getThresholdForLevel(level: AlertLevel): Double {
        return when (level) {
            AlertLevel.INFO -> info
            AlertLevel.WARNING -> warning
            AlertLevel.CRITICAL -> critical
        }
    }
}

/**
 * Migawka metryki
 */
data class MetricSnapshot(
    val timestamp: Long,
    val type: MetricType,
    val value: Long
)

/**
 * Statystyki timera
 */
data class TimerStats(
    val count: Long,
    val sum: Long,
    val average: Double,
    val min: Long,
    val max: Long,
    val p50: Double,
    val p90: Double,
    val p95: Double,
    val p99: Double
)

/**
 * Bucket histogramu
 */
data class HistogramBucket(
    val upperBound: Double,
    val count: Long,
    val cumulativeCount: Long
)

/**
 * Statystyki histogramu
 */
data class HistogramStats(
    val count: Long,
    val sum: Double,
    val average: Double,
    val p50: Double,
    val p90: Double,
    val p95: Double,
    val p99: Double,
    val buckets: List<HistogramBucket>
)

/**
 * Metryki kolejki SMS
 */
data class QueueMetrics(
    val totalMessages: Int,
    val queuedMessages: Int,
    val scheduledMessages: Int,
    val sendingMessages: Int,
    val sentMessages: Int,
    val failedMessages: Int,
    val averageProcessingTime: Long,
    val throughputPerHour: Int,
    val errorRate: Double
)

/**
 * Metryki wydajnościowe systemu
 */
data class PerformanceMetrics(
    val cpuUsagePercent: Long,
    val memoryUsagePercent: Long,
    val diskUsagePercent: Long,
    val networkIOBytes: Long,
    val databaseAverageQueryTime: Double,
    val apiAverageResponseTime: Double
)

/**
 * Raport metryk
 */
data class MetricsReport(
    val timestamp: Long,
    val queueMetrics: QueueMetrics,
    val performanceMetrics: PerformanceMetrics,
    val allMetrics: Map<String, Any>,
    val thresholds: Map<String, MetricThreshold>
)