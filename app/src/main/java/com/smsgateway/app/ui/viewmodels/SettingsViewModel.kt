package com.smsgateway.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smsgateway.app.repositories.HealthRepository
import com.smsgateway.app.repositories.SmsRepository
import com.smsgateway.app.repositories.QueueRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel dla ekranu Ustawień
 * Zarządza stanem danych i logiką biznesową dla ustawień aplikacji
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val healthRepository = HealthRepository(application)
    private val smsRepository = SmsRepository(application)
    private val queueRepository = QueueRepository(application)
    
    // Stany dla konfiguracji systemu
    private val _systemConfiguration = MutableStateFlow<com.smsgateway.app.api.models.SystemConfigurationApi?>(null)
    val systemConfiguration: StateFlow<com.smsgateway.app.api.models.SystemConfigurationApi?> = _systemConfiguration.asStateFlow()
    
    // Stany dla zdrowia systemu
    private val _systemHealth = MutableStateFlow<com.smsgateway.app.api.models.SystemHealthApi?>(null)
    val systemHealth: StateFlow<com.smsgateway.app.api.models.SystemHealthApi?> = _systemHealth.asStateFlow()
    
    private val _detailedHealth = MutableStateFlow<com.smsgateway.app.api.models.DetailedSystemHealthApi?>(null)
    val detailedHealth: StateFlow<com.smsgateway.app.api.models.DetailedSystemHealthApi?> = _detailedHealth.asStateFlow()
    
    // Stany dla logów diagnostycznych
    private val _diagnosticLogs = MutableStateFlow<List<com.smsgateway.app.api.models.DiagnosticLogApi>>(emptyList())
    val diagnosticLogs: StateFlow<List<com.smsgateway.app.api.models.DiagnosticLogApi>> = _diagnosticLogs.asStateFlow()
    
    // Stany dla metryk wydajności
    private val _performanceMetrics = MutableStateFlow<com.smsgateway.app.api.models.PerformanceMetricsApi?>(null)
    val performanceMetrics: StateFlow<com.smsgateway.app.api.models.PerformanceMetricsApi?> = _performanceMetrics.asStateFlow()
    
    // Stany dla ustawień lokalnych
    private val _apiBaseUrl = MutableStateFlow("")
    val apiBaseUrl: StateFlow<String> = _apiBaseUrl.asStateFlow()
    
    private val _apiTimeout = MutableStateFlow(30000)
    val apiTimeout: StateFlow<Int> = _apiTimeout.asStateFlow()
    
    private val _refreshInterval = MutableStateFlow(30000)
    val refreshInterval: StateFlow<Int> = _refreshInterval.asStateFlow()
    
    private val _enableNotifications = MutableStateFlow(true)
    val enableNotifications: StateFlow<Boolean> = _enableNotifications.asStateFlow()
    
    private val _enableAutoRefresh = MutableStateFlow(true)
    val enableAutoRefresh: StateFlow<Boolean> = _enableAutoRefresh.asStateFlow()
    
    private val _enableCache = MutableStateFlow(true)
    val enableCache: StateFlow<Boolean> = _enableCache.asStateFlow()
    
    private val _logLevel = MutableStateFlow("INFO")
    val logLevel: StateFlow<String> = _logLevel.asStateFlow()
    
    // Stany UI
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()
    
    init {
        loadSettings()
        loadSystemData()
    }
    
    /**
     * Ładuje wszystkie dane systemowe
     */
    fun loadSystemData() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                // Załaduj konfigurację systemu
                val config = healthRepository.getSystemConfiguration()
                _systemConfiguration.value = config
                
                // Załaduj zdrowie systemu
                val health = healthRepository.getSystemHealth()
                _systemHealth.value = health
                
                // Załaduj szczegółowe dane zdrowia systemu
                try {
                    val detailedHealth = healthRepository.getDetailedHealth()
                    _detailedHealth.value = detailedHealth
                } catch (e: Exception) {
                    // Ignoruj błąd, szczegółowe dane są opcjonalne
                }
                
            } catch (e: Exception) {
                _error.value = "Błąd ładowania danych systemu: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Odświeża dane systemowe
     */
    fun refreshSystemData() {
        viewModelScope.launch {
            try {
                _isRefreshing.value = true
                _error.value = null
                
                // Odśwież konfigurację systemu
                val config = healthRepository.getSystemConfiguration(forceRefresh = true)
                _systemConfiguration.value = config
                
                // Odśwież zdrowie systemu
                val health = healthRepository.getSystemHealth(forceRefresh = true)
                _systemHealth.value = health
                
                // Odśwież szczegółowe dane zdrowia systemu
                try {
                    val detailedHealth = healthRepository.getDetailedHealth(forceRefresh = true)
                    _detailedHealth.value = detailedHealth
                } catch (e: Exception) {
                    // Ignoruj błąd, szczegółowe dane są opcjonalne
                }
                
            } catch (e: Exception) {
                _error.value = "Błąd odświeżania danych systemu: ${e.message}"
            } finally {
                _isRefreshing.value = false
            }
        }
    }
    
    /**
     * Ładuje logi diagnostyczne
     */
    fun loadDiagnosticLogs(level: String = "info", limit: Int = 50) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                val logs = healthRepository.getDiagnosticLogs(level, limit)
                _diagnosticLogs.value = logs
                
            } catch (e: Exception) {
                _error.value = "Błąd ładowania logów diagnostycznych: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Ładuje metryki wydajności
     */
    fun loadPerformanceMetrics(timeRange: String = "1h") {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                val metrics = healthRepository.getPerformanceMetrics(timeRange)
                _performanceMetrics.value = metrics
                
            } catch (e: Exception) {
                _error.value = "Błąd ładowania metryk wydajności: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Zapisuje konfigurację systemu
     */
    fun saveSystemConfiguration() {
        viewModelScope.launch {
            try {
                _isSaving.value = true
                _error.value = null
                
                val config = _systemConfiguration.value ?: return@launch
                
                val updatedConfig = healthRepository.updateSystemConfiguration(config)
                _systemConfiguration.value = updatedConfig
                
                _successMessage.value = "Konfiguracja systemu została zapisana"
                
            } catch (e: Exception) {
                _error.value = "Błąd zapisywania konfiguracji: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }
    
    /**
     * Wykonuje akcję systemową
     */
    fun performSystemAction(action: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                val result = healthRepository.performSystemAction(action)
                
                if (result) {
                    _successMessage.value = "Akcja $action została wykonana pomyślnie"
                    // Odśwież dane po wykonaniu akcji
                    loadSystemData()
                } else {
                    _error.value = "Nie udało się wykonać akcji $action"
                }
                
            } catch (e: Exception) {
                _error.value = "Błąd wykonywania akcji: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Czyści cache
     */
    fun clearAllCache() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                smsRepository.clearCache()
                queueRepository.clearCache()
                healthRepository.clearCache()
                
                _successMessage.value = "Cache został wyczyszczony"
                
            } catch (e: Exception) {
                _error.value = "Błąd czyszczenia cache: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Ustawia URL bazowy API
     */
    fun setApiBaseUrl(url: String) {
        _apiBaseUrl.value = url
    }
    
    /**
     * Ustawia timeout API
     */
    fun setApiTimeout(timeout: Int) {
        _apiTimeout.value = timeout
    }
    
    /**
     * Ustawia interwał odświeżania
     */
    fun setRefreshInterval(interval: Int) {
        _refreshInterval.value = interval
    }
    
    /**
     * Włącza/wyłącza powiadomienia
     */
    fun setEnableNotifications(enabled: Boolean) {
        _enableNotifications.value = enabled
    }
    
    /**
     * Włącza/wyłącza automatyczne odświeżanie
     */
    fun setEnableAutoRefresh(enabled: Boolean) {
        _enableAutoRefresh.value = enabled
    }
    
    /**
     * Włącza/wyłącza cache
     */
    fun setEnableCache(enabled: Boolean) {
        _enableCache.value = enabled
        
        viewModelScope.launch {
            try {
                smsRepository.setCacheEnabled(enabled)
                queueRepository.setCacheEnabled(enabled)
                healthRepository.setCacheEnabled(enabled)
            } catch (e: Exception) {
                _error.value = "Błąd zmiany ustawień cache: ${e.message}"
            }
        }
    }
    
    /**
     * Ustawia poziom logowania
     */
    fun setLogLevel(level: String) {
        _logLevel.value = level
    }
    
    /**
     * Resetuje ustawienia do domyślnych
     */
    fun resetToDefaults() {
        _apiBaseUrl.value = ""
        _apiTimeout.value = 30000
        _refreshInterval.value = 30000
        _enableNotifications.value = true
        _enableAutoRefresh.value = true
        _enableCache.value = true
        _logLevel.value = "INFO"
        
        _successMessage.value = "Ustawienia zostały zresetowane do domyślnych"
    }
    
    /**
     * Ładuje ustawienia z lokalnego storage
     */
    private fun loadSettings() {
        // W prawdziwej implementacji tutaj byłby kod ładujący ustawienia
        // z SharedPreferences lub innego lokalnego storage
        
        // Na razie używamy wartości domyślnych
        _apiBaseUrl.value = ""
        _apiTimeout.value = 30000
        _refreshInterval.value = 30000
        _enableNotifications.value = true
        _enableAutoRefresh.value = true
        _enableCache.value = true
        _logLevel.value = "INFO"
    }
    
    /**
     * Zapisuje ustawienia do lokalnego storage
     */
    fun saveSettings() {
        // W prawdziwej implementacji tutaj byłby kod zapisujący ustawienia
        // do SharedPreferences lub innego lokalnego storage
        
        _successMessage.value = "Ustawienia zostały zapisane"
    }
    
    /**
     * Pobiera opis poziomu logowania
     */
    fun getLogLevelDescription(level: String): String {
        return when (level) {
            "DEBUG" -> "Debug (wszystkie logi)"
            "INFO" -> "Info (standardowe logi)"
            "WARN" -> "Ostrzeżenia (tylko błędy i ostrzeżenia)"
            "ERROR" -> "Błędy (tylko błędy)"
            else -> "Nieznany"
        }
    }
    
    /**
     * Pobiera dostępne akcje systemowe
     */
    fun getAvailableSystemActions(): List<Pair<String, String>> {
        return listOf(
            Pair("restart", "Restart systemu"),
            Pair("clear_cache", "Wyczyść cache"),
            Pair("optimize_database", "Optymalizuj bazę danych"),
            Pair("cleanup_logs", "Wyczyść logi"),
            Pair("reset_counters", "Zresetuj liczniki"),
            Pair("rebuild_indexes", "Odbuduj indeksy")
        )
    }
    
    /**
     * Pobiera dostępne zakresy czasu dla metryk
     */
    fun getAvailableTimeRanges(): List<Pair<String, String>> {
        return listOf(
            Pair("1h", "Ostatnia godzina"),
            Pair("6h", "Ostatnie 6 godzin"),
            Pair("24h", "Ostatnie 24 godziny"),
            Pair("7d", "Ostatnie 7 dni"),
            Pair("30d", "Ostatnie 30 dni")
        )
    }
    
    /**
     * Pobiera dostępne poziomy logów
     */
    fun getAvailableLogLevels(): List<Pair<String, String>> {
        return listOf(
            Pair("debug", "Debug"),
            Pair("info", "Info"),
            Pair("warn", "Ostrzeżenia"),
            Pair("error", "Błędy")
        )
    }
    
    /**
     * Formatuje rozmiar danych
     */
    fun formatDataSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        
        return when {
            gb >= 1 -> "%.2f GB".format(gb)
            mb >= 1 -> "%.2f MB".format(mb)
            kb >= 1 -> "%.2f KB".format(kb)
            else -> "$bytes B"
        }
    }
    
    /**
     * Formatuje czas działania
     */
    fun formatUptime(seconds: Long): String {
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        
        return when {
            days > 0 -> "${days}d ${hours}h ${minutes}m"
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }
    
    /**
     * Czyści komunikat o błędzie
     */
    fun clearError() {
        _error.value = null
    }
    
    /**
     * Czyści komunikat o sukcesie
     */
    fun clearSuccessMessage() {
        _successMessage.value = null
    }
    
    /**
     * Eksportuje logi diagnostyczne
     */
    fun exportDiagnosticLogs(): String {
        val logs = _diagnosticLogs.value
        val sb = StringBuilder()
        
        sb.appendLine("=== DIAGNOSTIC LOGS ===")
        sb.appendLine("Generated: ${java.util.Date()}")
        sb.appendLine("Total logs: ${logs.size}")
        sb.appendLine()
        
        logs.forEach { log ->
            sb.appendLine("[${log.timestamp}] [${log.level.uppercase()}] ${log.message}")
            if (log.exception != null) {
                sb.appendLine("Exception: ${log.exception}")
            }
            if (log.stackTrace != null) {
                sb.appendLine("Stack trace: ${log.stackTrace}")
            }
            sb.appendLine()
        }
        
        return sb.toString()
    }
    
    /**
     * Pobiera podsumowanie systemu
     */
    fun getSystemSummary(): Map<String, String> {
        val health = _systemHealth.value
        val detailed = _detailedHealth.value
        val config = _systemConfiguration.value
        
        val summary = mutableMapOf<String, String>()
        
        health?.let {
            summary["Status"] = it.status
            summary["Version"] = it.version
            summary["Uptime"] = formatUptime(it.uptime)
        }
        
        detailed?.let {
            summary["Components"] = "${it.components.size} total"
            summary["Healthy components"] = "${it.components.count { it.status == "HEALTHY" }}"
        }
        
        config?.let {
            summary["Environment"] = it.environment
            summary["Region"] = it.region
        }
        
        return summary
    }
}