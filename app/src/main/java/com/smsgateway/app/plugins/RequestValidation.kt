package com.smsgateway.app.plugins

import com.smsgateway.app.models.dto.SmsRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Konfiguruje walidację żądań dla API
 * 
 * Automatycznie waliduje żądania przed przetworzeniem
 */
fun Application.configureRequestValidation() {
    install(RequestValidation) {
        // Walidacja dla SmsRequest
        validate<SmsRequest> { request ->
            when {
                request.phoneNumber.isBlank() -> 
                    ValidationResult.Invalid("Phone number is required")
                    
                !isValidPhoneNumber(request.phoneNumber) -> 
                    ValidationResult.Invalid("Invalid phone number format. Use E.164 format (e.g., +48123456789)")
                    
                request.message.isBlank() -> 
                    ValidationResult.Invalid("Message content is required")
                    
                request.message.length > 1600 -> // 10 części SMS po 160 znaków
                    ValidationResult.Invalid("Message content exceeds 1600 characters (10 SMS parts)")
                    
                request.appointmentTime.isBlank() -> 
                    ValidationResult.Invalid("Appointment time is required")
                    
                !isValidAppointmentTime(request.appointmentTime) -> 
                    ValidationResult.Invalid("appointmentTime must be in ISO 8601 format and in the future")
                    
                else -> ValidationResult.Valid
            }
        }
    }
}

/**
 * Waliduje format numeru telefonu (E.164)
 */
private fun isValidPhoneNumber(phoneNumber: String): Boolean {
    return phoneNumber.matches(Regex("^\\+[1-9]\\d{1,14}$"))
}

/**
 * Waliduje format czasu wizyty (ISO 8601) i czy jest w przyszłości
 */
private fun isValidAppointmentTime(appointmentTime: String): Boolean {
    return try {
        val parsedTime = Instant.parse(appointmentTime)
        val currentTime = Instant.now()
        parsedTime.isAfter(currentTime)
    } catch (e: DateTimeParseException) {
        false
    }
}