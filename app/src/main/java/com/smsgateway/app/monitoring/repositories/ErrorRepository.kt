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
 * Repozytorium błędów z Cache-First Strategy
 * Lokalne przechowywanie błędów w Room Database z synchronizacją z backendem
 */
class ErrorRepository(
    private val context: Context,
    private val apiService: com.smsgateway.app.api.ApiService
) {
    private val database = AppDatabase.getInstance(context)
    private val errorDao = database.errorDao()
    private val mutex = Mutex()
    
    // Cache dla błędów
    private val _errorsCache = MutableStateFlow<List<AppError>>(emptyList())
    val errorsCache: StateFlow<List<AppError>> = _errorsCache.asStateFlow()
    
    // Cache dla statystyk
    private val _statsCache = MutableStateFlow<ErrorStats?>(null)
    val statsCache: StateFlow<ErrorStats?> = _statsCache.asStateFlow()
    
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
        // Wczytaj błędy z lokalnej bazy danych do cache
        _errorsCache.value = errorDao.getAllErrorsSync()
        
        // Oblicz statystyki
        updateStatsCache()
    }
    
    /**
     * Zapis błędu z Cache-First Strategy
     */
    suspend fun saveError(error: AppError) {
        mutex.withLock {
            try {
                // 1. Zapis do lokalnej bazy danych
                errorDao.insertError(error.toEntity())
                
                // 2. Aktualizacja cache
                val currentErrors = _errorsCache.value.toMutableList()
                currentErrors.add(0, error) // Dodaj na początku
                _errorsCache.value = currentErrors
                
                // 3. Aktualizacja statystyk
                updateStatsCache()
                
                // 4. Synchronizacja z backendem (asynchronicznie)
                syncWithBackend()
                
            } catch (e: Exception) {
                // Fallback - tylko cache
                val currentErrors = _errorsCache.value.toMutableList()
                currentErrors.add(0, error)
                _errorsCache.value = currentErrors
                updateStatsCache()
            }
        }
    }
    
    /**
     * Pobieranie błędów z Cache-First Strategy
     */
    suspend fun getErrors(
        filter: ErrorFilter? = null,
        limit: Int = 50,
        offset: Int = 0
    ): List<AppError> {
        return try {
            // 1. Spróbuj z cache
            val cachedErrors = _errorsCache.value
            val filteredErrors = filterErrors(cachedErrors, filter)
            
            if (filteredErrors.isNotEmpty()) {
                return filteredErrors.drop(offset).take(limit)
            }
            
            // 2. Jeśli cache pusty, spróbuj z lokalnej bazy
            val dbErrors = errorDao.getAllErrors()
                .map { it.toAppError() }
            
            if (dbErrors.isNotEmpty()) {
                _errorsCache.value = dbErrors
                updateStatsCache()
                return filterErrors(dbErrors, filter).drop(offset).take(limit)
            }
            
            // 3. Jeśli lokalna baza pusta, spróbuj z backendu
            refreshFromBackend()
            filterErrors(_errorsCache.value, filter).drop(offset).take(limit)
            
        } catch (e: Exception) {
            // Fallback - zwróć z cache nawet jeśli częściowe
            filterErrors(_errorsCache.value, filter).drop(offset).take(limit)
        }
    }
    
    /**
     * Pobieranie statystyk błędów
     */
    suspend fun getErrorStats(filter: ErrorFilter? = null): ErrorStats {
        return try {
            // 1. Spróbuj z cache
            _statsCache.value?.let { return it }
            
            // 2. Oblicz z cache błędów
            val errors = if (filter != null) {
                filterErrors(_errorsCache.value, filter)
            } else {
                _errorsCache.value
            }
            
            val stats = calculateStats(errors)
            _statsCache.value = stats
            stats
            
        } catch (e: Exception) {
            // Fallback - zwróć domyślne statystyki
            ErrorStats(
                totalErrors = 0,
                errorsByType = emptyMap(),
                errorsBySeverity = emptyMap(),
                errorsByHour = emptyMap(),
                recentErrors = emptyList(),
                criticalErrors = emptyList()
            )
        }
    }
    
    /**
     * Oznaczenie błędu jako zgłoszonego
     */
    suspend fun markErrorAsReported(errorId: String) {
        mutex.withLock {
            try {
                // 1. Aktualizacja w lokalnej bazie
                errorDao.markAsReported(errorId)
                
                // 2. Aktualizacja cache
                val currentErrors = _errorsCache.value.map { error ->
                    if (error.id == errorId) {
                        error.copy(isReported = true)
                    } else {
                        error
                    }
                }
                _errorsCache.value = currentErrors
                
            } catch (e: Exception) {
                // Fallback - tylko cache
                val currentErrors = _errorsCache.value.map { error ->
                    if (error.id == errorId) {
                        error.copy(isReported = true)
                    } else {
                        error
                    }
                }
                _errorsCache.value = currentErrors
            }
        }
    }
    
    /**
     * Czyszczenie starych błędów
     */
    suspend fun cleanupOldErrors(retentionDays: Int) {
        try {
            val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
            
            // 1. Usuń z lokalnej bazy
            errorDao.deleteOldErrors(cutoffTime)
            
            // 2. Aktualizacja cache
            val currentErrors = _errorsCache.value.filter { 
                it.timestamp > cutoffTime 
            }
            _errorsCache.value = currentErrors
            
            // 3. Aktualizacja statystyk
            updateStatsCache()
            
        } catch (e: Exception) {
            // Fallback - tylko cache
            val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
            val currentErrors = _errorsCache.value.filter { 
                it.timestamp > cutoffTime 
            }
            _errorsCache.value = currentErrors
            updateStatsCache()
        }
    }
    
    /**
     * Odświeżanie danych z backendu
     */
    suspend fun refreshFromBackend(): Boolean {
        return try {
            val response = apiService.getErrors()
            if (response.isSuccessful) {
                val errors = response.body()?.map { it.toAppError() } ?: emptyList()
                
                // Zapisz do lokalnej bazy
                errorDao.insertAllErrors(errors.map { it.toEntity() })
                
                // Aktualizuj cache
                _errorsCache.value = errors
                updateStatsCache()
                
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
            // Synchronizuj nowe błędy z backendem
            val unsyncedErrors = _errorsCache.value.filter { !it.isReported }
            
            if (unsyncedErrors.isNotEmpty()) {
                for (error in unsyncedErrors) {
                    try {
                        val response = apiService.reportError(error)
                        if (response.isSuccessful) {
                            markErrorAsReported(error.id)
                        }
                    } catch (e: Exception) {
                        // Kontynuuj z innymi błędami
                    }
                }
            }
            
            lastSyncTime = currentTime
        } catch (e: Exception) {
            // Logowanie błędu synchronizacji
        } finally {
            isSyncing = false
        }
    }
    
    /**
     * Filtrowanie błędów
     */
    private fun filterErrors(errors: List<AppError>, filter: ErrorFilter?): List<AppError> {
        if (filter == null) return errors
        
        return errors.filter { error ->
            // Filtr typów
            if (error.type !in filter.types) return@filter false
            
            // Filtr priorytetów
            if (error.severity !in filter.severities) return@filter false
            
            // Filtr daty początkowej
            if (filter.startDate != null && error.timestamp < filter.startDate) {
                return@filter false
            }
            
            // Filtr daty końcowej
            if (filter.endDate != null && error.timestamp > filter.endDate) {
                return@filter false
            }
            
            // Filtr zgłoszenia
            if (filter.isReported != null && error.isReported != filter.isReported) {
                return@filter false
            }
            
            // Filtr wyszukiwania
            if (!filter.searchQuery.isNullOrBlank()) {
                val query = filter.searchQuery.lowercase()
                if (!error.message.lowercase().contains(query) &&
                    !error.context.values.any { it.lowercase().contains(query) }) {
                    return@filter false
                }
            }
            
            true
        }
    }
    
    /**
     * Obliczanie statystyk
     */
    private fun calculateStats(errors: List<AppError>): ErrorStats {
        val totalErrors = errors.size.toLong()
        
        // Grupowanie po typie
        val errorsByType = errors.groupBy { it.type }
            .mapValues { it.value.size.toLong() }
        
        // Grupowanie po priorytecie
        val errorsBySeverity = errors.groupBy { it.severity }
            .mapValues { it.value.size.toLong() }
        
        // Grupowanie po godzinie
        val errorsByHour = errors.groupBy { 
            java.text.SimpleDateFormat("HH", java.util.Locale.getDefault())
                .format(java.util.Date(it.timestamp))
        }.mapValues { it.value.size.toLong() }
        
        // Ostatnie błędy
        val recentErrors = errors.take(10)
        
        // Krytyczne błędy
        val criticalErrors = errors.filter { it.severity == ErrorSeverity.CRITICAL }
        
        return ErrorStats(
            totalErrors = totalErrors,
            errorsByType = errorsByType,
            errorsBySeverity = errorsBySeverity,
            errorsByHour = errorsByHour,
            recentErrors = recentErrors,
            criticalErrors = criticalErrors
        )
    }
    
    /**
     * Aktualizacja cache statystyk
     */
    private fun updateStatsCache() {
        val stats = calculateStats(_errorsCache.value)
        _statsCache.value = stats
    }
}

