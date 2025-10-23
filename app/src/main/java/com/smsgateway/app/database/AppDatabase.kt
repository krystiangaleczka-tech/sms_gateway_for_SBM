package com.smsgateway.app.database

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Główna klasa bazy danych Room dla aplikacji SMS Gateway
 */
@Database(
    entities = [
        SmsMessage::class,
        com.smsgateway.app.models.security.SecurityEvent::class,
        com.smsgateway.app.models.security.ApiToken::class,
        com.smsgateway.app.models.security.RateLimitEntry::class,
        com.smsgateway.app.models.security.TunnelConfig::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    /**
     * Zwraca DAO dla operacji na wiadomościach SMS
     */
    abstract fun smsDao(): SmsDao
    abstract fun securityEventDao(): com.smsgateway.app.repositories.SecurityEventRepository
    abstract fun apiTokenDao(): com.smsgateway.app.repositories.ApiTokenRepository
    abstract fun rateLimitDao(): com.smsgateway.app.repositories.RateLimitRepository
    abstract fun tunnelConfigDao(): com.smsgateway.app.repositories.TunnelConfigRepository
    
    companion object {
        /**
         * Nazwa pliku bazy danych
         */
        private const val DATABASE_NAME = "sms_gateway_database"
        
        /**
         * Instancja bazy danych (singleton)
         */
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        /**
         * Zwraca instancję bazy danych (wzorzec singleton)
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                .addCallback(DatabaseCallback())
                .addMigrations(MIGRATION_1_2) // Dodajemy migrację dla tabel bezpieczeństwa
                .fallbackToDestructiveMigration() // W prostszej wersji - do zmiany w produkcji
                .build()
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * Czyści instancję bazy danych (użyteczne w testach)
         */
        fun clearInstance() {
            INSTANCE = null
        }
    }
    
    /**
     * Callback dla bazy danych - inicjalizacja danych startowych
     */
    private class DatabaseCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // Inicjalizacja bazy danych - można dodać dane startowe
            // Na razie pozostawiamy pustą bazę
        }
    }
}

/**
 * Konwertery typów dla Room
 */
class Converters {
    
    /**
     * Konwertuje enum SmsStatus na String
     */
    @TypeConverter
    fun fromSmsStatus(status: SmsStatus): String {
        return status.name
    }
    
    /**
     * Konwertuje String na enum SmsStatus
     */
    @TypeConverter
    fun toSmsStatus(status: String): SmsStatus {
        return SmsStatus.valueOf(status)
    }
    
    /**
     * Konwertuje enum SecurityEventType na String
     */
    @TypeConverter
    fun fromSecurityEventType(type: com.smsgateway.app.models.security.SecurityEventType): String {
        return type.name
    }
    
    /**
     * Konwertuje String na enum SecurityEventType
     */
    @TypeConverter
    fun toSecurityEventType(type: String): com.smsgateway.app.models.security.SecurityEventType {
        return com.smsgateway.app.models.security.SecurityEventType.valueOf(type)
    }
    
    /**
     * Konwertuje enum SecuritySeverity na String
     */
    @TypeConverter
    fun fromSecuritySeverity(severity: com.smsgateway.app.models.security.SecuritySeverity): String {
        return severity.name
    }
    
    /**
     * Konwertuje String na enum SecuritySeverity
     */
    @TypeConverter
    fun toSecuritySeverity(severity: String): com.smsgateway.app.models.security.SecuritySeverity {
        return com.smsgateway.app.models.security.SecuritySeverity.valueOf(severity)
    }
    
    /**
     * Konwertuje enum ApiTokenType na String
     */
    @TypeConverter
    fun fromApiTokenType(type: com.smsgateway.app.models.security.ApiTokenType): String {
        return type.name
    }
    
    /**
     * Konwertuje String na enum ApiTokenType
     */
    @TypeConverter
    fun toApiTokenType(type: String): com.smsgateway.app.models.security.ApiTokenType {
        return com.smsgateway.app.models.security.ApiTokenType.valueOf(type)
    }
    
