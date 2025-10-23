package com.smsgateway.app.repositories

import androidx.room.RoomDatabase
import com.smsgateway.app.database.AppDatabase
import com.smsgateway.app.models.security.TunnelConfig
import com.smsgateway.app.models.security.TunnelStatus
import kotlinx.coroutines.flow.Flow

/**
 * Implementacja repozytorium dla konfiguracji tuneli Cloudflare
 */
class TunnelConfigRepositoryImpl(private val database: AppDatabase) : TunnelConfigRepository {
    
    override fun getAllConfigs(): Flow<List<TunnelConfig>> {
        return database.tunnelConfigDao().getAllConfigs()
    }
    
    override suspend fun getConfigById(id: String): TunnelConfig? {
        return database.tunnelConfigDao().getConfigById(id)
    }
    
    override suspend fun getConfigByName(name: String): TunnelConfig? {
        return database.tunnelConfigDao().getConfigByName(name)
    }
    
    override suspend fun getConfigByTunnelId(tunnelId: String): TunnelConfig? {
        return database.tunnelConfigDao().getConfigByTunnelId(tunnelId)
    }
    
    override fun getActiveConfigs(): Flow<List<TunnelConfig>> {
        return database.tunnelConfigDao().getActiveConfigs()
    }
    
    override fun getConfigsByStatus(status: TunnelStatus): Flow<List<TunnelConfig>> {
        return database.tunnelConfigDao().getConfigsByStatus(status)
    }
    
    override suspend fun getEnabledConfigs(): List<TunnelConfig> {
        return database.tunnelConfigDao().getEnabledConfigs()
    }
    
    override suspend fun getConnectedConfigs(): List<TunnelConfig> {
        return database.tunnelConfigDao().getConnectedConfigs()
    }
    
    override suspend fun getDisconnectedConfigs(): List<TunnelConfig> {
        return database.tunnelConfigDao().getDisconnectedConfigs()
    }
    
    override suspend fun getConfigsByAccountId(accountId: String): List<TunnelConfig> {
        return database.tunnelConfigDao().getConfigsByAccountId(accountId)
    }
    
    override suspend fun insertConfig(config: TunnelConfig): Long {
        return database.tunnelConfigDao().insertConfig(config)
    }
    
    override suspend fun insertConfigs(configs: List<TunnelConfig>) {
        return database.tunnelConfigDao().insertConfigs(configs)
    }
    
    override suspend fun updateConfig(config: TunnelConfig): Int {
        return database.tunnelConfigDao().updateConfig(config)
    }
    
    override suspend fun updateTunnelStatus(id: String, status: TunnelStatus): Int {
        return database.tunnelConfigDao().updateTunnelStatus(id, status)
    }
    
    override suspend fun updateTunnelUrl(id: String, url: String): Int {
        return database.tunnelConfigDao().updateTunnelUrl(id, url)
    }
    
    override suspend fun updateLastConnected(id: String, timestamp: Long): Int {
        return database.tunnelConfigDao().updateLastConnected(id, timestamp)
    }
    
    override suspend fun updateConnectionCount(id: String, count: Int): Int {
        return database.tunnelConfigDao().updateConnectionCount(id, count)
    }
    
    override suspend fun enableConfig(id: String): Int {
        return database.tunnelConfigDao().enableConfig(id)
    }
    
    override suspend fun disableConfig(id: String): Int {
        return database.tunnelConfigDao().disableConfig(id)
    }
    
    override suspend fun deleteConfigById(id: String): Int {
        return database.tunnelConfigDao().deleteConfigById(id)
    }
    
    override suspend fun deleteConfigByName(name: String): Int {
        return database.tunnelConfigDao().deleteConfigByName(name)
    }
    
    override suspend fun deleteConfigsByAccountId(accountId: String): Int {
        return database.tunnelConfigDao().deleteConfigsByAccountId(accountId)
    }
    
    override suspend fun deleteAllConfigs(): Int {
        return database.tunnelConfigDao().deleteAllConfigs()
    }
    
    override suspend fun getConfigCount(): Int {
        return database.tunnelConfigDao().getConfigCount()
    }
    
    override suspend fun getActiveConfigCount(): Int {
        return database.tunnelConfigDao().getActiveConfigCount()
    }
    
    override suspend fun getEnabledConfigCount(): Int {
        return database.tunnelConfigDao().getEnabledConfigCount()
    }
    
    override suspend fun getConnectedConfigCount(): Int {
        return database.tunnelConfigDao().getConnectedConfigCount()
    }
    
    override suspend fun getConfigCountByStatus(status: TunnelStatus): Int {
        return database.tunnelConfigDao().getConfigCountByStatus(status)
    }
    
    override suspend fun getConfigCountByAccountId(accountId: String): Int {
        return database.tunnelConfigDao().getConfigCountByAccountId(accountId)
    }
    
    override suspend fun getMostUsedConfigs(limit: Int): List<TunnelConfig> {
        return database.tunnelConfigDao().getMostUsedConfigs(limit)
    }
    
    override suspend fun getRecentlyConnectedConfigs(limit: Int): List<TunnelConfig> {
        return database.tunnelConfigDao().getRecentlyConnectedConfigs(limit)
    }
    
    override suspend fun getConfigsNeedingAttention(): List<TunnelConfig> {
        return database.tunnelConfigDao().getConfigsNeedingAttention()
    }
    
    override suspend fun refreshConfigStatus(id: String): TunnelConfig? {
        val config = getConfigById(id) ?: return null
        
        // Tutaj można dodać logikę do sprawdzenia aktualnego statusu tunelu
        // np. przez wywołanie API Cloudflare
        
        return config
    }
    
    override suspend fun markAllAsDisconnected(): Int {
        return database.tunnelConfigDao().markAllAsDisconnected()
    }
}