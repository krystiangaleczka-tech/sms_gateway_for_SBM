package com.smsgateway.app.api

import com.smsgateway.app.monitoring.models.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Główna usługa API odpowiedzialna za komunikację z backendem
 * Implementuje wzorzec Singleton dla zapewnienia jednej instancji klienta HTTP
 */
class ApiService private constructor() {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor())
        .addInterceptor(LoggingInterceptor())
        .build()
    
    /**
     * Wykonuje zapytanie GET do API
     * @param url URL endpointu
     * @return Response z danymi JSON
     * @throws ApiException w przypadku błędu
     */
    suspend fun get(url: String): Response {
        return try {
            val request = Request.Builder()
                .url(getBaseUrl() + url)
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            handleResponse(response)
        } catch (e: IOException) {
            throw ApiException("Network error: ${e.message}", e)
        }
    }
    
    /**
     * Wykonuje zapytanie POST do API
     * @param url URL endpointu
     * @param body Obiekt do serializacji jako JSON
     * @return Response z danymi JSON
     * @throws ApiException w przypadku błędu
     */
    suspend fun post(url: String, body: Any? = null): Response {
        return try {
            val requestBody = if (body != null) {
                val jsonBody = json.encodeToString(JsonElementSerializer, body)
                jsonBody.toRequestBody("application/json".toMediaType())
            } else {
                "".toRequestBody("application/json".toMediaType())
            }
            
            val request = Request.Builder()
                .url(getBaseUrl() + url)
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            handleResponse(response)
        } catch (e: IOException) {
            throw ApiException("Network error: ${e.message}", e)
        }
    }
    
    /**
     * Wykonuje zapytanie DELETE do API
     * @param url URL endpointu
     * @return Response z danymi JSON
     * @throws ApiException w przypadku błędu
     */
    suspend fun delete(url: String): Response {
        return try {
            val request = Request.Builder()
                .url(getBaseUrl() + url)
                .delete()
                .build()
            
            val response = client.newCall(request).execute()
            handleResponse(response)
    
    /**
     * Raportowanie błędu do backendu
     */
    suspend fun reportError(error: AppError): Response {
        return post("/errors/report", error)
    }
    
    /**
     * Pobieranie listy błędów z backendu
     */
    suspend fun getErrors(limit: Int = 50): Response {
        return get("/errors?limit=$limit")
    }
    
    /**
     * Pobieranie szczegółów błędu z backendu
     */
    suspend fun getError(errorId: String): Response {
        return get("/errors/$errorId")
    }
    
    /**
     * Pobieranie metryk systemu z backendu
     */
    suspend fun getSystemMetrics(): Response {
        return get("/monitoring/metrics")
    }
    
    /**
     * Pobieranie statusu systemu z backendu
     */
    suspend fun getSystemHealth(): Response {
        return get("/monitoring/health")
    }
    
    /**
     * Pobieranie alertów systemu z backendu
     */
    suspend fun getSystemAlerts(limit: Int = 20): Response {
        return get("/monitoring/alerts?limit=$limit")
    }
        } catch (e: IOException) {
            throw ApiException("Network error: ${e.message}", e)
        }
    }
    
    private fun handleResponse(response: Response): Response {
        if (!response.isSuccessful) {
            throw ApiException(
                "API Error: ${response.code} ${response.message}",
                response.code,
                response.body?.string()
            )
        }
        return response
    }
    
    private fun getBaseUrl(): String {
        // W produkcyjnej wersji powinno pochodzić z konfiguracji
        return "http://localhost:8080/api/v1"
    }
    
    companion object {
        @Volatile
        private var INSTANCE: ApiService? = null
        
        fun getInstance(): ApiService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ApiService().also { INSTANCE = it }
            }
        }
    }
}

/**
 * Interceptor dodający nagłówek autentykacji Bearer Token
 */
private class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()
            .header("Authorization", "Bearer smsgateway-api-token-2024-secure")
            .header("Content-Type", "application/json")
        
        val request = requestBuilder.build()
        return chain.proceed(request)
    }
}

/**
 * Interceptor do logowania zapytań i odpowiedzi API
 */
private class LoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        
        // Logowanie zapytania
        println("API Request: ${request.method} ${request.url}")
        
        val startTime = System.nanoTime()
        val response = chain.proceed(request)
        val endTime = System.nanoTime()
        
        // Logowanie odpowiedzi
        println("API Response: ${response.code} (${(endTime - startTime) / 1e6}ms)")
        
        return response
    }
}

/**
 * Wyjątek reprezentujący błędy API
 */
class ApiException(
    message: String,
    val code: Int = -1,
    val errorBody: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)