    /**
     * Konwertuje enum RateLimitType na String
     */
    @TypeConverter
    fun fromRateLimitType(type: com.smsgateway.app.models.security.RateLimitType): String {
        return type.name
    }
    
    /**
     * Konwertuje String na enum RateLimitType
     */
    @TypeConverter
    fun toRateLimitType(type: String): com.smsgateway.app.models.security.RateLimitType {
        return com.smsgateway.app.models.security.RateLimitType.valueOf(type)
    }
    
    /**
     * Konwertuje enum TunnelStatus na String
     */
    @TypeConverter
    fun fromTunnelStatus(status: com.smsgateway.app.models.security.TunnelStatus): String {
        return status.name
    }
    
    /**
     * Konwertuje String na enum TunnelStatus
     */
    @TypeConverter
    fun toTunnelStatus(status: String): com.smsgateway.app.models.security.TunnelStatus {
        return com.smsgateway.app.models.security.TunnelStatus.valueOf(status)
    }
    
    /**
     * Konwertuje enum TunnelType na String
     */
    @TypeConverter
    fun fromTunnelType(type: com.smsgateway.app.models.security.TunnelType): String {
        return type.name
    }
    
    /**
     * Konwertuje String na enum TunnelType
     */
    @TypeConverter
    fun toTunnelType(type: String): com.smsgateway.app.models.security.TunnelType {
        return com.smsgateway.app.models.security.TunnelType.valueOf(type)
    }
}

/**
 * Migracja z wersji 1 do 2 - dodanie tabel bezpieczeństwa
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Tabela security_events
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS security_events (
                id TEXT PRIMARY KEY NOT NULL,
                type TEXT NOT NULL,
                severity TEXT NOT NULL,
                source TEXT NOT NULL,
                message TEXT NOT NULL,
                details TEXT,
                ipAddress TEXT,
                userAgent TEXT,
                timestamp INTEGER NOT NULL,
                resolved INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        
        // Tabela api_tokens
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS api_tokens (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                token TEXT NOT NULL,
                permissions TEXT,
                expiresAt INTEGER NOT NULL,
                lastUsedAt INTEGER,
                isActive INTEGER NOT NULL DEFAULT 1,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        
        // Tabela rate_limit_entries
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS rate_limit_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                identifier TEXT NOT NULL,
                type TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                blocked INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        
        // Tabela tunnel_configs
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tunnel_configs (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                hostname TEXT NOT NULL,
                port INTEGER NOT NULL,
                type TEXT NOT NULL,
                credentials TEXT,
                metadata TEXT,
                isActive INTEGER NOT NULL DEFAULT 1,
                connectionStatus TEXT NOT NULL DEFAULT 'DISCONNECTED',
                lastConnectedAt INTEGER,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        
        // Indeksy dla tabel bezpieczeństwa
        database.execSQL("CREATE INDEX IF NOT EXISTS index_security_events_timestamp ON security_events(timestamp)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_security_events_type ON security_events(type)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_api_tokens_token ON api_tokens(token)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_api_tokens_isActive ON api_tokens(isActive)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_rate_limit_entries_identifier ON rate_limit_entries(identifier)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_rate_limit_entries_timestamp ON rate_limit_entries(timestamp)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_tunnel_configs_isActive ON tunnel_configs(isActive)")
    }
}

/**
 * Repozytorium dla operacji na bazie danych - abstrakcja nad DAO
 */
class SmsRepository(private val smsDao: SmsDao) {
    
    /**
     * Wstawia nową wiadomość SMS
     */
    suspend fun insertSms(smsMessage: SmsMessage): Long {
        return smsDao.insertSms(smsMessage)
    }
    
    /**
     * Pobiera wszystkie wiadomości SMS jako Flow
     */
    fun getAllSms(): Flow<List<SmsMessage>> {
        return smsDao.getAllSms()
    }
    
