package com.smsgateway.app.queue

import com.smsgateway.app.database.SmsMessage
import com.smsgateway.app.database.SmsPriority
import com.smsgateway.app.database.SmsStatus
import com.smsgateway.app.database.SmsDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Kolejka priorytetowa dla wiadomości SMS z persistencją
 * Używa wzorca Adapter do integracji z bazą danych Room
 */
class PriorityQueue(
    private val smsDao: SmsDao
) {
    private val logger = LoggerFactory.getLogger(PriorityQueue::class.java)
    private val mutex = Mutex()
    
    // Cache dla pozycji w kolejce - optymalizacja wydajności
    private val positionCache = ConcurrentHashMap<Long, Int>()
    
    /**
     * Dodaje wiadomość do kolejki z priorytetem
     * @param message Wiadomość SMS do dodania
     * @param priority Priorytet wiadomości
     * @return Pozycja w kolejce
     */
    suspend fun add(message: SmsMessage, priority: SmsPriority = message.priority): Int {
        return mutex.withLock {
            try {
                // Pobierz maksymalną pozycję w kolejce dla danego priorytetu
                val maxPosition = smsDao.getMaxQueuePosition(priority.value) ?: 0
                
                // Oblicz nową pozycję - elementy o wyższym priorytecie mają niższe pozycje
                val newPosition = calculatePosition(priority, maxPosition)
                
                // Zaktualizuj pozycję wiadomości
                smsDao.updateQueuePosition(message.id, newPosition)
                
                // Zaktualizuj cache
                positionCache[message.id] = newPosition
                
                logger.info(
                    "Added SMS ID: ${message.id} to queue at position $newPosition " +
                    "with priority ${priority.name}"
                )
                
                newPosition
            } catch (e: Exception) {
                logger.error("Failed to add SMS ID: ${message.id} to priority queue", e)
                throw e
            }
        }
    }
    
    /**
     * Pobiera kolejną wiadomość z kolejki (o najwyższym priorytecie)
     * @return Wiadomość SMS lub null jeśli kolejka jest pusta
     */
    suspend fun poll(): SmsMessage? {
        return mutex.withLock {
            try {
                // Pobierz wiadomość o najwyższym priorytecie i najniższej pozycji
                val message = smsDao.getNextQueuedMessage()
                
                if (message != null) {
                    // Usuń z kolejki (ustaw status na SCHEDULED)
                    smsDao.updateSmsStatus(message.id, SmsStatus.SCHEDULED)
                    
                    // Usuń z cache
                    positionCache.remove(message.id)
                    
                    logger.info(
                        "Polled SMS ID: ${message.id} from queue " +
                        "(priority: ${message.priority.name}, position: ${message.queuePosition})"
                    )
                }
                
                message
            } catch (e: Exception) {
                logger.error("Failed to poll message from priority queue", e)
                null
            }
        }
    }
    
    /**
     * Podgląda następnej wiadomości w kolejce bez jej usuwania
     * @return Wiadomość SMS lub null jeśli kolejka jest pusta
     */
    suspend fun peek(): SmsMessage? {
        return try {
            smsDao.getNextQueuedMessage()
        } catch (e: Exception) {
            logger.error("Failed to peek message from priority queue", e)
            null
        }
    }
    
    /**
     * Zwraca rozmiar kolejki
     * @return Liczba wiadomości w kolejce
     */
    suspend fun size(): Int {
        return try {
            smsDao.getQueuedMessagesCount()
        } catch (e: Exception) {
            logger.error("Failed to get queue size", e)
            0
        }
    }
    
    /**
     * Sprawdza czy kolejka jest pusta
     * @return True jeśli kolejka jest pusta
     */
    suspend fun isEmpty(): Boolean {
        return size() == 0
    }
    
    /**
     * Pobiera wiadomości w kolejce dla danego priorytetu
     * @param priority Priorytet
     * @return Lista wiadomości o podanym priorytecie
     */
    suspend fun getMessagesByPriority(priority: SmsPriority): List<SmsMessage> {
        return try {
            smsDao.getQueuedMessagesByPriority(priority.value)
        } catch (e: Exception) {
            logger.error("Failed to get messages by priority: ${priority.name}", e)
            emptyList()
        }
    }
    
    /**
     * Usuwa wiadomość z kolejki
     * @param messageId ID wiadomości do usunięcia
     * @return True jeśli wiadomość została usunięta
     */
    suspend fun remove(messageId: Long): Boolean {
        return mutex.withLock {
            try {
                // Ustaw status na CANCELLED
                val updatedRows = smsDao.updateSmsStatus(messageId, SmsStatus.CANCELLED)
                
                if (updatedRows > 0) {
                    // Usuń pozycję z bazy
                    smsDao.updateQueuePosition(messageId, null)
                    
                    // Usuń z cache
                    positionCache.remove(messageId)
                    
                    logger.info("Removed SMS ID: $messageId from queue")
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                logger.error("Failed to remove SMS ID: $messageId from queue", e)
                false
            }
        }
    }
    
    /**
     * Przenosi wiadomość w kolejce
     * @param messageId ID wiadomości
     * @param newPriority Nowy priorytet
     * @return Nowa pozycja w kolejce
     */
    suspend fun reprioritize(messageId: Long, newPriority: SmsPriority): Int? {
        return mutex.withLock {
            try {
                val message = smsDao.getSmsById(messageId)
                if (message != null && message.status == SmsStatus.QUEUED) {
                    // Oblicz nową pozycję
                    val maxPosition = smsDao.getMaxQueuePosition(newPriority.value) ?: 0
                    val newPosition = calculatePosition(newPriority, maxPosition)
                    
                    // Zaktualizuj priorytet i pozycję
                    smsDao.updatePriorityAndPosition(messageId, newPriority, newPosition)
                    
                    // Zaktualizuj cache
                    positionCache[messageId] = newPosition
                    
                    logger.info(
                        "Reprioritized SMS ID: $messageId from ${message.priority.name} " +
                        "to ${newPriority.name}, new position: $newPosition"
                    )
                    
                    newPosition
                } else {
                    null
                }
            } catch (e: Exception) {
                logger.error("Failed to reprioritize SMS ID: $messageId", e)
                null
            }
        }
    }
    
    /**
     * Czyści kolejkę
     * @param priority Opcjonalny priorytet do wyczyszczenia (null dla wszystkich)
     * @return Liczba usuniętych wiadomości
     */
    suspend fun clear(priority: SmsPriority? = null): Int {
        return mutex.withLock {
            try {
                val clearedCount = if (priority != null) {
                    smsDao.clearQueueByPriority(priority.value)
                } else {
                    smsDao.clearEntireQueue()
                }
                
                // Wyczyść cache
                positionCache.clear()
                
                logger.info("Cleared queue: $clearedCount messages removed")
                clearedCount
            } catch (e: Exception) {
                logger.error("Failed to clear queue", e)
                0
            }
        }
    }
    
    /**
     * Reorganizuje kolejkę - naprawia pozycje po usunięciach
     * @return Liczba zaktualizowanych wiadomości
     */
    suspend fun reorganize(): Int {
        return mutex.withLock {
            try {
                val updatedCount = smsDao.reorganizeQueuePositions()
                
                // Wyczyść cache
                positionCache.clear()
                
                logger.info("Reorganized queue: $updatedCount positions updated")
                updatedCount
            } catch (e: Exception) {
                logger.error("Failed to reorganize queue", e)
                0
            }
        }
    }
    
    /**
     * Oblicza pozycję w kolejce na podstawie priorytetu
     * Elementy o wyższym priorytecie mają niższe numery pozycji
     */
    private fun calculatePosition(priority: SmsPriority, maxPosition: Int): Int {
        // Przesunięcie dla priorytetów: URGENT(4) * 10000, HIGH(3) * 10000, etc.
        val priorityOffset = (5 - priority.value) * 10000
        return priorityOffset + maxPosition + 1
    }
    
    /**
     * Pobiera statystyki kolejki
     */
    suspend fun getQueueStats(): QueueStats {
        return try {
            val totalCount = smsDao.getQueuedMessagesCount()
            val priorityStats = mutableMapOf<SmsPriority, Int>()
            
            for (priority in SmsPriority.values()) {
                priorityStats[priority] = smsDao.getQueuedMessagesCountByPriority(priority.value)
            }
            
            QueueStats(
                totalCount = totalCount,
                priorityStats = priorityStats,
                oldestMessageTime = smsDao.getOldestQueuedMessageTime(),
                newestMessageTime = smsDao.getNewestQueuedMessageTime()
            )
        } catch (e: Exception) {
            logger.error("Failed to get queue stats", e)
            QueueStats(0, emptyMap(), null, null)
        }
    }
}

/**
 * Klasa danych reprezentująca statystyki kolejki
 */
data class QueueStats(
    val totalCount: Int,
    val priorityStats: Map<SmsPriority, Int>,
    val oldestMessageTime: Long?,
    val newestMessageTime: Long?
)