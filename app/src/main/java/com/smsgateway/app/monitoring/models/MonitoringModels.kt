package com.smsgateway.app.monitoring.models

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Metryki systemowe
 */
@Serializable
data class SystemMetrics(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val cpuUsage: Float,
    val memoryUsage: MemoryUsage,
    val diskUsage: DiskUsage,
    val networkMetrics: NetworkMetrics,
    val appMetrics: AppMetrics,
    val databaseMetrics: DatabaseMetrics
)

/**
 * Użycie pamięci
 */
@Serializable
data class MemoryUsage(
    val totalMemory: Long,
    val usedMemory: Long,
    val freeMemory: Long,
    val maxMemory: Long,
    val heapSize: Long,
    val heapUsage: Float
)

/**
 * Użycie dysku
 */
@Serializable
data class DiskUsage(
    val totalSpace: Long,
    val freeSpace: Long,
    val usedSpace: Long,
    val appCacheSize: Long,
    val logFilesSize: Long
)

/**
 * Metryki sieciowe
 */
@Serializable
data class NetworkMetrics(
    val networkType: String,
    val isConnected: Boolean,
    val signalStrength: Int,
    val downloadSpeed: Float,
    val uploadSpeed: Float,
    val latency: Int,
    val packetLoss: Float,
    val activeConnections: Int
)

/**
 * Metryki aplikacji
 */
@Serializable
data class AppMetrics(
    val uptime: Long,
    val activeUsers: Int,
    val queuedMessages: Int,
    val sentMessages: Int,
    val failedMessages: Int,
    val averageResponseTime: Float,
    val totalRequests: Long,
    val errorRate: Float,
    val cacheHitRate: Float
)

/**
 * Metryki bazy danych
 */
@Serializable
data class DatabaseMetrics(
    val connectionPoolSize: Int,
    val activeConnections: Int,
    val idleConnections: Int,
    val queryCount: Long,
    val averageQueryTime: Float,
    val slowQueries: Int,
    val deadlocks: Int,
    val cacheHitRate: Float,
    val databaseSize: Long
)

/**
 * Wydajność systemu
 */
@Serializable
data class PerformanceMetrics(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val responseTime: Float,
    val throughput: Float,
    val errorRate: Float,
    val availability: Float,
    val resourceUtilization: Float,
    val userSatisfaction: Float
)

/**
 * Stan zdrowia systemu
 */
@Serializable
data class HealthStatus(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val status: HealthState,
    val components: Map<String, ComponentHealth>,
    val checks: List<HealthCheck>,
    val uptime: Long,
    val version: String,
    val environment: String
)

/**
 * Stany zdrowia
 */
@Serializable
enum class HealthState {
    HEALTHY,
    DEGRADED,
    UNHEALTHY,
    CRITICAL
}

/**
 * Zdrowie komponentu
 */
@Serializable
data class ComponentHealth(
    val name: String,
    val status: HealthState,
    val message: String? = null,
    val lastChecked: Long = System.currentTimeMillis(),
    val responseTime: Float? = null,
    val details: Map<String, String> = emptyMap()
)

/**
 * Sprawdzenie zdrowia
 */
@Serializable
data class HealthCheck(
    val name: String,
    val status: HealthState,
    val message: String? = null,
    val duration: Long,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Alert systemowy
 */
@Serializable
data class SystemAlert(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val severity: AlertSeverity,
    val type: AlertType,
    val title: String,
    val message: String,
    val source: String,
    val context: Map<String, String> = emptyMap(),
    val isAcknowledged: Boolean = false,
    val acknowledgedBy: String? = null,
    val acknowledgedAt: Long? = null,
    val resolvedAt: Long? = null
)

/**
 * Priorytety alertów
 */
@Serializable
enum class AlertSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

/**
 * Typy alertów
 */
@Serializable
enum class AlertType {
    SYSTEM_PERFORMANCE,
    DATABASE_ISSUE,
    NETWORK_PROBLEM,
    SECURITY_ALERT,
    RESOURCE_EXHAUSTION,
    SERVICE_UNAVAILABLE,
    CUSTOM
}

/**
 * Filtr alertów
 */
@Serializable
data class AlertFilter(
    val severities: Set<AlertSeverity> = AlertSeverity.values().toSet(),
    val types: Set<AlertType> = AlertType.values().toSet(),
    val isAcknowledged: Boolean? = null,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val searchQuery: String? = null
)

/**
 * Dashboard danych
 */
@Serializable
data class DashboardData(
    val systemMetrics: SystemMetrics,
    val healthStatus: HealthStatus,
    val recentAlerts: List<SystemAlert>,
    val errorStats: ErrorStats,
    val performanceMetrics: List<PerformanceMetrics>,
    val uptimeHistory: List<UptimeRecord>
)

/**
 * Rekord czasu pracy
 */
@Serializable
data class UptimeRecord(
    val timestamp: Long,
    val uptime: Long,
    val availability: Float
)

/**
 * Konfiguracja monitoringu
 */
@Serializable
data class MonitoringConfig(
    val isEnabled: Boolean = true,
    val metricsInterval: Long = 60000L, // 1 minuta
    val healthCheckInterval: Long = 30000L, // 30 sekund
    val alertingEnabled: Boolean = true,
    val retentionDays: Int = 30,
    val thresholds: MonitoringThresholds = MonitoringThresholds()
)

/**
 * Progi alertowania
 */
@Serializable
data class MonitoringThresholds(
    val cpuUsageThreshold: Float = 80.0f,
    val memoryUsageThreshold: Float = 85.0f,
    val diskUsageThreshold: Float = 90.0f,
    val errorRateThreshold: Float = 5.0f,
    val responseTimeThreshold: Float = 1000.0f,
    val availabilityThreshold: Float = 99.0f
)