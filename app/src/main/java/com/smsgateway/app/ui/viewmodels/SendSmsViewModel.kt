package com.smsgateway.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smsgateway.app.api.models.SmsPriority
import com.smsgateway.app.repositories.SmsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * ViewModel dla ekranu wysyłania SMS
 * Zarządza stanem danych i logiką biznesową dla formularza wysyłania SMS
 */
class SendSmsViewModel(application: Application) : AndroidViewModel(application) {
    private val smsRepository = SmsRepository(application)
    
    // Stany dla formularza
    private val _phoneNumber = MutableStateFlow("")
    val phoneNumber: StateFlow<String> = _phoneNumber.asStateFlow()
    
    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()
    
    private val _priority = MutableStateFlow(SmsPriority.MEDIUM)
    val priority: StateFlow<SmsPriority> = _priority.asStateFlow()
    
    private val _sendAt = MutableStateFlow<Date?>(null)
    val sendAt: StateFlow<Date?> = _sendAt.asStateFlow()
    
    private val _sendLater = MutableStateFlow(false)
    val sendLater: StateFlow<Boolean> = _sendLater.asStateFlow()
    
    // Stany dla walidacji
    private val _phoneNumberError = MutableStateFlow<String?>(null)
    val phoneNumberError: StateFlow<String?> = _phoneNumberError.asStateFlow()
    
    private val _messageError = MutableStateFlow<String?>(null)
    val messageError: StateFlow<String?> = _messageError.asStateFlow()
    
    private val _sendAtError = MutableStateFlow<String?>(null)
    val sendAtError: StateFlow<String?> = _sendAtError.asStateFlow()
    
    // Stany dla wysyłania
    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()
    
    private val _sendResult = MutableStateFlow<com.smsgateway.app.api.models.SendSmsResponse?>(null)
    val sendResult: StateFlow<com.smsgateway.app.api.models.SendSmsResponse?> = _sendResult.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()
    
    // Stany dla historii wysyłania
    private val _recentPhoneNumbers = MutableStateFlow<List<String>>(emptyList())
    val recentPhoneNumbers: StateFlow<List<String>> = _recentPhoneNumbers.asStateFlow()
    
    private val _recentMessages = MutableStateFlow<List<String>>(emptyList())
    val recentMessages: StateFlow<List<String>> = _recentMessages.asStateFlow()
    
    init {
        loadRecentData()
    }
    
    /**
     * Ustawia numer telefonu
     */
    fun setPhoneNumber(number: String) {
        _phoneNumber.value = number
        validatePhoneNumber()
    }
    
    /**
     * Ustawia treść wiadomości
     */
    fun setMessage(text: String) {
        _message.value = text
        validateMessage()
    }
    
    /**
     * Ustawia priorytet wiadomości
     */
    fun setPriority(priority: SmsPriority) {
        _priority.value = priority
    }
    
    /**
     * Ustawia datę wysyłania
     */
    fun setSendAt(date: Date?) {
        _sendAt.value = date
        validateSendAt()
    }
    
    /**
     * Włącza/wyłącza wysyłanie z opóźnieniem
     */
    fun setSendLater(enabled: Boolean) {
        _sendLater.value = enabled
        if (!enabled) {
            _sendAt.value = null
            _sendAtError.value = null
        }
    }
    
    /**
     * Waliduje numer telefonu
     */
    private fun validatePhoneNumber(): Boolean {
        val number = _phoneNumber.value.trim()
        
        return when {
            number.isEmpty() -> {
                _phoneNumberError.value = "Numer telefonu jest wymagany"
                false
            }
            !isValidPhoneNumber(number) -> {
                _phoneNumberError.value = "Nieprawidłowy format numeru telefonu"
                false
            }
            else -> {
                _phoneNumberError.value = null
                true
            }
        }
    }
    
    /**
     * Waliduje treść wiadomości
     */
    private fun validateMessage(): Boolean {
        val text = _message.value.trim()
        
        return when {
            text.isEmpty() -> {
                _messageError.value = "Treść wiadomości jest wymagana"
                false
            }
            text.length > 160 -> {
                _messageError.value = "Wiadomość może mieć maksymalnie 160 znaków"
                false
            }
            else -> {
                _messageError.value = null
                true
            }
        }
    }
    
