package com.smsgateway.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smsgateway.app.api.models.SmsStatus
import com.smsgateway.app.api.models.SmsPriority
import com.smsgateway.app.repositories.SmsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel dla ekranu Historii SMS
 * Zarządza stanem danych i logiką biznesową dla historii SMS
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val smsRepository = SmsRepository(application)
    
    // Stany dla danych historii SMS
    private val _smsHistory = MutableStateFlow<List<com.smsgateway.app.api.models.SmsMessageApi>>(emptyList())
    val smsHistory: StateFlow<List<com.smsgateway.app.api.models.SmsMessageApi>> = _smsHistory.asStateFlow()
    
    private val _filteredHistory = MutableStateFlow<List<com.smsgateway.app.api.models.SmsMessageApi>>(emptyList())
    val filteredHistory: StateFlow<List<com.smsgateway.app.api.models.SmsMessageApi>> = _filteredHistory.asStateFlow()
    
    private val _selectedSms = MutableStateFlow<com.smsgateway.app.api.models.SmsMessageApi?>(null)
    val selectedSms: StateFlow<com.smsgateway.app.api.models.SmsMessageApi?> = _selectedSms.asStateFlow()
    
    // Stany dla paginacji
    private val _currentPage = MutableStateFlow(1)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()
    
    private val _totalPages = MutableStateFlow(1)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()
    
    private val _totalItems = MutableStateFlow(0)
    val totalItems: StateFlow<Int> = _totalItems.asStateFlow()
    
    private val _hasNextPage = MutableStateFlow(false)
    val hasNextPage: StateFlow<Boolean> = _hasNextPage.asStateFlow()
    
    private val _hasPreviousPage = MutableStateFlow(false)
    val hasPreviousPage: StateFlow<Boolean> = _hasPreviousPage.asStateFlow()
    
    // Stany dla filtrów
    private val _statusFilter = MutableStateFlow<SmsStatus?>(null)
    val statusFilter: StateFlow<SmsStatus?> = _statusFilter.asStateFlow()
    
    private val _priorityFilter = MutableStateFlow<SmsPriority?>(null)
    val priorityFilter: StateFlow<SmsPriority?> = _priorityFilter.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    // Stany UI
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()
    
    init {
        loadHistory()
    }
    
    /**
     * Ładuje historię SMS
     */
    fun loadHistory(page: Int = 1, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            try {
                if (page == 1) {
                    if (forceRefresh) {
                        _isRefreshing.value = true
                    } else {
                        _isLoading.value = true
                    }
                } else {
                    _isLoadingMore.value = true
                }
                
                _error.value = null
                
                val response = smsRepository.getSmsHistory(
                    page = page,
                    limit = 20,
                    status = _statusFilter.value,
                    priority = _priorityFilter.value,
                    forceRefresh = forceRefresh
                )
                
                if (page == 1) {
                    _smsHistory.value = response.data
                    _currentPage.value = response.pagination.page
                    _totalPages.value = response.pagination.totalPages
                    _totalItems.value = response.pagination.total
                    _hasNextPage.value = response.pagination.hasNext
                    _hasPreviousPage.value = response.pagination.hasPrevious
                } else {
                    // Dodaj nowe dane do istniejącej listy
                    _smsHistory.value = _smsHistory.value + response.data
                }
                
                // Zastosuj filtry
                applyFilters()
                
            } catch (e: Exception) {
                _error.value = "Błąd ładowania historii: ${e.message}"
            } finally {
                _isLoading.value = false
                _isRefreshing.value = false
                _isLoadingMore.value = false
            }
        }
    }
    
    /**
     * Odświeża historię SMS
     */
    fun refreshHistory() {
        _currentPage.value = 1
        loadHistory(forceRefresh = true)
    }
    
    /**
     * Ładuje kolejną stronę historii
     */
    fun loadNextPage() {
        if (_hasNextPage.value && !_isLoadingMore.value) {
            val nextPage = _currentPage.value + 1
            _currentPage.value = nextPage
            loadHistory(nextPage)
        }
    }
    
    /**
     * Ładuje poprzednią stronę historii
     */
    fun loadPreviousPage() {
        if (_hasPreviousPage.value && !_isLoadingMore.value) {
            val previousPage = _currentPage.value - 1
            _currentPage.value = previousPage
            loadHistory(previousPage)
        }
    }
    
    /**
     * Ustawia filtr statusu
     */
    fun setStatusFilter(status: SmsStatus?) {
        _statusFilter.value = status
        _currentPage.value = 1
        loadHistory()
    }
    
    /**
     * Ustawia filtr priorytetu
     */
    fun setPriorityFilter(priority: SmsPriority?) {
        _priorityFilter.value = priority
        _currentPage.value = 1
        loadHistory()
    }
    
    /**
     * Ustawia zapytanie wyszukiwania
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilters()
    }
    
    /**
     * Czyści wszystkie filtry
     */
    fun clearFilters() {
        _statusFilter.value = null
        _priorityFilter.value = null
        _searchQuery.value = ""
        _currentPage.value = 1
        loadHistory()
    }
    
    /**
     * Zastosowuje filtry do listy historii
     */
    private fun applyFilters() {
        var filtered = _smsHistory.value
        
        // Filtruj po statusie
        _statusFilter.value?.let { status ->
            filtered = filtered.filter { it.status == status }
        }
        
        // Filtruj po priorytecie
        _priorityFilter.value?.let { priority ->
            filtered = filtered.filter { it.priority == priority }
        }
        
        // Filtruj po zapytaniu wyszukiwania
        val query = _searchQuery.value.trim()
        if (query.isNotEmpty()) {
            filtered = filtered.filter { sms ->
                sms.phoneNumber.contains(query, ignoreCase = true) ||
                sms.message.contains(query, ignoreCase = true) ||
                sms.id.contains(query, ignoreCase = true)
            }
        }
        
        _filteredHistory.value = filtered
    }
    
    /**
     * Wybiera wiadomość SMS do wyświetlenia szczegółów
     */
    fun selectSms(sms: com.smsgateway.app.api.models.SmsMessageApi) {
        _selectedSms.value = sms
    }
    
    /**
     * Anuluje wiadomość SMS
     */
    fun cancelSms(messageId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                val result = smsRepository.cancelSms(messageId)
                
                if (result) {
                    _successMessage.value = "Wiadomość została anulowana"
                    // Odśwież historię po anulowaniu
                    loadHistory()
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
     * Ponownie wysyła wiadomość SMS
     */
    fun resendSms(messageId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                val result = smsRepository.resendSms(messageId)
                
                if (result) {
                    _successMessage.value = "Wiadomość została ponownie wysłana"
                    // Odśwież historię po ponownym wysłaniu
                    loadHistory()
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
     * Usuwa wiadomość SMS z historii
     */
    fun deleteSms(messageId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                val result = smsRepository.deleteSms(messageId)
                
                if (result) {
                    _successMessage.value = "Wiadomość została usunięta"
                    // Odśwież historię po usunięciu
                    loadHistory()
                } else {
                    _error.value = "Nie udało się usunąć wiadomości"
                }
            } catch (e: Exception) {
                _error.value = "Błąd usuwania wiadomości: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Pobiera szczegóły wiadomości SMS
     */
    fun getSmsDetails(messageId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                val sms = smsRepository.getSmsDetails(messageId)
                _selectedSms.value = sms
                
            } catch (e: Exception) {
                _error.value = "Błąd pobierania szczegółów wiadomości: ${e.message}"
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
     * Pobiera liczbę wiadomości dla każdego statusu
     */
    fun getStatusCounts(): Map<SmsStatus, Int> {
        val counts = mutableMapOf<SmsStatus, Int>()
        
        SmsStatus.values().forEach { status ->
            counts[status] = _smsHistory.value.count { it.status == status }
        }
        
        return counts
    }
    
    /**
     * Pobiera liczbę wiadomości dla każdego priorytetu
     */
    fun getPriorityCounts(): Map<SmsPriority, Int> {
        val counts = mutableMapOf<SmsPriority, Int>()
        
        SmsPriority.values().forEach { priority ->
            counts[priority] = _smsHistory.value.count { it.priority == priority }
        }
        
        return counts
    }
    
    /**
     * Pobiera procent wiadomości dla każdego statusu
     */
    fun getStatusPercentages(): Map<SmsStatus, Float> {
        val total = _smsHistory.value.size
        if (total == 0) return emptyMap()
        
        val percentages = mutableMapOf<SmsStatus, Float>()
        
        SmsStatus.values().forEach { status ->
            val count = _smsHistory.value.count { it.status == status }
            percentages[status] = (count.toFloat() / total.toFloat()) * 100
        }
        
        return percentages
    }
    
    /**
     * Pobiera kolor dla statusu
     */
    fun getStatusColor(status: SmsStatus): String {
        return when (status) {
            SmsStatus.SENT -> "#4CAF50" // Zielony
            SmsStatus.DELIVERED -> "#2196F3" // Niebieski
            SmsStatus.FAILED -> "#F44336" // Czerwony
            SmsStatus.PENDING -> "#FF9800" // Pomarańczowy
            SmsStatus.CANCELLED -> "#9E9E9E" // Szary
            SmsStatus.QUEUED -> "#9C27B0" // Fioletowy
        }
    }
    
    /**
     * Pobiera kolor dla priorytetu
     */
    fun getPriorityColor(priority: SmsPriority): String {
        return when (priority) {
            SmsPriority.HIGH -> "#F44336" // Czerwony
            SmsPriority.MEDIUM -> "#FF9800" // Pomarańczowy
            SmsPriority.LOW -> "#4CAF50" // Zielony
        }
    }
    
    /**
     * Formatuje datę wiadomości
     */
    fun formatDate(dateString: String): String {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault())
            val date = sdf.parse(dateString)
            val outputFormat = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
            outputFormat.format(date)
        } catch (e: Exception) {
            dateString
        }
    }
    
    /**
     * Sprawdza czy wiadomość może być anulowana
     */
    fun canCancelSms(sms: com.smsgateway.app.api.models.SmsMessageApi): Boolean {
        return sms.status == SmsStatus.PENDING || sms.status == SmsStatus.QUEUED
    }
    
    /**
     * Sprawdza czy wiadomość może być ponownie wysłana
     */
    fun canResendSms(sms: com.smsgateway.app.api.models.SmsMessageApi): Boolean {
        return sms.status == SmsStatus.FAILED || sms.status == SmsStatus.CANCELLED
    }
    
    /**
     * Sprawdza czy wiadomość może być usunięta
     */
    fun canDeleteSms(sms: com.smsgateway.app.api.models.SmsMessageApi): Boolean {
        return sms.status == SmsStatus.FAILED || 
               sms.status == SmsStatus.CANCELLED || 
               sms.status == SmsStatus.SENT || 
               sms.status == SmsStatus.DELIVERED
    }
}