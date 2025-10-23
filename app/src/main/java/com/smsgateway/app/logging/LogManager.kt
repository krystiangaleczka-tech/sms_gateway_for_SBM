package com.smsgateway.app.logging

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Menedżer logów odpowiedzialny za zarządzanie, filtrowanie i eksportowanie logów
 * Integruje się z głównym systemem logowania i zapewnia dodatkowe funkcjonalności
 */
class LogManager {
    
    companion object {
        private const val LOG_FILE_PREFIX = "smsgateway_"
        private const val LOG_FILE_EXTENSION = ".log"
        private const val MAX_LOG_FILE_SIZE = 10 * 1024 * 1024 // 10MB
        private const val MAX_LOG_FILES = 5
        private const val LOG_CLEANUP_INTERVAL_HOURS = 24
        private const val LOG_RETENTION_DAYS = 30
    }
    
    // Konfiguracja menedżera logów
    data class LogManagerConfig(
        val enableFileLogging: Boolean = true,
        val logDirectory: String = "logs",
        val maxFileSizeBytes: Long = MAX_LOG_FILE_SIZE,
        val maxFiles: Int = MAX_LOG_FILES,
        val retentionDays: Int = LOG_RETENTION_DAYS,
        val enableAutoCleanup: Boolean = true,
        val enableCompression: Boolean = true,
        val enableLogAnalysis: Boolean = true,
        val exportFormats: Set<ExportFormat> = setOf(ExportFormat.JSON, ExportFormat.CSV)
    )
    
    // Formaty eksportu logów
    enum class ExportFormat(val extension: String, val mimeType: String) {
        JSON("json", "application/json"),
        CSV("csv", "text/csv"),
        TXT("txt", "text/plain"),
        XML("xml", "application/xml")
    }
    
    // Filtry logów
    data class LogFilter(
        val levels: Set<Logger.LogLevel> = Logger.LogLevel.values().toSet(),
        val categories: Set<Logger.Category> = Logger.Category.values().toSet(),
        val components: Set<String> = emptySet(),
        val startTime: Long? = null,
        val endTime: Long? = null,
        val messagePattern: String? = null,
        val includeExceptions: Boolean = true,
        val excludeExceptions: Boolean = false
    ) {
        fun matches(entry: Logger.LogEntry): Boolean {
            // Sprawdzenie poziomu
            if (entry.level !in levels) return false
            
            // Sprawdzenie kategorii
            if (entry.category !in categories) return false
            
            // Sprawdzenie komponentu
            if (components.isNotEmpty() && (entry.component == null || entry.component !in components)) {
                return false
            }
            
            // Sprawdzenie czasu
            if (startTime != null && entry.timestamp < startTime) return false
            if (endTime != null && entry.timestamp > endTime) return false
            
            // Sprawdzenie patternu wiadomości
            if (messagePattern != null && !entry.message.contains(messagePattern, ignoreCase = true)) {
                return false
            }
            
            // Sprawdzenie wyjątków
            if (!includeExceptions && entry.throwable != null) return false
            if (excludeExceptions && entry.throwable != null) return false
            
            return true
        }
    }
    
    // Statystyki logów
    data class LogAnalysisStats(
        val totalLogs: Long = 0,
        val logsByLevel: Map<Logger.LogLevel, Long> = emptyMap(),
        val logsByCategory: Map<Logger.Category, Long> = emptyMap(),
        val logsByComponent: Map<String, Long> = emptyMap(),
        val logsByHour: Map<String, Long> = emptyMap(),
        val errorPatterns: Map<String, Long> = emptyMap(),
        val topErrors: List<Logger.LogEntry> = emptyList(),
        val performanceMetrics: Map<String, Double> = emptyMap(),
        val generatedAt: Long = System.currentTimeMillis()
    )
    
    // Prywatne pola
    private val config = AtomicReference(LogManagerConfig())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(2)
    private val isInitialized = AtomicBoolean(false)
    private val currentLogFile = AtomicReference<File>()
    private val logFileWriter = AtomicReference<FileWriter>()
    private val logAnalysisCache = ConcurrentHashMap<String, LogAnalysisStats>()
    private val lastAnalysisTime = AtomicLong(0)
    
