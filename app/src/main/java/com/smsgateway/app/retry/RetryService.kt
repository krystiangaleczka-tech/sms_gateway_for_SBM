package com.smsgateway.app.retry

import android.util.Log
import com.smsgateway.app.database.SmsMessage
import com.smsgateway.app.database.SmsStatus
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Service responsible for managing retry logic for SMS sending
 * Implements different retry strategies with exponential backoff and jitter
 */
class RetryService {
    
    companion object {
        private const val TAG = "RetryService"
        
        // Default retry limits
        private const val DEFAULT_MAX_RETRIES = 3
        private const val DEFAULT_BASE_DELAY_MS = 1000L // 1 second
        private const val DEFAULT_MAX_DELAY_MS = 300000L // 5 minutes
        private const val DEFAULT_JITTER_FACTOR = 0.1 // 10% jitter
        
        // Error types that should be retried
        private val RETRYABLE_ERRORS = setOf(
            "NETWORK_ERROR",
            "TIMEOUT",
            "SERVICE_UNAVAILABLE",
            "RATE_LIMITED",
            "SIM_BUSY",
            "NO_SIGNAL"
        )
        
        // Error types that should not be retried
        private val NON_RETRYABLE_ERRORS = setOf(
            "INVALID_NUMBER",
            "BLOCKED_NUMBER",
            "PERMISSION_DENIED",
            "MESSAGE_TOO_LONG",
            "INVALID_CONTENT"
        )
    }
    
    /**
     * Calculates the delay before the next retry attempt based on strategy and attempt number
     * 
     * @param attempt Current retry attempt (0-based)
     * @param strategy Retry strategy to use
     * @param baseDelayMs Base delay in milliseconds
     * @param maxDelayMs Maximum delay in milliseconds
     * @param jitterFactor Random jitter factor (0.0 to 1.0)
     * @return Delay in milliseconds before next retry
     */
    fun calculateRetryDelay(
        attempt: Int,
        strategy: RetryStrategy,
        baseDelayMs: Long = DEFAULT_BASE_DELAY_MS,
        maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
        jitterFactor: Float = DEFAULT_JITTER_FACTOR
    ): Long {
        val baseDelay = when (strategy) {
            RetryStrategy.EXPONENTIAL_BACKOFF -> {
                // Exponential backoff: delay = baseDelay * 2^attempt
                baseDelayMs * 2.0.pow(attempt.toDouble()).toLong()
            }
            
            RetryStrategy.LINEAR_BACKOFF -> {
                // Linear backoff: delay = baseDelay * (attempt + 1)
                baseDelayMs * (attempt + 1)
            }
            
            RetryStrategy.FIXED_DELAY -> {
                // Fixed delay: always use base delay
                baseDelayMs
            }
            
            RetryStrategy.CUSTOM -> {
                // Custom strategy can be implemented here
                // For now, use exponential as default
                baseDelayMs * 2.0.pow(attempt.toDouble()).toLong()
            }
        }
        
        // Apply jitter to prevent thundering herd
        val jitter = (baseDelay * jitterFactor * Random.nextDouble()).toLong()
        val delayWithJitter = baseDelay + jitter
        
        // Ensure delay doesn't exceed maximum
        return min(delayWithJitter, maxDelayMs)
    }
    
    /**
     * Determines if a message should be retried based on error type and attempt count
     * 
     * @param message SMS message that failed
     * @param errorType Type of error that occurred
     * @return true if message should be retried, false otherwise
     */
    fun shouldRetry(message: SmsMessage, errorType: String): Boolean {
        // Check if we've exceeded max retries
        if (message.retryCount >= message.maxRetries) {
            Log.d(TAG, "Message ${message.id} exceeded max retries (${message.maxRetries})")
            return false
        }
        
        // Check if error type is retryable
        if (errorType in NON_RETRYABLE_ERRORS) {
            Log.d(TAG, "Message ${message.id} failed with non-retryable error: $errorType")
            return false
        }
        
        if (errorType in RETRYABLE_ERRORS) {
            Log.d(TAG, "Message ${message.id} failed with retryable error: $errorType")
            return true
        }
        
        // Default: retry unknown errors if we haven't exceeded max retries
        Log.d(TAG, "Message ${message.id} failed with unknown error: $errorType, allowing retry")
        return true
    }
    
    /**
     * Gets the retry policy for a specific message
     * 
     * @param message SMS message
     * @return RetryPolicy with strategy and parameters
     */
    fun getRetryPolicy(message: SmsMessage): RetryPolicy {
        return RetryPolicy(
            strategy = message.retryStrategy,
            maxRetries = message.maxRetries,
            baseDelayMs = getBaseDelayForPriority(message.priority),
            maxDelayMs = getMaxDelayForPriority(message.priority),
            jitterFactor = getJitterForStrategy(message.retryStrategy)
        )
    }
    
