package com.smsgateway.app.repositories

import androidx.room.*
import com.smsgateway.app.models.security.RateLimitEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface RateLimitRepository {
    
    @Query("SELECT * FROM rate_limit_entries WHERE identifier = :identifier ORDER BY timestamp DESC")
    fun getEntriesByIdentifier(identifier: String): Flow<List<RateLimitEntry>>
    
    @Query("SELECT * FROM rate_limit_entries WHERE identifier = :identifier AND timestamp >= :since")
    suspend fun getRecentEntriesByIdentifier(identifier: String, since: Long): List<RateLimitEntry>
    
    @Query("SELECT COUNT(*) FROM rate_limit_entries WHERE identifier = :identifier AND timestamp >= :since")
    suspend fun getRecentCountByIdentifier(identifier: String, since: Long): Int
    
    @Query("SELECT * FROM rate_limit_entries WHERE identifier = :identifier AND timestamp >= :since AND type = :type")
    suspend fun getRecentEntriesByIdentifierAndType(identifier: String, since: Long, type: String): List<RateLimitEntry>
    
    @Query("SELECT COUNT(*) FROM rate_limit_entries WHERE identifier = :identifier AND timestamp >= :since AND type = :type")
    suspend fun getRecentCountByIdentifierAndType(identifier: String, since: Long, type: String): Int
    
    @Query("SELECT DISTINCT identifier FROM rate_limit_entries")
    suspend fun getAllIdentifiers(): List<String>
    
    @Query("SELECT * FROM rate_limit_entries WHERE timestamp < :before")
    suspend fun getOldEntries(before: Long): List<RateLimitEntry>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: RateLimitEntry): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<RateLimitEntry>)
    
    @Query("DELETE FROM rate_limit_entries WHERE identifier = :identifier")
    suspend fun deleteEntriesByIdentifier(identifier: String): Int
    
    @Query("DELETE FROM rate_limit_entries WHERE identifier = :identifier AND timestamp < :before")
    suspend fun deleteOldEntriesByIdentifier(identifier: String, before: Long): Int
    
    @Query("DELETE FROM rate_limit_entries WHERE timestamp < :before")
    suspend fun deleteOldEntries(before: Long): Int
    
    @Query("DELETE FROM rate_limit_entries")
    suspend fun deleteAllEntries(): Int
    
    @Query("SELECT COUNT(*) FROM rate_limit_entries")
    suspend fun getTotalCount(): Int
    
    @Query("SELECT COUNT(*) FROM rate_limit_entries WHERE identifier = :identifier")
    suspend fun getCountByIdentifier(identifier: String): Int
    
    @Query("SELECT COUNT(*) FROM rate_limit_entries WHERE timestamp >= :since")
    suspend fun getRecentCount(since: Long): Int
    
    @Query("SELECT MIN(timestamp) FROM rate_limit_entries WHERE identifier = :identifier")
    suspend fun getFirstTimestampByIdentifier(identifier: String): Long?
    
    @Query("SELECT MAX(timestamp) FROM rate_limit_entries WHERE identifier = :identifier")
    suspend fun getLastTimestampByIdentifier(identifier: String): Long?
    
    @Query("SELECT * FROM rate_limit_entries WHERE identifier = :identifier AND timestamp >= :since AND type = :type AND blocked = 1 LIMIT 1")
    suspend fun getActiveBlockEntry(identifier: String, since: Long, type: String): RateLimitEntry?
    
    @Query("UPDATE rate_limit_entries SET blocked = 0 WHERE identifier = :identifier AND type = :type")
    suspend fun unblockIdentifier(identifier: String, type: String): Int
    
    @Query("UPDATE rate_limit_entries SET blocked = 1 WHERE identifier = :identifier AND type = :type AND timestamp >= :since")
    suspend fun blockIdentifier(identifier: String, type: String, since: Long): Int
}