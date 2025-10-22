package com.smsgateway.app.models.dto

import kotlinx.serialization.Serializable

/**
 * DTO dla żądania kolejkowania SMS
 * 
 * @property phoneNumber Numer telefonu w formacie E.164
 * @property message Treść wiadomości SMS
 * @property appointmentTime Czas wizyty w formacie ISO 8601
 */
@Serializable
data class SmsRequest(
    val phoneNumber: String,
    val message: String,
    val appointmentTime: String
)