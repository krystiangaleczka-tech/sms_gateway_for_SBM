package com.smsgateway.app.models.security

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "api_tokens")
@Serializable
data class ApiToken(
    @PrimaryKey
    val id: String,
    val tokenHash: String,
    val name: String,
    val permissions: List<String>,
    val createdAt: Long,
    val expiresAt: Long,
    val lastUsedAt: Long? = null,
    val isActive: Boolean = true,
    val createdBy: String? = null
)