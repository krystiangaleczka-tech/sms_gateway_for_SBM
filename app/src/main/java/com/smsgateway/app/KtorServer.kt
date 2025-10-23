package com.smsgateway.app

import com.smsgateway.app.plugins.*
import com.smsgateway.app.routes.queueRoutes
import com.smsgateway.app.routes.smsRoutes
import com.smsgateway.app.routes.loggingRoutes
import com.smsgateway.app.routes.errorRoutes
import com.smsgateway.app.queue.SmsQueueService
import com.smsgateway.app.health.HealthChecker
import com.smsgateway.app.events.MetricsCollector
import com.smsgateway.app.retry.RetryService
import com.smsgateway.app.database.SmsRepository
import com.smsgateway.app.database.AppDatabase
import com.smsgateway.app.logging.Logger
import com.smsgateway.app.logging.LogManager
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // Inicjalizacja systemu logowania
    initializeLogging()
    
    // Inicjalizacja bazy danych
    val database = AppDatabase.getDatabase(this)
    val smsRepository = SmsRepository(database.smsDao())
    
    // Inicjalizacja komponentów systemu kolejkowania
    val healthChecker = HealthChecker(this)
    val metricsCollector = MetricsCollector()
    val retryService = RetryService()
    val smsQueueService = SmsQueueService(smsRepository, retryService, metricsCollector, healthChecker)
    
    // Konfiguracja wtyczek
    configureCORS()
    configureAuthentication()
    configureRequestValidation()
    configureStatusPages()
    configureSerialization()
    configureMonitoring()
    
    // Konfiguracja routingów
    routing {
        smsRoutes(smsRepository, smsQueueService)
        queueRoutes(smsQueueService, healthChecker, metricsCollector, retryService)
        loggingRoutes()
        errorRoutes()
    }
    
    // Logowanie startu serwera
    Logger.system(Logger.LogLevel.INFO, "SMSGateway server started successfully", mapOf(
        "port" to 8080,
        "host" to "0.0.0.0",
        "version" to "1.0.0"
    ))
}

/**
 * Inicjalizuje system logowania z odpowiednią konfiguracją
 */
fun Application.initializeLogging() {
    try {
        // Konfiguracja Logger
        val loggerConfig = Logger.LoggingConfig(
            minLevel = Logger.LogLevel.DEBUG,
            enableConsole = true,
            enableFile = true,
            enableEvents = true,
            maxLogEntries = 10000,
            enableMetrics = true,
            categories = Logger.Category.values().toSet(),
            componentFilters = emptySet(),
            enableStructuredLogging = true
        )
        Logger.configure(loggerConfig)
        
        // Konfiguracja LogManager
        val logManagerConfig = LogManager.LogManagerConfig(
            enableFileLogging = true,
            logDirectory = "logs",
            maxFileSizeBytes = 10 * 1024 * 1024, // 10MB
            maxFiles = 5,
            retentionDays = 30,
            enableAutoCleanup = true,
            enableCompression = true,
            enableLogAnalysis = true,
            exportFormats = setOf(
                LogManager.ExportFormat.JSON,
                LogManager.ExportFormat.CSV,
                LogManager.ExportFormat.TXT
            )
        )
        
        // Inicjalizacja LogManager
        LogManager.initialize(logManagerConfig)
        
        Logger.system(Logger.LogLevel.INFO, "Logging system initialized successfully", mapOf(
            "loggerMinLevel" to loggerConfig.minLevel.displayName,
            "enableFileLogging" to logManagerConfig.enableFileLogging,
            "logDirectory" to logManagerConfig.logDirectory
        ))
    } catch (e: Exception) {
        // Fallback do podstawowego logowania jeśli inicjalizacja się nie powiedzie
        println("Failed to initialize logging system: ${e.message}")
        e.printStackTrace()
    }
}
