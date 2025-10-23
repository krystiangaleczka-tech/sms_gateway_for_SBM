package com.smsgateway.app.models.security

import kotlinx.serialization.Serializable

@Serializable
data class RateLimitEntry(
    val clientId: String,
    val endpoint: String,
    val requestCount: Int,
    val windowStart: Long,
    val windowSize: Long
)