package com.smsgateway.app.retry

import com.smsgateway.app.database.RetryStrategy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import kotlin.random.Random

class RetryServiceTest {
    
    private lateinit var retryService: RetryService
    
    @BeforeEach
    fun setUp() {
        retryService = RetryService()
    }
    
    @AfterEach
    fun tearDown() {
        // Cleanup if needed
    }
    
    @Test
    fun `should calculate exponential backoff delay`() = runTest {
        // Given
        val attempt = 3
        val errorType = "Network timeout"
        val strategy = RetryStrategy.EXPONENTIAL_BACKOFF
        
        // When
        val delay = retryService.calculateRetryDelay(attempt, errorType, strategy)
        
        // Then
        // Expected: baseDelay * (2 ^ (attempt - 1)) = 1000 * (2 ^ 2) = 4000ms
        val expectedDelay = 1000L * (1 shl (attempt - 1))
        assertEquals(expectedDelay, delay)
    }
    
    @Test
    fun `should calculate linear backoff delay`() = runTest {
        // Given
        val attempt = 3
        val errorType = "Network timeout"
        val strategy = RetryStrategy.LINEAR_BACKOFF
        
        // When
        val delay = retryService.calculateRetryDelay(attempt, errorType, strategy)
        
        // Then
        // Expected: baseDelay * attempt = 1000 * 3 = 3000ms
        val expectedDelay = 1000L * attempt
        assertEquals(expectedDelay, delay)
    }
    
    @Test
    fun `should calculate fixed delay`() = runTest {
        // Given
        val attempt = 5
        val errorType = "Network timeout"
        val strategy = RetryStrategy.FIXED_DELAY
        
        // When
        val delay = retryService.calculateRetryDelay(attempt, errorType, strategy)
        
        // Then
        // Expected: fixed delay = 5000ms
        assertEquals(5000L, delay)
    }
    
    @Test
    fun `should add jitter to exponential backoff`() = runTest {
        // Given
        val attempt = 2
        val errorType = "Network timeout"
        val strategy = RetryStrategy.EXPONENTIAL_BACKOFF
        val baseDelay = 1000L * (1 shl (attempt - 1)) // 2000ms
        
        // When
        val delay1 = retryService.calculateRetryDelay(attempt, errorType, strategy)
        val delay2 = retryService.calculateRetryDelay(attempt, errorType, strategy)
        val delay3 = retryService.calculateRetryDelay(attempt, errorType, strategy)
        
        // Then
        // All delays should be within ±25% of base delay
        val minDelay = (baseDelay * 0.75).toLong()
        val maxDelay = (baseDelay * 1.25).toLong()
        
        assertTrue(delay1 in minDelay..maxDelay, "Delay1: $delay1 not in range [$minDelay, $maxDelay]")
        assertTrue(delay2 in minDelay..maxDelay, "Delay2: $delay2 not in range [$minDelay, $maxDelay]")
        assertTrue(delay3 in minDelay..maxDelay, "Delay3: $delay3 not in range [$minDelay, $maxDelay]")
        
        // Delays should be different (due to jitter)
        assertTrue(delay1 != delay2 || delay2 != delay3, "All delays are the same, jitter not working")
    }
    
    @Test
    fun `should determine retry eligibility for retryable errors`() = runTest {
        // Given
        val retryableErrors = listOf(
            "Network timeout",
            "Connection refused",
            "Service unavailable",
            "Rate limit exceeded",
            "Temporary failure"
        )
        
        retryableErrors.forEach { errorType ->
            // When
            val shouldRetry = retryService.shouldRetry(1, errorType)
            
            // Then
            assertTrue(shouldRetry, "Should retry for error: $errorType")
        }
    }
    
    @Test
    fun `should not retry for non-retryable errors`() = runTest {
        // Given
        val nonRetryableErrors = listOf(
            "Invalid phone number",
            "Authentication failed",
            "Insufficient credits",
            "Message blocked",
            "Account suspended"
        )
        
        nonRetryableErrors.forEach { errorType ->
            // When
            val shouldRetry = retryService.shouldRetry(1, errorType)
            
            // Then
            assertFalse(shouldRetry, "Should not retry for error: $errorType")
        }
    }
    
