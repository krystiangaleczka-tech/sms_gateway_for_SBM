package com.smsgateway.app.ui.viewmodels

import com.smsgateway.app.monitoring.ErrorRepository
import com.smsgateway.app.monitoring.models.AppError
import com.smsgateway.app.monitoring.models.ErrorSeverity
import com.smsgateway.app.monitoring.models.ErrorType
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Testy jednostkowe dla ErrorViewModel
 */
@ExperimentalCoroutinesApi
class ErrorViewModelTest {
    
    private lateinit var errorRepository: ErrorRepository
    private lateinit var errorViewModel: ErrorViewModel
    private val testDispatcher = UnconfinedTestDispatcher()
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        errorRepository = mockk(relaxed = true)
        errorViewModel = ErrorViewModel(errorRepository)
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `loadErrors should update errors state when successful`() = runTest {
        // Given
        val errors = listOf(
            AppError(
                id = "error-1",
                type = ErrorType.SYSTEM_ERROR,
                severity = ErrorSeverity.HIGH,
                message = "System error 1",
                timestamp = System.currentTimeMillis()
            ),
            AppError(
                id = "error-2",
                type = ErrorType.NETWORK_ERROR,
                severity = ErrorSeverity.MEDIUM,
                message = "Network error 1",
                timestamp = System.currentTimeMillis()
            )
        )
        
        coEvery { 
            errorRepository.getErrors(
                limit = 20,
                offset = 0,
                type = null,
                severity = null,
                startDate = null,
                endDate = null
            ) 
        } returns flowOf(*errors.toTypedArray())
        
        // When
        errorViewModel.loadErrors()
        
        // Then
        val resultErrors = errorViewModel.errors.value
        assertEquals(2, resultErrors.size)
        assertEquals("error-1", resultErrors[0].id)
        assertEquals("error-2", resultErrors[1].id)
        assertEquals(false, errorViewModel.isLoading.value)
        assertEquals(null, errorViewModel.errorMessage.value)
        
        coVerify { 
            errorRepository.getErrors(
                limit = 20,
                offset = 0,
                type = null,
                severity = null,
                startDate = null,
                endDate = null
            ) 
        }
    }
    
    @Test
    fun `loadErrors should handle errors gracefully`() = runTest {
        // Given
        coEvery { 
            errorRepository.getErrors(
                limit = 20,
                offset = 0,
                type = null,
                severity = null,
                startDate = null,
                endDate = null
            ) 
        } throws Exception("Network error")
        
        // When
        errorViewModel.loadErrors()
        
        // Then
        assertEquals(0, errorViewModel.errors.value.size)
        assertEquals(false, errorViewModel.isLoading.value)
        assertNotNull(errorViewModel.errorMessage.value)
        assertTrue(errorViewModel.errorMessage.value!!.contains("Failed to load errors"))
    }
    
    @Test
    fun `loadErrorDetails should update selectedError state when successful`() = runTest {
        // Given
        val errorId = "error-123"
        val error = AppError(
            id = errorId,
            type = ErrorType.SYSTEM_ERROR,
            severity = ErrorSeverity.HIGH,
            message = "Test error message",
            timestamp = System.currentTimeMillis()
        )
        
        coEvery { errorRepository.getError(errorId) } returns error
        
        // When
        errorViewModel.loadErrorDetails(errorId)
        
        // Then
        val selectedError = errorViewModel.selectedError.value
        assertNotNull(selectedError)
        assertEquals(errorId, selectedError!!.id)
        assertEquals(ErrorType.SYSTEM_ERROR, selectedError.type)
        assertEquals(ErrorSeverity.HIGH, selectedError.severity)
        assertEquals("Test error message", selectedError.message)
        assertEquals(false, errorViewModel.isLoading.value)
        assertEquals(null, errorViewModel.errorMessage.value)
        
        coVerify { errorRepository.getError(errorId) }
    }
    
    @Test
    fun `loadErrorDetails should handle errors gracefully`() = runTest {
        // Given
        val errorId = "non-existent-error"
        
        coEvery { errorRepository.getError(errorId) } throws Exception("Error not found")
        
        // When
        errorViewModel.loadErrorDetails(errorId)
        
        // Then
        assertEquals(null, errorViewModel.selectedError.value)
        assertEquals(false, errorViewModel.isLoading.value)
        assertNotNull(errorViewModel.errorMessage.value)
        assertTrue(errorViewModel.errorMessage.value!!.contains("Failed to load error details"))
    }
    
