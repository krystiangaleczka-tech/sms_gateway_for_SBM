package com.smsgateway.app.models.dto

import kotlinx.serialization.Serializable

/**
 * DTO dla odpowiedzi API SMS
 * 
 * @property id Unikalny identyfikator wiadomości SMS
 * @property status Status wiadomości (QUEUED, SCHEDULED, SENDING, SENT, FAILED, CANCELLED)
 * @property message Komunikat statusu
 */
@Serializable
data class SmsResponse(
    val id: Long,
    val status: String,
    val message: String
)