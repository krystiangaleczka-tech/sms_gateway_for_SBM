package com.smsgateway.app.queue

import android.util.Log
import com.smsgateway.app.database.SmsDao
import com.smsgateway.app.database.SmsMessage
import com.smsgateway.app.database.SmsPriority
import com.smsgateway.app.database.SmsStatus
import com.smsgateway.app.events.EventPublisher
import com.smsgateway.app.workers.WorkManagerService
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

/**
 * Główny serwis zarządzający kolejką SMS z priorytetami.
 * Odpowiada za dodawanie wiadomości do kolejki, pobieranie następnej wiadomości
 * oraz zarządzanie stanem kolejki.
 */
class SmsQueueService(
    private val smsDao: SmsDao,
    private val priorityQueue: PriorityQueue,
    private val workManagerService: WorkManagerService,
    private val eventPublisher: EventPublisher? = null
) : CoroutineScope {

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = Dispatchers.IO + job

    companion object {
        private const val TAG = "SmsQueueService"
        
        // Maksymalna liczba wiadomości pobieranych naraz z bazy
        private const val BATCH_SIZE = 50
        
        // Próg aktywacji przetwarzania - liczba wiadomości w kolejce
        private const val PROCESSING_THRESHOLD = 5
    }

    // Stan kolejki
    private val isQueueActive = AtomicBoolean(true)
    private val isProcessing = AtomicBoolean(false)

    /**
     * Dodaje wiadomość SMS do kolejki z określonym priorytetem
     */
    suspend fun enqueueSms(
        phoneNumber: String,
        messageContent: String,
        priority: SmsPriority = SmsPriority.NORMAL,
        scheduledAt: Long? = null,
        retryStrategy: com.smsgateway.app.database.RetryStrategy = com.smsgateway.app.database.RetryStrategy.EXPONENTIAL_BACKOFF,
        metadata: Map<String, Any> = emptyMap()
    ): SmsMessage {
        return withContext(Dispatchers.IO) {
            try {
                // Tworzenie nowej wiadomości
                val smsMessage = SmsMessage(
                    phoneNumber = phoneNumber,
                    messageContent = messageContent,
                    status = if (scheduledAt != null && scheduledAt > System.currentTimeMillis()) {
                        SmsStatus.SCHEDULED
                    } else {
                        SmsStatus.QUEUED
                    },
                    priority = priority,
                    createdAt = System.currentTimeMillis(),
                    scheduledAt = scheduledAt,
                    retryStrategy = retryStrategy,
                    metadata = metadata
                )

                // Zapis do bazy danych
                val insertedId = smsDao.insert(smsMessage)
                val insertedMessage = smsMessage.copy(id = insertedId)

                // Dodanie do kolejki priorytetowej (jeśli nie jest zaplanowana)
                if (insertedMessage.status == SmsStatus.QUEUED) {
                    val position = priorityQueue.add(insertedMessage)
                    insertedMessage.copy(queuePosition = position)
                } else {
                    insertedMessage
                }

                Log.i(TAG, "SMS dodany do kolejki: ID=${insertedId}, priorytet=$priority, status=${insertedMessage.status}")

                // Publikacja zdarzenia
                eventPublisher?.publishSmsQueued(insertedMessage)

                // Sprawdzenie czy uruchomić przetwarzanie
                checkAndStartProcessing()

                insertedMessage
            } catch (e: Exception) {
                Log.e(TAG, "Błąd podczas dodawania SMS do kolejki", e)
                throw e
            }
        }
    }

    /**
     * Pobiera następną wiadomość z kolejki (o najwyższym priorytecie)
     */
    suspend fun dequeueNextSms(): SmsMessage? {
        return withContext(Dispatchers.IO) {
            try {
                if (!isQueueActive.get()) {
                    Log.d(TAG, "Kolejka jest zatrzymana - nie pobierano wiadomości")
                    return@withContext null
                }

                // Pobranie z bazy danych (zgodnie z priorytetami)
                val nextMessage = smsDao.getNextQueuedMessage()
                
                if (nextMessage != null) {
                    // Aktualizacja statusu na "wysyłanie"
                    smsDao.updateStatus(nextMessage.id, SmsStatus.SENDING)
                    
                    // Usunięcie z kolejki priorytetowej
                    priorityQueue.remove(nextMessage.id)
                    
                    Log.i(TAG, "Pobrano SMS z kolejki: ID=${nextMessage.id}, priorytet=${nextMessage.priority}")
                    
                    // Publikacja zdarzenia
                    eventPublisher?.publishSmsDequeued(nextMessage)
                }

                nextMessage
            } catch (e: Exception) {
                Log.e(TAG, "Błąd podczas pobierania następnej wiadomości z kolejki", e)
                null
            }
        }
    }

    /**
     * Zwraca statystyki kolejki
     */
    suspend fun getQueueStats(): QueueStats {
        return withContext(Dispatchers.IO) {
            try {
                val totalMessages = smsDao.getMessageCount()
                val queuedMessages = smsDao.getMessagesByStatus(SmsStatus.QUEUED).size
                val scheduledMessages = smsDao.getMessagesByStatus(SmsStatus.SCHEDULED).size
                val sendingMessages = smsDao.getMessagesByStatus(SmsStatus.SENDING).size
                val sentMessages = smsDao.getMessagesByStatus(SmsStatus.SENT).size
                val failedMessages = smsDao.getMessagesByStatus(SmsStatus.FAILED).size

                // Metryki wydajności
                val oldestQueuedTime = smsDao.getOldestQueuedMessageTime()
                val newestQueuedTime = smsDao.getNewestQueuedMessageTime()
                
                val averageWaitTime = if (oldestQueuedTime != null) {
                    System.currentTimeMillis() - oldestQueuedTime
                } else {
                    0L
                }

                // Przepustowość na godzinę (uproszczona)
                val throughputPerHour = calculateThroughputPerHour()

                // Wskaźnik błędów
                val errorRate = if (totalMessages > 0) {
                    failedMessages.toDouble() / totalMessages.toDouble()
                } else {
                    0.0
                }

                QueueStats(
                    totalMessages = totalMessages,
                    queuedMessages = queuedMessages,
                    scheduledMessages = scheduledMessages,
                    sendingMessages = sendingMessages,
                    sentMessages = sentMessages,
                    failedMessages = failedMessages,
                    averageWaitTime = averageWaitTime,
                    throughputPerHour = throughputPerHour,
                    errorRate = errorRate,
                    isQueueActive = isQueueActive.get(),
                    isProcessing = isProcessing.get(),
                    oldestQueuedTime = oldestQueuedTime,
                    newestQueuedTime = newestQueuedTime
                )
            } catch (e: Exception) {
                Log.e(TAG, "Błąd podczas pobierania statystyk kolejki", e)
                QueueStats() // Puste statystyki w przypadku błędu
            }
        }
    }

    /**
     * Zatrzymuje przetwarzanie kolejki (nie dodaje nowych wiadomości)
     */
    suspend fun pauseQueue() {
        withContext(Dispatchers.IO) {
            isQueueActive.set(false)
            Log.i(TAG, "Kolejka SMS została zatrzymana")
            
            eventPublisher?.publishQueuePaused()
            
            // Zatrzymanie workerów
            workManagerService.cancelAllSmsWork()
        }
    }

    /**
     * Wznawia przetwarzanie kolejki
     */
    suspend fun resumeQueue() {
        withContext(Dispatchers.IO) {
            isQueueActive.set(true)
            Log.i(TAG, "Kolejka SMS została wznowiona")
            
            eventPublisher?.publishQueueResumed()
            
            // Sprawdzenie czy uruchomić przetwarzanie
            checkAndStartProcessing()
        }
    }

    /**
     * Czyści kolejkę z wiadomości o określonym statusie
     */
    suspend fun clearQueue(status: SmsStatus? = null): Int {
        return withContext(Dispatchers.IO) {
            try {
                val clearedCount = if (status != null) {
                    when (status) {
                        SmsStatus.QUEUED -> priorityQueue.clear()
                        else -> smsDao.deleteByStatus(status)
                    }
                } else {
                    priorityQueue.clear()
                    smsDao.deleteByStatus(SmsStatus.QUEUED)
                }

                Log.i(TAG, "Wyczyszczono kolejkę: $clearedCount wiadomości")
                
                eventPublisher?.publishQueueCleared(clearedCount)
                
                clearedCount
            } catch (e: Exception) {
                Log.e(TAG, "Błąd podczas czyszczenia kolejki", e)
                0
            }
        }
    }

    /**
     * Zmienia priorytet wiadomości w kolejce
     */
    suspend fun reprioritizeSms(smsId: Long, newPriority: SmsPriority): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val message = smsDao.getMessageById(smsId)
                if (message != null && message.status == SmsStatus.QUEUED) {
                    // Aktualizacja w bazie danych
                    smsDao.updatePriorityAndPosition(smsId, newPriority, 0)
                    
                    // Usunięcie z obecnej pozycji i dodanie z nowym priorytetem
                    priorityQueue.remove(smsId)
                    val updatedMessage = message.copy(priority = newPriority)
                    val newPosition = priorityQueue.add(updatedMessage)
                    
                    Log.i(TAG, "Zmieniono priorytet SMS ID=$smsId na $newPriority, nowa pozycja=$newPosition")
                    
                    eventPublisher?.publishSmsReprioritized(smsId, newPriority)
                    
                    true
                } else {
                    Log.w(TAG, "Nie znaleziono wiadomości ID=$smsId lub nie jest w kolejce")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Błąd podczas zmiany priorytetu SMS ID=$smsId", e)
                false
            }
        }
    }

    /**
     * Przetwarza zaplanowane wiadomości (przenosi je do kolejki)
     */
    suspend fun processScheduledMessages(): Int {
        return withContext(Dispatchers.IO) {
            try {
                val currentTime = System.currentTimeMillis()
                val scheduledMessages = smsDao.getScheduledMessagesBefore(currentTime)
                
                var processedCount = 0
                
                for (message in scheduledMessages) {
                    // Aktualizacja statusu na QUEUED
                    smsDao.updateStatus(message.id, SmsStatus.QUEUED)
                    
                    // Dodanie do kolejki priorytetowej
                    val position = priorityQueue.add(message)
                    
                    Log.d(TAG, "Przetworzono zaplanowaną wiadomość ID=${message.id}, pozycja=$position")
                    
                    eventPublisher?.publishSmsScheduledProcessed(message)
                    
                    processedCount++
                }
                
                if (processedCount > 0) {
                    Log.i(TAG, "Przetworzono $processedCount zaplanowanych wiadomości")
                    checkAndStartProcessing()
                }
                
                processedCount
            } catch (e: Exception) {
                Log.e(TAG, "Błąd podczas przetwarzania zaplanowanych wiadomości", e)
                0
            }
        }
    }

    /**
     * Reorganizuje pozycje w kolejce - naprawia luki po usunięciach
     */
    suspend fun reorganizeQueue(): Int {
        return withContext(Dispatchers.IO) {
            try {
                val updatedCount = smsDao.reorganizeQueuePositions()
                
                if (updatedCount > 0) {
                    Log.i(TAG, "Zreorganizowano pozycje w kolejce: $updatedCount wiadomości")
                    
                    // Przeładowanie kolejki priorytetowej
                    priorityQueue.reload()
                    
                    eventPublisher?.publishQueueReorganized(updatedCount)
                }
                
                updatedCount
            } catch (e: Exception) {
                Log.e(TAG, "Błąd podczas reorganizacji kolejki", e)
                0
            }
        }
    }

    /**
     * Sprawdza czy uruchomić przetwarzanie kolejki
     */
    private fun checkAndStartProcessing() {
        if (isQueueActive.get() && !isProcessing.get()) {
            launch {
                val queueSize = priorityQueue.size()
                if (queueSize >= PROCESSING_THRESHOLD) {
                    startProcessing()
                }
            }
        }
    }

    /**
     * Uruchamia przetwarzanie kolejki
     */
    private suspend fun startProcessing() {
        if (isProcessing.compareAndSet(false, true)) {
            try {
                Log.i(TAG, "Uruchamianie przetwarzania kolejki SMS")
                
                // Uruchomienie workerów WorkManager
                workManagerService.startSmsProcessing()
                
                eventPublisher?.publishQueueProcessingStarted()
            } catch (e: Exception) {
                Log.e(TAG, "Błąd podczas uruchamiania przetwarzania kolejki", e)
                isProcessing.set(false)
            }
        }
    }

    /**
     * Zatrzymuje przetwarzanie kolejki
     */
    private fun stopProcessing() {
        if (isProcessing.compareAndSet(true, false)) {
            Log.i(TAG, "Zatrzymywanie przetwarzania kolejki SMS")
            
            eventPublisher?.publishQueueProcessingStopped()
        }
    }

    /**
     * Oblicza przepustowość na godzinę (uproszczona metoda)
     */
    private suspend fun calculateThroughputPerHour(): Int {
        return withContext(Dispatchers.IO) {
            try {
                val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
                val sentMessages = smsDao.getMessagesByStatusAndTimeRange(SmsStatus.SENT, oneHourAgo, System.currentTimeMillis())
                sentMessages.size
            } catch (e: Exception) {
                Log.e(TAG, "Błąd podczas obliczania przepustowości", e)
                0
            }
        }
    }

    /**
     * Zwalnia zasoby
     */
    fun cleanup() {
        job.cancel()
        stopProcessing()
        Log.i(TAG, "Zwolniono zasoby SmsQueueService")
    }
}

/**
 * Klasa danych dla statystyk kolejki
 */
data class QueueStats(
    val totalMessages: Int = 0,
    val queuedMessages: Int = 0,
    val scheduledMessages: Int = 0,
    val sendingMessages: Int = 0,
    val sentMessages: Int = 0,
    val failedMessages: Int = 0,
    val averageWaitTime: Long = 0L,
    val throughputPerHour: Int = 0,
    val errorRate: Double = 0.0,
    val isQueueActive: Boolean = true,
    val isProcessing: Boolean = false,
    val oldestQueuedTime: Long? = null,
    val newestQueuedTime: Long? = null
)