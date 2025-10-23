package com.smsgateway.app.health

import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.smsgateway.app.database.SmsDao
import com.smsgateway.app.queue.SmsQueueService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service responsible for checking the health of the SMS Gateway system
 * Monitors SMS permissions, SIM status, network connectivity, and queue health
 */
class HealthChecker(
    private val context: Context,
    private val smsDao: SmsDao,
    private val smsQueueService: SmsQueueService
) {
    
    companion object {
        private const val TAG = "HealthChecker"
        
        // Health check thresholds
        private const val MAX_QUEUE_SIZE_WARNING = 100
        private const val MAX_QUEUE_SIZE_CRITICAL = 500
        private const val MAX_FAILED_MESSAGES_WARNING = 10
        private const val MAX_FAILED_MESSAGES_CRITICAL = 50
        private const val MAX_PROCESSING_TIME_WARNING_MS = 30000L // 30 seconds
        private const val MAX_PROCESSING_TIME_CRITICAL_MS = 120000L // 2 minutes
    }
    
    /**
     * Performs a comprehensive health check of the SMS Gateway system
     * 
     * @return SystemHealth object containing the overall health status
     */
    suspend fun performHealthCheck(): SystemHealth {
        return withContext(Dispatchers.IO) {
            val currentTime = System.currentTimeMillis()
            
            Log.d(TAG, "Starting comprehensive health check")
            
            // Check individual components
            val smsPermission = checkSmsPermission()
            val simStatus = checkSimStatus()
            val networkConnectivity = checkNetworkConnectivity()
            val queueHealth = checkQueueHealth()
            
            // Determine overall health status
            val overallStatus = determineOverallHealthStatus(
                smsPermission, simStatus, networkConnectivity, queueHealth
            )
            
            val systemHealth = SystemHealth(
                overallStatus = overallStatus,
                smsPermission = smsPermission,
                simStatus = simStatus,
                networkConnectivity = networkConnectivity,
                queueHealth = queueHealth,
                lastCheckTime = currentTime
            )
            
            Log.d(TAG, "Health check completed: $overallStatus")
            systemHealth
        }
    }
    
    /**
     * Checks if the app has SMS permissions
     * 
     * @return true if SMS permissions are granted, false otherwise
     */
    private fun checkSmsPermission(): Boolean {
        return try {
            val hasSendPermission = context.checkSelfPermission(android.Manifest.permission.SEND_SMS) 
                == PackageManager.PERMISSION_GRANTED
            val hasReceivePermission = context.checkSelfPermission(android.Manifest.permission.RECEIVE_SMS) 
                == PackageManager.PERMISSION_GRANTED
            val hasReadPermission = context.checkSelfPermission(android.Manifest.permission.READ_SMS) 
                == PackageManager.PERMISSION_GRANTED
            
            val hasAllPermissions = hasSendPermission && hasReceivePermission && hasReadPermission
            
            if (!hasAllPermissions) {
                Log.w(TAG, "Missing SMS permissions - Send: $hasSendPermission, Receive: $hasReceivePermission, Read: $hasReadPermission")
            }
            
            hasAllPermissions
        } catch (e: Exception) {
            Log.e(TAG, "Error checking SMS permissions", e)
            false
        }
    }
    
    /**
     * Checks the status of the SIM card
     * 
     * @return SIM status string
     */
    private fun checkSimStatus(): String {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            
            when {
                telephonyManager.simState == TelephonyManager.SIM_STATE_READY -> "READY"
                telephonyManager.simState == TelephonyManager.SIM_STATE_ABSENT -> "ABSENT"
                telephonyManager.simState == TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN_REQUIRED"
                telephonyManager.simState == TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK_REQUIRED"
                telephonyManager.simState == TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "NETWORK_LOCKED"
                telephonyManager.simState == TelephonyManager.SIM_STATE_NOT_READY -> "NOT_READY"
                else -> "UNKNOWN"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking SIM status", e)
            "ERROR"
        }
    }
    
    /**
     * Checks network connectivity
     * 
     * @return true if network is available, false otherwise
     */
    private fun checkNetworkConnectivity(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetworkInfo
            
            val isConnected = activeNetwork?.isConnectedOrConnecting == true
            
            if (!isConnected) {
                Log.w(TAG, "No network connectivity available")
            }
            
            isConnected
        } catch (e: Exception) {
            Log.e(TAG, "Error checking network connectivity", e)
            false
        }
    }
    
    /**
     * Checks the health of the SMS queue
     * 
     * @return QueueHealth object containing queue statistics and status
     */
    private suspend fun checkQueueHealth(): QueueHealth {
        return try {
            val queueStats = smsQueueService.getQueueStats()
            
            // Calculate processing rate (messages per minute)
            val processingRate = calculateProcessingRate()
            
            // Determine queue health status
            val status = determineQueueHealthStatus(queueStats, processingRate)
            
            QueueHealth(
                status = status,
                size = queueStats.totalMessages,
                processingRate = processingRate,
                averageWaitTime = queueStats.averageWaitTime,
                throughputPerHour = queueStats.throughputPerHour,
                errorRate = queueStats.errorRate
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error checking queue health", e)
            QueueHealth(
                status = HealthStatus.DOWN,
                size = 0,
                processingRate = 0.0,
                averageWaitTime = 0L,
                throughputPerHour = 0,
                errorRate = 1.0
            )
        }
    }
    
    /**
     * Calculates the processing rate (messages per minute)
     * 
     * @return Processing rate as messages per minute
     */
    private suspend fun calculateProcessingRate(): Double {
        return try {
            val oneMinuteAgo = System.currentTimeMillis() - 60000L
            val recentMessages = smsDao.getMessagesUpdatedAfter(oneMinuteAgo)
            
            val processedMessages = recentMessages.count { 
                it.status == com.smsgateway.app.database.SmsStatus.SENT 
            }
            
            processedMessages.toDouble()
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating processing rate", e)
            0.0
        }
    }
    
    /**
     * Determines the queue health status based on statistics
     * 
     * @param queueStats Queue statistics
     * @param processingRate Processing rate in messages per minute
     * @return Queue health status
     */
    private fun determineQueueHealthStatus(
        queueStats: com.smsgateway.app.queue.QueueStats,
        processingRate: Double
    ): HealthStatus {
        // Check for critical conditions
        if (queueStats.totalMessages >= MAX_QUEUE_SIZE_CRITICAL ||
            queueStats.failedMessages >= MAX_FAILED_MESSAGES_CRITICAL ||
            processingRate == 0.0 && queueStats.queuedMessages > 0) {
            return HealthStatus.CRITICAL
        }
        
        // Check for warning conditions
        if (queueStats.totalMessages >= MAX_QUEUE_SIZE_WARNING ||
            queueStats.failedMessages >= MAX_FAILED_MESSAGES_WARNING ||
            processingRate < 1.0 && queueStats.queuedMessages > 10) {
            return HealthStatus.WARNING
        }
        
        // Check for healthy conditions
        if (queueStats.errorRate < 0.05 && processingRate > 0.5) {
            return HealthStatus.HEALTHY
        }
        
        return HealthStatus.WARNING
    }
    
    /**
     * Determines the overall system health status
     * 
     * @param smsPermission SMS permission status
     * @param simStatus SIM status
     * @param networkConnectivity Network connectivity status
     * @param queueHealth Queue health status
     * @return Overall health status
     */
    private fun determineOverallHealthStatus(
        smsPermission: Boolean,
        simStatus: String,
        networkConnectivity: Boolean,
        queueHealth: QueueHealth
    ): HealthStatus {
        // Check for critical conditions
        if (!smsPermission || 
            simStatus == "ABSENT" || 
            simStatus == "ERROR" || 
            queueHealth.status == HealthStatus.DOWN ||
            queueHealth.status == HealthStatus.CRITICAL) {
            return HealthStatus.CRITICAL
        }
        
        // Check for warning conditions
        if (simStatus != "READY" || 
            !networkConnectivity || 
            queueHealth.status == HealthStatus.WARNING) {
            return HealthStatus.WARNING
        }
        
        // All checks passed - system is healthy
        return HealthStatus.HEALTHY
    }
    
    /**
     * Checks if the system is ready to send SMS messages
     * 
     * @return true if system is ready, false otherwise
     */
    suspend fun isSystemReadyForSms(): Boolean {
        val health = performHealthCheck()
        return health.overallStatus == HealthStatus.HEALTHY && 
               health.smsPermission && 
               health.simStatus == "READY" &&
               health.networkConnectivity
    }
    
    /**
     * Gets a quick health status without detailed statistics
     * 
     * @return Quick health status
     */
    suspend fun getQuickHealthStatus(): HealthStatus {
        return try {
            val smsPermission = checkSmsPermission()
            val simStatus = checkSimStatus()
            val networkConnectivity = checkNetworkConnectivity()
            
            when {
                !smsPermission -> HealthStatus.CRITICAL
                simStatus != "READY" -> HealthStatus.WARNING
                !networkConnectivity -> HealthStatus.WARNING
                else -> HealthStatus.HEALTHY
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting quick health status", e)
            HealthStatus.DOWN
        }
    }
}