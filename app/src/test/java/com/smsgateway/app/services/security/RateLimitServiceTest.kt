package com.smsgateway.app.services.security

import com.smsgateway.app.models.security.RateLimitEntry
import com.smsgateway.app.models.security.RateLimitType
import com.smsgateway.app.repositories.RateLimitRepository
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

class RateLimitServiceTest {
    
    private lateinit var rateLimitService: RateLimitService
    private lateinit var mockRateLimitRepository: RateLimitRepository
    
    @BeforeEach
    fun setup() {
        mockRateLimitRepository = mockk()
        rateLimitService = RateLimitService(mockRateLimitRepository)
    }
    
    @Test
    fun `should check rate limit and allow request when under limit`() = runTest {
        // Given
        val identifier = "user123"
        val limitType = RateLimitType.API_REQUESTS
        val windowSizeMinutes = 60
        val maxRequests = 100
        
        val existingEntries = mutableListOf<RateLimitEntry>()
        for (i in 1..50) {
            existingEntries.add(
                RateLimitEntry(
                    id = UUID.randomUUID().toString(),
                    identifier = identifier,
                    limitType = limitType,
                    timestamp = LocalDateTime.now().minusMinutes(i.toLong()),
                    metadata = mapOf("endpoint" to "/api/sms")
                )
            )
        }
        
        coEvery { 
            mockRateLimitRepository.findByIdentifierAndTypeAndTimeWindow(
                identifier, 
                limitType, 
                any()
            ) 
        } returns existingEntries
        
        coEvery { mockRateLimitRepository.save(any()) } returns mockk()
        
        // When
        val result = rateLimitService.checkRateLimit(
            identifier, 
            limitType, 
            windowSizeMinutes, 
            maxRequests
        )
        
        // Then
        assertTrue(result.allowed)
        assertEquals(50, result.currentCount)
        assertEquals(100, result.limit)
        assertEquals(50, result.remaining)
        
        coVerify { 
            mockRateLimitRepository.findByIdentifierAndTypeAndTimeWindow(
                identifier, 
                limitType, 
                any()
            ) 
        }
        coVerify { mockRateLimitRepository.save(any()) }
    }
    
    @Test
    fun `should check rate limit and deny request when over limit`() = runTest {
        // Given
        val identifier = "user123"
        val limitType = RateLimitType.API_REQUESTS
        val windowSizeMinutes = 60
        val maxRequests = 100
        
        val existingEntries = mutableListOf<RateLimitEntry>()
        for (i in 1..100) {
            existingEntries.add(
                RateLimitEntry(
                    id = UUID.randomUUID().toString(),
                    identifier = identifier,
                    limitType = limitType,
                    timestamp = LocalDateTime.now().minusMinutes(i.toLong()),
                    metadata = mapOf("endpoint" to "/api/sms")
                )
            )
        }
        
        coEvery { 
            mockRateLimitRepository.findByIdentifierAndTypeAndTimeWindow(
                identifier, 
                limitType, 
                any()
            ) 
        } returns existingEntries
        
        // When
        val result = rateLimitService.checkRateLimit(
            identifier, 
            limitType, 
            windowSizeMinutes, 
            maxRequests
        )
        
        // Then
        assertFalse(result.allowed)
        assertEquals(100, result.currentCount)
        assertEquals(100, result.limit)
        assertEquals(0, result.remaining)
        
        coVerify { 
            mockRateLimitRepository.findByIdentifierAndTypeAndTimeWindow(
                identifier, 
                limitType, 
                any()
            ) 
        }
        coVerify(exactly = 0) { mockRateLimitRepository.save(any()) }
    }
    
    @Test
    fun `should record rate limit entry successfully`() = runTest {
        // Given
        val identifier = "user123"
        val limitType = RateLimitType.SMS_SENDING
        val metadata = mapOf("phoneNumber" to "+1234567890", "messageLength" to "160")
        
        val expectedEntry = RateLimitEntry(
            id = UUID.randomUUID().toString(),
            identifier = identifier,
            limitType = limitType,
            timestamp = LocalDateTime.now(),
            metadata = metadata
        )
        
        coEvery { mockRateLimitRepository.save(any()) } returns expectedEntry
        
        // When
        val result = rateLimitService.recordRateLimitEntry(identifier, limitType, metadata)
        
        // Then
        assertNotNull(result)
        assertEquals(identifier, result.identifier)
        assertEquals(limitType, result.limitType)
        assertEquals(metadata, result.metadata)
        
        coVerify { mockRateLimitRepository.save(any()) }
    }
    
    @Test
    fun `should get rate limit statistics successfully`() = runTest {
        // Given
        val identifier = "user123"
        val limitType = RateLimitType.API_REQUESTS
        val windowSizeMinutes = 60
        
        val existingEntries = mutableListOf<RateLimitEntry>()
        for (i in 1..30) {
            existingEntries.add(
                RateLimitEntry(
                    id = UUID.randomUUID().toString(),
                    identifier = identifier,
                    limitType = limitType,
                    timestamp = LocalDateTime.now().minusMinutes(i.toLong()),
                    metadata = mapOf("endpoint" to "/api/sms")
                )
            )
        }
        
        coEvery { 
            mockRateLimitRepository.findByIdentifierAndTypeAndTimeWindow(
                identifier, 
                limitType, 
                any()
            ) 
        } returns existingEntries
        
        // When
        val result = rateLimitService.getRateLimitStatistics(
            identifier, 
            limitType, 
            windowSizeMinutes
        )
        
        // Then
        assertEquals(30, result.count)
        assertNotNull(result.windowStart)
        assertNotNull(result.windowEnd)
        
        coVerify { 
            mockRateLimitRepository.findByIdentifierAndTypeAndTimeWindow(
                identifier, 
                limitType, 
                any()
            ) 
        }
    }
    
