package com.smsgateway.app.retry

/**
 * Configuration class for retry policies
 * Defines how messages should be retried when they fail
 */
data class RetryPolicy(
    /**
     * The retry strategy to use (exponential backoff, linear, etc.)
     */
    val strategy: RetryStrategy,
    
    /**
     * Maximum number of retry attempts
     */
    val maxRetries: Int,
    
    /**
     * Base delay in milliseconds between retries
     */
    val baseDelayMs: Long,
    
    /**
     * Maximum delay in milliseconds between retries
     */
    val maxDelayMs: Long,
    
    /**
     * Jitter factor to add randomness to delays (0.0 to 1.0)
     * Helps prevent thundering herd problems
     */
    val jitterFactor: Float,
    
    /**
     * Custom delay calculator for CUSTOM strategy
     * Function that takes attempt number and returns delay in milliseconds
     */
    val customDelayCalculator: ((Int) -> Long)? = null
) {
    companion object {
        /**
         * Creates a default retry policy for normal priority messages
         */
        fun defaultPolicy(): RetryPolicy {
            return RetryPolicy(
                strategy = RetryStrategy.EXPONENTIAL_BACKOFF,
                maxRetries = 3,
                baseDelayMs = 2000L,  // 2 seconds
                maxDelayMs = 300000L, // 5 minutes
                jitterFactor = 0.1f   // 10% jitter
            )
        }
        
        /**
         * Creates an aggressive retry policy for high priority messages
         */
        fun aggressivePolicy(): RetryPolicy {
            return RetryPolicy(
                strategy = RetryStrategy.EXPONENTIAL_BACKOFF,
                maxRetries = 5,
                baseDelayMs = 1000L,  // 1 second
                maxDelayMs = 180000L, // 3 minutes
                jitterFactor = 0.05f  // 5% jitter
            )
        }
        
        /**
         * Creates a conservative retry policy for low priority messages
         */
        fun conservativePolicy(): RetryPolicy {
            return RetryPolicy(
                strategy = RetryStrategy.LINEAR_BACKOFF,
                maxRetries = 2,
                baseDelayMs = 5000L,  // 5 seconds
                maxDelayMs = 600000L, // 10 minutes
                jitterFactor = 0.2f   // 20% jitter
            )
        }
        
        /**
         * Creates a custom retry policy with specified delay calculator
         */
        fun customPolicy(
            maxRetries: Int,
            delayCalculator: (Int) -> Long
        ): RetryPolicy {
            return RetryPolicy(
                strategy = RetryStrategy.CUSTOM,
                maxRetries = maxRetries,
                baseDelayMs = 1000L,
                maxDelayMs = 300000L,
                jitterFactor = 0.1f,
                customDelayCalculator = delayCalculator
            )
        }
    }
    
    /**
     * Validates that the retry policy parameters are valid
     */
    fun isValid(): Boolean {
        return maxRetries >= 0 &&
               baseDelayMs > 0 &&
               maxDelayMs >= baseDelayMs &&
               jitterFactor >= 0.0f &&
               jitterFactor <= 1.0f
    }
    
    /**
     * Creates a copy of this policy with updated max retries
     */
    fun withMaxRetries(maxRetries: Int): RetryPolicy {
        return copy(maxRetries = maxRetries)
    }
    
    /**
     * Creates a copy of this policy with updated strategy
     */
    fun withStrategy(strategy: RetryStrategy): RetryPolicy {
        return copy(strategy = strategy)
    }
    
    /**
     * Creates a copy of this policy with updated base delay
     */
    fun withBaseDelay(baseDelayMs: Long): RetryPolicy {
        return copy(baseDelayMs = baseDelayMs)
    }
    
    /**
     * Creates a copy of this policy with updated max delay
     */
    fun withMaxDelay(maxDelayMs: Long): RetryPolicy {
        return copy(maxDelayMs = maxDelayMs)
    }
    
    /**
     * Creates a copy of this policy with updated jitter factor
     */
    fun withJitterFactor(jitterFactor: Float): RetryPolicy {
        return copy(jitterFactor = jitterFactor)
    }
}