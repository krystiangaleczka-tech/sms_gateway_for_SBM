package com.smsgateway.app.events

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interfejs dla subskrybentów zdarzeń
 */
interface EventSubscriber<T : SmsEvent> {
    /**
     * Metoda wywoływana gdy zdarzenie jest publikowane
     */
    suspend fun onEvent(event: T)
    
    /**
     * Typ zdarzeń, które subskrybent obsługuje
     */
    fun getEventType(): Class<T>
}

/**
 * EventPublisher - centralny komponent do publikowania zdarzeń w systemie
 * Implementuje wzorzec Observer do komunikacji między komponentami
 * Obsługuje subskrypcję, filtrowanie i asynchroniczną publikację
 */
@Singleton
class EventPublisher @Inject constructor() {
    
    private val logger = LoggerFactory.getLogger(EventPublisher::class.java)
    
    // Mapa subskrybentów zgrupowanych po typie zdarzenia
    private val subscribers = ConcurrentHashMap<Class<out SmsEvent>, CopyOnWriteArrayList<EventSubscriber<*>>>()
    
    // Statystyki publikacji
    private val publishedEventsCount = ConcurrentHashMap<String, Long>()
    private val failedEventsCount = ConcurrentHashMap<String, Long>()
    
    // Scope dla asynchronicznej publikacji
    private val publishScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Rejestruje subskrybenta dla określonego typu zdarzeń
     */
    fun <T : SmsEvent> subscribe(subscriber: EventSubscriber<T>) {
        val eventType = subscriber.getEventType()
        val subscriberList = subscribers.getOrPut(eventType) { CopyOnWriteArrayList() }
        
        @Suppress("UNCHECKED_CAST")
        subscriberList.add(subscriber as EventSubscriber<*>)
        
        logger.debug("Registered subscriber for event type: ${eventType.simpleName}")
    }
    
    /**
     * Wyrejestrowuje subskrybenta
     */
    fun <T : SmsEvent> unsubscribe(subscriber: EventSubscriber<T>) {
        val eventType = subscriber.getEventType()
        subscribers[eventType]?.remove(subscriber)
        
        logger.debug("Unregistered subscriber for event type: ${eventType.simpleName}")
    }
    
    /**
     * Publikuje zdarzenie synchronicznie
     */
    suspend fun publish(event: SmsEvent) {
        publishEvent(event, async = false)
    }
    
    /**
     * Publikuje zdarzenie asynchronicznie
     */
    fun publishAsync(event: SmsEvent) {
        publishScope.launch {
            publishEvent(event, async = true)
        }
    }
    
    /**
     * Publikuje zdarzenie z określonym trybem (sync/async)
     */
    private suspend fun publishEvent(event: SmsEvent, async: Boolean) {
        val eventType = event::class.java
        val eventTypeName = eventType.simpleName ?: "Unknown"
        
        try {
            // Aktualizacja statystyk
            publishedEventsCount.merge(eventTypeName, 1L, Long::plus)
            
            // Pobranie subskrybentów dla tego typu zdarzenia
            val eventSubscribers = subscribers[eventType] ?: emptyList()
            
            if (eventSubscribers.isEmpty()) {
                logger.debug("No subscribers for event type: $eventTypeName")
                return
            }
            
            // Logowanie zdarzenia
            logger.debug("Publishing event: ${event.toLogString()}")
            
            // Publikacja do wszystkich subskrybentów
            val exceptions = mutableListOf<Exception>()
            
            for (subscriber in eventSubscribers) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    (subscriber as EventSubscriber<SmsEvent>).onEvent(event)
                } catch (e: Exception) {
                    logger.error("Error in subscriber ${subscriber::class.simpleName} for event $eventTypeName", e)
                    exceptions.add(e)
                }
            }
            
            // Aktualizacja statystyk błędów
            if (exceptions.isNotEmpty()) {
                failedEventsCount.merge(eventTypeName, exceptions.size.toLong(), Long::plus)
                
                if (!async) {
                    // Rzucenie wyjątku tylko w trybie synchronicznym
                    throw EventPublishingException(
                        "Failed to publish event $eventTypeName to ${exceptions.size} subscribers",
                        exceptions
                    )
                }
            }
            
        } catch (e: Exception) {
            logger.error("Error publishing event: $eventTypeName", e)
            failedEventsCount.merge(eventTypeName, 1L, Long::plus)
            
            if (!async) {
                throw e
            }
        }
    }
    
    /**
     * Publikuje wiele zdarzeń asynchronicznie
     */
    fun publishBatch(events: List<SmsEvent>) {
        publishScope.launch {
            for (event in events) {
                publishEvent(event, async = true)
            }
        }
    }
    
    /**
     * Pobiera statystyki publikacji
     */
    fun getPublishingStats(): EventPublishingStats {
        return EventPublishingStats(
            totalPublished = publishedEventsCount.values.sum(),
            totalFailed = failedEventsCount.values.sum(),
            publishedByType = publishedEventsCount.toMap(),
            failedByType = failedEventsCount.toMap(),
            activeSubscribers = subscribers.mapValues { it.value.size }
        )
    }
    
    /**
     * Resetuje statystyki
     */
    fun resetStats() {
        publishedEventsCount.clear()
        failedEventsCount.clear()
        logger.info("Event publishing stats reset")
    }
    
    /**
     * Zamyka EventPublisher i czyszczy zasoby
     */
    fun shutdown() {
        publishScope.cancel()
        subscribers.clear()
        logger.info("EventPublisher shutdown")
    }
    
    /**
     * Pobiera liczbę subskrybentów dla danego typu zdarzenia
     */
    fun getSubscriberCount(eventType: Class<out SmsEvent>): Int {
        return subscribers[eventType]?.size ?: 0
    }
    
    /**
     * Pobiera wszystkie typy zdarzeń z subskrybentami
     */
    fun getRegisteredEventTypes(): Set<Class<out SmsEvent>> {
        return subscribers.keys.toSet()
    }
}