    @Test
    fun `loadErrorsByType should update errors state when successful`() = runTest {
        // Given
        val errorType = ErrorType.NETWORK_ERROR
        val errors = listOf(
            AppError(
                id = "error-1",
                type = errorType,
                severity = ErrorSeverity.HIGH,
                message = "Network error 1",
                timestamp = System.currentTimeMillis()
            ),
            AppError(
                id = "error-2",
                type = errorType,
                severity = ErrorSeverity.MEDIUM,
                message = "Network error 2",
                timestamp = System.currentTimeMillis()
            )
        )
        
        coEvery { errorRepository.getErrorsByType(errorType) } returns flowOf(*errors.toTypedArray())
        
        // When
        errorViewModel.loadErrorsByType(errorType)
        
        // Then
        val resultErrors = errorViewModel.errors.value
        assertEquals(2, resultErrors.size)
        assertEquals(errorType, resultErrors[0].type)
        assertEquals(errorType, resultErrors[1].type)
        assertEquals(errorType, errorViewModel.selectedType.value)
        
        coVerify { errorRepository.getErrorsByType(errorType) }
    }
    
    @Test
    fun `loadErrorsBySeverity should update errors state when successful`() = runTest {
        // Given
        val severity = ErrorSeverity.CRITICAL
        val errors = listOf(
            AppError(
                id = "error-1",
                type = ErrorType.SYSTEM_ERROR,
                severity = severity,
                message = "Critical error 1",
                timestamp = System.currentTimeMillis()
            ),
            AppError(
                id = "error-2",
                type = ErrorType.NETWORK_ERROR,
                severity = severity,
                message = "Critical error 2",
                timestamp = System.currentTimeMillis()
            )
        )
        
        coEvery { errorRepository.getErrorsBySeverity(severity) } returns flowOf(*errors.toTypedArray())
        
        // When
        errorViewModel.loadErrorsBySeverity(severity)
        
        // Then
        val resultErrors = errorViewModel.errors.value
        assertEquals(2, resultErrors.size)
        assertEquals(severity, resultErrors[0].severity)
        assertEquals(severity, resultErrors[1].severity)
        assertEquals(severity, errorViewModel.selectedSeverity.value)
        
        coVerify { errorRepository.getErrorsBySeverity(severity) }
    }
    
    @Test
    fun `loadRecentErrors should update errors state when successful`() = runTest {
        // Given
        val limit = 10
        val errors = listOf(
            AppError(
                id = "error-1",
                type = ErrorType.SYSTEM_ERROR,
                severity = ErrorSeverity.HIGH,
                message = "Recent error 1",
                timestamp = System.currentTimeMillis()
            ),
            AppError(
                id = "error-2",
                type = ErrorType.NETWORK_ERROR,
                severity = ErrorSeverity.MEDIUM,
                message = "Recent error 2",
                timestamp = System.currentTimeMillis()
            )
        )
        
        coEvery { errorRepository.getRecentErrors(limit) } returns flowOf(*errors.toTypedArray())
        
        // When
        errorViewModel.loadRecentErrors(limit)
        
        // Then
        val resultErrors = errorViewModel.errors.value
        assertEquals(2, resultErrors.size)
        
        coVerify { errorRepository.getRecentErrors(limit) }
    }
    
    @Test
    fun `loadErrorsByDateRange should update errors state when successful`() = runTest {
        // Given
        val startDate = 1000000L
        val endDate = 2000000L
        val error = AppError(
            id = "error-1",
            type = ErrorType.SYSTEM_ERROR,
            severity = ErrorSeverity.HIGH,
            message = "Date range error",
            timestamp = 1500000L
        )
        
        coEvery { errorRepository.getErrorsByDateRange(startDate, endDate) } returns flowOf(error)
        
        // When
        errorViewModel.loadErrorsByDateRange(startDate, endDate)
        
        // Then
        val resultErrors = errorViewModel.errors.value
        assertEquals(1, resultErrors.size)
        assertTrue(resultErrors[0].timestamp >= startDate)
        assertTrue(resultErrors[0].timestamp <= endDate)
        
        coVerify { errorRepository.getErrorsByDateRange(startDate, endDate) }
    }
    