    // Inicjalizacja
    fun initialize(newConfig: LogManagerConfig = LogManagerConfig()) {
        if (isInitialized.compareAndSet(false, true)) {
            config.set(newConfig)
            
            Logger.info(Logger.Category.SYSTEM, "LogManager initializing", mapOf(
                "enableFileLogging" to newConfig.enableFileLogging,
                "logDirectory" to newConfig.logDirectory,
                "maxFileSizeBytes" to newConfig.maxFileSizeBytes,
                "maxFiles" to newConfig.maxFiles,
                "retentionDays" to newConfig.retentionDays
            ))
            
            // Utworzenie katalogu logów
            if (newConfig.enableFileLogging) {
                createLogDirectory()
                setupFileLogging()
                setupLogRotation()
            }
            
            // Uruchomienie automatycznego czyszczenia
            if (newConfig.enableAutoCleanup) {
                scheduleLogCleanup()
            }
            
            // Subskrypcja zdarzeń logowania
            subscribeToLogEvents()
            
            Logger.info(Logger.Category.SYSTEM, "LogManager initialized successfully")
        }
    }
    
    /**
     * Konfiguruje menedżer logów
     */
    fun configure(newConfig: LogManagerConfig) {
        val oldConfig = config.get()
        config.set(newConfig)
        
        Logger.info(Logger.Category.SYSTEM, "LogManager configuration updated", mapOf(
            "oldConfig" to oldConfig.toString(),
            "newConfig" to newConfig.toString()
        ))
        
        // Ponowna inicjalizacja jeśli wymagane
        if (newConfig.enableFileLogging != oldConfig.enableFileLogging ||
            newConfig.logDirectory != oldConfig.logDirectory) {
            
            shutdownFileLogging()
            if (newConfig.enableFileLogging) {
                createLogDirectory()
                setupFileLogging()
                setupLogRotation()
            }
        }
    }
    
    /**
     * Pobiera aktualną konfigurację
     */
    fun getConfig(): LogManagerConfig = config.get()
    
    /**
     * Pobiera przefiltrowane logi
     */
    fun getFilteredLogs(filter: LogFilter = LogFilter(), limit: Int = 1000): List<Logger.LogEntry> {
        return Logger.getRecentLogs(5000)
            .filter { filter.matches(it) }
            .takeLast(limit)
    }
    
    /**
     * Pobiera flow przefiltrowanych logów
     */
    fun getFilteredLogsFlow(filter: LogFilter = LogFilter()): Flow<Logger.LogEntry> {
        return Logger.logEvents.filter { filter.matches(it) }
    }
    
    /**
     * Eksportuje logi do wybranego formatu
     */
    suspend fun exportLogs(
        filter: LogFilter = LogFilter(),
        format: ExportFormat = ExportFormat.JSON,
        outputFile: File? = null
    ): File {
        val logs = getFilteredLogs(filter, 10000)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val fileName = if (outputFile != null) {
            outputFile
        } else {
            File(config.get().logDirectory, "export_${timestamp}.${format.extension}")
        }
        
        return when (format) {
            ExportFormat.JSON -> exportToJson(logs, fileName)
            ExportFormat.CSV -> exportToCsv(logs, fileName)
            ExportFormat.TXT -> exportToTxt(logs, fileName)
            ExportFormat.XML -> exportToXml(logs, fileName)
        }
    }
    
