package com.smsgateway.app.api.security

import com.smsgateway.app.models.security.SecurityEventType
import com.smsgateway.app.models.security.dto.*
import com.smsgateway.app.services.security.SecurityAuditService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import java.time.Instant
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

/**
 * Kontroler do zarządzania bezpieczeństwem
 * Obsługuje audyt zdarzeń bezpieczeństwa i monitorowanie systemu
 */
class SecurityController(
    private val securityAuditService: SecurityAuditService
) {
    
    /**
     * Pobiera zdarzenia bezpieczeństwa
     */
    suspend fun getSecurityEvents(call: ApplicationCall) {
        try {
            val userId = call.authenticatedUserId
            
            // Sprawdzenie uprawnień
            if (!call.hasPermission("security:view") && !call.hasPermission("admin")) {
                logger.warn { "Użytkownik $userId próbuje przeglądać zdarzenia bezpieczeństwa bez uprawnień" }
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Brak uprawnień do przeglądania zdarzeń bezpieczeństwa")
                )
                return
            }
            
            // Pobranie parametrów zapytania
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
            val eventType = call.request.queryParameters["eventType"]
            val severity = call.request.queryParameters["severity"]
            val startDate = call.request.queryParameters["startDate"]
            val endDate = call.request.queryParameters["endDate"]
            
            // Konwersja dat
            val start = startDate?.let { 
                try { Instant.parse(it) } catch (e: Exception) { null } 
            }
            val end = endDate?.let { 
                try { Instant.parse(it) } catch (e: Exception) { null } 
            }
            
            // Pobranie zdarzeń
            val events = securityAuditService.getSecurityEvents(
                limit = limit.coerceIn(1, 100),
                offset = offset.coerceAtLeast(0),
                eventType = eventType?.let { SecurityEventType.valueOf(it) },
                severity = severity,
                startDate = start,
                endDate = end
            )
            
            val eventResponses = events.map { event ->
                SecurityEventResponse(
                    id = event.id,
                    eventType = event.eventType,
                    severity = event.severity,
                    userId = event.userId,
                    clientInfo = event.clientInfo,
                    endpoint = event.endpoint,
                    method = event.method,
                    details = event.details,
                    timestamp = event.timestamp
                )
            }
            
            call.respond(
                HttpStatusCode.OK,
                mapOf("events" to eventResponses)
            )
        } catch (e: Exception) {
            logger.error(e) { "Błąd pobierania zdarzeń bezpieczeństwa" }
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Błąd pobierania zdarzeń bezpieczeństwa")
            )
        }
    }
    
    /**
     * Pobiera statystyki bezpieczeństwa
     */
    suspend fun getSecurityStats(call: ApplicationCall) {
        try {
            val userId = call.authenticatedUserId
            
            // Sprawdzenie uprawnień
            if (!call.hasPermission("security:view") && !call.hasPermission("admin")) {
                logger.warn { "Użytkownik $userId próbuje przeglądać statystyki bezpieczeństwa bez uprawnień" }
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Brak uprawnień do przeglądania statystyk bezpieczeństwa")
                )
                return
            }
            
            // Pobranie parametrów
            val days = call.request.queryParameters["days"]?.toIntOrNull() ?: 7
            
            // Pobranie statystyk
            val stats = securityAuditService.getSecurityStats(days)
            
            call.respond(
                HttpStatusCode.OK,
                SecurityStatsResponse(
                    totalEvents = stats.totalEvents,
                    criticalEvents = stats.criticalEvents,
                    highSeverityEvents = stats.highSeverityEvents,
                    mediumSeverityEvents = stats.mediumSeverityEvents,
                    lowSeverityEvents = stats.lowSeverityEvents,
                    uniqueUsers = stats.uniqueUsers,
                    uniqueIPs = stats.uniqueIPs,
                    topEndpoints = stats.topEndpoints,
                    eventTypes = stats.eventTypes.map { (type, count) ->
                        EventTypeCount(type.toString(), count)
                    },
                    dailyStats = stats.dailyStats.map { (date, count) ->
                        DailyStat(date.toString(), count)
                    }
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Błąd pobierania statystyk bezpieczeństwa" }
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Błąd pobierania statystyk bezpieczeństwa")
            )
        }
    }
    
    /**
     * Pobiera aktywność użytkownika
     */
    suspend fun getUserActivity(call: ApplicationCall) {
        try {
            val currentUserId = call.authenticatedUserId
            val targetUserId = call.parameters["userId"] ?: currentUserId
            
            // Sprawdzenie uprawnień
            if (targetUserId != currentUserId && !call.hasPermission("admin")) {
                logger.warn { "Użytkownik $currentUserId próbuje przeglądać aktywność użytkownika $targetUserId bez uprawnień" }
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Brak uprawnień do przeglądania aktywności tego użytkownika")
                )
                return
            }
            
            // Pobranie parametrów
            val days = call.request.queryParameters["days"]?.toIntOrNull() ?: 7
            
            // Pobranie aktywności
            val activity = securityAuditService.getUserActivity(targetUserId, days)
            
            val activityResponse = UserActivityResponse(
                userId = targetUserId,
                totalEvents = activity.totalEvents,
                successfulLogins = activity.successfulLogins,
                failedLogins = activity.failedLogins,
                tokenCreations = activity.tokenCreations,
                tunnelOperations = activity.tunnelOperations,
                lastActivity = activity.lastActivity,
                mostUsedEndpoints = activity.mostUsedEndpoints,
                dailyActivity = activity.dailyActivity.map { (date, count) ->
                    DailyActivity(date.toString(), count)
                }
            )
            
            call.respond(
                HttpStatusCode.OK,
                activityResponse
            )
        } catch (e: Exception) {
            logger.error(e) { "Błąd pobierania aktywności użytkownika" }
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Błąd pobierania aktywności użytkownika")
            )
        }
    }
    
    /**
     * Pobiera alerty bezpieczeństwa
     */
    suspend fun getSecurityAlerts(call: ApplicationCall) {
        try {
            val userId = call.authenticatedUserId
            
            // Sprawdzenie uprawnień
            if (!call.hasPermission("security:view") && !call.hasPermission("admin")) {
                logger.warn { "Użytkownik $userId próbuje przeglądać alerty bezpieczeństwa bez uprawnień" }
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Brak uprawnień do przeglądania alertów bezpieczeństwa")
                )
                return
            }
            
            // Pobranie alertów
            val alerts = securityAuditService.getSecurityAlerts()
            
            val alertResponses = alerts.map { alert ->
                SecurityAlertResponse(
                    id = alert.id,
                    type = alert.type,
                    severity = alert.severity,
                    title = alert.title,
                    description = alert.description,
                    userId = alert.userId,
                    timestamp = alert.timestamp,
                    acknowledged = alert.acknowledged,
                    acknowledgedBy = alert.acknowledgedBy,
                    acknowledgedAt = alert.acknowledgedAt
                )
            }
            
            call.respond(
                HttpStatusCode.OK,
                mapOf("alerts" to alertResponses)
            )
        } catch (e: Exception) {
            logger.error(e) { "Błąd pobierania alertów bezpieczeństwa" }
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Błąd pobierania alertów bezpieczeństwa")
            )
        }
    }
    
    /**
     * Potwierdza alert bezpieczeństwa
     */
    suspend fun acknowledgeAlert(call: ApplicationCall) {
        try {
            val alertId = call.parameters["id"]
            val userId = call.authenticatedUserId
            
            if (alertId == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Brak ID alertu")
                )
                return
            }
            
            // Sprawdzenie uprawnień
            if (!call.hasPermission("security:manage") && !call.hasPermission("admin")) {
                logger.warn { "Użytkownik $userId próbuje potwierdzić alert bez uprawnień" }
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Brak uprawnień do potwierdzania alertów")
                )
                return
            }
            
            // Potwierdzenie alertu
            val success = securityAuditService.acknowledgeAlert(alertId, userId)
            
            if (!success) {
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Alert nie znaleziony")
                )
                return
            }
            
            logger.info { "Użytkownik $userId potwierdził alert $alertId" }
            
            call.respond(
                HttpStatusCode.OK,
                mapOf("message" to "Alert został potwierdzony")
            )
        } catch (e: Exception) {
            logger.error(e) { "Błąd potwierdzania alertu" }
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Błąd potwierdzania alertu")
            )
        }
    }
    
    /**
     * Generuje raport bezpieczeństwa
     */
    suspend fun generateSecurityReport(call: ApplicationCall) {
        try {
            val userId = call.authenticatedUserId
            
            // Sprawdzenie uprawnień
            if (!call.hasPermission("security:report") && !call.hasPermission("admin")) {
                logger.warn { "Użytkownik $userId próbuje generować raport bezpieczeństwa bez uprawnień" }
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Brak uprawnień do generowania raportów bezpieczeństwa")
                )
                return
            }
            
            val request = call.receive<GenerateReportRequest>()
            
            // Generowanie raportu
            val report = securityAuditService.generateSecurityReport(
                startDate = request.startDate,
                endDate = request.endDate,
                eventType = request.eventType,
                severity = request.severity,
                format = request.format
            )
            
            call.respond(
                HttpStatusCode.OK,
                SecurityReportResponse(
                    id = report.id,
                    title = report.title,
                    generatedAt = report.generatedAt,
                    period = "${report.startDate} - ${report.endDate}",
                    summary = report.summary,
                    totalEvents = report.totalEvents,
                    criticalEvents = report.criticalEvents,
                    highSeverityEvents = report.highSeverityEvents,
                    recommendations = report.recommendations,
                    downloadUrl = report.downloadUrl
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Błąd generowania raportu bezpieczeństwa" }
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Błąd generowania raportu bezpieczeństwa")
            )
        }
    }
    
    /**
     * Pobiera konfigurację bezpieczeństwa
     */
    suspend fun getSecurityConfig(call: ApplicationCall) {
        try {
            val userId = call.authenticatedUserId
            
            // Sprawdzenie uprawnień
            if (!call.hasPermission("security:config") && !call.hasPermission("admin")) {
                logger.warn { "Użytkownik $userId próbuje przeglądać konfigurację bezpieczeństwa bez uprawnień" }
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Brak uprawnień do przeglądania konfiguracji bezpieczeństwa")
                )
                return
            }
            
            // Pobranie konfiguracji
            val config = securityAuditService.getSecurityConfig()
            
            call.respond(
                HttpStatusCode.OK,
                SecurityConfigResponse(
                    passwordPolicy = config.passwordPolicy,
                    sessionTimeout = config.sessionTimeout,
                    maxLoginAttempts = config.maxLoginAttempts,
                    lockoutDuration = config.lockoutDuration,
                    requireTwoFactor = config.requireTwoFactor,
                    allowedIPs = config.allowedIPs,
                    blockedIPs = config.blockedIPs,
                    logLevel = config.logLevel,
                    retentionDays = config.retentionDays
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Błąd pobierania konfiguracji bezpieczeństwa" }
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Błąd pobierania konfiguracji bezpieczeństwa")
            )
        }
    }
}