    @Test
    fun `deleteError should remove error from list when successful`() = runTest {
        // Given
        val errorId = "error-123"
        val errors = listOf(
            AppError(
                id = "error-123",
                type = ErrorType.SYSTEM_ERROR,
                severity = ErrorSeverity.HIGH,
                message = "Test error",
                timestamp = System.currentTimeMillis()
            ),
            AppError(
                id = "error-456",
                type = ErrorType.NETWORK_ERROR,
                severity = ErrorSeverity.MEDIUM,
                message = "Another test error",
                timestamp = System.currentTimeMillis()
            )
        )
        
        // Set initial state
        errorViewModel.errors.value = errors.toMutableList()
        
        coEvery { errorRepository.deleteError(errorId) } returns true
        
        // When
        errorViewModel.deleteError(errorId)
        
        // Then
        val resultErrors = errorViewModel.errors.value
        assertEquals(1, resultErrors.size)
        assertEquals("error-456", resultErrors[0].id)
        
        coVerify { errorRepository.deleteError(errorId) }
    }
    
    @Test
    fun `deleteError should handle errors gracefully`() = runTest {
        // Given
        val errorId = "error-123"
        val errors = listOf(
            AppError(
                id = "error-123",
                type = ErrorType.SYSTEM_ERROR,
                severity = ErrorSeverity.HIGH,
                message = "Test error",
                timestamp = System.currentTimeMillis()
            )
        )
        
        // Set initial state
        errorViewModel.errors.value = errors.toMutableList()
        
        coEvery { errorRepository.deleteError(errorId) } throws Exception("Delete failed")
        
        // When
        errorViewModel.deleteError(errorId)
        
        // Then
        assertEquals(1, errorViewModel.errors.value.size) // Error not removed
        assertNotNull(errorViewModel.errorMessage.value)
        
        coVerify { errorRepository.deleteError(errorId) }
    }
    
    @Test
    fun `searchErrors should update errors state when successful`() = runTest {
        // Given
        val query = "network"
        val errors = listOf(
            AppError(
                id = "error-123",
                type = ErrorType.NETWORK_ERROR,
                severity = ErrorSeverity.HIGH,
                message = "Network connection failed",
                timestamp = System.currentTimeMillis()
            )
        )
        
        coEvery { errorRepository.searchErrors(query) } returns flowOf(*errors.toTypedArray())
        
        // When
        errorViewModel.searchErrors(query)
        
        // Then
        val resultErrors = errorViewModel.errors.value
        assertEquals(1, resultErrors.size)
        assertEquals(error.id, resultErrors[0].id)
        assertEquals(query, errorViewModel.searchQuery.value)
        
        coVerify { errorRepository.searchErrors(query) }
    }
    
    @Test
    fun `refreshErrors should reload current errors`() = runTest {
        // Given
        val errors = listOf(
            AppError(
                id = "error-1",
                type = ErrorType.SYSTEM_ERROR,
                severity = ErrorSeverity.HIGH,
                message = "System error 1",
                timestamp = System.currentTimeMillis()
            )
        )
        
        coEvery { 
            errorRepository.getErrors(
                limit = 20,
                offset = 0,
                type = null,
                severity = null,
                startDate = null,
                endDate = null
            ) 
        } returns flowOf(*errors.toTypedArray())
        
        // When
        errorViewModel.refreshErrors()
        
        // Then
        val resultErrors = errorViewModel.errors.value
        assertEquals(1, resultErrors.size)
        assertEquals("error-1", resultErrors[0].id)
        
        coVerify { 
            errorRepository.getErrors(
                limit = 20,
                offset = 0,
                type = null,
                severity = null,
                startDate = null,
                endDate = null
            ) 
        }
    }
    
    @Test
    fun `clearFilters should reset filter states`() = runTest {
        // Given
        errorViewModel.selectedType.value = ErrorType.NETWORK_ERROR
        errorViewModel.selectedSeverity.value = ErrorSeverity.HIGH
        errorViewModel.searchQuery.value = "test query"
        
        // When
        errorViewModel.clearFilters()
        
        // Then
        assertEquals(null, errorViewModel.selectedType.value)
        assertEquals(null, errorViewModel.selectedSeverity.value)
        assertEquals("", errorViewModel.searchQuery.value)
    }
    
    @Test
    fun `clearError should reset selectedError`() = runTest {
        // Given
        val error = AppError(
            id = "error-123",
            type = ErrorType.SYSTEM_ERROR,
            severity = ErrorSeverity.HIGH,
            message = "Test error",
            timestamp = System.currentTimeMillis()
        )
        
        errorViewModel.selectedError.value = error
        
        // When
        errorViewModel.clearError()
        
        // Then
        assertEquals(null, errorViewModel.selectedError.value)
    }
    
    @Test
    fun `clearErrorMessage should reset errorMessage`() = runTest {
        // Given
        errorViewModel.errorMessage.value = "Test error message"
        
        // When
        errorViewModel.clearErrorMessage()
        
        // Then
        assertEquals(null, errorViewModel.errorMessage.value)
    }
}