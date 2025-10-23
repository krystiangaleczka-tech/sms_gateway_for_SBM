package com.smsgateway.app.monitoring.repositories

import android.content.Context
import androidx.room.*
import com.smsgateway.app.monitoring.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * Repozytorium metryk z Cache-First Strategy
 * Lokalne przechowywanie metryk w Room Database z synchronizacją z backendem
 */
class MetricsRepository(
    private val context: Context,
    private val apiService: com.smsgateway.app.api.ApiService
) {
    private val database = MetricsDatabase.getInstance(context)
    private val metricsDao = database.metricsDao()
    private val mutex = Mutex()
    
    // Cache dla metryk systemowych
    private val _systemMetricsCache = MutableStateFlow<SystemMetrics?>(null)
    val systemMetricsCache: StateFlow<SystemMetrics?> = _systemMetricsCache.asStateFlow()
    
    // Cache dla stanu zdrowia
    private val _healthStatusCache = MutableStateFlow<HealthStatus?>(null)
    val healthStatusCache: StateFlow<HealthStatus?> = _healthStatusCache.asStateFlow()
    
    // Cache dla alertów
    private val _alertsCache = MutableStateFlow<List<SystemAlert>>(emptyList())
    val alertsCache: StateFlow<List<SystemAlert>> = _alertsCache.asStateFlow()
    
    // Cache dla metryk wydajności
    private val _performanceMetricsCache = MutableStateFlow<List<PerformanceMetrics>>(emptyList())
    val performanceMetricsCache: StateFlow<List<PerformanceMetrics>> = _performanceMetricsCache.asStateFlow()
    
    // Flaga synchronizacji
    private var isSyncing = false
    private var lastSyncTime = 0L
    private val syncIntervalMs = TimeUnit.MINUTES.toMillis(5)
    
    init {
        // Inicjalizacja cache z lokalnej bazy danych
        initializeCache()
    }
    
    /**
     * Inicjalizacja cache z lokalnej bazy danych
     */
    private fun initializeCache() {
        // Wczytaj ostatnie metryki z lokalnej bazy danych do cache
        val lastSystemMetrics = metricsDao.getLastSystemMetricsSync()
        if (lastSystemMetrics != null) {
            _systemMetricsCache.value = lastSystemMetrics.toSystemMetrics()
        }
        
        val lastHealthStatus = metricsDao.getLastHealthStatusSync()
        if (lastHealthStatus != null) {
            _healthStatusCache.value = lastHealthStatus.toHealthStatus()
        }
        
        val recentAlerts = metricsDao.getRecentAlertsSync(20).map { it.toSystemAlert() }
        _alertsCache.value = recentAlerts
        
        val recentPerformanceMetrics = metricsDao.getRecentPerformanceMetricsSync(50).map { it.toPerformanceMetrics() }
        _performanceMetricsCache.value = recentPerformanceMetrics
    }
    
    /**
     * Zapis metryk systemowych z Cache-First Strategy
     */
    suspend fun saveSystemMetrics(metrics: SystemMetrics) {
        mutex.withLock {
            try {
                // 1. Zapis do lokalnej bazy danych
                metricsDao.insertSystemMetrics(metrics.toEntity())
                
                // 2. Aktualizacja cache
                _systemMetricsCache.value = metrics
                
                // 3. Synchronizacja z backendem (asynchronicznie)
                syncWithBackend()
                
            } catch (e: Exception) {
                // Fallback - tylko cache
                _systemMetricsCache.value = metrics
            }
        }
    }
    
    /**
     * Zapis stanu zdrowia z Cache-First Strategy
     */
    suspend fun saveHealthStatus(healthStatus: HealthStatus) {
        mutex.withLock {
            try {
                // 1. Zapis do lokalnej bazy danych
                metricsDao.insertHealthStatus(healthStatus.toEntity())
                
                // 2. Aktualizacja cache
                _healthStatusCache.value = healthStatus
                
                // 3. Synchronizacja z backendem (asynchronicznie)
                syncWithBackend()
                
            } catch (e: Exception) {
                // Fallback - tylko cache
                _healthStatusCache.value = healthStatus
            }
        }
    }
    
    /**
     * Zapis alertu systemowego z Cache-First Strategy
     */
    suspend fun saveAlert(alert: SystemAlert) {
        mutex.withLock {
            try {
                // 1. Zapis do lokalnej bazy danych
                metricsDao.insertAlert(alert.toEntity())
                
                // 2. Aktualizacja cache
                val currentAlerts = _alertsCache.value.toMutableList()
                currentAlerts.add(0, alert) // Dodaj na początku
                _alertsCache.value = currentAlerts
                
                // 3. Synchronizacja z backendem (asynchronicznie)
                syncWithBackend()
                
            } catch (e: Exception) {
                // Fallback - tylko cache
                val currentAlerts = _alertsCache.value.toMutableList()
                currentAlerts.add(0, alert)
                _alertsCache.value = currentAlerts
            }
        }
    }
    
    /**
     * Zapis metryk wydajności z Cache-First Strategy
     */
    suspend fun savePerformanceMetrics(metrics: PerformanceMetrics) {
        mutex.withLock {
            try {
                // 1. Zapis do lokalnej bazy danych
                metricsDao.insertPerformanceMetrics(metrics.toEntity())
                
                // 2. Aktualizacja cache
                val currentMetrics = _performanceMetricsCache.value.toMutableList()
                currentMetrics.add(0, metrics) // Dodaj na początku
                
                // Ogranicz cache do ostatnich 100 metryk
                if (currentMetrics.size > 100) {
                    currentMetrics.removeAt(currentMetrics.size - 1)
                }
                
                _performanceMetricsCache.value = currentMetrics
                
                // 3. Synchronizacja z backendem (asynchronicznie)
                syncWithBackend()
                
            } catch (e: Exception) {
                // Fallback - tylko cache
                val currentMetrics = _performanceMetricsCache.value.toMutableList()
                currentMetrics.add(0, metrics)
                
                if (currentMetrics.size > 100) {
                    currentMetrics.removeAt(currentMetrics.size - 1)
                }
                
                _performanceMetricsCache.value = currentMetrics
            }
        }
    }
    
    /**
     * Pobieranie metryk systemowych z Cache-First Strategy
     */
    suspend fun getSystemMetrics(): SystemMetrics? {
        return try {
            // 1. Spróbuj z cache
            _systemMetricsCache.value?.let { return it }
            
            // 2. Jeśli cache pusty, spróbuj z lokalnej bazy
            val dbMetrics = metricsDao.getLastSystemMetrics()
            if (dbMetrics != null) {
                val metrics = dbMetrics.toSystemMetrics()
                _systemMetricsCache.value = metrics
                return metrics
            }
            
            // 3. Jeśli lokalna baza pusta, spróbuj z backendu
            refreshSystemMetricsFromBackend()
            _systemMetricsCache.value
            
        } catch (e: Exception) {
            // Fallback - zwróć z cache nawet jeśli null
            _systemMetricsCache.value
        }
    }
    
    /**
     * Pobieranie stanu zdrowia z Cache-First Strategy
     */
    suspend fun getHealthStatus(): HealthStatus? {
        return try {
            // 1. Spróbuj z cache
            _healthStatusCache.value?.let { return it }
            
            // 2. Jeśli cache pusty, spróbuj z lokalnej bazy
            val dbHealthStatus = metricsDao.getLastHealthStatus()
            if (dbHealthStatus != null) {
                val healthStatus = dbHealthStatus.toHealthStatus()
                _healthStatusCache.value = healthStatus
                return healthStatus
            }
            
            // 3. Jeśli lokalna baza pusta, spróbuj z backendu
            refreshHealthStatusFromBackend()
            _healthStatusCache.value
            
        } catch (e: Exception) {
            // Fallback - zwróć z cache nawet jeśli null
            _healthStatusCache.value
        }
    }
    
    /**
     * Pobieranie alertów z Cache-First Strategy
     */
    suspend fun getAlerts(
        filter: AlertFilter? = null,
        limit: Int = 50,
        offset: Int = 0
    ): List<SystemAlert> {
        return try {
            // 1. Spróbuj z cache
            val cachedAlerts = _alertsCache.value
            val filteredAlerts = filterAlerts(cachedAlerts, filter)
            
            if (filteredAlerts.isNotEmpty()) {
                return filteredAlerts.drop(offset).take(limit)
            }
            
            // 2. Jeśli cache pusty, spróbuj z lokalnej bazy
            val dbAlerts = metricsDao.getRecentAlerts(limit + offset)
                .map { it.toSystemAlert() }
            
            if (dbAlerts.isNotEmpty()) {
                _alertsCache.value = dbAlerts
                return filterAlerts(dbAlerts, filter).drop(offset).take(limit)
            }
            
            // 3. Jeśli lokalna baza pusta, spróbuj z backendu
            refreshAlertsFromBackend()
            filterAlerts(_alertsCache.value, filter).drop(offset).take(limit)
            
        } catch (e: Exception) {
            // Fallback - zwróć z cache nawet jeśli częściowe
            filterAlerts(_alertsCache.value, filter).drop(offset).take(limit)
        }
    }
    
    /**
     * Pobieranie metryk wydajności z Cache-First Strategy
     */
    suspend fun getPerformanceMetrics(
        limit: Int = 50,
        offset: Int = 0
    ): List<PerformanceMetrics> {
        return try {
            // 1. Spróbuj z cache
            val cachedMetrics = _performanceMetricsCache.value
            
            if (cachedMetrics.isNotEmpty()) {
                return cachedMetrics.drop(offset).take(limit)
            }
            
            // 2. Jeśli cache pusty, spróbuj z lokalnej bazy
            val dbMetrics = metricsDao.getRecentPerformanceMetrics(limit + offset)
                .map { it.toPerformanceMetrics() }
            
            if (dbMetrics.isNotEmpty()) {
                _performanceMetricsCache.value = dbMetrics
                return dbMetrics.drop(offset).take(limit)
            }
            
            // 3. Jeśli lokalna baza pusta, spróbuj z backendu
            refreshPerformanceMetricsFromBackend()
            _performanceMetricsCache.value.drop(offset).take(limit)
            
        } catch (e: Exception) {
            // Fallback - zwróć z cache nawet jeśli częściowe
            _performanceMetricsCache.value.drop(offset).take(limit)
        }
    }
    
    /**
     * Inkrementacja licznika błędów
     */
    suspend fun incrementErrorCount(errorType: ErrorType) {
        try {
            // Zwiększ licznik w lokalnej bazie
            metricsDao.incrementErrorCount(errorType.name)
            
            // Synchronizacja z backendem (asynchronicznie)
            syncWithBackend()
            
        } catch (e: Exception) {
            // Fallback - ignoruj błąd
        }
    }
    
    /**
     * Czyszczenie starych metryk
     */
    suspend fun cleanupOldMetrics(retentionDays: Int) {
        try {
            val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
            
            // Usuń stare metryki z lokalnej bazy
            metricsDao.deleteOldSystemMetrics(cutoffTime)
            metricsDao.deleteOldHealthStatuses(cutoffTime)
            metricsDao.deleteOldAlerts(cutoffTime)
            metricsDao.deleteOldPerformanceMetrics(cutoffTime)
            
            // Aktualizacja cache
            val currentAlerts = _alertsCache.value.filter { 
                it.timestamp > cutoffTime 
            }
            _alertsCache.value = currentAlerts
            
            val currentMetrics = _performanceMetricsCache.value.filter { 
                it.timestamp > cutoffTime 
            }
            _performanceMetricsCache.value = currentMetrics
            
        } catch (e: Exception) {
            // Fallback - tylko cache
            val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
            
            val currentAlerts = _alertsCache.value.filter { 
                it.timestamp > cutoffTime 
            }
            _alertsCache.value = currentAlerts
            
            val currentMetrics = _performanceMetricsCache.value.filter { 
                it.timestamp > cutoffTime 
            }
            _performanceMetricsCache.value = currentMetrics
        }
    }
    
    /**
     * Odświeżanie metryk systemowych z backendu
     */
    private suspend fun refreshSystemMetricsFromBackend(): Boolean {
        return try {
            val response = apiService.getSystemMetrics()
            if (response.isSuccessful) {
                val metrics = response.body()
                if (metrics != null) {
                    // Zapisz do lokalnej bazy
                    metricsDao.insertSystemMetrics(metrics.toEntity())
                    
                    // Aktualizuj cache
                    _systemMetricsCache.value = metrics
                    
                    lastSyncTime = System.currentTimeMillis()
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Odświeżanie stanu zdrowia z backendu
     */
    private suspend fun refreshHealthStatusFromBackend(): Boolean {
        return try {
            val response = apiService.getHealthStatus()
            if (response.isSuccessful) {
                val healthStatus = response.body()
                if (healthStatus != null) {
                    // Zapisz do lokalnej bazy
                    metricsDao.insertHealthStatus(healthStatus.toEntity())
                    
                    // Aktualizuj cache
                    _healthStatusCache.value = healthStatus
                    
                    lastSyncTime = System.currentTimeMillis()
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Odświeżanie alertów z backendu
     */
    private suspend fun refreshAlertsFromBackend(): Boolean {
        return try {
            val response = apiService.getAlerts()
            if (response.isSuccessful) {
                val alerts = response.body() ?: emptyList()
                
                // Zapisz do lokalnej bazy
                metricsDao.insertAllAlerts(alerts.map { it.toEntity() })
                
                // Aktualizuj cache
                _alertsCache.value = alerts
                
                lastSyncTime = System.currentTimeMillis()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Odświeżanie metryk wydajności z backendu
     */
    private suspend fun refreshPerformanceMetricsFromBackend(): Boolean {
        return try {
            val response = apiService.getPerformanceMetrics()
            if (response.isSuccessful) {
                val metrics = response.body() ?: emptyList()
                
                // Zapisz do lokalnej bazy
                metricsDao.insertAllPerformanceMetrics(metrics.map { it.toEntity() })
                
                // Aktualizuj cache
                _performanceMetricsCache.value = metrics
                
                lastSyncTime = System.currentTimeMillis()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Synchronizacja z backendem
     */
    private suspend fun syncWithBackend() {
        val currentTime = System.currentTimeMillis()
        
        // Sprawdź czy trzeba synchronizować
        if (isSyncing || currentTime - lastSyncTime < syncIntervalMs) {
            return
        }
        
        isSyncing = true
        
        try {
            // Synchronizuj wszystkie metryki z backendem
            refreshSystemMetricsFromBackend()
            refreshHealthStatusFromBackend()
            refreshAlertsFromBackend()
            refreshPerformanceMetricsFromBackend()
            
            lastSyncTime = currentTime
        } catch (e: Exception) {
            // Logowanie błędu synchronizacji
        } finally {
            isSyncing = false
        }
    }
    
    /**
     * Filtrowanie alertów
     */
    private fun filterAlerts(alerts: List<SystemAlert>, filter: AlertFilter?): List<SystemAlert> {
        if (filter == null) return alerts
        
        return alerts.filter { alert ->
            // Filtr priorytetów
            if (alert.severity !in filter.severities) return@filter false
            
            // Filtr typów
            if (alert.type !in filter.types) return@filter false
            
            // Filtr potwierdzenia
            if (filter.isAcknowledged != null && alert.isAcknowledged != filter.isAcknowledged) {
                return@filter false
            }
            
            // Filtr daty początkowej
            if (filter.startDate != null && alert.timestamp < filter.startDate) {
                return@filter false
            }
            
            // Filtr daty końcowej
            if (filter.endDate != null && alert.timestamp > filter.endDate) {
                return@filter false
            }
            
            // Filtr wyszukiwania
            if (!filter.searchQuery.isNullOrBlank()) {
                val query = filter.searchQuery.lowercase()
                if (!alert.title.lowercase().contains(query) &&
                    !alert.message.lowercase().contains(query) &&
                    !alert.source.lowercase().contains(query)) {
                    return@filter false
                }
            }
            
            true
        }
    }
}

// Konwersje między modelami a encjami bazy danych

private fun SystemMetrics.toEntity(): SystemMetricsEntity {
    return SystemMetricsEntity(
        id = this.id,
        timestamp = this.timestamp,
        cpuUsage = this.cpuUsage,
        memoryUsage = this.memoryUsage.toString(),
        diskUsage = this.diskUsage.toString(),
        networkMetrics = this.networkMetrics.toString(),
        appMetrics = this.appMetrics.toString(),
        databaseMetrics = this.databaseMetrics.toString()
    )
}

private fun SystemMetricsEntity.toSystemMetrics(): SystemMetrics {
    return SystemMetrics(
        id = this.id,
        timestamp = this.timestamp,
        cpuUsage = this.cpuUsage,
        memoryUsage = MemoryUsage(), // Prosta deserializacja
        diskUsage = DiskUsage(), // Prosta deserializacja
        networkMetrics = NetworkMetrics(), // Prosta deserializacja
        appMetrics = AppMetrics(), // Prosta deserializacja
        databaseMetrics = DatabaseMetrics() // Prosta deserializacja
    )
}

private fun HealthStatus.toEntity(): HealthStatusEntity {
    return HealthStatusEntity(
        id = this.id,
        timestamp = this.timestamp,
        status = this.status.name,
        components = this.components.toString(),
        checks = this.checks.toString(),
        uptime = this.uptime,
        version = this.version,
        environment = this.environment
    )
}

private fun HealthStatusEntity.toHealthStatus(): HealthStatus {
    return HealthStatus(
        id = this.id,
        timestamp = this.timestamp,
        status = HealthState.valueOf(this.status),
        components = emptyMap(), // Prosta deserializacja
        checks = emptyList(), // Prosta deserializacja
        uptime = this.uptime,
        version = this.version,
        environment = this.environment
    )
}

private fun SystemAlert.toEntity(): AlertEntity {
    return AlertEntity(
        id = this.id,
        timestamp = this.timestamp,
        severity = this.severity.name,
        type = this.type.name,
        title = this.title,
        message = this.message,
        source = this.source,
        context = this.context.toString(),
        isAcknowledged = this.isAcknowledged,
        acknowledgedBy = this.acknowledgedBy,
        acknowledgedAt = this.acknowledgedAt,
        resolvedAt = this.resolvedAt
    )
}

private fun AlertEntity.toSystemAlert(): SystemAlert {
    return SystemAlert(
        id = this.id,
        timestamp = this.timestamp,
        severity = AlertSeverity.valueOf(this.severity),
        type = AlertType.valueOf(this.type),
        title = this.title,
        message = this.message,
        source = this.source,
        context = emptyMap(), // Prosta deserializacja
        isAcknowledged = this.isAcknowledged,
        acknowledgedBy = this.acknowledgedBy,
        acknowledgedAt = this.acknowledgedAt,
        resolvedAt = this.resolvedAt
    )
}

private fun PerformanceMetrics.toEntity(): PerformanceMetricsEntity {
    return PerformanceMetricsEntity(
        id = this.id,
        timestamp = this.timestamp,
        responseTime = this.responseTime,
        throughput = this.throughput,
        errorRate = this.errorRate,
        availability = this.availability,
        resourceUtilization = this.resourceUtilization,
        userSatisfaction = this.userSatisfaction
    )
}

private fun PerformanceMetricsEntity.toPerformanceMetrics(): PerformanceMetrics {
    return PerformanceMetrics(
        id = this.id,
        timestamp = this.timestamp,
        responseTime = this.responseTime,
        throughput = this.throughput,
        errorRate = this.errorRate,
        availability = this.availability,
        resourceUtilization = this.resourceUtilization,
        userSatisfaction = this.userSatisfaction
    )
}

/**
 * Baza danych Room dla metryk
 */
@Database(
    entities = [
        SystemMetricsEntity::class,
        HealthStatusEntity::class,
        AlertEntity::class,
        PerformanceMetricsEntity::class
    ],
    version = 1
)
abstract class MetricsDatabase : RoomDatabase() {
    abstract fun metricsDao(): MetricsDao
    
    companion object {
        @Volatile
        private var INSTANCE: MetricsDatabase? = null
        
        fun getInstance(context: Context): MetricsDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MetricsDatabase::class.java,
                    "metrics_database"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

/**
 * Encje bazy danych dla metryk
 */
@Entity(tableName = "system_metrics")
data class SystemMetricsEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val cpuUsage: Float,
    val memoryUsage: String,
    val diskUsage: String,
    val networkMetrics: String,
    val appMetrics: String,
    val databaseMetrics: String
)

@Entity(tableName = "health_status")
data class HealthStatusEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val status: String,
    val components: String,
    val checks: String,
    val uptime: Long,
    val version: String,
    val environment: String
)

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val severity: String,
    val type: String,
    val title: String,
    val message: String,
    val source: String,
    val context: String,
    val isAcknowledged: Boolean,
    val acknowledgedBy: String?,
    val acknowledgedAt: Long?,
    val resolvedAt: Long?
)

@Entity(tableName = "performance_metrics")
data class PerformanceMetricsEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val responseTime: Float,
    val throughput: Float,
    val errorRate: Float,
    val availability: Float,
    val resourceUtilization: Float,
    val userSatisfaction: Float
)

/**
 * DAO dla metryk
 */
@Dao
interface MetricsDao {
    // System Metrics
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSystemMetrics(metrics: SystemMetricsEntity)
    
    @Query("SELECT * FROM system_metrics ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastSystemMetrics(): SystemMetricsEntity?
    
    @Query("SELECT * FROM system_metrics ORDER BY timestamp DESC LIMIT 1")
    fun getLastSystemMetricsSync(): SystemMetricsEntity?
    
    @Query("DELETE FROM system_metrics WHERE timestamp < :cutoffTime")
    suspend fun deleteOldSystemMetrics(cutoffTime: Long)
    
    // Health Status
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHealthStatus(healthStatus: HealthStatusEntity)
    
    @Query("SELECT * FROM health_status ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastHealthStatus(): HealthStatusEntity?
    
    @Query("SELECT * FROM health_status ORDER BY timestamp DESC LIMIT 1")
    fun getLastHealthStatusSync(): HealthStatusEntity?
    
    @Query("DELETE FROM health_status WHERE timestamp < :cutoffTime")
    suspend fun deleteOldHealthStatuses(cutoffTime: Long)
    
    // Alerts
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AlertEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllAlerts(alerts: List<AlertEntity>)
    
    @Query("SELECT * FROM alerts ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentAlerts(limit: Int): List<AlertEntity>
    
    @Query("SELECT * FROM alerts ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentAlertsSync(limit: Int): List<AlertEntity>
    
    @Query("DELETE FROM alerts WHERE timestamp < :cutoffTime")
    suspend fun deleteOldAlerts(cutoffTime: Long)
    
    // Performance Metrics
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerformanceMetrics(metrics: PerformanceMetricsEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPerformanceMetrics(metrics: List<PerformanceMetricsEntity>)
    
    @Query("SELECT * FROM performance_metrics ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentPerformanceMetrics(limit: Int): List<PerformanceMetricsEntity>
    
    @Query("SELECT * FROM performance_metrics ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentPerformanceMetricsSync(limit: Int): List<PerformanceMetricsEntity>
    
    @Query("DELETE FROM performance_metrics WHERE timestamp < :cutoffTime")
    suspend fun deleteOldPerformanceMetrics(cutoffTime: Long)
    
    // Error Counts
    @Query("INSERT OR REPLACE INTO error_counts (errorType, count) VALUES (:errorType, COALESCE((SELECT count FROM error_counts WHERE errorType = :errorType), 0) + 1)")
    suspend fun incrementErrorCount(errorType: String)
}