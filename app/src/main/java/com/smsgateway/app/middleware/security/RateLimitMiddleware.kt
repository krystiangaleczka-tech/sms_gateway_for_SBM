package com.smsgateway.app.middleware.security

import com.smsgateway.app.models.security.RateLimitType
import com.smsgateway.app.services.security.RateLimitService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Middleware do limitowania żądań API
 * Chroni przed nadużyciami i atakami typu DoS
 */
class RateLimitMiddleware(
    private val rateLimitService: RateLimitService
) {
    
    /**
     * Konfiguruje middleware limitowania żądań
     */
    fun configure(application: Application) {
        application.intercept(ApplicationCallPipeline.Plugins) {
            // Pobranie identyfikatora klienta
            val clientId = getClientIdentifier(call)
            val endpoint = call.request.path()
            val method = call.request.httpMethod.value
            
            // Sprawdzenie limitu żądań
            val isAllowed = try {
                rateLimitService.checkRateLimit(
                    clientId = clientId,
                    endpoint = endpoint,
                    method = method,
                    type = RateLimitType.API_REQUEST
                )
            } catch (e: Exception) {
                logger.error(e) { "Błąd sprawdzania limitu żądań dla klienta: $clientId" }
                // W przypadku błędu, zezwalamy na żądanie (fail-safe)
                true
            }
            
            if (!isAllowed) {
                logger.warn { "Limit żądań przekroczony dla klienta: $clientId, endpoint: $endpoint" }
                
                // Pobranie informacji o limicie
                val rateLimitInfo = try {
                    rateLimitService.getRateLimitInfo(clientId, endpoint, method)
                } catch (e: Exception) {
                    logger.error(e) { "Błąd pobierania informacji o limicie" }
                    null
                }
                
                val resetTime = rateLimitInfo?.resetTime ?: Instant.now().plusSeconds(60)
                val retryAfter = (resetTime.epochSecond - Instant.now().epochSecond).coerceAtLeast(1)
                
                call.response.headers.append(
                    HttpHeaders.RetryAfter,
                    retryAfter.toString()
                )
                
                call.respond(
                    HttpStatusCode.TooManyRequests,
                    mapOf(
                        "error" to "Limit żądań przekroczony",
                        "message" to "Zbyt wiele żądań. Spróbuj ponownie później.",
                        "retryAfter" to retryAfter
                    )
                )
                return@intercept finish()
            }
            
            // Dodanie nagłówków informacyjnych o limicie
            try {
                val rateLimitInfo = rateLimitService.getRateLimitInfo(clientId, endpoint, method)
                call.response.headers.append(
                    "X-RateLimit-Limit",
                    rateLimitInfo.limit.toString()
                )
                call.response.headers.append(
                    "X-RateLimit-Remaining",
                    rateLimitInfo.remaining.toString()
                )
                call.response.headers.append(
                    "X-RateLimit-Reset",
                    rateLimitInfo.resetTime.epochSecond.toString()
                )
            } catch (e: Exception) {
                logger.debug(e) { "Nie udało się dodać nagłówków informacyjnych o limicie" }
            }
            
            proceed()
        }
    }
    
    /**
     * Pobiera identyfikator klienta na podstawie żądania
     * Priorytet: ID użytkownika (jeśli uwierzytelniony) -> IP adres
     */
    private fun getClientIdentifier(call: ApplicationCall): String {
        // Sprawdzenie czy użytkownik jest uwierzytelniony
        return try {
            if (call.attributes.contains(AuthenticationMiddleware.AuthenticatedUserKey)) {
                "user:${call.authenticatedUserId}"
            } else {
                // Użycie adresu IP jako identyfikatora
                val clientIp = call.request.origin.remoteHost ?: "unknown"
                "ip:$clientIp"
            }
        } catch (e: Exception) {
            logger.debug(e) { "Błąd pobierania identyfikatora klienta" }
            "unknown"
        }
    }
    
    /**
     * Middleware specjalny dla endpointów uwierzytelniania
     * Bardziej restrykcyjne limity dla logowania
     */
    fun configureAuthEndpoints(application: Application) {
        application.intercept(ApplicationCallPipeline.Plugins) {
            val endpoint = call.request.path()
            
            // Specjalne limity dla endpointów uwierzytelniania
            if (endpoint.startsWith("/api/auth/login") || 
                endpoint.startsWith("/api/auth/refresh")) {
                
                val clientId = getClientIdentifier(call)
                
                // Sprawdzenie limitu dla uwierzytelniania
                val isAllowed = try {
                    rateLimitService.checkRateLimit(
                        clientId = clientId,
                        endpoint = endpoint,
                        method = call.request.httpMethod.value,
                        type = RateLimitType.AUTH_ATTEMPT
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Błąd sprawdzania limitu uwierzytelniania dla klienta: $clientId" }
                    true
                }
                
                if (!isAllowed) {
                    logger.warn { "Limit prób uwierzytelnienia przekroczony dla klienta: $clientId" }
                    
                    val resetTime = Instant.now().plusSeconds(300) // 5 minut blokady
                    val retryAfter = (resetTime.epochSecond - Instant.now().epochSecond).coerceAtLeast(1)
                    
                    call.response.headers.append(
                        HttpHeaders.RetryAfter,
                        retryAfter.toString()
                    )
                    
                    call.respond(
                        HttpStatusCode.TooManyRequests,
                        mapOf(
                            "error" to "Zbyt wiele prób uwierzytelnienia",
                            "message" to "Konto zostało tymczasowo zablokowane ze względów bezpieczeństwa. Spróbuj ponownie za 5 minut.",
                            "retryAfter" to retryAfter
                        )
                    )
                    return@intercept finish()
                }
            }
            
            proceed()
        }
    }
    
    /**
     * Middleware dla endpointów administracyjnych
     * Bardzo restrykcyjne limity dla operacji administracyjnych
     */
    fun configureAdminEndpoints(application: Application) {
        application.intercept(ApplicationCallPipeline.Plugins) {
            val endpoint = call.request.path()
            
            // Specjalne limity dla endpointów administracyjnych
            if (endpoint.startsWith("/api/admin/") || 
                endpoint.startsWith("/api/security/tokens/create") ||
                endpoint.startsWith("/api/security/tunnels/start")) {
                
                // Sprawdzenie uprawnień administracyjnych
                if (!call.hasPermission("admin")) {
                    logger.warn { "Próba dostępu do endpointu administracyjnego bez uprawnień: $endpoint" }
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Brak uprawnień administracyjnych"))
                    return@intercept finish()
                }
                
                val clientId = getClientIdentifier(call)
                
                // Sprawdzenie limitu dla operacji administracyjnych
                val isAllowed = try {
                    rateLimitService.checkRateLimit(
                        clientId = clientId,
                        endpoint = endpoint,
                        method = call.request.httpMethod.value,
                        type = RateLimitType.ADMIN_OPERATION
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Błąd sprawdzania limitu operacji administracyjnych dla klienta: $clientId" }
                    true
                }
                
                if (!isAllowed) {
                    logger.warn { "Limit operacji administracyjnych przekroczony dla klienta: $clientId" }
                    
                    val resetTime = Instant.now().plusSeconds(3600) // 1 godzina blokady
                    val retryAfter = (resetTime.epochSecond - Instant.now().epochSecond).coerceAtLeast(1)
                    
                    call.response.headers.append(
                        HttpHeaders.RetryAfter,
                        retryAfter.toString()
                    )
                    
                    call.respond(
                        HttpStatusCode.TooManyRequests,
                        mapOf(
                            "error" to "Limit operacji administracyjnych przekroczony",
                            "message" to "Zbyt wiele operacji administracyjnych. Spróbuj ponownie później.",
                            "retryAfter" to retryAfter
                        )
                    )
                    return@intercept finish()
                }
            }
            
            proceed()
        }
    }
}