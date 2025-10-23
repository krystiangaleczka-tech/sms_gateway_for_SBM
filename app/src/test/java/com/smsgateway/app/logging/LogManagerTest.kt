package com.smsgateway.app.logging

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList

class LogManagerTest {
    
    @TempDir
    lateinit var tempDir: Path
    
    private lateinit var logManager: LogManager
    
    @BeforeEach
    fun setUp() {
        LogManager.reset()
        
        val config = LogManager.LogManagerConfig(
            enableFileLogging = true,
            logDirectory = tempDir.toString(),
            maxFileSizeBytes = 1024, // 1KB dla testów
            maxFiles = 3,
            retentionDays = 7,
            enableAutoCleanup = true,
            enableCompression = false, // Wyłączone dla prostszych testów
            enableLogAnalysis = true,
            exportFormats = setOf(
                LogManager.ExportFormat.JSON,
                LogManager.ExportFormat.CSV,
                LogManager.ExportFormat.TXT
            )
        )
        
        logManager = LogManager.getInstance()
        logManager.configure(config)
    }
    
    @AfterEach
    fun tearDown() {
        LogManager.reset()
    }
    
    @Test
    fun `should configure log manager with custom config`() {
        // Given
        val config = LogManager.LogManagerConfig(
            enableFileLogging = false,
            logDirectory = "/custom/logs",
            maxFileSizeBytes = 5 * 1024 * 1024,
            maxFiles = 10,
            retentionDays = 30,
            enableAutoCleanup = false,
            enableCompression = true,
            enableLogAnalysis = false,
            exportFormats = setOf(LogManager.ExportFormat.JSON)
        )
        
        // When
        logManager.configure(config)
        
        // Then
        val currentConfig = logManager.getConfig()
        assertFalse(currentConfig.enableFileLogging)
        assertEquals("/custom/logs", currentConfig.logDirectory)
        assertEquals(5 * 1024 * 1024, currentConfig.maxFileSizeBytes)
        assertEquals(10, currentConfig.maxFiles)
        assertEquals(30, currentConfig.retentionDays)
        assertFalse(currentConfig.enableAutoCleanup)
        assertTrue(currentConfig.enableCompression)
        assertFalse(currentConfig.enableLogAnalysis)
        assertEquals(setOf(LogManager.ExportFormat.JSON), currentConfig.exportFormats)
    }
    
    @Test
    fun `should add log entry and retrieve it`() = runTest {
        // Given
        val logEntry = Logger.LogEntry(
            timestamp = System.currentTimeMillis(),
            level = Logger.LogLevel.INFO,
            category = Logger.Category.SMS,
            message = "Test message",
            metadata = mapOf("key" to "value"),
            threadName = "test-thread"
        )
        
        // When
        logManager.addLogEntry(logEntry)
        
        // Then
        val logs = logManager.getLogs(10).first()
        assertEquals(1, logs.size)
        assertEquals(logEntry, logs[0])
    }
    
    @Test
    fun `should filter logs by level`() = runTest {
        // Given
        val debugEntry = Logger.LogEntry(
            timestamp = System.currentTimeMillis(),
            level = Logger.LogLevel.DEBUG,
            category = Logger.Category.SMS,
            message = "Debug message",
            metadata = emptyMap(),
            threadName = "test-thread"
        )
        
        val infoEntry = Logger.LogEntry(
            timestamp = System.currentTimeMillis() + 1,
            level = Logger.LogLevel.INFO,
            category = Logger.Category.SMS,
            message = "Info message",
            metadata = emptyMap(),
            threadName = "test-thread"
        )
        
        val errorEntry = Logger.LogEntry(
            timestamp = System.currentTimeMillis() + 2,
            level = Logger.LogLevel.ERROR,
            category = Logger.Category.SMS,
            message = "Error message",
            metadata = emptyMap(),
            threadName = "test-thread"
        )
        
        logManager.addLogEntry(debugEntry)
        logManager.addLogEntry(infoEntry)
        logManager.addLogEntry(errorEntry)
        
        // When
        val filter = LogManager.LogFilter(
            minLevel = Logger.LogLevel.INFO,
            maxLevel = Logger.LogLevel.ERROR
        )
        val filteredLogs = logManager.getFilteredLogs(filter, 10).first()
        
        // Then
        assertEquals(2, filteredLogs.size)
        assertTrue(filteredLogs.all { it.level.value >= Logger.LogLevel.INFO.value })
        assertTrue(filteredLogs.all { it.level.value <= Logger.LogLevel.ERROR.value })
    }
    