    /**
     * Waliduje datę wysyłania
     */
    private fun validateSendAt(): Boolean {
        if (!_sendLater.value) return true
        
        val date = _sendAt.value
        
        return when {
            date == null -> {
                _sendAtError.value = "Data wysyłania jest wymagana"
                false
            }
            date.before(Date()) -> {
                _sendAtError.value = "Data wysyłania nie może być w przeszłości"
                false
            }
            else -> {
                _sendAtError.value = null
                true
            }
        }
    }
    
    /**
     * Sprawdza czy numer telefonu jest prawidłowy
     */
    private fun isValidPhoneNumber(number: String): Boolean {
        // Prosta walidacja numeru telefonu
        val regex = Regex("^[+]?[0-9]{9,15}$")
        return regex.matches(number.replace(Regex("[\\s\\-\\(\\)]"), ""))
    }
    
    /**
     * Waliduje cały formularz
     */
    private fun validateForm(): Boolean {
        val isPhoneValid = validatePhoneNumber()
        val isMessageValid = validateMessage()
        val isSendAtValid = validateSendAt()
        
        return isPhoneValid && isMessageValid && isSendAtValid
    }
    
    /**
     * Wysyła SMS
     */
    fun sendSms() {
        if (!validateForm()) {
            return
        }
        
        viewModelScope.launch {
            try {
                _isSending.value = true
                _error.value = null
                _successMessage.value = null
                _sendResult.value = null
                
                val request = com.smsgateway.app.api.models.SendSmsRequest(
                    phoneNumber = _phoneNumber.value.trim(),
                    message = _message.value.trim(),
                    priority = _priority.value,
                    sendAt = if (_sendLater.value) _sendAt.value else null
                )
                
                val response = smsRepository.sendSms(request)
                _sendResult.value = response
                
                _successMessage.value = "Wiadomość została dodana do kolejki"
                
                // Dodaj numer i wiadomość do historii
                addToRecent(_phoneNumber.value.trim(), _message.value.trim())
                
                // Wyczyść formularz
                clearForm()
                
            } catch (e: Exception) {
                _error.value = "Błąd wysyłania wiadomości: ${e.message}"
            } finally {
                _isSending.value = false
            }
        }
    }
    
    /**
     * Wysyła wiele SMSów
     */
    fun sendMultipleSms(phoneNumbers: List<String>, message: String, priority: SmsPriority) {
        viewModelScope.launch {
            try {
                _isSending.value = true
                _error.value = null
                _successMessage.value = null
                
                var successCount = 0
                var failureCount = 0
                
                phoneNumbers.forEach { phoneNumber ->
                    try {
                        val request = com.smsgateway.app.api.models.SendSmsRequest(
                            phoneNumber = phoneNumber.trim(),
                            message = message.trim(),
                            priority = priority
                        )
                        
                        smsRepository.sendSms(request)
                        successCount++
                        
                        // Dodaj numer do historii
                        addToRecent(phoneNumber.trim(), message.trim())
                        
                    } catch (e: Exception) {
                        failureCount++
                    }
                }
                
                if (failureCount == 0) {
                    _successMessage.value = "Wysłano $successCount wiadomości"
                } else {
                    _error.value = "Wysłano $successCount wiadomości, $failureCount nie udało się"
                }
                
            } catch (e: Exception) {
                _error.value = "Błąd wysyłania wiadomości: ${e.message}"
            } finally {
                _isSending.value = false
            }
        }
    }
    
    /**
     * Czyści formularz
     */
    fun clearForm() {
        _phoneNumber.value = ""
        _message.value = ""
        _priority.value = SmsPriority.MEDIUM
        _sendAt.value = null
        _sendLater.value = false
        
        _phoneNumberError.value = null
        _messageError.value = null
        _sendAtError.value = null
        
        _sendResult.value = null
    }
    
    /**
     * Ładuje ostatnio używane numery i wiadomości
     */
    private fun loadRecentData() {
        viewModelScope.launch {
            try {
                // Pobierz ostatnią historię SMS
                val history = smsRepository.getSmsHistory(1, 10)
                
                // Wyodrębnij unikalne numery telefonów
                val phoneNumbers = history.data
                    .map { it.phoneNumber }
                    .distinct()
                    .take(5)
                
                _recentPhoneNumbers.value = phoneNumbers
                
                // Wyodrębnij unikalne wiadomości
                val messages = history.data
                    .map { it.message }
                    .distinct()
                    .take(5)
                
                _recentMessages.value = messages
                
            } catch (e: Exception) {
                // Ignoruj błąd, ostatnie dane są opcjonalne
            }
        }
    }
    