    /**
     * Calculates the next retry time for a message
     * 
     * @param message SMS message that failed
     * @param errorType Type of error that occurred
     * @return Timestamp in milliseconds for next retry, or null if no retry
     */
    fun getNextRetryTime(message: SmsMessage, errorType: String): Long? {
        if (!shouldRetry(message, errorType)) {
            return null
        }
        
        val policy = getRetryPolicy(message)
        val delayMs = calculateRetryDelay(
            attempt = message.retryCount,
            strategy = policy.strategy,
            baseDelayMs = policy.baseDelayMs,
            maxDelayMs = policy.maxDelayMs,
            jitterFactor = policy.jitterFactor
        )
        
        return System.currentTimeMillis() + delayMs
    }
    
    /**
     * Prepares a message for retry by updating its status and retry count
     * 
     * @param message SMS message to prepare for retry
     * @param errorType Type of error that occurred
     * @param errorMessage Error message from previous attempt
     * @return Updated SMS message ready for retry
     */
    fun prepareMessageForRetry(
        message: SmsMessage,
        errorType: String,
        errorMessage: String
    ): SmsMessage {
        val newRetryCount = message.retryCount + 1
        val nextRetryTime = getNextRetryTime(message, errorType)
        
        Log.d(TAG, "Preparing message ${message.id} for retry #$newRetryCount")
        
        return message.copy(
            status = if (nextRetryTime != null) SmsStatus.SCHEDULED else SmsStatus.FAILED,
            retryCount = newRetryCount,
            errorMessage = errorMessage,
            scheduledAt = nextRetryTime
        )
    }
    
    /**
     * Checks if a message is ready to be retried based on its scheduled time
     * 
     * @param message SMS message to check
     * @return true if message is ready for retry, false otherwise
     */
    fun isReadyForRetry(message: SmsMessage): Boolean {
        if (message.status != SmsStatus.SCHEDULED || message.scheduledAt == null) {
            return false
        }
        
        return System.currentTimeMillis() >= message.scheduledAt
    }
    
    /**
     * Gets base delay based on message priority
     * Higher priority messages get shorter base delays
     */
    private fun getBaseDelayForPriority(priority: com.smsgateway.app.database.SmsPriority): Long {
        return when (priority) {
            com.smsgateway.app.database.SmsPriority.URGENT -> 500L    // 0.5 seconds
            com.smsgateway.app.database.SmsPriority.HIGH -> 1000L    // 1 second
            com.smsgateway.app.database.SmsPriority.NORMAL -> 2000L  // 2 seconds
            com.smsgateway.app.database.SmsPriority.LOW -> 5000L     // 5 seconds
        }
    }
    
    /**
     * Gets maximum delay based on message priority
     * Higher priority messages have lower maximum delays
     */
    private fun getMaxDelayForPriority(priority: com.smsgateway.app.database.SmsPriority): Long {
        return when (priority) {
            com.smsgateway.app.database.SmsPriority.URGENT -> 60000L   // 1 minute
            com.smsgateway.app.database.SmsPriority.HIGH -> 180000L   // 3 minutes
            com.smsgateway.app.database.SmsPriority.NORMAL -> 300000L // 5 minutes
            com.smsgateway.app.database.SmsPriority.LOW -> 600000L    // 10 minutes
        }
    }
    
    /**
     * Gets jitter factor based on retry strategy
     * Some strategies benefit from more jitter to prevent synchronization
     */
    private fun getJitterForStrategy(strategy: RetryStrategy): Float {
        return when (strategy) {
            RetryStrategy.EXPONENTIAL_BACKOFF -> 0.1f  // 10% jitter
            RetryStrategy.LINEAR_BACKOFF -> 0.05f      // 5% jitter
            RetryStrategy.FIXED_DELAY -> 0.2f          // 20% jitter (more to prevent synchronization)
            RetryStrategy.CUSTOM -> 0.1f               // Default 10% jitter
        }
    }
    
    /**
     * Suspends coroutine for the calculated delay time
     * Used in workers to wait before retry
     */
    suspend fun waitForRetryDelay(
        attempt: Int,
        strategy: RetryStrategy,
        baseDelayMs: Long = DEFAULT_BASE_DELAY_MS,
        maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
        jitterFactor: Float = DEFAULT_JITTER_FACTOR
    ) {
        val delayMs = calculateRetryDelay(
            attempt = attempt,
            strategy = strategy,
            baseDelayMs = baseDelayMs,
            maxDelayMs = maxDelayMs,
            jitterFactor = jitterFactor
        )
        
        Log.d(TAG, "Waiting ${delayMs}ms before retry attempt #$attempt")
        delay(delayMs)
    }
}