package com.smsgateway.app.api.security

import com.smsgateway.app.models.security.TunnelType
import com.smsgateway.app.models.security.dto.*
import com.smsgateway.app.services.security.CloudflareTunnelService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

private val logger = KotlinLogging.logger {}

/**
 * Kontroler do zarządzania tunelami Cloudflare
 * Obsługuje tworzenie, konfigurację i zarządzanie tunelami dla zewnętrznego dostępu
 */
class TunnelController(
    private val tunnelService: CloudflareTunnelService
) {
    
    /**
     * Tworzy nowy tunel Cloudflare
     */
    suspend fun createTunnel(call: ApplicationCall) {
        try {
            val userId = call.authenticatedUserId
            
            // Sprawdzenie uprawnień
            if (!call.hasPermission("tunnel:create") && !call.hasPermission("admin")) {
                logger.warn { "Użytkownik $userId próbuje utworzyć tunel bez uprawnień" }
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Brak uprawnień do tworzenia tuneli")
                )
                return
            }
            
            val request = call.receive<CreateTunnelRequest>()
            
            // Tworzenie tunelu
            val tunnel = tunnelService.createTunnel(
                name = request.name,
                type = request.type,
                hostname = request.hostname,
                port = request.port,
                userId = userId,
                description = request.description
            )
            
            logger.info { "Utworzono tunel ${tunnel.id} typu ${tunnel.type} dla użytkownika $userId" }
            
            call.respond(
                HttpStatusCode.Created,
                CreateTunnelResponse(
                    id = tunnel.id,
                    name = tunnel.name,
                    type = tunnel.type,
                    hostname = tunnel.hostname,
                    port = tunnel.port,
                    publicUrl = tunnel.publicUrl,
                    status = tunnel.status,
                    createdAt = tunnel.createdAt,
                    description = tunnel.description
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Błąd tworzenia tunelu" }
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Błąd tworzenia tunelu: ${e.message}")
            )
        }
    }
    
    /**
     * Pobiera listę tuneli użytkownika
     */
    suspend fun getUserTunnels(call: ApplicationCall) {
        try {
            val userId = call.authenticatedUserId
            
            // Sprawdzenie uprawnień do przeglądania wszystkich tuneli
            val tunnels = if (call.hasPermission("admin")) {
                tunnelService.getAllTunnels()
            } else {
                tunnelService.getUserTunnels(userId)
            }
            
            val tunnelResponses = tunnels.map { tunnel ->
                TunnelResponse(
                    id = tunnel.id,
                    name = tunnel.name,
                    type = tunnel.type,
                    hostname = tunnel.hostname,
                    port = tunnel.port,
                    publicUrl = tunnel.publicUrl,
                    status = tunnel.status,
                    createdAt = tunnel.createdAt,
                    lastActiveAt = tunnel.lastActiveAt,
                    isActive = tunnel.isActive,
                    description = tunnel.description
                )
            }
            
            call.respond(
                HttpStatusCode.OK,
                mapOf("tunnels" to tunnelResponses)
            )
        } catch (e: Exception) {
            logger.error(e) { "Błąd pobierania listy tuneli" }
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Błąd pobierania listy tuneli")
            )
        }
    }
    
    /**
     * Pobiera szczegóły tunelu
     */
    suspend fun getTunnel(call: ApplicationCall) {
        try {
            val tunnelId = call.parameters["id"]
            val userId = call.authenticatedUserId
            
            if (tunnelId == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Brak ID tunelu")
                )
                return
            }
            
            val tunnel = tunnelService.getTunnel(tunnelId)
            
            if (tunnel == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Tunel nie znaleziony")
                )
                return
            }
            
            // Sprawdzenie uprawnień
            if (tunnel.userId != userId && !call.hasPermission("admin")) {
                logger.warn { "Użytkownik $userId próbuje uzyskać dostęp do tunelu ${tunnel.id} innego użytkownika" }
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Brak dostępu do tego tunelu")
                )
                return
            }
            
            call.respond(
                HttpStatusCode.OK,
                TunnelResponse(
                    id = tunnel.id,
                    name = tunnel.name,
                    type = tunnel.type,
                    hostname = tunnel.hostname,
                    port = tunnel.port,
                    publicUrl = tunnel.publicUrl,
                    status = tunnel.status,
                    createdAt = tunnel.createdAt,
                    lastActiveAt = tunnel.lastActiveAt,
                    isActive = tunnel.isActive,
                    description = tunnel.description
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Błąd pobierania szczegółów tunelu" }
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Błąd pobierania szczegółów tunelu")
            )
        }
    }
    
    /**
     * Startuje tunel
     */
    suspend fun startTunnel(call: ApplicationCall) {
        try {
            val tunnelId = call.parameters["id"]
            val userId = call.authenticatedUserId
            
            if (tunnelId == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Brak ID tunelu")
                )
                return
            }
            
            val tunnel = tunnelService.getTunnel(tunnelId)
            
            if (tunnel == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Tunel nie znaleziony")
                )
                return
            }
            
            // Sprawdzenie uprawnień
            if (tunnel.userId != userId && !call.hasPermission("admin")) {
                logger.warn { "Użytkownik $userId próbuje uruchomić tunel ${tunnel.id} innego użytkownika" }
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Brak dostępu do tego tunelu")
                )
                return
            }
            
            // Uruchomienie tunelu
            val success = tunnelService.startTunnel(tunnelId)
            
            if (!success) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Nie udało się uruchomić tunelu")
                )
                return
            }
            
            logger.info { "Uruchomiono tunel $tunnelId dla użytkownika $userId" }
            
            call.respond(
                HttpStatusCode.OK,
                mapOf("message" to "Tunel został uruchomiony")
            )
        } catch (e: Exception) {
            logger.error(e) { "Błąd uruchamiania tunelu" }
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Błąd uruchamiania tunelu: ${e.message}")
            )
        }
    }
    
    /**
     * Zatrzymuje tunel
     */
    suspend fun stopTunnel(call: ApplicationCall) {
        try {
            val tunnelId = call.parameters["id"]
            val userId = call.authenticatedUserId
            
            if (tunnelId == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Brak ID tunelu")
                )
                return
            }
            
            val tunnel = tunnelService.getTunnel(tunnelId)
            
            if (tunnel == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Tunel nie znaleziony")
                )
                return
            }
            
            // Sprawdzenie uprawnień
            if (tunnel.userId != userId && !call.hasPermission("admin")) {
                logger.warn { "Użytkownik $userId próbuje zatrzymać tunel ${tunnel.id} innego użytkownika" }
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Brak dostępu do tego tunelu")
                )
                return
            }
            
            // Zatrzymanie tunelu
            val success = tunnelService.stopTunnel(tunnelId)
            
            if (!success) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Nie udało się zatrzymać tunelu")
                )
                return
            }
            
            logger.info { "Zatrzymano tunel $tunnelId dla użytkownika $userId" }
            
            call.respond(
                HttpStatusCode.OK,
                mapOf("message" to "Tunel został zatrzymany")
            )
        } catch (e: Exception) {
            logger.error(e) { "Błąd zatrzymywania tunelu" }
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Błąd zatrzymywania tunelu: ${e.message}")
            )
        }
    }
    
    /**
     * Usuwa tunel
     */
    suspend fun deleteTunnel(call: ApplicationCall) {
        try {
            val tunnelId = call.parameters["id"]
            val userId = call.authenticatedUserId
            
            if (tunnelId == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Brak ID tunelu")
                )
                return
            }
            
            val tunnel = tunnelService.getTunnel(tunnelId)
            
            if (tunnel == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Tunel nie znaleziony")
                )
                return
            }
            
            // Sprawdzenie uprawnień
            if (tunnel.userId != userId && !call.hasPermission("admin")) {
                logger.warn { "Użytkownik $userId próbuje usunąć tunel ${tunnel.id} innego użytkownika" }
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Brak dostępu do tego tunelu")
                )
                return
            }
            
            // Usunięcie tunelu
            val success = tunnelService.deleteTunnel(tunnelId)
            
            if (!success) {
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Nie udało się usunąć tunelu")
                )
                return
            }
            
            logger.info { "Usunięto tunel $tunnelId dla użytkownika $userId" }
            
            call.respond(
                HttpStatusCode.OK,
                mapOf("message" to "Tunel został usunięty")
            )
        } catch (e: Exception) {
            logger.error(e) { "Błąd usuwania tunelu" }
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Błąd usuwania tunelu: ${e.message}")
            )
        }
    }
    
    /**
     * Pobiera status tunelu
     */
    suspend fun getTunnelStatus(call: ApplicationCall) {
        try {
            val tunnelId = call.parameters["id"]
            val userId = call.authenticatedUserId
            
            if (tunnelId == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Brak ID tunelu")
                )
                return
            }
            
            val tunnel = tunnelService.getTunnel(tunnelId)
            
            if (tunnel == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Tunel nie znaleziony")
                )
                return
            }
            
            // Sprawdzenie uprawnień
            if (tunnel.userId != userId && !call.hasPermission("admin")) {
                logger.warn { "Użytkownik $userId próbuje sprawdzić status tunelu ${tunnel.id} innego użytkownika" }
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Brak dostępu do tego tunelu")
                )
                return
            }
            
            val status = tunnelService.getTunnelStatus(tunnelId)
            
            call.respond(
                HttpStatusCode.OK,
                TunnelStatusResponse(
                    id = tunnel.id,
                    name = tunnel.name,
                    status = status.status,
                    isActive = status.isActive,
                    lastActiveAt = status.lastActiveAt,
                    connectionCount = status.connectionCount,
                    bandwidthUsed = status.bandwidthUsed,
                    uptime = status.uptime
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Błąd pobierania statusu tunelu" }
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Błąd pobierania statusu tunelu: ${e.message}")
            )
        }
    }
    
    /**
     * Pobiera konfigurację klienta tunelu
     */
    suspend fun getTunnelConfig(call: ApplicationCall) {
        try {
            val tunnelId = call.parameters["id"]
            val userId = call.authenticatedUserId
            
            if (tunnelId == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Brak ID tunelu")
                )
                return
            }
            
            val tunnel = tunnelService.getTunnel(tunnelId)
            
            if (tunnel == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Tunel nie znaleziony")
                )
                return
            }
            
            // Sprawdzenie uprawnień
            if (tunnel.userId != userId && !call.hasPermission("admin")) {
                logger.warn { "Użytkownik $userId próbuje pobrać konfigurację tunelu ${tunnel.id} innego użytkownika" }
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Brak dostępu do tego tunelu")
                )
                return
            }
            
            val config = tunnelService.getTunnelConfig(tunnelId)
            
            call.respond(
                HttpStatusCode.OK,
                TunnelConfigResponse(
                    tunnelId = tunnel.id,
                    tunnelName = tunnel.name,
                    tunnelToken = config.tunnelToken,
                    tunnelSecret = config.tunnelSecret,
                    accountId = config.accountId,
                    configYaml = config.configYaml
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Błąd pobierania konfiguracji tunelu" }
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Błąd pobierania konfiguracji tunelu: ${e.message}")
            )
        }
    }
}