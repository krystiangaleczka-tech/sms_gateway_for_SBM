package com.smsgateway.app.repositories

import androidx.room.*
import com.smsgateway.app.models.security.TunnelConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface TunnelConfigRepository {
    
    @Query("SELECT * FROM tunnel_configs ORDER BY createdAt DESC")
    fun getAllConfigs(): Flow<List<TunnelConfig>>
    
    @Query("SELECT * FROM tunnel_configs WHERE id = :id")
    suspend fun getConfigById(id: String): TunnelConfig?
    
    @Query("SELECT * FROM tunnel_configs WHERE name = :name")
    suspend fun getConfigByName(name: String): TunnelConfig?
    
    @Query("SELECT * FROM tunnel_configs WHERE isActive = 1")
    fun getActiveConfigs(): Flow<List<TunnelConfig>>
    
    @Query("SELECT * FROM tunnel_configs WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveConfig(): TunnelConfig?
    
    @Query("SELECT * FROM tunnel_configs WHERE type = :type")
    fun getConfigsByType(type: String): Flow<List<TunnelConfig>>
    
    @Query("SELECT * FROM tunnel_configs WHERE type = :type AND isActive = 1")
    suspend fun getActiveConfigByType(type: String): TunnelConfig?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: TunnelConfig): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfigs(configs: List<TunnelConfig>)
    
    @Query("UPDATE tunnel_configs SET isActive = 0 WHERE id = :id")
    suspend fun deactivateConfig(id: String): Int
    
    @Query("UPDATE tunnel_configs SET isActive = 0")
    suspend fun deactivateAllConfigs(): Int
    
    @Query("UPDATE tunnel_configs SET isActive = 1 WHERE id = :id")
    suspend fun activateConfig(id: String): Int
    
    @Query("UPDATE tunnel_configs SET isActive = 1 WHERE id = :id AND isActive = 0")
    suspend fun activateConfigIfInactive(id: String): Int
    
    @Query("UPDATE tunnel_configs SET isActive = 0 WHERE isActive = 1 AND id != :id")
    suspend fun deactivateAllExcept(id: String): Int
    
    @Query("UPDATE tunnel_configs SET name = :name, hostname = :hostname, port = :port, type = :type, credentials = :credentials, metadata = :metadata WHERE id = :id")
    suspend fun updateConfig(
        id: String,
        name: String,
        hostname: String,
        port: Int,
        type: String,
        credentials: String?,
        metadata: String?
    ): Int
    
    @Query("UPDATE tunnel_configs SET lastConnectedAt = :timestamp WHERE id = :id")
    suspend fun updateLastConnected(id: String, timestamp: Long = System.currentTimeMillis()): Int
    
    @Query("UPDATE tunnel_configs SET connectionStatus = :status, lastConnectedAt = :timestamp WHERE id = :id")
    suspend fun updateConnectionStatus(
        id: String, 
        status: String, 
        timestamp: Long = System.currentTimeMillis()
    ): Int
    
    @Query("UPDATE tunnel_configs SET connectionStatus = :status WHERE isActive = 1")
    suspend fun updateActiveConfigsStatus(status: String): Int
    
    @Query("DELETE FROM tunnel_configs WHERE id = :id")
    suspend fun deleteConfigById(id: String): Int
    
    @Query("DELETE FROM tunnel_configs WHERE name = :name")
    suspend fun deleteConfigByName(name: String): Int
    
    @Query("DELETE FROM tunnel_configs WHERE isActive = 0 AND createdAt < :cutoffTime")
    suspend fun deleteOldInactiveConfigs(cutoffTime: Long): Int
    
    @Query("DELETE FROM tunnel_configs")
    suspend fun deleteAllConfigs(): Int
    
    @Query("SELECT COUNT(*) FROM tunnel_configs")
    suspend fun getTotalCount(): Int
    
    @Query("SELECT COUNT(*) FROM tunnel_configs WHERE isActive = 1")
    suspend fun getActiveCount(): Int
    
    @Query("SELECT COUNT(*) FROM tunnel_configs WHERE type = :type")
    suspend fun getCountByType(type: String): Int
    
    @Query("SELECT COUNT(*) FROM tunnel_configs WHERE connectionStatus = :status")
    suspend fun getCountByStatus(status: String): Int
    
    @Query("SELECT * FROM tunnel_configs WHERE connectionStatus = :status")
    fun getConfigsByStatus(status: String): Flow<List<TunnelConfig>>
    
    @Query("SELECT * FROM tunnel_configs WHERE connectionStatus != 'CONNECTED' AND isActive = 1")
    fun getDisconnectedActiveConfigs(): Flow<List<TunnelConfig>>
}