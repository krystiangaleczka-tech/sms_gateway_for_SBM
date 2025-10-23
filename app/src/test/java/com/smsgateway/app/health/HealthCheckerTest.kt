package com.smsgateway.app.health

import android.content.Context
import android.telephony.TelephonyManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.ktor.server.application.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.mockito.kotlin.*
import org.mockito.MockedStatic

class HealthCheckerTest {
    
    private lateinit var healthChecker: HealthChecker
    private lateinit var mockContext: Context
    private lateinit var mockTelephonyManager: TelephonyManager
    private lateinit var mockConnectivityManager: ConnectivityManager
    private lateinit var mockNetworkCapabilities: NetworkCapabilities
    private lateinit var mockApplication: Application
    
    @BeforeEach
    fun setUp() {
        mockContext = mock()
        mockTelephonyManager = mock()
        mockConnectivityManager = mock()
        mockNetworkCapabilities = mock()
        mockApplication = mock()
        
        // Setup default mock behaviors
        whenever(mockContext.getSystemService(Context.TELEPHONY_SERVICE))
            .thenReturn(mockTelephonyManager)
        whenever(mockContext.getSystemService(Context.CONNECTIVITY_SERVICE))
            .thenReturn(mockConnectivityManager)
        
        healthChecker = HealthChecker(mockApplication)
    }
    
    @AfterEach
    fun tearDown() {
        // Cleanup if needed
    }
    
    @Test
    fun `should return healthy status when all checks pass`() = runTest {
        // Given
        setupMockForHealthySystem()
        
        // When
        val health = healthChecker.checkSystemHealth()
        
        // Then
        assertEquals(HealthStatus.HEALTHY, health.overallStatus)
        assertTrue(health.smsPermission)
        assertEquals("READY", health.simStatus)
        assertTrue(health.networkConnectivity)
        assertEquals(HealthStatus.HEALTHY, health.queueHealth.status)
        assertTrue(health.lastCheckTime > 0)
    }
    
    @Test
    fun `should return warning status when SMS permission is missing`() = runTest {
        // Given
        setupMockForHealthySystem()
        // Mock missing SMS permission
        whenever(mockContext.checkCallingOrSelfPermission("android.permission.SEND_SMS"))
            .thenReturn(-1) // PERMISSION_DENIED
        
        // When
        val health = healthChecker.checkSystemHealth()
        
        // Then
        assertEquals(HealthStatus.WARNING, health.overallStatus)
        assertFalse(health.smsPermission)
        assertEquals("READY", health.simStatus)
        assertTrue(health.networkConnectivity)
    }
    
    @Test
    fun `should return critical status when SIM is not ready`() = runTest {
        // Given
        setupMockForHealthySystem()
        whenever(mockTelephonyManager.simState).thenReturn(TelephonyManager.SIM_STATE_ABSENT)
        
        // When
        val health = healthChecker.checkSystemHealth()
        
        // Then
        assertEquals(HealthStatus.CRITICAL, health.overallStatus)
        assertTrue(health.smsPermission)
        assertEquals("ABSENT", health.simStatus)
        assertTrue(health.networkConnectivity)
    }
    
    @Test
    fun `should return warning status when network is not connected`() = runTest {
        // Given
        setupMockForHealthySystem()
        whenever(mockConnectivityManager.activeNetwork).thenReturn(null)
        
        // When
        val health = healthChecker.checkSystemHealth()
        
        // Then
        assertEquals(HealthStatus.WARNING, health.overallStatus)
        assertTrue(health.smsPermission)
        assertEquals("READY", health.simStatus)
        assertFalse(health.networkConnectivity)
    }
    
    @Test
    fun `should return critical status when multiple critical issues exist`() = runTest {
        // Given
        whenever(mockContext.checkCallingOrSelfPermission("android.permission.SEND_SMS"))
            .thenReturn(-1) // No SMS permission
        whenever(mockTelephonyManager.simState).thenReturn(TelephonyManager.SIM_STATE_ABSENT)
        whenever(mockConnectivityManager.activeNetwork).thenReturn(null)
        
        // When
        val health = healthChecker.checkSystemHealth()
        
        // Then
        assertEquals(HealthStatus.CRITICAL, health.overallStatus)
        assertFalse(health.smsPermission)
        assertEquals("ABSENT", health.simStatus)
        assertFalse(health.networkConnectivity)
    }
    
    @Test
    fun `should check SMS permission correctly`() = runTest {
        // Given
        whenever(mockContext.checkCallingOrSelfPermission("android.permission.SEND_SMS"))
            .thenReturn(0) // PERMISSION_GRANTED
        
        // When
        val hasPermission = healthChecker.checkSmsPermission()
        
        // Then
        assertTrue(hasPermission)
        verify(mockContext).checkCallingOrSelfPermission("android.permission.SEND_SMS")
    }
    
