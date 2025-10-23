package com.smsgateway.app.repositories

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smsgateway.app.api.HealthApiService
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
 * Repozytorium do obsługi danych zdrowia systemu z API
 * Implementuje wzorzec Repository Pattern z Cache-First Strategy
 */
class HealthRepository(private val context: Context) {
    private val healthApiService = HealthApiService()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
    
    // Cache dla danych zdrowia systemu
    private val _systemHealthCache = MutableStateFlow<SystemHealthApi?>(null)
    val systemHealthCache: StateFlow<SystemHealthApi?> = _systemHealthCache.asStateFlow()
    
    private val _detailedHealthCache = MutableStateFlow<DetailedSystemHealthApi?>(null)
    val detailedHealthCache: StateFlow<DetailedSystemHealthApi?> = _detailedHealthCache.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // DataStore dla cache
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "health_cache")
    
    companion object {
        private val SYSTEM_HEALTH_CACHE_KEY = stringPreferencesKey("system_health_cache")
        private val DETAILED_HEALTH_CACHE_KEY = stringPreferencesKey("detailed_health_cache")
        private val CACHE_ENABLED_KEY = booleanPreferencesKey("cache_enabled")
        private val LAST_UPDATE_KEY = stringPreferencesKey("last_update")
    }
    
    /**
     * Pobiera podstawowe informacje o zdrowiu systemu
     * Implementuje Cache-First Strategy
     * @param forceRefresh Wymuś odświeżenie z API (domyślnie false)
     * @return SystemHealthApi z podstawowymi informacjami o zdrowiu systemu
     */
    suspend fun getSystemHealth(forceRefresh: Boolean = false): SystemHealthApi {
        return try {
            _isLoading.value = true
            _error.value = null
            
            // Sprawdź cache jeśli nie wymuszono odświeżenia
            if (!forceRefresh) {
                val cachedHealth = getCachedSystemHealth()
                if (cachedHealth != null) {
                    _systemHealthCache.value = cachedHealth
                    return cachedHealth
                }
            }
            
            // Pobierz dane z API
            val health = healthApiService.getSystemHealth()
            
            // Zaktualizuj cache
            _systemHealthCache.value = health
            saveSystemHealthToCache(health)
            
            health
        } catch (e: ApiException) {
            _error.value = e.message
            
            // W przypadku błędu spróbuj zwrócić dane z cache
            val cachedHealth = getCachedSystemHealth()
            if (cachedHealth != null) {
                _systemHealthCache.value = cachedHealth
                return cachedHealth
            }
            
            throw e
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Pobiera szczegółowe informacje o zdrowiu systemu
     * Implementuje Cache-First Strategy
     * @param forceRefresh Wymuś odświeżenie z API (domyślnie false)
     * @return DetailedSystemHealthApi ze szczegółowymi informacjami o zdrowiu systemu
     */
    suspend fun getDetailedHealth(forceRefresh: Boolean = false): DetailedSystemHealthApi {
        return try {
            _isLoading.value = true
            _error.value = null
            
            // Sprawdź cache jeśli nie wymuszono odświeżenia
            if (!forceRefresh) {
                val cachedHealth = getCachedDetailedHealth()
                if (cachedHealth != null) {
                    _detailedHealthCache.value = cachedHealth
                    return cachedHealth
                }
            }
            
            // Pobierz dane z API
            val health = healthApiService.getDetailedHealth()
            
            // Zaktualizuj cache
            _detailedHealthCache.value = health
            saveDetailedHealthToCache(health)
            
            health
        } catch (e: ApiException) {
            _error.value = e.message
            
            // W przypadku błędu spróbuj zwrócić dane z cache
            val cachedHealth = getCachedDetailedHealth()
            if (cachedHealth != null) {
                _detailedHealthCache.value = cachedHealth
                return cachedHealth
            }
            
            throw e
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Pobiera status komponentu systemowego
     * @param component Nazwa komponentu
     * @param forceRefresh Wymuś odświeżenie z API (domyślnie false)
     * @return ComponentStatusApi ze statusem komponentu
     */
    suspend fun getComponentStatus(component: String, forceRefresh: Boolean = false): ComponentStatusApi {
        return try {
            _isLoading.value = true
            _error.value = null
            
            // Komponenty nie są cache'owane ze względu na ich dynamiczny charakter
            val status = healthApiService.getComponentStatus(component)
            
            // Odśwież cache szczegółowego zdrowia systemu po pobraniu statusu komponentu
            if (!forceRefresh) {
                refreshDetailedHealth()
            }
            
            status
        } catch (e: ApiException) {
            _error.value = e.message
            throw e
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Pobiera metryki wydajności systemu
     * @param timeRange Zakres czasowy (1h, 24h, 7d)
     * @param forceRefresh Wymuś odświeżenie z API (domyślnie false)
     * @return PerformanceMetricsApi z metrykami wydajności
     */
    suspend fun getPerformanceMetrics(
        timeRange: String = "1h",
        forceRefresh: Boolean = false
    ): PerformanceMetricsApi {
        return try {
            _isLoading.value = true
            _error.value = null
            
            // Metryki wydajności nie są cache'owane ze względu na ich dynamiczny charakter
            val metrics = healthApiService.getPerformanceMetrics(timeRange)
            
            metrics
        } catch (e: ApiException) {
            _error.value = e.message
            throw e
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Pobiera logi diagnostyczne systemu
     * @param level Poziom logów (debug, info, warn, error)
     * @param limit Maksymalna liczba logów do pobrania (domyślnie 50)
     * @param forceRefresh Wymuś odświeżenie z API (domyślnie false)
     * @return Lista logów diagnostycznych
     */
    suspend fun getDiagnosticLogs(
        level: String = "info",
        limit: Int = 50,
        forceRefresh: Boolean = false
    ): List<DiagnosticLogApi> {
        return try {
            _isLoading.value = true
            _error.value = null
            
            // Logi diagnostyczne nie są cache'owane ze względu na ich dynamiczny charakter
            val logs = healthApiService.getDiagnosticLogs(level, limit)
            
            logs
        } catch (e: ApiException) {
            _error.value = e.message
            emptyList()
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Wykonuje akcję naprawczą na systemie
     * @param action Akcja do wykonania (restart, clear_cache, optimize_database, etc.)
     * @return Boolean czy operacja się powiodła
     */
    suspend fun performSystemAction(action: String): Boolean {
        return try {
            _isLoading.value = true
            _error.value = null
            
            val result = healthApiService.performSystemAction(action)
            
            // Odśwież cache po wykonaniu akcji systemowej
            refreshSystemHealth()
            refreshDetailedHealth()
            
            result
        } catch (e: ApiException) {
            _error.value = e.message
            false
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Pobiera konfigurację systemu
     * @param forceRefresh Wymuś odświeżenie z API (domyślnie false)
     * @return SystemConfigurationApi z konfiguracją systemu
     */
    suspend fun getSystemConfiguration(forceRefresh: Boolean = false): SystemConfigurationApi {
        return try {
            _isLoading.value = true
            _error.value = null
            
            // Konfiguracja systemu jest cache'owana ze względu na jej względnie statyczny charakter
            if (!forceRefresh) {
                // Pobierz dane z API (konfiguracja nie jest cache'owana lokalnie ze względu na bezpieczeństwo)
                val config = healthApiService.getSystemConfiguration()
                return config
            }
            
            // Wymuś odświeżenie
            val config = healthApiService.getSystemConfiguration()
            
            config
        } catch (e: ApiException) {
            _error.value = e.message
            throw e
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Aktualizuje konfigurację systemu
     * @param config Nowa konfiguracja systemu
     * @return SystemConfigurationApi z zaktualizowaną konfiguracją
     */
    suspend fun updateSystemConfiguration(config: SystemConfigurationApi): SystemConfigurationApi {
        return try {
            _isLoading.value = true
            _error.value = null
            
            val updatedConfig = healthApiService.updateSystemConfiguration(config)
            
            // Odśwież cache po aktualizacji konfiguracji
            refreshSystemHealth()
            refreshDetailedHealth()
            
            updatedConfig
        } catch (e: ApiException) {
            _error.value = e.message
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
            
            refreshSystemHealth()
            refreshDetailedHealth()
        } catch (e: ApiException) {
            _error.value = e.message
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Odświeża podstawowe informacje o zdrowiu systemu
     */
    private suspend fun refreshSystemHealth() {
        try {
            val health = healthApiService.getSystemHealth()
            _systemHealthCache.value = health
            saveSystemHealthToCache(health)
        } catch (e: ApiException) {
            // Nie rzucaj wyjątku, aby nie przerywać innych operacji odświeżania
        }
    }
    
    /**
     * Odświeża szczegółowe informacje o zdrowiu systemu
     */
    private suspend fun refreshDetailedHealth() {
        try {
            val health = healthApiService.getDetailedHealth()
            _detailedHealthCache.value = health
            saveDetailedHealthToCache(health)
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
                preferences.remove(SYSTEM_HEALTH_CACHE_KEY)
                preferences.remove(DETAILED_HEALTH_CACHE_KEY)
                preferences.remove(LAST_UPDATE_KEY)
            }
        }
        
        _systemHealthCache.value = null
        _detailedHealthCache.value = null
    }
    
    /**
     * Zapisuje podstawowe informacje o zdrowiu systemu do cache
     */
    private suspend fun saveSystemHealthToCache(health: SystemHealthApi) {
        runBlocking {
            context.dataStore.edit { preferences ->
                val json = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                }
                val serialized = json.encodeToString(SystemHealthApi.serializer(), health)
                preferences[SYSTEM_HEALTH_CACHE_KEY] = serialized
                preferences[LAST_UPDATE_KEY] = dateFormat.format(Date())
            }
        }
    }
    
    /**
     * Pobiera podstawowe informacje o zdrowiu systemu z cache
     */
    private suspend fun getCachedSystemHealth(): SystemHealthApi? {
        return runBlocking {
            val preferences = context.dataStore.first()
            val serialized = preferences[SYSTEM_HEALTH_CACHE_KEY] ?: return@runBlocking null
            
            try {
                val json = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                }
                json.decodeFromString(SystemHealthApi.serializer(), serialized)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Zapisuje szczegółowe informacje o zdrowiu systemu do cache
     */
    private suspend fun saveDetailedHealthToCache(health: DetailedSystemHealthApi) {
        runBlocking {
            context.dataStore.edit { preferences ->
                val json = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                }
                val serialized = json.encodeToString(DetailedSystemHealthApi.serializer(), health)
                preferences[DETAILED_HEALTH_CACHE_KEY] = serialized
                preferences[LAST_UPDATE_KEY] = dateFormat.format(Date())
            }
        }
    }
    
    /**
     * Pobiera szczegółowe informacje o zdrowiu systemu z cache
     */
    private suspend fun getCachedDetailedHealth(): DetailedSystemHealthApi? {
        return runBlocking {
            val preferences = context.dataStore.first()
            val serialized = preferences[DETAILED_HEALTH_CACHE_KEY] ?: return@runBlocking null
            
            try {
                val json = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                }
                json.decodeFromString(DetailedSystemHealthApi.serializer(), serialized)
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
    
    /**
     * Sprawdza czy system jest w trybie maintenance
     */
    suspend fun isMaintenanceMode(): Boolean {
        return try {
            val health = getSystemHealth()
            health.status == "MAINTENANCE" || health.status == "DEGRADED"
        } catch (e: ApiException) {
            // W przypadku błędu zakładamy, że system nie jest w trybie maintenance
            false
        }
    }
    
    /**
     * Sprawdza czy system działa poprawnie
     */
    suspend fun isSystemHealthy(): Boolean {
        return try {
            val health = getSystemHealth()
            health.status == "HEALTHY"
        } catch (e: ApiException) {
            // W przypadku błędu zakładamy, że system nie działa poprawnie
            false
        }
    }
    
    /**
     * Pobiera status komponentów systemowych
     */
    suspend fun getComponentStatuses(): List<ComponentStatusApi> {
        return try {
            val detailedHealth = getDetailedHealth()
            detailedHealth.components
        } catch (e: ApiException) {
            emptyList()
        }
    }
    
    /**
     * Pobiera metryki systemowe
     */
    suspend fun getSystemMetrics(): SystemMetricsApi? {
        return try {
            val detailedHealth = getDetailedHealth()
            detailedHealth.metrics
        } catch (e: ApiException) {
            null
        }
    }
}