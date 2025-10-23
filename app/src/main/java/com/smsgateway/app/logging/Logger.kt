package com.smsgateway.app.logging

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Główny komponent systemu logowania
 * Obsługuje różne poziomy logowania, filtrowanie i agregację
 */
object Logger {
    
    // Poziomy logowania
    enum class LogLevel(val value: Int, val displayName: String) {
        TRACE(0, "TRACE"),
        DEBUG(1, "DEBUG"),
        INFO(2, "INFO"),
        WARN(3, "WARN"),
        ERROR(4, "ERROR"),
        FATAL(5, "FATAL")
    }
    
    // Kategorie logowania
    enum class Category(val value: String) {
        QUEUE("QUEUE"),
        RETRY("RETRY"),
        HEALTH("HEALTH"),
        METRICS("METRICS"),
        WORKER("WORKER"),
        API("API"),
        DATABASE("DATABASE"),
        SMS("SMS"),
        SYSTEM("SYSTEM"),
        SECURITY("SECURITY")
    }
    
    // Struktura wpisu logu
    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val level: LogLevel,
        val category: Category,
        val message: String,
        val throwable: Throwable? = null,
        val metadata: Map<String, Any> = emptyMap(),
        val threadName: String = Thread.currentThread().name,
        val component: String? = null,
        val messageId: Long? = null
    ) {
        fun toFormattedString(): String {
            val formatter = DateTimeFormatter.ISO_INSTANT
            val timeStr = Instant.ofEpochMilli(timestamp).format(formatter)
            val levelStr = level.displayName.padEnd(5)
            val categoryStr = "[${category.value}]".padEnd(10)
            val threadStr = "[$threadName]".padEnd(20)
            val componentStr = component?.let { "[$it]" }?.padEnd(15) ?: ""
            
            val baseLog = "$timeStr $levelStr $categoryStr $threadStr $componentStr $message"
            
            // Dodanie metadanych
            val metadataStr = if (metadata.isNotEmpty()) {
                " | ${metadata.map { "${it.key}=${it.value}" }.joinToString(", ")}"
            } else ""
            
            // Dodanie wyjątku
            val exceptionStr = throwable?.let { 
                "\n${it.stackTraceToString()}" 
            } ?: ""
            
            return baseLog + metadataStr + exceptionStr
        }
        
        fun toJsonString(): String {
            val formatter = DateTimeFormatter.ISO_INSTANT
            val timeStr = Instant.ofEpochMilli(timestamp).format(formatter)
            
            return buildString {
                append("{")
                append("\"timestamp\":\"$timeStr\",")
                append("\"level\":\"${level.displayName}\",")
                append("\"category\":\"${category.value}\",")
                append("\"message\":\"$message\",")
                append("\"thread\":\"$threadName\"")
                
                component?.let { append(",\"component\":\"$it\"") }
                messageId?.let { append(",\"messageId\":$it") }
                
                if (metadata.isNotEmpty()) {
                    append(",\"metadata\":{")
                    append(metadata.map { "\"${it.key}\":\"${it.value}\"" }.joinToString(","))
                    append("}")
                }
                
                throwable?.let {
                    append(",\"exception\":{")
                    append("\"type\":\"${it.javaClass.simpleName}\",")
                    append("\"message\":\"${it.message?.replace("\"", "\\\"") ?: ""}\"")
                    append("}")
                }
                
                append("}")
            }
        }
    }
    
    // Konfiguracja logowania
    data class LoggingConfig(
        val minLevel: LogLevel = LogLevel.DEBUG,
        val enableConsole: Boolean = true,
        val enableFile: Boolean = true,
        val enableEvents: Boolean = true,
        val maxLogEntries: Int = 10000,
        val enableMetrics: Boolean = true,
        val categories: Set<Category> = Category.values().toSet(),
        val componentFilters: Set<String> = emptySet(),
        val enableStructuredLogging: Boolean = true
    )
    
    // Statystyki logowania
    data class LoggingStats(
        val totalLogs: AtomicLong = AtomicLong(0),
        val logsByLevel: MutableMap<LogLevel, AtomicLong> = ConcurrentHashMap(),
        val logsByCategory: MutableMap<Category, AtomicLong> = ConcurrentHashMap(),
        val logsByComponent: MutableMap<String, AtomicLong> = ConcurrentHashMap(),
        val errorRate: AtomicLong = AtomicLong(0),
        val lastLogTime: AtomicLong = AtomicLong(0)
    ) {
        fun getTotalLogs(): Long = totalLogs.get()
        
        fun getLogsByLevel(level: LogLevel): Long = 
            logsByLevel.getOrPut(level) { AtomicLong(0) }.get()
        
        fun getLogsByCategory(category: Category): Long = 
            logsByCategory.getOrPut(category) { AtomicLong(0) }.get()
        
        fun getLogsByComponent(component: String): Long = 
            logsByComponent.getOrPut(component) { AtomicLong(0) }.get()
        
        fun getErrorRate(): Double {
            val total = totalLogs.get()
            val errors = getLogsByLevel(LogLevel.ERROR) + getLogsByLevel(LogLevel.FATAL)
            return if (total > 0) (errors.toDouble() / total) * 100 else 0.0
        }
        
        fun updateStats(entry: LogEntry) {
            totalLogs.incrementAndGet()
            logsByLevel.getOrPut(entry.level) { AtomicLong(0) }.incrementAndGet()
            logsByCategory.getOrPut(entry.category) { AtomicLong(0) }.incrementAndGet()
            entry.component?.let { 
                logsByComponent.getOrPut(it) { AtomicLong(0) }.incrementAndGet() 
            }
            lastLogTime.set(System.currentTimeMillis())
            
            if (entry.level in listOf(LogLevel.ERROR, LogLevel.FATAL)) {
                errorRate.incrementAndGet()
            }
        }
    }
    
    // Prywatne pola
    private val slf4jLoggers = ConcurrentHashMap<String, org.slf4j.Logger>()
    private val config = AtomicReference(LoggingConfig())
    private val stats = LoggingStats()
    private val logBuffer = mutableListOf<LogEntry>()
    private val logBufferMutex = Any()
    private val maxBufferSize = 1000
    
    // Flow dla zdarzeń logowania
    private val _logEvents = MutableSharedFlow<LogEntry>(extraBufferCapacity = 100)
    val logEvents: SharedFlow<LogEntry> = _logEvents.asSharedFlow()
    
    // Inicjalizacja
    init {
        LogLevel.values().forEach { level ->
            stats.logsByLevel[level] = AtomicLong(0)
        }
        Category.values().forEach { category ->
            stats.logsByCategory[category] = AtomicLong(0)
        }
    }
    
    /**
     * Konfiguruje system logowania
     */
    fun configure(newConfig: LoggingConfig) {
        config.set(newConfig)
        info(Category.SYSTEM, "Logger configuration updated", mapOf(
            "minLevel" to newConfig.minLevel.displayName,
            "enableConsole" to newConfig.enableConsole,
            "enableFile" to newConfig.enableFile,
            "enableEvents" to newConfig.enableEvents
        ))
    }
    
    /**
     * Pobiera aktualną konfigurację
     */
    fun getConfig(): LoggingConfig = config.get()
    
    /**
     * Pobiera statystyki logowania
     */
    fun getStats(): LoggingStats = stats
    
    /**
     * Czyści bufor logów
     */
    fun clearLogBuffer() {
        synchronized(logBufferMutex) {
            logBuffer.clear()
        }
        info(Category.SYSTEM, "Log buffer cleared")
    }
    
    /**
     * Pobiera ostatnie wpisy logów
     */
    fun getRecentLogs(count: Int = 100): List<LogEntry> {
        synchronized(logBufferMutex) {
            return logBuffer.takeLast(count).toList()
        }
    }
    
    /**
     * Główna metoda logowania
     */
    private fun log(
        level: LogLevel,
        category: Category,
        message: String,
        throwable: Throwable? = null,
        metadata: Map<String, Any> = emptyMap(),
        component: String? = null,
        messageId: Long? = null
    ) {
        val currentConfig = config.get()
        
        // Sprawdzenie poziomu logowania
        if (level.value < currentConfig.minLevel.value) {
            return
        }
        
        // Sprawdzenie kategorii
        if (category !in currentConfig.categories) {
            return
        }
        
        // Sprawdzenie filtrów komponentów
        if (currentConfig.componentFilters.isNotEmpty() && 
            (component == null || component !in currentConfig.componentFilters)) {
            return
        }
        
        val entry = LogEntry(
            level = level,
            category = category,
            message = message,
            throwable = throwable,
            metadata = metadata,
            component = component,
            messageId = messageId
        )
        
        // Aktualizacja statystyk
        if (currentConfig.enableMetrics) {
            stats.updateStats(entry)
        }
        
        // Dodanie do bufora
        synchronized(logBufferMutex) {
            logBuffer.add(entry)
            if (logBuffer.size > maxBufferSize) {
                logBuffer.removeAt(0)
            }
        }
        
        // Logowanie do konsoli
        if (currentConfig.enableConsole) {
            logToConsole(entry, currentConfig)
        }
        
        // Publikacja zdarzenia
        if (currentConfig.enableEvents) {
            try {
                _logEvents.tryEmit(entry)
            } catch (e: Exception) {
                // Nie blokujemy logowania w przypadku błędu publikacji
            }
        }
    }
    
    /**
     * Logowanie do konsoli
     */
    private fun logToConsole(entry: LogEntry, config: LoggingConfig) {
        val logger = slf4jLoggers.getOrPut(entry.category.value) {
            LoggerFactory.getLogger("SMSGateway.${entry.category.value}")
        }
        
        val message = if (config.enableStructuredLogging) {
            entry.toJsonString()
        } else {
            entry.toFormattedString()
        }
        
        when (entry.level) {
            LogLevel.TRACE -> logger.trace(message)
            LogLevel.DEBUG -> logger.debug(message)
            LogLevel.INFO -> logger.info(message)
            LogLevel.WARN -> logger.warn(message)
            LogLevel.ERROR -> logger.error(message)
            LogLevel.FATAL -> logger.error(message)
        }
    }
    
    // Metody wygody dla różnych poziomów logowania
    
    fun trace(category: Category, message: String, metadata: Map<String, Any> = emptyMap(), component: String? = null, messageId: Long? = null) {
        log(LogLevel.TRACE, category, message, null, metadata, component, messageId)
    }
    
    fun debug(category: Category, message: String, metadata: Map<String, Any> = emptyMap(), component: String? = null, messageId: Long? = null) {
        log(LogLevel.DEBUG, category, message, null, metadata, component, messageId)
    }
    
    fun info(category: Category, message: String, metadata: Map<String, Any> = emptyMap(), component: String? = null, messageId: Long? = null) {
        log(LogLevel.INFO, category, message, null, metadata, component, messageId)
    }
    
    fun warn(category: Category, message: String, throwable: Throwable? = null, metadata: Map<String, Any> = emptyMap(), component: String? = null, messageId: Long? = null) {
        log(LogLevel.WARN, category, message, throwable, metadata, component, messageId)
    }
    
    fun error(category: Category, message: String, throwable: Throwable? = null, metadata: Map<String, Any> = emptyMap(), component: String? = null, messageId: Long? = null) {
        log(LogLevel.ERROR, category, message, throwable, metadata, component, messageId)
    }
    
    fun fatal(category: Category, message: String, throwable: Throwable? = null, metadata: Map<String, Any> = emptyMap(), component: String? = null, messageId: Long? = null) {
        log(LogLevel.FATAL, category, message, throwable, metadata, component, messageId)
    }
    
    // Metody dla konkretnych kategorii
    
    fun queue(level: LogLevel, message: String, throwable: Throwable? = null, metadata: Map<String, Any> = emptyMap(), messageId: Long? = null) {
        log(level, Category.QUEUE, message, throwable, metadata, "SmsQueueService", messageId)
    }
    
    fun retry(level: LogLevel, message: String, throwable: Throwable? = null, metadata: Map<String, Any> = emptyMap(), messageId: Long? = null) {
        log(level, Category.RETRY, message, throwable, metadata, "RetryService", messageId)
    }
    
    fun health(level: LogLevel, message: String, throwable: Throwable? = null, metadata: Map<String, Any> = emptyMap) {
        log(level, Category.HEALTH, message, throwable, metadata, "HealthChecker")
    }
    
    fun metrics(level: LogLevel, message: String, metadata: Map<String, Any> = emptyMap) {
        log(level, Category.METRICS, message, null, metadata, "MetricsCollector")
    }
    
    fun worker(level: LogLevel, workerName: String, message: String, throwable: Throwable? = null, metadata: Map<String, Any> = emptyMap, messageId: Long? = null) {
        log(level, Category.WORKER, message, throwable, metadata, workerName, messageId)
    }
    
    fun api(level: LogLevel, endpoint: String, message: String, throwable: Throwable? = null, metadata: Map<String, Any> = emptyMap) {
        log(level, Category.API, message, throwable, metadata + mapOf("endpoint" to endpoint), "QueueRoutes")
    }
    
    fun database(level: LogLevel, operation: String, message: String, throwable: Throwable? = null, metadata: Map<String, Any> = emptyMap) {
        log(level, Category.DATABASE, message, throwable, metadata + mapOf("operation" to operation), "SmsRepository")
    }
    
    fun sms(level: LogLevel, phoneNumber: String, message: String, throwable: Throwable? = null, metadata: Map<String, Any> = emptyMap, messageId: Long? = null) {
        log(level, Category.SMS, message, throwable, metadata + mapOf("phoneNumber" to phoneNumber), "SmsManagerWrapper", messageId)
    }
    
    fun system(level: LogLevel, message: String, throwable: Throwable? = null, metadata: Map<String, Any> = emptyMap) {
        log(level, Category.SYSTEM, message, throwable, metadata, "SMSGateway")
    }
    
    fun security(level: LogLevel, message: String, throwable: Throwable? = null, metadata: Map<String, Any> = emptyMap) {
        log(level, Category.SECURITY, message, throwable, metadata, "Authentication")
    }
}