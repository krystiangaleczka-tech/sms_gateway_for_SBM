package com.smsgateway.app.routes

import com.smsgateway.app.logging.LogManager
import com.smsgateway.app.logging.Logger
import com.smsgateway.app.logging.LogManager.LogFilter
import com.smsgateway.app.logging.LogManager.ExportFormat
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.toList
import java.io.File

/**
 * Endpointy API do zarządzania logami
 * Umożliwiają przeglądanie, filtrowanie i eksportowanie logów systemowych
 */
fun Route.loggingRoutes() {
    
    route("/api/v1/logs") {
        
        // Pobieranie statystyk logowania
        get("/stats") {
            try {
                Logger.api(Logger.LogLevel.INFO, "GET /api/v1/logs/stats", "Fetching logging statistics")
                
                val stats = Logger.getStats()
                val logManagerStats = LogManager.analyzeLogs()
                
                val response = mapOf(
                    "loggerStats" to mapOf(
                        "totalLogs" to stats.getTotalLogs(),
                        "logsByLevel" to Logger.LogLevel.values().associate { level ->
                            level.displayName to stats.getLogsByLevel(level)
                        },
                        "logsByCategory" to Logger.Category.values().associate { category ->
                            category.value to stats.getLogsByCategory(category)
                        },
                        "logsByComponent" to stats.logsByComponent.mapValues { it.value.get() },
                        "errorRate" to stats.getErrorRate(),
                        "lastLogTime" to stats.lastLogTime.get()
                    ),
                    "analysisStats" to mapOf(
                        "totalLogs" to logManagerStats.totalLogs,
                        "logsByLevel" to logManagerStats.logsByLevel.mapKeys { it.key.displayName },
                        "logsByCategory" to logManagerStats.logsByCategory.mapKeys { it.key.value },
                        "logsByComponent" to logManagerStats.logsByComponent,
                        "logsByHour" to logManagerStats.logsByHour,
                        "errorPatterns" to logManagerStats.errorPatterns,
                        "performanceMetrics" to logManagerStats.performanceMetrics,
                        "generatedAt" to logManagerStats.generatedAt
                    )
                )
                
                call.respond(response)
            } catch (e: Exception) {
                Logger.error(Logger.Category.API, "Failed to fetch logging statistics", e)
                call.respond(mapOf("error" to "Failed to fetch statistics: ${e.message}"))
            }
        }
        
        // Pobieranie ostatnich logów
        get("/recent") {
            try {
                val count = call.request.queryParameters["count"]?.toIntOrNull() ?: 100
                val level = call.request.queryParameters["level"]?.let { 
                    Logger.LogLevel.values().find { level -> level.displayName.equals(it, ignoreCase = true) }
                }
                val category = call.request.queryParameters["category"]?.let { 
                    Logger.Category.values().find { cat -> cat.value.equals(it, ignoreCase = true) }
                }
                val component = call.request.queryParameters["component"]
                val messagePattern = call.request.queryParameters["search"]
                
                Logger.api(Logger.LogLevel.INFO, "GET /api/v1/logs/recent", "Fetching recent logs", mapOf(
                    "count" to count,
                    "level" to level?.displayName,
                    "category" to category?.value,
                    "component" to component,
                    "search" to messagePattern
                ))
                
                val filter = LogFilter(
                    levels = level?.let { setOf(it) } ?: Logger.LogLevel.values().toSet(),
                    categories = category?.let { setOf(it) } ?: Logger.Category.values().toSet(),
                    components = component?.let { setOf(it) } ?: emptySet(),
                    messagePattern = messagePattern
                )
                
                val logs = LogManager.getFilteredLogs(filter, count)
                val response = logs.map { entry ->
                    mapOf(
                        "timestamp" to entry.timestamp,
                        "level" to entry.level.displayName,
                        "category" to entry.category.value,
                        "component" to entry.component,
                        "message" to entry.message,
                        "thread" to entry.threadName,
                        "metadata" to entry.metadata,
                        "messageId" to entry.messageId,
                        "throwable" to entry.throwable?.let { throwable ->
                            mapOf(
                                "type" to throwable.javaClass.simpleName,
                                "message" to throwable.message
                            )
                        }
                    )
                }
                
                call.respond(mapOf(
                    "logs" to response,
                    "total" to response.size,
                    "filter" to mapOf(
                        "level" to level?.displayName,
                        "category" to category?.value,
                        "component" to component,
                        "search" to messagePattern
                    )
                ))
            } catch (e: Exception) {
                Logger.error(Logger.Category.API, "Failed to fetch recent logs", e)
                call.respond(mapOf("error" to "Failed to fetch logs: ${e.message}"))
            }
        }
        
        // Wyszukiwanie zaawansowane logów
        post("/search") {
            try {
                val request = call.receive<Map<String, Any>>()
                
                val levels = (request["levels"] as? List<String>)?.mapNotNull { levelStr ->
                    Logger.LogLevel.values().find { it.displayName.equals(levelStr, ignoreCase = true) }
                }?.toSet() ?: Logger.LogLevel.values().toSet()
                
                val categories = (request["categories"] as? List<String>)?.mapNotNull { catStr ->
                    Logger.Category.values().find { it.value.equals(catStr, ignoreCase = true) }
                }?.toSet() ?: Logger.Category.values().toSet()
                
                val components = (request["components"] as? List<String>)?.toSet() ?: emptySet()
                val messagePattern = request["messagePattern"] as? String
                val startTime = (request["startTime"] as? String)?.toLongOrNull()
                val endTime = (request["endTime"] as? String)?.toLongOrNull()
                val includeExceptions = request["includeExceptions"] as? Boolean ?: true
                val excludeExceptions = request["excludeExceptions"] as? Boolean ?: false
                val limit = (request["limit"] as? String)?.toIntOrNull() ?: 1000
                
                Logger.api(Logger.LogLevel.INFO, "POST /api/v1/logs/search", "Advanced log search", mapOf(
                    "levels" to levels.map { it.displayName },
                    "categories" to categories.map { it.value },
                    "components" to components,
                    "messagePattern" to messagePattern,
                    "startTime" to startTime,
                    "endTime" to endTime,
                    "limit" to limit
                ))
                
                val filter = LogFilter(
                    levels = levels,
                    categories = categories,
                    components = components,
                    messagePattern = messagePattern,
                    startTime = startTime,
                    endTime = endTime,
                    includeExceptions = includeExceptions,
                    excludeExceptions = excludeExceptions
                )
                
                val logs = LogManager.getFilteredLogs(filter, limit)
                val response = logs.map { entry ->
                    mapOf(
                        "timestamp" to entry.timestamp,
                        "level" to entry.level.displayName,
                        "category" to entry.category.value,
                        "component" to entry.component,
                        "message" to entry.message,
                        "thread" to entry.threadName,
                        "metadata" to entry.metadata,
                        "messageId" to entry.messageId,
                        "throwable" to entry.throwable?.let { throwable ->
                            mapOf(
                                "type" to throwable.javaClass.simpleName,
                                "message" to throwable.message,
                                "stackTrace" to throwable.stackTraceToString()
                            )
                        }
                    )
                }
                
                call.respond(mapOf(
                    "logs" to response,
                    "total" to response.size,
                    "filter" to mapOf(
                        "levels" to levels.map { it.displayName },
                        "categories" to categories.map { it.value },
                        "components" to components,
                        "messagePattern" to messagePattern,
                        "startTime" to startTime,
                        "endTime" to endTime
                    )
                ))
            } catch (e: Exception) {
                Logger.error(Logger.Category.API, "Failed to search logs", e)
                call.respond(mapOf("error" to "Failed to search logs: ${e.message}"))
            }
        }
        
        // Eksportowanie logów
        post("/export") {
            try {
                val request = call.receive<Map<String, Any>>()
                
                val formatStr = request["format"] as? String ?: "json"
                val format = ExportFormat.values().find { 
                    it.extension.equals(formatStr, ignoreCase = true) 
                } ?: ExportFormat.JSON
                
                // Tworzenie filtra na podstawie żądania
                val levels = (request["levels"] as? List<String>)?.mapNotNull { levelStr ->
                    Logger.LogLevel.values().find { it.displayName.equals(levelStr, ignoreCase = true) }
                }?.toSet() ?: Logger.LogLevel.values().toSet()
                
                val categories = (request["categories"] as? List<String>)?.mapNotNull { catStr ->
                    Logger.Category.values().find { it.value.equals(catStr, ignoreCase = true) }
                }?.toSet() ?: Logger.Category.values().toSet()
                
                val components = (request["components"] as? List<String>)?.toSet() ?: emptySet()
                val messagePattern = request["messagePattern"] as? String
                val startTime = (request["startTime"] as? String)?.toLongOrNull()
                val endTime = (request["endTime"] as? String)?.toLongOrNull()
                
                val filter = LogFilter(
                    levels = levels,
                    categories = categories,
                    components = components,
                    messagePattern = messagePattern,
                    startTime = startTime,
                    endTime = endTime
                )
                
                Logger.api(Logger.LogLevel.INFO, "POST /api/v1/logs/export", "Exporting logs", mapOf(
                    "format" to format.extension,
                    "levels" to levels.map { it.displayName },
                    "categories" to categories.map { it.value }
                ))
                
                // Eksport logów
                val exportFile = LogManager.exportLogs(filter, format)
                
                call.response.header(
                    "Content-Disposition", 
                    "attachment; filename=\"${exportFile.name}\""
                )
                call.response.header(
                    "Content-Type", 
                    format.mimeType
                )
                
                call.respondFile(exportFile)
            } catch (e: Exception) {
                Logger.error(Logger.Category.API, "Failed to export logs", e)
                call.respond(mapOf("error" to "Failed to export logs: ${e.message}"))
            }
        }
        
        // Analiza logów
        post("/analyze") {
            try {
                val request = call.receive<Map<String, Any>>()
                
                // Tworzenie filtra na podstawie żądania
                val levels = (request["levels"] as? List<String>)?.mapNotNull { levelStr ->
                    Logger.LogLevel.values().find { it.displayName.equals(levelStr, ignoreCase = true) }
                }?.toSet() ?: Logger.LogLevel.values().toSet()
                
                val categories = (request["categories"] as? List<String>)?.mapNotNull { catStr ->
                    Logger.Category.values().find { it.value.equals(catStr, ignoreCase = true) }
                }?.toSet() ?: Logger.Category.values().toSet()
                
                val components = (request["components"] as? List<String>)?.toSet() ?: emptySet()
                val messagePattern = request["messagePattern"] as? String
                val startTime = (request["startTime"] as? String)?.toLongOrNull()
                val endTime = (request["endTime"] as? String)?.toLongOrNull()
                val forceRefresh = request["forceRefresh"] as? Boolean ?: false
                
                val filter = LogFilter(
                    levels = levels,
                    categories = categories,
                    components = components,
                    messagePattern = messagePattern,
                    startTime = startTime,
                    endTime = endTime
                )
                
                Logger.api(Logger.LogLevel.INFO, "POST /api/v1/logs/analyze", "Analyzing logs", mapOf(
                    "forceRefresh" to forceRefresh,
                    "levels" to levels.map { it.displayName },
                    "categories" to categories.map { it.value }
                ))
                
                val analysis = LogManager.analyzeLogs(filter, forceRefresh)
                
                val response = mapOf(
                    "totalLogs" to analysis.totalLogs,
                    "logsByLevel" to analysis.logsByLevel.mapKeys { it.key.displayName },
                    "logsByCategory" to analysis.logsByCategory.mapKeys { it.key.value },
                    "logsByComponent" to analysis.logsByComponent,
                    "logsByHour" to analysis.logsByHour,
                    "errorPatterns" to analysis.errorPatterns,
                    "topErrors" to analysis.topErrors.map { entry ->
                        mapOf(
                            "timestamp" to entry.timestamp,
                            "level" to entry.level.displayName,
                            "category" to entry.category.value,
                            "component" to entry.component,
                            "message" to entry.message,
                            "throwable" to entry.throwable?.let { throwable ->
                                mapOf(
                                    "type" to throwable.javaClass.simpleName,
                                    "message" to throwable.message
                                )
                            }
                        )
                    },
                    "performanceMetrics" to analysis.performanceMetrics,
                    "generatedAt" to analysis.generatedAt
                )
                
                call.respond(response)
            } catch (e: Exception) {
                Logger.error(Logger.Category.API, "Failed to analyze logs", e)
                call.respond(mapOf("error" to "Failed to analyze logs: ${e.message}"))
            }
        }
        
        // Czyszczenie starych logów
        post("/cleanup") {
            try {
                Logger.api(Logger.LogLevel.INFO, "POST /api/v1/logs/cleanup", "Cleaning up old logs")
                
                LogManager.cleanupOldLogs()
                
                call.respond(mapOf(
                    "message" to "Log cleanup completed successfully",
                    "timestamp" to System.currentTimeMillis()
                ))
            } catch (e: Exception) {
                Logger.error(Logger.Category.API, "Failed to cleanup logs", e)
                call.respond(mapOf("error" to "Failed to cleanup logs: ${e.message}"))
            }
        }
        
        // Pobieranie konfiguracji logowania
        get("/config") {
            try {
                Logger.api(Logger.LogLevel.INFO, "GET /api/v1/logs/config", "Fetching logging configuration")
                
                val loggerConfig = Logger.getConfig()
                val logManagerConfig = LogManager.getConfig()
                
                val response = mapOf(
                    "logger" to mapOf(
                        "minLevel" to loggerConfig.minLevel.displayName,
                        "enableConsole" to loggerConfig.enableConsole,
                        "enableFile" to loggerConfig.enableFile,
                        "enableEvents" to loggerConfig.enableEvents,
                        "maxLogEntries" to loggerConfig.maxLogEntries,
                        "enableMetrics" to loggerConfig.enableMetrics,
                        "categories" to loggerConfig.categories.map { it.value },
                        "componentFilters" to loggerConfig.componentFilters,
                        "enableStructuredLogging" to loggerConfig.enableStructuredLogging
                    ),
                    "logManager" to mapOf(
                        "enableFileLogging" to logManagerConfig.enableFileLogging,
                        "logDirectory" to logManagerConfig.logDirectory,
                        "maxFileSizeBytes" to logManagerConfig.maxFileSizeBytes,
                        "maxFiles" to logManagerConfig.maxFiles,
                        "retentionDays" to logManagerConfig.retentionDays,
                        "enableAutoCleanup" to logManagerConfig.enableAutoCleanup,
                        "enableCompression" to logManagerConfig.enableCompression,
                        "enableLogAnalysis" to logManagerConfig.enableLogAnalysis,
                        "exportFormats" to logManagerConfig.exportFormats.map { it.extension }
                    )
                )
                
                call.respond(response)
            } catch (e: Exception) {
                Logger.error(Logger.Category.API, "Failed to fetch logging configuration", e)
                call.respond(mapOf("error" to "Failed to fetch configuration: ${e.message}"))
            }
        }
        
        // Aktualizacja konfiguracji logowania
        put("/config") {
            try {
                val request = call.receive<Map<String, Any>>()
                
                Logger.api(Logger.LogLevel.INFO, "PUT /api/v1/logs/config", "Updating logging configuration")
                
                // Aktualizacja konfiguracji Logger
                val loggerConfig = request["logger"] as? Map<String, Any>
                if (loggerConfig != null) {
                    val minLevel = (loggerConfig["minLevel"] as? String)?.let { levelStr ->
                        Logger.LogLevel.values().find { it.displayName.equals(levelStr, ignoreCase = true) }
                    } ?: Logger.getConfig().minLevel
                    
                    val newLoggerConfig = Logger.LoggingConfig(
                        minLevel = minLevel,
                        enableConsole = loggerConfig["enableConsole"] as? Boolean ?: Logger.getConfig().enableConsole,
                        enableFile = loggerConfig["enableFile"] as? Boolean ?: Logger.getConfig().enableFile,
                        enableEvents = loggerConfig["enableEvents"] as? Boolean ?: Logger.getConfig().enableEvents,
                        maxLogEntries = loggerConfig["maxLogEntries"] as? Int ?: Logger.getConfig().maxLogEntries,
                        enableMetrics = loggerConfig["enableMetrics"] as? Boolean ?: Logger.getConfig().enableMetrics,
                        categories = (loggerConfig["categories"] as? List<String>)?.mapNotNull { catStr ->
                            Logger.Category.values().find { it.value.equals(catStr, ignoreCase = true) }
                        }?.toSet() ?: Logger.getConfig().categories,
                        componentFilters = (loggerConfig["componentFilters"] as? List<String>)?.toSet() ?: Logger.getConfig().componentFilters,
                        enableStructuredLogging = loggerConfig["enableStructuredLogging"] as? Boolean ?: Logger.getConfig().enableStructuredLogging
                    )
                    
                    Logger.configure(newLoggerConfig)
                }
                
                // Aktualizacja konfiguracji LogManager
                val logManagerConfig = request["logManager"] as? Map<String, Any>
                if (logManagerConfig != null) {
                    val exportFormats = (logManagerConfig["exportFormats"] as? List<String>)?.mapNotNull { formatStr ->
                        ExportFormat.values().find { it.extension.equals(formatStr, ignoreCase = true) }
                    }?.toSet() ?: LogManager.getConfig().exportFormats
                    
                    val newLogManagerConfig = LogManager.LogManagerConfig(
                        enableFileLogging = logManagerConfig["enableFileLogging"] as? Boolean ?: LogManager.getConfig().enableFileLogging,
                        logDirectory = logManagerConfig["logDirectory"] as? String ?: LogManager.getConfig().logDirectory,
                        maxFileSizeBytes = (logManagerConfig["maxFileSizeBytes"] as? String)?.toLongOrNull() ?: LogManager.getConfig().maxFileSizeBytes,
                        maxFiles = logManagerConfig["maxFiles"] as? Int ?: LogManager.getConfig().maxFiles,
                        retentionDays = logManagerConfig["retentionDays"] as? Int ?: LogManager.getConfig().retentionDays,
                        enableAutoCleanup = logManagerConfig["enableAutoCleanup"] as? Boolean ?: LogManager.getConfig().enableAutoCleanup,
                        enableCompression = logManagerConfig["enableCompression"] as? Boolean ?: LogManager.getConfig().enableCompression,
                        enableLogAnalysis = logManagerConfig["enableLogAnalysis"] as? Boolean ?: LogManager.getConfig().enableLogAnalysis,
                        exportFormats = exportFormats
                    )
                    
                    LogManager.configure(newLogManagerConfig)
                }
                
                call.respond(mapOf(
                    "message" to "Logging configuration updated successfully",
                    "timestamp" to System.currentTimeMillis()
                ))
            } catch (e: Exception) {
                Logger.error(Logger.Category.API, "Failed to update logging configuration", e)
                call.respond(mapOf("error" to "Failed to update configuration: ${e.message}"))
            }
        }
        
        // Pobieranie dostępnych formatów eksportu
        get("/export/formats") {
            try {
                Logger.api(Logger.LogLevel.INFO, "GET /api/v1/logs/export/formats", "Fetching export formats")
                
                val formats = ExportFormat.values().map { format ->
                    mapOf(
                        "name" to format.name,
                        "extension" to format.extension,
                        "mimeType" to format.mimeType
                    )
                }
                
                call.respond(mapOf(
                    "formats" to formats
                ))
            } catch (e: Exception) {
                Logger.error(Logger.Category.API, "Failed to fetch export formats", e)
                call.respond(mapOf("error" to "Failed to fetch export formats: ${e.message}"))
            }
        }
        
        // Pobieranie dostępnych poziomów logowania
        get("/levels") {
            try {
                Logger.api(Logger.LogLevel.INFO, "GET /api/v1/logs/levels", "Fetching log levels")
                
                val levels = Logger.LogLevel.values().map { level ->
                    mapOf(
                        "name" to level.name,
                        "displayName" to level.displayName,
                        "value" to level.value
                    )
                }.sortedBy { it["value"] as Int }
                
                call.respond(mapOf(
                    "levels" to levels
                ))
            } catch (e: Exception) {
                Logger.error(Logger.Category.API, "Failed to fetch log levels", e)
                call.respond(mapOf("error" to "Failed to fetch log levels: ${e.message}"))
            }
        }
        
        // Pobieranie dostępnych kategorii logowania
        get("/categories") {
            try {
                Logger.api(Logger.LogLevel.INFO, "GET /api/v1/logs/categories", "Fetching log categories")
                
                val categories = Logger.Category.values().map { category ->
                    mapOf(
                        "name" to category.name,
                        "value" to category.value
                    )
                }
                
                call.respond(mapOf(
                    "categories" to categories
                ))
            } catch (e: Exception) {
                Logger.error(Logger.Category.API, "Failed to fetch log categories", e)
                call.respond(mapOf("error" to "Failed to fetch log categories: ${e.message}"))
            }
        }
    }
}