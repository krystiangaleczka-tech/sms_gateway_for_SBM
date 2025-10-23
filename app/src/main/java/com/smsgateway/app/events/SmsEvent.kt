package com.smsgateway.app.events

import com.smsgateway.app.database.SmsMessage
import com.smsgateway.app.database.SmsStatus
import com.smsgateway.app.database.SmsPriority
import java.util.*

/**
 * Bazowa klasa dla wszystkich zdarzeń SMS
 */
abstract class SmsEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val source: String
)

/**
 * Zdarzenie dodania SMS do kolejki
 */
data class SmsQueuedEvent(
    val smsMessage: SmsMessage,
    val queuePosition: Int?,
    val estimatedSendTime: Long?,
    source: String
) : SmsEvent(source = source)

/**
 * Zdarzenie rozpoczęcia wysyłania SMS
 */
data class SmsSendingStartedEvent(
    val smsMessage: SmsMessage,
    val attemptNumber: Int,
    source: String
) : SmsEvent(source = source)

/**
 * Zdarzenie pomyślnego wysłania SMS
 */
data class SmsSentEvent(
    val smsMessage: SmsMessage,
    val sentAt: Long,
    val processingTime: Long, // czas przetwarzania w ms
    val attemptNumber: Int,
    source: String
) : SmsEvent(source = source)

/**
 * Zdarzenie nieudanego wysłania SMS
 */
data class SmsFailedEvent(
    val smsMessage: SmsMessage,
    val errorType: String,
    val errorMessage: String?,
    val willRetry: Boolean,
    val nextRetryTime: Long?,
    val attemptNumber: Int,
    source: String
) : SmsEvent(source = source)

/**
 * Zdarzenie ponowienia wysyłania SMS
 */
data class SmsRetryEvent(
    val smsMessage: SmsMessage,
    val retryStrategy: String,
    val retryDelay: Long,
    val attemptNumber: Int,
    val maxRetries: Int,
    source: String
) : SmsEvent(source = source)

/**
 * Zdarzenie zmiany statusu SMS
 */
data class SmsStatusChangedEvent(
    val smsMessage: SmsMessage,
    val oldStatus: SmsStatus,
    val newStatus: SmsStatus,
    val reason: String?,
    source: String
) : SmsEvent(source = source)

/**
 * Zdarzenie zmiany priorytetu SMS
 */
data class SmsPriorityChangedEvent(
    val smsMessage: SmsMessage,
    val oldPriority: SmsPriority,
    val newPriority: SmsPriority,
    val oldPosition: Int?,
    val newPosition: Int?,
    source: String
) : SmsEvent(source = source)

/**
 * Zdarzenie planowania SMS
 */
data class SmsScheduledEvent(
    val smsMessage: SmsMessage,
    val scheduledAt: Long,
    val estimatedSendTime: Long,
    source: String
) : SmsEvent(source = source)

/**
 * Zdarzenie anulowania SMS
 */
data class SmsCancelledEvent(
    val smsMessage: SmsMessage,
    val reason: String?,
    source: String
) : SmsEvent(source = source)

/**
 * Zdarzenie czyszczenia kolejki
 */
data class QueueClearedEvent(
    val clearedCount: Int,
    val clearedByPriority: Map<SmsPriority, Int>,
    val reason: String,
    source: String
) : SmsEvent(source = source)

/**
 * Zdarzenie pauzy/wznowienia kolejki
 */
data class QueueStateChangedEvent(
    val isPaused: Boolean,
    val reason: String?,
    val queuedMessagesCount: Int,
    source: String
) : SmsEvent(source = source)

/**
 * Zdarzenie konserwacji kolejki
 */
data class QueueMaintenanceEvent(
    val maintenanceType: String,
    val messagesProcessed: Int,
    val messagesCleaned: Int,
    val duration: Long,
    source: String
) : SmsEvent(source = source)

/**
 * Zdarzenie zdrowia systemu
 */
data class SystemHealthEvent(
    val healthStatus: String,
    val issues: List<String>,
    val recommendations: List<String>,
    source: String
) : SmsEvent(source = source)

/**
 * Zdarzenie metryki wydajności
 */
data class PerformanceMetricEvent(
    val metricType: String,
    val metricValue: Double,
    val unit: String,
    val tags: Map<String, String>,
    source: String
) : SmsEvent(source = source)

/**
 * Zdarzenie błędu systemowego
 */
data class SystemErrorEvent(
    val errorType: String,
    val errorMessage: String,
    val stackTrace: String?,
    val context: Map<String, Any>,
    source: String
) : SmsEvent(source = source)

/**
 * Zdarzenie alertu
 */
