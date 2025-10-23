package com.smsgateway.app.repositories

import androidx.room.*
import com.smsgateway.app.models.security.ApiToken
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiTokenRepository {
    
    @Query("SELECT * FROM api_tokens ORDER BY createdAt DESC")
    fun getAllTokens(): Flow<List<ApiToken>>
    
    @Query("SELECT * FROM api_tokens WHERE id = :id")
    suspend fun getTokenById(id: String): ApiToken?
    
    @Query("SELECT * FROM api_tokens WHERE token = :token")
    suspend fun getTokenByValue(token: String): ApiToken?
    
    @Query("SELECT * FROM api_tokens WHERE name = :name")
    suspend fun getTokenByName(name: String): ApiToken?
    
    @Query("SELECT * FROM api_tokens WHERE isActive = 1")
    fun getActiveTokens(): Flow<List<ApiToken>>
    
    @Query("SELECT * FROM api_tokens WHERE isActive = 1 AND expiresAt > :currentTime")
    fun getValidTokens(currentTime: Long = System.currentTimeMillis()): Flow<List<ApiToken>>
    
    @Query("SELECT * FROM api_tokens WHERE isActive = 1 AND expiresAt <= :currentTime")
    suspend fun getExpiredTokens(currentTime: Long = System.currentTimeMillis()): List<ApiToken>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToken(token: ApiToken): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTokens(tokens: List<ApiToken>)
    
    @Query("UPDATE api_tokens SET isActive = 0 WHERE id = :id")
    suspend fun deactivateToken(id: String): Int
    
    @Query("UPDATE api_tokens SET isActive = 0 WHERE expiresAt <= :currentTime")
    suspend fun deactivateExpiredTokens(currentTime: Long = System.currentTimeMillis()): Int
    
    @Query("UPDATE api_tokens SET lastUsedAt = :timestamp WHERE token = :token")
    suspend fun updateLastUsed(token: String, timestamp: Long = System.currentTimeMillis()): Int
    
    @Query("DELETE FROM api_tokens WHERE id = :id")
    suspend fun deleteTokenById(id: String): Int
    
    @Query("DELETE FROM api_tokens WHERE isActive = 0 AND expiresAt < :cutoffTime")
    suspend fun deleteOldInactiveTokens(cutoffTime: Long): Int
    
    @Query("DELETE FROM api_tokens")
    suspend fun deleteAllTokens(): Int
    
    @Query("SELECT COUNT(*) FROM api_tokens WHERE isActive = 1")
    suspend fun getActiveTokenCount(): Int
    
    @Query("SELECT COUNT(*) FROM api_tokens WHERE isActive = 1 AND expiresAt > :currentTime")
    suspend fun getValidTokenCount(currentTime: Long = System.currentTimeMillis()): Int
    
    @Query("SELECT COUNT(*) FROM api_tokens WHERE isActive = 1 AND expiresAt <= :currentTime")
    suspend fun getExpiredTokenCount(currentTime: Long = System.currentTimeMillis()): Int
}