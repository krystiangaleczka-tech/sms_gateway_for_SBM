package com.smsgateway.app.repositories

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smsgateway.app.api.SmsApiService
import com.smsgateway.app.api.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.*

/**
 * Repozytorium do obsługi danych SMS z API
 * Implementuje wzorzec Repository Pattern z Cache-First Strategy
 */
class SmsRepository(private val context: Context) {
    private val smsApiService = SmsApiService()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
    
    // Cache dla danych SMS
    private val _messagesCache = MutableStateFlow<List<SmsMessageApi>>(emptyList())
    val messagesCache: StateFlow<List<SmsMessageApi>> = _messagesCache.asStateFlow()
    
    private val _queueStatsCache = MutableStateFlow<QueueStatsApi?>(null)
    val queueStatsCache: StateFlow<QueueStatsApi?> = _queueStatsCache.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // DataStore dla cache
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sms_cache")
    
    companion object {
        private val MESSAGES_CACHE_KEY = stringPreferencesKey("messages_cache")
        private val QUEUE_STATS_CACHE_KEY = stringPreferencesKey("queue_stats_cache")
        private val CACHE_ENABLED_KEY = booleanPreferencesKey("cache_enabled")
        private val LAST_UPDATE_KEY = stringPreferencesKey("last_update")
    }
    
