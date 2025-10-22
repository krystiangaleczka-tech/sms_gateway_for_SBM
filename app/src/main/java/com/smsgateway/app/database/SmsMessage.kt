package com.smsgateway.app.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Index
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Encja Room reprezentująca wiadomość SMS w bazie danych
 */
@Entity(
    tableName = "sms_messages",
    indices = [
        Index(value = ["status"]),
        Index(value = ["scheduled_at"]),
        Index(value = ["status", "scheduled_at"])
    ]
)
data class SmsMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "phone_number")
    val phoneNumber: String,
    
    @ColumnInfo(name = "message_content")
    val messageContent: String,
    
    @ColumnInfo(name = "status")
    val status: SmsStatus,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    
    @ColumnInfo(name = "scheduled_at")
    val scheduledAt: Long?,
    
    @ColumnInfo(name = "sent_at")
    val sentAt: Long?,
    
    @ColumnInfo(name = "error_message")
    val errorMessage: String?,
    
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,
    
    @ColumnInfo(name = "max_retries")
    val maxRetries: Int = 3
)

/**
 * Enum definiujący statusy wiadomości SMS
 */
enum class SmsStatus {
    QUEUED,        // W kolejce do wysyłki
    SCHEDULED,     // Zaplanowana do wysyłki
    SENDING,       // W trakcie wysyłania
    SENT,          // Wysłana pomyślnie
    FAILED,        // Błąd wysyłki
    CANCELLED      // Anulowana
}

/**
 * Funkcje rozszerzające dla formatowania dat
 */
fun Long.toDateTimeString(): String {
    return if (this > 0) {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    } else {
        "N/A"
    }
}

fun Long?.toDateTimeString(): String {
    return this?.toDateTimeString() ?: "N/A"
}