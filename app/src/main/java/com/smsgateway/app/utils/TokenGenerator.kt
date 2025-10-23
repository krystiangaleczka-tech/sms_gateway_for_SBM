package com.smsgateway.app.utils

import java.security.SecureRandom
import java.util.Base64

/**
 * Narzędzie do generowania bezpiecznych tokenów API
 * Używa SecureRandom do generowania kryptograficznie bezpiecznych losowych wartości
 */
class TokenGenerator {
    private val secureRandom = SecureRandom()
    
    /**
     * Generuje bezpieczny token o domyślnej długości 32 bajtów (256 bitów)
     * @return Bezpieczny token zakodowany w Base64
     */
    fun generateSecureToken(): String {
        return generateSecureToken(DEFAULT_TOKEN_LENGTH)
    }
    
    /**
     * Generuje bezpieczny token o określonej długości
     * @param lengthInBytes Długość tokena w bajtach
     * @return Bezpieczny token zakodowany w Base64
     */
    fun generateSecureToken(lengthInBytes: Int): String {
        val bytes = ByteArray(lengthInBytes)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
    
    /**
     * Generuje identyfikator sesji (krótszy token)
     * @return Identyfikator sesji zakodowany w Base64
     */
    fun generateSessionId(): String {
        return generateSecureToken(SESSION_ID_LENGTH)
    }
    
    /**
     * Generuje losowy ciąg znaków alfanumerycznych
     * @param length Długość ciągu
     * @return Losowy ciąg alfanumeryczny
     */
    fun generateAlphanumericString(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val result = StringBuilder()
        
        repeat(length) {
            result.append(chars[secureRandom.nextInt(chars.length)])
        }
        
        return result.toString()
    }
    
    /**
     * Generuje unikalny identyfikator API (prefiks + losowa wartość)
     * @param prefix Prefiks identyfikatora (np. "api", "token")
     * @return Unikalny identyfikator API
     */
    fun generateApiId(prefix: String = "api"): String {
        val randomPart = generateAlphanumericString(API_ID_RANDOM_LENGTH)
        return "${prefix}_${randomPart}"
    }
    
    /**
     * Generuje unikalny identyfikator tunelu
     * @return Identyfikator tunelu
     */
    fun generateTunnelId(): String {
        return generateApiId("tunnel")
    }
    
    /**
     * Generuje klucz API (dłuższy token dla kluczy API)
     * @return Klucz API
     */
    fun generateApiKey(): String {
        val prefix = "sk" // secret key
        val randomPart = generateSecureToken(API_KEY_LENGTH)
        return "${prefix}_${randomPart}"
    }
    
    /**
     * Generuje identyfikator klienta (dla OAuth/Client Credentials)
     * @return Identyfikator klienta
     */
    fun generateClientId(): String {
        return generateApiId("client")
    }
    
    /**
     * Generuje sekret klienta (dla OAuth/Client Credentials)
     * @return Sekret klienta
     */
    fun generateClientSecret(): String {
        return generateSecureToken(CLIENT_SECRET_LENGTH)
    }
    
    /**
     * Generuje kod weryfikacyjny (np. do 2FA)
     * @param length Długość kodu
     * @return Kod weryfikacyjny
     */
    fun generateVerificationCode(length: Int = VERIFICATION_CODE_LENGTH): String {
        return generateAlphanumericString(length)
    }
    
    /**
     * Generuje kod resetowania hasła
     * @return Kod resetowania hasła
     */
    fun generatePasswordResetCode(): String {
        return generateSecureToken(PASSWORD_RESET_CODE_LENGTH)
    }
    
    companion object {
        private const val DEFAULT_TOKEN_LENGTH = 32 // 256 bitów
        private const val SESSION_ID_LENGTH = 16 // 128 bitów
        private const val API_ID_RANDOM_LENGTH = 16
        private const val API_KEY_LENGTH = 32
        private const val CLIENT_SECRET_LENGTH = 32
        private const val VERIFICATION_CODE_LENGTH = 6
        private const val PASSWORD_RESET_CODE_LENGTH = 16
    }
}