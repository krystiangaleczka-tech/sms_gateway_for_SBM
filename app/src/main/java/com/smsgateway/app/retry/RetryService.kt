package com.smsgateway.app.retry

import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import com.smsgateway.app.database.SmsStatus

/**
 * Klasa do zarządzania ponawianiem operacji
 * 
 * Implementuje strategie ponawiania dla różnych typów błędów:
 * - Exponential backoff
 * - Linear backoff
 * - Fixed delay
 * - Maksymalna liczba prób
 */
class RetryService {
    
    /**
     * Strategie ponawiania
     */
    enum class RetryStrategy {
        EXPONENTIAL_BACKOFF,
        LINEAR_BACKOFF,
        FIXED_DELAY,
        NO_RETRY
    }
    
    /**
     * Konfiguracja ponawiania
     */
    data class RetryConfig(
        val maxAttempts: Int = 3,
        val initialDelayMs: Long = 1000,
        val maxDelayMs: Long = 30000,
        val backoffMultiplier: Double = 2.0,
        val strategy: RetryStrategy = RetryStrategy.EXPONENTIAL_BACKOFF
    )
    
    /**
     * Wynik operacji ponawiania
     */
    data class RetryResult<T>(
        val success: Boolean,
        val result: T? = null,
        val error: Throwable? = null,
        val attempts: Int = 0
    )
    
    /**
     * Wykonuje operację z ponawianiem
     */
    suspend fun <T> executeWithRetry(
        operation: suspend () -> T,
        config: RetryConfig = RetryConfig()
    ): RetryResult<T> {
        var lastError: Throwable? = null
        var currentDelay = config.initialDelayMs
        
        for (attempt in 1..config.maxAttempts) {
            try {
                val result = operation()
                return RetryResult(
                    success = true,
                    result = result,
                    attempts = attempt
                )
            } catch (e: Exception) {
                lastError = e
                
                // Nie ponawiaj przy ostatniej próbie
                if (attempt == config.maxAttempts) {
                    break
                }
                
                // Sprawdź czy błąd kwalifikuje się do ponowienia
                if (!shouldRetry(e)) {
                    break
                }
                
                // Poczekaj przed następną próbą
                delay(calculateDelay(currentDelay, config, attempt))
                currentDelay = (currentDelay * config.backoffMultiplier).toLong()
                    .coerceAtMost(config.maxDelayMs)
            }
        }
        
        return RetryResult(
            success = false,
            error = lastError,
            attempts = config.maxAttempts
        )
    }
    
    /**
     * Wykonuje operację z domyślną konfiguracją
     */
    suspend fun <T> executeWithRetry(operation: suspend () -> T): RetryResult<T> {
        return executeWithRetry(operation, RetryConfig())
    }
    
    /**
     * Sprawdza czy błąd kwalifikuje się do ponowienia
     */
    private fun shouldRetry(error: Throwable): Boolean {
        // Nie ponawiaj przy błędach walidacji
        if (error is IllegalArgumentException) {
            return false
        }
        
        // Nie ponawiaj przy błędach autoryzacji
        if (error.message?.contains("401") == true || 
            error.message?.contains("Unauthorized") == true) {
            return false
        }
        
        // Nie ponawiaj przy błędach "not found"
        if (error.message?.contains("404") == true || 
            error.message?.contains("Not found") == true) {
            return false
        }
        
        return true
    }
    
    /**
     * Oblicza opóźnienie przed następną próbą
     */
    private fun calculateDelay(
        currentDelay: Long,
        config: RetryConfig,
        attempt: Int
    ): Long {
        return when (config.strategy) {
            RetryStrategy.EXPONENTIAL_BACKOFF -> {
                (config.backoffMultiplier.pow(attempt - 1).toDouble() * currentDelay).toLong()
                    .coerceAtMost(config.maxDelayMs)
            }
            RetryStrategy.LINEAR_BACKOFF -> {
                (config.initialDelayMs * attempt).coerceAtMost(config.maxDelayMs)
            }
            RetryStrategy.FIXED_DELAY -> {
                config.initialDelayMs
            }
            RetryStrategy.NO_RETRY -> {
                0L
            }
        }
    }
    
    /**
     * Konwertuje milisekundy na TimeUnit
     */
    fun toTimeUnit(milliseconds: Long): TimeUnit {
        return when {
            milliseconds < 1000 -> TimeUnit.MILLISECONDS
            milliseconds < 60_000 -> TimeUnit.SECONDS
            milliseconds < 3_600_000 -> TimeUnit.MINUTES
            else -> TimeUnit.HOURS
        }
    }
}