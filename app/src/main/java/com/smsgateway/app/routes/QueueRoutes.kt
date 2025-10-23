package com.smsgateway.app.routes

import com.smsgateway.app.database.SmsMessage
import com.smsgateway.app.database.SmsRepository
import com.smsgateway.app.database.SmsStatus
import com.smsgateway.app.database.SmsPriority
import com.smsgateway.app.models.dto.*
import com.smsgateway.app.queue.SmsQueueService
import com.smsgateway.app.health.HealthChecker
import com.smsgateway.app.events.MetricsCollector
import com.smsgateway.app.retry.RetryService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Konfiguruje routingi dla endpointów zarządzania kolejką SMS
 */
fun Route.queueRoutes(
    smsRepository: SmsRepository,
    smsQueueService: SmsQueueService,
    healthChecker: HealthChecker,
    metricsCollector: MetricsCollector,
    retryService: RetryService
) {
    
    val logger = LoggerFactory.getLogger("QueueRoutes")
    
    authenticate("auth-bearer") {
        
        /**
         * POST /api/v1/sms/queue/enhanced
         * Kolejkowanie nowej wiadomości SMS z priorytetem i strategią retry
         */
        post("/api/v1/sms/queue/enhanced") {
            try {
                val queueRequest = call.receive<QueueSmsRequest>()
                
                // Walidacja numeru telefonu
                if (!isValidPhoneNumber(queueRequest.phoneNumber)) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid phone number format")
                    )
                    return@post
                }
                
                // Walidacja treści wiadomości
                if (queueRequest.message.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Message content cannot be empty")
                    )
                    return@post
                }
                
                // Tworzenie nowej wiadomości SMS z rozszerzonymi polami
                val currentTime = System.currentTimeMillis()
                val smsMessage = SmsMessage(
                    phoneNumber = queueRequest.phoneNumber,
                    messageContent = queueRequest.message,
                    status = SmsStatus.QUEUED,
                    priority = queueRequest.priority,
                    createdAt = currentTime,
                    scheduledAt = null, // Natychmiastowa wysyłka
                    sentAt = null,
                    errorMessage = null,
                    retryCount = 0,
                    maxRetries = 3,
                    retryStrategy = queueRequest.retryStrategy,
                    queuePosition = null,
                    metadata = queueRequest.metadata
                )
                
                // Dodanie do kolejki priorytetowej
                val queuedMessage = smsQueueService.enqueueSms(smsMessage)
                
                // Obliczanie liczby części SMS
                val partsCount = calculateSmsParts(queueRequest.message)
                
                // Publikacja zdarzenia
                metricsCollector.incrementCounter("sms_queued_enhanced")
                
                call.respond(
                    HttpStatusCode.Created,
                    mapOf(
                        "id" to queuedMessage.id,
                        "status" to queuedMessage.status.name,
                        "queuePosition" to queuedMessage.queuePosition,
                        "priority" to queuedMessage.priority.name,
                        "retryStrategy" to queuedMessage.retryStrategy.name,
                        "parts" to partsCount,
                        "message" to "SMS queued successfully with priority"
                    )
                )
                
            } catch (e: Exception) {
                logger.error("Error queuing SMS", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to queue SMS", "message" to e.message)
                )
            }
        }
        
        /**
         * GET /api/v1/sms/queue/stats
         * Pobieranie statystyk kolejki
         */
        get("/api/v1/sms/queue/stats") {
            try {
                val queueStats = smsQueueService.getQueueStats()
                val metrics = metricsCollector.getQueueMetrics()
                
                val response = QueueStatsResponse(
                    totalMessages = queueStats.totalMessages,
                    queuedMessages = queueStats.queuedMessages,
                    scheduledMessages = queueStats.scheduledMessages,
                    sendingMessages = queueStats.sendingMessages,
                    sentMessages = queueStats.sentMessages,
                    failedMessages = queueStats.failedMessages,
                    averageWaitTime = queueStats.averageWaitTime,
                    throughputPerHour = metrics.throughputPerHour,
                    errorRate = metrics.errorRate,
                    queueSizeByPriority = queueStats.queueSizeByPriority
                )
                
                call.respond(HttpStatusCode.OK, response)
                
            } catch (e: Exception) {
                logger.error("Error getting queue stats", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to get queue stats", "message" to e.message)
                )
            }
        }
        
        /**
         * POST /api/v1/sms/queue/control
         * Kontrolowanie kolejki (pauza/wznowienie/czyszczenie)
         */
        post("/api/v1/sms/queue/control") {
            try {
                val controlRequest = call.receive<QueueControlRequest>()
                
                when (controlRequest.action) {
                    QueueAction.PAUSE -> {
                        smsQueueService.pauseQueue()
                        call.respond(
                            HttpStatusCode.OK,
                            mapOf(
                                "status" to "PAUSED",
                                "message" to "Queue paused successfully",
                                "reason" to (controlRequest.reason ?: "Manual pause")
                            )
                        )
                    }
                    
                    QueueAction.RESUME -> {
                        smsQueueService.resumeQueue()
                        call.respond(
                            HttpStatusCode.OK,
                            mapOf(
                                "status" to "ACTIVE",
                                "message" to "Queue resumed successfully",
                                "reason" to (controlRequest.reason ?: "Manual resume")
                            )
                        )
                    }
                    
                    QueueAction.CLEAR -> {
                        val clearedCount = smsQueueService.clearQueue()
                        call.respond(
                            HttpStatusCode.OK,
                            mapOf(
                                "status" to "CLEARED",
                                "clearedCount" to clearedCount,
                                "message" to "Queue cleared successfully",
                                "reason" to (controlRequest.reason ?: "Manual clear")
                            )
                        )
                    }
                    
                    QueueAction.REORGANIZE -> {
                        smsQueueService.reorganizeQueue()
                        call.respond(
                            HttpStatusCode.OK,
                            mapOf(
                                "status" to "REORGANIZED",
                                "message" to "Queue reorganized successfully",
                                "reason" to (controlRequest.reason ?: "Manual reorganization")
                            )
                        )
                    }
                }
                
            } catch (e: Exception) {
                logger.error("Error controlling queue", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to control queue", "message" to e.message)
                )
            }
        }
        
        /**
         * DELETE /api/v1/sms/queue/clear
         * Czyszczenie kolejki z opcjonalnymi filtrami
         */
        delete("/api/v1/sms/queue/clear") {
            try {
                val clearRequest = call.receive<ClearQueueRequest>()
                
                var clearedCount = 0
                
                when {
                    // Czyszczenie po statusie
                    clearRequest.status != null -> {
                        val status = try {
                            SmsStatus.valueOf(clearRequest.status.uppercase())
                        } catch (e: IllegalArgumentException) {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("error" to "Invalid status: ${clearRequest.status}")
                            )
                            return@delete
                        }
                        clearedCount = smsQueueService.clearQueueByStatus(status)
                    }
                    
                    // Czyszczenie po priorytecie
                    clearRequest.priority != null -> {
                        clearedCount = smsQueueService.clearQueueByPriority(clearRequest.priority)
                    }
                    
                    // Czyszczenie wiadomości starszych niż
                    clearRequest.olderThan != null -> {
                        clearedCount = smsQueueService.clearQueueOlderThan(clearRequest.olderThan)
                    }
                    
                    // Czyszczenie całej kolejki
                    else -> {
                        clearedCount = smsQueueService.clearQueue()
                    }
                }
                
                val queueStats = smsQueueService.getQueueStats()
                
                val response = ClearQueueResponse(
                    clearedCount = clearedCount,
                    remainingCount = queueStats.queuedMessages,
                    message = "Cleared $clearedCount messages from queue"
                )
                
                call.respond(HttpStatusCode.OK, response)
                
            } catch (e: Exception) {
                logger.error("Error clearing queue", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to clear queue", "message" to e.message)
                )
            }
        }
        
        /**
         * POST /api/v1/sms/queue/reprioritize
         * Zmiana priorytetu wiadomości w kolejce
         */
        post("/api/v1/sms/queue/reprioritize") {
            try {
                val reprioritizeRequest = call.receive<ReprioritizeRequest>()
                
                // Pobranie wiadomości
                val smsMessage = smsRepository.getSmsById(reprioritizeRequest.messageId)
                    ?: throw NoSuchElementException("SMS with ID ${reprioritizeRequest.messageId} not found")
                
                // Sprawdzenie czy wiadomość może być repriorytetyzowana
                if (smsMessage.status !in listOf(SmsStatus.QUEUED, SmsStatus.SCHEDULED)) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf(
                            "error" to "SMS with status ${smsMessage.status.name} cannot be reprioritized"
                        )
                    )
                    return@post
                }
                
                val oldPriority = smsMessage.priority
                val oldPosition = smsMessage.queuePosition
                
                // Zmiana priorytetu
                val newPosition = smsQueueService.reprioritizeSms(
                    reprioritizeRequest.messageId,
                    reprioritizeRequest.newPriority
                )
                
                val response = ReprioritizeResponse(
                    messageId = reprioritizeRequest.messageId,
                    oldPriority = oldPriority,
                    newPriority = reprioritizeRequest.newPriority,
                    oldPosition = oldPosition,
                    newPosition = newPosition,
                    success = true,
                    message = "SMS priority changed successfully"
                )
                
                call.respond(HttpStatusCode.OK, response)
                
            } catch (e: NoSuchElementException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error("Error reprioritizing SMS", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to reprioritize SMS", "message" to e.message)
                )
            }
        }
        
        /**
         * GET /api/v1/sms/queue/status/{id}
         * Pobieranie szczegółowego statusu wiadomości w kolejce
         */
        get("/api/v1/sms/queue/status/{id}") {
            try {
                val id = call.parameters["id"]?.toLong()
                    ?: throw IllegalArgumentException("ID must be a valid number")
                
                val smsMessage = smsRepository.getSmsById(id)
                    ?: throw NoSuchElementException("SMS with ID $id not found")
                
                // Obliczanie szacowanego czasu wysyłki
                val estimatedSendTime = calculateEstimatedSendTime(smsMessage, smsQueueService)
                
                val response = QueueStatusResponse(
                    id = smsMessage.id,
                    status = smsMessage.status.name,
                    queuePosition = smsMessage.queuePosition,
                    estimatedSendTime = estimatedSendTime,
                    priority = smsMessage.priority,
                    retryCount = smsMessage.retryCount,
                    maxRetries = smsMessage.maxRetries
                )
                
                call.respond(HttpStatusCode.OK, response)
                
            } catch (e: NumberFormatException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ID must be a valid number"))
            } catch (e: NoSuchElementException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error("Error getting SMS queue status", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to get SMS status", "message" to e.message)
                )
            }
        }
        
        /**
         * GET /api/v1/health
         * Pobieranie statusu zdrowia systemu
         */
        get("/api/v1/health") {
            try {
                val systemHealth = healthChecker.performHealthCheck()
                
                val queueHealthResponse = QueueHealthResponse(
                    status = systemHealth.queueHealth.status.name,
                    size = systemHealth.queueHealth.size,
                    processingRate = systemHealth.queueHealth.processingRate,
                    averageWaitTime = systemHealth.queueHealth.averageWaitTime,
                    errorRate = systemHealth.queueHealth.errorRate
                )
                
                val response = HealthStatusResponse(
                    status = systemHealth.overallStatus.name,
                    smsPermission = systemHealth.smsPermission,
                    simStatus = systemHealth.simStatus,
                    networkConnectivity = systemHealth.networkConnectivity,
                    queueHealth = queueHealthResponse,
                    lastCheckTime = systemHealth.lastCheckTime,
                    issues = systemHealth.getIssues(),
                    recommendations = systemHealth.getRecommendations()
                )
                
                call.respond(HttpStatusCode.OK, response)
                
            } catch (e: Exception) {
                logger.error("Error getting health status", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to get health status", "message" to e.message)
                )
            }
        }
        
        /**
         * GET /api/v1/metrics
         * Pobieranie metryk systemu
         */
        get("/api/v1/metrics") {
            try {
                val metricsReport = metricsCollector.generateReport()
                
                val queueMetricsResponse = QueueMetricsResponse(
                    totalMessages = metricsReport.queueMetrics.totalMessages,
                    queuedMessages = metricsReport.queueMetrics.queuedMessages,
                    scheduledMessages = metricsReport.queueMetrics.scheduledMessages,
                    sendingMessages = metricsReport.queueMetrics.sendingMessages,
                    sentMessages = metricsReport.queueMetrics.sentMessages,
                    failedMessages = metricsReport.queueMetrics.failedMessages,
                    averageProcessingTime = metricsReport.queueMetrics.averageProcessingTime,
                    throughputPerHour = metricsReport.queueMetrics.throughputPerHour,
                    errorRate = metricsReport.queueMetrics.errorRate
                )
                
                val performanceMetricsResponse = PerformanceMetricsResponse(
                    cpuUsagePercent = metricsReport.performanceMetrics.cpuUsagePercent,
                    memoryUsagePercent = metricsReport.performanceMetrics.memoryUsagePercent,
                    diskUsagePercent = metricsReport.performanceMetrics.diskUsagePercent,
                    networkIOBytes = metricsReport.performanceMetrics.networkIOBytes,
                    databaseAverageQueryTime = metricsReport.performanceMetrics.databaseAverageQueryTime,
                    apiAverageResponseTime = metricsReport.performanceMetrics.apiAverageResponseTime
                )
                
                val response = MetricsResponse(
                    timestamp = metricsReport.timestamp,
                    queueMetrics = queueMetricsResponse,
                    performanceMetrics = performanceMetricsResponse,
                    customMetrics = metricsReport.allMetrics
                )
                
                call.respond(HttpStatusCode.OK, response)
                
            } catch (e: Exception) {
                logger.error("Error getting metrics", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to get metrics", "message" to e.message)
                )
            }
        }
        
        /**
         * GET /api/v1/sms/queue/next
         * Pobieranie następnej wiadomości z kolejki (do debugowania)
         */
        get("/api/v1/sms/queue/next") {
            try {
                val nextMessage = smsQueueService.dequeueNextSms()
                
                if (nextMessage != null) {
                    call.respond(
                        HttpStatusCode.OK,
                        nextMessage.toQueueStatusResponse()
                    )
                } else {
                    call.respond(
                        HttpStatusCode.OK,
                        mapOf("message" to "Queue is empty")
                    )
                }
                
            } catch (e: Exception) {
                logger.error("Error getting next message from queue", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to get next message", "message" to e.message)
                )
            }
        }
    }
}

