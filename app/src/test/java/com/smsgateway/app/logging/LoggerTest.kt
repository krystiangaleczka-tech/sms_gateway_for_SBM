package com.smsgateway.app.logging

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.mockito.kotlin.*
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class LoggerTest {
    
    private val originalOut = System.out
    private val originalErr = System.err
    private lateinit var testOut: ByteArrayOutputStream
    private lateinit var testErr: ByteArrayOutputStream
    
    @BeforeEach
    fun setUp() {
        testOut = ByteArrayOutputStream()
        testErr = ByteArrayOutputStream()
        System.setOut(PrintStream(testOut))
        System.setErr(PrintStream(testErr))
        
        // Reset Logger przed każdym testem
        Logger.reset()
    }
    
    @AfterEach
    fun tearDown() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        Logger.reset()
    }
    
    @Test
    fun `should configure logger with custom config`() {
        // Given
        val config = Logger.LoggingConfig(
            minLevel = Logger.LogLevel.WARN,
            enableConsole = true,
            enableFile = false,
            enableEvents = true,
            maxLogEntries = 500,
            enableMetrics = true,
            categories = setOf(Logger.Category.SMS, Logger.Category.QUEUE),
            componentFilters = setOf("TestComponent"),
            enableStructuredLogging = false
        )
        
        // When
        Logger.configure(config)
        
        // Then
        val currentConfig = Logger.getConfig()
        assertEquals(Logger.LogLevel.WARN, currentConfig.minLevel)
        assertFalse(currentConfig.enableFile)
        assertTrue(currentConfig.enableEvents)
        assertEquals(500, currentConfig.maxLogEntries)
        assertEquals(setOf(Logger.Category.SMS, Logger.Category.QUEUE), currentConfig.categories)
        assertEquals(setOf("TestComponent"), currentConfig.componentFilters)
    }
    
    @Test
    fun `should log message with correct level and category`() = runTest {
        // Given
        val config = Logger.LoggingConfig(
            minLevel = Logger.LogLevel.DEBUG,
            enableConsole = true,
            enableFile = false,
            enableEvents = false,
            maxLogEntries = 100,
            enableMetrics = false,
            categories = Logger.Category.values().toSet(),
            componentFilters = emptySet(),
            enableStructuredLogging = false
        )
        Logger.configure(config)
        
        // When
        Logger.debug(Logger.Category.SMS, "Test debug message", mapOf("key" to "value"))
        
        // Then
        val output = testOut.toString()
        assertTrue(output.contains("DEBUG"))
        assertTrue(output.contains("SMS"))
        assertTrue(output.contains("Test debug message"))
    }
    
    @Test
    fun `should not log message below minimum level`() = runTest {
        // Given
        val config = Logger.LoggingConfig(
            minLevel = Logger.LogLevel.ERROR,
            enableConsole = true,
            enableFile = false,
            enableEvents = false,
            maxLogEntries = 100,
            enableMetrics = false,
            categories = Logger.Category.values().toSet(),
            componentFilters = emptySet(),
            enableStructuredLogging = false
        )
        Logger.configure(config)
        
        // When
        Logger.debug(Logger.Category.SMS, "Debug message")
        Logger.info(Logger.Category.SMS, "Info message")
        Logger.warn(Logger.Category.SMS, "Warning message")
        
        // Then
        val output = testOut.toString()
        assertTrue(output.isEmpty()) // Nic nie powinno być zalogowane
    }
    
    @Test
    fun `should filter by component`() = runTest {
        // Given
        val config = Logger.LoggingConfig(
            minLevel = Logger.LogLevel.DEBUG,
            enableConsole = true,
            enableFile = false,
            enableEvents = false,
            maxLogEntries = 100,
            enableMetrics = false,
            categories = Logger.Category.values().toSet(),
            componentFilters = setOf("AllowedComponent"),
            enableStructuredLogging = false
        )
        Logger.configure(config)
        
        // When
        Logger.debug(Logger.Category.SMS, "Message from allowed component", component = "AllowedComponent")
        Logger.debug(Logger.Category.SMS, "Message from blocked component", component = "BlockedComponent")
        
        // Then
        val output = testOut.toString()
        assertTrue(output.contains("Message from allowed component"))
        assertFalse(output.contains("Message from blocked component"))
    }
    
    @Test
    fun `should log with throwable information`() = runTest {
        // Given
        val config = Logger.LoggingConfig(
            minLevel = Logger.LogLevel.DEBUG,
            enableConsole = true,
            enableFile = false,
            enableEvents = false,
            maxLogEntries = 100,
            enableMetrics = false,
            categories = Logger.Category.values().toSet(),
            componentFilters = emptySet(),
            enableStructuredLogging = false
        )
        Logger.configure(config)
        val exception = RuntimeException("Test exception")
        
        // When
        Logger.error(Logger.Category.SYSTEM, "Error occurred", exception)
        
        // Then
        val output = testOut.toString()
        assertTrue(output.contains("ERROR"))
        assertTrue(output.contains("Error occurred"))
        assertTrue(output.contains("Test exception"))
    }
    
    @Test
    fun `should use convenience methods for categories`() = runTest {
        // Given
        val config = Logger.LoggingConfig(
            minLevel = Logger.LogLevel.DEBUG,
            enableConsole = true,
            enableFile = false,
            enableEvents = false,
            maxLogEntries = 100,
            enableMetrics = false,
            categories = Logger.Category.values().toSet(),
            componentFilters = emptySet(),
            enableStructuredLogging = false
        )
        Logger.configure(config)
        
        // When
        Logger.queue("Queue message")
        Logger.retry("Retry message")
        Logger.health("Health message")
        Logger.metrics("Metrics message")
        Logger.worker("Worker message")
        Logger.api("API message")
        Logger.database("Database message")
        Logger.sms("SMS message")
        Logger.system("System message")
        Logger.security("Security message")
        
        // Then
        val output = testOut.toString()
        assertTrue(output.contains("QUEUE"))
        assertTrue(output.contains("RETRY"))
        assertTrue(output.contains("HEALTH"))
        assertTrue(output.contains("METRICS"))
        assertTrue(output.contains("WORKER"))
        assertTrue(output.contains("API"))
        assertTrue(output.contains("DATABASE"))
        assertTrue(output.contains("SMS"))
        assertTrue(output.contains("SYSTEM"))
        assertTrue(output.contains("SECURITY"))
    }
    
    @Test
    fun `should limit log entries to max size`() = runTest {
        // Given
        val maxEntries = 5
        val config = Logger.LoggingConfig(
            minLevel = Logger.LogLevel.DEBUG,
            enableConsole = false,
            enableFile = false,
            enableEvents = false,
            maxLogEntries = maxEntries,
            enableMetrics = false,
            categories = Logger.Category.values().toSet(),
            componentFilters = emptySet(),
            enableStructuredLogging = false
        )
        Logger.configure(config)
        
        // When - dodaj więcej wpisów niż limit
        repeat(10) { index ->
            Logger.debug(Logger.Category.SMS, "Message $index")
        }
        
        // Then - sprawdź czy nie przekroczono limitu
        val logs = Logger.getRecentLogs(100)
        assertTrue(logs.size <= maxEntries)
        assertEquals("Message 9", logs.last().message) // Ostatnia wiadomość powinna być zachowana
    }
    
    @Test
    fun `should get recent logs with limit`() = runTest {
        // Given
        val config = Logger.LoggingConfig(
            minLevel = Logger.LogLevel.DEBUG,
            enableConsole = false,
            enableFile = false,
            enableEvents = false,
            maxLogEntries = 100,
            enableMetrics = false,
            categories = Logger.Category.values().toSet(),
            componentFilters = emptySet(),
            enableStructuredLogging = false
        )
        Logger.configure(config)
        
        repeat(10) { index ->
            Logger.debug(Logger.Category.SMS, "Message $index")
        }
        
        // When
        val recentLogs = Logger.getRecentLogs(5)
        
        // Then
        assertEquals(5, recentLogs.size)
        assertEquals("Message 9", recentLogs[0].message) // Najnowsze na początku
        assertEquals("Message 5", recentLogs[4].message)
    }
    
    @Test
    fun `should create log entry with correct structure`() = runTest {
        // Given
        val config = Logger.LoggingConfig(
            minLevel = Logger.LogLevel.DEBUG,
            enableConsole = false,
            enableFile = false,
            enableEvents = false,
            maxLogEntries = 100,
            enableMetrics = false,
            categories = Logger.Category.values().toSet(),
            componentFilters = emptySet(),
            enableStructuredLogging = false
        )
        Logger.configure(config)
        
        // When
        Logger.debug(Logger.Category.SMS, "Test message", mapOf("key" to "value"))
        
        // Then
        val logs = Logger.getRecentLogs(1)
        assertEquals(1, logs.size)
        
        val logEntry = logs[0]
        assertEquals(Logger.LogLevel.DEBUG, logEntry.level)
        assertEquals(Logger.Category.SMS, logEntry.category)
        assertEquals("Test message", logEntry.message)
        assertEquals(mapOf("key" to "value"), logEntry.metadata)
        assertNotNull(logEntry.timestamp)
        assertNotNull(logEntry.threadName)
        assertNull(logEntry.throwable)
    }
    
    @Test
    fun `should handle structured logging`() = runTest {
        // Given
        val config = Logger.LoggingConfig(
            minLevel = Logger.LogLevel.DEBUG,
            enableConsole = true,
            enableFile = false,
            enableEvents = false,
            maxLogEntries = 100,
            enableMetrics = false,
            categories = Logger.Category.values().toSet(),
            componentFilters = emptySet(),
            enableStructuredLogging = true
        )
        Logger.configure(config)
        
        // When
        Logger.debug(Logger.Category.SMS, "Structured message", mapOf("key" to "value"))
        
        // Then
        val output = testOut.toString()
        assertTrue(output.contains("\"level\":\"DEBUG\""))
        assertTrue(output.contains("\"category\":\"SMS\""))
        assertTrue(output.contains("\"message\":\"Structured message\""))
        assertTrue(output.contains("\"key\":\"value\""))
    }
}