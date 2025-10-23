package com.smsgateway.app.models.security.dto

import com.smsgateway.app.models.security.SecurityEvent
import kotlinx.serialization.Serializable

@Serializable
data class SecurityEventResponse(
    val id: String,
    val type: SecurityEvent.EventType,
    val severity: SecurityEvent.Severity,
    val source: String,
    val message: String,
    val timestamp: Long,
    val metadata: Map<String, String>?
)