/**
 * Waliduje format numeru telefonu
 */
private fun isValidPhoneNumber(phoneNumber: String): Boolean {
    // Prosta walidacja - numer musi zaczynać się od + i zawierać tylko cyfry
    val phoneRegex = Regex("^\\+[0-9]{6,15}$")
    return phoneRegex.matches(phoneNumber)
}

/**
 * Oblicza szacowany czas wysyłki wiadomości
 */
private fun calculateEstimatedSendTime(
    smsMessage: SmsMessage,
    smsQueueService: SmsQueueService
): Long? {
    // Jeśli wiadomość ma zaplanowany czas wysyłki
    if (smsMessage.scheduledAt != null) {
        return smsMessage.scheduledAt
    }
    
    // Jeśli wiadomość jest w kolejce, oblicz szacowany czas na podstawie pozycji
    if (smsMessage.queuePosition != null && smsMessage.queuePosition!! > 0) {
        val queueStats = smsQueueService.getQueueStats()
        val averageProcessingTime = queueStats.averageProcessingTime
        
        if (averageProcessingTime > 0) {
            return System.currentTimeMillis() + (smsMessage.queuePosition!! * averageProcessingTime)
        }
    }
    
    return null
}

/**
 * Oblicza liczbę części SMS na podstawie długości wiadomości
 */
private fun calculateSmsParts(message: String): Int {
    // Uproszczona wersja - zakładamy GSM-7 encoding
    val gsm7CharsPerPart = 160
    val maxParts = 10 // Maksymalnie 10 części SMS
    
    return if (message.length <= gsm7CharsPerPart) {
        1
    } else {
        kotlin.math.ceil(message.length.toDouble() / gsm7CharsPerPart).toInt().coerceAtMost(maxParts)
    }
}