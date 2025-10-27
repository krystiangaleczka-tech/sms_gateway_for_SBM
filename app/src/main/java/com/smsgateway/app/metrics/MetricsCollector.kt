package com.smsgateway.app.metrics

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Klasa do zbierania metryk systemowych
 * 
 * Monitoruje kluczowe wskaźniki wydajności i użycia:
 * - Liczba wysłanych SMS-ów
 * - Czas odpowiedzi API
 * - Błędy systemowe
 * - Stan kolejki
 */
class MetricsCollector {
    
    private val smsSentCount = AtomicLong(0)
    private val smsFailedCount = AtomicLong(0)
    private val apiResponseTimes = ConcurrentHashMap<String, MutableList<Long>>()
    private val systemErrors = AtomicLong(0)
    private val queueSize = AtomicLong(0)
    
    /**
     * Zwiększa licznik wysłanych SMS-ów
     */
    fun incrementSmsSent() {
        smsSentCount.incrementAndGet()
    }
    
    /**
     * Zwiększa licznik wysłanych SMS-ów (alias dla incrementSmsSent)
     */
    fun incrementSentSms() {
        smsSentCount.incrementAndGet()
    }
    
    /**
     * Zwiększa licznik zakolejkowanych SMS-ów
     */
    fun incrementQueuedSms() {
        smsSentCount.incrementAndGet()
    }
    
    /**
     * Zwiększa licznik nieudanych SMS-ów
     */
    fun incrementSmsFailed() {
        smsFailedCount.incrementAndGet()
    }
    
    /**
     * Zwiększa licznik nieudanych SMS-ów (alias dla incrementSmsFailed)
     */
    fun incrementFailedSms() {
        smsFailedCount.incrementAndGet()
    }
    
    /**
     * Zwiększa licznik błędów systemowych
     */
    fun incrementError(type: String) {
        systemErrors.incrementAndGet()
    }
    
    /**
     * Rejestruje czas odpowiedzi API
     */
    fun recordApiResponseTime(endpoint: String, responseTimeMs: Long) {
        val times = apiResponseTimes.getOrPut(endpoint) { mutableListOf() }
        times.add(responseTimeMs)
        
        // Ogranicz rozmiar listy do ostatnich 1000 pomiarów
        if (times.size > 1000) {
            times.removeAt(0)
        }
    }
    
    /**
     * Zwiększa licznik błędów systemowych
     */
    fun incrementSystemErrors() {
        systemErrors.incrementAndGet()
    }
    
    /**
     * Aktualizuje rozmiar kolejki
     */
    fun updateQueueSize(size: Long) {
        queueSize.set(size)
    }
    
    /**
     * Zwraca średni czas odpowiedzi dla danego endpointu
     */
    fun getAverageResponseTime(endpoint: String): Double {
        val times = apiResponseTimes[endpoint] ?: return 0.0
        return if (times.isEmpty()) 0.0 else times.average()
    }
    
    /**
     * Zwraca wszystkie metryki jako mapę
     */
    fun getAllMetrics(): Map<String, Any> {
        val responseTimeAverages = mutableMapOf<String, Double>()
        apiResponseTimes.forEach { (endpoint, times) ->
            responseTimeAverages[endpoint] = if (times.isEmpty()) 0.0 else times.average()
        }
        
        return mapOf(
            "smsSent" to smsSentCount.get(),
            "smsFailed" to smsFailedCount.get(),
            "systemErrors" to systemErrors.get(),
            "queueSize" to queueSize.get(),
            "averageResponseTimes" to responseTimeAverages,
            "timestamp" to System.currentTimeMillis()
        )
    }
    
    /**
     * Resetuje wszystkie metryki
     */
    fun reset() {
        smsSentCount.set(0)
        smsFailedCount.set(0)
        systemErrors.set(0)
        queueSize.set(0)
        apiResponseTimes.clear()
    }
}