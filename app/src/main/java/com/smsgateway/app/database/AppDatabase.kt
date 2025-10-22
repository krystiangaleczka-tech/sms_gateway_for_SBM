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
    entities = [SmsMessage::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    /**
     * Zwraca DAO dla operacji na wiadomościach SMS
     */
    abstract fun smsDao(): SmsDao
    
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
}