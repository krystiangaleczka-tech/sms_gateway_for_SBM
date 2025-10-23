package com.smsgateway.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smsgateway.app.api.models.SmsStatus
import com.smsgateway.app.repositories.QueueRepository
import com.smsgateway.app.repositories.HealthRepository
import com.smsgateway.app.repositories.SmsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel dla ekranu Dashboard
 * Zarządza stanem danych i logiką biznesową dla dashboardu
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val queueRepository = QueueRepository(application)
    private val healthRepository = HealthRepository(application)
    private val smsRepository = SmsRepository(application)
    
    // Stany dla danych kolejki
    private val _queueStats = MutableStateFlow<com.smsgateway.app.api.models.QueueStatsApi?>(null)
    val queueStats: StateFlow<com.smsgateway.app.api.models.QueueStatsApi?> = _queueStats.asStateFlow()
    
    private val _highPriorityMessages = MutableStateFlow<List<com.smsgateway.app.api.models.SmsMessageApi>>(emptyList())
    val highPriorityMessages: StateFlow<List<com.smsgateway.app.api.models.SmsMessageApi>> = _highPriorityMessages.asStateFlow()
    
    private val _stuckMessages = MutableStateFlow<List<com.smsgateway.app.api.models.SmsMessageApi>>(emptyList())
    val stuckMessages: StateFlow<List<com.smsgateway.app.api.models.SmsMessageApi>> = _stuckMessages.asStateFlow()
    
    // Stany dla danych zdrowia systemu
    private val _systemHealth = MutableStateFlow<com.smsgateway.app.api.models.SystemHealthApi?>(null)
    val systemHealth: StateFlow<com.smsgateway.app.api.models.SystemHealthApi?> = _systemHealth.asStateFlow()
    
    private val _systemMetrics = MutableStateFlow<com.smsgateway.app.api.models.SystemMetricsApi?>(null)
    val systemMetrics: StateFlow<com.smsgateway.app.api.models.SystemMetricsApi?> = _systemMetrics.asStateFlow()
    
    // Stany dla danych SMS
    private val _recentSmsHistory = MutableStateFlow<List<com.smsgateway.app.api.models.SmsMessageApi>>(emptyList())
    val recentSmsHistory: StateFlow<List<com.smsgateway.app.api.models.SmsMessageApi>> = _recentSmsHistory.asStateFlow()
    
    // Stany UI
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()
    
    init {
        loadDashboardData()
    }
    
    /**
     * Ładuje wszystkie dane potrzebne dla dashboardu
     */
    fun loadDashboardData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            try {
                if (forceRefresh) {
                    _isRefreshing.value = true
                } else {
                    _isLoading.value = true
                }
                
                _error.value = null
                
                // Załaduj statystyki kolejki
                val stats = queueRepository.getQueueStats(forceRefresh)
                _queueStats.value = stats
                
                // Załaduj wiadomości o wysokim priorytecie
                val highPriority = queueRepository.getHighPriorityMessages(5, forceRefresh)
                _highPriorityMessages.value = highPriority
                
                // Załaduj zablokowane wiadomości
                val stuck = queueRepository.getStuckMessages(5, forceRefresh)
                _stuckMessages.value = stuck
                
                // Załaduj zdrowie systemu
                val health = healthRepository.getSystemHealth(forceRefresh)
                _systemHealth.value = health
                
                // Pobierz szczegółowe dane zdrowia systemu dla metryk
                try {
                    val detailedHealth = healthRepository.getDetailedHealth(forceRefresh)
                    _systemMetrics.value = detailedHealth.metrics
                } catch (e: Exception) {
                    // Ignoruj błąd, metryki są opcjonalne
                }
                
                // Załaduj ostatnią historię SMS
                val smsHistory = smsRepository.getSmsHistory(1, 5, forceRefresh)
                _recentSmsHistory.value = smsHistory.data
                
            } catch (e: Exception) {
                _error.value = "Błąd ładowania danych: ${e.message}"
            } finally {
                _isLoading.value = false
                _isRefreshing.value = false
            }
        }
    }
    
    /**
     * Odświeża dane dashboardu
     */
    fun refreshData() {
        loadDashboardData(forceRefresh = true)
    }
    
    /**
     * Próbuje ponownie wysłać wiadomość
     */
    fun retryMessage(messageId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                val result = queueRepository.retryMessage(messageId)
                
                if (result) {
                    _successMessage.value = "Wiadomość została ponownie dodana do kolejki"
                    // Odśwież dane po ponownym wysłaniu
                    loadDashboardData()
                } else {
                    _error.value = "Nie udało się ponownie wysłać wiadomości"
                }
            } catch (e: Exception) {
                _error.value = "Błąd ponownego wysyłania wiadomości: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Wznawia przetwarzanie kolejki
     */
    fun resumeQueue() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                val stats = queueRepository.resumeQueue()
                _queueStats.value = stats
                
                _successMessage.value = "Przetwarzanie kolejki zostało wznowione"
                
                // Odśwież dane po wznowieniu kolejki
                loadDashboardData()
            } catch (e: Exception) {
                _error.value = "Błąd wznawiania kolejki: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Wstrzymuje przetwarzanie kolejki
     */
    fun pauseQueue() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                val stats = queueRepository.pauseQueue()
                _queueStats.value = stats
                
                _successMessage.value = "Przetwarzanie kolejki zostało wstrzymane"
                
                // Odśwież dane po wstrzymaniu kolejki
                loadDashboardData()
            } catch (e: Exception) {
                _error.value = "Błąd wstrzymywania kolejki: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Czyści kolejkę z wiadomości o określonym statusie
     */
    fun clearQueueByStatus(status: SmsStatus) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                val deletedCount = queueRepository.clearQueueByStatus(status)
                
                _successMessage.value = "Usunięto $deletedCount wiadomości ze statusem $status"
                
                // Odśwież dane po czyszczeniu kolejki
                loadDashboardData()
            } catch (e: Exception) {
                _error.value = "Błąd czyszczenia kolejki: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Anuluje wiadomość
     */
    fun cancelMessage(messageId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                val result = smsRepository.cancelSms(messageId)
                
                if (result) {
                    _successMessage.value = "Wiadomość została anulowana"
                    // Odśwież danych po anulowaniu
                    loadDashboardData()
                } else {
                    _error.value = "Nie udało się anulować wiadomości"
                }
            } catch (e: Exception) {
                _error.value = "Błąd anulowania wiadomości: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Wykonuje akcję naprawczą na systemie
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
                    loadDashboardData()
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
     * Pobiera podsumowanie statusu kolejki
     */
    fun getQueueStatusSummary(): String {
        val stats = _queueStats.value ?: return "Brak danych"
        
        return when {
            stats.paused -> "Kolejka wstrzymana"
            stats.totalMessages == 0 -> "Kolejka pusta"
            stats.failedMessages > 0 -> "Błędy w kolejce (${stats.failedMessages})"
            stats.pendingMessages > 0 -> "Aktywna (${stats.pendingMessages} oczekujących)"
            else -> "Kolejka OK"
        }
    }
    
    /**
     * Pobiera podsumowanie statusu systemu
     */
    fun getSystemStatusSummary(): String {
        val health = _systemHealth.value ?: return "Brak danych"
        
        return when (health.status) {
            "HEALTHY" -> "System OK"
            "DEGRADED" -> "System ograniczony"
            "UNHEALTHY" -> "System błędy"
            "MAINTENANCE" -> "Tryb maintenance"
            else -> "Status nieznany"
        }
    }
    
    /**
     * Pobiera kolor statusu systemu
     */
    fun getSystemStatusColor(): String {
        val health = _systemHealth.value ?: return "#9E9E9E" // Szary dla braku danych
        
        return when (health.status) {
            "HEALTHY" -> "#4CAF50" // Zielony
            "DEGRADED" -> "#FF9800" // Pomarańczowy
            "UNHEALTHY" -> "#F44336" // Czerwony
            "MAINTENANCE" -> "#2196F3" // Niebieski
            else -> "#9E9E9E" // Szary
        }
    }
    
    /**
     * Pobiera kolor statusu kolejki
     */
    fun getQueueStatusColor(): String {
        val stats = _queueStats.value ?: return "#9E9E9E" // Szary dla braku danych
        
        return when {
            stats.paused -> "#9E9E9E" // Szary
            stats.failedMessages > 0 -> "#F44336" // Czerwony
            stats.pendingMessages > 0 -> "#FF9800" // Pomarańczowy
            else -> "#4CAF50" // Zielony
        }
    }
    
    /**
     * Pobiera procent wykorzystania pamięci
     */
    fun getMemoryUsagePercentage(): Float {
        val metrics = _systemMetrics.value ?: return 0f
        
        return if (metrics.totalMemory > 0) {
            (metrics.usedMemory.toFloat() / metrics.totalMemory.toFloat()) * 100
        } else {
            0f
        }
    }
    
    /**
     * Pobiera procent wykorzystania CPU
     */
    fun getCpuUsagePercentage(): Float {
        val metrics = _systemMetrics.value ?: return 0f
        
        return metrics.cpuUsage
    }
    
    /**
     * Pobiera procent wykorzystania dysku
     */
    fun getDiskUsagePercentage(): Float {
        val metrics = _systemMetrics.value ?: return 0f
        
        return if (metrics.totalDiskSpace > 0) {
            (metrics.usedDiskSpace.toFloat() / metrics.totalDiskSpace.toFloat()) * 100
        } else {
            0f
        }
    }
}