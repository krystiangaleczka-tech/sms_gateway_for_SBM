package com.smsgateway.app.routes

import com.smsgateway.app.database.SmsRepository
import com.smsgateway.app.monitoring.models.ErrorModels
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.*

/**
 * Routes for error handling and reporting
 */
fun Route.errorRoutes() {
    route("/api/errors") {
        // Pobierz listę błędów
        get {
            try {
                // W rzeczywistej implementacji pobieralibyśmy błędy z bazy danych
                // Na razie zwracamy przykładową listę
                val errors = listOf(
                    ErrorModels.AppError(
                        id = UUID.randomUUID().toString(),
                        message = "Przykładowy błąd",
                        stackTrace = "java.lang.Exception: Przykładowy błąd\n\tat com.example.App.main(App.java:10)",
                        type = ErrorModels.ErrorType.RUNTIME,
                        severity = ErrorModels.ErrorSeverity.MEDIUM,
                        timestamp = Instant.now(),
                        metadata = mapOf("screen" to "dashboard", "action" to "refresh"),
                        isReported = false
                    )
                )
                
                call.respond(HttpStatusCode.OK, errors)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError, 
                    mapOf("error" to "Failed to fetch errors: ${e.message}")
                )
            }
        }
        
        // Pobierz szczegóły błędu
        get("/{id}") {
            try {
                val errorId = call.parameters["id"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest, 
                    mapOf("error" to "Error ID is required")
                )
                
                // W rzeczywistej implementacji pobieralibyśmy błąd z bazy danych
                // Na razie zwracamy przykładowy błąd
                val error = ErrorModels.AppError(
                    id = errorId,
                    message = "Przykładowy błąd",
                    stackTrace = "java.lang.Exception: Przykładowy błąd\n\tat com.example.App.main(App.java:10)",
                    type = ErrorModels.ErrorType.RUNTIME,
                    severity = ErrorModels.ErrorSeverity.MEDIUM,
                    timestamp = Instant.now(),
                    metadata = mapOf("screen" to "dashboard", "action" to "refresh"),
                    isReported = false
                )
                
                call.respond(HttpStatusCode.OK, error)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError, 
                    mapOf("error" to "Failed to fetch error details: ${e.message}")
                )
            }
        }
        
        // Zgłoś błąd
        post("/report") {
            try {
                val reportableError = call.receive<ErrorModels.ReportableError>()
                
                // W rzeczywistej implementacji zapisywalibyśmy zgłoszenie błędu do bazy danych
                // i/lub wysyłalibyśmy do zewnętrznego systemu raportowania
                
                // Logowanie zgłoszenia błędu
                println("Error reported: ${reportableError.errorId} - ${reportableError.userDescription}")
                
                call.respond(
                    HttpStatusCode.OK,
                    mapOf(
                        "success" to true,
                        "message" to "Error reported successfully",
                        "reportId" to UUID.randomUUID().toString()
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError, 
                    mapOf("error" to "Failed to report error: ${e.message}")
                )
            }
        }
        
        // Pobierz metryki systemowe
        get("/metrics") {
            try {
                // W rzeczywistej implementacji pobieralibyśmy metryki z bazy danych
                // Na razie zwracamy przykładowe metryki
                val metrics = ErrorModels.SystemMetrics(
                    cpuUsage = 45.2,
                    memoryUsage = 62.8,
                    diskUsage = 78.5,
                    networkUsage = 12.3,
                    batteryLevel = 85.0,
                    timestamp = Instant.now()
                )
                
                call.respond(HttpStatusCode.OK, metrics)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError, 
                    mapOf("error" to "Failed to fetch metrics: ${e.message}")
                )
            }
        }
        
        // Pobierz status systemu
        get("/health") {
            try {
                // W rzeczywistej implementacji sprawdzalibyśmy status różnych komponentów
                // Na razie zwracamy przykładowy status
                val healthStatus = ErrorModels.HealthStatus(
                    overallStatus = ErrorModels.SystemStatus.HEALTHY,
                    components = mapOf(
                        "database" to ErrorModels.SystemStatus.HEALTHY,
                        "ktor_server" to ErrorModels.SystemStatus.HEALTHY,
                        "sms_service" to ErrorModels.SystemStatus.WARNING,
                        "error_handler" to ErrorModels.SystemStatus.HEALTHY
                    ),
                    lastChecked = Instant.now(),
                    uptime = "2 days, 14 hours, 32 minutes"
                )
                
                call.respond(HttpStatusCode.OK, healthStatus)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError, 
                    mapOf("error" to "Failed to fetch health status: ${e.message}")
                )
            }
        }
        
        // Pobierz alerty systemowe
        get("/alerts") {
            try {
                // W rzeczywistej implementacji pobieralibyśmy alerty z bazy danych
                // Na razie zwracamy przykładowe alerty
                val alerts = listOf(
                    ErrorModels.SystemAlert(
                        id = UUID.randomUUID().toString(),
                        title = "Wysokie użycie pamięci",
                        message = "Użycie pamięci przekroczyło 80%",
                        severity = ErrorModels.AlertSeverity.WARNING,
                        timestamp = Instant.now(),
                        isRead = false,
                        source = "system_monitor"
                    )
                )
                
                call.respond(HttpStatusCode.OK, alerts)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError, 
                    mapOf("error" to "Failed to fetch alerts: ${e.message}")
                )
            }
        }
    }
}