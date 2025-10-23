package com.smsgateway.app.models.security

import kotlinx.serialization.Serializable

@Serializable
data class SecurityEvent(
    val id: String,
    val timestamp: Long,
    val eventType: SecurityEventType,
    val clientId: String?,
    val ipAddress: String,
    val userAgent: String?,
    val endpoint: String?,
    val details: Map<String, String>,
    val severity: SecuritySeverity = SecuritySeverity.MEDIUM
)

enum class SecurityEventType {
    TOKEN_CREATED,
    TOKEN_USED,
    TOKEN_REVOKED,
    RATE_LIMIT_EXCEEDED,
    UNAUTHORIZED_ACCESS,
    SUSPICIOUS_ACTIVITY,
    TUNNEL_CONNECTED,
    TUNNEL_DISCONNECTED,
    SECURITY_BREACH_ATTEMPT
}

enum class SecuritySeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}