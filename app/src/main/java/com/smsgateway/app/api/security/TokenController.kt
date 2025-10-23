package com.smsgateway.app.api.security

import com.smsgateway.app.models.security.ApiTokenType
import com.smsgateway.app.models.security.dto.*
import com.smsgateway.app.services.security.TokenManagerService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

private val logger = KotlinLogging.logger {}

/**
 * Kontroler do zarządzania tokenami API
 * Obsługuje tworzenie, odnawianie i usuwanie tokenów
 */
class TokenController(
    private val tokenManagerService: TokenManagerService
) {
    
    /**
     * Tworzy nowy token API
     */
    suspend fun createToken(call: ApplicationCall) {
        try {
            val userId = call.authenticatedUserId
            val request = call.receive<CreateTokenRequest>()
            
            // Walidacja uprawnień
            if (!call.hasPermission("token:create") && 
                !call.hasPermission("admin") &&
                request.type != ApiTokenType.PERSONAL) {
                logger.warn { "Użytkownik $userId próbuje utworzyć token typu ${request.type} bez uprawnień" }
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Brak uprawnień do tworzenia tokenów tego typu")
                )
                return
            }
            
            // Tworzenie tokena
            val token = tokenManagerService.createToken(
                userId = userId,
                name = request.name,
                type = request.type,
                permissions = request.permissions,
                expiresInDays = request.expiresInDays,
                description = request.description
            )
            
            logger.info { "Utworzono token ${token.id} typu ${token.type} dla użytkownika $userId" }
            
            call.respond(
                HttpStatusCode.Created,
                CreateTokenResponse(
                    id = token.id,
                    name = token.name,
                    type = token.type,
                    permissions = token.permissions,
                    token = token.token,
                    expiresAt = token.expiresAt,
                    createdAt = token.createdAt,
                    description = token.description
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Błąd tworzenia tokena" }
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Błąd tworzenia tokena")
            )
        }
    }
    
    /**
     * Pobiera listę tokenów użytkownika
     */
    suspend fun getUserTokens(call: ApplicationCall) {
        try {
            val userId = call.authenticatedUserId
            
            // Sprawdzenie uprawnień do przeglądania wszystkich tokenów
            val tokens = if (call.hasPermission("admin")) {
                tokenManagerService.getAllTokens()
            } else {
                tokenManagerService.getUserTokens(userId)
            }
            
            val tokenResponses = tokens.map { token ->
                TokenResponse(
                    id = token.id,
                    name = token.name,
                    type = token.type,
                    permissions = token.permissions,
                    expiresAt = token.expiresAt,
                    createdAt = token.createdAt,
                    lastUsedAt = token.lastUsedAt,
                    isActive = token.isActive,
                    description = token.description
                )
            }
            
            call.respond(
                HttpStatusCode.OK,
                mapOf("tokens" to tokenResponses)
            )
        } catch (e: Exception) {
            logger.error(e) { "Błąd pobierania listy tokenów" }
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Błąd pobierania listy tokenów")
            )
        }
    }
    
    /**
     * Pobiera szczegóły tokena
     */
    suspend fun getToken(call: ApplicationCall) {
        try {
            val tokenId = call.parameters["id"]
            val userId = call.authenticatedUserId
            
            if (tokenId == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Brak ID tokena")
                )
                return
            }
            
            val token = tokenManagerService.getToken(tokenId)
            
            if (token == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Token nie znaleziony")
                )
                return
            }
            
            // Sprawdzenie uprawnień
            if (token.userId != userId && !call.hasPermission("admin")) {
                logger.warn { "Użytkownik $userId próbuje uzyskać dostęp do tokena ${token.id} innego użytkownika" }
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Brak dostępu do tego tokena")
                )
                return
            }
            
            call.respond(
                HttpStatusCode.OK,
                TokenResponse(
                    id = token.id,
                    name = token.name,
                    type = token.type,
                    permissions = token.permissions,
                    expiresAt = token.expiresAt,
                    createdAt = token.createdAt,
                    lastUsedAt = token.lastUsedAt,
                    isActive = token.isActive,
                    description = token.description
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Błąd pobierania szczegółów tokena" }
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Błąd pobierania szczegółów tokena")
            )
        }
    }
    
    /**
     * Odświeża token
     */
    suspend fun refreshToken(call: ApplicationCall) {
        try {
            val tokenId = call.parameters["id"]
            val userId = call.authenticatedUserId
            val request = call.receive<RefreshTokenRequest>()
            
            if (tokenId == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Brak ID tokena")
                )
                return
            }
            
            val token = tokenManagerService.getToken(tokenId)
            
            if (token == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Token nie znaleziony")
                )
                return
            }
            
            // Sprawdzenie uprawnień
            if (token.userId != userId && !call.hasPermission("admin")) {
                logger.warn { "Użytkownik $userId próbuje odświeżyć token ${token.id} innego użytkownika" }
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Brak dostępu do tego tokena")
                )
                return
            }
            
            // Odświeżenie tokena
            val refreshedToken = tokenManagerService.refreshToken(
                tokenId = tokenId,
                expiresInDays = request.expiresInDays
            )
            
            if (refreshedToken == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Nie udało się odświeżyć tokena")
                )
                return
            }
            
            logger.info { "Odświeżono token ${refreshedToken.id} dla użytkownika $userId" }
            
            call.respond(
                HttpStatusCode.OK,
                RefreshTokenResponse(
                    id = refreshedToken.id,
                    token = refreshedToken.token,
                    expiresAt = refreshedToken.expiresAt
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Błąd odświeżania tokena" }
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Błąd odświeżania tokena")
            )
        }
    }
    
    /**
     * Usuwa token
     */
    suspend fun deleteToken(call: ApplicationCall) {
        try {
            val tokenId = call.parameters["id"]
            val userId = call.authenticatedUserId
            
            if (tokenId == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Brak ID tokena")
                )
                return
            }
            
            val token = tokenManagerService.getToken(tokenId)
            
            if (token == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Token nie znaleziony")
                )
                return
            }
            
            // Sprawdzenie uprawnień
            if (token.userId != userId && !call.hasPermission("admin")) {
                logger.warn { "Użytkownik $userId próbuje usunąć token ${token.id} innego użytkownika" }
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Brak dostępu do tego tokena")
                )
                return
            }
            
            // Usunięcie tokena
            val success = tokenManagerService.revokeToken(tokenId)
            
            if (!success) {
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Nie udało się usunąć tokena")
                )
                return
            }
            
            logger.info { "Usunięto token $tokenId dla użytkownika $userId" }
            
            call.respond(
                HttpStatusCode.OK,
                mapOf("message" to "Token został usunięty")
            )
        } catch (e: Exception) {
            logger.error(e) { "Błąd usuwania tokena" }
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Błąd usuwania tokena")
            )
        }
    }
    
    /**
     * Waliduje token
     */
    suspend fun validateToken(call: ApplicationCall) {
        try {
            val request = call.receive<ValidateTokenRequest>()
            
            val validation = tokenManagerService.validateToken(request.token)
            
            call.respond(
                HttpStatusCode.OK,
                ValidateTokenResponse(
                    isValid = validation.isValid,
                    userId = validation.userId,
                    permissions = validation.permissions,
                    expiresAt = validation.expiresAt,
                    reason = validation.reason
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Błąd walidacji tokena" }
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Błąd walidacji tokena")
            )
        }
    }
}