package com.smsgateway.app.monitoring.repositories

import com.smsgateway.app.api.ApiService
import com.smsgateway.app.monitoring.models.MetricsType
import com.smsgateway.app.monitoring.models.SystemMetrics
import com.smsgateway.app.monitoring.models.PerformanceMetric
import com.smsgateway.app.monitoring.models.HealthStatus
import com.smsgateway.app.monitoring.models.SystemAlert
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Testy jednostkowe dla MetricsRepository
 */
class MetricsRepositoryTest {
    
    private lateinit var apiService: ApiService
    private lateinit var metricsRepository: MetricsRepository
    
    @Before
    fun setup() {
        apiService = mockk(relaxed = true)
        metricsRepository = MetricsRepository(apiService)
    }
    
    @Test
    fun `getSystemMetrics should return metrics from API`() = runTest {
        // Given
        val systemMetrics = SystemMetrics(
            timestamp = System.currentTimeMillis(),
            cpuUsage = 75.5f,
            memoryUsage = 60.2f,
            diskUsage = 45.8f,
            networkUsage = 30.0f,
            activeConnections = 120,
            queuedMessages = 15,
            processedMessages = 3500,
            failedMessages = 12
        )
        
        coEvery { apiService.getSystemMetrics() } returns systemMetrics
        
        // When
        val result = metricsRepository.getSystemMetrics()
        
        // Then
        assertNotNull(result)
        assertEquals(systemMetrics.timestamp, result.timestamp)
        assertEquals(systemMetrics.cpuUsage, result.cpuUsage)
        assertEquals(systemMetrics.memoryUsage, result.memoryUsage)
        assertEquals(systemMetrics.diskUsage, result.diskUsage)
        assertEquals(systemMetrics.networkUsage, result.networkUsage)
        assertEquals(systemMetrics.activeConnections, result.activeConnections)
        assertEquals(systemMetrics.queuedMessages, result.queuedMessages)
        assertEquals(systemMetrics.processedMessages, result.processedMessages)
        assertEquals(systemMetrics.failedMessages, result.failedMessages)
        
        coVerify { apiService.getSystemMetrics() }
    }
    
    @Test
    fun `getSystemMetrics should return null on API failure`() = runTest {
        // Given
        coEvery { apiService.getSystemMetrics() } throws Exception("API error")
        
        // When
        val result = metricsRepository.getSystemMetrics()
        
        // Then
        assertEquals(null, result)
        
        coVerify { apiService.getSystemMetrics() }
    }
    
    @Test
    fun `getMetricsHistory should return metrics history from API`() = runTest {
        // Given
        val startTime = 1000000L
        val endTime = 2000000L
        val interval = 300L // 5 minutes
        
        val metricsList = listOf(
            SystemMetrics(
                timestamp = 1100000L,
                cpuUsage = 70.0f,
                memoryUsage = 60.0f,
                diskUsage = 45.0f,
                networkUsage = 30.0f,
                activeConnections = 100,
                queuedMessages = 10,
                processedMessages = 3000,
                failedMessages = 10
            ),
            SystemMetrics(
                timestamp = 1200000L,
                cpuUsage = 75.0f,
                memoryUsage = 62.0f,
                diskUsage = 45.5f,
                networkUsage = 32.0f,
                activeConnections = 110,
                queuedMessages = 12,
                processedMessages = 3200,
                failedMessages = 11
            )
        )
        
        coEvery { 
            apiService.getMetricsHistory(startTime, endTime, interval) 
        } returns flowOf(*metricsList.toTypedArray())
        
        // When
        val result = metricsRepository.getMetricsHistory(startTime, endTime, interval).toList()
        
        // Then
        assertEquals(2, result.size)
        assertEquals(metricsList[0].timestamp, result[0].timestamp)
        assertEquals(metricsList[1].timestamp, result[1].timestamp)
        
        coVerify { apiService.getMetricsHistory(startTime, endTime, interval) }
    }
    
    @Test
    fun `getPerformanceMetrics should return performance metrics from API`() = runTest {
        // Given
        val metricsType = MetricsType.SMS_PERFORMANCE
        val startTime = 1000000L
        val endTime = 2000000L
        
        val performanceMetrics = listOf(
            PerformanceMetric(
                name = "sms_send_time",
                value = 150.5f,
                unit = "ms",
                timestamp = 1100000L,
                tags = mapOf("operator" to "orange")
            ),
            PerformanceMetric(
                name = "sms_send_time",
                value = 145.2f,
                unit = "ms",
                timestamp = 1200000L,
                tags = mapOf("operator" to "play")
            )
        )
        
        coEvery { 
            apiService.getPerformanceMetrics(metricsType, startTime, endTime) 
        } returns flowOf(*performanceMetrics.toTypedArray())
        
        // When
        val result = metricsRepository.getPerformanceMetrics(metricsType, startTime, endTime).toList()
        
        // Then
        assertEquals(2, result.size)
        assertEquals("sms_send_time", result[0].name)
        assertEquals(150.5f, result[0].value)
        assertEquals("ms", result[0].unit)
        assertEquals(1100000L, result[0].timestamp)
        assertEquals("orange", result[0].tags["operator"])
        
        coVerify { apiService.getPerformanceMetrics(metricsType, startTime, endTime) }
    }
    
