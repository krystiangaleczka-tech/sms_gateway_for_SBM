package com.smsgateway.app.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.SerializationException

/**
 * Konfiguruje globalną obsługę błędów przez StatusPages
 * 
 * Zapewnia spójne odpowiedzi błędów dla całego API
 */
fun Application.configureStatusPages() {
    install(StatusPages) {
        // Obsługa błędów walidacji
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf(
                    "error" to "Validation Error",
                    "message" to (cause.message ?: "Invalid request data"),
                    "code" to "VALIDATION_ERROR"
                )
            )
        }
        
        // Obsługa błędów serializacji JSON
        exception<SerializationException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf(
                    "error" to "JSON Serialization Error",
                    "message" to "Invalid JSON format",
                    "code" to "JSON_ERROR"
                )
            )
        }
        
        // Obsługa błędów NumberFormatException
        exception<NumberFormatException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf(
                    "error" to "Invalid Number Format",
                    "message" to "ID must be a valid number",
                    "code" to "INVALID_NUMBER"
                )
            )
        }
        
        // Obsługa błędów związanych z datą/czasem
        exception<java.time.DateTimeException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf(
                    "error" to "Invalid Date Format",
                    "message" to "appointmentTime must be in ISO 8601 format",
                    "code" to "INVALID_DATE"
                )
            )
        }
        
        // Ogólna obsługa wszystkich innych wyjątków
        exception<Throwable> { call, cause ->
            // Logowanie błędu (w środowisku produkcyjnym powinno być bardziej szczegółowe)
            this@configureStatusPages.environment.log.error("Unhandled exception: ${cause.message}", cause)
            
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf(
                    "error" to "Internal Server Error",
                    "message" to "An unexpected error occurred",
                    "code" to "INTERNAL_ERROR"
                )
            )
        }
        
        // Obsługa statusu 404 Not Found
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(
                status,
                mapOf(
                    "error" to "Not Found",
                    "message" to "The requested resource was not found",
                    "code" to "NOT_FOUND"
                )
            )
        }
        
        // Obsługa statusu 405 Method Not Allowed
        status(HttpStatusCode.MethodNotAllowed) { call, status ->
            call.respond(
                status,
                mapOf(
                    "error" to "Method Not Allowed",
                    "message" to "The HTTP method is not allowed for this resource",
                    "code" to "METHOD_NOT_ALLOWED"
                )
            )
        }
        
        // Obsługa statusu 415 Unsupported Media Type
        status(HttpStatusCode.UnsupportedMediaType) { call, status ->
            call.respond(
                status,
                mapOf(
                    "error" to "Unsupported Media Type",
                    "message" to "The server does not support the media type of the request",
                    "code" to "UNSUPPORTED_MEDIA_TYPE"
                )
            )
        }
    }
}