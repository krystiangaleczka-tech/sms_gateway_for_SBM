package com.smsgateway.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smsgateway.app.ui.viewmodels.HistoryViewModel
import com.smsgateway.app.ui.theme.SMSGatewayTheme
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    historyViewModel: HistoryViewModel = viewModel()
) {
    // Obserwowanie stanów z ViewModel
    val messages by historyViewModel.messages.collectAsState()
    val isLoading by historyViewModel.isLoading.collectAsState()
    val isRefreshing by historyViewModel.isRefreshing.collectAsState()
    val error by historyViewModel.error.collectAsState()
    val successMessage by historyViewModel.successMessage.collectAsState()
    val currentPage by historyViewModel.currentPage.collectAsState()
    val totalPages by historyViewModel.totalPages.collectAsState()
    val totalCount by historyViewModel.totalCount.collectAsState()
    val searchQuery by historyViewModel.searchQuery.collectAsState()
    val selectedStatus by historyViewModel.selectedStatus.collectAsState()
    val selectedDateRange by historyViewModel.selectedDateRange.collectAsState()
    
    // Stan dla dialogu filtrów
    var showFiltersDialog by remember { mutableStateOf(false) }
    
    // Stan dla dialogu szczegółów wiadomości
    var selectedMessage by remember { mutableStateOf<com.smsgateway.app.api.models.SmsMessageApi?>(null) }
    
    // Inicjalizacja danych przy pierwszym renderowaniu
    LaunchedEffect(Unit) {
        historyViewModel.loadMessages()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Historia SMS",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            Row {
                // Przycisk odświeżania
                IconButton(
                    onClick = { historyViewModel.refreshMessages() },
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
                            contentDescription = "Odśwież"
                        )
                    }
                }
                
                // Przycisk filtrów
                IconButton(
                    onClick = { showFiltersDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Filtry"
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
                        onClick = { historyViewModel.clearError() },
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
                        onClick = { historyViewModel.clearSuccessMessage() },
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
        
        // Pasek wyszukiwania i filtrów
        SearchAndFilterBar(
            searchQuery = searchQuery,
            onSearchQueryChange = { historyViewModel.setSearchQuery(it) },
            selectedStatus = selectedStatus,
            selectedDateRange = selectedDateRange,
            onShowFilters = { showFiltersDialog = true },
            onClearFilters = { historyViewModel.clearFilters() }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Licznik wyników
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Znaleziono ${totalCount} wiadomości",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (totalPages > 1) {
                Text(
                    text = "Strona ${currentPage + 1} z $totalPages",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Lista wiadomości
        if (isLoading && messages.isEmpty()) {
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
                        text = "Ładowanie wiadomości...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.MailOutline,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Brak wiadomości do wyświetlenia",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Spróbuj zmienić filtry lub odświeżyć dane",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val listState = rememberLazyListState()
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    MessageCard(
                        message = message,
                        onClick = { selectedMessage = message },
                        onRetry = { historyViewModel.retryMessage(message.id) },
                        onCancel = { historyViewModel.cancelMessage(message.id) },
                        onViewDetails = { selectedMessage = message }
                    )
                }
                
                // Przycisk ładowania więcej
                if (currentPage < totalPages - 1) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Button(
                                onClick = { historyViewModel.loadNextPage() },
                                enabled = !isRefreshing
                            ) {
                                if (isRefreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text(
                                        text = "Załaduj więcej",
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Przyciski paginacji
        if (totalPages > 1) {
            Spacer(modifier = Modifier.height(16.dp))
            PaginationControls(
                currentPage = currentPage,
                totalPages = totalPages,
                onPageChange = { historyViewModel.loadPage(it) },
                isLoading = isRefreshing
            )
        }
    }
    
    // Dialog filtrów
    if (showFiltersDialog) {
        FiltersDialog(
            searchQuery = searchQuery,
            onSearchQueryChange = { historyViewModel.setSearchQuery(it) },
            selectedStatus = selectedStatus,
            onStatusChange = { historyViewModel.setSelectedStatus(it) },
            selectedDateRange = selectedDateRange,
            onDateRangeChange = { historyViewModel.setSelectedDateRange(it) },
            onDismiss = { showFiltersDialog = false },
            onApply = {
                historyViewModel.applyFilters()
                showFiltersDialog = false
            },
            onClear = {
                historyViewModel.clearFilters()
                showFiltersDialog = false
            }
        )
    }
    
    // Dialog szczegółów wiadomości
    selectedMessage?.let { message ->
        MessageDetailsDialog(
            message = message,
            onDismiss = { selectedMessage = null },
            onRetry = { 
                historyViewModel.retryMessage(message.id)
                selectedMessage = null
            },
            onCancel = { 
                historyViewModel.cancelMessage(message.id)
                selectedMessage = null
            }
        )
    }
}

@Composable
fun SearchAndFilterBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedStatus: com.smsgateway.app.api.models.SmsStatus?,
    selectedDateRange: String?,
    onShowFilters: () -> Unit,
    onClearFilters: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Pole wyszukiwania
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Szukaj wiadomości...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                singleLine = true
            )
            
            // Aktywne filtry
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedStatus != null || selectedDateRange != null) {
                    Chip(
                        onClick = onClearFilters,
                        colors = ChipDefaults.chipColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            labelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Wyczyść filtry",
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    IconButton(
                        onClick = onShowFilters
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filtry"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageCard(
    message: com.smsgateway.app.api.models.SmsMessageApi,
    onClick: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onViewDetails: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Nagłówek z ID, numerem i statusem
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "#${message.id.takeLast(4)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message.phoneNumber.replaceBefore(length = 3, replacement = "***"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                StatusBadge(status = message.status)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Treść wiadomości
            Text(
                text = message.message,
                fontSize = 14.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Data i akcje
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = formatDateTime(message.createdAt),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (message.scheduledAt != null) {
                        Text(
                            text = "Zaplanowano: ${formatDateTime(message.scheduledAt!!)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Row {
                    when (message.status) {
                        com.smsgateway.app.api.models.SmsStatus.FAILED -> {
                            IconButton(
                                onClick = onRetry,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Ponów",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        com.smsgateway.app.api.models.SmsStatus.PENDING -> {
                            IconButton(
                                onClick = onCancel,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = "Anuluj",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        else -> {}
                    }
                    
                    IconButton(
                        onClick = onViewDetails,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Szczegóły",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: com.smsgateway.app.api.models.SmsStatus) {
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
                text = status.displayName(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

@Composable
fun PaginationControls(
    currentPage: Int,
    totalPages: Int,
    onPageChange: (Int) -> Unit,
    isLoading: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { onPageChange(currentPage - 1) },
            enabled = currentPage > 0 && !isLoading
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowLeft,
                contentDescription = "Poprzednia strona"
            )
        }
        
        // Numery stron
        val visiblePages = generateVisiblePages(currentPage, totalPages)
        visiblePages.forEach { page ->
            TextButton(
                onClick = { onPageChange(page) },
                enabled = page != currentPage && !isLoading
            ) {
                Text(
                    text = "${page + 1}",
                    color = if (page == currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (page == currentPage) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
        
        IconButton(
            onClick = { onPageChange(currentPage + 1) },
            enabled = currentPage < totalPages - 1 && !isLoading
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "Następna strona"
            )
        }
    }
}

@Composable
fun FiltersDialog(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedStatus: com.smsgateway.app.api.models.SmsStatus?,
    onStatusChange: (com.smsgateway.app.api.models.SmsStatus?) -> Unit,
    selectedDateRange: String?,
    onDateRangeChange: (String?) -> Unit,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit
) {
    var localSearchQuery by remember { mutableStateOf(searchQuery) }
    var localSelectedStatus by remember { mutableStateOf(selectedStatus) }
    var localSelectedDateRange by remember { mutableStateOf(selectedDateRange) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filtry wyszukiwania") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Pole wyszukiwania
                OutlinedTextField(
                    value = localSearchQuery,
                    onValueChange = { localSearchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Szukaj w treści wiadomości...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null
                        )
                    }
                )
                
                // Status
                Text(
                    text = "Status",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                
                val statuses = listOf(
                    null to "Wszystkie",
                    com.smsgateway.app.api.models.SmsStatus.SENT to "Wysłane",
                    com.smsgateway.app.api.models.SmsStatus.PENDING to "W kolejce",
                    com.smsgateway.app.api.models.SmsStatus.FAILED to "Błędy",
                    com.smsgateway.app.api.models.SmsStatus.CANCELLED to "Anulowane"
                )
                
                statuses.forEach { (status, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { localSelectedStatus = status }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = localSelectedStatus == status,
                            onClick = { localSelectedStatus = status }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label)
                    }
                }
                
                // Zakres dat
                Text(
                    text = "Zakres dat",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                
                val dateRanges = listOf(
                    null to "Wszystkie",
                    "today" to "Dzisiaj",
                    "yesterday" to "Wczoraj",
                    "week" to "Ostatni tydzień",
                    "month" to "Ostatni miesiąc"
                )
                
                dateRanges.forEach { (range, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { localSelectedDateRange = range }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = localSelectedDateRange == range,
                            onClick = { localSelectedDateRange = range }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label)
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Wyczyść")
                }
                
                Button(
                    onClick = {
                        onSearchQueryChange(localSearchQuery)
                        onStatusChange(localSelectedStatus)
                        onDateRangeChange(localSelectedDateRange)
                        onApply()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Zastosuj")
                }
            }
        }
    )
}

@Composable
fun MessageDetailsDialog(
    message: com.smsgateway.app.api.models.SmsMessageApi,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                text = "Szczegóły wiadomości #${message.id}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Informacje podstawowe
                DetailRow("ID wiadomości", "#${message.id}")
                DetailRow("Numer telefonu", message.phoneNumber)
                DetailRow("Status", message.status.displayName())
                
                // Czasy
                DetailRow("Utworzono", formatDateTime(message.createdAt))
                message.scheduledAt?.let { 
                    DetailRow("Zaplanowano", formatDateTime(it))
                }
                message.sentAt?.let { 
                    DetailRow("Wysłano", formatDateTime(it))
                }
                
                // Treść wiadomości
                Text(
                    text = "Treść wiadomości",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = message.message,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 14.sp
                    )
                }
                
                // Metadane
                if (message.metadata.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Metadane",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            message.metadata.forEach { (key, value) ->
                                DetailRow(key, value.toString())
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (message.status) {
                    com.smsgateway.app.api.models.SmsStatus.FAILED -> {
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Ponów wysyłanie")
                        }
                    }
                    com.smsgateway.app.api.models.SmsStatus.PENDING -> {
                        Button(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Anuluj wysyłanie")
                        }
                    }
                    else -> {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Zamknij")
                        }
                    }
                }
                
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Zamknij")
                }
            }
        }
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            modifier = Modifier.weight(2f)
        )
    }
}

// Funkcje pomocnicze
private fun com.smsgateway.app.api.models.SmsStatus.displayName(): String {
    return when (this) {
        com.smsgateway.app.api.models.SmsStatus.SENT -> "Wysłane"
        com.smsgateway.app.api.models.SmsStatus.PENDING -> "W kolejce"
        com.smsgateway.app.api.models.SmsStatus.FAILED -> "Błąd"
        com.smsgateway.app.api.models.SmsStatus.CANCELLED -> "Anulowane"
    }
}

private fun formatDateTime(dateString: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        val date = sdf.parse(dateString)
        val displayFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        displayFormat.format(date ?: Date())
    } catch (e: Exception) {
        "Nieznany format daty"
    }
}

private fun generateVisiblePages(currentPage: Int, totalPages: Int): List<Int> {
    val visiblePages = mutableListOf<Int>()
    
    // Zawsze pokazuj pierwszą stronę
    if (currentPage > 0) {
        visiblePages.add(0)
    }
    
    // Pokazuj strony wokół aktualnej
    for (i in maxOf(0, currentPage - 1)..minOf(totalPages - 1, currentPage + 1)) {
        if (i >= 0 && i < totalPages && !visiblePages.contains(i)) {
            visiblePages.add(i)
        }
    }
    
    // Zawsze pokazuj ostatnią stronę
    if (currentPage < totalPages - 1) {
        visiblePages.add(totalPages - 1)
    }
    
    return visiblePages.distinct().sorted()
}