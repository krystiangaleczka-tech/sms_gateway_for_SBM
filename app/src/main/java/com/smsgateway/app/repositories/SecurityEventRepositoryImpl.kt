package com.smsgateway.app.repositories

import androidx.room.RoomDatabase
import com.smsgateway.app.database.AppDatabase
import com.smsgateway.app.models.security.SecurityEvent
import kotlinx.coroutines.flow.Flow

/**
 * Implementacja repozytorium dla zdarzeń bezpieczeństwa
 */
class SecurityEventRepositoryImpl(private val database: AppDatabase) : SecurityEventRepository {
    
    override fun getAllEvents(): Flow<List<SecurityEvent>> {
        return database.securityEventDao().getAllEvents()
    }
    
    override suspend fun getEventById(id: String): SecurityEvent? {
        return database.securityEventDao().getEventById(id)
    }
    
    override fun getEventsByType(type: String): Flow<List<SecurityEvent>> {
        return database.securityEventDao().getEventsByType(type)
    }
    
    override fun getEventsBySeverity(severity: String): Flow<List<SecurityEvent>> {
        return database.securityEventDao().getEventsBySeverity(severity)
    }
    
    override suspend fun getRecentEvents(since: Long): List<SecurityEvent> {
        return database.securityEventDao().getRecentEvents(since)
    }
    
    override suspend fun getRecentEventsByType(type: String, since: Long): List<SecurityEvent> {
        return database.securityEventDao().getRecentEventsByType(type, since)
    }
    
    override suspend fun getRecentEventsBySeverity(severity: String, since: Long): List<SecurityEvent> {
        return database.securityEventDao().getRecentEventsBySeverity(severity, since)
    }
    
    override suspend fun getUnresolvedEvents(): List<SecurityEvent> {
        return database.securityEventDao().getUnresolvedEvents()
    }
    
    override suspend fun insertEvent(event: SecurityEvent): Long {
        return database.securityEventDao().insertEvent(event)
    }
    
    override suspend fun insertEvents(events: List<SecurityEvent>) {
        return database.securityEventDao().insertEvents(events)
    }
    
    override suspend fun updateEventResolved(id: String, resolved: Boolean): Int {
        return database.securityEventDao().updateEventResolved(id, resolved)
    }
    
    override suspend fun deleteEventById(id: String): Int {
        return database.securityEventDao().deleteEventById(id)
    }
    
    override suspend fun deleteOldEvents(before: Long): Int {
        return database.securityEventDao().deleteOldEvents(before)
    }
    
    override suspend fun deleteAllEvents(): Int {
        return database.securityEventDao().deleteAllEvents()
    }
    
    override suspend fun getTotalCount(): Int {
        return database.securityEventDao().getTotalCount()
    }
    
    override suspend fun getCountByType(type: String): Int {
        return database.securityEventDao().getCountByType(type)
    }
    
    override suspend fun getCountBySeverity(severity: String): Int {
        return database.securityEventDao().getCountBySeverity(severity)
    }
    
    override suspend fun getUnresolvedCount(): Int {
        return database.securityEventDao().getUnresolvedCount()
    }
    
    override suspend fun getRecentCount(since: Long): Int {
        return database.securityEventDao().getRecentCount(since)
    }
    
    override suspend fun getFirstTimestamp(): Long? {
        return database.securityEventDao().getFirstTimestamp()
    }
    
    override suspend fun getLastTimestamp(): Long? {
        return database.securityEventDao().getLastTimestamp()
    }
}