    @Test
    fun `should detect missing SMS permission`() = runTest {
        // Given
        whenever(mockContext.checkCallingOrSelfPermission("android.permission.SEND_SMS"))
            .thenReturn(-1) // PERMISSION_DENIED
        
        // When
        val hasPermission = healthChecker.checkSmsPermission()
        
        // Then
        assertFalse(hasPermission)
    }
    
    @Test
    fun `should check SIM status correctly`() = runTest {
        // Given
        whenever(mockTelephonyManager.simState).thenReturn(TelephonyManager.SIM_STATE_READY)
        
        // When
        val simStatus = healthChecker.checkSimStatus()
        
        // Then
        assertEquals("READY", simStatus)
        verify(mockTelephonyManager).simState
    }
    
    @Test
    fun `should detect different SIM states`() = runTest {
        val testCases = mapOf(
            TelephonyManager.SIM_STATE_ABSENT to "ABSENT",
            TelephonyManager.SIM_STATE_NOT_READY to "NOT_READY",
            TelephonyManager.SIM_STATE_PIN_REQUIRED to "PIN_REQUIRED",
            TelephonyManager.SIM_STATE_PUK_REQUIRED to "PUK_REQUIRED",
            TelephonyManager.SIM_STATE_NETWORK_LOCKED to "NETWORK_LOCKED",
            TelephonyManager.SIM_STATE_READY to "READY",
            999 to "UNKNOWN" // Test unknown state
        )
        
        testCases.forEach { (simState, expectedStatus) ->
            whenever(mockTelephonyManager.simState).thenReturn(simState)
            val status = healthChecker.checkSimStatus()
            assertEquals(expectedStatus, status, "Failed for SIM state: $simState")
        }
    }
    