    @Test
    fun `should not retry when max attempts exceeded`() = runTest {
        // Given
        val maxAttempts = 5
        val errorType = "Network timeout"
        
        // When
        val shouldRetry1 = retryService.shouldRetry(1, errorType, maxAttempts)
        val shouldRetry5 = retryService.shouldRetry(5, errorType, maxAttempts)
        val shouldRetry6 = retryService.shouldRetry(6, errorType, maxAttempts)
        
        // Then
        assertTrue(shouldRetry1, "Should retry on attempt 1")
        assertFalse(shouldRetry5, "Should not retry on attempt 5 (max attempts)")
        assertFalse(shouldRetry6, "Should not retry on attempt 6 (exceeded max)")
    }
    
    @Test
    fun `should get retry policy for SMS`() = runTest {
        // Given
        val smsId = 1L
        val expectedPolicy = retryService.RetryPolicy(
            maxRetries = 3,
            strategy = RetryStrategy.EXPONENTIAL_BACKOFF,
            baseDelay = 1000L,
            maxDelay = 60000L,
            retryableErrors = setOf("Network timeout", "Connection refused")
        )
        
        // When
        val policy = retryService.getRetryPolicy(smsId)
        
        // Then
        assertNotNull(policy)
        assertEquals(expectedPolicy.maxRetries, policy.maxRetries)
        assertEquals(expectedPolicy.strategy, policy.strategy)
        assertEquals(expectedPolicy.baseDelay, policy.baseDelay)
        assertEquals(expectedPolicy.maxDelay, policy.maxDelay)
        assertTrue(policy.retryableErrors.contains("Network timeout"))
    }
    
    @Test
    fun `should create custom retry policy`() = runTest {
        // Given
        val customPolicy = retryService.RetryPolicy(
            maxRetries = 5,
            strategy = RetryStrategy.LINEAR_BACKOFF,
            baseDelay = 2000L,
            maxDelay = 120000L,
            retryableErrors = setOf("Custom error", "Another error")
        )
        
        // When
        val smsId = 1L
        retryService.setRetryPolicy(smsId, customPolicy)
        val retrievedPolicy = retryService.getRetryPolicy(smsId)
        
        // Then
        assertNotNull(retrievedPolicy)
        assertEquals(customPolicy.maxRetries, retrievedPolicy.maxRetries)
        assertEquals(customPolicy.strategy, retrievedPolicy.strategy)
        assertEquals(customPolicy.baseDelay, retrievedPolicy.baseDelay)
        assertEquals(customPolicy.maxDelay, retrievedPolicy.maxDelay)
        assertEquals(customPolicy.retryableErrors, retrievedPolicy.retryableErrors)
    }
    
    @Test
    fun `should use default policy when no custom policy set`() = runTest {
        // Given
        val smsId = 999L // Non-existent ID
        
        // When
        val policy = retryService.getRetryPolicy(smsId)
        
        // Then
        assertNotNull(policy)
        assertEquals(3, policy.maxRetries) // Default value
        assertEquals(RetryStrategy.EXPONENTIAL_BACKOFF, policy.strategy) // Default value
        assertEquals(1000L, policy.baseDelay) // Default value
        assertEquals(60000L, policy.maxDelay) // Default value
    }
    
    @Test
    fun `should cap delay at maximum`() = runTest {
        // Given
        val attempt = 20 // Very high attempt number
        val errorType = "Network timeout"
        val strategy = RetryStrategy.EXPONENTIAL_BACKOFF
        
        // When
        val delay = retryService.calculateRetryDelay(attempt, errorType, strategy)
        
        // Then
        // Should be capped at maxDelay (60000ms)
        assertEquals(60000L, delay)
    }
    
