package com.smsgateway.app.services.security

import com.smsgateway.app.models.security.SecurityEvent
import com.smsgateway.app.models.security.SecurityEventType
import com.smsgateway.app.repositories.SecurityEventRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SecurityAuditServiceTest {
    
    private lateinit var securityAuditService: SecurityAuditService
    private lateinit var mockSecurityEventRepository: SecurityEventRepository
    
    @BeforeEach
    fun setup() {
        mockSecurityEventRepository = mockk()
        securityAuditService = SecurityAuditService(mockSecurityEventRepository)
    }
    
    @Test
    fun `should log security event successfully`() = runTest {
        // Given
        val eventType = SecurityEventType.LOGIN_SUCCESS
        val userId = "user123"
        val ipAddress = "192.168.1.1"
        val userAgent = "Mozilla/5.0"
        val details = mapOf("loginMethod" to "password")
        
        val expectedEvent = SecurityEvent(
            id = UUID.randomUUID().toString(),
            eventType = eventType,
            userId = userId,
            ipAddress = ipAddress,
            userAgent = userAgent,
            timestamp = LocalDateTime.now(),
            details = details
        )
        
        coEvery { mockSecurityEventRepository.save(any()) } returns expectedEvent
        
        // When
        val result = securityAuditService.logSecurityEvent(
            eventType, 
            userId, 
            ipAddress, 
            userAgent, 
            details
        )
        
        // Then
        assertNotNull(result)
        assertEquals(eventType, result.eventType)
        assertEquals(userId, result.userId)
        assertEquals(ipAddress, result.ipAddress)
        assertEquals(userAgent, result.userAgent)
        assertEquals(details, result.details)
        
        coVerify { mockSecurityEventRepository.save(any()) }
    }
    
    @Test
    fun `should log authentication failure event`() = runTest {
        // Given
        val userId = "user123"
        val ipAddress = "192.168.1.1"
        val userAgent = "Mozilla/5.0"
        val reason = "Invalid password"
        
        val expectedEvent = SecurityEvent(
            id = UUID.randomUUID().toString(),
            eventType = SecurityEventType.LOGIN_FAILURE,
            userId = userId,
            ipAddress = ipAddress,
            userAgent = userAgent,
            timestamp = LocalDateTime.now(),
            details = mapOf("reason" to reason)
        )
        
        coEvery { mockSecurityEventRepository.save(any()) } returns expectedEvent
        
        // When
        val result = securityAuditService.logAuthenticationFailure(
            userId, 
            ipAddress, 
            userAgent, 
            reason
        )
        
        // Then
        assertNotNull(result)
        assertEquals(SecurityEventType.LOGIN_FAILURE, result.eventType)
        assertEquals(userId, result.userId)
        assertEquals(ipAddress, result.ipAddress)
        assertEquals(userAgent, result.userAgent)
        assertEquals(reason, result.details["reason"])
        
        coVerify { mockSecurityEventRepository.save(any()) }
    }
    
    @Test
    fun `should log authentication success event`() = runTest {
        // Given
        val userId = "user123"
        val ipAddress = "192.168.1.1"
        val userAgent = "Mozilla/5.0"
        
        val expectedEvent = SecurityEvent(
            id = UUID.randomUUID().toString(),
            eventType = SecurityEventType.LOGIN_SUCCESS,
            userId = userId,
            ipAddress = ipAddress,
            userAgent = userAgent,
            timestamp = LocalDateTime.now(),
            details = emptyMap()
        )
        
        coEvery { mockSecurityEventRepository.save(any()) } returns expectedEvent
        
        // When
        val result = securityAuditService.logAuthenticationSuccess(
            userId, 
            ipAddress, 
            userAgent
        )
        
        // Then
        assertNotNull(result)
        assertEquals(SecurityEventType.LOGIN_SUCCESS, result.eventType)
        assertEquals(userId, result.userId)
        assertEquals(ipAddress, result.ipAddress)
        assertEquals(userAgent, result.userAgent)
        
        coVerify { mockSecurityEventRepository.save(any()) }
    }
    
    @Test
    fun `should log API access event`() = runTest {
        // Given
        val userId = "user123"
        val endpoint = "/api/sms"
        val method = "POST"
        val ipAddress = "192.168.1.1"
        val userAgent = "Mozilla/5.0"
        val responseCode = 200
        
        val expectedEvent = SecurityEvent(
            id = UUID.randomUUID().toString(),
            eventType = SecurityEventType.API_ACCESS,
            userId = userId,
            ipAddress = ipAddress,
            userAgent = userAgent,
            timestamp = LocalDateTime.now(),
            details = mapOf(
                "endpoint" to endpoint,
                "method" to method,
                "responseCode" to responseCode.toString()
            )
        )
        
        coEvery { mockSecurityEventRepository.save(any()) } returns expectedEvent
        
        // When
        val result = securityAuditService.logApiAccess(
            userId, 
            endpoint, 
            method, 
            ipAddress, 
            userAgent, 
            responseCode
        )
        
        // Then
        assertNotNull(result)
        assertEquals(SecurityEventType.API_ACCESS, result.eventType)
        assertEquals(userId, result.userId)
        assertEquals(endpoint, result.details["endpoint"])
        assertEquals(method, result.details["method"])
        assertEquals(responseCode.toString(), result.details["responseCode"])
        
        coVerify { mockSecurityEventRepository.save(any()) }
    }
    
    @Test
    fun `should log token creation event`() = runTest {
        // Given
        val userId = "user123"
        val tokenId = "token123"
        val ipAddress = "192.168.1.1"
        val userAgent = "Mozilla/5.0"
        
        val expectedEvent = SecurityEvent(
            id = UUID.randomUUID().toString(),
            eventType = SecurityEventType.TOKEN_CREATED,
            userId = userId,
            ipAddress = ipAddress,
            userAgent = userAgent,
            timestamp = LocalDateTime.now(),
            details = mapOf("tokenId" to tokenId)
        )
        
        coEvery { mockSecurityEventRepository.save(any()) } returns expectedEvent
        
        // When
        val result = securityAuditService.logTokenCreation(
            userId, 
            tokenId, 
            ipAddress, 
            userAgent
        )
        
        // Then
        assertNotNull(result)
        assertEquals(SecurityEventType.TOKEN_CREATED, result.eventType)
        assertEquals(userId, result.userId)
        assertEquals(tokenId, result.details["tokenId"])
        
        coVerify { mockSecurityEventRepository.save(any()) }
    }
    
    @Test
    fun `should log token revocation event`() = runTest {
        // Given
        val userId = "user123"
        val tokenId = "token123"
        val ipAddress = "192.168.1.1"
        val userAgent = "Mozilla/5.0"
        
        val expectedEvent = SecurityEvent(
            id = UUID.randomUUID().toString(),
            eventType = SecurityEventType.TOKEN_REVOKED,
            userId = userId,
            ipAddress = ipAddress,
            userAgent = userAgent,
            timestamp = LocalDateTime.now(),
            details = mapOf("tokenId" to tokenId)
        )
        
        coEvery { mockSecurityEventRepository.save(any()) } returns expectedEvent
        
        // When
        val result = securityAuditService.logTokenRevocation(
            userId, 
            tokenId, 
            ipAddress, 
            userAgent
        )
        
        // Then
        assertNotNull(result)
        assertEquals(SecurityEventType.TOKEN_REVOKED, result.eventType)
        assertEquals(userId, result.userId)
        assertEquals(tokenId, result.details["tokenId"])
        
        coVerify { mockSecurityEventRepository.save(any()) }
    }
    
    @Test
    fun `should log rate limit exceeded event`() = runTest {
        // Given
        val identifier = "user123"
        val limitType = "API_REQUESTS"
        val ipAddress = "192.168.1.1"
        val userAgent = "Mozilla/5.0"
        
        val expectedEvent = SecurityEvent(
            id = UUID.randomUUID().toString(),
            eventType = SecurityEventType.RATE_LIMIT_EXCEEDED,
            userId = null,
            ipAddress = ipAddress,
            userAgent = userAgent,
            timestamp = LocalDateTime.now(),
            details = mapOf(
                "identifier" to identifier,
                "limitType" to limitType
            )
        )
        
        coEvery { mockSecurityEventRepository.save(any()) } returns expectedEvent
        
        // When
        val result = securityAuditService.logRateLimitExceeded(
            identifier, 
            limitType, 
            ipAddress, 
            userAgent
        )
        
        // Then
        assertNotNull(result)
        assertEquals(SecurityEventType.RATE_LIMIT_EXCEEDED, result.eventType)
        assertEquals(identifier, result.details["identifier"])
        assertEquals(limitType, result.details["limitType"])
        
        coVerify { mockSecurityEventRepository.save(any()) }
    }
    
    @Test
    fun `should log suspicious activity event`() = runTest {
        // Given
        val userId = "user123"
        val activity = "Multiple failed login attempts"
        val ipAddress = "192.168.1.1"
        val userAgent = "Mozilla/5.0"
        val riskScore = 75
        
        val expectedEvent = SecurityEvent(
            id = UUID.randomUUID().toString(),
            eventType = SecurityEventType.SUSPICIOUS_ACTIVITY,
            userId = userId,
            ipAddress = ipAddress,
            userAgent = userAgent,
            timestamp = LocalDateTime.now(),
            details = mapOf(
                "activity" to activity,
                "riskScore" to riskScore.toString()
            )
        )
        
        coEvery { mockSecurityEventRepository.save(any()) } returns expectedEvent
        
        // When
        val result = securityAuditService.logSuspiciousActivity(
            userId, 
            activity, 
            ipAddress, 
            userAgent, 
            riskScore
        )
        
        // Then
        assertNotNull(result)
        assertEquals(SecurityEventType.SUSPICIOUS_ACTIVITY, result.eventType)
        assertEquals(userId, result.userId)
        assertEquals(activity, result.details["activity"])
        assertEquals(riskScore.toString(), result.details["riskScore"])
        
        coVerify { mockSecurityEventRepository.save(any()) }
    }
    
    @Test
    fun `should get security events by user ID`() = runTest {
        // Given
        val userId = "user123"
        val events = listOf(
            SecurityEvent(
                id = "event1",
                eventType = SecurityEventType.LOGIN_SUCCESS,
                userId = userId,
                ipAddress = "192.168.1.1",
                userAgent = "Mozilla/5.0",
                timestamp = LocalDateTime.now(),
                details = emptyMap()
            ),
            SecurityEvent(
                id = "event2",
                eventType = SecurityEventType.API_ACCESS,
                userId = userId,
                ipAddress = "192.168.1.1",
                userAgent = "Mozilla/5.0",
                timestamp = LocalDateTime.now(),
                details = mapOf("endpoint" to "/api/sms")
            )
        )
        
        coEvery { mockSecurityEventRepository.findByUserId(userId) } returns events
        
        // When
        val result = securityAuditService.getSecurityEventsByUserId(userId)
        
        // Then
        assertEquals(2, result.size)
        assertEquals("event1", result[0].id)
        assertEquals("event2", result[1].id)
        
        coVerify { mockSecurityEventRepository.findByUserId(userId) }
    }
    
    @Test
    fun `should get security events by IP address`() = runTest {
        // Given
        val ipAddress = "192.168.1.1"
        val events = listOf(
            SecurityEvent(
                id = "event1",
                eventType = SecurityEventType.LOGIN_SUCCESS,
                userId = "user123",
                ipAddress = ipAddress,
                userAgent = "Mozilla/5.0",
                timestamp = LocalDateTime.now(),
                details = emptyMap()
            ),
            SecurityEvent(
                id = "event2",
                eventType = SecurityEventType.LOGIN_FAILURE,
                userId = null,
                ipAddress = ipAddress,
                userAgent = "Mozilla/5.0",
                timestamp = LocalDateTime.now(),
                details = mapOf("reason" to "Invalid password")
            )
        )
        
        coEvery { mockSecurityEventRepository.findByIpAddress(ipAddress) } returns events
        
        // When
        val result = securityAuditService.getSecurityEventsByIpAddress(ipAddress)
        
        // Then
        assertEquals(2, result.size)
        assertEquals("event1", result[0].id)
        assertEquals("event2", result[1].id)
        
        coVerify { mockSecurityEventRepository.findByIpAddress(ipAddress) }
    }
    
    @Test
    fun `should get security events by event type`() = runTest {
        // Given
        val eventType = SecurityEventType.LOGIN_FAILURE
        val events = listOf(
            SecurityEvent(
                id = "event1",
                eventType = eventType,
                userId = "user123",
                ipAddress = "192.168.1.1",
                userAgent = "Mozilla/5.0",
                timestamp = LocalDateTime.now(),
                details = mapOf("reason" to "Invalid password")
            ),
            SecurityEvent(
                id = "event2",
                eventType = eventType,
                userId = "user456",
                ipAddress = "192.168.1.2",
                userAgent = "Mozilla/5.0",
                timestamp = LocalDateTime.now(),
                details = mapOf("reason" to "Account locked")
            )
        )
        
        coEvery { mockSecurityEventRepository.findByEventType(eventType) } returns events
        
        // When
        val result = securityAuditService.getSecurityEventsByEventType(eventType)
        
        // Then
        assertEquals(2, result.size)
        assertEquals("event1", result[0].id)
        assertEquals("event2", result[1].id)
        
        coVerify { mockSecurityEventRepository.findByEventType(eventType) }
    }
    
    @Test
    fun `should get security events by time range`() = runTest {
        // Given
        val startTime = LocalDateTime.now().minusDays(1)
        val endTime = LocalDateTime.now()
        val events = listOf(
            SecurityEvent(
                id = "event1",
                eventType = SecurityEventType.LOGIN_SUCCESS,
                userId = "user123",
                ipAddress = "192.168.1.1",
                userAgent = "Mozilla/5.0",
                timestamp = startTime.plusHours(1),
                details = emptyMap()
            ),
            SecurityEvent(
                id = "event2",
                eventType = SecurityEventType.API_ACCESS,
                userId = "user123",
                ipAddress = "192.168.1.1",
                userAgent = "Mozilla/5.0",
                timestamp = startTime.plusHours(2),
                details = mapOf("endpoint" to "/api/sms")
            )
        )
        
        coEvery { 
            mockSecurityEventRepository.findByTimeRange(startTime, endTime) 
        } returns events
        
        // When
        val result = securityAuditService.getSecurityEventsByTimeRange(startTime, endTime)
        
        // Then
        assertEquals(2, result.size)
        assertEquals("event1", result[0].id)
        assertEquals("event2", result[1].id)
        
        coVerify { 
            mockSecurityEventRepository.findByTimeRange(startTime, endTime) 
        }
    }
    
    @Test
    fun `should clean up old security events successfully`() = runTest {
        // Given
        val olderThanDays = 30
        
        coEvery { mockSecurityEventRepository.deleteOlderThan(any()) } returns 100
        
        // When
        val result = securityAuditService.cleanupOldSecurityEvents(olderThanDays)
        
        // Then
        assertEquals(100, result)
        
        coVerify { mockSecurityEventRepository.deleteOlderThan(any()) }
    }
}