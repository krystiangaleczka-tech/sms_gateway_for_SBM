package com.smsgateway.app.repositories

import androidx.room.RoomDatabase
import com.smsgateway.app.database.AppDatabase
import com.smsgateway.app.models.security.RateLimitEntry
import kotlinx.coroutines.flow.Flow

/**
 * Implementacja repozytorium dla limitów żądań
 */
class RateLimitRepositoryImpl(private val database: AppDatabase) : RateLimitRepository {
    
    override fun getAllEntries(): Flow<List<RateLimitEntry>> {
        return database.rateLimitDao().getAllEntries()
    }
    
    override suspend fun getEntryById(id: String): RateLimitEntry? {
        return database.rateLimitDao().getEntryById(id)
    }
    
    override suspend fun getEntriesByClientId(clientId: String): List<RateLimitEntry> {
        return database.rateLimitDao().getEntriesByClientId(clientId)
    }
    
    override suspend fun getActiveEntriesByClientId(clientId: String): List<RateLimitEntry> {
        return database.rateLimitDao().getActiveEntriesByClientId(clientId)
    }
    
    override suspend fun getEntriesByEndpoint(endpoint: String): List<RateLimitEntry> {
        return database.rateLimitDao().getEntriesByEndpoint(endpoint)
    }
    
    override suspend fun getEntriesByTimeRange(startTime: Long, endTime: Long): List<RateLimitEntry> {
        return database.rateLimitDao().getEntriesByTimeRange(startTime, endTime)
    }
    
    override suspend fun getRecentEntriesByClientId(clientId: String, since: Long): List<RateLimitEntry> {
        return database.rateLimitDao().getRecentEntriesByClientId(clientId, since)
    }
    
    override suspend fun getRecentEntriesByEndpoint(endpoint: String, since: Long): List<RateLimitEntry> {
        return database.rateLimitDao().getRecentEntriesByEndpoint(endpoint, since)
    }
    
    override suspend fun getRecentEntriesByClientAndEndpoint(
        clientId: String, 
        endpoint: String, 
        since: Long
    ): List<RateLimitEntry> {
        return database.rateLimitDao().getRecentEntriesByClientAndEndpoint(clientId, endpoint, since)
    }
    
    override suspend fun insertEntry(entry: RateLimitEntry): Long {
        return database.rateLimitDao().insertEntry(entry)
    }
    
    override suspend fun insertEntries(entries: List<RateLimitEntry>) {
        return database.rateLimitDao().insertEntries(entries)
    }
    
    override suspend fun updateEntry(entry: RateLimitEntry): Int {
        return database.rateLimitDao().updateEntry(entry)
    }
    
    override suspend fun deleteEntryById(id: String): Int {
        return database.rateLimitDao().deleteEntryById(id)
    }
    
    override suspend fun deleteEntriesByClientId(clientId: String): Int {
        return database.rateLimitDao().deleteEntriesByClientId(clientId)
    }
    
    override suspend fun deleteOldEntries(cutoffTime: Long): Int {
        return database.rateLimitDao().deleteOldEntries(cutoffTime)
    }
    
    override suspend fun deleteAllEntries(): Int {
        return database.rateLimitDao().deleteAllEntries()
    }
    
    override suspend fun getEntryCount(): Int {
        return database.rateLimitDao().getEntryCount()
    }
    
    override suspend fun getEntryCountByClientId(clientId: String): Int {
        return database.rateLimitDao().getEntryCountByClientId(clientId)
    }
    
    override suspend fun getEntryCountByEndpoint(endpoint: String): Int {
        return database.rateLimitDao().getEntryCountByEndpoint(endpoint)
    }
    
    override suspend fun getEntryCountByTimeRange(startTime: Long, endTime: Long): Int {
        return database.rateLimitDao().getEntryCountByTimeRange(startTime, endTime)
    }
    
    override suspend fun getActiveEntryCount(): Int {
        return database.rateLimitDao().getActiveEntryCount()
    }
    
    override suspend fun getBlockedEntryCount(): Int {
        return database.rateLimitDao().getBlockedEntryCount()
    }
    
    override suspend fun getMostActiveClients(limit: Int): List<Pair<String, Int>> {
        return database.rateLimitDao().getMostActiveClients(limit)
    }
    
    override suspend fun getMostAccessedEndpoints(limit: Int): List<Pair<String, Int>> {
        return database.rateLimitDao().getMostAccessedEndpoints(limit)
    }
    
    override suspend fun cleanupExpiredEntries(): Int {
        val cutoffTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000) // 24 hours ago
        return deleteOldEntries(cutoffTime)
    }
}