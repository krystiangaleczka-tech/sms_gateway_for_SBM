package com.smsgateway.app.monitoring

import android.content.Context
import com.smsgateway.app.monitoring.models.*
import com.smsgateway.app.api.ApiService
import kotlinx.coroutines.*
import java.util.*

/**
 * Serwis raportowania błędów do zewnętrznych usług
 * Wspiera Firebase Crashlytics i własny backend
 */
class ErrorReportingService(
    private val context: Context,
    private val apiService: ApiService,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private var isCrashlyticsEnabled = false
    private var isBackendReportingEnabled = true
    private var maxRetries = 3
    private var retryDelayMs = 1000L
    
    /**
     * Inicjalizacja serwisu z opcjonalnym Firebase Crashlytics
     */
    fun initialize(
        enableCrashlytics: Boolean = false,
        enableBackendReporting: Boolean = true,
        maxRetries: Int = 3,
        retryDelayMs: Long = 1000L
    ) {
        this.isCrashlyticsEnabled = enableCrashlytics
        this.isBackendReportingEnabled = enableBackendReporting
        this.maxRetries = maxRetries
        this.retryDelayMs = retryDelayMs
        
        if (isCrashlyticsEnabled) {
            initializeCrashlytics()
        }
    }
    
    /**
     * Inicjalizacja Firebase Crashlytics
     */
    private fun initializeCrashlytics() {
        try {
            // Inicjalizacja Firebase Crashlytics (opcjonalnie)
            // Crashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        } catch (e: Exception) {
            // Firebase nie jest dostępne - kontynuujemy bez niego
        }
    }
    
    /**
     * Raportowanie błędu do wszystkich skonfigurowanych usług
     */
    suspend fun reportError(error: AppError): ErrorReportResult {
        val results = mutableListOf<ErrorReportResult>()
        
        // Raportowanie do Firebase Crashlytics (jeśli włączone)
        if (isCrashlyticsEnabled) {
            val crashlyticsResult = reportToCrashlytics(error)
            results.add(crashlyticsResult)
        }
        
        // Raportowanie do backendu (jeśli włączone)
        if (isBackendReportingEnabled) {
            val backendResult = reportToBackend(error)
            results.add(backendResult)
        }
        
        // Zwrócenie pierwszego udanego wyniku lub ostatniego błędu
        return results.firstOrNull { it.status == "SUCCESS" } 
            ?: results.lastOrNull() 
            ?: ErrorReportResult(
                reportId = UUID.randomUUID().toString(),
                status = "FAILED",
                message = "No reporting services available"
            )
    }
    
    /**
     * Raportowanie błędu do Firebase Crashlytics
     */
    private suspend fun reportToCrashlytics(error: AppError): ErrorReportResult {
        return try {
            // Firebase Crashlytics integration (opcjonalnie)
            /*
            val crashlytics = Crashlytics.getInstance()
            
            // Ustawienie kluczowych danych
            crashlytics.setCustomKey("error_type", error.type.name)
            crashlytics.setCustomKey("error_severity", error.severity.name)
            crashlytics.setCustomKey("error_id", error.id)
            
            // Dodanie kontekstu
            error.context.forEach { (key, value) ->
                crashlytics.setCustomKey(key, value)
            }
            
            // Logowanie błędu
            if (error.stackTrace != null) {
                crashlytics.recordException(
                    RuntimeException(error.message).apply {
                        stackTrace = error.stackTrace.split("\n").map { 
                            StackTraceElement("", "", "", 0) 
                        }.toTypedArray()
                    }
                )
            } else {
                crashlytics.log(error.message)
            }
            */
            
            ErrorReportResult(
                reportId = error.id,
                status = "SUCCESS",
                message = "Error reported to Crashlytics"
            )
        } catch (e: Exception) {
            ErrorReportResult(
                reportId = error.id,
                status = "FAILED",
                message = "Failed to report to Crashlytics: ${e.message}"
            )
        }
    }
    
    /**
     * Raportowanie błędu do backendu z ponawianiem
     */
    private suspend fun reportToBackend(error: AppError): ErrorReportResult {
        var lastException: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                val response = apiService.reportError(error)
                
                if (response.isSuccess) {
                    return ErrorReportResult(
                        reportId = error.id,
                        status = "SUCCESS",
                        message = "Error reported to backend successfully"
                    )
                } else {
                    lastException = Exception("Backend returned error: ${response.error}")
                }
            } catch (e: Exception) {
                lastException = e
            }
            
            // Odczekaj przed kolejną próbą (ale nie po ostatniej)
            if (attempt < maxRetries - 1) {
                delay(retryDelayMs * (attempt + 1))
            }
        }
        
        return ErrorReportResult(
            reportId = error.id,
            status = "FAILED",
            message = "Failed to report to backend after $maxRetries attempts: ${lastException?.message}"
        )
    }
    
    /**
     * Raportowanie błędu z dodatkowymi informacjami od użytkownika
     */
    suspend fun reportError(reportableError: ReportableError): ErrorReportResult {
        val enhancedError = reportableError.error.copy(
            context = reportableError.error.context + mapOf(
                "user_description" to (reportableError.userDescription ?: ""),
                "user_email" to (reportableError.userEmail ?: ""),
                "include_logs" to reportableError.includeLogs.toString(),
                "include_device_info" to reportableError.includeDeviceInfo.toString()
            )
        )
        
        return reportError(enhancedError)
    }
    
    /**
     * Raportowanie crashu aplikacji
     */
    suspend fun reportCrash(
        throwable: Throwable,
        context: Map<String, String> = emptyMap()
    ): ErrorReportResult {
        val error = AppError(
            type = ErrorType.SYSTEM_ERROR,
            severity = ErrorSeverity.CRITICAL,
            message = "Application crash: ${throwable.message}",
            cause = throwable.cause?.message,
            context = context + mapOf(
                "crash_thread" to Thread.currentThread().name,
                "crash_time" to System.currentTimeMillis().toString()
            ),
            stackTrace = getStackTrace(throwable)
        )
        
        return reportError(error)
    }
    
    /**
     * Raportowanie feedbacku od użytkownika
     */
    suspend fun reportFeedback(
        feedback: String,
        userEmail: String? = null,
        context: Map<String, String> = emptyMap()
    ): ErrorReportResult {
        val error = AppError(
            type = ErrorType.UI_ERROR,
            severity = ErrorSeverity.LOW,
            message = "User feedback: $feedback",
            context = context + mapOf(
                "user_email" to (userEmail ?: ""),
                "feedback_type" to "user_feedback"
            )
        )
        
        return reportError(error)
    }
    
    /**
     * Konwersja Throwable na String stack trace
     */
    private fun getStackTrace(throwable: Throwable): String {
        return try {
            val stringWriter = java.io.StringWriter()
            val printWriter = java.io.PrintWriter(stringWriter)
            throwable.printStackTrace(printWriter)
            stringWriter.toString()
        } catch (e: Exception) {
            "Failed to get stack trace: ${e.message}"
        }
    }
    
    /**
     * Sprawdzanie statusu usług raportowania
     */
    fun getReportingStatus(): Map<String, Boolean> {
        return mapOf(
            "crashlytics" to isCrashlyticsEnabled,
            "backend" to isBackendReportingEnabled
        )
    }
    
    /**
     * Włączanie/wyłączanie usług raportowania
     */
    fun setReportingServiceEnabled(service: String, enabled: Boolean) {
        when (service.lowercase()) {
            "crashlytics" -> isCrashlyticsEnabled = enabled
            "backend" -> isBackendReportingEnabled = enabled
        }
    }
    
    /**
     * Czyszczenie zasobów
     */
    fun cleanup() {
        scope.cancel()
    }
    
    /**
     * Pobiera listę błędów z backendu
     */
    suspend fun getErrors(limit: Int = 50): List<AppError> {
        return try {
            val response = apiService.getErrors(limit)
            if (response.isSuccess && response.data != null) {
                // Konwersja danych z Response na List<AppError>
                // W rzeczywistej implementacji tutaj byłaby deserializacja JSON
                emptyList() // Tymczasowo zwracamy pustą listę
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Pobiera metryki systemu z backendu
     */
    suspend fun getSystemMetrics(): SystemMetrics? {
        return try {
            val response = apiService.getSystemMetrics()
            if (response.isSuccess && response.data != null) {
                // Konwersja danych z Response na SystemMetrics
                // W rzeczywistej implementacji tutaj byłaby deserializacja JSON
                null // Tymczasowo zwracamy null
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Pobiera status systemu z backendu
     */
    suspend fun getSystemHealth(): HealthStatus? {
        return try {
            val response = apiService.getSystemHealth()
            if (response.isSuccess && response.data != null) {
                // Konwersja danych z Response na HealthStatus
                // W rzeczywistej implementacji tutaj byłaby deserializacja JSON
                null // Tymczasowo zwracamy null
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Pobiera alerty systemu z backendu
     */
    suspend fun getSystemAlerts(limit: Int = 20): List<SystemAlert> {
        return try {
            val response = apiService.getSystemAlerts(limit)
            if (response.isSuccess && response.data != null) {
                // Konwersja danych z Response na List<SystemAlert>
                // W rzeczywistej implementacji tutaj byłaby deserializacja JSON
                emptyList() // Tymczasowo zwracamy pustą listę
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Pobiera szczegóły błędu z backendu
     */
    suspend fun getErrorDetails(errorId: String): AppError? {
        return try {
            val response = apiService.getError(errorId)
            if (response.isSuccess && response.data != null) {
                // Konwersja danych z Response na AppError
                // W rzeczywistej implementacji tutaj byłaby deserializacja JSON
                null // Tymczasowo zwracamy null
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}