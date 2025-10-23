package com.smsgateway.app.monitoring.models

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Błędy aplikacji
 */
@Serializable
data class AppError(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: ErrorType,
    val severity: ErrorSeverity,
    val message: String,
    val cause: String? = null,
    val context: Map<String, String> = emptyMap(),
    val stackTrace: String? = null,
    val userId: String? = null,
    val sessionId: String? = null,
    val deviceInfo: DeviceInfo? = null,
    val appVersion: String = "1.0.0",
    val isReported: Boolean = false
)

/**
 * Typy błędów
 */
@Serializable
enum class ErrorType {
    NETWORK_ERROR,
    API_ERROR,
    DATABASE_ERROR,
    VALIDATION_ERROR,
    UI_ERROR,
    SYSTEM_ERROR,
    WORKER_ERROR,
    UNKNOWN_ERROR
}

/**
 * Priorytety błędów
 */
@Serializable
enum class ErrorSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Błąd do wyświetlenia użytkownikowi
 */
@Serializable
data class UserError(
    val title: String,
    val message: String,
    val type: ErrorType,
    val severity: ErrorSeverity,
    val actions: List<ErrorAction> = emptyList(),
    val isDismissible: Boolean = true
)

/**
 * Akcje dostępne dla użytkownika
 */
@Serializable
data class ErrorAction(
    val label: String,
    val actionType: String,
    val isPrimary: Boolean = false
)

/**
 * Błąd do raportowania
 */
@Serializable
data class ReportableError(
    val error: AppError,
    val userDescription: String? = null,
    val userEmail: String? = null,
    val includeLogs: Boolean = false,
    val includeDeviceInfo: Boolean = true
)

/**
 * Informacje o urządzeniu
 */
@Serializable
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val osVersion: String,
    val sdkVersion: Int,
    val totalMemory: Long,
    val availableMemory: Long,
    val batteryLevel: Float,
    val isCharging: Boolean,
    val networkType: String
)

/**
 * Filtr błędów
 */
@Serializable
data class ErrorFilter(
    val types: Set<ErrorType> = ErrorType.values().toSet(),
    val severities: Set<ErrorSeverity> = ErrorSeverity.values().toSet(),
    val startDate: Long? = null,
    val endDate: Long? = null,
    val searchQuery: String? = null,
    val isReported: Boolean? = null
)

/**
 * Statystyki błędów
 */
@Serializable
data class ErrorStats(
    val totalErrors: Long,
    val errorsByType: Map<ErrorType, Long>,
    val errorsBySeverity: Map<ErrorSeverity, Long>,
    val errorsByHour: Map<String, Long>,
    val recentErrors: List<AppError>,
    val criticalErrors: List<AppError>
)

/**
 * Wynik raportowania błędu
 */
@Serializable
data class ErrorReportResult(
    val reportId: String,
    val status: String,
    val message: String
)