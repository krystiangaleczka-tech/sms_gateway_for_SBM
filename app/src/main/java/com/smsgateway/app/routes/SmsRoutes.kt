package com.smsgateway.app.routes

import com.smsgateway.app.database.SmsMessage
import com.smsgateway.app.database.SmsRepository
import com.smsgateway.app.database.SmsStatus
import com.smsgateway.app.models.dto.SmsRequest
import com.smsgateway.app.models.dto.SmsResponse
import com.smsgateway.app.workers.WorkManagerService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Konfiguruje routingi dla endpointów SMS
 */
fun Route.smsRoutes(smsRepository: SmsRepository, workManagerService: WorkManagerService) {
    
    /**
     * POST /api/v1/sms/queue
     * Kolejkowanie nowej wiadomości SMS
     */
    authenticate("auth-bearer") {
        post("/api/v1/sms/queue") {
            val smsRequest = call.receive<SmsRequest>()
            
            // Konwersja czasu wizyty z ISO 8601 na timestamp
            val appointmentTime = Instant.parse(smsRequest.appointmentTime).toEpochMilli()
            
            // Obliczanie czasów zgodnie z PRD
            val currentTime = System.currentTimeMillis()
            val queueTime = appointmentTime - (18 * 60 * 60 * 1000) // 18 godzin przed wizytą
            val sendTime = appointmentTime - (24 * 60 * 60 * 1000) // 24 godziny przed wizytą
            
            // Obliczanie liczby części SMS
            val partsCount = calculateSmsParts(smsRequest.message)
            
            // Tworzenie nowej wiadomości SMS
            val smsMessage = SmsMessage(
                phoneNumber = smsRequest.phoneNumber,
                messageContent = smsRequest.message,
                status = if (queueTime <= currentTime) SmsStatus.SCHEDULED else SmsStatus.QUEUED,
                createdAt = currentTime,
                scheduledAt = if (queueTime <= currentTime) sendTime else queueTime,
                sentAt = null,
                errorMessage = null,
                retryCount = 0,
                maxRetries = 3
            )
            
            // Zapis do bazy danych
            val messageId = smsRepository.insertSms(smsMessage)
            
            // Pobranie zapisanej wiadomości z ID
            val savedMessage = smsRepository.getSmsById(messageId)
            
            if (savedMessage != null) {
                // Zaplanowanie wysyłki SMS przez WorkManager
                workManagerService.scheduleSmsSending(savedMessage)
                
                call.respond(
                    HttpStatusCode.Created,
                    mapOf(
                        "id" to messageId,
                        "status" to savedMessage.status.name,
                        "message" to "SMS queued successfully",
                        "parts" to partsCount,
                        "scheduledAt" to savedMessage.scheduledAt
                    )
                )
            } else {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf(
                        "error" to "Failed to save SMS message",
                        "message" to "SMS was not saved to database"
                    )
                )
            }
        }
    }
    
    /**
     * GET /api/v1/sms/status/{id}
     * Pobieranie statusu wiadomości SMS po ID
     */
    authenticate("auth-bearer") {
        get("/api/v1/sms/status/{id}") {
            val id = call.parameters["id"]?.toLong()
                ?: throw IllegalArgumentException("ID must be a valid number")
            
            val smsMessage = smsRepository.getSmsById(id)
                ?: throw NoSuchElementException("SMS with ID $id not found")
            
            call.respond(
                HttpStatusCode.OK,
                mapOf<String, Any?>(
                    "id" to smsMessage.id,
                    "phoneNumber" to smsMessage.phoneNumber,
                    "message" to smsMessage.messageContent,
                    "status" to smsMessage.status.name,
                    "createdAt" to smsMessage.createdAt,
                    "scheduledAt" to smsMessage.scheduledAt,
                    "sentAt" to smsMessage.sentAt,
                    "errorMessage" to smsMessage.errorMessage,
                    "retryCount" to smsMessage.retryCount
                )
            )
        }
    }
    
    /**
     * GET /api/v1/sms/history
     * Pobieranie historii wiadomości SMS z paginacją
     */
    authenticate("auth-bearer") {
        get("/api/v1/sms/history") {
            // Parametry paginacji
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            val status = call.request.queryParameters["status"]
            
            // Walidacja parametrów
            if (page < 1) throw IllegalArgumentException("Page must be >= 1")
            if (limit < 1 || limit > 100) throw IllegalArgumentException("Limit must be between 1 and 100")
            
            val paginatedResult = smsRepository.getSmsWithPagination(page, limit, status)
            
            val response: List<Map<String, Any?>> = paginatedResult.data.map { sms ->
                mapOf<String, Any?>(
                    "id" to sms.id,
                    "phoneNumber" to sms.phoneNumber,
                    "message" to sms.messageContent,
                    "status" to sms.status.name,
                    "createdAt" to sms.createdAt,
                    "scheduledAt" to sms.scheduledAt,
                    "sentAt" to sms.sentAt,
                    "errorMessage" to sms.errorMessage,
                    "retryCount" to sms.retryCount
                )
            }
            
            call.respond(
                HttpStatusCode.OK,
                mapOf<String, Any>(
                    "data" to response,
                    "pagination" to mapOf(
                        "total" to paginatedResult.total,
                        "page" to paginatedResult.page,
                        "limit" to paginatedResult.limit,
                        "totalPages" to paginatedResult.totalPages,
                        "hasNextPage" to paginatedResult.hasNextPage,
                        "hasPreviousPage" to paginatedResult.hasPreviousPage
                    )
                )
            )
        }
    }
    
    /**
     * DELETE /api/v1/sms/cancel/{id}
     * Anulowanie wiadomości SMS
     */
    authenticate("auth-bearer") {
        delete("/api/v1/sms/cancel/{id}") {
            val id = call.parameters["id"]?.toLong()
                ?: throw IllegalArgumentException("ID must be a valid number")
            
            // Sprawdzenie czy wiadomość istnieje
            val smsMessage = smsRepository.getSmsById(id)
                ?: throw NoSuchElementException("SMS with ID $id not found")
            
            // Sprawdzenie czy wiadomość może być anulowana
            if (smsMessage.status in listOf(SmsStatus.SENT, SmsStatus.CANCELLED)) {
                throw IllegalArgumentException("SMS with status ${smsMessage.status.name} cannot be cancelled")
            }
            
            // Anulowanie wiadomości
            val updatedRows = smsRepository.updateSmsStatus(id, SmsStatus.CANCELLED)
            
            if (updatedRows > 0) {
                // Anulowanie zaplanowanego zadania WorkManager
                workManagerService.cancelSmsSending(id)
                
                call.respond(
                    HttpStatusCode.OK,
                    mapOf(
                        "id" to id,
                        "status" to SmsStatus.CANCELLED.name,
                        "message" to "SMS cancelled successfully"
                    )
                )
            } else {
                throw IllegalStateException("Failed to cancel SMS")
            }
        }
    }
}

/**
 * Oblicza liczbę części SMS na podstawie długości wiadomości
 * GSM-7: 160 znaków na część, Unicode: 70 znaków na część
 */
private fun calculateSmsParts(message: String): Int {
    // Uproszczona wersja - zakładamy GSM-7 encoding
    // W pełnej implementacji trzeba by sprawdzać znaki Unicode
    val gsm7CharsPerPart = 160
    val maxParts = 10 // Maksymalnie 10 części SMS
    
    return if (message.length <= gsm7CharsPerPart) {
        1
    } else {
        kotlin.math.ceil(message.length.toDouble() / gsm7CharsPerPart).toInt().coerceAtMost(maxParts)
    }
}