package com.smsgateway.app.health

/**
 * Data class representing the overall health status of the SMS Gateway system
 * Contains comprehensive information about system components and their status
 */
data class SystemHealth(
    /**
     * Overall health status of the system
     */
    val overallStatus: HealthStatus,
    
    /**
     * Whether SMS permissions are granted
     */
    val smsPermission: Boolean,
    
    /**
     * Status of the SIM card
     */
    val simStatus: String,
    
    /**
     * Whether network connectivity is available
     */
    val networkConnectivity: Boolean,
    
    /**
     * Health status of the SMS queue
     */
    val queueHealth: QueueHealth,
    
    /**
     * Timestamp when the health check was performed
     */
    val lastCheckTime: Long
) {
    /**
     * Checks if the system is in a healthy state
     */
    fun isHealthy(): Boolean = overallStatus == HealthStatus.HEALTHY
    
    /**
     * Checks if the system has any critical issues
     */
    fun hasCriticalIssues(): Boolean = overallStatus == HealthStatus.CRITICAL || overallStatus == HealthStatus.DOWN
    
    /**
     * Checks if the system has any warnings
     */
    fun hasWarnings(): Boolean = overallStatus == HealthStatus.WARNING
    
    /**
     * Gets a summary of the health status
     */
    fun getSummary(): String {
        return when (overallStatus) {
            HealthStatus.HEALTHY -> "System is operating normally"
            HealthStatus.WARNING -> "System has warnings that may affect performance"
            HealthStatus.CRITICAL -> "System has critical issues that need immediate attention"
            HealthStatus.DOWN -> "System is down and not functioning"
        }
    }
    
    /**
     * Gets a list of issues found during health check
     */
    fun getIssues(): List<String> {
        val issues = mutableListOf<String>()
        
        if (!smsPermission) {
            issues.add("SMS permissions are not granted")
        }
        
        if (simStatus != "READY") {
            issues.add("SIM card status: $simStatus")
        }
        
        if (!networkConnectivity) {
            issues.add("No network connectivity")
        }
        
        if (queueHealth.status != HealthStatus.HEALTHY) {
            issues.add("Queue health: ${queueHealth.status}")
        }
        
        return issues
    }
    
    /**
     * Gets recommendations based on the health status
     */
    fun getRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (!smsPermission) {
            recommendations.add("Grant SMS permissions in app settings")
        }
        
        when (simStatus) {
            "ABSENT" -> recommendations.add("Insert a valid SIM card")
            "PIN_REQUIRED" -> recommendations.add("Enter SIM PIN")
            "PUK_REQUIRED" -> recommendations.add("Enter SIM PUK code")
            "NETWORK_LOCKED" -> recommendations.add("Contact carrier to unlock SIM")
            "NOT_READY" -> recommendations.add("Wait for SIM to initialize")
            "ERROR" -> recommendations.add("Restart device and check SIM")
        }
        
        if (!networkConnectivity) {
            recommendations.add("Check network connection")
        }
        
        if (queueHealth.status == HealthStatus.CRITICAL) {
            recommendations.add("Queue is critically full - consider increasing processing capacity")
        } else if (queueHealth.status == HealthStatus.WARNING) {
            recommendations.add("Queue has warnings - monitor performance")
        }
        
        return recommendations
    }
    
    /**
     * Creates a copy of this SystemHealth with updated overall status
     */
    fun withOverallStatus(status: HealthStatus): SystemHealth {
        return copy(overallStatus = status)
    }
    
    /**
     * Creates a copy of this SystemHealth with updated SMS permission status
     */
    fun withSmsPermission(smsPermission: Boolean): SystemHealth {
        return copy(smsPermission = smsPermission)
    }
    
    /**
     * Creates a copy of this SystemHealth with updated SIM status
     */
    fun withSimStatus(simStatus: String): SystemHealth {
        return copy(simStatus = simStatus)
    }
    
    /**
     * Creates a copy of this SystemHealth with updated network connectivity
     */
    fun withNetworkConnectivity(networkConnectivity: Boolean): SystemHealth {
        return copy(networkConnectivity = networkConnectivity)
    }
    
    /**
     * Creates a copy of this SystemHealth with updated queue health
     */
    fun withQueueHealth(queueHealth: QueueHealth): SystemHealth {
        return copy(queueHealth = queueHealth)
    }
    
    /**
     * Creates a copy of this SystemHealth with updated check time
     */
    fun withLastCheckTime(lastCheckTime: Long): SystemHealth {
        return copy(lastCheckTime = lastCheckTime)
    }
    
    companion object {
        /**
         * Creates a default healthy SystemHealth object
         */
        fun createHealthy(): SystemHealth {
            return SystemHealth(
                overallStatus = HealthStatus.HEALTHY,
                smsPermission = true,
                simStatus = "READY",
                networkConnectivity = true,
                queueHealth = QueueHealth.createHealthy(),
                lastCheckTime = System.currentTimeMillis()
            )
        }
        
        /**
         * Creates a default unhealthy SystemHealth object
         */
        fun createUnhealthy(reason: String): SystemHealth {
            return SystemHealth(
                overallStatus = HealthStatus.DOWN,
                smsPermission = false,
                simStatus = "UNKNOWN",
                networkConnectivity = false,
                queueHealth = QueueHealth.createUnhealthy(reason),
                lastCheckTime = System.currentTimeMillis()
            )
        }
    }
}

