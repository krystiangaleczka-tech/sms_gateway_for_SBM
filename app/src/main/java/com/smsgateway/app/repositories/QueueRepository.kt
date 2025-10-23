package com.smsgateway.app.repositories

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smsgateway.app.api.QueueApiService
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
 * Repozytorium do obsługi danych kolejki SMS z API
 * Implementuje wzorzec Repository Pattern z Cache-First Strategy
 */
class QueueRepository(private val context: Context) {
    private val queueApiService = QueueApiService()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
    
    // Cache dla danych kolejki
    private val _queueMessagesCache = MutableStateFlow<List<SmsMessageApi>>(emptyList())
    val queueMessagesCache: StateFlow<List<SmsMessageApi>> = _queueMessagesCache.asStateFlow()
    
    private val _highPriorityMessagesCache = MutableStateFlow<List<SmsMessageApi>>(emptyList())
    val highPriorityMessagesCache: StateFlow<List<SmsMessageApi>> = _highPriorityMessagesCache.asStateFlow()
    
    private val _stuckMessagesCache = MutableStateFlow<List<SmsMessageApi>>(emptyList())
    val stuckMessagesCache: StateFlow<List<SmsMessageApi>> = _stuckMessagesCache.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // DataStore dla cache
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "queue_cache")
    
    companion object {
        private val QUEUE_MESSAGES_CACHE_KEY = stringPreferencesKey("queue_messages_cache")
        private val HIGH_PRIORITY_CACHE_KEY = stringPreferencesKey("high_priority_cache")
        private val STUCK_MESSAGES_CACHE_KEY = stringPreferencesKey("stuck_messages_cache")
        private val CACHE_ENABLED_KEY = booleanPreferencesKey("cache_enabled")
        private val LAST_UPDATE_KEY = stringPreferencesKey("last_update")
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
            
            // Pobierz dane z API (statystyki nie są cache'owane ze względu na ich dynamiczny charakter)
            val stats = queueApiService.getQueueStats()
            
            stats
        } catch (e: ApiException) {
            _error.value = e.message
            throw e
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Pobiera listę wiadomości w kolejce z paginacją
     * Implementuje Cache-First Strategy
     * @param page Numer strony (domyślnie 1)
     * @param limit Liczba elementów na stronie (domyślnie 20)
     * @param status Opcjonalny filtr statusu
     * @param priority Opcjonalny filtr priorytetu
     * @param forceRefresh Wymuś odświeżenie z API (domyślnie false)
     * @return PaginatedResponse z listą wiadomości w kolejce
     */
    suspend fun getQueueMessages(
        page: Int = 1,
        limit: Int = 20,
        status: SmsStatus? = null,
        priority: SmsPriority? = null,
        forceRefresh: Boolean = false
    ): PaginatedResponse<SmsMessageApi> {
        return try {
            _isLoading.value = true
            _error.value = null
            
            // Sprawdź cache jeśli nie wymuszono odświeżenia
            if (!forceRefresh && page == 1 && status == null && priority == null) {
                val cachedMessages = getCachedQueueMessages()
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
            val response = queueApiService.getQueueMessages(page, limit, status, priority)
            
            // Zaktualizuj cache tylko dla pierwszej strony bez filtrów
            if (page == 1 && status == null && priority == null) {
                _queueMessagesCache.value = response.data
                saveQueueMessagesToCache(response.data)
            }
            
            response
        } catch (e: ApiException) {
            _error.value = e.message
            
            // W przypadku błędu spróbuj zwrócić dane z cache
            if (page == 1 && status == null && priority == null) {
                val cachedMessages = getCachedQueueMessages()
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
     * Pobiera wiadomości o wysokim priorytecie w kolejce
     * @param limit Maksymalna liczba wiadomości do pobrania (domyślnie 10)
     * @param forceRefresh Wymuś odświeżenie z API (domyślnie false)
     * @return Lista wiadomości o wysokim priorytecie
     */
    suspend fun getHighPriorityMessages(
        limit: Int = 10,
        forceRefresh: Boolean = false
    ): List<SmsMessageApi> {
        return try {
            _isLoading.value = true
            _error.value = null
            
            // Sprawdź cache jeśli nie wymuszono odświeżenia
            if (!forceRefresh) {
                val cachedMessages = getCachedHighPriorityMessages()
                if (cachedMessages.isNotEmpty()) {
                    return cachedMessages.take(limit)
                }
            }
            
            // Pobierz dane z API
            val messages = queueApiService.getHighPriorityMessages(limit)
            
            // Zaktualizuj cache
            _highPriorityMessagesCache.value = messages
            saveHighPriorityMessagesToCache(messages)
            
            messages
        } catch (e: ApiException) {
            _error.value = e.message
            
            // W przypadku błędu spróbuj zwrócić dane z cache
            val cachedMessages = getCachedHighPriorityMessages()
            if (cachedMessages.isNotEmpty()) {
                return cachedMessages.take(limit)
            }
            
            emptyList()
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Pobiera wiadomości zablokowane w kolejce
     * @param limit Maksymalna liczba wiadomości do pobrania (domyślnie 10)
     * @param forceRefresh Wymuś odświeżenie z API (domyślnie false)
     * @return Lista wiadomości zablokowanych
     */
    suspend fun getStuckMessages(
        limit: Int = 10,
        forceRefresh: Boolean = false
    ): List<SmsMessageApi> {
        return try {
            _isLoading.value = true
            _error.value = null
            
            // Sprawdź cache jeśli nie wymuszono odświeżenia
            if (!forceRefresh) {
                val cachedMessages = getCachedStuckMessages()
                if (cachedMessages.isNotEmpty()) {
                    return cachedMessages.take(limit)
                }
            }
            
            // Pobierz dane z API
            val messages = queueApiService.getStuckMessages(limit)
            
            // Zaktualizuj cache
            _stuckMessagesCache.value = messages
            saveStuckMessagesToCache(messages)
            
            messages
        } catch (e: ApiException) {
            _error.value = e.message
            
            // W przypadku błędu spróbuj zwrócić dane z cache
            val cachedMessages = getCachedStuckMessages()
            if (cachedMessages.isNotEmpty()) {
                return cachedMessages.take(limit)
            }
            
            emptyList()
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Próbuje ponownie wysłać wiadomość z kolejki
     * @param id ID wiadomości do ponownego wysłania
     * @return Boolean czy operacja się powiodła
     */
    suspend fun retryMessage(id: String): Boolean {
        return try {
            _isLoading.value = true
            _error.value = null
            
            val result = queueApiService.retryMessage(id)
            
            // Odśwież cache po ponownym wysłaniu wiadomości
            refreshQueueMessages()
            refreshHighPriorityMessages()
            refreshStuckMessages()
            
            result
        } catch (e: ApiException) {
            _error.value = e.message
            false
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Wznawia przetwarzanie kolejki
     * @return QueueStatsApi ze zaktualizowanymi statystykami
     */
    suspend fun resumeQueue(): QueueStatsApi {
        return try {
            _isLoading.value = true
            _error.value = null
            
            val stats = queueApiService.resumeQueue()
            
            // Odśwież cache po wznowieniu kolejki
            refreshQueueMessages()
            refreshHighPriorityMessages()
            refreshStuckMessages()
            
            stats
        } catch (e: ApiException) {
            _error.value = e.message
            throw e
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Wstrzymuje przetwarzanie kolejki
     * @return QueueStatsApi ze zaktualizowanymi statystykami
     */
    suspend fun pauseQueue(): QueueStatsApi {
        return try {
            _isLoading.value = true
            _error.value = null
            
            val stats = queueApiService.pauseQueue()
            
            // Odśwież cache po wstrzymaniu kolejki
            refreshQueueMessages()
            refreshHighPriorityMessages()
            refreshStuckMessages()
            
            stats
        } catch (e: ApiException) {
            _error.value = e.message
            throw e
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Czyści kolejkę z wiadomości o określonym statusie
     * @param status Status wiadomości do usunięcia
     * @return Liczba usuniętych wiadomości
     */
    suspend fun clearQueueByStatus(status: SmsStatus): Int {
        return try {
            _isLoading.value = true
            _error.value = null
            
            val deletedCount = queueApiService.clearQueueByStatus(status)
            
            // Odśwież cache po czyszczeniu kolejki
            refreshQueueMessages()
            refreshHighPriorityMessages()
            refreshStuckMessages()
            
            deletedCount
        } catch (e: ApiException) {
            _error.value = e.message
            0
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Aktualizuje priorytet wiadomości w kolejce
     * @param id ID wiadomości
     * @param priority Nowy priorytet
     * @return Boolean czy operacja się powiodła
     */
    suspend fun updateMessagePriority(id: String, priority: SmsPriority): Boolean {
        return try {
            _isLoading.value = true
            _error.value = null
            
            val result = queueApiService.updateMessagePriority(id, priority)
            
            // Odśwież cache po aktualizacji priorytetu
            refreshQueueMessages()
            refreshHighPriorityMessages()
            refreshStuckMessages()
            
            result
        } catch (e: ApiException) {
            _error.value = e.message
            false
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
            
            refreshQueueMessages()
            refreshHighPriorityMessages()
            refreshStuckMessages()
        } catch (e: ApiException) {
            _error.value = e.message
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Odświeża listę wiadomości w kolejce
     */
    private suspend fun refreshQueueMessages() {
        try {
            val response = queueApiService.getQueueMessages()
            _queueMessagesCache.value = response.data
            saveQueueMessagesToCache(response.data)
        } catch (e: ApiException) {
            // Nie rzucaj wyjątku, aby nie przerywać innych operacji odświeżania
        }
    }
    
    /**
     * Odświeża wiadomości o wysokim priorytecie
     */
    private suspend fun refreshHighPriorityMessages() {
        try {
            val messages = queueApiService.getHighPriorityMessages()
            _highPriorityMessagesCache.value = messages
            saveHighPriorityMessagesToCache(messages)
        } catch (e: ApiException) {
            // Nie rzucaj wyjątku, aby nie przerywać innych operacji odświeżania
        }
    }
    
    /**
     * Odświeża wiadomości zablokowane
     */
    private suspend fun refreshStuckMessages() {
        try {
            val messages = queueApiService.getStuckMessages()
            _stuckMessagesCache.value = messages
            saveStuckMessagesToCache(messages)
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
                preferences.remove(QUEUE_MESSAGES_CACHE_KEY)
                preferences.remove(HIGH_PRIORITY_CACHE_KEY)
                preferences.remove(STUCK_MESSAGES_CACHE_KEY)
                preferences.remove(LAST_UPDATE_KEY)
            }
        }
        
        _queueMessagesCache.value = emptyList()
        _highPriorityMessagesCache.value = emptyList()
        _stuckMessagesCache.value = emptyList()
    }
    
    /**
     * Zapisuje listę wiadomości w kolejce do cache
     */
    private suspend fun saveQueueMessagesToCache(messages: List<SmsMessageApi>) {
        runBlocking {
            context.dataStore.edit { preferences ->
                val json = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                }
                val serialized = json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(SmsMessageApi.serializer()),
                    messages
                )
                preferences[QUEUE_MESSAGES_CACHE_KEY] = serialized
                preferences[LAST_UPDATE_KEY] = dateFormat.format(Date())
            }
        }
    }
    
    /**
     * Pobiera listę wiadomości w kolejce z cache
     */
    private suspend fun getCachedQueueMessages(): List<SmsMessageApi> {
        return runBlocking {
            val preferences = context.dataStore.first()
            val serialized = preferences[QUEUE_MESSAGES_CACHE_KEY] ?: return@runBlocking emptyList()
            
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
     * Zapisuje wiadomości o wysokim priorytecie do cache
     */
    private suspend fun saveHighPriorityMessagesToCache(messages: List<SmsMessageApi>) {
        runBlocking {
            context.dataStore.edit { preferences ->
                val json = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                }
                val serialized = json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(SmsMessageApi.serializer()),
                    messages
                )
                preferences[HIGH_PRIORITY_CACHE_KEY] = serialized
                preferences[LAST_UPDATE_KEY] = dateFormat.format(Date())
            }
        }
    }
    
    /**
     * Pobiera wiadomości o wysokim priorytecie z cache
     */
    private suspend fun getCachedHighPriorityMessages(): List<SmsMessageApi> {
        return runBlocking {
            val preferences = context.dataStore.first()
            val serialized = preferences[HIGH_PRIORITY_CACHE_KEY] ?: return@runBlocking emptyList()
            
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
     * Zapisuje wiadomości zablokowane do cache
     */
    private suspend fun saveStuckMessagesToCache(messages: List<SmsMessageApi>) {
        runBlocking {
            context.dataStore.edit { preferences ->
                val json = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                }
                val serialized = json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(SmsMessageApi.serializer()),
                    messages
                )
                preferences[STUCK_MESSAGES_CACHE_KEY] = serialized
                preferences[LAST_UPDATE_KEY] = dateFormat.format(Date())
            }
        }
    }
    
    /**
     * Pobiera wiadomości zablokowane z cache
     */
    private suspend fun getCachedStuckMessages(): List<SmsMessageApi> {
        return runBlocking {
            val preferences = context.dataStore.first()
            val serialized = preferences[STUCK_MESSAGES_CACHE_KEY] ?: return@runBlocking emptyList()
            
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