    @Test
    fun `should filter logs by category`() = runTest {
        // Given
        val smsEntry = Logger.LogEntry(
            timestamp = System.currentTimeMillis(),
            level = Logger.LogLevel.INFO,
            category = Logger.Category.SMS,
            message = "SMS message",
            metadata = emptyMap(),
            threadName = "test-thread"
        )
        
        val queueEntry = Logger.LogEntry(
            timestamp = System.currentTimeMillis() + 1,
            level = Logger.LogLevel.INFO,
            category = Logger.Category.QUEUE,
            message = "Queue message",
            metadata = emptyMap(),
            threadName = "test-thread"
        )
        
        logManager.addLogEntry(smsEntry)
        logManager.addLogEntry(queueEntry)
        
        // When
        val filter = LogManager.LogFilter(
            categories = setOf(Logger.Category.SMS)
        )
        val filteredLogs = logManager.getFilteredLogs(filter, 10).first()
        
        // Then
        assertEquals(1, filteredLogs.size)
        assertEquals(Logger.Category.SMS, filteredLogs[0].category)
    }
    
    @Test
    fun `should filter logs by time range`() = runTest {
        // Given
        val baseTime = System.currentTimeMillis()
        val oldEntry = Logger.LogEntry(
            timestamp = baseTime - 3600000, // 1 hour ago
            level = Logger.LogLevel.INFO,
            category = Logger.Category.SMS,
            message = "Old message",
            metadata = emptyMap(),
            threadName = "test-thread"
        )
        
        val newEntry = Logger.LogEntry(
            timestamp = baseTime,
            level = Logger.LogLevel.INFO,
            category = Logger.Category.SMS,
            message = "New message",
            metadata = emptyMap(),
            threadName = "test-thread"
        )
        
        logManager.addLogEntry(oldEntry)
        logManager.addLogEntry(newEntry)
        
        // When
        val filter = LogManager.LogFilter(
            startTime = baseTime - 1800000, // 30 minutes ago
            endTime = baseTime + 1800000     // 30 minutes from now
        )
        val filteredLogs = logManager.getFilteredLogs(filter, 10).first()
        
        // Then
        assertEquals(1, filteredLogs.size)
        assertEquals("New message", filteredLogs[0].message)
    }
    
    @Test
    fun `should filter logs by message pattern`() = runTest {
        // Given
        val matchingEntry = Logger.LogEntry(
            timestamp = System.currentTimeMillis(),
            level = Logger.LogLevel.INFO,
            category = Logger.Category.SMS,
            message = "SMS sent successfully",
            metadata = emptyMap(),
            threadName = "test-thread"
        )
        
        val nonMatchingEntry = Logger.LogEntry(
            timestamp = System.currentTimeMillis() + 1,
            level = Logger.LogLevel.INFO,
            category = Logger.Category.SMS,
            message = "SMS failed",
            metadata = emptyMap(),
            threadName = "test-thread"
        )
        
        logManager.addLogEntry(matchingEntry)
        logManager.addLogEntry(nonMatchingEntry)
        
        // When
        val filter = LogManager.LogFilter(
            messagePattern = "sent.*successfully"
        )
        val filteredLogs = logManager.getFilteredLogs(filter, 10).first()
        
        // Then
        assertEquals(1, filteredLogs.size)
        assertEquals("SMS sent successfully", filteredLogs[0].message)
    }
    
    @Test
    fun `should export logs to JSON format`() = runTest {
        // Given
        val logEntry = Logger.LogEntry(
            timestamp = System.currentTimeMillis(),
            level = Logger.LogLevel.INFO,
            category = Logger.Category.SMS,
            message = "Test message",
            metadata = mapOf("key" to "value"),
            threadName = "test-thread"
        )
        
        logManager.addLogEntry(logEntry)
        
        // When
        val exportedData = logManager.exportLogs(LogManager.ExportFormat.JSON, 10)
        
        // Then
        assertNotNull(exportedData)
        assertTrue(exportedData.contains("\"level\":\"INFO\""))
        assertTrue(exportedData.contains("\"category\":\"SMS\""))
        assertTrue(exportedData.contains("\"message\":\"Test message\""))
        assertTrue(exportedData.contains("\"key\":\"value\""))
    }
    
    @Test
    fun `should export logs to CSV format`() = runTest {
        // Given
        val logEntry = Logger.LogEntry(
            timestamp = System.currentTimeMillis(),
            level = Logger.LogLevel.INFO,
            category = Logger.Category.SMS,
            message = "Test message",
            metadata = mapOf("key" to "value"),
            threadName = "test-thread"
        )
        
        logManager.addLogEntry(logEntry)
        
        // When
        val exportedData = logManager.exportLogs(LogManager.ExportFormat.CSV, 10)
        
        // Then
        assertNotNull(exportedData)
        assertTrue(exportedData.contains("timestamp,level,category,message"))
        assertTrue(exportedData.contains("INFO,SMS,Test message"))
    }
    
