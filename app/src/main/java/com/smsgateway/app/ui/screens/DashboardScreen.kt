package com.smsgateway.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smsgateway.app.ui.viewmodels.DashboardViewModel
import com.smsgateway.app.ui.theme.SMSGatewayTheme
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    dashboardViewModel: DashboardViewModel = viewModel()
) {
    // Obserwowanie stanów z ViewModel
    val queueStats by dashboardViewModel.queueStats.collectAsState()
    val systemHealth by dashboardViewModel.systemHealth.collectAsState()
    val recentMessages by dashboardViewModel.recentMessages.collectAsState()
    val isLoading by dashboardViewModel.isLoading.collectAsState()
    val isRefreshing by dashboardViewModel.isRefreshing.collectAsState()
    val error by dashboardViewModel.error.collectAsState()
    val successMessage by dashboardViewModel.successMessage.collectAsState()
    
    // Inicjalizacja danych przy pierwszym renderowaniu
    LaunchedEffect(Unit) {
        dashboardViewModel.refreshData()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        // Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Dashboard",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            // Przycisk odświeżania
            IconButton(
                onClick = { dashboardViewModel.refreshData() },
                enabled = !isRefreshing
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Odśwież dane"
                    )
                }
            }
        }
        
        // Komunikat błędu
        error?.let { errorMessage ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF7ED)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFEA580C),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = errorMessage,
                        color = Color(0xFF92400E),
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { dashboardViewModel.clearError() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Zamknij",
                            tint = Color(0xFF92400E),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        
        // Komunikat sukcesu
        successMessage?.let { message ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF0FDF4)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF22C55E),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = message,
                        color = Color(0xFF166534),
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { dashboardViewModel.clearSuccessMessage() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Zamknij",
                            tint = Color(0xFF166534),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Ładowanie danych...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Stats Grid
            StatsGrid(
                queueStats = queueStats,
                systemHealth = systemHealth,
                onRetryFailedMessages = { dashboardViewModel.retryFailedMessages() },
                onResumeQueue = { dashboardViewModel.resumeQueue() },
                onPauseQueue = { dashboardViewModel.pauseQueue() },
                onClearQueue = { dashboardViewModel.clearQueue() }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Recent Messages
            RecentMessagesCard(
                messages = recentMessages,
                onViewAllMessages = { /* TODO: Navigate to history */ },
                onRetryMessage = { messageId -> dashboardViewModel.retryMessage(messageId) },
                onCancelMessage = { messageId -> dashboardViewModel.cancelMessage(messageId) },
                onRetryFailedMessages = { dashboardViewModel.retryFailedMessages() }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Quick Actions
            QuickActionsCard(
                onSendTestSms = { phoneNumber, message ->
                    dashboardViewModel.sendTestSms(phoneNumber, message)
                },
                onViewHistory = { /* TODO: Navigate to history */ },
                onExportData = { dashboardViewModel.exportData() },
                onOpenSettings = { /* TODO: Navigate to settings */ }
            )
        }
    }
}