    @Test
    fun `getSystemHealth should return health status from API`() = runTest {
        // Given
        val healthStatus = HealthStatus(
            status = "HEALTHY",
            timestamp = System.currentTimeMillis(),
            components = mapOf(
                "database" to mapOf(
                    "status" to "HEALTHY",
                    "responseTime" to "15ms"
                ),
                "sms_gateway" to mapOf(
                    "status" to "HEALTHY",
                    "activeConnections" to "50"
                ),
                "queue" to mapOf(
                    "status" to "WARNING",
                    "queuedMessages" to "25"
                )
            ),
            uptime = 86400000L, // 24 hours in milliseconds
            version = "1.0.0"
        )
        
        coEvery { apiService.getSystemHealth() } returns healthStatus
        
        // When
        val result = metricsRepository.getSystemHealth()
        
        // Then
        assertNotNull(result)
        assertEquals("HEALTHY", result.status)
        assertEquals(healthStatus.timestamp, result.timestamp)
        assertEquals(3, result.components.size)
        assertEquals("HEALTHY", result.components["database"]?.get("status"))
        assertEquals("WARNING", result.components["queue"]?.get("status"))
        assertEquals(86400000L, result.uptime)
        assertEquals("1.0.0", result.version)
        
        coVerify { apiService.getSystemHealth() }
    }
    
    @Test
    fun `getSystemHealth should return null on API failure`() = runTest {
        // Given
        coEvery { apiService.getSystemHealth() } throws Exception("Health check failed")
        
        // When
        val result = metricsRepository.getSystemHealth()
        
        // Then
        assertEquals(null, result)
        
        coVerify { apiService.getSystemHealth() }
    }
    
    @Test
    fun `getSystemAlerts should return alerts from API`() = runTest {
        // Given
        val activeOnly = true
        val severity = "HIGH"
        
        val alerts = listOf(
            SystemAlert(
                id = "alert-123",
                title = "High CPU Usage",
                description = "CPU usage is above 90%",
                severity = "HIGH",
                status = "ACTIVE",
                timestamp = System.currentTimeMillis() - 3600000L, // 1 hour ago
                source = "system_monitor",
                metrics = mapOf(
                    "cpu_usage" to "92%"
                ),
                acknowledged = false
            ),
            SystemAlert(
                id = "alert-456",
                title = "Memory Usage Warning",
                description = "Memory usage is above 80%",
                severity = "MEDIUM",
                status = "ACTIVE",
                timestamp = System.currentTimeMillis() - 7200000L, // 2 hours ago
                source = "system_monitor",
                metrics = mapOf(
                    "memory_usage" to "85%"
                ),
                acknowledged = true
            )
        )
        
        coEvery { apiService.getSystemAlerts(activeOnly, severity) } returns flowOf(*alerts.toTypedArray())
        
        // When
        val result = metricsRepository.getSystemAlerts(activeOnly, severity).toList()
        
        // Then
        assertEquals(2, result.size)
        assertEquals("alert-123", result[0].id)
        assertEquals("High CPU Usage", result[0].title)
        assertEquals("HIGH", result[0].severity)
        assertEquals("ACTIVE", result[0].status)
        assertEquals(false, result[0].acknowledged)
        
        coVerify { apiService.getSystemAlerts(activeOnly, severity) }
    }
    
    @Test
    fun `acknowledgeAlert should call API acknowledge method`() = runTest {
        // Given
        val alertId = "alert-123"
        val acknowledgedBy = "admin"
        
        coEvery { apiService.acknowledgeAlert(alertId, acknowledgedBy) } returns true
        
        // When
        val result = metricsRepository.acknowledgeAlert(alertId, acknowledgedBy)
        
        // Then
        assertTrue(result)
        
        coVerify { apiService.acknowledgeAlert(alertId, acknowledgedBy) }
    }
    
    @Test
    fun `acknowledgeAlert should handle API failure`() = runTest {
        // Given
        val alertId = "alert-123"
        val acknowledgedBy = "admin"
        
        coEvery { apiService.acknowledgeAlert(alertId, acknowledgedBy) } throws Exception("Acknowledge failed")
        
        // When
        val result = metricsRepository.acknowledgeAlert(alertId, acknowledgedBy)
        
        // Then
        assertEquals(false, result)
        
        coVerify { apiService.acknowledgeAlert(alertId, acknowledgedBy) }
    }
    
