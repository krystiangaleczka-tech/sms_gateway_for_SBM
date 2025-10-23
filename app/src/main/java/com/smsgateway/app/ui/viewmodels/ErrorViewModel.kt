package com.smsgateway.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smsgateway.app.monitoring.ErrorHandler
import com.smsgateway.app.monitoring.repositories.ErrorRepository
import com.smsgateway.app.monitoring.models.AppError
import com.smsgateway.app.monitoring.models.ErrorType
import com.smsgateway.app.monitoring.models.ErrorSeverity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*

class ErrorViewModel(
    application: Application,
    private val errorRepository: ErrorRepository,
    private val errorHandler: ErrorHandler
) : AndroidViewModel(application) {
    
    private val _errors = MutableStateFlow<List<AppError>>(emptyList())
    val errors: StateFlow<List<AppError>> = _errors.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorDetails = MutableStateFlow<AppError?>(null)
    val errorDetails: StateFlow<AppError?> = _errorDetails.asStateFlow()
    
    private val _showErrorReportDialog = MutableStateFlow(false)
    val showErrorReportDialog: StateFlow<Boolean> = _showErrorReportDialog.asStateFlow()
    
    init {
        loadErrors()
    }
    
    fun loadErrors() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                errorRepository.getAllErrors()
                    .collect { errorList ->
                        _errors.value = errorList.sortedByDescending { it.timestamp }
                    }
            } catch (e: Exception) {
                errorHandler.handleError(e, ErrorType.UI_ERROR, ErrorSeverity.MEDIUM)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun getErrorById(errorId: String) {
        viewModelScope.launch {
            try {
                _errorDetails.value = errorRepository.getErrorById(errorId)
            } catch (e: Exception) {
                errorHandler.handleError(e, ErrorType.UI_ERROR, ErrorSeverity.MEDIUM)
            }
        }
    }
    
    fun getErrorsByType(type: ErrorType) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                errorRepository.getErrorsByType(type)
                    .collect { errorList ->
                        _errors.value = errorList.sortedByDescending { it.timestamp }
                    }
            } catch (e: Exception) {
                errorHandler.handleError(e, ErrorType.UI_ERROR, ErrorSeverity.MEDIUM)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun getErrorsBySeverity(severity: ErrorSeverity) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                errorRepository.getErrorsBySeverity(severity)
                    .collect { errorList ->
                        _errors.value = errorList.sortedByDescending { it.timestamp }
                    }
            } catch (e: Exception) {
                errorHandler.handleError(e, ErrorType.UI_ERROR, ErrorSeverity.MEDIUM)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun reportError(errorId: String, userFeedback: String) {
        viewModelScope.launch {
            try {
                errorHandler.reportUserFeedback(errorId, userFeedback)
                _showErrorReportDialog.value = false
            } catch (e: Exception) {
                errorHandler.handleError(e, ErrorType.UI_ERROR, ErrorSeverity.MEDIUM)
            }
        }
    }
    
    fun deleteError(errorId: String) {
        viewModelScope.launch {
            try {
                errorRepository.deleteError(errorId)
                // Odśwież listę błędów
                loadErrors()
            } catch (e: Exception) {
                errorHandler.handleError(e, ErrorType.UI_ERROR, ErrorSeverity.MEDIUM)
            }
        }
    }
    
    fun clearAllErrors() {
        viewModelScope.launch {
            try {
                errorRepository.clearAllErrors()
                loadErrors()
            } catch (e: Exception) {
                errorHandler.handleError(e, ErrorType.UI_ERROR, ErrorSeverity.MEDIUM)
            }
        }
    }
    
    fun showErrorReportDialog() {
        _showErrorReportDialog.value = true
    }
    
    fun hideErrorReportDialog() {
        _showErrorReportDialog.value = false
    }
    
    fun refreshErrors() {
        loadErrors()
    }
}