    /**
     * Dodaje numer i wiadomość do historii
     */
    private fun addToRecent(phoneNumber: String, message: String) {
        // Dodaj numer telefonu do listy
        val currentPhones = _recentPhoneNumbers.value.toMutableList()
        if (!currentPhones.contains(phoneNumber)) {
            currentPhones.add(0, phoneNumber)
            if (currentPhones.size > 5) {
                currentPhones.removeAt(5)
            }
            _recentPhoneNumbers.value = currentPhones
        }
        
        // Dodaj wiadomość do listy
        val currentMessages = _recentMessages.value.toMutableList()
        if (!currentMessages.contains(message)) {
            currentMessages.add(0, message)
            if (currentMessages.size > 5) {
                currentMessages.removeAt(5)
            }
            _recentMessages.value = currentMessages
        }
    }
    
    /**
     * Formatuje datę do wyświetlenia
     */
    fun formatDate(date: Date): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return sdf.format(date)
    }
    
    /**
     * Pobiera opis priorytetu
     */
    fun getPriorityDescription(priority: SmsPriority): String {
        return when (priority) {
            SmsPriority.HIGH -> "Wysoki"
            SmsPriority.MEDIUM -> "Średni"
            SmsPriority.LOW -> "Niski"
        }
    }
    
    /**
     * Pobiera kolor priorytetu
     */
    fun getPriorityColor(priority: SmsPriority): String {
        return when (priority) {
            SmsPriority.HIGH -> "#F44336" // Czerwony
            SmsPriority.MEDIUM -> "#FF9800" // Pomarańczowy
            SmsPriority.LOW -> "#4CAF50" // Zielony
        }
    }
    
    /**
     * Pobiera liczbę znaków wiadomości
     */
    fun getCharacterCount(): Int {
        return _message.value.length
    }
    
    /**
     * Pobiera liczbę SMSów (każde 160 znaków to jeden SMS)
     */
    fun getSmsCount(): Int {
        val length = _message.value.length
        return if (length == 0) 0 else (length + 159) / 160
    }
    
    /**
     * Sprawdza czy formularz jest prawidłowo wypełniony
     */
    fun isFormValid(): Boolean {
        return validateForm()
    }
    
    /**
     * Czyści komunikat o błędzie
     */
    fun clearError() {
        _error.value = null
    }
    
    /**
     * Czyści komunikat o sukcesie
     */
    fun clearSuccessMessage() {
        _successMessage.value = null
    }
    
    /**
     * Ustawia datę wysyłania na teraz + określoną liczbę minut
     */
    fun setSendAtInMinutes(minutes: Int) {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MINUTE, minutes)
        _sendAt.value = calendar.time
        validateSendAt()
    }
    
    /**
     * Ustawia datę wysyłania na określoną godzinę
     */
    fun setSendAtAtHour(hour: Int, minute: Int = 0) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        
        // Jeśli ustawiona godzina jest w przeszłości, ustaw na następny dzień
        if (calendar.time.before(Date())) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        
        _sendAt.value = calendar.time
        validateSendAt()
    }
    
    /**
     * Pobiera sugerowane daty wysyłania
     */
    fun getSuggestedSendAtDates(): List<Pair<String, Date>> {
        val suggestions = mutableListOf<Pair<String, Date>>()
        val calendar = Calendar.getInstance()
        
        // Teraz + 15 minut
        calendar.time = Date()
        calendar.add(Calendar.MINUTE, 15)
        suggestions.add(Pair("Za 15 minut", calendar.time))
        
        // Teraz + 1 godzinę
        calendar.time = Date()
        calendar.add(Calendar.HOUR, 1)
        suggestions.add(Pair("Za 1 godzinę", calendar.time))
        
        // Dziś 18:00
        calendar.time = Date()
        calendar.set(Calendar.HOUR_OF_DAY, 18)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        if (calendar.time.after(Date())) {
            suggestions.add(Pair("Dziś 18:00", calendar.time))
        }
        
        // Jutro 09:00
        calendar.time = Date()
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 9)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        suggestions.add(Pair("Jutro 09:00", calendar.time))
        
        return suggestions
    }
}