data class AlertEvent(
    val alertType: String,
    val severity: String, // LOW, MEDIUM, HIGH, CRITICAL
    val title: String,
    val message: String,
    val data: Map<String, Any>?,
    source: String
) : SmsEvent(source = source)

/**
 * Typy zdarzeń do filtrowania
 */
enum class SmsEventType {
    SMS_QUEUED,
    SMS_SENDING_STARTED,
    SMS_SENT,
    SMS_FAILED,
    SMS_RETRY,
    SMS_STATUS_CHANGED,
    SMS_PRIORITY_CHANGED,
    SMS_SCHEDULED,
    SMS_CANCELLED,
    QUEUE_CLEARED,
    QUEUE_STATE_CHANGED,
    QUEUE_MAINTENANCE,
    SYSTEM_HEALTH,
    PERFORMANCE_METRIC,
    SYSTEM_ERROR,
    ALERT
}

/**
 * Pomocnicze funkcje do określania typu zdarzenia
 */
fun SmsEvent.getEventType(): SmsEventType {
    return when (this) {
        is SmsQueuedEvent -> SmsEventType.SMS_QUEUED
        is SmsSendingStartedEvent -> SmsEventType.SMS_SENDING_STARTED
        is SmsSentEvent -> SmsEventType.SMS_SENT
        is SmsFailedEvent -> SmsEventType.SMS_FAILED
        is SmsRetryEvent -> SmsEventType.SMS_RETRY
        is SmsStatusChangedEvent -> SmsEventType.SMS_STATUS_CHANGED
        is SmsPriorityChangedEvent -> SmsEventType.SMS_PRIORITY_CHANGED
        is SmsScheduledEvent -> SmsEventType.SMS_SCHEDULED
        is SmsCancelledEvent -> SmsEventType.SMS_CANCELLED
        is QueueClearedEvent -> SmsEventType.QUEUE_CLEARED
        is QueueStateChangedEvent -> SmsEventType.QUEUE_STATE_CHANGED
        is QueueMaintenanceEvent -> SmsEventType.QUEUE_MAINTENANCE
        is SystemHealthEvent -> SmsEventType.SYSTEM_HEALTH
        is PerformanceMetricEvent -> SmsEventType.PERFORMANCE_METRIC
        is SystemErrorEvent -> SmsEventType.SYSTEM_ERROR
        is AlertEvent -> SmsEventType.ALERT
        else -> throw IllegalArgumentException("Unknown event type: ${this::class.simpleName}")
    }
}

/**
 * Pomocnicze funkcje do formatowania zdarzeń
 */
fun SmsEvent.toLogString(): String {
    return when (this) {
        is SmsQueuedEvent -> "SMS queued: ID=${smsMessage.id}, Priority=${smsMessage.priority}, Position=$queuePosition"
        is SmsSendingStartedEvent -> "SMS sending started: ID=${smsMessage.id}, Attempt=$attemptNumber"
        is SmsSentEvent -> "SMS sent: ID=${smsMessage.id}, ProcessingTime=${processingTime}ms, Attempt=$attemptNumber"
        is SmsFailedEvent -> "SMS failed: ID=${smsMessage.id}, Error=$errorType, WillRetry=$willRetry, Attempt=$attemptNumber"
        is SmsRetryEvent -> "SMS retry: ID=${smsMessage.id}, Strategy=$retryStrategy, Delay=${retryDelay}ms, Attempt=$attemptNumber/$maxRetries"
        is SmsStatusChangedEvent -> "SMS status changed: ID=${smsMessage.id}, $oldStatus -> $newStatus"
        is SmsPriorityChangedEvent -> "SMS priority changed: ID=${smsMessage.id}, $oldPriority -> $newPriority"
        is SmsScheduledEvent -> "SMS scheduled: ID=${smsMessage.id}, At=$scheduledAt"
        is SmsCancelledEvent -> "SMS cancelled: ID=${smsMessage.id}, Reason=$reason"
        is QueueClearedEvent -> "Queue cleared: Count=$clearedCount, Reason=$reason"
        is QueueStateChangedEvent -> "Queue state changed: Paused=$isPaused, Reason=$reason"
        is QueueMaintenanceEvent -> "Queue maintenance: Type=$maintenanceType, Processed=$messagesProcessed, Cleaned=$messagesCleaned"
        is SystemHealthEvent -> "System health: Status=$healthStatus, Issues=${issues.size}"
        is PerformanceMetricEvent -> "Performance metric: $metricType=$metricValue$unit"
        is SystemErrorEvent -> "System error: $errorType: $errorMessage"
        is AlertEvent -> "Alert: $severity - $title: $message"
        else -> "Unknown event: ${this::class.simpleName}"
    }
}