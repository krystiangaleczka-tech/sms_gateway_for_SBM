package com.smsgateway.app.api

import com.smsgateway.app.api.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Response

/**
 * Serwis API do obsługi operacji związanych z kolejką SMS
 * Komunikuje się z endpointami Queue backendu
 */
class QueueApiService {
    private val apiService = ApiService.getInstance()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Pobiera statystyki kolejki SMS
     * @return QueueStatsApi ze statystykami kolejki
     * @throws ApiException w przypadku błędu
     */
    suspend fun getQueueStats(): QueueStatsApi {
        return withContext(Dispatchers.IO) {
            val response = apiService.get("/sms/queue/stats")
            parseResponse(response, QueueStatsApi::class.java)
        }
    }
    
    /**
     * Pobiera listę wiadomości w kolejce z paginacją
     * @param page Numer strony (domyślnie 1)
     * @param limit Liczba elementów na stronie (domyślnie 20)
     * @param status Opcjonalny filtr statusu
     * @param priority Opcjonalny filtr priorytetu
     * @return PaginatedResponse z listą wiadomości w kolejce
     * @throws ApiException w przypadku błędu
     */
    suspend fun getQueueMessages(
        page: Int = 1,
        limit: Int = 20,
        status: SmsStatus? = null,
        priority: SmsPriority? = null
    ): PaginatedResponse<SmsMessageApi> {
        return withContext(Dispatchers.IO) {
            val queryParams = mutableListOf<String>()
            queryParams.add("page=$page")
            queryParams.add("limit=$limit")
            
            status?.let { queryParams.add("status=${it.name}") }
            priority?.let { queryParams.add("priority=${it.name}") }
            
            val queryString = if (queryParams.isNotEmpty()) {
                "?${queryParams.joinToString("&")}"
            } else {
                ""
            }
            
            val response = apiService.get("/sms/queue$queryString")
            
            // Parsowanie odpowiedzi
            val responseBody = response.body?.string() ?: throw ApiException("Empty response body")
            val paginatedResponse = json.decodeFromString(
                PaginatedResponse.serializer(SmsMessageApi.serializer()),
                responseBody
            )
            
            paginatedResponse
        }
    }
    
    /**
     * Pobiera wiadomości o wysokim priorytecie w kolejce
     * @param limit Maksymalna liczba wiadomości do pobrania (domyślnie 10)
     * @return Lista wiadomości o wysokim priorytecie
     * @throws ApiException w przypadku błędu
     */
    suspend fun getHighPriorityMessages(limit: Int = 10): List<SmsMessageApi> {
        return withContext(Dispatchers.IO) {
            val response = apiService.get("/sms/queue/high-priority?limit=$limit")
            
            val responseBody = response.body?.string() ?: throw ApiException("Empty response body")
            val messageList = json.decodeFromString(
                ListSerializer(SmsMessageApi.serializer()),
                responseBody
            )
            
            messageList
        }
    }
    
    /**
     * Pobiera wiadomości zablokowane w kolejce
     * @param limit Maksymalna liczba wiadomości do pobrania (domyślnie 10)
     * @return Lista wiadomości zablokowanych
     * @throws ApiException w przypadku błędu
     */
    suspend fun getStuckMessages(limit: Int = 10): List<SmsMessageApi> {
        return withContext(Dispatchers.IO) {
            val response = apiService.get("/sms/queue/stuck?limit=$limit")
            
            val responseBody = response.body?.string() ?: throw ApiException("Empty response body")
            val messageList = json.decodeFromString(
                ListSerializer(SmsMessageApi.serializer()),
                responseBody
            )
            
            messageList
        }
    }
    
    /**
     * Próbuje ponownie wysłać wiadomość z kolejki
     * @param id ID wiadomości do ponownego wysłania
     * @return Boolean czy operacja się powiodła
     * @throws ApiException w przypadku błędu
     */
    suspend fun retryMessage(id: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.post("/sms/queue/retry/$id", null)
                response.isSuccessful
            } catch (e: ApiException) {
                // Jeśli wiadomość nie może być ponownie wysłana
                // zwracamy false zamiast rzucać wyjątek
                false
            }
        }
    }
    
    /**
     * Wznawia przetwarzanie kolejki
     * @return QueueStatsApi ze zaktualizowanymi statystykami
     * @throws ApiException w przypadku błędu
     */
    suspend fun resumeQueue(): QueueStatsApi {
        return withContext(Dispatchers.IO) {
            val response = apiService.post("/sms/queue/resume", null)
            parseResponse(response, QueueStatsApi::class.java)
        }
    }
    
    /**
     * Wstrzymuje przetwarzanie kolejki
     * @return QueueStatsApi ze zaktualizowanymi statystykami
     * @throws ApiException w przypadku błędu
     */
    suspend fun pauseQueue(): QueueStatsApi {
        return withContext(Dispatchers.IO) {
            val response = apiService.post("/sms/queue/pause", null)
            parseResponse(response, QueueStatsApi::class.java)
        }
    }
    
    /**
     * Czyści kolejkę z wiadomości o określonym statusie
     * @param status Status wiadomości do usunięcia
     * @return Liczba usuniętych wiadomości
     * @throws ApiException w przypadku błędu
     */
    suspend fun clearQueueByStatus(status: SmsStatus): Int {
        return withContext(Dispatchers.IO) {
            val response = apiService.delete("/sms/queue/clear?status=${status.name}")
            
            val responseBody = response.body?.string() ?: throw ApiException("Empty response body")
            val result = json.decodeFromString<QueueClearResult>(responseBody)
            
            result.deletedCount
        }
    }
    
    /**
     * Aktualizuje priorytet wiadomości w kolejce
     * @param id ID wiadomości
     * @param priority Nowy priorytet
     * @return Boolean czy operacja się powiodła
     * @throws ApiException w przypadku błędu
     */
    suspend fun updateMessagePriority(id: String, priority: SmsPriority): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = mapOf("priority" to priority.name)
                val response = apiService.post("/sms/queue/priority/$id", request)
                response.isSuccessful
            } catch (e: ApiException) {
                false
            }
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
    
    /**
     * Model wyniku czyszczenia kolejki
     */
    @kotlinx.serialization.Serializable
    private data class QueueClearResult(
        val deletedCount: Int,
        val message: String
    )
}