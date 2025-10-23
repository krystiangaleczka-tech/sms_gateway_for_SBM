package com.smsgateway.app.models.security.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateTokenRequest(
    val name: String,
    val expiresInDays: Int = 30
)