package com.smsgateway.app.api

import com.smsgateway.app.api.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Response

/**
 * Serwis API do obsługi operacji związanych ze zdrowiem systemu
 * Komunikuje się z endpointami Health backendu
 */
class HealthApiService {
    private val apiService = ApiService.getInstance()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Pobiera ogólny status zdrowia systemu
     * @return SystemHealthApi ze statusem zdrowia systemu
     * @throws ApiException w przypadku błędu
     */
    suspend fun getSystemHealth(): SystemHealthApi {
        return withContext(Dispatchers.IO) {
            val response = apiService.get("/health")
            parseResponse(response, SystemHealthApi::class.java)
        }
    }
    
    /**
     * Pobiera szczegółowy status zdrowia systemu
     * @return DetailedHealthApi ze szczegółowym statusem zdrowia
     * @throws ApiException w przypadku błędu
     */
    suspend fun getDetailedHealth(): DetailedHealthApi {
        return withContext(Dispatchers.IO) {
            val response = apiService.get("/health/detailed")
            parseResponse(response, DetailedHealthApi::class.java)
        }
    }
    
    /**
     * Pobiera status zdrowia bazy danych
     * @return DatabaseHealthApi ze statusem zdrowia bazy danych
     * @throws ApiException w przypadku błędu
     */
    suspend fun getDatabaseHealth(): DatabaseHealthApi {
        return withContext(Dispatchers.IO) {
            val response = apiService.get("/health/database")
            parseResponse(response, DatabaseHealthApi::class.java)
        }
    }
    
    /**
     * Pobiera status zdrowia kolejki SMS
     * @return QueueHealthApi ze statusem zdrowia kolejki
     * @throws ApiException w przypadku błędu
     */
    suspend fun getQueueHealth(): QueueHealthApi {
        return withContext(Dispatchers.IO) {
            val response = apiService.get("/health/queue")
            parseResponse(response, QueueHealthApi::class.java)
        }
    }
    
    /**
     * Pobiera status zdrowia usługi SMS
     * @return SmsServiceHealthApi ze statusem zdrowia usługi SMS
     * @throws ApiException w przypadku błędu
     */
    suspend fun getSmsServiceHealth(): SmsServiceHealthApi {
        return withContext(Dispatchers.IO) {
            val response = apiService.get("/health/sms-service")
            parseResponse(response, SmsServiceHealthApi::class.java)
        }
    }
    
    /**
     * Pobiera metryki wydajności systemu
     * @return PerformanceMetricsApi z metrykami wydajności
     * @throws ApiException w przypadku błędu
     */
    suspend fun getPerformanceMetrics(): PerformanceMetricsApi {
        return withContext(Dispatchers.IO) {
            val response = apiService.get("/health/performance")
            parseResponse(response, PerformanceMetricsApi::class.java)
        }
    }
    
    /**
     * Pobiera status WorkManagera
     * @return WorkManagerHealthApi ze statusem WorkManagera
     * @throws ApiException w przypadku błędu
     */
    suspend fun getWorkManagerHealth(): WorkManagerHealthApi {
        return withContext(Dispatchers.IO) {
            val response = apiService.get("/health/workmanager")
            parseResponse(response, WorkManagerHealthApi::class.java)
        }
    }
    
    /**
     * Pobiera status pamięci systemowej
     * @return MemoryHealthApi ze statusem pamięci
     * @throws ApiException w przypadku błędu
     */
    suspend fun getMemoryHealth(): MemoryHealthApi {
        return withContext(Dispatchers.IO) {
            val response = apiService.get("/health/memory")
            parseResponse(response, MemoryHealthApi::class.java)
        }
    }
    
    /**
     * Sprawdza łączność z zewnętrznymi usługami
     * @return ExternalServicesHealthApi ze statusem usług zewnętrznych
     * @throws ApiException w przypadku błędu
     */
    suspend fun getExternalServicesHealth(): ExternalServicesHealthApi {
        return withContext(Dispatchers.IO) {
            val response = apiService.get("/health/external-services")
            parseResponse(response, ExternalServicesHealthApi::class.java)
        }
    }
    
    /**
     * Wymusza odświeżenie statusu zdrowia systemu
     * @return SystemHealthApi ze zaktualizowanym statusem zdrowia
     * @throws ApiException w przypadku błędu
     */
    suspend fun refreshHealthStatus(): SystemHealthApi {
        return withContext(Dispatchers.IO) {
            val response = apiService.post("/health/refresh", null)
            parseResponse(response, SystemHealthApi::class.java)
        }
    }
    
    /**
     * Uruchamia diagnostykę systemu
     * @return DiagnosticResultApi z wynikami diagnostyki
     * @throws ApiException w przypadku błędu
     */
    suspend fun runDiagnostics(): DiagnosticResultApi {
        return withContext(Dispatchers.IO) {
            val response = apiService.post("/health/diagnostics", null)
            parseResponse(response, DiagnosticResultApi::class.java)
        }
    }
    
    /**
     * Pobiera logi systemu
     * @param level Poziom logów (opcjonalny)
     * @param limit Maksymalna liczba logów (domyślnie 100)
     * @return List<SystemLogApi> z listą logów systemowych
     * @throws ApiException w przypadku błędu
     */
    suspend fun getSystemLogs(level: LogLevel? = null, limit: Int = 100): List<SystemLogApi> {
        return withContext(Dispatchers.IO) {
            val queryParams = mutableListOf<String>()
            queryParams.add("limit=$limit")
            
            level?.let { queryParams.add("level=${it.name}") }
            
            val queryString = if (queryParams.isNotEmpty()) {
                "?${queryParams.joinToString("&")}"
            } else {
                ""
            }
            
            val response = apiService.get("/health/logs$queryString")
            
            val responseBody = response.body?.string() ?: throw ApiException("Empty response body")
            val logList = json.decodeFromString(
                ListSerializer(SystemLogApi.serializer()),
                responseBody
            )
            
            logList
        }
    }
    
    /**
     * Pobiera statystyki błędów systemu
     * @param timeFrame Okres czasowy (LAST_HOUR, LAST_DAY, LAST_WEEK)
     * @return ErrorStatsApi ze statystykami błędów
     * @throws ApiException w przypadku błędu
     */
    suspend fun getErrorStats(timeFrame: TimeFrame = TimeFrame.LAST_DAY): ErrorStatsApi {
        return withContext(Dispatchers.IO) {
            val response = apiService.get("/health/errors?timeFrame=${timeFrame.name}")
            parseResponse(response, ErrorStatsApi::class.java)
        }
    }
    
    /**
     * Parsuje odpowiedź API do obiektu
     * @param response Odpowiedź HTTP
     * @param clazz Klasa docelowa
     * @return Obiekt sparsowany z JSON
     */
    private fun <T> parseResponse(response: Response, clazz: Class<T>): T {
        val responseBody = response.body?.string() ?: throw ApiException("Empty response body")
        return json.decodeFromString(clazz.kotlinObjectType, responseBody)
    }
}