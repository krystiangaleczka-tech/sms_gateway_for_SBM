package com.smsgateway.app.services.security

import com.smsgateway.app.models.security.ApiToken
import com.smsgateway.app.models.security.ApiTokenType
import com.smsgateway.app.repositories.ApiTokenRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TokenManagerServiceTest {
    
    private lateinit var tokenManagerService: TokenManagerService
    private lateinit var mockApiTokenRepository: ApiTokenRepository
    
    @BeforeEach
    fun setup() {
        mockApiTokenRepository = mockk()
        tokenManagerService = TokenManagerService(mockApiTokenRepository)
    }
    
    @Test
    fun `should create API token successfully`() = runTest {
        // Given
        val userId = "user123"
        val tokenName = "Test Token"
        val permissions = setOf("sms:send", "sms:view")
        val expirationDays = 30
        
        val expectedToken = ApiToken(
            id = UUID.randomUUID().toString(),
            userId = userId,
            name = tokenName,
            tokenHash = "hashed_token",
            tokenType = ApiTokenType.BEARER,
            permissions = permissions,
            isActive = true,
            createdAt = LocalDateTime.now(),
            expiresAt = LocalDateTime.now().plusDays(expirationDays.toLong()),
            lastUsedAt = null
        )
        
        coEvery { mockApiTokenRepository.save(any()) } returns expectedToken
        
        // When
        val result = tokenManagerService.createToken(userId, tokenName, permissions, expirationDays)
        
        // Then
        assertNotNull(result)
        assertEquals(userId, result.userId)
        assertEquals(tokenName, result.name)
        assertEquals(permissions, result.permissions)
        assertTrue(result.isActive)
        
        coVerify { mockApiTokenRepository.save(any()) }
    }
    
    @Test
    fun `should validate token successfully`() = runTest {
        // Given
        val token = "valid_token"
        val userId = "user123"
        val apiToken = ApiToken(
            id = UUID.randomUUID().toString(),
            userId = userId,
            name = "Test Token",
            tokenHash = "hashed_token",
            tokenType = ApiTokenType.BEARER,
            permissions = setOf("sms:send"),
            isActive = true,
            createdAt = LocalDateTime.now(),
            expiresAt = LocalDateTime.now().plusDays(30),
            lastUsedAt = null
        )
        
        coEvery { mockApiTokenRepository.findByTokenHash(any()) } returns apiToken
        
        // When
        val result = tokenManagerService.validateToken(token)
        
        // Then
        assertNotNull(result)
        assertEquals(userId, result.userId)
        assertEquals(setOf("sms:send"), result.permissions)
        
        coVerify { mockApiTokenRepository.findByTokenHash(any()) }
        coVerify { mockApiTokenRepository.updateLastUsed(any()) }
    }
    
    @Test
    fun `should return null for invalid token`() = runTest {
        // Given
        val token = "invalid_token"
        
        coEvery { mockApiTokenRepository.findByTokenHash(any()) } returns null
        
        // When
        val result = tokenManagerService.validateToken(token)
        
        // Then
        assertEquals(null, result)
        
        coVerify { mockApiTokenRepository.findByTokenHash(any()) }
        coVerify(exactly = 0) { mockApiTokenRepository.updateLastUsed(any()) }
    }
    
    @Test
    fun `should return null for expired token`() = runTest {
        // Given
        val token = "expired_token"
        val apiToken = ApiToken(
            id = UUID.randomUUID().toString(),
            userId = "user123",
            name = "Test Token",
            tokenHash = "hashed_token",
            tokenType = ApiTokenType.BEARER,
            permissions = setOf("sms:send"),
            isActive = true,
            createdAt = LocalDateTime.now(),
            expiresAt = LocalDateTime.now().minusDays(1), // Expired
            lastUsedAt = null
        )
        
        coEvery { mockApiTokenRepository.findByTokenHash(any()) } returns apiToken
        
        // When
        val result = tokenManagerService.validateToken(token)
        
        // Then
        assertEquals(null, result)
        
        coVerify { mockApiTokenRepository.findByTokenHash(any()) }
        coVerify(exactly = 0) { mockApiTokenRepository.updateLastUsed(any()) }
    }
    
    @Test
    fun `should return null for inactive token`() = runTest {
        // Given
        val token = "inactive_token"
        val apiToken = ApiToken(
            id = UUID.randomUUID().toString(),
            userId = "user123",
            name = "Test Token",
            tokenHash = "hashed_token",
            tokenType = ApiTokenType.BEARER,
            permissions = setOf("sms:send"),
            isActive = false, // Inactive
            createdAt = LocalDateTime.now(),
            expiresAt = LocalDateTime.now().plusDays(30),
            lastUsedAt = null
        )
        
        coEvery { mockApiTokenRepository.findByTokenHash(any()) } returns apiToken
        
        // When
        val result = tokenManagerService.validateToken(token)
        
        // Then
        assertEquals(null, result)
        
        coVerify { mockApiTokenRepository.findByTokenHash(any()) }
        coVerify(exactly = 0) { mockApiTokenRepository.updateLastUsed(any()) }
    }
    
    @Test
    fun `should refresh token successfully`() = runTest {
        // Given
        val tokenId = "token123"
        val expirationDays = 30
        val existingToken = ApiToken(
            id = tokenId,
            userId = "user123",
            name = "Test Token",
            tokenHash = "old_hashed_token",
            tokenType = ApiTokenType.BEARER,
            permissions = setOf("sms:send"),
            isActive = true,
            createdAt = LocalDateTime.now(),
            expiresAt = LocalDateTime.now().plusDays(1), // About to expire
            lastUsedAt = null
        )
        
        val refreshedToken = existingToken.copy(
            tokenHash = "new_hashed_token",
            expiresAt = LocalDateTime.now().plusDays(expirationDays.toLong())
        )
        
        coEvery { mockApiTokenRepository.findById(tokenId) } returns existingToken
        coEvery { mockApiTokenRepository.save(any()) } returns refreshedToken
        
        // When
        val result = tokenManagerService.refreshToken(tokenId, expirationDays)
        
        // Then
        assertNotNull(result)
        assertEquals("new_hashed_token", result.tokenHash)
        
        coVerify { mockApiTokenRepository.findById(tokenId) }
        coVerify { mockApiTokenRepository.save(any()) }
    }
    
    @Test
    fun `should revoke token successfully`() = runTest {
        // Given
        val tokenId = "token123"
        val existingToken = ApiToken(
            id = tokenId,
            userId = "user123",
            name = "Test Token",
            tokenHash = "hashed_token",
            tokenType = ApiTokenType.BEARER,
            permissions = setOf("sms:send"),
            isActive = true,
            createdAt = LocalDateTime.now(),
            expiresAt = LocalDateTime.now().plusDays(30),
            lastUsedAt = null
        )
        
        coEvery { mockApiTokenRepository.findById(tokenId) } returns existingToken
        coEvery { mockApiTokenRepository.save(any()) } returns existingToken.copy(isActive = false)
        
        // When
        val result = tokenManagerService.revokeToken(tokenId)
        
        // Then
        assertTrue(result)
        
        coVerify { mockApiTokenRepository.findById(tokenId) }
        coVerify { mockApiTokenRepository.save(any()) }
    }
    
    @Test
    fun `should return false when revoking non-existent token`() = runTest {
        // Given
        val tokenId = "non_existent_token"
        
        coEvery { mockApiTokenRepository.findById(tokenId) } returns null
        
        // When
        val result = tokenManagerService.revokeToken(tokenId)
        
        // Then
        assertEquals(false, result)
        
        coVerify { mockApiTokenRepository.findById(tokenId) }
        coVerify(exactly = 0) { mockApiTokenRepository.save(any()) }
    }
    
    @Test
    fun `should get user tokens successfully`() = runTest {
        // Given
        val userId = "user123"
        val tokens = listOf(
            ApiToken(
                id = "token1",
                userId = userId,
                name = "Token 1",
                tokenHash = "hash1",
                tokenType = ApiTokenType.BEARER,
                permissions = setOf("sms:send"),
                isActive = true,
                createdAt = LocalDateTime.now(),
                expiresAt = LocalDateTime.now().plusDays(30),
                lastUsedAt = null
            ),
            ApiToken(
                id = "token2",
                userId = userId,
                name = "Token 2",
                tokenHash = "hash2",
                tokenType = ApiTokenType.BEARER,
                permissions = setOf("sms:view"),
                isActive = true,
                createdAt = LocalDateTime.now(),
                expiresAt = LocalDateTime.now().plusDays(60),
                lastUsedAt = null
            )
        )
        
        coEvery { mockApiTokenRepository.findByUserId(userId) } returns tokens
        
        // When
        val result = tokenManagerService.getUserTokens(userId)
        
        // Then
        assertEquals(2, result.size)
        assertEquals("Token 1", result[0].name)
        assertEquals("Token 2", result[1].name)
        
        coVerify { mockApiTokenRepository.findByUserId(userId) }
    }
    
    @Test
    fun `should get user ID from token`() = runTest {
        // Given
        val token = "valid_token"
        val userId = "user123"
        val apiToken = ApiToken(
            id = UUID.randomUUID().toString(),
            userId = userId,
            name = "Test Token",
            tokenHash = "hashed_token",
            tokenType = ApiTokenType.BEARER,
            permissions = setOf("sms:send"),
            isActive = true,
            createdAt = LocalDateTime.now(),
            expiresAt = LocalDateTime.now().plusDays(30),
            lastUsedAt = null
        )
        
        coEvery { mockApiTokenRepository.findByTokenHash(any()) } returns apiToken
        
        // When
        val result = tokenManagerService.getUserIdFromToken(token)
        
        // Then
        assertEquals(userId, result)
        
        coVerify { mockApiTokenRepository.findByTokenHash(any()) }
    }
    
    @Test
    fun `should return null for user ID from invalid token`() = runTest {
        // Given
        val token = "invalid_token"
        
        coEvery { mockApiTokenRepository.findByTokenHash(any()) } returns null
        
        // When
        val result = tokenManagerService.getUserIdFromToken(token)
        
        // Then
        assertEquals(null, result)
        
        coVerify { mockApiTokenRepository.findByTokenHash(any()) }
    }
}