    /**
     * Wysyła SMS przez API
     * @param request Obiekt żądania wysłania SMS
     * @return SendSmsResponse z odpowiedzią serwera
     */
    suspend fun sendSms(request: SendSmsRequest): SendSmsResponse {
        return try {
            _isLoading.value = true
            _error.value = null
            
            val response = smsApiService.sendSms(request)
            
            // Odśwież cache po wysłaniu SMS
            refreshMessages()
            refreshQueueStats()
            
            response
        } catch (e: ApiException) {
            _error.value = e.message
            throw e
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Pobiera status wiadomości SMS
     * @param id ID wiadomości
     * @return SmsStatusResponse ze statusem wiadomości
     */
    suspend fun getSmsStatus(id: String): SmsStatusResponse {
        return try {
            _isLoading.value = true
            _error.value = null
            
            smsApiService.getSmsStatus(id)
        } catch (e: ApiException) {
            _error.value = e.message
            throw e
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Pobiera historię wiadomości SMS z paginacją
     * Implementuje Cache-First Strategy - najpierw sprawdza cache, potem API
     * @param page Numer strony (domyślnie 1)
     * @param limit Liczba elementów na stronie (domyślnie 20)
     * @param status Opcjonalny filtr statusu
     * @param forceRefresh Wymuś odświeżenie z API (domyślnie false)
     * @return PaginatedResponse z listą wiadomości
     */
    suspend fun getSmsHistory(
        page: Int = 1,
        limit: Int = 20,
        status: SmsStatus? = null,
        forceRefresh: Boolean = false
    ): PaginatedResponse<SmsMessageApi> {
        return try {
            _isLoading.value = true
            _error.value = null
            
            // Sprawdź cache jeśli nie wymuszono odświeżenia
            if (!forceRefresh && page == 1 && status == null) {
                val cachedMessages = getCachedMessages()
                if (cachedMessages.isNotEmpty()) {
                    return PaginatedResponse(
                        data = cachedMessages,
                        pagination = PaginationInfo(
                            page = 1,
                            limit = cachedMessages.size,
                            total = cachedMessages.size,
                            totalPages = 1,
                            hasNext = false,
                            hasPrevious = false
                        )
                    )
                }
            }
            
            // Pobierz dane z API
            val response = smsApiService.getSmsHistory(page, limit, status)
            
            // Zaktualizuj cache tylko dla pierwszej strony bez filtrów
            if (page == 1 && status == null) {
                _messagesCache.value = response.data
                saveMessagesToCache(response.data)
            }
            
            response
        } catch (e: ApiException) {
            _error.value = e.message
            
            // W przypadku błędu spróbuj zwrócić dane z cache
            if (page == 1 && status == null) {
                val cachedMessages = getCachedMessages()
                if (cachedMessages.isNotEmpty()) {
                    return PaginatedResponse(
                        data = cachedMessages,
                        pagination = PaginationInfo(
                            page = 1,
                            limit = cachedMessages.size,
                            total = cachedMessages.size,
                            totalPages = 1,
                            hasNext = false,
                            hasPrevious = false
                        )
                    )
                }
            }
            
            throw e
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Anuluje wiadomość SMS
     * @param id ID wiadomości do anulowania
     * @return Boolean czy operacja się powiodła
     */
    suspend fun cancelSms(id: String): Boolean {
        return try {
            _isLoading.value = true
            _error.value = null
            
            val result = smsApiService.cancelSms(id)
            
            // Odśwież cache po anulowaniu SMS
            refreshMessages()
            
            result
        } catch (e: ApiException) {
            _error.value = e.message
            false
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Pobiera szczegóły pojedynczej wiadomości SMS
     * @param id ID wiadomości
     * @return SmsMessageApi ze szczegółami wiadomości
     */
    suspend fun getSmsDetails(id: String): SmsMessageApi {
        return try {
            _isLoading.value = true
            _error.value = null
            
            smsApiService.getSmsDetails(id)
        } catch (e: ApiException) {
            _error.value = e.message
            throw e
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Pobiera statystyki kolejki SMS
     * Implementuje Cache-First Strategy
     * @param forceRefresh Wymuś odświeżenie z API (domyślnie false)
     * @return QueueStatsApi ze statystykami kolejki
     */
    suspend fun getQueueStats(forceRefresh: Boolean = false): QueueStatsApi {
        return try {
            _isLoading.value = true
            _error.value = null
            
            // Sprawdź cache jeśli nie wymuszono odświeżenia
            if (!forceRefresh) {
                val cachedStats = getCachedQueueStats()
                if (cachedStats != null) {
                    return cachedStats
                }
            }
            
            // Pobierz dane z API
            val stats = smsApiService.getQueueStats()
            
            // Zaktualizuj cache
            _queueStatsCache.value = stats
            saveQueueStatsToCache(stats)
            
            stats
        } catch (e: ApiException) {
            _error.value = e.message
            
            // W przypadku błędu spróbuj zwrócić dane z cache
            val cachedStats = getCachedQueueStats()
            if (cachedStats != null) {
                return cachedStats
            }
            
            throw e
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Odświeża wszystkie dane z API
     */
    suspend fun refreshAll() {
        try {
            _isLoading.value = true
            _error.value = null
            
            refreshMessages()
            refreshQueueStats()
        } catch (e: ApiException) {
            _error.value = e.message
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Odświeża listę wiadomości SMS
     */
    private suspend fun refreshMessages() {
        try {
            val response = smsApiService.getSmsHistory()
            _messagesCache.value = response.data
            saveMessagesToCache(response.data)
        } catch (e: ApiException) {
            // Nie rzucaj wyjątku, aby nie przerywać innych operacji odświeżania
        }
    }
    
    /**
     * Odświeża statystyki kolejki
     */
    private suspend fun refreshQueueStats() {
        try {
            val stats = smsApiService.getQueueStats()
            _queueStatsCache.value = stats
            saveQueueStatsToCache(stats)
        } catch (e: ApiException) {
            // Nie rzucaj wyjątku, aby nie przerywać innych operacji odświeżania
        }
    }
    
    /**
     * Czyści cache
     */
    suspend fun clearCache() {
        runBlocking {
            context.dataStore.edit { preferences ->
                preferences.remove(MESSAGES_CACHE_KEY)
                preferences.remove(QUEUE_STATS_CACHE_KEY)
                preferences.remove(LAST_UPDATE_KEY)
            }
        }
        
        _messagesCache.value = emptyList()
        _queueStatsCache.value = null
    }
    
    /**
     * Zapisuje listę wiadomości do cache
     */
    private suspend fun saveMessagesToCache(messages: List<SmsMessageApi>) {
        runBlocking {
            context.dataStore.edit { preferences ->
                // Prosta serializacja listy do JSON
                val json = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                }
                val serialized = json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(SmsMessageApi.serializer()),
                    messages
                )
                preferences[MESSAGES_CACHE_KEY] = serialized
                preferences[LAST_UPDATE_KEY] = dateFormat.format(Date())
            }
        }
    }
    
    /**
     * Pobiera listę wiadomości z cache
     */
    private suspend fun getCachedMessages(): List<SmsMessageApi> {
        return runBlocking {
            val preferences = context.dataStore.first()
            val serialized = preferences[MESSAGES_CACHE_KEY] ?: return@runBlocking emptyList()
            
            try {
                val json = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                }
                json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(SmsMessageApi.serializer()),
                    serialized
                )
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    /**
     * Zapisuje statystyki kolejki do cache
     */
    private suspend fun saveQueueStatsToCache(stats: QueueStatsApi) {
        runBlocking {
            context.dataStore.edit { preferences ->
                val json = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                }
                val serialized = json.encodeToString(QueueStatsApi.serializer(), stats)
                preferences[QUEUE_STATS_CACHE_KEY] = serialized
                preferences[LAST_UPDATE_KEY] = dateFormat.format(Date())
            }
        }
    }
    
    /**
     * Pobiera statystyki kolejki z cache
     */
    private suspend fun getCachedQueueStats(): QueueStatsApi? {
        return runBlocking {
            val preferences = context.dataStore.first()
            val serialized = preferences[QUEUE_STATS_CACHE_KEY] ?: return@runBlocking null
            
            try {
                val json = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                }
                json.decodeFromString(QueueStatsApi.serializer(), serialized)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Sprawdza czy cache jest włączony
     */
    suspend fun isCacheEnabled(): Boolean {
        return runBlocking {
            val preferences = context.dataStore.first()
            preferences[CACHE_ENABLED_KEY] ?: true
        }
    }
    
    /**
     * Włącza/wyłącza cache
     */
    suspend fun setCacheEnabled(enabled: Boolean) {
        runBlocking {
            context.dataStore.edit { preferences ->
                preferences[CACHE_ENABLED_KEY] = enabled
            }
        }
        
        if (!enabled) {
            clearCache()
        }
    }
    
    /**
     * Pobiera datę ostatniej aktualizacji cache
     */
    suspend fun getLastUpdateDate(): Date? {
        return runBlocking {
            val preferences = context.dataStore.first()
            val dateString = preferences[LAST_UPDATE_KEY] ?: return@runBlocking null
            
            try {
                dateFormat.parse(dateString)
            } catch (e: Exception) {
                null
            }
        }
    }
}