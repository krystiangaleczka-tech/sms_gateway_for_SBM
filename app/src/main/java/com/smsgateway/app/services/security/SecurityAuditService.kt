package com.smsgateway.app.services.security

import com.smsgateway.app.events.EventPublisher
import com.smsgateway.app.models.security.SecurityEvent
import com.smsgateway.app.models.security.SecurityEventType
import com.smsgateway.app.models.security.dto.SecurityEventRequest
import com.smsgateway.app.models.security.dto.SecurityEventResponse
import com.smsgateway.app.models.security.dto.SecurityStatsResponse
import com.smsgateway.app.repositories.SecurityEventRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant
import java.time.temporal.ChronoUnit

private val logger = KotlinLogging.logger {}

/**
 * Serwis zarządzający audytem bezpieczeństwa
 * Odpowiedzialny za zbieranie, analizę i raportowanie zdarzeń bezpieczeństwa
 */
class SecurityAuditService(
    private val securityEventRepository: SecurityEventRepository,
    private val eventPublisher: EventPublisher
) {
    
    /**
     * Zapisuje zdarzenie bezpieczeństwa
     * @param request Dane zdarzenia bezpieczeństwa
     * @return Zapisane zdarzenie
     */
    suspend fun logSecurityEvent(request: SecurityEventRequest): SecurityEventResponse {
        logger.debug { "Zapisywanie zdarzenia bezpieczeństwa: ${request.type}" }
        
        val event = SecurityEvent(
            id = java.util.UUID.randomUUID().toString(),
            type = request.type,
            severity = request.severity,
            userId = request.userId,
            clientId = request.clientId,
            ipAddress = request.ipAddress,
            userAgent = request.userAgent,
            endpoint = request.endpoint,
            method = request.method,
            statusCode = request.statusCode,
            message = request.message,
            details = request.details,
            timestamp = Instant.now()
        )
        
        val savedEvent = securityEventRepository.save(event)
        
        // Publikacja zdarzenia do systemu powiadomień
        eventPublisher.publishSecurityEvent(savedEvent)
        
        // Sprawdzenie czy zdarzenie wymaga natychmiastowej akcji
        if (requiresImmediateAction(savedEvent)) {
            handleCriticalSecurityEvent(savedEvent)
        }
        
        logger.info { "Zapisano zdarzenie bezpieczeństwa: ${savedEvent.id}" }
        
        return SecurityEventResponse(
            id = savedEvent.id,
            type = savedEvent.type,
            severity = savedEvent.severity,
            userId = savedEvent.userId,
            clientId = savedEvent.clientId,
            ipAddress = savedEvent.ipAddress,
            endpoint = savedEvent.endpoint,
            method = savedEvent.method,
            statusCode = savedEvent.statusCode,
            message = savedEvent.message,
            timestamp = savedEvent.timestamp
        )
    }
    
    /**
     * Pobiera zdarzenia bezpieczeństwa z filtrowaniem
     * @param userId ID użytkownika (opcjonalny)
     * @param clientId ID klienta (opcjonalny)
     * @param type Typ zdarzenia (opcjonalny)
     * @param severity Poziom zagrożenia (opcjonalny)
     * @param from Data początkowa (opcjonalna)
     * @param to Data końcowa (opcjonalna)
     * @param limit Maksymalna liczba wyników
     * @return Lista zdarzeń bezpieczeństwa
     */
    suspend fun getSecurityEvents(
        userId: String? = null,
        clientId: String? = null,
        type: SecurityEventType? = null,
        severity: String? = null,
        from: Instant? = null,
        to: Instant? = null,
        limit: Int = 100
    ): List<SecurityEventResponse> {
        logger.debug { "Pobieranie zdarzeń bezpieczeństwa z filtrami" }
        
        val events = securityEventRepository.findByFilters(
            userId = userId,
            clientId = clientId,
            type = type,
            severity = severity,
            from = from,
            to = to,
            limit = limit
        )
        
        return events.map { event ->
            SecurityEventResponse(
                id = event.id,
                type = event.type,
                severity = event.severity,
                userId = event.userId,
                clientId = event.clientId,
                ipAddress = event.ipAddress,
                endpoint = event.endpoint,
                method = event.method,
                statusCode = event.statusCode,
                message = event.message,
                timestamp = event.timestamp
            )
        }
    }
    
    /**
     * Pobiera statystyki bezpieczeństwa
     * @param from Data początkowa (opcjonalna)
     * @param to Data końcowa (opcjonalna)
     * @return Statystyki bezpieczeństwa
     */
    suspend fun getSecurityStats(
        from: Instant? = null,
        to: Instant? = null
    ): SecurityStatsResponse {
        logger.debug { "Pobieranie statystyk bezpieczeństwa" }
        
        val defaultFrom = from ?: Instant.now().minus(24, ChronoUnit.HOURS)
        val defaultTo = to ?: Instant.now()
        
        val totalEvents = securityEventRepository.countByDateRange(defaultFrom, defaultTo)
        val criticalEvents = securityEventRepository.countBySeverityAndDateRange("CRITICAL", defaultFrom, defaultTo)
        val warningEvents = securityEventRepository.countBySeverityAndDateRange("WARNING", defaultFrom, defaultTo)
        val infoEvents = securityEventRepository.countBySeverityAndDateRange("INFO", defaultFrom, defaultTo)
        
        val eventsByType = mutableMapOf<SecurityEventType, Long>()
        SecurityEventType.values().forEach { type ->
            eventsByType[type] = securityEventRepository.countByTypeAndDateRange(type, defaultFrom, defaultTo)
        }
        
        val topIPs = securityEventRepository.findTopIPsByDateRange(defaultFrom, defaultTo, 10)
        val topUsers = securityEventRepository.findTopUsersByDateRange(defaultFrom, defaultTo, 10)
        
        return SecurityStatsResponse(
            totalEvents = totalEvents,
            criticalEvents = criticalEvents,
            warningEvents = warningEvents,
            infoEvents = infoEvents,
            eventsByType = eventsByType,
            topIPs = topIPs,
            topUsers = topUsers,
            from = defaultFrom,
            to = defaultTo
        )
    }
    
    /**
     * Pobiera ostatnie zdarzenia dla użytkownika
     * @param userId ID użytkownika
     * @param limit Maksymalna liczba wyników
     * @return Lista zdarzeń użytkownika
     */
    suspend fun getUserSecurityEvents(userId: String, limit: Int = 50): List<SecurityEventResponse> {
        logger.debug { "Pobieranie zdarzeń bezpieczeństwa dla użytkownika: $userId" }
        
        val events = securityEventRepository.findByUserId(userId, limit)
        
        return events.map { event ->
            SecurityEventResponse(
                id = event.id,
                type = event.type,
                severity = event.severity,
                userId = event.userId,
                clientId = event.clientId,
                ipAddress = event.ipAddress,
                endpoint = event.endpoint,
                method = event.method,
                statusCode = event.statusCode,
                message = event.message,
                timestamp = event.timestamp
            )
        }
    }
    
    /**
     * Pobiera ostatnie zdarzenia dla klienta
     * @param clientId ID klienta
     * @param limit Maksymalna liczba wyników
     * @return Lista zdarzeń klienta
     */
    suspend fun getClientSecurityEvents(clientId: String, limit: Int = 50): List<SecurityEventResponse> {
        logger.debug { "Pobieranie zdarzeń bezpieczeństwa dla klienta: $clientId" }
        
        val events = securityEventRepository.findByClientId(clientId, limit)
        
        return events.map { event ->
            SecurityEventResponse(
                id = event.id,
                type = event.type,
                severity = event.severity,
                userId = event.userId,
                clientId = event.clientId,
                ipAddress = event.ipAddress,
                endpoint = event.endpoint,
                method = event.method,
                statusCode = event.statusCode,
                message = event.message,
                timestamp = event.timestamp
            )
        }
    }
    
    /**
     * Czyści stare zdarzenia bezpieczeństwa
     * @param olderThan Czas graniczny
     * @return Liczba usuniętych zdarzeń
     */
    suspend fun cleanupOldEvents(olderThan: Instant = Instant.now().minus(90, ChronoUnit.DAYS)): Int {
        logger.info { "Czyszczenie starych zdarzeń bezpieczeństwa starszych niż: $olderThan" }
        return securityEventRepository.deleteOlderThan(olderThan)
    }
    
    /**
     * Sprawdza czy zdarzenie wymaga natychmiastowej akcji
     * @param event Zdarzenie bezpieczeństwa
     * @return True jeśli wymaga natychmiastowej akcji
     */
    private fun requiresImmediateAction(event: SecurityEvent): Boolean {
        return event.severity == "CRITICAL" || 
               event.type == SecurityEventType.BLOCKED_IP ||
               event.type == SecurityEventType.BRUTE_FORCE_DETECTED ||
               event.type == SecurityEventType.SUSPICIOUS_PATTERN
    }
    
    /**
     * Obsługuje krytyczne zdarzenia bezpieczeństwa
     * @param event Krytyczne zdarzenie bezpieczeństwa
     */
    private suspend fun handleCriticalSecurityEvent(event: SecurityEvent) {
        logger.warn { "Obsługa krytycznego zdarzenia bezpieczeństwa: ${event.id}" }
        
        when (event.type) {
            SecurityEventType.BLOCKED_IP -> {
                // Logika blokady IP
                logger.warn { "Zablokowano IP: ${event.ipAddress}" }
            }
            
            SecurityEventType.BRUTE_FORCE_DETECTED -> {
                // Logika obsługi ataku brute force
                logger.warn { "Wykryto atak brute force z IP: ${event.ipAddress}" }
            }
            
            SecurityEventType.SUSPICIOUS_PATTERN -> {
                // Logika obsługi podejrzanego wzorca
                logger.warn { "Wykryto podejrzany wzorzec od użytkownika: ${event.userId}" }
            }
            
            else -> {
                logger.warn { "Nieznany typ krytycznego zdarzenia: ${event.type}" }
            }
        }
        
        // Tutaj można dodać dodatkowe akcje, np. wysłanie powiadomienia email,
        // zablokowanie konta, powiadomienie administratora, etc.
    }
}