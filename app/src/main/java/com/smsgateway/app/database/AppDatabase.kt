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
    exportSchema = true
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
