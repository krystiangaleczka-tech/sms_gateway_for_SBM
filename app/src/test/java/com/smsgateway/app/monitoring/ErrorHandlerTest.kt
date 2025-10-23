package com.smsgateway.app.monitoring

import com.smsgateway.app.logging.Logger
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Testy jednostkowe dla ErrorHandler
 */
class ErrorHandlerTest {
    
    private lateinit var logger: Logger
    private lateinit var errorReportingService: ErrorReportingService
    private lateinit var errorHandler: ErrorHandler
    
    @Before
    fun setup() {
        logger = mockk(relaxed = true)
        errorReportingService = mockk(relaxed = true)
        
        errorHandler = ErrorHandler.getInstance(logger, errorReportingService)
        
        // Reset singleton before each test
        ErrorHandler.resetInstance()
    }
    
    @Test
    fun `getInstance should return singleton instance`() {
        // Given
        val instance1 = ErrorHandler.getInstance(logger, errorReportingService)
        val instance2 = ErrorHandler.getInstance()
        
        // Then
        assertEquals(instance1, instance2)
    }
    
    @Test
    fun `handleError should log error and report to service`() = runTest {
        // Given
        val error = AppError(
            id = "error-123",
            type = ErrorType.SYSTEM_ERROR,
            severity = ErrorSeverity.HIGH,
            message = "Test error message",
            timestamp = System.currentTimeMillis()
        )
        
        coEvery { errorReportingService.reportError(error) } returns ErrorReportResult(
            reportId = "report-123",
            status = "SUCCESS",
            message = "Error reported successfully"
        )
        
        // When
        val result = errorHandler.handleError(error)
        
        // Then
        assertEquals("SUCCESS", result.status)
        assertEquals("report-123", result.reportId)
        
        coVerify { logger.error("Error handled: ${error.type}", error) }
        coVerify { errorReportingService.reportError(error) }
    }
    
    @Test
    fun `handleError should create error from exception if not provided`() = runTest {
        // Given
        val exception = RuntimeException("Test exception")
        val context = mapOf("additional_info" to "test_value")
        
        coEvery { errorReportingService.reportError(any()) } returns ErrorReportResult(
            reportId = "report-123",
            status = "SUCCESS",
            message = "Error reported successfully"
        )
        
        // When
        val result = errorHandler.handleError(exception, context)
        
        // Then
        assertEquals("SUCCESS", result.status)
        assertEquals("report-123", result.reportId)
        
        // Sprawdzenie, czy błąd został utworzony z odpowiednimi danymi
        coVerify {
            logger.error(match { message ->
                message.contains("Error handled: SYSTEM_ERROR")
            }, any())
            
            errorReportingService.reportError(match { error ->
                error.type == ErrorType.SYSTEM_ERROR &&
                error.severity == ErrorSeverity.MEDIUM &&
                error.message!!.contains("Test exception") &&
                error.context["additional_info"] == "test_value" &&
                error.stackTrace!!.isNotEmpty()
            })
        }
    }
    
    @Test
    fun `handleError should not report if severity is LOW`() = runTest {
        // Given
        val error = AppError(
            id = "error-123",
            type = ErrorType.UI_ERROR,
            severity = ErrorSeverity.LOW,
            message = "Low priority error",
            timestamp = System.currentTimeMillis()
        )
        
        // When
        val result = errorHandler.handleError(error)
        
        // Then
        assertEquals("SUCCESS", result.status) // Sukces lokalny, nie raportowany
        assertEquals(error.id, result.reportId)
        
        coVerify { logger.error("Error handled: ${error.type}", error) }
        coVerify(exactly = 0) { errorReportingService.reportError(any()) }
    }
    
    @Test
    fun `handleCriticalError should always report error`() = runTest {
        // Given
        val error = AppError(
            id = "error-123",
            type = ErrorType.SYSTEM_ERROR,
            severity = ErrorSeverity.CRITICAL,
            message = "Critical error",
            timestamp = System.currentTimeMillis()
        )
        
        coEvery { errorReportingService.reportError(error) } returns ErrorReportResult(
            reportId = "report-123",
            status = "SUCCESS",
            message = "Error reported successfully"
        )
        
        // When
        val result = errorHandler.handleCriticalError(error)
        
        // Then
        assertEquals("SUCCESS", result.status)
        assertEquals("report-123", result.reportId)
        
        coVerify { logger.error("Critical error handled: ${error.type}", error) }
        coVerify { errorReportingService.reportError(error) }
    }
    
