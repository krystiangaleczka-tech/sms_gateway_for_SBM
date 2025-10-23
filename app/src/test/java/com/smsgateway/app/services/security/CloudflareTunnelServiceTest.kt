package com.smsgateway.app.services.security

import com.smsgateway.app.models.security.TunnelConfig
import com.smsgateway.app.models.security.TunnelStatus
import com.smsgateway.app.models.security.TunnelType
import com.smsgateway.app.repositories.TunnelConfigRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CloudflareTunnelServiceTest {
    
    private lateinit var cloudflareTunnelService: CloudflareTunnelService
    private lateinit var mockTunnelConfigRepository: TunnelConfigRepository
    
    @BeforeEach
    fun setup() {
        mockTunnelConfigRepository = mockk()
        cloudflareTunnelService = CloudflareTunnelService(mockTunnelConfigRepository)
    }
    
    @Test
    fun `should create tunnel configuration successfully`() = runTest {
        // Given
        val name = "Test Tunnel"
        val tunnelType = TunnelType.HTTP
        val localPort = 8080
        val subdomain = "test-smsgateway"
        val metadata = mapOf("description" to "Test tunnel for SMS Gateway")
        
        val expectedTunnel = TunnelConfig(
            id = UUID.randomUUID().toString(),
            name = name,
            tunnelType = tunnelType,
            localPort = localPort,
            subdomain = subdomain,
            tunnelId = "tunnel_123",
            tunnelSecret = "secret_456",
            publicUrl = "https://test-smsgateway.trycloudflare.com",
            status = TunnelStatus.ACTIVE,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            metadata = metadata
        )
        
        coEvery { mockTunnelConfigRepository.save(any()) } returns expectedTunnel
        
        // When
        val result = cloudflareTunnelService.createTunnel(
            name, 
            tunnelType, 
            localPort, 
            subdomain, 
            metadata
        )
        
        // Then
        assertNotNull(result)
        assertEquals(name, result.name)
        assertEquals(tunnelType, result.tunnelType)
        assertEquals(localPort, result.localPort)
        assertEquals(subdomain, result.subdomain)
        assertEquals(TunnelStatus.ACTIVE, result.status)
        assertTrue(result.publicUrl.startsWith("https://"))
        
        coVerify { mockTunnelConfigRepository.save(any()) }
    }
    
    @Test
    fun `should get tunnel configuration by ID successfully`() = runTest {
        // Given
        val tunnelId = "tunnel123"
        val expectedTunnel = TunnelConfig(
            id = tunnelId,
            name = "Test Tunnel",
            tunnelType = TunnelType.HTTP,
            localPort = 8080,
            subdomain = "test-smsgateway",
            tunnelId = "tunnel_123",
            tunnelSecret = "secret_456",
            publicUrl = "https://test-smsgateway.trycloudflare.com",
            status = TunnelStatus.ACTIVE,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            metadata = emptyMap()
        )
        
        coEvery { mockTunnelConfigRepository.findById(tunnelId) } returns expectedTunnel
        
        // When
        val result = cloudflareTunnelService.getTunnel(tunnelId)
        
        // Then
        assertNotNull(result)
        assertEquals(tunnelId, result.id)
        assertEquals("Test Tunnel", result.name)
        assertEquals(TunnelStatus.ACTIVE, result.status)
        
        coVerify { mockTunnelConfigRepository.findById(tunnelId) }
    }
    
    @Test
    fun `should return null when getting non-existent tunnel`() = runTest {
        // Given
        val tunnelId = "non_existent_tunnel"
        
        coEvery { mockTunnelConfigRepository.findById(tunnelId) } returns null
        
        // When
        val result = cloudflareTunnelService.getTunnel(tunnelId)
        
        // Then
        assertEquals(null, result)
        
        coVerify { mockTunnelConfigRepository.findById(tunnelId) }
    }
    
    @Test
    fun `should get all tunnel configurations successfully`() = runTest {
        // Given
        val tunnels = listOf(
            TunnelConfig(
                id = "tunnel1",
                name = "Tunnel 1",
                tunnelType = TunnelType.HTTP,
                localPort = 8080,
                subdomain = "tunnel1-smsgateway",
                tunnelId = "tunnel_1",
                tunnelSecret = "secret_1",
                publicUrl = "https://tunnel1-smsgateway.trycloudflare.com",
                status = TunnelStatus.ACTIVE,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
                metadata = emptyMap()
            ),
            TunnelConfig(
                id = "tunnel2",
                name = "Tunnel 2",
                tunnelType = TunnelType.HTTPS,
                localPort = 8081,
                subdomain = "tunnel2-smsgateway",
                tunnelId = "tunnel_2",
                tunnelSecret = "secret_2",
                publicUrl = "https://tunnel2-smsgateway.trycloudflare.com",
                status = TunnelStatus.INACTIVE,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
                metadata = emptyMap()
            )
        )
        
        coEvery { mockTunnelConfigRepository.findAll() } returns tunnels
        
        // When
        val result = cloudflareTunnelService.getAllTunnels()
        
        // Then
        assertEquals(2, result.size)
        assertEquals("Tunnel 1", result[0].name)
        assertEquals("Tunnel 2", result[1].name)
        
        coVerify { mockTunnelConfigRepository.findAll() }
    }
    
    @Test
    fun `should update tunnel configuration successfully`() = runTest {
        // Given
        val tunnelId = "tunnel123"
        val existingTunnel = TunnelConfig(
            id = tunnelId,
            name = "Test Tunnel",
            tunnelType = TunnelType.HTTP,
            localPort = 8080,
            subdomain = "test-smsgateway",
            tunnelId = "tunnel_123",
            tunnelSecret = "secret_456",
            publicUrl = "https://test-smsgateway.trycloudflare.com",
            status = TunnelStatus.ACTIVE,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            metadata = emptyMap()
        )
        
        val updatedTunnel = existingTunnel.copy(
            name = "Updated Tunnel",
            localPort = 8081,
            status = TunnelStatus.INACTIVE,
            updatedAt = LocalDateTime.now()
        )
        
        coEvery { mockTunnelConfigRepository.findById(tunnelId) } returns existingTunnel
        coEvery { mockTunnelConfigRepository.save(any()) } returns updatedTunnel
        
        // When
        val result = cloudflareTunnelService.updateTunnel(
            tunnelId, 
            "Updated Tunnel", 
            8081, 
            TunnelStatus.INACTIVE
        )
        
        // Then
        assertNotNull(result)
        assertEquals("Updated Tunnel", result.name)
        assertEquals(8081, result.localPort)
        assertEquals(TunnelStatus.INACTIVE, result.status)
        
        coVerify { mockTunnelConfigRepository.findById(tunnelId) }
        coVerify { mockTunnelConfigRepository.save(any()) }
    }
    
    @Test
    fun `should return null when updating non-existent tunnel`() = runTest {
        // Given
        val tunnelId = "non_existent_tunnel"
        
        coEvery { mockTunnelConfigRepository.findById(tunnelId) } returns null
        
        // When
        val result = cloudflareTunnelService.updateTunnel(
            tunnelId, 
            "Updated Tunnel", 
            8081, 
            TunnelStatus.INACTIVE
        )
        
        // Then
        assertEquals(null, result)
        
        coVerify { mockTunnelConfigRepository.findById(tunnelId) }
        coVerify(exactly = 0) { mockTunnelConfigRepository.save(any()) }
    }
    
    @Test
    fun `should delete tunnel configuration successfully`() = runTest {
        // Given
        val tunnelId = "tunnel123"
        val existingTunnel = TunnelConfig(
            id = tunnelId,
            name = "Test Tunnel",
            tunnelType = TunnelType.HTTP,
            localPort = 8080,
            subdomain = "test-smsgateway",
            tunnelId = "tunnel_123",
            tunnelSecret = "secret_456",
            publicUrl = "https://test-smsgateway.trycloudflare.com",
            status = TunnelStatus.ACTIVE,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            metadata = emptyMap()
        )
        
        coEvery { mockTunnelConfigRepository.findById(tunnelId) } returns existingTunnel
        coEvery { mockTunnelConfigRepository.deleteById(tunnelId) } returns true
        
        // When
        val result = cloudflareTunnelService.deleteTunnel(tunnelId)
        
        // Then
        assertTrue(result)
        
        coVerify { mockTunnelConfigRepository.findById(tunnelId) }
        coVerify { mockTunnelConfigRepository.deleteById(tunnelId) }
    }
    
    @Test
    fun `should return false when deleting non-existent tunnel`() = runTest {
        // Given
        val tunnelId = "non_existent_tunnel"
        
        coEvery { mockTunnelConfigRepository.findById(tunnelId) } returns null
        
        // When
        val result = cloudflareTunnelService.deleteTunnel(tunnelId)
        
        // Then
        assertFalse(result)
        
        coVerify { mockTunnelConfigRepository.findById(tunnelId) }
        coVerify(exactly = 0) { mockTunnelConfigRepository.deleteById(any()) }
    }
    
    @Test
    fun `should start tunnel successfully`() = runTest {
        // Given
        val tunnelId = "tunnel123"
        val existingTunnel = TunnelConfig(
            id = tunnelId,
            name = "Test Tunnel",
            tunnelType = TunnelType.HTTP,
            localPort = 8080,
            subdomain = "test-smsgateway",
            tunnelId = "tunnel_123",
            tunnelSecret = "secret_456",
            publicUrl = "https://test-smsgateway.trycloudflare.com",
            status = TunnelStatus.INACTIVE,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            metadata = emptyMap()
        )
        
        val startedTunnel = existingTunnel.copy(
            status = TunnelStatus.ACTIVE,
            updatedAt = LocalDateTime.now()
        )
        
        coEvery { mockTunnelConfigRepository.findById(tunnelId) } returns existingTunnel
        coEvery { mockTunnelConfigRepository.save(any()) } returns startedTunnel
        
        // When
        val result = cloudflareTunnelService.startTunnel(tunnelId)
        
        // Then
        assertTrue(result)
        
        coVerify { mockTunnelConfigRepository.findById(tunnelId) }
        coVerify { mockTunnelConfigRepository.save(any()) }
    }
    
    @Test
    fun `should return false when starting non-existent tunnel`() = runTest {
        // Given
        val tunnelId = "non_existent_tunnel"
        
        coEvery { mockTunnelConfigRepository.findById(tunnelId) } returns null
        
        // When
        val result = cloudflareTunnelService.startTunnel(tunnelId)
        
        // Then
        assertFalse(result)
        
        coVerify { mockTunnelConfigRepository.findById(tunnelId) }
        coVerify(exactly = 0) { mockTunnelConfigRepository.save(any()) }
    }
    
    @Test
    fun `should stop tunnel successfully`() = runTest {
        // Given
        val tunnelId = "tunnel123"
        val existingTunnel = TunnelConfig(
            id = tunnelId,
            name = "Test Tunnel",
            tunnelType = TunnelType.HTTP,
            localPort = 8080,
            subdomain = "test-smsgateway",
            tunnelId = "tunnel_123",
            tunnelSecret = "secret_456",
            publicUrl = "https://test-smsgateway.trycloudflare.com",
            status = TunnelStatus.ACTIVE,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            metadata = emptyMap()
        )
        
        val stoppedTunnel = existingTunnel.copy(
            status = TunnelStatus.INACTIVE,
            updatedAt = LocalDateTime.now()
        )
        
        coEvery { mockTunnelConfigRepository.findById(tunnelId) } returns existingTunnel
        coEvery { mockTunnelConfigRepository.save(any()) } returns stoppedTunnel
        
        // When
        val result = cloudflareTunnelService.stopTunnel(tunnelId)
        
        // Then
        assertTrue(result)
        
        coVerify { mockTunnelConfigRepository.findById(tunnelId) }
        coVerify { mockTunnelConfigRepository.save(any()) }
    }
    
    @Test
    fun `should return false when stopping non-existent tunnel`() = runTest {
        // Given
        val tunnelId = "non_existent_tunnel"
        
        coEvery { mockTunnelConfigRepository.findById(tunnelId) } returns null
        
        // When
        val result = cloudflareTunnelService.stopTunnel(tunnelId)
        
        // Then
        assertFalse(result)
        
        coVerify { mockTunnelConfigRepository.findById(tunnelId) }
        coVerify(exactly = 0) { mockTunnelConfigRepository.save(any()) }
    }
    
    @Test
    fun `should get tunnel status successfully`() = runTest {
        // Given
        val tunnelId = "tunnel123"
        val existingTunnel = TunnelConfig(
            id = tunnelId,
            name = "Test Tunnel",
            tunnelType = TunnelType.HTTP,
            localPort = 8080,
            subdomain = "test-smsgateway",
            tunnelId = "tunnel_123",
            tunnelSecret = "secret_456",
            publicUrl = "https://test-smsgateway.trycloudflare.com",
            status = TunnelStatus.ACTIVE,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            metadata = emptyMap()
        )
        
        coEvery { mockTunnelConfigRepository.findById(tunnelId) } returns existingTunnel
        
        // When
        val result = cloudflareTunnelService.getTunnelStatus(tunnelId)
        
        // Then
        assertNotNull(result)
        assertEquals(TunnelStatus.ACTIVE, result)
        
        coVerify { mockTunnelConfigRepository.findById(tunnelId) }
    }
    
    @Test
    fun `should return null when getting status of non-existent tunnel`() = runTest {
        // Given
        val tunnelId = "non_existent_tunnel"
        
        coEvery { mockTunnelConfigRepository.findById(tunnelId) } returns null
        
        // When
        val result = cloudflareTunnelService.getTunnelStatus(tunnelId)
        
        // Then
        assertEquals(null, result)
        
        coVerify { mockTunnelConfigRepository.findById(tunnelId) }
    }
}