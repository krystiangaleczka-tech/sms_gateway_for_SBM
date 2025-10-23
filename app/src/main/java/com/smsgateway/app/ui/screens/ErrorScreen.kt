package com.smsgateway.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smsgateway.app.monitoring.models.AppError
import com.smsgateway.app.monitoring.models.ErrorType
import com.smsgateway.app.monitoring.models.ErrorSeverity
import com.smsgateway.app.ui.theme.SMSGatewayTheme
import com.smsgateway.app.ui.viewmodels.ErrorViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ErrorScreen(
    errorViewModel: ErrorViewModel = viewModel()
) {
    var selectedFilter by remember { mutableStateOf(ErrorFilter.ALL) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        errorViewModel.refreshErrors()
    }
    
    val errors = errorViewModel.errors.collectAsState()
    val isLoading = errorViewModel.isLoading.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Zarządzanie błędami",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Monitoruj i zarządzaj błędami systemu",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (errors.value.isNotEmpty()) {
                IconButton(
                    onClick = { showClearAllDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.ClearAll,
                        contentDescription = "Wyczyść wszystkie",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Filtry
        ErrorFilterRow(
            selectedFilter = selectedFilter,
            onFilterSelected = { filter ->
                selectedFilter = filter
                when (filter) {
                    ErrorFilter.ALL -> errorViewModel.loadErrors()
                    ErrorFilter.NETWORK -> errorViewModel.getErrorsByType(ErrorType.NETWORK)
                    ErrorType.DATABASE -> errorViewModel.getErrorsByType(ErrorType.DATABASE)
                    ErrorType.SMS_ERROR -> errorViewModel.getErrorsByType(ErrorType.SMS_ERROR)
                    ErrorType.UI_ERROR -> errorViewModel.getErrorsByType(ErrorType.UI_ERROR)
                    ErrorType.API_ERROR -> errorViewModel.getErrorsByType(ErrorType.API_ERROR)
                    ErrorSeverity.HIGH -> errorViewModel.getErrorsBySeverity(ErrorSeverity.HIGH)
                    ErrorSeverity.MEDIUM -> errorViewModel.getErrorsBySeverity(ErrorSeverity.MEDIUM)
                    ErrorSeverity.LOW -> errorViewModel.getErrorsBySeverity(ErrorSeverity.LOW)
                }
            }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Lista błędów
        when {
            isLoading.value -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            errors.value.isEmpty() -> {
                EmptyState()
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(errors.value) { error ->
                        ErrorItem(
                            error = error,
                            onErrorClick = { errorViewModel.getErrorById(error.id) },
                            onReportClick = { errorViewModel.showErrorReportDialog() },
                            onDeleteClick = { errorViewModel.deleteError(error.id) }
                        )
                    }
                }
            }
        }
    }
    
    // Dialog potwierdzenia usunięcia wszystkich błędów
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Potwierdzenie") },
            text = { Text("Czy na pewno chcesz usunąć wszystkie zapisane błędy?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        errorViewModel.clearAllErrors()
                        showClearAllDialog = false
                    }
                ) {
                    Text("Tak", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearAllDialog = false }
                ) {
                    Text("Anuluj")
                }
            }
        )
    }
}

@Composable
fun ErrorFilterRow(
    selectedFilter: Any,
    onFilterSelected: (Any) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            text = "Wszystkie",
            isSelected = selectedFilter == ErrorFilter.ALL,
            onClick = { onFilterSelected(ErrorFilter.ALL) }
        )
        
        FilterChip(
            text = "Sieć",
            isSelected = selectedFilter == ErrorType.NETWORK,
            onClick = { onFilterSelected(ErrorType.NETWORK) }
        )
        
        FilterChip(
            text = "Baza danych",
            isSelected = selectedFilter == ErrorType.DATABASE,
            onClick = { onFilterSelected(ErrorType.DATABASE) }
        )
        
        FilterChip(
            text = "SMS",
            isSelected = selectedFilter == ErrorType.SMS_ERROR,
            onClick = { onFilterSelected(ErrorType.SMS_ERROR) }
        )
        
        FilterChip(
            text = "UI",
            isSelected = selectedFilter == ErrorType.UI_ERROR,
            onClick = { onFilterSelected(ErrorType.UI_ERROR) }
        )
    }
    
    Spacer(modifier = Modifier.height(8.dp))
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            text = "API",
            isSelected = selectedFilter == ErrorType.API_ERROR,
            onClick = { onFilterSelected(ErrorType.API_ERROR) }
        )
        
        FilterChip(
            text = "Krytyczne",
            isSelected = selectedFilter == ErrorSeverity.HIGH,
            onClick = { onFilterSelected(ErrorSeverity.HIGH) }
        )
        
        FilterChip(
            text = "Średnie",
            isSelected = selectedFilter == ErrorSeverity.MEDIUM,
            onClick = { onFilterSelected(ErrorSeverity.MEDIUM) }
        )
        
        FilterChip(
            text = "Niskie",
            isSelected = selectedFilter == ErrorSeverity.LOW,
            onClick = { onFilterSelected(ErrorSeverity.LOW) }
        )
    }
}

@Composable
fun FilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary 
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary 
                   else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Brak błędów",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "System działa prawidłowo.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ErrorItem(
    error: AppError,
    onErrorClick: () -> Unit,
    onReportClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onErrorClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header z typem błędu i priorytetem
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = getErrorIcon(error.type),
                        contentDescription = null,
                        tint = getSeverityColor(error.severity),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = error.type.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = getSeverityColor(error.severity)
                    )
                }
                
                Text(
                    text = formatTimestamp(error.timestamp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Treść błędu
            Text(
                text = error.message,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            if (error.stackTrace.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = error.stackTrace.lines().first(),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Akcje
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onReportClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Zgłoś",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Usuń",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

fun getErrorIcon(errorType: ErrorType): ImageVector {
    return when (errorType) {
        ErrorType.NETWORK -> Icons.Default.NetworkCheck
        ErrorType.DATABASE -> Icons.Default.Storage
        ErrorType.SMS_ERROR -> Icons.Default.Textsms
        ErrorType.UI_ERROR -> Icons.Default.BugReport
        ErrorType.API_ERROR -> Icons.Default.Api
    }
}

fun getSeverityColor(severity: ErrorSeverity): Color {
    return when (severity) {
        ErrorSeverity.HIGH -> Color(0xFFD32F2F) // Czerwony
        ErrorSeverity.MEDIUM -> Color(0xFFF57C00) // Pomarańczowy
        ErrorSeverity.LOW -> Color(0xFF388E3C) // Zielony
    }
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

enum class ErrorFilter {
    ALL
}