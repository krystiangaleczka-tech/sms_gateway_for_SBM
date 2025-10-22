package com.smsgateway.app.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmsDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var smsDao: SmsDao

    @Before
    fun createDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        smsDao = database.smsDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun insertSms_returnsId() = runTest {
        // Given
        val smsMessage = createTestSmsMessage()

        // When
        val insertedId = smsDao.insertSms(smsMessage)

        // Then
        assertTrue(insertedId > 0)
    }

    @Test
    fun getSmsById_returnsInsertedSms() = runTest {
        // Given
        val smsMessage = createTestSmsMessage()
        val insertedId = smsDao.insertSms(smsMessage)

        // When
        val retrievedSms = smsDao.getSmsById(insertedId)

        // Then
        assertNotNull(retrievedSms)
        assertEquals(insertedId, retrievedSms?.id)
        assertEquals(smsMessage.phoneNumber, retrievedSms?.phoneNumber)
        assertEquals(smsMessage.messageContent, retrievedSms?.messageContent)
        assertEquals(smsMessage.status, retrievedSms?.status)
    }

    @Test
    fun getAllSms_returnsAllInsertedSms() = runTest {
        // Given
        val smsMessage1 = createTestSmsMessage(phoneNumber = "+48123456789")
        val smsMessage2 = createTestSmsMessage(phoneNumber = "+48987654321")
        smsDao.insertSms(smsMessage1)
        smsDao.insertSms(smsMessage2)

        // When
        val allSms = smsDao.getAllSmsSync()

        // Then
        assertEquals(2, allSms.size)
        assertTrue(allSms.any { it.phoneNumber == "+48123456789" })
        assertTrue(allSms.any { it.phoneNumber == "+48987654321" })
    }

    @Test
    fun getAllSmsFlow_emitsUpdates() = runTest {
        // Given
        val smsMessage = createTestSmsMessage()
        
        // When
        val flowValues = mutableListOf<List<SmsMessage>>()
        smsDao.getAllSms().collect { flowValues.add(it) }
        
        // Initially empty
        assertEquals(0, flowValues.first().size)
        
        // Insert SMS
        smsDao.insertSms(smsMessage)
        
        // Get latest value
        val latestValue = smsDao.getAllSms().first()
        
        // Then
        assertEquals(1, latestValue.size)
        assertEquals(smsMessage.phoneNumber, latestValue.first().phoneNumber)
    }

    @Test
    fun updateSmsStatus_updatesStatus() = runTest {
        // Given
        val smsMessage = createTestSmsMessage(status = SmsStatus.QUEUED)
        val insertedId = smsDao.insertSms(smsMessage)

        // When
        val updatedRows = smsDao.updateSmsStatus(insertedId, SmsStatus.SENT)
        val updatedSms = smsDao.getSmsById(insertedId)

        // Then
        assertEquals(1, updatedRows)
        assertEquals(SmsStatus.SENT, updatedSms?.status)
    }

    @Test
    fun updateSmsStatusWithSentTime_updatesStatusAndSentTime() = runTest {
        // Given
        val smsMessage = createTestSmsMessage(status = SmsStatus.SENDING)
        val insertedId = smsDao.insertSms(smsMessage)
        val sentTime = System.currentTimeMillis()

        // When
        val updatedRows = smsDao.updateSmsStatusWithSentTime(insertedId, SmsStatus.SENT, sentTime)
        val updatedSms = smsDao.getSmsById(insertedId)

        // Then
        assertEquals(1, updatedRows)
        assertEquals(SmsStatus.SENT, updatedSms?.status)
        assertEquals(sentTime, updatedSms?.sentAt)
    }

    @Test
    fun updateSmsStatusWithError_updatesStatusSentTimeAndError() = runTest {
        // Given
        val smsMessage = createTestSmsMessage(status = SmsStatus.SENDING)
        val insertedId = smsDao.insertSms(smsMessage)
        val sentTime = System.currentTimeMillis()
        val errorMessage = "Network error"

        // When
        val updatedRows = smsDao.updateSmsStatusWithError(insertedId, SmsStatus.FAILED, sentTime, errorMessage)
        val updatedSms = smsDao.getSmsById(insertedId)

        // Then
        assertEquals(1, updatedRows)
        assertEquals(SmsStatus.FAILED, updatedSms?.status)
        assertEquals(sentTime, updatedSms?.sentAt)
        assertEquals(errorMessage, updatedSms?.errorMessage)
    }

    @Test
    fun updateSmsStatusWithErrorAndIncrementRetry_updatesAllFields() = runTest {
        // Given
        val smsMessage = createTestSmsMessage(status = SmsStatus.SENDING, retryCount = 1)
        val insertedId = smsDao.insertSms(smsMessage)
        val sentTime = System.currentTimeMillis()
        val errorMessage = "Network error"

        // When
        smsDao.updateSmsStatusWithErrorAndIncrementRetry(insertedId, SmsStatus.FAILED, sentTime, errorMessage)
        val updatedSms = smsDao.getSmsById(insertedId)

        // Then
        assertEquals(SmsStatus.FAILED, updatedSms?.status)
        assertEquals(sentTime, updatedSms?.sentAt)
        assertEquals(errorMessage, updatedSms?.errorMessage)
        assertEquals(2, updatedSms?.retryCount) // Incremented from 1 to 2
    }

    @Test
    fun incrementRetryCount_incrementsRetryCount() = runTest {
        // Given
        val smsMessage = createTestSmsMessage(retryCount = 1)
        val insertedId = smsDao.insertSms(smsMessage)

        // When
        val updatedRows = smsDao.incrementRetryCount(insertedId)
        val updatedSms = smsDao.getSmsById(insertedId)

        // Then
        assertEquals(1, updatedRows)
        assertEquals(2, updatedSms?.retryCount)
    }

    @Test
    fun getSmsByStatus_returnsOnlySmsWithGivenStatus() = runTest {
        // Given
        val queuedSms = createTestSmsMessage(status = SmsStatus.QUEUED)
        val sentSms = createTestSmsMessage(status = SmsStatus.SENT)
        val failedSms = createTestSmsMessage(status = SmsStatus.FAILED)
        smsDao.insertSms(queuedSms)
        smsDao.insertSms(sentSms)
        smsDao.insertSms(failedSms)

        // When
        val queuedSmsList = smsDao.getSmsByStatus(SmsStatus.QUEUED)
        val sentSmsList = smsDao.getSmsByStatus(SmsStatus.SENT)

        // Then
        assertEquals(1, queuedSmsList.size)
        assertEquals(1, sentSmsList.size)
        assertEquals(SmsStatus.QUEUED, queuedSmsList.first().status)
        assertEquals(SmsStatus.SENT, sentSmsList.first().status)
    }

    @Test
    fun getScheduledForSending_returnsOnlyScheduledMessagesPastCurrentTime() = runTest {
        // Given
        val currentTime = System.currentTimeMillis()
        val pastScheduledSms = createTestSmsMessage(
            status = SmsStatus.SCHEDULED,
            scheduledAt = currentTime - 1000 // 1 second ago
        )
        val futureScheduledSms = createTestSmsMessage(
            status = SmsStatus.SCHEDULED,
            scheduledAt = currentTime + 1000 // 1 second in future
        )
        val queuedSms = createTestSmsMessage(status = SmsStatus.QUEUED)
        smsDao.insertSms(pastScheduledSms)
        smsDao.insertSms(futureScheduledSms)
        smsDao.insertSms(queuedSms)

        // When
        val scheduledForSending = smsDao.getScheduledForSending(currentTime)

        // Then
        assertEquals(1, scheduledForSending.size)
        assertEquals(pastScheduledSms.phoneNumber, scheduledForSending.first().phoneNumber)
    }

    @Test
    fun getQueuedSms_returnsOnlyQueuedMessages() = runTest {
        // Given
        val queuedSms1 = createTestSmsMessage(status = SmsStatus.QUEUED)
        val queuedSms2 = createTestSmsMessage(status = SmsStatus.QUEUED)
        val sentSms = createTestSmsMessage(status = SmsStatus.SENT)
        smsDao.insertSms(queuedSms1)
        smsDao.insertSms(queuedSms2)
        smsDao.insertSms(sentSms)

        // When
        val queuedSmsList = smsDao.getQueuedSms()

        // Then
        assertEquals(2, queuedSmsList.size)
        assertTrue(queuedSmsList.all { it.status == SmsStatus.QUEUED })
    }

    @Test
    fun deleteSmsById_deletesSms() = runTest {
        // Given
        val smsMessage = createTestSmsMessage()
        val insertedId = smsDao.insertSms(smsMessage)

        // When
        val deletedRows = smsDao.deleteSmsById(insertedId)
        val deletedSms = smsDao.getSmsById(insertedId)

        // Then
        assertEquals(1, deletedRows)
        assertNull(deletedSms)
    }

    @Test
    fun getSmsCountByStatus_returnsCorrectCount() = runTest {
        // Given
        val queuedSms1 = createTestSmsMessage(status = SmsStatus.QUEUED)
        val queuedSms2 = createTestSmsMessage(status = SmsStatus.QUEUED)
        val sentSms = createTestSmsMessage(status = SmsStatus.SENT)
        smsDao.insertSms(queuedSms1)
        smsDao.insertSms(queuedSms2)
        smsDao.insertSms(sentSms)

        // When
        val queuedCount = smsDao.getSmsCountByStatus(SmsStatus.QUEUED)
        val sentCount = smsDao.getSmsCountByStatus(SmsStatus.SENT)

        // Then
        assertEquals(2, queuedCount)
        assertEquals(1, sentCount)
    }

    @Test
    fun getSmsStatsLast24Hours_returnsCorrectStats() = runTest {
        // Given
        val currentTime = System.currentTimeMillis()
        val twentyFourHoursAgo = currentTime - (24 * 60 * 60 * 1000)
        
        val recentSms = createTestSmsMessage(
            status = SmsStatus.SENT,
            createdAt = currentTime - 1000 // 1 second ago
        )
        val oldSms = createTestSmsMessage(
            status = SmsStatus.QUEUED,
            createdAt = twentyFourHoursAgo - 1000 // 1 second before 24h ago
        )
        smsDao.insertSms(recentSms)
        smsDao.insertSms(oldSms)

        // When
        val stats = smsDao.getSmsStatsLast24Hours(currentTime)

        // Then
        assertEquals(1, stats.size)
        assertEquals("SENT", stats.first().status)
        assertEquals(1, stats.first().count)
    }

    @Test
    fun cleanupOldMessages_deletesOldSentAndCancelledMessages() = runTest {
        // Given
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        val oldSentSms = createTestSmsMessage(
            status = SmsStatus.SENT,
            createdAt = thirtyDaysAgo - 1000
        )
        val oldCancelledSms = createTestSmsMessage(
            status = SmsStatus.CANCELLED,
            createdAt = thirtyDaysAgo - 1000
        )
        val oldFailedSms = createTestSmsMessage(
            status = SmsStatus.FAILED,
            createdAt = thirtyDaysAgo - 1000
        )
        val recentSms = createTestSmsMessage(status = SmsStatus.SENT)
        smsDao.insertSms(oldSentSms)
        smsDao.insertSms(oldCancelledSms)
        smsDao.insertSms(oldFailedSms)
        smsDao.insertSms(recentSms)

        // When
        val deletedRows = smsDao.cleanupOldMessages(thirtyDaysAgo)
        val remainingSms = smsDao.getAllSmsSync()

        // Then
        assertEquals(2, deletedRows) // oldSentSms and oldCancelledSms
        assertEquals(2, remainingSms.size) // oldFailedSms and recentSms
        assertTrue(remainingSms.any { it.status == SmsStatus.FAILED })
        assertTrue(remainingSms.any { it.status == SmsStatus.SENT })
    }

    // Helper function to create test SMS messages
    private fun createTestSmsMessage(
        phoneNumber: String = "+48123456789",
        messageContent: String = "Test message",
        status: SmsStatus = SmsStatus.QUEUED,
        createdAt: Long = System.currentTimeMillis(),
        scheduledAt: Long? = null,
        sentAt: Long? = null,
        errorMessage: String? = null,
        retryCount: Int = 0,
        maxRetries: Int = 3
    ): SmsMessage {
        return SmsMessage(
            phoneNumber = phoneNumber,
            messageContent = messageContent,
            status = status,
            createdAt = createdAt,
            scheduledAt = scheduledAt,
            sentAt = sentAt,
            errorMessage = errorMessage,
            retryCount = retryCount,
            maxRetries = maxRetries
        )
    }
}