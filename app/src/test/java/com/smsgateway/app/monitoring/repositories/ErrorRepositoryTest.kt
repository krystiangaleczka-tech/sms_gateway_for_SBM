package com.smsgateway.app.monitoring.repositories

import com.smsgateway.app.api.ApiService
import com.smsgateway.app.database.SmsMessage
import com.smsgateway.app.monitoring.models.AppError
import com.smsgateway.app.monitoring.models.ErrorSeverity
import com.smsgateway.app.monitoring.models.ErrorType
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
 * Testy jednostkowe dla ErrorRepository
 */
class ErrorRepositoryTest {
    
    private lateinit var apiService: ApiService
    private lateinit var errorRepository: ErrorRepository
    
    @Before
    fun setup() {
        apiService = mockk(relaxed = true)
        errorRepository = ErrorRepository(apiService)
    }
    
    @Test
    fun `getErrors should return errors from API and cache them`() = runTest {
        // Given
        val errorId = "error-123"
        val error = AppError(
            id = errorId,
            type = ErrorType.SYSTEM_ERROR,
            severity = ErrorSeverity.HIGH,
            message = "Test error message",
            timestamp = System.currentTimeMillis()
        )
        
        coEvery { 
            apiService.getErrors(
                limit = 20,
                offset = 0,
                type = null,
                severity = null,
                startDate = null,
                endDate = null
            ) 
        } returns flowOf(error)
        
        // When
        val result = errorRepository.getErrors(
            limit = 20,
            offset = 0,
            type = null,
            severity = null,
            startDate = null,
            endDate = null
        ).toList()
        
        // Then
        assertEquals(1, result.size)
        assertEquals(errorId, result[0].id)
        assertEquals(ErrorType.SYSTEM_ERROR, result[0].type)
        assertEquals(ErrorSeverity.HIGH, result[0].severity)
        
        coVerify { 
            apiService.getErrors(
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
    fun `getErrors should apply filters correctly`() = runTest {
        // Given
        val errorId = "error-123"
        val error = AppError(
            id = errorId,
            type = ErrorType.NETWORK_ERROR,
            severity = ErrorSeverity.MEDIUM,
            message = "Test error message",
            timestamp = System.currentTimeMillis()
        )
        
        val type = ErrorType.NETWORK_ERROR
        val severity = ErrorSeverity.MEDIUM
        val startDate = 1000000L
        val endDate = 2000000L
        
        coEvery { 
            apiService.getErrors(
                limit = 10,
                offset = 5,
                type = type,
                severity = severity,
                startDate = startDate,
                endDate = endDate
            ) 
        } returns flowOf(error)
        
        // When
        val result = errorRepository.getErrors(
            limit = 10,
            offset = 5,
            type = type,
            severity = severity,
            startDate = startDate,
            endDate = endDate
        ).toList()
        
        // Then
        assertEquals(1, result.size)
        assertEquals(errorId, result[0].id)
        assertEquals(type, result[0].type)
        assertEquals(severity, result[0].severity)
        
        coVerify { 
            apiService.getErrors(
                limit = 10,
                offset = 5,
                type = type,
                severity = severity,
                startDate = startDate,
                endDate = endDate
            ) 
        }
    }
    
    @Test
    fun `getError should return error by ID from API`() = runTest {
        // Given
        val errorId = "error-123"
        val error = AppError(
            id = errorId,
            type = ErrorType.SYSTEM_ERROR,
            severity = ErrorSeverity.HIGH,
            message = "Test error message",
            timestamp = System.currentTimeMillis()
        )
        
        coEvery { apiService.getError(errorId) } returns error
        
        // When
        val result = errorRepository.getError(errorId)
        
        // Then
        assertNotNull(result)
        assertEquals(errorId, result!!.id)
        assertEquals(ErrorType.SYSTEM_ERROR, result.type)
        assertEquals(ErrorSeverity.HIGH, result.severity)
        
        coVerify { apiService.getError(errorId) }
    }
    
    @Test
    fun `getError should return null if error not found`() = runTest {
        // Given
        val errorId = "non-existent-error"
        
        coEvery { apiService.getError(errorId) } throws Exception("Error not found")
        
        // When
        val result = errorRepository.getError(errorId)
        
        // Then
        assertEquals(null, result)
        
        coVerify { apiService.getError(errorId) }
    }
    
    @Test
    fun `getErrorsByType should return filtered errors`() = runTest {
        // Given
        val errorType = ErrorType.NETWORK_ERROR
        val error1 = AppError(
            id = "error-1",
            type = errorType,
            severity = ErrorSeverity.HIGH,
            message = "Network error 1",
            timestamp = System.currentTimeMillis()
        )
        val error2 = AppError(
            id = "error-2",
            type = errorType,
            severity = ErrorSeverity.MEDIUM,
            message = "Network error 2",
            timestamp = System.currentTimeMillis()
        )
        
        coEvery { 
            apiService.getErrors(
                limit = 20,
                offset = 0,
                type = errorType,
                severity = null,
                startDate = null,
                endDate = null
            ) 
        } returns flowOf(error1, error2)
        
        // When
        val result = errorRepository.getErrorsByType(errorType).toList()
        
        // Then
        assertEquals(2, result.size)
        assertEquals(errorType, result[0].type)
        assertEquals(errorType, result[1].type)
        
        coVerify { 
            apiService.getErrors(
                limit = 20,
                offset = 0,
                type = errorType,
                severity = null,
                startDate = null,
                endDate = null
            ) 
        }
    }
    
    @Test
    fun `getErrorsBySeverity should return filtered errors`() = runTest {
        // Given
        val severity = ErrorSeverity.CRITICAL
        val error1 = AppError(
            id = "error-1",
            type = ErrorType.SYSTEM_ERROR,
            severity = severity,
            message = "Critical error 1",
            timestamp = System.currentTimeMillis()
        )
        val error2 = AppError(
            id = "error-2",
            type = ErrorType.NETWORK_ERROR,
            severity = severity,
            message = "Critical error 2",
            timestamp = System.currentTimeMillis()
        )
        
        coEvery { 
            apiService.getErrors(
                limit = 20,
                offset = 0,
                type = null,
                severity = severity,
                startDate = null,
                endDate = null
            ) 
        } returns flowOf(error1, error2)
        
        // When
        val result = errorRepository.getErrorsBySeverity(severity).toList()
        
        // Then
        assertEquals(2, result.size)
        assertEquals(severity, result[0].severity)
        assertEquals(severity, result[1].severity)
        
        coVerify { 
            apiService.getErrors(
                limit = 20,
                offset = 0,
                type = null,
                severity = severity,
                startDate = null,
                endDate = null
            ) 
        }
    }
    
    @Test
    fun `getRecentErrors should return recent errors with default limit`() = runTest {
        // Given
        val error1 = AppError(
            id = "error-1",
            type = ErrorType.SYSTEM_ERROR,
            severity = ErrorSeverity.HIGH,
            message = "Recent error 1",
            timestamp = System.currentTimeMillis()
        )
        val error2 = AppError(
            id = "error-2",
            type = ErrorType.NETWORK_ERROR,
            severity = ErrorSeverity.MEDIUM,
            message = "Recent error 2",
            timestamp = System.currentTimeMillis()
        )
        
        coEvery { 
            apiService.getErrors(
                limit = 10,
                offset = 0,
                type = null,
                severity = null,
                startDate = null,
                endDate = null
            ) 
        } returns flowOf(error1, error2)
        
        // When
        val result = errorRepository.getRecentErrors().toList()
        
        // Then
        assertEquals(2, result.size)
        
        coVerify { 
            apiService.getErrors(
                limit = 10,
                offset = 0,
                type = null,
                severity = null,
                startDate = null,
                endDate = null
            ) 
        }
    }
    
    @Test
    fun `getRecentErrors should use custom limit`() = runTest {
        // Given
        val limit = 5
        val error = AppError(
            id = "error-1",
            type = ErrorType.SYSTEM_ERROR,
            severity = ErrorSeverity.HIGH,
            message = "Recent error",
            timestamp = System.currentTimeMillis()
        )
        
        coEvery { 
            apiService.getErrors(
                limit = limit,
                offset = 0,
                type = null,
                severity = null,
                startDate = null,
                endDate = null
            ) 
        } returns flowOf(error)
        
        // When
        val result = errorRepository.getRecentErrors(limit).toList()
        
        // Then
        assertEquals(1, result.size)
        
        coVerify { 
            apiService.getErrors(
                limit = limit,
                offset = 0,
                type = null,
                severity = null,
                startDate = null,
                endDate = null
            ) 
        }
    }
    
    @Test
    fun `getErrorsByDateRange should return filtered errors`() = runTest {
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
        
        coEvery { 
            apiService.getErrors(
                limit = 20,
                offset = 0,
                type = null,
                severity = null,
                startDate = startDate,
                endDate = endDate
            ) 
        } returns flowOf(error)
        
        // When
        val result = errorRepository.getErrorsByDateRange(startDate, endDate).toList()
        
        // Then
        assertEquals(1, result.size)
        assertTrue(result[0].timestamp >= startDate)
        assertTrue(result[0].timestamp <= endDate)
        
        coVerify { 
            apiService.getErrors(
                limit = 20,
                offset = 0,
                type = null,
                severity = null,
                startDate = startDate,
                endDate = endDate
            ) 
        }
    }
    
    @Test
    fun `deleteError should call API delete method`() = runTest {
        // Given
        val errorId = "error-123"
        
        coEvery { apiService.deleteError(errorId) } returns true
        
        // When
        val result = errorRepository.deleteError(errorId)
        
        // Then
        assertTrue(result)
        
        coVerify { apiService.deleteError(errorId) }
    }
    
    @Test
    fun `deleteError should handle API failure`() = runTest {
        // Given
        val errorId = "error-123"
        
        coEvery { apiService.deleteError(errorId) } throws Exception("Delete failed")
        
        // When
        val result = errorRepository.deleteError(errorId)
        
        // Then
        assertEquals(false, result)
        
        coVerify { apiService.deleteError(errorId) }
    }
    
    @Test
    fun `searchErrors should call API search method`() = runTest {
        // Given
        val query = "network"
        val error = AppError(
            id = "error-123",
            type = ErrorType.NETWORK_ERROR,
            severity = ErrorSeverity.HIGH,
            message = "Network connection failed",
            timestamp = System.currentTimeMillis()
        )
        
        coEvery { apiService.searchErrors(query) } returns flowOf(error)
        
        // When
        val result = errorRepository.searchErrors(query).toList()
        
        // Then
        assertEquals(1, result.size)
        assertEquals(error.id, result[0].id)
        
        coVerify { apiService.searchErrors(query) }
    }
    
    @Test
    fun `getErrorCount should return error count from API`() = runTest {
        // Given
        val expectedCount = 42
        
        coEvery { apiService.getErrorCount() } returns expectedCount
        
        // When
        val result = errorRepository.getErrorCount()
        
        // Then
        assertEquals(expectedCount, result)
        
        coVerify { apiService.getErrorCount() }
    }
    
    @Test
    fun `getErrorCount should handle API failure`() = runTest {
        // Given
        coEvery { apiService.getErrorCount() } throws Exception("Count failed")
        
        // When
        val result = errorRepository.getErrorCount()
        
        // Then
        assertEquals(0, result)
        
        coVerify { apiService.getErrorCount() }
    }
    
    @Test
    fun `getErrorCountByType should return filtered error count from API`() = runTest {
        // Given
        val type = ErrorType.NETWORK_ERROR
        val expectedCount = 15
        
        coEvery { apiService.getErrorCountByType(type) } returns expectedCount
        
        // When
        val result = errorRepository.getErrorCountByType(type)
        
        // Then
        assertEquals(expectedCount, result)
        
        coVerify { apiService.getErrorCountByType(type) }
    }
    
    @Test
    fun `getErrorCountBySeverity should return filtered error count from API`() = runTest {
        // Given
        val severity = ErrorSeverity.CRITICAL
        val expectedCount = 3
        
        coEvery { apiService.getErrorCountBySeverity(severity) } returns expectedCount
        
        // When
        val result = errorRepository.getErrorCountBySeverity(severity)
        
        // Then
        assertEquals(expectedCount, result)
        
        coVerify { apiService.getErrorCountBySeverity(severity) }
    }
}