    /**
     * Pobiera wiadomości do wysyłki
     */
    suspend fun getScheduledForSending(): List<SmsMessage> {
        val currentTime = System.currentTimeMillis()
        return smsDao.getScheduledForSending(currentTime)
    }
    
    /**
     * Pobiera wiadomości w kolejce
     */
    suspend fun getQueuedSms(): List<SmsMessage> {
        return smsDao.getQueuedSms()
    }
    
    /**
     * Aktualizuje status wiadomości
     */
    suspend fun updateSmsStatus(id: Long, status: SmsStatus): Int {
        return smsDao.updateSmsStatus(id, status)
    }
    
    /**
     * Aktualizuje status wiadomości z datą wysyłki
     */
    suspend fun updateSmsStatusWithSentTime(id: Long, status: SmsStatus, sentAt: Long): Int {
        return smsDao.updateSmsStatusWithSentTime(id, status, sentAt)
    }
    
    /**
     * Aktualizuje status wiadomości z błędem
     */
    suspend fun updateSmsStatusWithError(id: Long, status: SmsStatus, sentAt: Long, errorMessage: String?): Int {
        return smsDao.updateSmsStatusWithError(id, status, sentAt, errorMessage)
    }
    
    /**
     * Zwiększa licznik prób ponowienia
     */
    suspend fun incrementRetryCount(id: Long): Int {
        return smsDao.incrementRetryCount(id)
    }
    
    /**
     * Pobiera statystyki wiadomości z ostatnich 24 godzin
     */
    suspend fun getSmsStatsLast24Hours(): List<StatusCount> {
        val twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        return smsDao.getSmsStatsLast24Hours(twentyFourHoursAgo)
    }
    
    /**
     * Pobiera liczbę wiadomości według statusu
     */
    suspend fun getSmsCountByStatus(status: SmsStatus): Int {
        return smsDao.getSmsCountByStatus(status)
    }
    
    /**
     * Usuwa wiadomość po ID
     */
    suspend fun deleteSmsById(id: Long): Int {
        return smsDao.deleteSmsById(id)
    }
    
    /**
     * Czyści stare wiadomości
     */
    suspend fun cleanupOldMessages(): Int {
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        return smsDao.cleanupOldMessages(thirtyDaysAgo)
    }
    
    /**
     * Pobiera wiadomość SMS po ID
     */
    suspend fun getSmsById(id: Long): SmsMessage? {
        return smsDao.getSmsById(id)
    }
    
    /**
     * Pobiera wszystkie wiadomości SMS (synchronicznie)
     */
    suspend fun getAllSmsSync(): List<SmsMessage> {
        return smsDao.getAllSmsSync()
    }
    
    /**
     * Pobiera wiadomości SMS z paginacją
     */
    suspend fun getSmsWithPagination(page: Int, limit: Int, status: String? = null): PaginatedResult<SmsMessage> {
        val offset = (page - 1) * limit
        
        val messages = if (status != null) {
            smsDao.getSmsWithPaginationAndStatus(status, limit, offset)
        } else {
            smsDao.getSmsWithPagination(limit, offset)
        }
        
        val totalCount = if (status != null) {
            smsDao.getSmsCountByStatusString(status)
        } else {
            smsDao.getSmsTotalCount()
        }
        
        val totalPages = (totalCount + limit - 1) / limit // Math.ceil(totalCount / limit)
        
        return PaginatedResult(
            data = messages,
            total = totalCount,
            page = page,
            limit = limit,
            totalPages = totalPages,
            hasNextPage = page < totalPages,
            hasPreviousPage = page > 1
        )
    }
}

/**
 * Klasa danych dla statystyk statusów
 */
data class StatusCount(
    val status: String,
    val count: Int
)

/**
 * Klasa danych dla wyników paginacji
 */
data class PaginatedResult<T>(
    val data: List<T>,
    val total: Int,
    val page: Int,
    val limit: Int,
    val totalPages: Int,
    val hasNextPage: Boolean,
    val hasPreviousPage: Boolean
)