    @Test
    fun `handleUserError should report with additional context`() = runTest {
        // Given
        val userError = UserError(
            id = "user-error-123",
            title = "User reported issue",
            description = "Detailed description",
            userEmail = "user@example.com",
            timestamp = System.currentTimeMillis(),
            stepsToReproduce = listOf("Step 1", "Step 2", "Step 3")
        )
        
        coEvery { errorReportingService.reportError(any()) } returns ErrorReportResult(
            reportId = "report-123",
            status = "SUCCESS",
            message = "Error reported successfully"
        )
        
        // When
        val result = errorHandler.handleUserError(userError)
        
        // Then
        assertEquals("SUCCESS", result.status)
        assertEquals("report-123", result.reportId)
        
        coVerify { 
            logger.info("User error handled: ${userError.title}")
            errorReportingService.reportError(match { error ->
                error.type == ErrorType.USER_REPORTED &&
                error.severity == ErrorSeverity.MEDIUM &&
                error.message == userError.title &&
                error.context["user_email"] == userError.userEmail &&
                error.context["steps"] == "Step 1, Step 2, Step 3"
            })
        }
    }
    
    @Test
    fun `handleCrash should report critical error`() = runTest {
        // Given
        val throwable = RuntimeException("Application crash")
        val context = mapOf("additional_info" to "test_value")
        
        coEvery { errorReportingService.reportError(any()) } returns ErrorReportResult(
            reportId = "report-123",
            status = "SUCCESS",
            message = "Error reported successfully"
        )
        
        // When
        val result = errorHandler.handleCrash(throwable, context)
        
        // Then
        assertEquals("SUCCESS", result.status)
        assertEquals("report-123", result.reportId)
        
        coVerify { 
            logger.error("Application crash handled", throwable)
            errorReportingService.reportError(match { error ->
                error.type == ErrorType.SYSTEM_ERROR &&
                error.severity == ErrorSeverity.CRITICAL &&
                error.message!!.contains("Application crash") &&
                error.context["additional_info"] == "test_value"
            })
        }
    }
    
    @Test
    fun `handleFeedback should report low priority error`() = runTest {
        // Given
        val feedback = "User feedback message"
        val userEmail = "user@example.com"
        val context = mapOf("feature" to "sms_sending")
        
        coEvery { errorReportingService.reportError(any()) } returns ErrorReportResult(
            reportId = "report-123",
            status = "SUCCESS",
            message = "Error reported successfully"
        )
        
        // When
        val result = errorHandler.handleFeedback(feedback, userEmail, context)
        
        // Then
        assertEquals("SUCCESS", result.status)
        assertEquals("report-123", result.reportId)
        
        coVerify { 
            logger.info("User feedback handled")
            errorReportingService.reportError(match { error ->
                error.type == ErrorType.UI_ERROR &&
                error.severity == ErrorSeverity.LOW &&
                error.message!!.contains("User feedback") &&
                error.context["user_email"] == userEmail &&
                error.context["feature"] == "sms_sending"
            })
        }
    }
    
    @Test
    fun `setReportingEnabled should update reporting status`() {
        // Given
        val initialStatus = errorHandler.isReportingEnabled()
        assertTrue(initialStatus) // Domyślnie włączone
        
        // When
        errorHandler.setReportingEnabled(false)
        
        // Then
        assertFalse(errorHandler.isReportingEnabled())
        
        // When
        errorHandler.setReportingEnabled(true)
        
        // Then
        assertTrue(errorHandler.isReportingEnabled())
    }
    
    @Test
    fun `handleError should not report when reporting is disabled`() = runTest {
        // Given
        errorHandler.setReportingEnabled(false)
        
        val error = AppError(
            id = "error-123",
            type = ErrorType.SYSTEM_ERROR,
            severity = ErrorSeverity.HIGH,
            message = "Test error message",
            timestamp = System.currentTimeMillis()
        )
        
        // When
        val result = errorHandler.handleError(error)
        
        // Then
        assertEquals("SUCCESS", result.status) // Sukces lokalny, nie raportowany
        assertEquals(error.id, result.reportId)
        
        coVerify { logger.error("Error handled: ${error.type}", error) }
        coVerify(exactly = 0) { errorReportingService.reportError(any()) }
    }
    
    @Test
    fun `createError should create error with proper data`() {
        // Given
        val type = ErrorType.NETWORK_ERROR
        val severity = ErrorSeverity.MEDIUM
        val message = "Network error occurred"
        val cause = "Connection timeout"
        val context = mapOf("url" to "https://api.example.com")
        
        // When
        val error = errorHandler.createError(type, severity, message, cause, context)
        
        // Then
        assertNotNull(error.id)
        assertEquals(type, error.type)
        assertEquals(severity, error.severity)
        assertEquals(message, error.message)
        assertEquals(cause, error.cause)
        assertEquals(context, error.context)
        assertTrue(error.stackTrace!!.isNotEmpty())
    }
    
    @Test
    fun `createUserError should create user error with proper data`() {
        // Given
        val title = "User issue"
        val description = "Detailed description"
        val userEmail = "user@example.com"
        val stepsToReproduce = listOf("Step 1", "Step 2")
        
        // When
        val userError = errorHandler.createUserError(title, description, userEmail, stepsToReproduce)
        
        // Then
        assertNotNull(userError.id)
        assertEquals(title, userError.title)
        assertEquals(description, userError.description)
        assertEquals(userEmail, userError.userEmail)
        assertEquals(stepsToReproduce, userError.stepsToReproduce)
    }
}