@Composable
fun StatsGrid(
    queueStats: com.smsgateway.app.api.models.QueueStatsApi?,
    systemHealth: com.smsgateway.app.api.models.SystemHealthApi?,
    onRetryFailedMessages: () -> Unit,
    onResumeQueue: () -> Unit,
    onPauseQueue: () -> Unit,
    onClearQueue: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Mail,
                    value = queueStats?.pending?.toString() ?: "0",
                    label = "SMS w kolejce",
                    iconColor = MaterialTheme.colorScheme.outline
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.CheckCircle,
                    value = queueStats?.sentToday?.toString() ?: "0",
                    label = "Wysłane dzisiaj",
                    subLabel = "Sukces: ${queueStats?.successRate?.let { "${(it * 100).toInt()}%" } ?: "N/A"}",
                    iconColor = Color(0xFF22C55E)
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Warning,
                    value = queueStats?.failed?.toString() ?: "0",
                    label = "Błędy",
                    subLabel = if ((queueStats?.failed ?: 0) > 0) "Zobacz szczegóły" else null,
                    iconColor = Color(0xFFEAB308),
                    showLink = (queueStats?.failed ?: 0) > 0,
                    onClick = if ((queueStats?.failed ?: 0) > 0) onRetryFailedMessages else null
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Storage,
                    value = systemHealth?.status?.let { if (it == "UP") "Aktywny" else it } ?: "N/A",
                    label = "Status systemu",
                    subLabel = systemHealth?.uptime?.let { dashboardViewModel.formatUptime(it) },
                    iconColor = if (systemHealth?.status == "UP") Color(0xFF22C55E) else Color(0xFFEAB308),
                    isStatus = true
                )
            }
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    subLabel: String? = null,
    iconColor: Color,
    showLink: Boolean = false,
    isStatus: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.let { 
            if (onClick != null) it.clickable { onClick() } else it 
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = value,
                fontSize = if (isStatus) 28.sp else 36.sp,
                fontWeight = FontWeight.Bold,
                color = if (isStatus) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = label,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            
            subLabel?.let {
                Spacer(modifier = Modifier.height(4.dp))
                if (showLink) {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        color = if (isStatus) MaterialTheme.colorScheme.outline else Color(0xFF22C55E),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun RecentMessagesCard(
    messages: List<com.smsgateway.app.api.models.SmsMessageApi>,
    onViewAllMessages: () -> Unit,
    onRetryMessage: (String) -> Unit,
    onCancelMessage: (String) -> Unit,
    onRetryFailedMessages: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp, 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Ostatnie wiadomości",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Najnowsze SMS z systemu",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onViewAllMessages
                ) {
                    Text(
                        text = "Zobacz wszystkie →",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            // Table
            Column {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp, 24.dp)
                ) {
                    Text(
                        text = "ID",
                        modifier = Modifier.weight(1f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Numer",
                        modifier = Modifier.weight(2f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Wiadomość",
                        modifier = Modifier.weight(3f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Status",
                        modifier = Modifier.weight(1.5f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Czas",
                        modifier = Modifier.weight(1.5f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Data
                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Brak wiadomości do wyświetlenia",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    messages.take(5).forEach { message ->
                        MessageRow(
                            message = message,
                            onRetry = { onRetryMessage(message.id) },
                            onCancel = { onCancelMessage(message.id) }
                        )
                    }
                }
                
                // Retry failed button
                if (messages.any { it.status == com.smsgateway.app.api.models.SmsStatus.FAILED }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = onRetryFailedMessages,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Ponów wysyłanie nieudanych wiadomości",
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageRow(
    message: com.smsgateway.app.api.models.SmsMessageApi,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp, 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#${message.id.takeLast(4)}",
            modifier = Modifier.weight(1f),
            fontSize = 14.sp
        )
        Text(
            text = message.phoneNumber.replaceBefore(length = 3, replacement = "***"),
            modifier = Modifier.weight(2f),
            fontSize = 14.sp
        )
        Text(
            text = message.message,
            modifier = Modifier.weight(3f),
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier.weight(1.5f),
            contentAlignment = Alignment.CenterStart
        ) {
            StatusBadge(
                status = message.status,
                onRetry = if (message.status == com.smsgateway.app.api.models.SmsStatus.FAILED) onRetry else null,
                onCancel = if (message.status == com.smsgateway.app.api.models.SmsStatus.PENDING) onCancel else null
            )
        }
        Text(
            text = dashboardViewModel.formatRelativeTime(message.createdAt),
            modifier = Modifier.weight(1.5f),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StatusBadge(
    status: com.smsgateway.app.api.models.SmsStatus,
    onRetry: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null
) {
    val (backgroundColor, textColor, icon) = when (status) {
        com.smsgateway.app.api.models.SmsStatus.SENT -> 
            Triple(Color(0xFFDCFCE7), Color(0xFF166534), Icons.Default.CheckCircle)
        com.smsgateway.app.api.models.SmsStatus.PENDING -> 
            Triple(Color(0xFFFEF9C3), Color(0xFF854D0E), Icons.Default.Schedule)
        com.smsgateway.app.api.models.SmsStatus.FAILED -> 
            Triple(Color(0xFFFEE2E2), Color(0xFF991B1B), Icons.Default.Error)
        com.smsgateway.app.api.models.SmsStatus.CANCELLED -> 
            Triple(Color(0xFFF3F4F6), Color(0xFF6B7280), Icons.Default.Cancel)
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(backgroundColor)
                .padding(4.dp, 12.dp)
        ) {
            Text(
                text = status.displayName,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
        
        // Akcje dla statusów FAILED i PENDING
        if (onRetry != null || onCancel != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Row {
                if (onRetry != null) {
                    IconButton(
                        onClick = onRetry,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Ponów",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                if (onCancel != null) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Anuluj",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionsCard(
    onSendTestSms: (String, String) -> Unit,
    onViewHistory: () -> Unit,
    onExportData: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            // Header
            Column {
                Text(
                    text = "Szybkie akcje",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Najczęściej używane funkcje",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Actions Grid
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.AutoMirrored.Filled.Send,
                        text = "Wyślij testowy SMS",
                        isPrimary = true,
                        onClick = { 
                            // TODO: Show dialog for test SMS
                            onSendTestSms("+48123456789", "Test SMS from SMS Gateway")
                        }
                    )
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.BarChart,
                        text = "Zobacz historię",
                        onClick = onViewHistory
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Download,
                        text = "Eksportuj dane",
                        onClick = onExportData
                    )
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Settings,
                        text = "Ustawienia",
                        onClick = onOpenSettings
                    )
                }
            }
        }
    }
}

@Composable
fun ActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    text: String,
    isPrimary: Boolean = false,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            contentColor = if (isPrimary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        ),
        border = if (!isPrimary) ButtonDefaults.outlinedButtonBorder else null,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// Funkcje rozszerzające dla formatowania
private fun com.smsgateway.app.api.models.SmsStatus.displayName(): String {
    return when (this) {
        com.smsgateway.app.api.models.SmsStatus.PENDING -> "W kolejce"
        com.smsgateway.app.api.models.SmsStatus.SENT -> "Wysłane"
        com.smsgateway.app.api.models.SmsStatus.FAILED -> "Błąd"
        com.smsgateway.app.api.models.SmsStatus.CANCELLED -> "Anulowano"
    }
}

// Funkcje pomocnicze (w rzeczywistej implementacji powinny być w ViewModel)
private fun dashboardViewModel.formatUptime(seconds: Long): String {
    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val minutes = (seconds % 3600) / 60
    
    return when {
        days > 0 -> "${days}d ${hours}h ${minutes}m"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

private fun dashboardViewModel.formatRelativeTime(dateString: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        val date = sdf.parse(dateString)
        val now = Date()
        val diff = now.time - (date?.time ?: 0)
        
        when {
            diff < 60000 -> "${diff / 1000} sek. temu"
            diff < 3600000 -> "${diff / 60000} min. temu"
            diff < 86400000 -> "${diff / 3600000} godz. temu"
            else -> "${diff / 86400000} dni temu"
        }
    } catch (e: Exception) {
        "Nieznany czas"
    }
}