    @Test
    fun `resolveAlert should call API resolve method`() = runTest {
        // Given
        val alertId = "alert-123"
        val resolvedBy = "admin"
        val resolution = "Restarted the service"
        
        coEvery { apiService.resolveAlert(alertId, resolvedBy, resolution) } returns true
        
        // When
        val result = metricsRepository.resolveAlert(alertId, resolvedBy, resolution)
        
        // Then
        assertTrue(result)
        
        coVerify { apiService.resolveAlert(alertId, resolvedBy, resolution) }
    }
    
    @Test
    fun `resolveAlert should handle API failure`() = runTest {
        // Given
        val alertId = "alert-123"
        val resolvedBy = "admin"
        val resolution = "Restarted the service"
        
        coEvery { apiService.resolveAlert(alertId, resolvedBy, resolution) } throws Exception("Resolve failed")
        
        // When
        val result = metricsRepository.resolveAlert(alertId, resolvedBy, resolution)
        
        // Then
        assertEquals(false, result)
        
        coVerify { apiService.resolveAlert(alertId, resolvedBy, resolution) }
    }
    
    @Test
    fun `getCustomMetrics should return custom metrics from API`() = runTest {
        // Given
        val metricName = "custom_sms_metric"
        val startTime = 1000000L
        val endTime = 2000000L
        
        val metrics = listOf(
            PerformanceMetric(
                name = metricName,
                value = 100.0f,
                unit = "count",
                timestamp = 1100000L,
                tags = mapOf("region" to "eu")
            ),
            PerformanceMetric(
                name = metricName,
                value = 120.0f,
                unit = "count",
                timestamp = 1200000L,
                tags = mapOf("region" to "us")
            )
        )
        
        coEvery { apiService.getCustomMetrics(metricName, startTime, endTime) } returns flowOf(*metrics.toTypedArray())
        
        // When
        val result = metricsRepository.getCustomMetrics(metricName, startTime, endTime).toList()
        
        // Then
        assertEquals(2, result.size)
        assertEquals(metricName, result[0].name)
        assertEquals(100.0f, result[0].value)
        assertEquals("count", result[0].unit)
        assertEquals("eu", result[0].tags["region"])
        
        coVerify { apiService.getCustomMetrics(metricName, startTime, endTime) }
    }
    
    @Test
    fun `getAlertCount should return alert count from API`() = runTest {
        // Given
        val activeOnly = true
        val severity = "HIGH"
        val expectedCount = 5
        
        coEvery { apiService.getAlertCount(activeOnly, severity) } returns expectedCount
        
        // When
        val result = metricsRepository.getAlertCount(activeOnly, severity)
        
        // Then
        assertEquals(expectedCount, result)
        
        coVerify { apiService.getAlertCount(activeOnly, severity) }
    }
    
    @Test
    fun `getAlertCount should handle API failure`() = runTest {
        // Given
        val activeOnly = true
        val severity = "HIGH"
        
        coEvery { apiService.getAlertCount(activeOnly, severity) } throws Exception("Count failed")
        
        // When
        val result = metricsRepository.getAlertCount(activeOnly, severity)
        
        // Then
        assertEquals(0, result)
        
        coVerify { apiService.getAlertCount(activeOnly, severity) }
    }
    
    @Test
    fun `createCustomAlert should call API create method`() = runTest {
        // Given
        val title = "Custom Alert"
        val description = "Custom alert description"
        val severity = "MEDIUM"
        val source = "custom_monitor"
        val metrics = mapOf("custom_value" to "123")
        
        val expectedAlert = SystemAlert(
            id = "custom-alert-123",
            title = title,
            description = description,
            severity = severity,
            status = "ACTIVE",
            timestamp = System.currentTimeMillis(),
            source = source,
            metrics = metrics,
            acknowledged = false
        )
        
        coEvery { apiService.createCustomAlert(title, description, severity, source, metrics) } returns expectedAlert
        
        // When
        val result = metricsRepository.createCustomAlert(title, description, severity, source, metrics)
        
        // Then
        assertNotNull(result)
        assertEquals("custom-alert-123", result!!.id)
        assertEquals(title, result.title)
        assertEquals(description, result.description)
        assertEquals(severity, result.severity)
        assertEquals(source, result.source)
        assertEquals(metrics, result.metrics)
        
        coVerify { apiService.createCustomAlert(title, description, severity, source, metrics) }
    }
    
    @Test
    fun `createCustomAlert should handle API failure`() = runTest {
        // Given
        val title = "Custom Alert"
        val description = "Custom alert description"
        val severity = "MEDIUM"
        val source = "custom_monitor"
        val metrics = mapOf("custom_value" to "123")
        
        coEvery { apiService.createCustomAlert(title, description, severity, source, metrics) } throws Exception("Create failed")
        
        // When
        val result = metricsRepository.createCustomAlert(title, description, severity, source, metrics)
        
        // Then
        assertEquals(null, result)
        
        coVerify { apiService.createCustomAlert(title, description, severity, source, metrics) }
    }
}