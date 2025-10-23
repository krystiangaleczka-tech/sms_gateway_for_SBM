package com.smsgateway.app.models.dto

import com.smsgateway.app.database.SmsMessage
import com.smsgateway.app.database.SmsPriority
import com.smsgateway.app.database.RetryStrategy

/**
 * DTO dla żądania dodania SMS do kolejki z priorytetem
 */
data class QueueSmsRequest(
    val phoneNumber: String,
    val message: String,
    val priority: SmsPriority = SmsPriority.NORMAL,
    val retryStrategy: RetryStrategy = RetryStrategy.EXPONENTIAL_BACKOFF,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * DTO dla odpowiedzi z informacjami o kolejce
 */
data class QueueStatusResponse(
    val id: Long,
    val status: String,
    val queuePosition: Int?,
    val estimatedSendTime: Long?,
    val priority: SmsPriority,
    val retryCount: Int,
    val maxRetries: Int
)

/**
 * DTO dla statystyk kolejki
 */
data class QueueStatsResponse(
    val totalMessages: Int,
    val queuedMessages: Int,
    val scheduledMessages: Int,
    val sendingMessages: Int,
    val sentMessages: Int,
    val failedMessages: Int,
    val averageWaitTime: Long,
    val throughputPerHour: Int,
    val errorRate: Double,
    val queueSizeByPriority: Map<SmsPriority, Int>
)

/**
 * DTO dla żądania zarządzania kolejką
 */
data class QueueControlRequest(
    val action: QueueAction,
    val reason: String? = null
)

/**
 * Akcje zarządzania kolejką
 */
enum class QueueAction {
    PAUSE,
    RESUME,
    CLEAR,
    REORGANIZE
}

/**
 * DTO dla odpowiedzi zdrowia systemu
 */
data class HealthStatusResponse(
    val status: String,
    val smsPermission: Boolean,
    val simStatus: String,
    val networkConnectivity: Boolean,
    val queueHealth: QueueHealthResponse,
    val lastCheckTime: Long,
    val issues: List<String>,
    val recommendations: List<String>
)

/**
 * DTO dla zdrowia kolejki
 */
data class QueueHealthResponse(
    val status: String,
    val size: Int,
    val processingRate: Double,
    val averageWaitTime: Long,
    val errorRate: Double
)

/**
 * DTO dla odpowiedzi metryk
 */
data class MetricsResponse(
    val timestamp: Long,
    val queueMetrics: QueueMetricsResponse,
    val performanceMetrics: PerformanceMetricsResponse,
    val customMetrics: Map<String, Any>
)

/**
 * DTO dla metryk kolejki
 */
data class QueueMetricsResponse(
    val totalMessages: Int,
    val queuedMessages: Int,
    val scheduledMessages: Int,
    val sendingMessages: Int,
    val sentMessages: Int,
    val failedMessages: Int,
    val averageProcessingTime: Long,
    val throughputPerHour: Int,
    val errorRate: Double
)

/**
 * DTO dla metryk wydajnościowych
 */
data class PerformanceMetricsResponse(
    val cpuUsagePercent: Long,
    val memoryUsagePercent: Long,
    val diskUsagePercent: Long,
    val networkIOBytes: Long,
    val databaseAverageQueryTime: Double,
    val apiAverageResponseTime: Double
)

/**
 * DTO dla żądania czyszczenia kolejki
 */
data class ClearQueueRequest(
    val status: String? = null,
    val priority: SmsPriority? = null,
    val olderThan: Long? = null,
    val reason: String? = null
)

/**
 * DTO dla odpowiedzi czyszczenia kolejki
 */
data class ClearQueueResponse(
    val clearedCount: Int,
    val remainingCount: Int,
    val message: String
)

/**
 * DTO dla żądania repriorytetyzacji wiadomości
 */
data class ReprioritizeRequest(
    val messageId: Long,
    val newPriority: SmsPriority,
    val reason: String? = null
)

/**
 * DTO dla odpowiedzi repriorytetyzacji
 */
data class ReprioritizeResponse(
    val messageId: Long,
    val oldPriority: SmsPriority,
    val newPriority: SmsPriority,
    val oldPosition: Int?,
    val newPosition: Int?,
    val success: Boolean,
    val message: String
)

/**
 * Funkcje rozszerzające do konwersji między modelami a DTO
 */

/**
 * Konwertuje SmsMessage na QueueStatusResponse
 */
fun SmsMessage.toQueueStatusResponse(): QueueStatusResponse {
    return QueueStatusResponse(
        id = id,
        status = status.name,
        queuePosition = queuePosition,
        estimatedSendTime = scheduledAt,
        priority = priority,
        retryCount = retryCount,
        maxRetries = maxRetries
    )
}

/**
 * Konwertuje listę SmsMessage na listę QueueStatusResponse
 */
fun List<SmsMessage>.toQueueStatusResponseList(): List<QueueStatusResponse> {
    return this.map { it.toQueueStatusResponse() }
}