package com.smsgateway.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.smsgateway.app.monitoring.models.AppError
import com.smsgateway.app.monitoring.models.ErrorSeverity
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ErrorDetailsDialog(
    error: AppError,
    onDismiss: () -> Unit,
    onReport: (String) -> Unit,
    onDelete: () -> Unit
) {
    var showReportDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(ErrorDetailsTab.OVERVIEW) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
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
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = error.type.name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ID: ${error.id.take(8)}...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Zamknij"
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Tabs
                TabRow(selectedTabIndex = selectedTab.ordinal) {
                    ErrorDetailsTab.values().forEach { tab ->
                        Tab(
                            text = { Text(tab.label) },
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Content
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (selectedTab) {
                        ErrorDetailsTab.OVERVIEW -> OverviewTab(error = error)
                        ErrorDetailsTab.STACK_TRACE -> StackTraceTab(error = error)
                        ErrorDetailsTab.METADATA -> MetadataTab(error = error)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Usuń")
                    }
                    
                    Button(
                        onClick = { showReportDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Zgłoś")
                    }
                }
            }
        }
    }
    
    // Report Dialog
    if (showReportDialog) {
        ErrorReportDialog(
            error = error,
            onDismiss = { showReportDialog = false },
            onReport = { feedback ->
                onReport(feedback)
                showReportDialog = false
            }
        )
    }
    
    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Potwierdzenie usunięcia") },
            text = { Text("Czy na pewno chcesz usunąć ten błąd? Tej operacji nie można cofnąć.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirmDialog = false
                    }
                ) {
                    Text("Usuń", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Anuluj")
                }
            }
        )
    }
}

@Composable
fun OverviewTab(error: AppError) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Severity Badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(getSeverityColor(error.severity).copy(alpha = 0.1f))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getSeverityIcon(error.severity),
                contentDescription = null,
                tint = getSeverityColor(error.severity),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Priorytet: ${getSeverityLabel(error.severity)}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = getSeverityColor(error.severity)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Error Message
        Text(
            text = "Wiadomość błędu",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error.message,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Timestamp
        Text(
            text = "Czas wystąpienia",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = formatFullTimestamp(error.timestamp),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Error Type
        Text(
            text = "Typ błędu",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error.type.name,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Device Info
        Text(
            text = "Informacje o urządzeniu",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        error.deviceInfo?.let { deviceInfo ->
            Text(
                text = "Model: ${deviceInfo.model}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Android: ${deviceInfo.androidVersion}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Wersja aplikacji: ${deviceInfo.appVersion}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StackTraceTab(error: AppError) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Stack Trace",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        ) {
            if (error.stackTrace.isNotBlank()) {
                Text(
                    text = error.stackTrace,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Brak dostępnych informacji o stack trace",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MetadataTab(error: AppError) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Metadane",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        if (error.metadata.isNotEmpty()) {
            error.metadata.forEach { (key, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "$key:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(120.dp)
                    )
                    Text(
                        text = value.toString(),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        } else {
            Text(
                text = "Brak dostępnych metadanych",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ErrorReportDialog(
    error: AppError,
    onDismiss: () -> Unit,
    onReport: (String) -> Unit
) {
    var feedback by remember { mutableStateOf("") }
    var includeDeviceInfo by remember { mutableStateOf(true) }
    var includeStackTrace by remember { mutableStateOf(true) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "Zgłoś błąd",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Opisz co robiłeś/aś gdy wystąpił błąd:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = feedback,
                    onValueChange = { feedback = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    placeholder = { Text("Opisz problem...") }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Options
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeDeviceInfo,
                        onCheckedChange = { includeDeviceInfo = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Dołącz informacje o urządzeniu",
                        fontSize = 14.sp
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeStackTrace,
                        onCheckedChange = { includeStackTrace = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Dołącz stack trace",
                        fontSize = 14.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Anuluj")
                    }
                    
                    Button(
                        onClick = {
                            val reportData = buildReportData(
                                error = error,
                                feedback = feedback,
                                includeDeviceInfo = includeDeviceInfo,
                                includeStackTrace = includeStackTrace
                            )
                            onReport(reportData)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = feedback.isNotBlank()
                    ) {
                        Text("Wyślij")
                    }
                }
            }
        }
    }
}

private fun buildReportData(
    error: AppError,
    feedback: String,
    includeDeviceInfo: Boolean,
    includeStackTrace: Boolean
): String {
    val report = StringBuilder()
    report.appendLine("Opis użytkownika:")
    report.appendLine(feedback)
    report.appendLine()
    
    report.appendLine("Informacje o błędzie:")
    report.appendLine("ID: ${error.id}")
    report.appendLine("Typ: ${error.type}")
    report.appendLine("Priorytet: ${error.severity}")
    report.appendLine("Wiadomość: ${error.message}")
    report.appendLine("Czas: ${formatFullTimestamp(error.timestamp)}")
    report.appendLine()
    
    if (includeDeviceInfo && error.deviceInfo != null) {
        report.appendLine("Informacje o urządzeniu:")
        report.appendLine("Model: ${error.deviceInfo!!.model}")
        report.appendLine("Android: ${error.deviceInfo!!.androidVersion}")
        report.appendLine("Wersja aplikacji: ${error.deviceInfo!!.appVersion}")
        report.appendLine()
    }
    
    if (includeStackTrace && error.stackTrace.isNotBlank()) {
        report.appendLine("Stack Trace:")
        report.appendLine(error.stackTrace)
    }
    
    return report.toString()
}

enum class ErrorDetailsTab(val label: String) {
    OVERVIEW("Przegląd"),
    STACK_TRACE("Stack Trace"),
    METADATA("Metadane")
}

private fun getSeverityIcon(severity: ErrorSeverity): ImageVector {
    return when (severity) {
        ErrorSeverity.HIGH -> Icons.Default.Error
        ErrorSeverity.MEDIUM -> Icons.Default.Warning
        ErrorSeverity.LOW -> Icons.Default.Info
    }
}

private fun getSeverityLabel(severity: ErrorSeverity): String {
    return when (severity) {
        ErrorSeverity.HIGH -> "Krytyczny"
        ErrorSeverity.MEDIUM -> "Średni"
        ErrorSeverity.LOW -> "Niski"
    }
}

private fun formatFullTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}