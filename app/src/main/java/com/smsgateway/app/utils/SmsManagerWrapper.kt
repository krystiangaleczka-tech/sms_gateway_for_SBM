package com.smsgateway.app.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.telephony.SmsManager.RESULT_ERROR_GENERIC_FAILURE
import android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE
import android.telephony.SmsManager.RESULT_ERROR_NULL_PDU
import android.telephony.SmsManager.RESULT_ERROR_RADIO_OFF
import androidx.core.content.ContextCompat
import org.slf4j.LoggerFactory

/**
 * Wrapper na Android SmsManager
 * Umożliwia wysyłanie SMS z obsługą błędów i logowaniem
 */
class SmsManagerWrapper(private val context: Context) {
    
    private val logger = LoggerFactory.getLogger(SmsManagerWrapper::class.java)
    private val smsManager: SmsManager = context.getSystemService(SmsManager::class.java)
    
    /**
     * Wysyła wiadomość SMS na podany numer
     * 
     * @param phoneNumber Numer telefonu w formacie E.164
     * @param message Treść wiadomości SMS
     * @throws SmsException Jeśli wystąpi błąd podczas wysyłki
     * @throws SecurityException Jeśli brak uprawnień
     */
    @Throws(SmsException::class, SecurityException::class)
    fun sendTextMessage(phoneNumber: String, message: String) {
        // Sprawdzenie uprawnień
        if (!hasSmsPermission()) {
            logger.error("SMS permission not granted")
            throw SecurityException("SMS permission not granted")
        }
        
        // Walidacja numeru telefonu (podstawowa)
        if (!isValidPhoneNumber(phoneNumber)) {
            logger.error("Invalid phone number: $phoneNumber")
            throw SmsException("Invalid phone number: $phoneNumber")
        }
        
        // Walidacja treści wiadomości
        if (message.isEmpty()) {
            logger.error("Message content is empty")
            throw SmsException("Message content is empty")
        }
        
        if (message.length > 160) {
            logger.warn("Message length exceeds 160 characters: ${message.length}")
        }
        
        logger.info("Sending SMS to $phoneNumber")
        
        try {
            // Wysyłka SMS
            smsManager.sendTextMessage(
                phoneNumber,        // destinationAddress
                null,                // smsc (null = use default)
                message,             // text
                null,                // sentIntent
                null,                // deliveryIntent
                null                 // dataIntent (for MMS)
            )
            
            logger.info("SMS sent successfully to $phoneNumber")
            
        } catch (e: Exception) {
            logger.error("Failed to send SMS to $phoneNumber", e)
            
            // Przekształcenie wyjątku na SmsException
            val errorMessage = when (e.message) {
                "RESULT_ERROR_GENERIC_FAILURE" -> "Generic failure"
                "RESULT_ERROR_NO_SERVICE" -> "No service available"
                "RESULT_ERROR_NULL_PDU" -> "Null PDU"
                "RESULT_ERROR_RADIO_OFF" -> "Radio off"
                else -> e.message ?: "Unknown error"
            }
            
            throw SmsException("Failed to send SMS: $errorMessage", e)
        }
    }
    
    /**
     * Wysyła wiadomość SMS z podziałem na części (dla długich wiadomości)
     * 
     * @param phoneNumber Numer telefonu w formacie E.164
     * @param message Treść wiadomości SMS
     * @throws SmsException Jeśli wystąpi błąd podczas wysyłki
     * @throws SecurityException Jeśli brak uprawnień
     */
    @Throws(SmsException::class, SecurityException::class)
    fun sendMultipartTextMessage(phoneNumber: String, message: String) {
        // Sprawdzenie uprawnień
        if (!hasSmsPermission()) {
            logger.error("SMS permission not granted")
            throw SecurityException("SMS permission not granted")
        }
        
        // Walidacja numeru telefonu
        if (!isValidPhoneNumber(phoneNumber)) {
            logger.error("Invalid phone number: $phoneNumber")
            throw SmsException("Invalid phone number: $phoneNumber")
        }
        
        // Walidacja treści wiadomości
        if (message.isEmpty()) {
            logger.error("Message content is empty")
            throw SmsException("Message content is empty")
        }
        
        logger.info("Sending multipart SMS to $phoneNumber (length: ${message.length})")
        
        try {
            // Podział wiadomości na części
            val parts = smsManager.divideMessage(message)
            
            if (parts.size == 1) {
                // Krótka wiadomość - użyj zwykłej metody
                sendTextMessage(phoneNumber, message)
                return
            }
            
            logger.info("Message divided into ${parts.size} parts")
            
            // Wysyłka wieloczęściowej wiadomości
            smsManager.sendMultipartTextMessage(
                phoneNumber,        // destinationAddress
                null,                // smsc (null = use default)
                parts,               // parts
                null,                // sentIntents
                null,                // deliveryIntents
                null                 // dataIntents (for MMS)
            )
            
            logger.info("Multipart SMS sent successfully to $phoneNumber")
            
        } catch (e: Exception) {
            logger.error("Failed to send multipart SMS to $phoneNumber", e)
            throw SmsException("Failed to send multipart SMS: ${e.message}", e)
        }
    }
    
    /**
     * Sprawdza czy aplikacja ma uprawnienia do wysyłania SMS
     */
    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Podstawowa walidacja numeru telefonu w formacie E.164
     */
    private fun isValidPhoneNumber(phoneNumber: String): Boolean {
        // Podstawowa walidacja formatu E.164 (+ followed by digits)
        return phoneNumber.isNotEmpty() && 
               phoneNumber.startsWith("+") && 
               phoneNumber.substring(1).all { it.isDigit() }
    }
    
    /**
     * Pobiera informacje o stanie SIM karty
     */
    fun getSimState(): String {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
            when (telephonyManager.simState) {
                android.telephony.TelephonyManager.SIM_STATE_ABSENT -> "ABSENT"
                android.telephony.TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "NETWORK_LOCKED"
                android.telephony.TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN_REQUIRED"
                android.telephony.TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK_REQUIRED"
                android.telephony.TelephonyManager.SIM_STATE_READY -> "READY"
                android.telephony.TelephonyManager.SIM_STATE_UNKNOWN -> "UNKNOWN"
                else -> "UNKNOWN"
            }
        } catch (e: Exception) {
            logger.error("Failed to get SIM state", e)
            "ERROR"
        }
    }
    
    /**
     * Wyjątek rzucany w przypadku błędów wysyłki SMS
     */
    class SmsException : Exception {
        constructor(message: String) : super(message)
        constructor(message: String, cause: Throwable) : super(message, cause)
    }
}