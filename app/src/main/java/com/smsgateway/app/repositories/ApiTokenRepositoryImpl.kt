package com.smsgateway.app.repositories

import androidx.room.RoomDatabase
import com.smsgateway.app.database.AppDatabase
import com.smsgateway.app.models.security.ApiToken
import kotlinx.coroutines.flow.Flow

/**
 * Implementacja repozytorium dla tokenów API
 */
class ApiTokenRepositoryImpl(private val database: AppDatabase) : ApiTokenRepository {
    
    override fun getAllTokens(): Flow<List<ApiToken>> {
        return database.apiTokenDao().getAllTokens()
    }
    
    override suspend fun getTokenById(id: String): ApiToken? {
        return database.apiTokenDao().getTokenById(id)
    }
    
    override suspend fun getTokenByValue(token: String): ApiToken? {
        return database.apiTokenDao().getTokenByValue(token)
    }
    
    override suspend fun getTokenByName(name: String): ApiToken? {
        return database.apiTokenDao().getTokenByName(name)
    }
    
    override fun getActiveTokens(): Flow<List<ApiToken>> {
        return database.apiTokenDao().getActiveTokens()
    }
    
    override fun getValidTokens(currentTime: Long): Flow<List<ApiToken>> {
        return database.apiTokenDao().getValidTokens(currentTime)
    }
    
    override suspend fun getExpiredTokens(currentTime: Long): List<ApiToken> {
        return database.apiTokenDao().getExpiredTokens(currentTime)
    }
    
    override suspend fun insertToken(token: ApiToken): Long {
        return database.apiTokenDao().insertToken(token)
    }
    
    override suspend fun insertTokens(tokens: List<ApiToken>) {
        return database.apiTokenDao().insertTokens(tokens)
    }
    
    override suspend fun deactivateToken(id: String): Int {
        return database.apiTokenDao().deactivateToken(id)
    }
    
    override suspend fun deactivateExpiredTokens(currentTime: Long): Int {
        return database.apiTokenDao().deactivateExpiredTokens(currentTime)
    }
    
    override suspend fun updateLastUsed(token: String, timestamp: Long): Int {
        return database.apiTokenDao().updateLastUsed(token, timestamp)
    }
    
    override suspend fun deleteTokenById(id: String): Int {
        return database.apiTokenDao().deleteTokenById(id)
    }
    
    override suspend fun deleteOldInactiveTokens(cutoffTime: Long): Int {
        return database.apiTokenDao().deleteOldInactiveTokens(cutoffTime)
    }
    
    override suspend fun deleteAllTokens(): Int {
        return database.apiTokenDao().deleteAllTokens()
    }
    
    override suspend fun getActiveTokenCount(): Int {
        return database.apiTokenDao().getActiveTokenCount()
    }
    
    override suspend fun getValidTokenCount(currentTime: Long): Int {
        return database.apiTokenDao().getValidTokenCount(currentTime)
    }
    
    override suspend fun getExpiredTokenCount(currentTime: Long): Int {
        return database.apiTokenDao().getExpiredTokenCount(currentTime)
    }
}