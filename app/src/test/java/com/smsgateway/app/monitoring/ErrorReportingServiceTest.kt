package com.smsgateway.app.monitoring

import android.content.Context
import com.smsgateway.app.api.ApiService
import com.smsgateway.app.api.Response
import com.smsgateway.app.logging.Logger
import com.smsgateway.app.monitoring.models.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Testy jednostkowe dla ErrorReportingService
 */
class ErrorReportingServiceTest {
    
    private lateinit var context: Context
    private lateinit var apiService: ApiService
    private lateinit var logger: Logger
    private lateinit var errorReportingService: ErrorReportingService
    
    @Before
    fun setup() {
        context = mockk(relaxed = true)
        apiService = mockk(relaxed = true)
        logger = mockk(relaxed = true)
        
        errorReportingService = ErrorReportingService(
            context = context,
            apiService = apiService
        )
        
        // Domyślna inicjalizacja
        errorReportingService.initialize(
            enableCrashlytics = false,
            enableBackendReporting = true,
            maxRetries = 3,
            retryDelayMs = 100L
        )
    }
    
    @Test
    fun `initialize should set correct configuration`() {
        // Given
        val errorReportingService = ErrorReportingService(context, apiService)
        
        // When
        errorReportingService.initialize(
            enableCrashlytics = true,
            enableBackendReporting = false,
            maxRetries = 5,
            retryDelayMs = 2000L
        )
        
        // Then
        val status = errorReportingService.getReportingStatus()
        assertEquals(true, status["crashlytics"])
        assertEquals(false, status["backend"])
    }
    
    @Test
    fun `reportError should return success when backend reporting succeeds`() = runTest {
        // Given
        val error = AppError(
            id = "error-123",
            type = ErrorType.SYSTEM_ERROR,
            severity = ErrorSeverity.HIGH,
            message = "Test error message",
            timestamp = System.currentTimeMillis()
        )
        
        coEvery { apiService.reportError(error) } returns Response.success(Unit)
        
        // When
        val result = errorReportingService.reportError(error)
        
        // Then
        assertEquals("SUCCESS", result.status)
        assertEquals("error-123", result.reportId)
        assertEquals("Error reported to backend successfully", result.message)
        
        coVerify { apiService.reportError(error) }
    }
    
    @Test
    fun `reportError should return failed when backend reporting fails`() = runTest {
        // Given
        val error = AppError(
            id = "error-123",
            type = ErrorType.SYSTEM_ERROR,
            severity = ErrorSeverity.HIGH,
            message = "Test error message",
            timestamp = System.currentTimeMillis()
        )
        
        coEvery { apiService.reportError(error) } returns Response.error("Network error")
        
        // When
        val result = errorReportingService.reportError(error)
        
        // Then
        assertEquals("FAILED", result.status)
        assertEquals("error-123", result.reportId)
        assertTrue(result.message!!.contains("Failed to report to backend"))
        
        coVerify { apiService.reportError(error) }
    }
    
    @Test
    fun `reportError should retry on failure`() = runTest {
        // Given
        val error = AppError(
            id = "error-123",
            type = ErrorType.SYSTEM_ERROR,
            severity = ErrorSeverity.HIGH,
            message = "Test error message",
            timestamp = System.currentTimeMillis()
        )
        
        // Pierwsze dwie próby nieudane, trzecia udana
        coEvery { apiService.reportError(error) } returnsMany 
            listOf(Response.error("Network error"), Response.error("Timeout"), Response.success(Unit))
        
        // When
        val result = errorReportingService.reportError(error)
        
        // Then
        assertEquals("SUCCESS", result.status)
        coVerify(exactly = 3) { apiService.reportError(error) }
    }
    
    @Test
    fun `reportError with ReportableError should include user context`() = runTest {
        // Given
        val baseError = AppError(
            id = "error-123",
            type = ErrorType.UI_ERROR,
            severity = ErrorSeverity.MEDIUM,
            message = "Base error message",
            timestamp = System.currentTimeMillis()
        )
        
        val reportableError = ReportableError(
            error = baseError,
            userDescription = "User description",
            userEmail = "user@example.com",
            includeLogs = true,
            includeDeviceInfo = false
        )
        
        coEvery { apiService.reportError(any()) } returns Response.success(Unit)
        
        // When
        val result = errorReportingService.reportError(reportableError)
        
        // Then
        assertEquals("SUCCESS", result.status)
        
        // Sprawdzenie, czy błąd został wzbogacony o kontekst użytkownika
        coVerify {
            apiService.reportError(match { error ->
                error.context.containsKey("user_description") &&
                error.context["user_description"] == "User description" &&
                error.context.containsKey("user_email") &&
                error.context["user_email"] == "user@example.com" &&
                error.context["include_logs"] == "true" &&
                error.context["include_device_info"] == "false"
            })
        }
    }
    