/**
 * Konwersja AppError na ErrorEntity
 */
private fun AppError.toEntity(): ErrorEntity {
    return ErrorEntity(
        id = this.id,
        timestamp = this.timestamp,
        type = this.type.name,
        severity = this.severity.name,
        message = this.message,
        cause = this.cause,
        context = this.context.toString(),
        stackTrace = this.stackTrace,
        userId = this.userId,
        sessionId = this.sessionId,
        deviceInfo = this.deviceInfo.toString(),
        appVersion = this.appVersion,
        isReported = this.isReported
    )
}

/**
 * Konwersja ErrorEntity na AppError
 */
private fun ErrorEntity.toAppError(): AppError {
    return AppError(
        id = this.id,
        timestamp = this.timestamp,
        type = ErrorType.valueOf(this.type),
        severity = ErrorSeverity.valueOf(this.severity),
        message = this.message,
        cause = this.cause,
        context = emptyMap(), // Prosta deserializacja
        stackTrace = this.stackTrace,
        userId = this.userId,
        sessionId = this.sessionId,
        deviceInfo = null, // Prosta deserializacja
        appVersion = this.appVersion,
        isReported = this.isReported
    )
}

/**
 * Baza danych Room dla błędów
 */
@Database(entities = [ErrorEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun errorDao(): ErrorDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "error_database"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

/**
 * Encja bazy danych dla błędu
 */
@Entity(tableName = "errors")
data class ErrorEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val type: String,
    val severity: String,
    val message: String,
    val cause: String?,
    val context: String,
    val stackTrace: String?,
    val userId: String?,
    val sessionId: String?,
    val deviceInfo: String,
    val appVersion: String,
    val isReported: Boolean
)

/**
 * DAO dla błędów
 */
@Dao
interface ErrorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertError(error: ErrorEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllErrors(errors: List<Entity>)
    
    @Query("SELECT * FROM errors ORDER BY timestamp DESC")
    suspend fun getAllErrors(): List<ErrorEntity>
    
    @Query("SELECT * FROM errors ORDER BY timestamp DESC")
    fun getAllErrorsSync(): List<ErrorEntity>
    
    @Query("UPDATE errors SET isReported = 1 WHERE id = :errorId")
    suspend fun markAsReported(errorId: String)
    
    @Query("DELETE FROM errors WHERE timestamp < :cutoffTime")
    suspend fun deleteOldErrors(cutoffTime: Long)
}