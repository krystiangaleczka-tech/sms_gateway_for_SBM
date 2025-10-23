package com.smsgateway.app.models.security.dto

import kotlinx.serialization.Serializable

@Serializable
data class TokenResponse(
    val token: String,
    val name: String,
    val expiresAt: Long
)