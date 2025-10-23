package com.smsgateway.app.routes.security

import com.smsgateway.app.api.security.*
import com.smsgateway.app.middleware.security.*
import com.smsgateway.app.services.security.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val logger = KotlinLogging.logger {}

/**
 * Routing dla bezpieczeństwa i zewnętrznego dostępu
 * Łączy wszystkie kontrolery i middleware bezpieczeństwa
 */
class SecurityRoutes(
    private val tokenController: TokenController,
    private val tunnelController: TunnelController,
    private val securityController: SecurityController,
    private val tokenManagerService: TokenManagerService,
    private val tunnelService: CloudflareTunnelService,
    private val securityAuditService: SecurityAuditService,
    private val rateLimitService: RateLimitService
) {
    
    /**
     * Konfiguruje routing bezpieczeństwa
     */
    fun configure(application: Application) {
        logger.info { "Konfigurowanie routing bezpieczeństwa" }
        
        application.routing {
            // Middleware limitowania żądań
            val rateLimitMiddleware = RateLimitMiddleware(rateLimitService)
            
            // Middleware audytu bezpieczeństwa
            val securityAuditMiddleware = SecurityAuditMiddleware(securityAuditService)
            
            // Routing dla tokenów API
            route("/api/security/tokens") {
                // Tworzenie tokena
                post {
                    try {
                        tokenController.createToken(call)
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "Brak uprawnień do tworzenia tokenów: ${e.message}")
                        )
                    }
                }
                
                // Pobieranie listy tokenów
                get {
                    try {
                        tokenController.getUserTokens(call)
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "Brak uprawnień do przeglądania tokenów: ${e.message}")
                        )
                    }
                }
                
                // Pobieranie szczegółów tokena
                get("/{id}") {
                    try {
                        tokenController.getToken(call)
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "Brak uprawnień do przeglądania tokenów: ${e.message}")
                        )
                    }
                }
                
                // Odświeżenie tokena
                put("/{id}") {
                    try {
                        tokenController.refreshToken(call)
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "Brak uprawnień do aktualizacji tokenów: ${e.message}")
                        )
                    }
                }
                
                // Usuwanie tokena
                delete("/{id}") {
                    try {
                        tokenController.deleteToken(call)
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "Brak uprawnień do usuwania tokenów: ${e.message}")
                        )
                    }
                }
                
                // Walidacja tokena
                post("/validate") {
                    try {
                        tokenController.validateToken(call)
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Błąd walidacji tokena: ${e.message}")
                        )
                    }
                }
            }
            
            // Routing dla tuneli Cloudflare
            route("/api/security/tunnels") {
                // Tworzenie tunelu
                post {
                    try {
                        tunnelController.createTunnel(call)
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "Brak uprawnień do tworzenia tuneli: ${e.message}")
                        )
                    }
                }
                
                // Pobieranie listy tuneli
                get {
                    try {
                        tunnelController.getUserTunnels(call)
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "Brak uprawnień do przeglądania tuneli: ${e.message}")
                        )
                    }
                }
                
                // Pobieranie szczegółów tunelu
                get("/{id}") {
                    try {
                        tunnelController.getTunnel(call)
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "Brak uprawnień do przeglądania tuneli: ${e.message}")
                        )
                    }
                }
                
                // Startowanie tunelu
                post("/{id}/start") {
                    try {
                        tunnelController.startTunnel(call)
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "Brak uprawnień do zarządzania tunelami: ${e.message}")
                        )
                    }
                }
                
                // Zatrzymywanie tunelu
                post("/{id}/stop") {
                    try {
                        tunnelController.stopTunnel(call)
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "Brak uprawnień do zarządzania tunelami: ${e.message}")
                        )
                    }
                }
                
                // Usuwanie tunelu
                delete("/{id}") {
                    try {
                        tunnelController.deleteTunnel(call)
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "Brak uprawnień do usuwania tuneli: ${e.message}")
                        )
                    }
                }
                
                // Pobieranie statusu tunelu
                get("/{id}/status") {
                    try {
                        tunnelController.getTunnelStatus(call)
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "Brak uprawnień do przeglądania tuneli: ${e.message}")
                        )
                    }
                }
                
                // Pobieranie konfiguracji tunelu
                get("/{id}/config") {
                    try {
                        tunnelController.getTunnelConfig(call)
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "Brak uprawnień do zarządzania tunelami: ${e.message}")
                        )
                    }
                }
            }
            
            // Routing dla audytu bezpieczeństwa
            route("/api/security") {
                // Pobieranie zdarzeń bezpieczeństwa
                get("/events") {
                    try {
                        securityController.getSecurityEvents(call)
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "Brak uprawnień do przeglądania zdarzeń bezpieczeństwa: ${e.message}")
                        )
                    }
                }
                
                // Pobieranie statystyk bezpieczeństwa
                get("/stats") {
                    try {
                        securityController.getSecurityStats(call)
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "Brak uprawnień do przeglądania statystyk bezpieczeństwa: ${e.message}")
                        )
                    }
                }
                
                // Pobieranie aktywności użytkownika
                get("/activity/{userId?}") {
                    try {
                        securityController.getUserActivity(call)
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "Brak uprawnień do przeglądania aktywności użytkowników: ${e.message}")
                        )
                    }
                }
                
                // Pobieranie alertów bezpieczeństwa
                get("/alerts") {
                    try {
                        securityController.getSecurityAlerts(call)
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "Brak uprawnień do przeglądania alertów bezpieczeństwa: ${e.message}")
                        )
                    }
                }
                
                // Potwierdzanie alertu bezpieczeństwa
                post("/alerts/{id}/acknowledge") {
                    try {
                        securityController.acknowledgeAlert(call)
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "Brak uprawnień do zarządzania alertami bezpieczeństwa: ${e.message}")
                        )
                    }
                }
                
                // Generowanie raportu bezpieczeństwa
                post("/reports/generate") {
                    try {
                        securityController.generateSecurityReport(call)
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "Brak uprawnień do generowania raportów bezpieczeństwa: ${e.message}")
                        )
                    }
                }
                
                // Pobieranie konfiguracji bezpieczeństwa
                get("/config") {
                    try {
                        securityController.getSecurityConfig(call)
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "Brak uprawnień do przeglądania konfiguracji bezpieczeństwa: ${e.message}")
                        )
                    }
                }
            }
        }
        
        logger.info { "Routing bezpieczeństwa skonfigurowany" }
    }
}