    /**
     * Analizuje logi i generuje statystyki
     */
    fun analyzeLogs(filter: LogFilter = LogFilter(), forceRefresh: Boolean = false): LogAnalysisStats {
        val cacheKey = "${filter.hashCode()}_${System.currentTimeMillis() / (60 * 60 * 1000)}" // Cache na godzinę
        
        if (!forceRefresh) {
            logAnalysisCache[cacheKey]?.let { return it }
        }
        
        val logs = getFilteredLogs(filter, 50000)
        val now = System.currentTimeMillis()
        
        val stats = LogAnalysisStats(
            totalLogs = logs.size.toLong(),
            logsByLevel = logs.groupBy { it.level }.mapValues { it.value.size.toLong() },
            logsByCategory = logs.groupBy { it.category }.mapValues { it.value.size.toLong() },
            logsByComponent = logs.groupBy { it.component ?: "Unknown" }.mapValues { it.value.size.toLong() },
            logsByHour = analyzeLogsByHour(logs),
            errorPatterns = analyzeErrorPatterns(logs),
            topErrors = logs.filter { it.level in listOf(Logger.LogLevel.ERROR, Logger.LogLevel.FATAL) }
                .sortedByDescending { it.timestamp }
                .take(10),
            performanceMetrics = analyzePerformanceMetrics(logs),
            generatedAt = now
        )
        
        logAnalysisCache[cacheKey] = stats
        lastAnalysisTime.set(now)
        
        Logger.debug(Logger.Category.METRICS, "Log analysis completed", mapOf(
            "totalLogs" to stats.totalLogs,
            "errorCount" to (stats.logsByLevel[Logger.LogLevel.ERROR] ?: 0),
            "fatalCount" to (stats.logsByLevel[Logger.LogLevel.FATAL] ?: 0)
        ))
        
        return stats
    }
    
    /**
     * Czyści stare logi
     */
    fun cleanupOldLogs() {
        val currentConfig = config.get()
        val logDir = File(currentConfig.logDirectory)
        
        if (!logDir.exists()) return
        
        val cutoffTime = System.currentTimeMillis() - (currentConfig.retentionDays * 24 * 60 * 60 * 1000L)
        val deletedFiles = mutableListOf<String>()
        
        logDir.listFiles { file ->
            file.name.startsWith(LOG_FILE_PREFIX) && file.name.endsWith(LOG_FILE_EXTENSION)
        }?.forEach { file ->
            if (file.lastModified() < cutoffTime) {
                if (file.delete()) {
                    deletedFiles.add(file.name)
                }
            }
        }
        
        Logger.info(Logger.Category.SYSTEM, "Log cleanup completed", mapOf(
            "deletedFiles" to deletedFiles.size,
            "retentionDays" to currentConfig.retentionDays
        ))
    }
    
