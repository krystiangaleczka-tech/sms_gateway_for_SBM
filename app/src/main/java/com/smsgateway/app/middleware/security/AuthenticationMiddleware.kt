package com.smsgateway.app.middleware.security

import com.smsgateway.app.models.security.dto.ValidateTokenResponse
import com.smsgateway.app.services.security.TokenManagerService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

private val logger = KotlinLogging.logger {}

/**
 * Middleware uwierzytelniający żądania API
 * Waliduje tokeny Bearer i wstrzykuje informacje o użytkowniku do kontekstu
 */
class AuthenticationMiddleware(
    private val tokenManagerService: TokenManagerService
) {
    
    /**
     * Konfiguruje middleware uwierzytelniający
     */
    fun configure(application: Application) {
        application.intercept(ApplicationCallPipeline.Plugins) {
            if (call.request.path().startsWith("/api/") && 
                !call.request.path().startsWith("/api/health") &&
                !call.request.path().startsWith("/api/auth/login")) {
                
                // Pobranie tokenu z nagłówka Authorization
                val authHeader = call.request.headers["Authorization"]
                
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    logger.warn { "Brak tokenu Bearer w żądaniu: ${call.request.path()}" }
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Brak tokenu uwierzytelniającego"))
                    return@intercept finish()
                }
                
                val token = authHeader.removePrefix("Bearer ").trim()
                
                if (token.isEmpty()) {
                    logger.warn { "Pusty token Bearer w żądaniu: ${call.request.path()}" }
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Nieprawidłowy token uwierzytelniający"))
                    return@intercept finish()
                }
                
                // Walidacja tokenu
                val tokenValidation: ValidateTokenResponse = try {
                    tokenManagerService.validateToken(token)
                } catch (e: Exception) {
                    logger.error(e) { "Błąd walidacji tokenu" }
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Błąd weryfikacji tokenu"))
                    return@intercept finish()
                }
                
                if (!tokenValidation.isValid) {
                    logger.warn { "Nieprawidłowy token: ${tokenValidation.reason}" }
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to (tokenValidation.reason ?: "Nieprawidłowy token")))
                    return@intercept finish()
                }
                
                // Dodanie informacji o użytkowniku do kontekstu
                call.attributes.put(AuthenticatedUserKey, tokenValidation.userId ?: "")
                call.attributes.put(UserPermissionsKey, tokenValidation.permissions)
                
                logger.debug { "Użytkownik uwierzytelniony: ${tokenValidation.userId}" }
            }
            
            proceed()
        }
    }
    
    companion object {
        val AuthenticatedUserKey = AttributeKey<String>("AuthenticatedUser")
        val UserPermissionsKey = AttributeKey<List<String>>("UserPermissions")
    }
}

/**
 * Rozszerzenie do pobierania uwierzytelnionego użytkownika z kontekstu
 */
val ApplicationCall.authenticatedUserId: String
    get() = this.attributes[AuthenticationMiddleware.AuthenticatedUserKey]

/**
 * Rozszerzenie do pobierania uprawnień użytkownika z kontekstu
 */
val ApplicationCall.userPermissions: List<String>
    get() = this.attributes[AuthenticationMiddleware.UserPermissionsKey] ?: emptyList()

/**
 * Sprawdza czy użytkownik ma określone uprawnienie
 */
fun ApplicationCall.hasPermission(permission: String): Boolean {
    return userPermissions.contains(permission)
}

/**
 * Sprawdza czy użytkownik ma któreś z określonych uprawnień
 */
fun ApplicationCall.hasAnyPermission(vararg permissions: String): Boolean {
    return permissions.any { userPermissions.contains(it) }
}

/**
 * Funkcja do walidacji uprawnień w endpointach
 */
fun ApplicationCall.validatePermission(permission: String): Boolean {
    if (!hasPermission(permission)) {
        logger.warn { "Brak uprawnień: $permission dla użytkownika: $authenticatedUserId" }
        return false
    }
    return true
}