    @Test
    fun `should clean up expired entries successfully`() = runTest {
        // Given
        val expirationMinutes = 1440 // 24 hours
        
        coEvery { mockRateLimitRepository.deleteExpiredEntries(any()) } returns 50
        
        // When
        val result = rateLimitService.cleanupExpiredEntries(expirationMinutes)
        
        // Then
        assertEquals(50, result)
        
        coVerify { mockRateLimitRepository.deleteExpiredEntries(any()) }
    }
    
    @Test
    fun `should check rate limit with custom identifier`() = runTest {
        // Given
        val identifier = "192.168.1.1"
        val limitType = RateLimitType.IP_BASED
        val windowSizeMinutes = 60
        val maxRequests = 200
        
        val existingEntries = mutableListOf<RateLimitEntry>()
        for (i in 1..150) {
            existingEntries.add(
                RateLimitEntry(
                    id = UUID.randomUUID().toString(),
                    identifier = identifier,
                    limitType = limitType,
                    timestamp = LocalDateTime.now().minusMinutes(i.toLong()),
                    metadata = mapOf("ip" to identifier)
                )
            )
        }
        
        coEvery { 
            mockRateLimitRepository.findByIdentifierAndTypeAndTimeWindow(
                identifier, 
                limitType, 
                any()
            ) 
        } returns existingEntries
        
        coEvery { mockRateLimitRepository.save(any()) } returns mockk()
        
        // When
        val result = rateLimitService.checkRateLimit(
            identifier, 
            limitType, 
            windowSizeMinutes, 
            maxRequests
        )
        
        // Then
        assertTrue(result.allowed)
        assertEquals(150, result.currentCount)
        assertEquals(200, result.limit)
        assertEquals(50, result.remaining)
        
        coVerify { 
            mockRateLimitRepository.findByIdentifierAndTypeAndTimeWindow(
                identifier, 
                limitType, 
                any()
            ) 
        }
        coVerify { mockRateLimitRepository.save(any()) }
    }
    
    @Test
    fun `should handle different rate limit types correctly`() = runTest {
        // Given
        val identifier = "user123"
        val smsLimitType = RateLimitType.SMS_SENDING
        val windowSizeMinutes = 1440 // 24 hours
        val maxSmsPerDay = 50
        
        val existingEntries = mutableListOf<RateLimitEntry>()
        for (i in 1..25) {
            existingEntries.add(
                RateLimitEntry(
                    id = UUID.randomUUID().toString(),
                    identifier = identifier,
                    limitType = smsLimitType,
                    timestamp = LocalDateTime.now().minusMinutes(i.toLong()),
                    metadata = mapOf("phoneNumber" to "+1234567890")
                )
            )
        }
        
        coEvery { 
            mockRateLimitRepository.findByIdentifierAndTypeAndTimeWindow(
                identifier, 
                smsLimitType, 
                any()
            ) 
        } returns existingEntries
        
        coEvery { mockRateLimitRepository.save(any()) } returns mockk()
        
        // When
        val result = rateLimitService.checkRateLimit(
            identifier, 
            smsLimitType, 
            windowSizeMinutes, 
            maxSmsPerDay
        )
        
        // Then
        assertTrue(result.allowed)
        assertEquals(25, result.currentCount)
        assertEquals(50, result.limit)
        assertEquals(25, result.remaining)
        
        coVerify { 
            mockRateLimitRepository.findByIdentifierAndTypeAndTimeWindow(
                identifier, 
                smsLimitType, 
                any()
            ) 
        }
        coVerify { mockRateLimitRepository.save(any()) }
    }
    
    @Test
    fun `should check rate limit without recording when dryRun is true`() = runTest {
        // Given
        val identifier = "user123"
        val limitType = RateLimitType.API_REQUESTS
        val windowSizeMinutes = 60
        val maxRequests = 100
        
        val existingEntries = mutableListOf<RateLimitEntry>()
        for (i in 1..50) {
            existingEntries.add(
                RateLimitEntry(
                    id = UUID.randomUUID().toString(),
                    identifier = identifier,
                    limitType = limitType,
                    timestamp = LocalDateTime.now().minusMinutes(i.toLong()),
                    metadata = mapOf("endpoint" to "/api/sms")
                )
            )
        }
        
        coEvery { 
            mockRateLimitRepository.findByIdentifierAndTypeAndTimeWindow(
                identifier, 
                limitType, 
                any()
            ) 
        } returns existingEntries
        
        // When
        val result = rateLimitService.checkRateLimit(
            identifier, 
            limitType, 
            windowSizeMinutes, 
            maxRequests,
            dryRun = true
        )
        
        // Then
        assertTrue(result.allowed)
        assertEquals(50, result.currentCount)
        assertEquals(100, result.limit)
        assertEquals(50, result.remaining)
        
        coVerify { 
            mockRateLimitRepository.findByIdentifierAndTypeAndTimeWindow(
                identifier, 
                limitType, 
                any()
            ) 
        }
        coVerify(exactly = 0) { mockRateLimitRepository.save(any()) }
    }
}