    @Test
    fun `should handle different error types differently`() = runTest {
        // Given
        val attempt = 2
        
        // When
        val networkDelay = retryService.calculateRetryDelay(attempt, "Network timeout")
        val rateLimitDelay = retryService.calculateRetryDelay(attempt, "Rate limit exceeded")
        val serviceUnavailableDelay = retryService.calculateRetryDelay(attempt, "Service unavailable")
        
        // Then
        // Rate limit should have longer delay than network timeout
        assertTrue(rateLimitDelay > networkDelay)
        // Service unavailable should have even longer delay
        assertTrue(serviceUnavailableDelay > rateLimitDelay)
    }
    
    @Test
    fun `should calculate delay with custom policy`() = runTest {
        // Given
        val customPolicy = retryService.RetryPolicy(
            maxRetries = 5,
            strategy = RetryStrategy.LINEAR_BACKOFF,
            baseDelay = 5000L,
            maxDelay = 300000L,
            retryableErrors = setOf("Custom error")
        )
        
        val smsId = 1L
        retryService.setRetryPolicy(smsId, customPolicy)
        
        // When
        val delay = retryService.calculateRetryDelay(3, "Custom error", smsId)
        
        // Then
        // Expected: baseDelay * attempt = 5000 * 3 = 15000ms
        assertEquals(15000L, delay)
    }
    
    @Test
    fun `should remove custom retry policy`() = runTest {
        // Given
        val smsId = 1L
        val customPolicy = retryService.RetryPolicy(
            maxRetries = 5,
            strategy = RetryStrategy.LINEAR_BACKOFF,
            baseDelay = 2000L,
            maxDelay = 120000L,
            retryableErrors = setOf("Custom error")
        )
        
        retryService.setRetryPolicy(smsId, customPolicy)
        var policy = retryService.getRetryPolicy(smsId)
        assertEquals(5, policy.maxRetries) // Custom policy is set
        
        // When
        retryService.removeRetryPolicy(smsId)
        policy = retryService.getRetryPolicy(smsId)
        
        // Then
        assertEquals(3, policy.maxRetries) // Back to default
        assertEquals(RetryStrategy.EXPONENTIAL_BACKOFF, policy.strategy) // Back to default
    }
    
    @Test
    fun `should handle edge cases for retry calculation`() = runTest {
        // Test with attempt = 0
        val delay1 = retryService.calculateRetryDelay(0, "Network timeout")
        assertEquals(1000L, delay1) // Should use base delay
        
        // Test with negative attempt
        val delay2 = retryService.calculateRetryDelay(-1, "Network timeout")
        assertEquals(1000L, delay2) // Should use base delay
        
        // Test with very high attempt
        val delay3 = retryService.calculateRetryDelay(1000, "Network timeout")
        assertEquals(60000L, delay3) // Should be capped at max delay
    }
    
    @Test
    fun `should handle empty error type`() = runTest {
        // Given
        val emptyErrorType = ""
        val nullErrorType: String? = null
        
        // When
        val shouldRetry1 = retryService.shouldRetry(1, emptyErrorType)
        val shouldRetry2 = retryService.shouldRetry(1, nullErrorType ?: "")
        
        // Then
        // Should treat unknown errors as retryable
        assertTrue(shouldRetry1)
        assertTrue(shouldRetry2)
    }
    
    @Test
    fun `should get retry statistics`() = runTest {
        // Given
        val smsId = 1L
        
        // Simulate some retry attempts
        retryService.calculateRetryDelay(1, "Network timeout", smsId)
        retryService.calculateRetryDelay(2, "Network timeout", smsId)
        retryService.shouldRetry(1, "Network timeout", 3, smsId)
        retryService.shouldRetry(2, "Network timeout", 3, smsId)
        retryService.shouldRetry(3, "Network timeout", 3, smsId)
        
        // When
        val stats = retryService.getRetryStatistics(smsId)
        
        // Then
        assertNotNull(stats)
        assertTrue(stats.totalAttempts > 0)
        assertTrue(stats.retryCount >= 0)
        assertTrue(stats.averageDelay > 0)
    }
}