    @Test
    fun `should export logs to TXT format`() = runTest {
        // Given
        val logEntry = Logger.LogEntry(
            timestamp = System.currentTimeMillis(),
            level = Logger.LogLevel.INFO,
            category = Logger.Category.SMS,
            message = "Test message",
            metadata = emptyMap(),
            threadName = "test-thread"
        )
        
        logManager.addLogEntry(logEntry)
        
        // When
        val exportedData = logManager.exportLogs(LogManager.ExportFormat.TXT, 10)
        
        // Then
        assertNotNull(exportedData)
        assertTrue(exportedData.contains("INFO"))
        assertTrue(exportedData.contains("SMS"))
        assertTrue(exportedData.contains("Test message"))
    }
    
    @Test
    fun `should analyze logs and generate stats`() = runTest {
        // Given
        repeat(5) {
            logManager.addLogEntry(Logger.LogEntry(
                timestamp = System.currentTimeMillis(),
                level = Logger.LogLevel.INFO,
                category = Logger.Category.SMS,
                message = "SMS message $it",
                metadata = emptyMap(),
                threadName = "test-thread"
            ))
        }
        
        repeat(3) {
            logManager.addLogEntry(Logger.LogEntry(
                timestamp = System.currentTimeMillis(),
                level = Logger.LogLevel.ERROR,
                category = Logger.Category.SMS,
                message = "SMS error $it",
                metadata = emptyMap(),
                threadName = "test-thread"
            ))
        }
        
        repeat(2) {
            logManager.addLogEntry(Logger.LogEntry(
                timestamp = System.currentTimeMillis(),
                level = Logger.LogLevel.WARN,
                category = Logger.Category.QUEUE,
                message = "Queue warning $it",
                metadata = emptyMap(),
                threadName = "test-thread"
            ))
        }
        
        // When
        val stats = logManager.analyzeLogs()
        
        // Then
        assertEquals(10, stats.totalLogs)
        assertEquals(5, stats.logsByLevel[Logger.LogLevel.INFO])
        assertEquals(3, stats.logsByLevel[Logger.LogLevel.ERROR])
        assertEquals(2, stats.logsByLevel[Logger.LogLevel.WARN])
        assertEquals(8, stats.logsByCategory[Logger.Category.SMS])
        assertEquals(2, stats.logsByCategory[Logger.Category.QUEUE])
        assertEquals(0.3, stats.errorRate, 0.01) // 3 errors out of 10 logs
    }
    
    @Test
    fun `should cleanup old files`() = runTest {
        // Given - stwórz stare pliki
        val oldFile = File(tempDir.toFile(), "old-log-20200101.log")
        val recentFile = File(tempDir.toFile(), "recent-log.log")
        
        oldFile.createNewFile()
        oldFile.setLastModified(System.currentTimeMillis() - (8 * 24 * 60 * 60 * 1000)) // 8 days ago
        recentFile.createNewFile()
        
        // When
        logManager.cleanupOldFiles()
        
        // Then
        assertFalse(oldFile.exists())
        assertTrue(recentFile.exists())
    }
    
    @Test
    fun `should get log statistics`() = runTest {
        // Given
        repeat(10) {
            logManager.addLogEntry(Logger.LogEntry(
                timestamp = System.currentTimeMillis(),
                level = Logger.LogLevel.INFO,
                category = Logger.Category.SMS,
                message = "Message $it",
                metadata = emptyMap(),
                threadName = "test-thread"
            ))
        }
        
        // When
        val stats = logManager.getLogStatistics()
        
        // Then
        assertEquals(10, stats.totalLogs)
        assertTrue(stats.memoryUsageBytes > 0)
        assertTrue(stats.averageLogSize > 0)
    }
    
    @Test
    fun `should handle empty log export`() = runTest {
        // When
        val exportedData = logManager.exportLogs(LogManager.ExportFormat.JSON, 10)
        
        // Then
        assertEquals("[]", exportedData)
    }
    
    @Test
    fun `should limit export size`() = runTest {
        // Given
        repeat(10) {
            logManager.addLogEntry(Logger.LogEntry(
                timestamp = System.currentTimeMillis(),
                level = Logger.LogLevel.INFO,
                category = Logger.Category.SMS,
                message = "Message $it",
                metadata = emptyMap(),
                threadName = "test-thread"
            ))
        }
        
        // When
        val exportedData = logManager.exportLogs(LogManager.ExportFormat.JSON, 5)
        
        // Then
        // Powinno zawierać tylko 5 wpisów
        val entries = exportedData.split("},{")
        assertEquals(5, entries.size)
    }
}