    /**
     * Zamyka menedżer logów
     */
    fun shutdown() {
        Logger.info(Logger.Category.SYSTEM, "LogManager shutting down")
        
        scheduler.shutdown()
        shutdownFileLogging()
        isInitialized.set(false)
        
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow()
            }
        } catch (e: InterruptedException) {
            scheduler.shutdownNow()
            Thread.currentThread().interrupt()
        }
        
        Logger.info(Logger.Category.SYSTEM, "LogManager shutdown completed")
    }
    
    // Prywatne metody
    
    private fun createLogDirectory() {
        val logDir = File(config.get().logDirectory)
        if (!logDir.exists()) {
            logDir.mkdirs()
            Logger.info(Logger.Category.SYSTEM, "Log directory created", mapOf("path" to logDir.absolutePath))
        }
    }
    
    private fun setupFileLogging() {
        val logFile = createNewLogFile()
        currentLogFile.set(logFile)
        
        try {
            val writer = FileWriter(logFile, true)
            logFileWriter.set(writer)
            
            Logger.info(Logger.Category.SYSTEM, "File logging initialized", mapOf(
                "logFile" to logFile.absolutePath
            ))
        } catch (e: Exception) {
            Logger.error(Logger.Category.SYSTEM, "Failed to initialize file logging", e)
        }
    }
    
    private fun createNewLogFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val fileName = "$LOG_FILE_PREFIX$timestamp$LOG_FILE_EXTENSION"
        return File(config.get().logDirectory, fileName)
    }
    
    private fun setupLogRotation() {
        scheduler.scheduleAtFixedRate({
            try {
                val currentFile = currentLogFile.get()
                val writer = logFileWriter.get()
                
                if (currentFile != null && writer != null && currentFile.length() > config.get().maxFileSizeBytes) {
                    // Rotacja pliku
                    writer.close()
                    setupFileLogging()
                    
                    Logger.info(Logger.Category.SYSTEM, "Log file rotated", mapOf(
                        "oldFile" to currentFile.absolutePath,
                        "oldSize" to currentFile.length()
                    ))
                    
                    // Sprawdzenie liczby plików
                    cleanupOldLogFiles()
                }
            } catch (e: Exception) {
                Logger.error(Logger.Category.SYSTEM, "Log rotation failed", e)
            }
        }, 1, 1, TimeUnit.HOURS)
    }
    
    private fun cleanupOldLogFiles() {
        val logDir = File(config.get().logDirectory)
        val logFiles = logDir.listFiles { file ->
            file.name.startsWith(LOG_FILE_PREFIX) && file.name.endsWith(LOG_FILE_EXTENSION)
        }?.sortedByDescending { it.lastModified() } ?: return
        
        if (logFiles.size > config.get().maxFiles) {
            val filesToDelete = logFiles.drop(config.get().maxFiles)
            filesToDelete.forEach { file ->
                if (file.delete()) {
                    Logger.debug(Logger.Category.SYSTEM, "Old log file deleted", mapOf(
                        "file" to file.name
                    ))
                }
            }
        }
    }
    
    private fun scheduleLogCleanup() {
        scheduler.scheduleAtFixedRate({
            try {
                cleanupOldLogs()
            } catch (e: Exception) {
                Logger.error(Logger.Category.SYSTEM, "Scheduled log cleanup failed", e)
            }
        }, 1, LOG_CLEANUP_INTERVAL_HOURS.toLong(), TimeUnit.HOURS)
    }
    
    private fun subscribeToLogEvents() {
        scope.launch {
            Logger.logEvents.collect { entry ->
                try {
                    writeToLogFile(entry)
                } catch (e: Exception) {
                    // Nie blokujemy logowania w przypadku błędu zapisu do pliku
                    Logger.error(Logger.Category.SYSTEM, "Failed to write log to file", e)
                }
            }
        }
    }
    
    private fun writeToLogFile(entry: Logger.LogEntry) {
        val writer = logFileWriter.get() ?: return
        val currentConfig = config.get()
        
        if (!currentConfig.enableFileLogging) return
        
        try {
            writer.write(entry.toFormattedString())
            writer.write("\n")
            writer.flush()
        } catch (e: Exception) {
            Logger.error(Logger.Category.SYSTEM, "Failed to write log entry to file", e)
        }
    }
    
    private fun shutdownFileLogging() {
        logFileWriter.get()?.let { writer ->
            try {
                writer.close()
            } catch (e: Exception) {
                Logger.error(Logger.Category.SYSTEM, "Failed to close log file writer", e)
            }
        }
        logFileWriter.set(null)
    }
    
    private fun analyzeLogsByHour(logs: List<Logger.LogEntry>): Map<String, Long> {
        return logs.groupBy { entry ->
            val dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(entry.timestamp), 
                ZoneId.systemDefault()
            )
            dateTime.hour.toString().padStart(2, '0') + ":00"
        }.mapValues { it.value.size.toLong() }
    }
    
    private fun analyzeErrorPatterns(logs: List<Logger.LogEntry>): Map<String, Long> {
        return logs
            .filter { it.level in listOf(Logger.LogLevel.ERROR, Logger.LogLevel.FATAL) }
            .groupBy { entry ->
                // Ekstrakcja wzorca błędu z wiadomości lub wyjątku
                val pattern = when {
                    entry.throwable != null -> entry.throwable!!::class.java.simpleName
                    entry.message.contains("timeout", ignoreCase = true) -> "TIMEOUT"
                    entry.message.contains("connection", ignoreCase = true) -> "CONNECTION"
                    entry.message.contains("permission", ignoreCase = true) -> "PERMISSION"
                    entry.message.contains("network", ignoreCase = true) -> "NETWORK"
                    else -> "UNKNOWN"
                }
                pattern
            }
            .mapValues { it.value.size.toLong() }
    }
    
    private fun analyzePerformanceMetrics(logs: List<Logger.LogEntry>): Map<String, Double> {
        val totalLogs = logs.size.toDouble()
        if (totalLogs == 0.0) return emptyMap()
        
        val errorLogs = logs.count { it.level in listOf(Logger.LogLevel.ERROR, Logger.LogLevel.FATAL) }
        val warnLogs = logs.count { it.level == Logger.LogLevel.WARN }
        
        return mapOf(
            "errorRate" to (errorLogs / totalLogs) * 100,
            "warnRate" to (warnLogs / totalLogs) * 100,
            "logsPerHour" to (totalLogs / 24), // Zakładamy 24h okres
            "averageLogsPerComponent" to (totalLogs / logs.mapNotNull { it.component }.toSet().size)
        )
    }
    
    // Metody eksportu
    
    private suspend fun exportToJson(logs: List<Logger.LogEntry>, outputFile: File): File {
        val jsonContent = buildString {
            append("[\n")
            logs.forEachIndexed { index, entry ->
                if (index > 0) append(",\n")
                append("  ${entry.toJsonString()}")
            }
            append("\n]")
        }
        
        outputFile.writeText(jsonContent)
        Logger.info(Logger.Category.SYSTEM, "Logs exported to JSON", mapOf(
            "file" to outputFile.absolutePath,
            "count" to logs.size
        ))
        
        return outputFile
    }
    
    private suspend fun exportToCsv(logs: List<Logger.LogEntry>, outputFile: File): File {
        val csvContent = buildString {
            append("timestamp,level,category,component,message,thread,metadata\n")
            logs.forEach { entry ->
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(Date(entry.timestamp))
                val level = entry.level.displayName
                val category = entry.category.value
                val component = entry.component ?: ""
                val message = entry.message.replace("\"", "\"\"")
                val thread = entry.threadName
                val metadata = entry.metadata.map { "${it.key}=${it.value}" }.joinToString(";")
                
                append("\"$timestamp\",\"$level\",\"$category\",\"$component\",\"$message\",\"$thread\",\"$metadata\"\n")
            }
        }
        
        outputFile.writeText(csvContent)
        Logger.info(Logger.Category.SYSTEM, "Logs exported to CSV", mapOf(
            "file" to outputFile.absolutePath,
            "count" to logs.size
        ))
        
        return outputFile
    }
    
    private suspend fun exportToTxt(logs: List<Logger.LogEntry>, outputFile: File): File {
        val txtContent = logs.joinToString("\n") { entry ->
            entry.toFormattedString()
        }
        
        outputFile.writeText(txtContent)
        Logger.info(Logger.Category.SYSTEM, "Logs exported to TXT", mapOf(
            "file" to outputFile.absolutePath,
            "count" to logs.size
        ))
        
        return outputFile
    }
    
    private suspend fun exportToXml(logs: List<Logger.LogEntry>, outputFile: File): File {
        val xmlContent = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<logs>\n")
            logs.forEach { entry ->
                append("  <log>\n")
                append("    <timestamp>${entry.timestamp}</timestamp>\n")
                append("    <level>${entry.level.displayName}</level>\n")
                append("    <category>${entry.category.value}</category>\n")
                append("    <component>${entry.component ?: ""}</component>\n")
                append("    <message><![CDATA[${entry.message}]]></message>\n")
                append("    <thread>${entry.threadName}</thread>\n")
                
                if (entry.metadata.isNotEmpty()) {
                    append("    <metadata>\n")
                    entry.metadata.forEach { (key, value) ->
                        append("      <entry key=\"$key\" value=\"$value\"/>\n")
                    }
                    append("    </metadata>\n")
                }
                
                entry.throwable?.let {
                    append("    <exception>\n")
                    append("      <type>${it.javaClass.simpleName}</type>\n")
                    append("      <message><![CDATA[${it.message ?: ""}]]></message>\n")
                    append("    </exception>\n")
                }
                
                append("  </log>\n")
            }
            append("</logs>\n")
        }
        
        outputFile.writeText(xmlContent)
        Logger.info(Logger.Category.SYSTEM, "Logs exported to XML", mapOf(
            "file" to outputFile.absolutePath,
            "count" to logs.size
        ))
        
        return outputFile
    }
}

// Globalna instancja menedżera logów
val LogManager = LogManager()