package com.smsgateway.app.api.models

import kotlinx.serialization.Serializable
import java.util.Date

/**
 * Modele danych API zgodne z backendem
 */

/**
 * Model wiadomości SMS z API
 */
@Serializable
data class SmsMessageApi(
    val id: String,
    val recipient: String,
    val content: String,
    val status: SmsStatus,
    val priority: SmsPriority,
    val createdAt: String,
    val updatedAt: String,
    val scheduledAt: String? = null,
    val sentAt: String? = null,
    val retryCount: Int = 0,
    val errorMessage: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Status wiadomości SMS
 */
@Serializable
enum class SmsStatus {
    QUEUED,
    SCHEDULED,
    PROCESSING,
    SENT,
    DELIVERED,
    FAILED,
    CANCELED
}

/**
 * Priorytet wiadomości SMS
 */
@Serializable
enum class SmsPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}

/**
 * Żądanie wysłania SMS
 */
@Serializable
data class SendSmsRequest(
    val recipient: String,
    val content: String,
    val priority: SmsPriority = SmsPriority.NORMAL,
    val scheduledAt: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Odpowiedź na wysłanie SMS
 */
@Serializable
data class SendSmsResponse(
    val id: String,
    val status: SmsStatus,
    val message: String,
    val queuedAt: String
)

/**
 * Odpowiedź ze statusem SMS
 */
@Serializable
data class SmsStatusResponse(
    val id: String,
    val status: SmsStatus,
    val createdAt: String,
    val updatedAt: String,
    val sentAt: String? = null,
    val errorMessage: String? = null
)

/**
 * Odpowiedź paginowana
 */
@Serializable
data class PaginatedResponse<T>(
    val data: List<T>,
    val pagination: PaginationInfo
)

/**
 * Informacje o paginacji
 */
@Serializable
data class PaginationInfo(
    val page: Int,
    val limit: Int,
    val total: Int,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
)

/**
 * Statystyki kolejki
 */
@Serializable
data class QueueStatsApi(
    val totalMessages: Int,
    val queuedMessages: Int,
    val processingMessages: Int,
    val sentMessages: Int,
    val failedMessages: Int,
    val scheduledMessages: Int,
    val averageProcessingTime: Double, // w sekundach
    val lastUpdated: String
)

/**
 * Status zdrowia systemu
 */
@Serializable
data class SystemHealthApi(
    val status: HealthStatus,
    val timestamp: String,
    val uptime: Long, // w sekundach
    val version: String
)

/**
 * Status zdrowia
 */
@Serializable
enum class HealthStatus {
    HEALTHY,
    DEGRADED,
    UNHEALTHY
}

/**
 * Szczegółowy status zdrowia systemu
 */
@Serializable
data class DetailedHealthApi(
    val overall: HealthStatus,
    val components: Map<String, ComponentHealth>,
    val timestamp: String,
    val uptime: Long,
    val version: String
)

/**
 * Status komponentu systemu
 */
@Serializable
data class ComponentHealth(
    val status: HealthStatus,
    val message: String? = null,
    val details: Map<String, String> = emptyMap(),
    val lastChecked: String
)

/**
 * Status zdrowia bazy danych
 */
@Serializable
data class DatabaseHealthApi(
    val status: HealthStatus,
    val connectionPool: ConnectionPoolInfo,
    val responseTime: Long, // w milisekundach
    val lastChecked: String
)

/**
 * Informacje o puli połączeń bazy danych
 */
@Serializable
data class ConnectionPoolInfo(
    val activeConnections: Int,
    val idleConnections: Int,
    val totalConnections: Int,
    val maxConnections: Int
)

/**
 * Status zdrowia kolejki
 */
@Serializable
data class QueueHealthApi(
    val status: HealthStatus,
    val queueSize: Int,
    val processingRate: Double, // wiadomości na sekundę
    val errorRate: Double, // procent błędów
    val lastProcessedAt: String? = null,
    val lastChecked: String
)

/**
 * Status zdrowia usługi SMS
 */
@Serializable
data class SmsServiceHealthApi(
    val status: HealthStatus,
    val provider: String,
    val rateLimit: RateLimitInfo,
    val lastSentAt: String? = null,
    val lastChecked: String
)

/**
 * Informacje o limicie速率
 */
@Serializable
data class RateLimitInfo(
    val current: Int,
    val max: Int,
    val resetAt: String
)

/**
 * Metryki wydajności systemu
 */
@Serializable
data class PerformanceMetricsApi(
    val cpuUsage: Double, // procent
    val memoryUsage: MemoryUsage,
    val diskUsage: DiskUsage,
    val networkThroughput: NetworkThroughput,
    val responseTime: ResponseTimeMetrics,
    val timestamp: String
)

/**
 * Użycie pamięci
 */
@Serializable
data class MemoryUsage(
    val used: Long, // w bajtach
    val total: Long, // w bajtach
    val percentage: Double
)

/**
 * Użycie dysku
 */
@Serializable
data class DiskUsage(
    val used: Long, // w bajtach
    val total: Long, // w bajtach
    val percentage: Double
)

/**
 * Przepustowość sieci
 */
@Serializable
data class NetworkThroughput(
    val inbound: Long, // bajty na sekundę
    val outbound: Long // bajty na sekundę
)

/**
 * Metryki czasu odpowiedzi
 */
@Serializable
data class ResponseTimeMetrics(
    val average: Long, // w milisekundach
    val median: Long, // w milisekundach
    val p95: Long, // 95 percentyl w milisekundach
    val p99: Long // 99 percentyl w milisekundach
)

/**
 * Status WorkManagera
 */
@Serializable
data class WorkManagerHealthApi(
    val status: HealthStatus,
    val runningWorkers: Int,
    val enqueuedWorkers: Int,
    val failedWorkers: Int,
    val lastCompletedAt: String? = null,
    val lastChecked: String
)

/**
 * Status pamięci systemowej
 */
@Serializable
data class MemoryHealthApi(
    val status: HealthStatus,
    val heapUsage: MemoryUsage,
    val nonHeapUsage: MemoryUsage,
    val gcInfo: GcInfo,
    val lastChecked: String
)

/**
 * Informacje o garbage collectorze
 */
@Serializable
data class GcInfo(
    val collectionCount: Long,
    val collectionTime: Long, // w milisekundach
    val lastGcAt: String? = null
)

/**
 * Status usług zewnętrznych
 */
@Serializable
data class ExternalServicesHealthApi(
    val status: HealthStatus,
    val services: Map<String, ExternalServiceHealth>,
    val lastChecked: String
)

/**
 * Status usługi zewnętrznej
 */
@Serializable
data class ExternalServiceHealth(
    val status: HealthStatus,
    val responseTime: Long, // w milisekundach
    val lastChecked: String,
    val errorMessage: String? = null
)

/**
 * Wynik diagnostyki systemu
 */
@Serializable
data class DiagnosticResultApi(
    val overall: HealthStatus,
    val tests: List<DiagnosticTest>,
    val timestamp: String,
    val summary: String
)

/**
 * Wynik pojedynczego testu diagnostycznego
 */
@Serializable
data class DiagnosticTest(
    val name: String,
    val status: HealthStatus,
    val message: String,
    val duration: Long, // w milisekundach
    val details: Map<String, String> = emptyMap()
)

/**
 * Log systemowy
 */
@Serializable
data class SystemLogApi(
    val timestamp: String,
    val level: LogLevel,
    val logger: String,
    val message: String,
    val thread: String? = null,
    val exception: String? = null
)

/**
 * Poziom logu
 */
@Serializable
enum class LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR
}

/**
 * Okres czasowy dla statystyk
 */
@Serializable
enum class TimeFrame {
    LAST_HOUR,
    LAST_DAY,
    LAST_WEEK,
    LAST_MONTH
}

/**
 * Statystyki błędów systemu
 */
@Serializable
data class ErrorStatsApi(
    val totalErrors: Int,
    val errorsByType: Map<String, Int>,
    val errorsByComponent: Map<String, Int>,
    val errorRate: Double, // błędy na minutę
    val timeFrame: TimeFrame,
    val timestamp: String
)

/**
 * Odpowiedź błędu API
 */
@Serializable
data class ApiErrorResponse(
    val error: String,
    val message: String,
    val timestamp: String,
    val path: String? = null
)

/**
 * Wyjątek dla błędów API
 */
class ApiException(message: String, val statusCode: Int = 500) : Exception(message)