/**
 * Wyjątek rzucany gdy publikacja zdarzenia nie powiodła się
 */
class EventPublishingException(
    message: String,
    val subscriberExceptions: List<Exception>
) : RuntimeException(message, subscriberExceptions.firstOrNull())

/**
 * Klasa danych dla statystyk publikacji zdarzeń
 */
data class EventPublishingStats(
    val totalPublished: Long,
    val totalFailed: Long,
    val publishedByType: Map<String, Long>,
    val failedByType: Map<String, Long>,
    val activeSubscribers: Map<String, Int>
) {
    /**
     * Oblicza całkowity wskaźnik sukcesu
     */
    fun getSuccessRate(): Double {
        val total = totalPublished + totalFailed
        return if (total > 0) {
            (totalPublished.toDouble() / total) * 100
        } else {
            100.0
        }
    }
    
    /**
     * Oblicza wskaźnik sukcesu dla danego typu
     */
    fun getSuccessRateForType(eventType: String): Double {
        val published = publishedByType[eventType] ?: 0L
        val failed = failedByType[eventType] ?: 0L
        val total = published + failed
        
        return if (total > 0) {
            (published.toDouble() / total) * 100
        } else {
            100.0
        }
    }
}

/**
 * Abstrakcyjna klasa bazowa dla subskrybentów zdarzeń
 * Upraszcza implementację interfejsu EventSubscriber
 */
abstract class BaseEventSubscriber<T : SmsEvent>(
    private val eventType: Class<T>
) : EventSubscriber<T> {
    
    override fun getEventType(): Class<T> = eventType
}

/**
 * Pomocniczy obiekt do tworzenia subskrybentów z lambdami
 */
object EventSubscribers {
    
    /**
     * Tworzy subskrybenta dla SmsQueuedEvent
     */
    fun forSmsQueued(handler: suspend (SmsQueuedEvent) -> Unit): EventSubscriber<SmsQueuedEvent> {
        return object : BaseEventSubscriber<SmsQueuedEvent>(SmsQueuedEvent::class.java) {
            override suspend fun onEvent(event: SmsQueuedEvent) {
                handler(event)
            }
        }
    }
    
    /**
     * Tworzy subskrybenta dla SmsSentEvent
     */
    fun forSmsSent(handler: suspend (SmsSentEvent) -> Unit): EventSubscriber<SmsSentEvent> {
        return object : BaseEventSubscriber<SmsSentEvent>(SmsSentEvent::class.java) {
            override suspend fun onEvent(event: SmsSentEvent) {
                handler(event)
            }
        }
    }
    
    /**
     * Tworzy subskrybenta dla SmsFailedEvent
     */
    fun forSmsFailed(handler: suspend (SmsFailedEvent) -> Unit): EventSubscriber<SmsFailedEvent> {
        return object : BaseEventSubscriber<SmsFailedEvent>(SmsFailedEvent::class.java) {
            override suspend fun onEvent(event: SmsFailedEvent) {
                handler(event)
            }
        }
    }
    
    /**
     * Tworzy subskrybenta dla SmsRetryEvent
     */
    fun forSmsRetry(handler: suspend (SmsRetryEvent) -> Unit): EventSubscriber<SmsRetryEvent> {
        return object : BaseEventSubscriber<SmsRetryEvent>(SmsRetryEvent::class.java) {
            override suspend fun onEvent(event: SmsRetryEvent) {
                handler(event)
            }
        }
    }
    
    /**
     * Tworzy subskrybenta dla SystemHealthEvent
     */
    fun forSystemHealth(handler: suspend (SystemHealthEvent) -> Unit): EventSubscriber<SystemHealthEvent> {
        return object : BaseEventSubscriber<SystemHealthEvent>(SystemHealthEvent::class.java) {
            override suspend fun onEvent(event: SystemHealthEvent) {
                handler(event)
            }
        }
    }
    
    /**
     * Tworzy subskrybenta dla PerformanceMetricEvent
     */
    fun forPerformanceMetric(handler: suspend (PerformanceMetricEvent) -> Unit): EventSubscriber<PerformanceMetricEvent> {
        return object : BaseEventSubscriber<PerformanceMetricEvent>(PerformanceMetricEvent::class.java) {
            override suspend fun onEvent(event: PerformanceMetricEvent) {
                handler(event)
            }
        }
    }
    
    /**
     * Tworzy subskrybenta dla AlertEvent
     */
    fun forAlert(handler: suspend (AlertEvent) -> Unit): EventSubscriber<AlertEvent> {
        return object : BaseEventSubscriber<AlertEvent>(AlertEvent::class.java) {
            override suspend fun onEvent(event: AlertEvent) {
                handler(event)
            }
        }
    }
}