package com.smsgateway.app.api

import com.smsgateway.app.api.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.*

/**
 * Serwis API do obsługi operacji związanych z SMS
 * Komunikuje się z endpointami SMS backendu
 */
class SmsApiService {
    private val apiService = ApiService.getInstance()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
    
    /**
     * Wysyła SMS przez API
     * @param request Obiekt żądania wysłania SMS
     * @return SendSmsResponse z odpowiedzią serwera
     * @throws ApiException w przypadku błędu
     */
    suspend fun sendSms(request: SendSmsRequest): SendSmsResponse {
        return withContext(Dispatchers.IO) {
            val response = apiService.post("/sms/queue", request)
            parseResponse(response, SendSmsResponse::class.java)
        }
    }
    
    /**
     * Pobiera status wiadomości SMS
     * @param id ID wiadomości
     * @return SmsStatusResponse ze statusem wiadomości
     * @throws ApiException w przypadku błędu
     */
    suspend fun getSmsStatus(id: String): SmsStatusResponse {
        return withContext(Dispatchers.IO) {
            val response = apiService.get("/sms/status/$id")
            parseResponse(response, SmsStatusResponse::class.java)
        }
    }
    
    /**
     * Pobiera historię wiadomości SMS z paginacją
     * @param page Numer strony (domyślnie 1)
     * @param limit Liczba elementów na stronie (domyślnie 20)
     * @param status Opcjonalny filtr statusu
     * @return PaginatedResponse z listą wiadomości
     * @throws ApiException w przypadku błędu
     */
    suspend fun getSmsHistory(
        page: Int = 1,
        limit: Int = 20,
        status: SmsStatus? = null
    ): PaginatedResponse<SmsMessageApi> {
        return withContext(Dispatchers.IO) {
            val queryParams = mutableListOf<String>()
            queryParams.add("page=$page")
            queryParams.add("limit=$limit")
            
            status?.let { queryParams.add("status=${it.name}") }
            
            val queryString = if (queryParams.isNotEmpty()) {
                "?${queryParams.joinToString("&")}"
            } else {
                ""
            }
            
            val response = apiService.get("/sms/history$queryString")
            
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
     * Anuluje wiadomość SMS
     * @param id ID wiadomości do anulowania
     * @return Boolean czy operacja się powiodła
     * @throws ApiException w przypadku błędu
     */
    suspend fun cancelSms(id: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.delete("/sms/cancel/$id")
                response.isSuccessful
            } catch (e: ApiException) {
                // Jeśli wiadomość nie może być anulowana (np. już wysłana)
                // zwracamy false zamiast rzucać wyjątek
                false
            }
        }
    }
    
    /**
     * Pobiera szczegóły pojedynczej wiadomości SMS
     * @param id ID wiadomości
     * @return SmsMessageApi ze szczegółami wiadomości
     * @throws ApiException w przypadku błędu
     */
    suspend fun getSmsDetails(id: String): SmsMessageApi {
        return withContext(Dispatchers.IO) {
            val response = apiService.get("/sms/details/$id")
            parseResponse(response, SmsMessageApi::class.java)
        }
    }
    
    /**
     * Konwertuje datę z API na obiekt Date
     * @param dateString Data w formacie string z API
     * @return Obiekt Date
     */
    fun parseDate(dateString: String?): Date? {
        return try {
            dateString?.let { dateFormat.parse(it) }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Konwertuje obiekt Date na string w formacie API
     * @param date Obiekt Date
     * @return String w formacie API
     */
    fun formatDate(date: Date): String {
        return dateFormat.format(date)
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