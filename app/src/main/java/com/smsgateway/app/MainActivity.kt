package com.smsgateway.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smsgateway.app.ui.theme.SMSGatewayTheme

class MainActivity : ComponentActivity() {
    
    private lateinit var ktorServer: KtorServer
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Uruchom Ktor Server
        ktorServer = KtorServer(this)
        ktorServer.start()
        
        setContent {
            SMSGatewayTheme {
                SMSGatewayApp()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        ktorServer.stop()
    }
}

@Composable
fun SMSGatewayApp() {
    val navController = rememberNavController()
    var selectedItem by remember { mutableStateOf("dashboard") }
    
    Row(modifier = Modifier.fillMaxSize()) {
        // Sidebar
        Sidebar(
            selectedItem = selectedItem,
            onItemSelected = { item ->
                selectedItem = item
                navController.navigate(item)
            }
        )
        
        // Main Content
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.fillMaxSize()
        ) {
            composable("dashboard") {
                DashboardScreen()
            }
            composable("history") {
                HistoryScreen()
            }
            composable("send") {
                SendSMSScreen()
            }
            composable("settings") {
                SettingsScreen()
            }
        }
    }
}

@Composable
fun Sidebar(selectedItem: String, onItemSelected: (String) -> Unit) {
    Card(
        modifier = Modifier
            .width(260.dp)
            .fillMaxHeight(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Logo
            Row(
                modifier = Modifier.padding(bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📱",
                        fontSize = 20.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "SMS Gateway",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            // Navigation Items
            NavItem(
                icon = Icons.Default.BarChart,
                label = "Dashboard",
                isSelected = selectedItem == "dashboard",
                onClick = { onItemSelected("dashboard") }
            )
            NavItem(
                icon = Icons.Default.History,
                label = "Historia SMS",
                isSelected = selectedItem == "history",
                onClick = { onItemSelected("history") }
            )
            NavItem(
                icon = Icons.AutoMirrored.Filled.Send,
                label = "Wyślij SMS",
                isSelected = selectedItem == "send",
                onClick = { onItemSelected("send") }
            )
            NavItem(
                icon = Icons.Default.Settings,
                label = "Ustawienia",
                isSelected = selectedItem == "settings",
                onClick = { onItemSelected("settings") }
            )
        }
    }
}

@Composable
fun NavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable { onClick() }
            .padding(12.dp, 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}

@Composable
fun DashboardScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        // Title
        Text(
            text = "Dashboard",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        // Stats Grid
        StatsGrid()
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Recent Messages
        RecentMessagesCard()
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Quick Actions
        QuickActionsCard()
    }
}

@Composable
fun StatsGrid() {
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
                    value = "128",
                    label = "SMS w kolejce",
                    iconColor = MaterialTheme.colorScheme.outline
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.CheckCircle,
                    value = "542",
                    label = "Wysłane dzisiaj",
                    subLabel = "Sukces: 99%",
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
                    value = "3",
                    label = "Błędy",
                    subLabel = "Zobacz szczegóły",
                    iconColor = Color(0xFFEAB308),
                    showLink = true
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Storage,
                    value = "Aktywny",
                    label = "Status systemu",
                    subLabel = "Uptime: 48h",
                    iconColor = Color(0xFF22C55E),
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
    isStatus: Boolean = false
) {
    Card(
        modifier = modifier,
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
fun RecentMessagesCard() {
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
                    onClick = { /* TODO */ }
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
                val messages = listOf(
                    MessageData("#0047", "+48 XXX XXX 789", "Przypomnienie o wizycie jutro...", "Wysłane", "2 min temu", true),
                    MessageData("#0046", "+48 XXX XXX 456", "Twoja wizyta została potwierdzona...", "W kolejce", "15 min temu", false),
                    MessageData("#0045", "+48 XXX XXX 123", "Dziękujemy za skorzystanie...", "Wysłane", "1 godz. temu", true)
                )
                
                messages.forEach { message ->
                    MessageRow(message = message)
                }
            }
        }
    }
}

@Composable
fun MessageRow(message: MessageData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp, 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message.id,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp
        )
        Text(
            text = message.number,
            modifier = Modifier.weight(2f),
            fontSize = 14.sp
        )
        Text(
            text = message.content,
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
                text = message.status,
                isSent = message.isSent
            )
        }
        Text(
            text = message.time,
            modifier = Modifier.weight(1.5f),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StatusBadge(text: String, isSent: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (isSent) Color(0xFFDCFCE7) else Color(0xFFFEF9C3))
            .padding(4.dp, 12.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSent) Color(0xFF166534) else Color(0xFF854D0E)
        )
    }
}

@Composable
fun QuickActionsCard() {
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
                        isPrimary = true
                    )
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.BarChart,
                        text = "Zobacz historię"
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Download,
                        text = "Eksportuj dane"
                    )
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Settings,
                        text = "Ustawienia"
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
    isPrimary: Boolean = false
) {
    Button(
        onClick = { /* TODO */ },
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

@Composable
fun HistoryScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Historia SMS",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tutaj będzie pełna historia wiadomości",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SendSMSScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Wyślij SMS",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tutaj będzie formularz wysyłania",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Ustawienia",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tutaj będą ustawienia aplikacji",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

data class MessageData(
    val id: String,
    val number: String,
    val content: String,
    val status: String,
    val time: String,
    val isSent: Boolean
)
