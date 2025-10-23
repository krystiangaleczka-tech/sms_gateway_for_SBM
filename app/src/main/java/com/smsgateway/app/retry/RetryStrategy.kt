package com.smsgateway.app.retry

/**
 * Enum defining different retry strategies for SMS sending
 * Each strategy has different delay calculation methods
 */
enum class RetryStrategy {
    /**
     * Exponential backoff strategy
     * Delay increases exponentially with each attempt
     * Formula: delay = baseDelay * 2^attempt
     * 
     * Use case: Good for network errors, rate limiting, and temporary system failures
     * Pros: Quickly backs off to reduce load, good for self-healing systems
     * Cons: Can lead to long delays for high attempt numbers
     */
    EXPONENTIAL_BACKOFF {
        override fun getDescription(): String {
            return "Exponential backoff: delay increases exponentially with each attempt (baseDelay * 2^attempt)"
        }
        
        override fun getRecommendedUseCases(): List<String> {
            return listOf(
                "Network connectivity issues",
                "Rate limiting",
                "Temporary service unavailability",
                "System overload situations"
            )
        }
    },
    
    /**
     * Linear backoff strategy
     * Delay increases linearly with each attempt
     * Formula: delay = baseDelay * (attempt + 1)
     * 
     * Use case: Good for predictable, temporary issues
     * Pros: Predictable delays, simpler to understand
     * Cons: May not back off quickly enough for severe issues
     */
    LINEAR_BACKOFF {
        override fun getDescription(): String {
            return "Linear backoff: delay increases linearly with each attempt (baseDelay * (attempt + 1))"
        }
        
        override fun getRecommendedUseCases(): List<String> {
            return listOf(
                "Predictable temporary failures",
                "Resource contention",
                "Moderate system load"
            )
        }
    },
    
    /**
     * Fixed delay strategy
     * Always uses the same delay between attempts
     * Formula: delay = baseDelay
     * 
     * Use case: Good for issues that are likely to resolve quickly
     * Pros: Simple, consistent retry timing
     * Cons: Can overwhelm system if many retries happen simultaneously
     */
    FIXED_DELAY {
        override fun getDescription(): String {
            return "Fixed delay: always uses the same delay between attempts (baseDelay)"
        }
        
        override fun getRecommendedUseCases(): List<String> {
            return listOf(
                "Quick recovery expected",
                "Simple retry logic needed",
                "Low-volume retry scenarios"
            )
        }
    },
    
    /**
     * Custom strategy
     * Allows for completely custom delay calculation
     * Uses a provided function to calculate delay
     * 
     * Use case: Complex retry scenarios with specific requirements
     * Pros: Maximum flexibility
     * Cons: More complex to implement and test
     */
    CUSTOM {
        override fun getDescription(): String {
            return "Custom strategy: uses a custom delay calculation function"
        }
        
        override fun getRecommendedUseCases(): List<String> {
            return listOf(
                "Complex retry requirements",
                "Business-specific retry logic",
                "Adaptive retry based on external factors"
            )
        }
    };
    
    /**
     * Gets a human-readable description of the retry strategy
     */
    abstract fun getDescription(): String
    
    /**
     * Gets recommended use cases for this retry strategy
     */
    abstract fun getRecommendedUseCases(): List<String>
    
    /**
     * Gets the default base delay for this strategy in milliseconds
     */
    fun getDefaultBaseDelay(): Long {
        return when (this) {
            EXPONENTIAL_BACKOFF -> 2000L  // 2 seconds
            LINEAR_BACKOFF -> 3000L       // 3 seconds
            FIXED_DELAY -> 5000L          // 5 seconds
            CUSTOM -> 2000L               // 2 seconds (default for custom)
        }
    }
    
    /**
     * Gets the default maximum delay for this strategy in milliseconds
     */
    fun getDefaultMaxDelay(): Long {
        return when (this) {
            EXPONENTIAL_BACKOFF -> 300000L  // 5 minutes
            LINEAR_BACKOFF -> 600000L       // 10 minutes
            FIXED_DELAY -> 300000L          // 5 minutes
            CUSTOM -> 300000L               // 5 minutes (default for custom)
        }
    }
    
    /**
     * Gets the default maximum retry attempts for this strategy
     */
    fun getDefaultMaxRetries(): Int {
        return when (this) {
            EXPONENTIAL_BACKOFF -> 3
            LINEAR_BACKOFF -> 3
            FIXED_DELAY -> 2
            CUSTOM -> 3
        }
    }
    
    /**
     * Gets the default jitter factor for this strategy
     * Jitter helps prevent thundering herd problems
     */
    fun getDefaultJitterFactor(): Float {
        return when (this) {
            EXPONENTIAL_BACKOFF -> 0.1f  // 10% jitter
            LINEAR_BACKOFF -> 0.05f      // 5% jitter
            FIXED_DELAY -> 0.2f          // 20% jitter (more to prevent synchronization)
            CUSTOM -> 0.1f               // 10% jitter (default for custom)
        }
    }
    
    companion object {
        /**
         * Gets all available retry strategies
         */
        fun getAllStrategies(): List<RetryStrategy> {
            return values().toList()
        }
        
        /**
         * Gets retry strategy by name (case-insensitive)
         */
        fun fromString(strategyName: String): RetryStrategy? {
            return values().find { 
                it.name.equals(strategyName, ignoreCase = true) 
            }
        }
        
        /**
         * Gets the recommended strategy for a given error type
         */
        fun getRecommendedStrategyForError(errorType: String): RetryStrategy {
            return when (errorType.uppercase()) {
                "NETWORK_ERROR", "TIMEOUT", "SERVICE_UNAVAILABLE" -> EXPONENTIAL_BACKOFF
                "RATE_LIMITED" -> EXPONENTIAL_BACKOFF
                "SIM_BUSY", "NO_SIGNAL" -> LINEAR_BACKOFF
                "TEMPORARY_FAILURE" -> FIXED_DELAY
                else -> EXPONENTIAL_BACKOFF  // Default to exponential
            }
        }
        
        /**
         * Gets the recommended strategy for a given message priority
         */
        fun getRecommendedStrategyForPriority(priority: com.smsgateway.app.database.SmsPriority): RetryStrategy {
            return when (priority) {
                com.smsgateway.app.database.SmsPriority.URGENT -> EXPONENTIAL_BACKOFF
                com.smsgateway.app.database.SmsPriority.HIGH -> EXPONENTIAL_BACKOFF
                com.smsgateway.app.database.SmsPriority.NORMAL -> LINEAR_BACKOFF
                com.smsgateway.app.database.SmsPriority.LOW -> FIXED_DELAY
            }
        }
    }
}