/**
 * Data class representing the health status of the SMS queue
 */
data class QueueHealth(
    /**
     * Health status of the queue
     */
    val status: HealthStatus,
    
    /**
     * Current size of the queue
     */
    val size: Int,
    
    /**
     * Processing rate (messages per minute)
     */
    val processingRate: Double,
    
    /**
     * Average wait time in milliseconds
     */
    val averageWaitTime: Long,
    
    /**
     * Throughput per hour
     */
    val throughputPerHour: Int,
    
    /**
     * Error rate (0.0 to 1.0)
     */
    val errorRate: Double
) {
    /**
     * Checks if the queue is healthy
     */
    fun isHealthy(): Boolean = status == HealthStatus.HEALTHY
    
    /**
     * Checks if the queue is overloaded
     */
    fun isOverloaded(): Boolean = status == HealthStatus.CRITICAL || status == HealthStatus.DOWN
    
    /**
     * Checks if the queue has performance issues
     */
    fun hasPerformanceIssues(): Boolean = status == HealthStatus.WARNING
    
    /**
     * Gets a summary of queue health
     */
    fun getSummary(): String {
        return when (status) {
            HealthStatus.HEALTHY -> "Queue is processing normally"
            HealthStatus.WARNING -> "Queue has performance warnings"
            HealthStatus.CRITICAL -> "Queue is critically overloaded"
            HealthStatus.DOWN -> "Queue is not processing"
        }
    }
    
    companion object {
        /**
         * Creates a default healthy QueueHealth object
         */
        fun createHealthy(): QueueHealth {
            return QueueHealth(
                status = HealthStatus.HEALTHY,
                size = 0,
                processingRate = 0.0,
                averageWaitTime = 0L,
                throughputPerHour = 0,
                errorRate = 0.0
            )
        }
        
        /**
         * Creates a default unhealthy QueueHealth object
         */
        fun createUnhealthy(reason: String): QueueHealth {
            return QueueHealth(
                status = HealthStatus.DOWN,
                size = 0,
                processingRate = 0.0,
                averageWaitTime = 0L,
                throughputPerHour = 0,
                errorRate = 1.0
            )
        }
    }
}

/**
 * Enum representing different health status levels
 */
enum class HealthStatus {
    /**
     * System is operating normally
     */
    HEALTHY,
    
    /**
     * System has warnings but is still functional
     */
    WARNING,
    
    /**
     * System has critical issues that need immediate attention
     */
    CRITICAL,
    
    /**
     * System is down and not functioning
     */
    DOWN;
    
    /**
     * Gets the severity level of this health status (higher is more severe)
     */
    fun getSeverityLevel(): Int {
        return when (this) {
            HEALTHY -> 0
            WARNING -> 1
            CRITICAL -> 2
            DOWN -> 3
        }
    }
    
    /**
     * Gets a color code for this health status (for UI display)
     */
    fun getColorCode(): String {
        return when (this) {
            HEALTHY -> "#4CAF50"  // Green
            WARNING -> "#FF9800"  // Orange
            CRITICAL -> "#F44336" // Red
            DOWN -> "#9E9E9E"     // Grey
        }
    }
    
    /**
     * Gets a human-readable description
     */
    fun getDescription(): String {
        return when (this) {
            HEALTHY -> "System is operating normally"
            WARNING -> "System has warnings that may affect performance"
            CRITICAL -> "System has critical issues that need immediate attention"
            DOWN -> "System is down and not functioning"
        }
    }
    
    /**
     * Checks if this status is worse than another status
     */
    fun isWorseThan(other: HealthStatus): Boolean {
        return this.getSeverityLevel() > other.getSeverityLevel()
    }
    
    /**
     * Gets the worst status from a list of statuses
     */
    companion object {
        fun getWorstStatus(statuses: List<HealthStatus>): HealthStatus {
            return statuses.maxByOrNull { it.getSeverityLevel() } ?: HEALTHY
        }
    }
}