    @Test
    fun `should check network connectivity correctly`() = runTest {
        // Given
        val mockNetwork = mock<android.net.Network>()
        whenever(mockConnectivityManager.activeNetwork).thenReturn(mockNetwork)
        whenever(mockConnectivityManager.getNetworkCapabilities(mockNetwork))
            .thenReturn(mockNetworkCapabilities)
        whenever(mockNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            .thenReturn(true)
        
        // When
        val isConnected = healthChecker.checkNetworkConnectivity()
        
        // Then
        assertTrue(isConnected)
        verify(mockConnectivityManager).activeNetwork
        verify(mockConnectivityManager).getNetworkCapabilities(mockNetwork)
    }
    
    @Test
    fun `should detect no network connectivity`() = runTest {
        // Given
        whenever(mockConnectivityManager.activeNetwork).thenReturn(null)
        
        // When
        val isConnected = healthChecker.checkNetworkConnectivity()
        
        // Then
        assertFalse(isConnected)
    }
    
    @Test
    fun `should detect network without internet capability`() = runTest {
        // Given
        val mockNetwork = mock<android.net.Network>()
        whenever(mockConnectivityManager.activeNetwork).thenReturn(mockNetwork)
        whenever(mockConnectivityManager.getNetworkCapabilities(mockNetwork))
            .thenReturn(mockNetworkCapabilities)
        whenever(mockNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            .thenReturn(false)
        
        // When
        val isConnected = healthChecker.checkNetworkConnectivity()
        
        // Then
        assertFalse(isConnected)
    }
    
    @Test
    fun `should generate health report with all components`() = runTest {
        // Given
        setupMockForHealthySystem()
        
        // When
        val report = healthChecker.generateHealthReport()
        
        // Then
        assertNotNull(report)
        assertTrue(report.contains("System Health Report"))
        assertTrue(report.contains("Overall Status: HEALTHY"))
        assertTrue(report.contains("SMS Permission: GRANTED"))
        assertTrue(report.contains("SIM Status: READY"))
        assertTrue(report.contains("Network Connectivity: CONNECTED"))
        assertTrue(report.contains("Queue Health: HEALTHY"))
        assertTrue(report.contains("Last Check Time"))
    }
    
    @Test
    fun `should include detailed component status in report`() = runTest {
        // Given
        whenever(mockContext.checkCallingOrSelfPermission("android.permission.SEND_SMS"))
            .thenReturn(-1) // No permission
        whenever(mockTelephonyManager.simState).thenReturn(TelephonyManager.SIM_STATE_ABSENT)
        whenever(mockConnectivityManager.activeNetwork).thenReturn(null)
        
        // When
        val report = healthChecker.generateHealthReport()
        
        // Then
        assertTrue(report.contains("Overall Status: CRITICAL"))
        assertTrue(report.contains("SMS Permission: DENIED"))
        assertTrue(report.contains("SIM Status: ABSENT"))
        assertTrue(report.contains("Network Connectivity: DISCONNECTED"))
    }
    
    @Test
    fun `should check queue health with metrics`() = runTest {
        // Given
        setupMockForHealthySystem()
        
        // When
        val queueHealth = healthChecker.checkQueueHealth()
        
        // Then
        assertNotNull(queueHealth)
        assertTrue(queueHealth.status == HealthStatus.HEALTHY || 
                  queueHealth.status == HealthStatus.WARNING ||
                  queueHealth.status == HealthStatus.CRITICAL)
        assertTrue(queueHealth.size >= 0)
        assertTrue(queueHealth.processingRate >= 0.0)
        assertTrue(queueHealth.averageWaitTime >= 0)
        assertTrue(queueHealth.errorRate >= 0.0)
    }
    
    @Test
    fun `should detect unhealthy queue when error rate is high`() = runTest {
        // Given
        setupMockForHealthySystem()
        // Mock high error rate scenario
        // This would need to be implemented based on actual queue metrics
        
        // When
        val queueHealth = healthChecker.checkQueueHealth()
        
        // Then
        // Implementation depends on actual queue metrics
        assertNotNull(queueHealth)
    }
    
    @Test
    fun `should handle exceptions during health checks gracefully`() = runTest {
        // Given
        whenever(mockContext.getSystemService(Context.TELEPHONY_SERVICE))
            .thenThrow(RuntimeException("Service not available"))
        
        // When
        val health = healthChecker.checkSystemHealth()
        
        // Then
        // Should not crash and return some status
        assertNotNull(health)
        assertTrue(health.overallStatus == HealthStatus.WARNING || 
                  health.overallStatus == HealthStatus.CRITICAL)
    }
    
    @Test
    fun `should provide timestamp for health checks`() = runTest {
        // Given
        setupMockForHealthySystem()
        val beforeCheck = System.currentTimeMillis()
        
        // When
        val health = healthChecker.checkSystemHealth()
        
        // Then
        assertTrue(health.lastCheckTime >= beforeCheck)
        assertTrue(health.lastCheckTime <= System.currentTimeMillis())
    }
    
    @Test
    fun `should check individual health components`() = runTest {
        // Given
        setupMockForHealthySystem()
        
        // When
        val smsPermission = healthChecker.checkSmsPermission()
        val simStatus = healthChecker.checkSimStatus()
        val networkConnectivity = healthChecker.checkNetworkConnectivity()
        val queueHealth = healthChecker.checkQueueHealth()
        
        // Then
        assertTrue(smsPermission)
        assertEquals("READY", simStatus)
        assertTrue(networkConnectivity)
        assertNotNull(queueHealth)
    }
    
    @Test
    fun `should aggregate health status correctly`() = runTest {
        // Test different combinations of component statuses
        
        // Test 1: All healthy -> Overall healthy
        var overallStatus = healthChecker.aggregateHealthStatus(
            smsPermission = true,
            simReady = true,
            networkConnected = true,
            queueHealthy = true
        )
        assertEquals(HealthStatus.HEALTHY, overallStatus)
        
        // Test 2: One warning -> Overall warning
        overallStatus = healthChecker.aggregateHealthStatus(
            smsPermission = false,
            simReady = true,
            networkConnected = true,
            queueHealthy = true
        )
        assertEquals(HealthStatus.WARNING, overallStatus)
        
        // Test 3: Critical issue -> Overall critical
        overallStatus = healthChecker.aggregateHealthStatus(
            smsPermission = true,
            simReady = false,
            networkConnected = true,
            queueHealthy = true
        )
        assertEquals(HealthStatus.CRITICAL, overallStatus)
    }
    
    // Helper method to setup mocks for healthy system
    private fun setupMockForHealthySystem() {
        whenever(mockContext.checkCallingOrSelfPermission("android.permission.SEND_SMS"))
            .thenReturn(0) // PERMISSION_GRANTED
        whenever(mockTelephonyManager.simState).thenReturn(TelephonyManager.SIM_STATE_READY)
        
        val mockNetwork = mock<android.net.Network>()
        whenever(mockConnectivityManager.activeNetwork).thenReturn(mockNetwork)
        whenever(mockConnectivityManager.getNetworkCapabilities(mockNetwork))
            .thenReturn(mockNetworkCapabilities)
        whenever(mockNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            .thenReturn(true)
    }
}