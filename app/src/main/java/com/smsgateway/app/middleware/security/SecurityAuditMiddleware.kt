package com.smsgateway.app.middleware.security

import com.smsgateway.app.models.security.SecurityEventType
import com.smsgateway.app.services.security.SecurityAuditService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Middleware do audytu bezpieczeństwa
 * Monitoruje i rejestruje wszystkie zdarzenia bezpieczeństwa w systemie
 */
class SecurityAuditMiddleware(
    private val securityAuditService: SecurityAuditService
) {
    
    /**
     * Konfiguruje middleware audytu bezpieczeństwa
     */
    fun configure(application: Application) {
        application.intercept(ApplicationCallPipeline.Plugins) {
            val startTime = Instant.now()
            val endpoint = call.request.path()
            val method = call.request.httpMethod.value
            
            try {
                proceed()
            } catch (e: Exception) {
                // Rejestrowanie wyjątków jako zdarzenia bezpieczeństwa
                logSecurityException(call, e, startTime)
                throw e
            } finally {
                // Rejestrowanie zakończenia żądania
                logRequestCompletion(call, startTime)
            }
        }
    }
    
    /**
     * Konfiguruje middleware do monitorowania zdarzeń uwierzytelniania
     */
    fun configureAuthMonitoring(application: Application) {
        application.intercept(ApplicationCallPipeline.Plugins) {
            val endpoint = call.request.path()
            
            // Monitorowanie endpointów uwierzytelniania
            if (endpoint.startsWith("/api/auth/")) {
                val startTime = Instant.now()
                
                try {
                    proceed()
                } catch (e: Exception) {
                    // Rejestrowanie nieudanych prób uwierzytelnienia
                    logAuthFailure(call, e, startTime)
                    throw e
                } finally {
                    // Rejestrowanie prób uwierzytelnienia
                    logAuthAttempt(call, startTime)
                }
            } else {
                proceed()
            }
        }
    }
    
    /**
     * Rejestruje próbę uwierzytelnienia
     */
    private fun logAuthAttempt(call: ApplicationCall, startTime: Instant) {
        try {
            val endpoint = call.request.path()
            val method = call.request.httpMethod.value
            val clientInfo = getClientInfo(call)
            val duration = Instant.now().toEpochMilli() - startTime.toEpochMilli()
            
            val eventType = when (endpoint) {
                "/api/auth/login" -> SecurityEventType.LOGIN_ATTEMPT
                "/api/auth/refresh" -> SecurityEventType.TOKEN_REFRESH_ATTEMPT
                "/api/auth/logout" -> SecurityEventType.LOGOUT_ATTEMPT
                else -> SecurityEventType.AUTH_ATTEMPT
            }
            
            val success = call.response.status() != null && 
                         call.response.status()!!.value < 400
            
            securityAuditService.logAuthEvent(
                eventType = eventType,
                userId = if (call.attributes.contains(AuthenticationMiddleware.AuthenticatedUserKey)) 
                    call.authenticatedUserId else null,
                clientInfo = clientInfo,
                endpoint = endpoint,
                method = method,
                success = success,
                duration = duration,
                details = mapOf(
                    "endpoint" to endpoint,
                    "method" to method,
                    "userAgent" to call.request.headers["User-Agent"]
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Błąd rejestrowania próby uwierzytelnienia" }
        }
    }
    
    /**
     * Rejestruje nieudaną próbę uwierzytelnienia
     */
    private fun logAuthFailure(call: ApplicationCall, exception: Throwable, startTime: Instant) {
        try {
            val endpoint = call.request.path()
            val method = call.request.httpMethod.value
            val clientInfo = getClientInfo(call)
            val duration = Instant.now().toEpochMilli() - startTime.toEpochMilli()
            
            val eventType = when (endpoint) {
                "/api/auth/login" -> SecurityEventType.LOGIN_FAILED
                "/api/auth/refresh" -> SecurityEventType.TOKEN_REFRESH_FAILED
                else -> SecurityEventType.AUTH_FAILED
            }
            
            securityAuditService.logSecurityEvent(
                eventType = eventType,
                severity = "HIGH",
                userId = if (call.attributes.contains(AuthenticationMiddleware.AuthenticatedUserKey)) 
                    call.authenticatedUserId else null,
                clientInfo = clientInfo,
                endpoint = endpoint,
                method = method,
                details = mapOf(
                    "error" to exception.message,
                    "errorType" to exception::class.simpleName,
                    "endpoint" to endpoint,
                    "method" to method,
                    "userAgent" to call.request.headers["User-Agent"]
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Błąd rejestrowania nieudanej próby uwierzytelnienia" }
        }
    }
    
    /**
     * Rejestruje zakończenie żądania
     */
    private fun logRequestCompletion(call: ApplicationCall, startTime: Instant) {
        try {
            val endpoint = call.request.path()
            val method = call.request.httpMethod.value
            val statusCode = call.response.status()?.value ?: 200
            val clientInfo = getClientInfo(call)
            val duration = Instant.now().toEpochMilli() - startTime.toEpochMilli()
            
            // Tylko logujemy żądania do endpointów API
            if (endpoint.startsWith("/api/")) {
                // Rejestruj zdarzenia bezpieczeństwa dla nietypowych statusów
                if (statusCode >= 400) {
                    val eventType = when (statusCode) {
                        in 400..499 -> SecurityEventType.ACCESS_DENIED
                        in 500..599 -> SecurityEventType.SYSTEM_ERROR
                        else -> SecurityEventType.SUSPICIOUS_ACTIVITY
                    }
                    
                    securityAuditService.logSecurityEvent(
                        eventType = eventType,
                        severity = if (statusCode >= 500) "HIGH" else "MEDIUM",
                        userId = if (call.attributes.contains(AuthenticationMiddleware.AuthenticatedUserKey)) 
                            call.authenticatedUserId else null,
                        clientInfo = clientInfo,
                        endpoint = endpoint,
                        method = method,
                        details = mapOf(
                            "statusCode" to statusCode,
                            "duration" to duration,
                            "endpoint" to endpoint,
                            "method" to method
                        )
                    )
                }
                
                // Rejestruj dostęp do wrażliwych endpointów
                if (isSensitiveEndpoint(endpoint)) {
                    securityAuditService.logAccessEvent(
                        eventType = SecurityEventType.SENSITIVE_ACCESS,
                        userId = if (call.attributes.contains(AuthenticationMiddleware.AuthenticatedUserKey)) 
                            call.authenticatedUserId else null,
                        clientInfo = clientInfo,
                        endpoint = endpoint,
                        method = method,
                        success = statusCode < 400,
                        details = mapOf(
                            "statusCode" to statusCode,
                            "duration" to duration,
                            "endpoint" to endpoint,
                            "method" to method
                        )
                    )
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Błąd rejestrowania zakończenia żądania" }
        }
    }
    
    /**
     * Rejestruje wyjątki jako zdarzenia bezpieczeństwa
     */
    private fun logSecurityException(call: ApplicationCall, exception: Throwable, startTime: Instant) {
        try {
            val endpoint = call.request.path()
            val method = call.request.httpMethod.value
            val clientInfo = getClientInfo(call)
            val duration = Instant.now().toEpochMilli() - startTime.toEpochMilli()
            
            // Określenie typu zdarzenia na podstawie wyjątku
            val eventType = when (exception::class.simpleName) {
                "SecurityException" -> SecurityEventType.SECURITY_VIOLATION
                "AuthenticationException" -> SecurityEventType.AUTH_FAILED
                "AuthorizationException" -> SecurityEventType.ACCESS_DENIED
                else -> SecurityEventType.SYSTEM_ERROR
            }
            
            val severity = when (exception::class.simpleName) {
                "SecurityException" -> "HIGH"
                "AuthenticationException" -> "MEDIUM"
                "AuthorizationException" -> "MEDIUM"
                else -> "LOW"
            }
            
            securityAuditService.logSecurityEvent(
                eventType = eventType,
                severity = severity,
                userId = if (call.attributes.contains(AuthenticationMiddleware.AuthenticatedUserKey)) 
                    call.authenticatedUserId else null,
                clientInfo = clientInfo,
                endpoint = endpoint,
                method = method,
                details = mapOf(
                    "error" to exception.message,
                    "errorType" to exception::class.simpleName,
                    "stackTrace" to exception.stackTraceToString(),
                    "duration" to duration,
                    "endpoint" to endpoint,
                    "method" to method
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Błąd rejestrowania wyjątku bezpieczeństwa" }
        }
    }
    
    /**
     * Pobiera informacje o kliencie
     */
    private fun getClientInfo(call: ApplicationCall): Map<String, String> {
        return try {
            mapOf(
                "ip" to (call.request.origin.remoteHost ?: "unknown"),
                "userAgent" to (call.request.headers["User-Agent"] ?: "unknown"),
                "referer" to (call.request.headers["Referer"] ?: "unknown"),
                "userId" to (if (call.attributes.contains(AuthenticationMiddleware.AuthenticatedUserKey)) 
                    call.authenticatedUserId else "anonymous")
            )
        } catch (e: Exception) {
            logger.debug(e) { "Błąd pobierania informacji o kliencie" }
            mapOf("ip" to "unknown", "userAgent" to "unknown")
        }
    }
    
    /**
     * Sprawdza czy endpoint jest wrażliwy
     */
    private fun isSensitiveEndpoint(endpoint: String): Boolean {
        val sensitivePaths = listOf(
            "/api/admin",
            "/api/security",
            "/api/auth/refresh",
            "/api/tokens/create",
            "/api/tunnels",
            "/api/users"
        )
        
        return sensitivePaths.any { endpoint.startsWith(it) }
    }
    
    /**
     * Rejestruje zdarzenie niestandardowe (można wywołać z innych miejsc w kodzie)
     */
    fun logCustomSecurityEvent(
        eventType: SecurityEventType,
        severity: String = "MEDIUM",
        userId: String? = null,
        details: Map<String, Any> = emptyMap()
    ) {
        try {
            securityAuditService.logSecurityEvent(
                eventType = eventType,
                severity = severity,
                userId = userId,
                clientInfo = mapOf("source" to "internal"),
                endpoint = "internal",
                method = "internal",
                details = details
            )
        } catch (e: Exception) {
            logger.error(e) { "Błąd rejestrowania niestandardowego zdarzenia bezpieczeństwa" }
        }
    }
}