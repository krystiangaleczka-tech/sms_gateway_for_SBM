package com.smsgateway.app.services.security

import at.favre.lib.crypto.bcrypt.BCrypt
import com.smsgateway.app.models.security.ApiToken
import com.smsgateway.app.models.security.ApiTokenType
import com.smsgateway.app.models.security.dto.CreateTokenRequest
import com.smsgateway.app.models.security.dto.CreateTokenResponse
import com.smsgateway.app.models.security.dto.ValidateTokenResponse
import com.smsgateway.app.repositories.ApiTokenRepository
import com.smsgateway.app.utils.TokenGenerator
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Serwis zarządzający tokenami API
 * Odpowiedzialny za tworzenie, walidację i odnawianie tokenów
 */
class TokenManagerService(
    private val tokenRepository: ApiTokenRepository,
    private val tokenGenerator: TokenGenerator
) {
    
    /**
     * Tworzy nowy token API
     * @param request Dane do stworzenia tokena
     * @return Odpowiedź z danymi nowego tokena
     */
    suspend fun createToken(request: CreateTokenRequest): CreateTokenResponse {
        logger.info { "Tworzenie nowego tokena API typu: ${request.type}" }
        
        // Walidacja hasła dla tokenów stałych
        if (request.type == ApiTokenType.PERMANENT && request.password.isNullOrEmpty()) {
            throw IllegalArgumentException("Hasło jest wymagane dla tokenów stałych")
        }
        
        // Sprawdzenie limitu tokenów na użytkownika
        val existingTokens = tokenRepository.findByUserId(request.userId)
        val activeTokens = existingTokens.filter { !it.isRevoked && it.expiresAt > Instant.now() }
        
        if (activeTokens.size >= MAX_TOKENS_PER_USER) {
            throw IllegalStateException("Osiągnięto maksymalną liczbę aktywnych tokenów: $MAX_TOKENS_PER_USER")
        }
        
        // Generowanie tokenu
        val tokenId = UUID.randomUUID().toString()
        val tokenValue = tokenGenerator.generateSecureToken()
        val hashedToken = BCrypt.withDefaults().hashToString(4, tokenValue.toCharArray())
        
        // Określenie daty wygaśnięcia
        val expiresAt = when (request.type) {
            ApiTokenType.TEMPORARY -> Instant.now().plus(1, ChronoUnit.HOURS)
            ApiTokenType.PERMANENT -> Instant.now().plus(365, ChronoUnit.DAYS)
        }
        
        // Tworzenie encji tokena
        val apiToken = ApiToken(
            id = tokenId,
            userId = request.userId,
            name = request.name,
            type = request.type,
            hashedToken = hashedToken,
            permissions = request.permissions,
            expiresAt = expiresAt,
            lastUsedAt = null,
            isRevoked = false,
            createdAt = Instant.now()
        )
        
        // Zapisanie tokena
        tokenRepository.save(apiToken)
        
        logger.info { "Utworzono token API: $tokenId dla użytkownika: ${request.userId}" }
        
        return CreateTokenResponse(
            id = tokenId,
            token = tokenValue, // Zwracamy niehashowany token tylko raz
            name = request.name,
            type = request.type,
            permissions = request.permissions,
            expiresAt = expiresAt
        )
    }
    
    /**
     * Waliduje token API
     * @param tokenValue Wartość tokena do walidacji
     * @return Odpowiedź z wynikiem walidacji
     */
    suspend fun validateToken(tokenValue: String): ValidateTokenResponse {
        logger.debug { "Walidacja tokena API" }
        
        // Znalezienie tokena po wartości (musimy sprawdzić wszystkie)
        val allTokens = tokenRepository.findAll()
        val matchingToken = allTokens.find { token ->
            BCrypt.verifyer().verify(tokenValue.toCharArray(), token.hashedToken).verified
        }
        
        if (matchingToken == null) {
            logger.warn { "Nieprawidłowy token API" }
            return ValidateTokenResponse(
                isValid = false,
                userId = null,
                permissions = emptyList(),
                reason = "Nieprawidłowy token"
            )
        }
        
        // Sprawdzenie czy token nie został odwołany
        if (matchingToken.isRevoked) {
            logger.warn { "Token został odwołany: ${matchingToken.id}" }
            return ValidateTokenResponse(
                isValid = false,
                userId = null,
                permissions = emptyList(),
                reason = "Token został odwołany"
            )
        }
        
        // Sprawdzenie czy token nie wygasł
        if (matchingToken.expiresAt < Instant.now()) {
            logger.warn { "Token wygasł: ${matchingToken.id}" }
            return ValidateTokenResponse(
                isValid = false,
                userId = null,
                permissions = emptyList(),
                reason = "Token wygasł"
            )
        }
        
        // Aktualizacja ostatniego użycia
        tokenRepository.updateLastUsedAt(matchingToken.id, Instant.now())
        
        logger.debug { "Token prawidłowy: ${matchingToken.id} dla użytkownika: ${matchingToken.userId}" }
        
        return ValidateTokenResponse(
            isValid = true,
            userId = matchingToken.userId,
            permissions = matchingToken.permissions,
            reason = null
        )
    }
    
    /**
     * Odwołuje token API
     * @param tokenId ID tokena do odwołania
     * @param userId ID użytkownika sprawdzającego
     * @return True jeśli token został odwołany
     */
    suspend fun revokeToken(tokenId: String, userId: String): Boolean {
        logger.info { "Odwoływanie tokena: $tokenId przez użytkownika: $userId" }
        
        val token = tokenRepository.findById(tokenId)
            ?: throw IllegalArgumentException("Token nie istnieje")
        
        // Sprawdzenie czy użytkownik jest właścicielem tokena
        if (token.userId != userId) {
            logger.warn { "Użytkownik $userId próbuje odwołać token należący do ${token.userId}" }
            throw SecurityException("Brak uprawnień do odwołania tego tokena")
        }
        
        return tokenRepository.revokeToken(tokenId)
    }
    
    /**
     * Pobiera wszystkie tokeny użytkownika
     * @param userId ID użytkownika
     * @return Lista tokenów użytkownika
     */
    suspend fun getUserTokens(userId: String): List<ApiToken> {
        logger.debug { "Pobieranie tokenów użytkownika: $userId" }
        return tokenRepository.findByUserId(userId)
    }
    
    /**
     * Usuwa wygasłe tokeny
     * @return Liczba usuniętych tokenów
     */
    suspend fun cleanupExpiredTokens(): Int {
        logger.info { "Czyszczenie wygasłych tokenów" }
        return tokenRepository.deleteExpired()
    }
    
    /**
     * Odnawia token API
     * @param tokenId ID tokena do odnowienia
     * @param userId ID użytkownika
     * @return Zaktualizowany token
     */
    suspend fun renewToken(tokenId: String, userId: String): ApiToken {
        logger.info { "Odnawianie tokena: $tokenId przez użytkownika: $userId" }
        
        val token = tokenRepository.findById(tokenId)
            ?: throw IllegalArgumentException("Token nie istnieje")
        
        // Sprawdzenie czy użytkownik jest właścicielem tokena
        if (token.userId != userId) {
            throw SecurityException("Brak uprawnień do odnowienia tego tokena")
        }
        
        // Sprawdzenie czy token nie został odwołany
        if (token.isRevoked) {
            throw IllegalStateException("Nie można odnowić odwołanego tokena")
        }
        
        // Nowa data wygaśnięcia
        val newExpiresAt = when (token.type) {
            ApiTokenType.TEMPORARY -> Instant.now().plus(1, ChronoUnit.HOURS)
            ApiTokenType.PERMANENT -> Instant.now().plus(365, ChronoUnit.DAYS)
        }
        
        // Aktualizacja tokena
        return tokenRepository.renewToken(tokenId, newExpiresAt)
    }
    
    companion object {
        private const val MAX_TOKENS_PER_USER = 10
    }
}