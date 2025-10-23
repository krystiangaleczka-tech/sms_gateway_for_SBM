package com.smsgateway.app.models.security

import kotlinx.serialization.Serializable

@Serializable
data class TunnelConfig(
    val tunnelId: String,
    val tunnelName: String,
    val tunnelSecret: String,
    val hostname: String,
    val port: Int,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastConnected: Long? = null
)