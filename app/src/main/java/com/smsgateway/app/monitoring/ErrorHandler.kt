package com.smsgateway.app.monitoring

import android.content.Context
import android.os.Build
import com.smsgateway.app.monitoring.models.*
import com.smsgateway.app.monitoring.repositories.ErrorRepository
import com.smsgateway.app.monitoring.repositories.MetricsRepository
import kotlinx.coroutines.*
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Globalny handler błędów z wzorcem Singleton
 * Odpowiada za przechwytywanie, logowanie i raportowanie błędów
 */
class ErrorHandler private constructor(
    private val context: Context,
    private val errorRepository: ErrorRepository,
    private val metricsRepository: MetricsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var errorReportingService: ErrorReportingService? = null
    
    companion object {
        @Volatile
        private var INSTANCE: ErrorHandler? = null
        
        fun getInstance(
            context: Context,
            errorRepository: ErrorRepository,
            metricsRepository: MetricsRepository
        ): ErrorHandler {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ErrorHandler(
                    context.applicationContext,
                    errorRepository,
                    metricsRepository
                ).also { INSTANCE = it }
            }
        }
        
        /**
         * Inicjalizacja domyślnego handlera
         */
        fun initialize(
            context: Context,
            errorRepository: ErrorRepository,
            metricsRepository: MetricsRepository
        ) {
            getInstance(context, errorRepository, metricsRepository)
        }
        
        /**
         * Globalna metoda do obsługi błędów
         */
        fun handleError(
            throwable: Throwable,
            type: ErrorType = ErrorType.UNKNOWN_ERROR,
            context: Map<String, String> = emptyMap(),
            severity: ErrorSeverity = ErrorSeverity.MEDIUM
        ) {
            INSTANCE?.handleErrorInternal(throwable, type, context, severity)
        }
        
        /**
         * Globalna metoda do logowania błędów
         */
        fun logError(
            message: String,
            type: ErrorType = ErrorType.UNKNOWN_ERROR,
            context: Map<String, String> = emptyMap(),
            severity: ErrorSeverity = ErrorSeverity.MEDIUM
        ) {
            INSTANCE?.logErrorInternal(message, type, context, severity)
        }
        
        /**
         * Globalna metoda do tworzenia błędów użytkownika
         */
        fun createUserError(
            title: String,
            message: String,
            type: ErrorType,
            severity: ErrorSeverity = ErrorSeverity.MEDIUM,
            actions: List<ErrorAction> = emptyList(),
            isDismissible: Boolean = true
        ): UserError {
            return UserError(
                title = title,
                message = message,
                type = type,
                severity = severity,
                actions = actions,
                isDismissible = isDismissible
            )
        }
    }
    
    /**
     * Ustawienie serwisu raportowania błędów
     */
    fun setErrorReportingService(service: ErrorReportingService) {
        this.errorReportingService = service
    }
    
    /**
     * Wewnętrzna metoda obsługi błędów
     */
    private fun handleErrorInternal(
        throwable: Throwable,
        type: ErrorType,
        context: Map<String, String>,
        severity: ErrorSeverity
    ) {
        try {
            val appError = createAppError(throwable, type, context, severity)
            
            // Zapis błędu w repozytorium
            scope.launch {
                try {
                    errorRepository.saveError(appError)
                    
                    // Automatyczne raportowanie krytycznych błędów
                    if (severity == ErrorSeverity.CRITICAL) {
                        reportError(appError)
                    }
                    
                    // Aktualizacja metryk
                    metricsRepository.incrementErrorCount(type)
                    
                } catch (e: Exception) {
                    // Fallback - logowanie do konsoli
                    e.printStackTrace()
                }
            }
            
        } catch (e: Exception) {
            // Fallback - logowanie do konsoli
            e.printStackTrace()
        }
    }
    
    /**
     * Wewnętrzna metoda logowania błędów
     */
    private fun logErrorInternal(
        message: String,
        type: ErrorType,
        context: Map<String, String>,
        severity: ErrorSeverity
    ) {
        try {
            val appError = AppError(
                type = type,
                severity = severity,
                message = message,
                context = context,
                deviceInfo = getDeviceInfo()
            )
            
            // Zapis błędu w repozytorium
            scope.launch {
                try {
                    errorRepository.saveError(appError)
                    metricsRepository.incrementErrorCount(type)
                } catch (e: Exception) {
                    // Fallback - logowanie do konsoli
                    e.printStackTrace()
                }
            }
            
        } catch (e: Exception) {
            // Fallback - logowanie do konsoli
            e.printStackTrace()
        }
    }
    
    /**
     * Tworzenie obiektu AppError z Throwable
     */
    private fun createAppError(
        throwable: Throwable,
        type: ErrorType,
        context: Map<String, String>,
        severity: ErrorSeverity
    ): AppError {
        val stackTrace = StringWriter().use { sw ->
            PrintWriter(sw).use { pw ->
                throwable.printStackTrace(pw)
                sw.toString()
            }
        }
        
        return AppError(
            type = type,
            severity = severity,
            message = throwable.message ?: "Unknown error",
            cause = throwable.cause?.message,
            context = context,
            stackTrace = stackTrace,
            deviceInfo = getDeviceInfo()
        )
    }
    
    /**
     * Pobieranie informacji o urządzeniu
     */
    private fun getDeviceInfo(): DeviceInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            osVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            sdkVersion = Build.VERSION.SDK_INT,
            totalMemory = memInfo.totalMem,
            availableMemory = memInfo.availMem,
            batteryLevel = getBatteryLevel(),
            isCharging = isCharging(),
            networkType = getNetworkType()
        )
    }
    
    /**
     * Pobieranie poziomu baterii
     */
    private fun getBatteryLevel(): Float {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) / 100.0f
        } catch (e: Exception) {
            0.0f
        }
    }
    
    /**
     * Sprawdzanie czy urządzenie jest ładowane
     */
    private fun isCharging(): Boolean {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            batteryManager.isCharging
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Pobieranie typu sieci
     */
    private fun getNetworkType(): String {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val activeNetwork = connectivityManager.activeNetworkInfo
            activeNetwork?.typeName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    /**
     * Raportowanie błędu do zewnętrznej usługi
     */
    private suspend fun reportError(error: AppError) {
        errorReportingService?.reportError(error)
    }
    
    /**
     * Publiczna metoda do ręcznego raportowania błędu
     */
    suspend fun reportErrorManually(error: AppError, userDescription: String? = null) {
        val reportableError = ReportableError(
            error = error,
            userDescription = userDescription,
            includeLogs = true,
            includeDeviceInfo = true
        )
        
        errorReportingService?.reportError(reportableError)
        
        // Oznaczenie błędu jako zgłoszonego
        errorRepository.markErrorAsReported(error.id)
    }
    
    /**
     * Pobieranie statystyk błędów
     */
    suspend fun getErrorStats(filter: ErrorFilter? = null): ErrorStats {
        return errorRepository.getErrorStats(filter)
    }
    
    /**
     * Pobieranie listy błędów
     */
    suspend fun getErrors(
        filter: ErrorFilter? = null,
        limit: Int = 50,
        offset: Int = 0
    ): List<AppError> {
        return errorRepository.getErrors(filter, limit, offset)
    }
    
    /**
     * Czyszczenie starych błędów
     */
    suspend fun cleanupOldErrors(retentionDays: Int = 30) {
        errorRepository.cleanupOldErrors(retentionDays)
    }
    
    /**
     * Zwalnianie zasobów
     */
    fun cleanup() {
        scope.cancel()
    }
}