    @Test
    fun `reportCrash should create appropriate error and report it`() = runTest {
        // Given
        val throwable = RuntimeException("Test crash")
        val context = mapOf("additional_info" to "test_value")
        
        coEvery { apiService.reportError(any()) } returns Response.success(Unit)
        
        // When
        val result = errorReportingService.reportCrash(throwable, context)
        
        // Then
        assertEquals("SUCCESS", result.status)
        
        // Sprawdzenie, czy błąd został utworzony z odpowiednimi danymi
        coVerify {
            apiService.reportError(match { error ->
                error.type == ErrorType.SYSTEM_ERROR &&
                error.severity == ErrorSeverity.CRITICAL &&
                error.message!!.contains("Application crash") &&
                error.context.containsKey("crash_thread") &&
                error.context.containsKey("crash_time") &&
                error.context["additional_info"] == "test_value" &&
                error.stackTrace!!.isNotEmpty()
            })
        }
    }
    
    @Test
    fun `reportFeedback should create appropriate error and report it`() = runTest {
        // Given
        val feedback = "This is a user feedback"
        val userEmail = "user@example.com"
        val context = mapOf("feature" to "sms_sending")
        
        coEvery { apiService.reportError(any()) } returns Response.success(Unit)
        
        // When
        val result = errorReportingService.reportFeedback(feedback, userEmail, context)
        
        // Then
        assertEquals("SUCCESS", result.status)
        
        // Sprawdzenie, czy błąd został utworzony z odpowiednimi danymi
        coVerify {
            apiService.reportError(match { error ->
                error.type == ErrorType.UI_ERROR &&
                error.severity == ErrorSeverity.LOW &&
                error.message!!.contains("User feedback") &&
                error.context.containsKey("user_email") &&
                error.context["user_email"] == userEmail &&
                error.context.containsKey("feedback_type") &&
                error.context["feedback_type"] == "user_feedback" &&
                error.context["feature"] == "sms_sending"
            })
        }
    }
    
    @Test
    fun `setReportingServiceEnabled should update service status`() {
        // Given
        val initialStatus = errorReportingService.getReportingStatus()
        assertEquals(false, initialStatus["crashlytics"])
        assertEquals(true, initialStatus["backend"])
        
        // When
        errorReportingService.setReportingServiceEnabled("crashlytics", true)
        errorReportingService.setReportingServiceEnabled("backend", false)
        
        // Then
        val updatedStatus = errorReportingService.getReportingStatus()
        assertEquals(true, updatedStatus["crashlytics"])
        assertEquals(false, updatedStatus["backend"])
    }
    
    @Test
    fun `getErrors should return empty list when API fails`() = runTest {
        // Given
        coEvery { apiService.getErrors(50) } returns Response.error("Network error")
        
        // When
        val errors = errorReportingService.getErrors(50)
        
        // Then
        assertTrue(errors.isEmpty())
        coVerify { apiService.getErrors(50) }
    }
    
    @Test
    fun `getSystemMetrics should return null when API fails`() = runTest {
        // Given
        coEvery { apiService.getSystemMetrics() } returns Response.error("Network error")
        
        // When
        val metrics = errorReportingService.getSystemMetrics()
        
        // Then
        assertEquals(null, metrics)
        coVerify { apiService.getSystemMetrics() }
    }
    
    @Test
    fun `getSystemHealth should return null when API fails`() = runTest {
        // Given
        coEvery { apiService.getSystemHealth() } returns Response.error("Network error")
        
        // When
        val health = errorReportingService.getSystemHealth()
        
        // Then
        assertEquals(null, health)
        coVerify { apiService.getSystemHealth() }
    }
    
    @Test
    fun `getSystemAlerts should return empty list when API fails`() = runTest {
        // Given
        coEvery { apiService.getSystemAlerts(20) } returns Response.error("Network error")
        
        // When
        val alerts = errorReportingService.getSystemAlerts(20)
        
        // Then
        assertTrue(alerts.isEmpty())
        coVerify { apiService.getSystemAlerts(20) }
    }
    
    @Test
    fun `getErrorDetails should return null when API fails`() = runTest {
        // Given
        val errorId = "error-123"
        coEvery { apiService.getError(errorId) } returns Response.error("Network error")
        
        // When
        val error = errorReportingService.getErrorDetails(errorId)
        
        // Then
        assertEquals(null, error)
        coVerify { apiService.getError(errorId) }
    }
}