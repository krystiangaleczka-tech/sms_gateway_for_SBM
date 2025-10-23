package com.smsgateway.app.models.security.dto

import kotlinx.serialization.Serializable

@Serializable
data class TunnelConfigRequest(
    val tunnelName: String,
    val hostname: String,
    val port: Int = 8080
)