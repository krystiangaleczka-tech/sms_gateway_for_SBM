package com.smsgateway.app.integration.security

import com.smsgateway.app.KtorServer
import com.smsgateway.app.models.security.ApiTokenType
import com.smsgateway.app.models.security.TunnelStatus
import com.smsgateway.app.models.security.TunnelType
import com.smsgateway.app.repositories.ApiTokenRepository
import com.smsgateway.app.repositories.TunnelConfigRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SecurityApiIntegrationTest {
    
    private lateinit var server: KtorServer
    private lateinit var client: HttpClient
    private lateinit var mockApiTokenRepository: ApiTokenRepository
    private lateinit var mockTunnelConfigRepository: TunnelConfigRepository
    
    @BeforeEach
    fun setup() {
        mockApiTokenRepository = mockk()
        mockTunnelConfigRepository = mockk()
        
        // Create test server with mocked repositories
        server = KtorServer(
            apiTokenRepository = mockApiTokenRepository,
            tunnelConfigRepository = mockTunnelConfigRepository
        )
        
        client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
        
        // Start server in test mode
        server.start()
    }
    
    @AfterEach
    fun tearDown() {
        client.close()
        server.stop()
    }
    
    @Test
    fun `should create API token successfully`() = runBlocking {
        // Given
        val tokenName = "Test Token"
        val permissions = setOf("sms:send", "sms:view")
        val expirationDays = 30
        
        val createTokenRequest = mapOf(
            "name" to tokenName,
            "permissions" to permissions.toList(),
            "expirationDays" to expirationDays
        )
        
        // Mock successful token creation
        coEvery { mockApiTokenRepository.save(any()) } returns mockk {
            every { id } returns "token123"
            every { name } returns tokenName
            every { permissions } returns permissions
            every { isActive } returns true
            every { expiresAt } returns LocalDateTime.now().plusDays(expirationDays.toLong())
        }
        
        // When
        val response = client.post("http://localhost:8080/api/security/tokens") {
            contentType(ContentType.Application.Json)
            setBody(createTokenRequest)
        }
        
        // Then
        assertEquals(HttpStatusCode.Created, response.status)
        
        val responseBody: Map<String, Any> = response.body()
        assertNotNull(responseBody["id"])
        assertEquals(tokenName, responseBody["name"])
        assertEquals(permissions, responseBody["permissions"])
        assertTrue(responseBody["isActive"] as Boolean)
        assertNotNull(responseBody["token"])
        
        coVerify { mockApiTokenRepository.save(any()) }
    }
    
    @Test
    fun `should get user tokens successfully`() = runBlocking {
        // Given
        val userId = "user123"
        val tokens = listOf(
            mapOf(
                "id" to "token1",
                "name" to "Token 1",
                "permissions" to listOf("sms:send"),
                "isActive" to true,
                "createdAt" to LocalDateTime.now().toString(),
                "expiresAt" to LocalDateTime.now().plusDays(30).toString()
            ),
            mapOf(
                "id" to "token2",
                "name" to "Token 2",
                "permissions" to listOf("sms:view"),
                "isActive" to true,
                "createdAt" to LocalDateTime.now().toString(),
                "expiresAt" to LocalDateTime.now().plusDays(60).toString()
            )
        )
        
        // Mock successful token retrieval
        coEvery { mockApiTokenRepository.findByUserId(userId) } returns mockk {
            every { size } returns 2
            every { get(0) } returns mockk {
                every { id } returns "token1"
                every { name } returns "Token 1"
                every { permissions } returns setOf("sms:send")
                every { isActive } returns true
                every { createdAt } returns LocalDateTime.now()
                every { expiresAt } returns LocalDateTime.now().plusDays(30)
            }
            every { get(1) } returns mockk {
                every { id } returns "token2"
                every { name } returns "Token 2"
                every { permissions } returns setOf("sms:view")
                every { isActive } returns true
                every { createdAt } returns LocalDateTime.now()
                every { expiresAt } returns LocalDateTime.now().plusDays(60)
            }
        }
        
        // When
        val response = client.get("http://localhost:8080/api/security/tokens?userId=$userId")
        
        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody: List<Map<String, Any>> = response.body()
        assertEquals(2, responseBody.size)
        assertEquals("Token 1", responseBody[0]["name"])
        assertEquals("Token 2", responseBody[1]["name"])
        
        coVerify { mockApiTokenRepository.findByUserId(userId) }
    }
    
    @Test
    fun `should revoke token successfully`() = runBlocking {
        // Given
        val tokenId = "token123"
        
        val existingToken = mockk {
            every { id } returns tokenId
            every { isActive } returns true
        }
        
        val revokedToken = existingToken.copy(isActive = false)
        
        // Mock successful token revocation
        coEvery { mockApiTokenRepository.findById(tokenId) } returns existingToken
        coEvery { mockApiTokenRepository.save(any()) } returns revokedToken
        
        // When
        val response = client.delete("http://localhost:8080/api/security/tokens/$tokenId")
        
        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody: Map<String, Any> = response.body()
        assertTrue(responseBody["success"] as Boolean)
        
        coVerify { mockApiTokenRepository.findById(tokenId) }
        coVerify { mockApiTokenRepository.save(any()) }
    }
    
    @Test
    fun `should create tunnel configuration successfully`() = runBlocking {
        // Given
        val tunnelName = "Test Tunnel"
        val tunnelType = "HTTP"
        val localPort = 8080
        val subdomain = "test-smsgateway"
        
        val createTunnelRequest = mapOf(
            "name" to tunnelName,
            "tunnelType" to tunnelType,
            "localPort" to localPort,
            "subdomain" to subdomain
        )
        
        // Mock successful tunnel creation
        coEvery { mockTunnelConfigRepository.save(any()) } returns mockk {
            every { id } returns "tunnel123"
            every { name } returns tunnelName
            every { tunnelType } returns TunnelType.HTTP
            every { localPort } returns localPort
            every { subdomain } returns subdomain
            every { status } returns TunnelStatus.ACTIVE
            every { publicUrl } returns "https://test-smsgateway.trycloudflare.com"
        }
        
        // When
        val response = client.post("http://localhost:8080/api/security/tunnels") {
            contentType(ContentType.Application.Json)
            setBody(createTunnelRequest)
        }
        
        // Then
        assertEquals(HttpStatusCode.Created, response.status)
        
        val responseBody: Map<String, Any> = response.body()
        assertNotNull(responseBody["id"])
        assertEquals(tunnelName, responseBody["name"])
        assertEquals(tunnelType, responseBody["tunnelType"])
        assertEquals(localPort, responseBody["localPort"])
        assertEquals(subdomain, responseBody["subdomain"])
        assertEquals("ACTIVE", responseBody["status"])
        assertNotNull(responseBody["publicUrl"])
        
        coVerify { mockTunnelConfigRepository.save(any()) }
    }
    
    @Test
    fun `should get all tunnel configurations successfully`() = runBlocking {
        // Given
        val tunnels = listOf(
            mockk {
                every { id } returns "tunnel1"
                every { name } returns "Tunnel 1"
                every { tunnelType } returns TunnelType.HTTP
                every { localPort } returns 8080
                every { subdomain } returns "tunnel1-smsgateway"
                every { status } returns TunnelStatus.ACTIVE
                every { publicUrl } returns "https://tunnel1-smsgateway.trycloudflare.com"
                every { createdAt } returns LocalDateTime.now()
            },
            mockk {
                every { id } returns "tunnel2"
                every { name } returns "Tunnel 2"
                every { tunnelType } returns TunnelType.HTTPS
                every { localPort } returns 8081
                every { subdomain } returns "tunnel2-smsgateway"
                every { status } returns TunnelStatus.INACTIVE
                every { publicUrl } returns "https://tunnel2-smsgateway.trycloudflare.com"
                every { createdAt } returns LocalDateTime.now()
            }
        )
        
        // Mock successful tunnel retrieval
        coEvery { mockTunnelConfigRepository.findAll() } returns tunnels
        
        // When
        val response = client.get("http://localhost:8080/api/security/tunnels")
        
        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody: List<Map<String, Any>> = response.body()
        assertEquals(2, responseBody.size)
        assertEquals("Tunnel 1", responseBody[0]["name"])
        assertEquals("Tunnel 2", responseBody[1]["name"])
        
        coVerify { mockTunnelConfigRepository.findAll() }
    }
    
    @Test
    fun `should start tunnel successfully`() = runBlocking {
        // Given
        val tunnelId = "tunnel123"
        
        val existingTunnel = mockk {
            every { id } returns tunnelId
            every { status } returns TunnelStatus.INACTIVE
        }
        
        val startedTunnel = existingTunnel.copy(status = TunnelStatus.ACTIVE)
        
        // Mock successful tunnel start
        coEvery { mockTunnelConfigRepository.findById(tunnelId) } returns existingTunnel
        coEvery { mockTunnelConfigRepository.save(any()) } returns startedTunnel
        
        // When
        val response = client.post("http://localhost:8080/api/security/tunnels/$tunnelId/start")
        
        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody: Map<String, Any> = response.body()
        assertTrue(responseBody["success"] as Boolean)
        
        coVerify { mockTunnelConfigRepository.findById(tunnelId) }
        coVerify { mockTunnelConfigRepository.save(any()) }
    }
    
    @Test
    fun `should stop tunnel successfully`() = runBlocking {
        // Given
        val tunnelId = "tunnel123"
        
        val existingTunnel = mockk {
            every { id } returns tunnelId
            every { status } returns TunnelStatus.ACTIVE
        }
        
        val stoppedTunnel = existingTunnel.copy(status = TunnelStatus.INACTIVE)
        
        // Mock successful tunnel stop
        coEvery { mockTunnelConfigRepository.findById(tunnelId) } returns existingTunnel
        coEvery { mockTunnelConfigRepository.save(any()) } returns stoppedTunnel
        
        // When
        val response = client.post("http://localhost:8080/api/security/tunnels/$tunnelId/stop")
        
        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody: Map<String, Any> = response.body()
        assertTrue(responseBody["success"] as Boolean)
        
        coVerify { mockTunnelConfigRepository.findById(tunnelId) }
        coVerify { mockTunnelConfigRepository.save(any()) }
    }
    
    @Test
    fun `should delete tunnel successfully`() = runBlocking {
        // Given
        val tunnelId = "tunnel123"
        
        val existingTunnel = mockk {
            every { id } returns tunnelId
        }
        
        // Mock successful tunnel deletion
        coEvery { mockTunnelConfigRepository.findById(tunnelId) } returns existingTunnel
        coEvery { mockTunnelConfigRepository.deleteById(tunnelId) } returns true
        
        // When
        val response = client.delete("http://localhost:8080/api/security/tunnels/$tunnelId")
        
        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody: Map<String, Any> = response.body()
        assertTrue(responseBody["success"] as Boolean)
        
        coVerify { mockTunnelConfigRepository.findById(tunnelId) }
        coVerify { mockTunnelConfigRepository.deleteById(tunnelId) }
    }
    
    @Test
    fun `should get security events successfully`() = runBlocking {
        // Given
        val userId = "user123"
        val limit = 50
        val offset = 0
        
        val events = listOf(
            mockk {
                every { id } returns "event1"
                every { eventType } returns mockk { every { name } returns "LOGIN_SUCCESS" }
                every { userId } returns userId
                every { ipAddress } returns "192.168.1.1"
                every { timestamp } returns LocalDateTime.now()
                every { details } returns emptyMap()
            },
            mockk {
                every { id } returns "event2"
                every { eventType } returns mockk { every { name } returns "API_ACCESS" }
                every { userId } returns userId
                every { ipAddress } returns "192.168.1.1"
                every { timestamp } returns LocalDateTime.now()
                every { details } returns mapOf("endpoint" to "/api/sms")
            }
        )
        
        // Mock successful event retrieval
        coEvery { mockApiTokenRepository.findByUserId(userId) } returns mockk {
            every { isNotEmpty() } returns true
        }
        
        // When
        val response = client.get("http://localhost:8080/api/security/events?userId=$userId&limit=$limit&offset=$offset")
        
        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody: Map<String, Any> = response.body()
        assertNotNull(responseBody["events"])
        assertNotNull(responseBody["total"])
        assertNotNull(responseBody["limit"])
        assertNotNull(responseBody["offset"])
        
        coVerify { mockApiTokenRepository.findByUserId(userId) }
    }
    
    @Test
    fun `should handle invalid token request`() = runBlocking {
        // Given
        val createTokenRequest = mapOf(
            "name" to "", // Invalid: empty name
            "permissions" to emptyList<String>(), // Invalid: no permissions
            "expirationDays" to -1 // Invalid: negative days
        )
        
        // When
        val response = client.post("http://localhost:8080/api/security/tokens") {
            contentType(ContentType.Application.Json)
            setBody(createTokenRequest)
        }
        
        // Then
        assertEquals(HttpStatusCode.BadRequest, response.status)
        
        val responseBody: Map<String, Any> = response.body()
        assertNotNull(responseBody["error"])
        
        coVerify(exactly = 0) { mockApiTokenRepository.save(any()) }
    }
    
    @Test
    fun `should handle non-existent token revocation`() = runBlocking {
        // Given
        val tokenId = "non_existent_token"
        
        // Mock token not found
        coEvery { mockApiTokenRepository.findById(tokenId) } returns null
        
        // When
        val response = client.delete("http://localhost:8080/api/security/tokens/$tokenId")
        
        // Then
        assertEquals(HttpStatusCode.NotFound, response.status)
        
        val responseBody: Map<String, Any> = response.body()
        assertNotNull(responseBody["error"])
        
        coVerify { mockApiTokenRepository.findById(tokenId) }
        coVerify(exactly = 0) { mockApiTokenRepository.save(any()) }
    }
    
    @Test
    fun `should handle non-existent tunnel operations`() = runBlocking {
        // Given
        val tunnelId = "non_existent_tunnel"
        
        // Mock tunnel not found
        coEvery { mockTunnelConfigRepository.findById(tunnelId) } returns null
        
        // When
        val response = client.post("http://localhost:8080/api/security/tunnels/$tunnelId/start")
        
        // Then
        assertEquals(HttpStatusCode.NotFound, response.status)
        
        val responseBody: Map<String, Any> = response.body()
        assertNotNull(responseBody["error"])
        
        coVerify { mockTunnelConfigRepository.findById(tunnelId) }
        coVerify(exactly = 0) { mockTunnelConfigRepository.save(any()) }
    }
}