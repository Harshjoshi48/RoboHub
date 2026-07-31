package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class NotificationItem(
    val id: Int,
    val icon: ImageVector,
    val iconTint: Color,
    val iconBgColor: Color,
    val title: String,
    val time: String,
    val message: String,
    val isUnread: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    var notifications by remember {
        mutableStateOf(
            listOf(
                NotificationItem(
                    id = 1,
                    icon = Icons.Outlined.SmartToy,
                    iconTint = Color(0xFF3B82F6),
                    iconBgColor = Color(0xFFEFF6FF),
                    title = "AI Assistant",
                    time = "2 min ago",
                    message = "New rehabilitation recommendation available.",
                    isUnread = true
                ),
                NotificationItem(
                    id = 2,
                    icon = Icons.Outlined.Router,
                    iconTint = Color(0xFF6366F1),
                    iconBgColor = Color(0xFFEEF2FF),
                    title = "Device Connected",
                    time = "10 min ago",
                    message = "ESP32 connected successfully."
                ),
                NotificationItem(
                    id = 3,
                    icon = Icons.Outlined.Psychology,
                    iconTint = Color(0xFF10B981),
                    iconBgColor = Color(0xFFECFDF5),
                    title = "Brain Calibration",
                    time = "Today",
                    message = "Calibration completed successfully."
                ),
                NotificationItem(
                    id = 4,
                    icon = Icons.Outlined.FitnessCenter,
                    iconTint = Color(0xFF10B981),
                    iconBgColor = Color(0xFFECFDF5),
                    title = "Muscle Control",
                    time = "Today",
                    message = "Muscle signal quality improved."
                ),
                NotificationItem(
                    id = 5,
                    icon = Icons.Outlined.Description,
                    iconTint = Color(0xFF8B5CF6),
                    iconBgColor = Color(0xFFF5F3FF),
                    title = "Therapy Report",
                    time = "Yesterday",
                    message = "Your report is ready."
                ),
                NotificationItem(
                    id = 6,
                    icon = Icons.Outlined.BatteryAlert,
                    iconTint = Color(0xFFEF4444),
                    iconBgColor = Color(0xFFFEF2F2),
                    title = "Low Battery",
                    time = "Yesterday",
                    message = "Robotic hand battery is below 20%."
                )
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Notifications", 
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF3B82F6)
                        )
                    }
                },
                actions = {
                    if (notifications.isNotEmpty()) {
                        TextButton(onClick = { notifications = emptyList() }) {
                            Text("Clear All", color = Color(0xFF3B82F6), fontWeight = FontWeight.Medium)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        containerColor = Color.White
    ) { paddingValues ->
        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("No notifications", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(notifications, key = { it.id }) { notification ->
                    NotificationCard(notification)
                }
            }
        }
    }
}

@Composable
fun NotificationCard(item: NotificationItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Unread dot indicator space
            Box(modifier = Modifier.width(12.dp)) {
                if (item.isUnread) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFF3B82F6), CircleShape)
                    )
                }
            }
            
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(item.iconBgColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = item.iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Text Content
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        fontSize = 15.sp
                    )
                    Text(
                        text = item.time,
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.message,
                    color = Color(0xFF475569),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Icon(
                imageVector = Icons.Default.ArrowForwardIos,
                contentDescription = null,
                tint = Color(0xFFCBD5E1),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
