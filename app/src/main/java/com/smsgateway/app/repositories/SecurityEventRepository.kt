package com.smsgateway.app.repositories

import androidx.room.*
import com.smsgateway.app.models.security.SecurityEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface SecurityEventRepository {
    
    @Query("SELECT * FROM security_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<SecurityEvent>>
    
    @Query("SELECT * FROM security_events WHERE id = :id")
    suspend fun getEventById(id: String): SecurityEvent?
    
    @Query("SELECT * FROM security_events WHERE type = :type ORDER BY timestamp DESC")
    fun getEventsByType(type: SecurityEvent.EventType): Flow<List<SecurityEvent>>
    
    @Query("SELECT * FROM security_events WHERE severity = :severity ORDER BY timestamp DESC")
    fun getEventsBySeverity(severity: SecurityEvent.Severity): Flow<List<SecurityEvent>>
    
    @Query("SELECT * FROM security_events WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getEventsByTimeRange(startTime: Long, endTime: Long): Flow<List<SecurityEvent>>
    
    @Query("SELECT * FROM security_events WHERE source = :source ORDER BY timestamp DESC")
    fun getEventsBySource(source: String): Flow<List<SecurityEvent>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: SecurityEvent)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<SecurityEvent>)
    
    @Query("DELETE FROM security_events WHERE timestamp < :cutoffTime")
    suspend fun deleteOldEvents(cutoffTime: Long): Int
    
    @Query("DELETE FROM security_events WHERE id = :id")
    suspend fun deleteEventById(id: String): Int
    
    @Query("DELETE FROM security_events")
    suspend fun deleteAllEvents(): Int
    
    @Query("SELECT COUNT(*) FROM security_events")
    suspend fun getEventCount(): Int
    
    @Query("SELECT COUNT(*) FROM security_events WHERE type = :type")
    suspend fun getEventCountByType(type: SecurityEvent.EventType): Int
    
    @Query("SELECT COUNT(*) FROM security_events WHERE severity = :severity")
    suspend fun getEventCountBySeverity(severity: SecurityEvent.Severity): Int
}