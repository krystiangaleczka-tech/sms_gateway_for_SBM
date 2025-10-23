package com.smsgateway.app.services.security

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.smsgateway.app.models.security.RateLimitEntry
import com.smsgateway.app.models.security.RateLimitType
import com.smsgateway.app.repositories.RateLimitRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Serwis zarządzający limitami żądań API
 * Używa pamięci podręcznej Caffeine do efektywnego śledzenia limitów
 */
class RateLimitService(
    private val rateLimitRepository: RateLimitRepository
) {
    // Cache w pamięci dla szybkiego dostępu do liczników
    private val requestCounter: Cache<String, Long> = Caffeine.newBuilder()
        .maximumSize(10000)
        .expireAfterWrite(1, TimeUnit.HOURS)
        .build()
    
    // Cache dla blokad
    private val blockedClients: Cache<String, Instant> = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(24, TimeUnit.HOURS)
        .build()
    
    /**
     * Sprawdza czy żądanie jest dozwolone na podstawie limitów
     * @param clientId Identyfikator klienta (IP, token API, etc.)
     * @param type Typ limitu
     * @param customLimit Niestandardowy limit (opcjonalny)
     * @return Pair<Boolean, String> - (czy dozwolone, powód odrzucenia)
     */
    suspend fun checkRateLimit(
        clientId: String,
        type: RateLimitType,
        customLimit: Int? = null
    ): Pair<Boolean, String> {
        logger.debug { "Sprawdzanie limitu żądań dla klienta: $clientId, typ: $type" }
        
        // Sprawdzenie czy klient jest zablokowany
        val blockedUntil = blockedClients.getIfPresent(clientId)
        if (blockedUntil != null && blockedUntil > Instant.now()) {
            logger.warn { "Klient $clientId jest zablokowany do $blockedUntil" }
            return Pair(false, "Klient jest tymczasowo zablokowany")
        }
        
        // Określenie limitu
        val limit = customLimit ?: getDefaultLimit(type)
        val windowDuration = getWindowDuration(type)
        
        // Pobranie aktualnego licznika
        val currentCount = requestCounter.getIfPresent(getCacheKey(clientId, type)) ?: 0
        
        // Sprawdzenie limitu
        if (currentCount >= limit) {
            logger.warn { "Przekroczono limit żądań dla klienta: $clientId, limit: $limit" }
            
            // Zapisanie zdarzenia przekroczenia limitu
            rateLimitRepository.save(
                RateLimitEntry(
                    id = java.util.UUID.randomUUID().toString(),
                    clientId = clientId,
                    type = type,
                    requestCount = currentCount,
                    limit = limit,
                    windowStart = Instant.now().minus(windowDuration),
                    windowEnd = Instant.now(),
                    isBlocked = false,
                    createdAt = Instant.now()
                )
            )
            
            // Sprawdzenie czy zablokować klienta
            if (shouldBlockClient(clientId, type)) {
                val blockDuration = getBlockDuration(type)
                val blockedUntil = Instant.now().plus(blockDuration)
                blockedClients.put(clientId, blockedUntil)
                
                logger.warn { "Blokowanie klienta $clientId do $blockedUntil" }
                
                // Zapisanie zdarzenia blokady
                rateLimitRepository.save(
                    RateLimitEntry(
                        id = java.util.UUID.randomUUID().toString(),
                        clientId = clientId,
                        type = type,
                        requestCount = currentCount,
                        limit = limit,
                        windowStart = Instant.now().minus(windowDuration),
                        windowEnd = Instant.now(),
                        isBlocked = true,
                        createdAt = Instant.now()
                    )
                )
                
                return Pair(false, "Przekroczono limit żądań - klient zablokowany na ${blockDuration.toMinutes()} minut")
            }
            
            return Pair(false, "Przekroczono limit żądań: $limit na ${windowDuration.toMinutes()} minut")
        }
        
        // Inkrementacja licznika
        val newCount = currentCount + 1
        requestCounter.put(getCacheKey(clientId, type), newCount)
        
        logger.debug { "Zezwalono na żądanie dla klienta: $clientId, licznik: $newCount/$limit" }
        
        return Pair(true, "")
    }
    
    /**
     * Resetuje licznik dla klienta
     * @param clientId Identyfikator klienta
     * @param type Typ limitu
     */
    fun resetCounter(clientId: String, type: RateLimitType) {
        logger.info { "Resetowanie licznika dla klienta: $clientId, typ: $type" }
        requestCounter.invalidate(getCacheKey(clientId, type))
    }
    
    /**
     * Odblokowuje klienta
     * @param clientId Identyfikator klienta
     */
    fun unblockClient(clientId: String) {
        logger.info { "Odblokowywanie klienta: $clientId" }
        blockedClients.invalidate(clientId)
    }
    
    /**
     * Pobiera statystyki limitów dla klienta
     * @param clientId Identyfikator klienta
     * @return Mapa z licznikami dla różnych typów limitów
     */
    fun getClientStats(clientId: String): Map<RateLimitType, Long> {
        val stats = mutableMapOf<RateLimitType, Long>()
        
        RateLimitType.values().forEach { type ->
            val count = requestCounter.getIfPresent(getCacheKey(clientId, type)) ?: 0
            stats[type] = count
        }
        
        return stats
    }
    
    /**
     * Czyści stare wpisy z bazy danych
     * @return Liczba usuniętych wpisów
     */
    suspend fun cleanupOldEntries(): Int {
        logger.info { "Czyszczenie starych wpisów limitów żądań" }
        return rateLimitRepository.deleteOlderThan(Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS))
    }
    
    /**
     * Pobiera historię limitów dla klienta
     * @param clientId Identyfikator klienta
     * @param limit Maksymalna liczba wpisów
     * @return Lista wpisów limitów
     */
    suspend fun getClientHistory(clientId: String, limit: Int = 100): List<RateLimitEntry> {
        logger.debug { "Pobieranie historii limitów dla klienta: $clientId" }
        return rateLimitRepository.findByClientId(clientId, limit)
    }
    
    /**
     * Sprawdza czy klient powinien zostać zablokowany
     * @param clientId Identyfikator klienta
     * @param type Typ limitu
     * @return True jeśli klient powinien zostać zablokowany
     */
    private suspend fun shouldBlockClient(clientId: String, type: RateLimitType): Boolean {
        // Sprawdzenie liczby ostatnich przekroczeń
        val recentViolations = rateLimitRepository.countRecentViolations(
            clientId = clientId,
            type = type,
            since = Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS)
        )
        
        // Blokowanie po 3 przekroczeniach w ciągu godziny
        return recentViolations >= 3
    }
    
    /**
     * Pobiera domyślny limit dla danego typu
     * @param type Typ limitu
     * @return Limit żądań
     */
    private fun getDefaultLimit(type: RateLimitType): Int {
        return when (type) {
            RateLimitType.IP_BASED -> 100 // 100 żądań na godzinę z IP
            RateLimitType.TOKEN_BASED -> 1000 // 1000 żądań na godzinę z tokena
            RateLimitType.USER_BASED -> 500 // 500 żądań na godzinę na użytkownika
            RateLimitType.ENDPOINT_SPECIFIC -> 50 // 50 żądań na minutę na endpoint
        }
    }
    
    /**
     * Pobiera czas trwania okna dla danego typu
     * @param type Typ limitu
     * @return Czas trwania okna
     */
    private fun getWindowDuration(type: RateLimitType): Duration {
        return when (type) {
            RateLimitType.IP_BASED -> Duration.ofHours(1)
            RateLimitType.TOKEN_BASED -> Duration.ofHours(1)
            RateLimitType.USER_BASED -> Duration.ofHours(1)
            RateLimitType.ENDPOINT_SPECIFIC -> Duration.ofMinutes(1)
        }
    }
    
    /**
     * Pobiera czas blokady dla danego typu
     * @param type Typ limitu
     * @return Czas blokady
     */
    private fun getBlockDuration(type: RateLimitType): Duration {
        return when (type) {
            RateLimitType.IP_BASED -> Duration.ofMinutes(30)
            RateLimitType.TOKEN_BASED -> Duration.ofMinutes(15)
            RateLimitType.USER_BASED -> Duration.ofMinutes(20)
            RateLimitType.ENDPOINT_SPECIFIC -> Duration.ofMinutes(5)
        }
    }
    
    /**
     * Generuje klucz cache
     * @param clientId Identyfikator klienta
     * @param type Typ limitu
     * @return Klucz cache
     */
    private fun getCacheKey(clientId: String, type: RateLimitType): String {
        return "${clientId}:${type.name}"
    }
}