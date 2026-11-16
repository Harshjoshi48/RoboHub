package com.example

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

data class NotificationItem(
    val id: Int,
    val icon: ImageVector,
    val iconTint: Color,
    val iconBgColor: Color,
    val title: String,
    val time: String,
    val message: String,
    val isUnread: Boolean = false,
    val category: String = "SYSTEM",
    val targetUsername: String = "ALL"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    adminViewModel: AdminViewModel = viewModel()
) {
    val dbNotifications by adminViewModel.notifications.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showFeedbackDialog by remember { mutableStateOf(false) }

    val mappedItems = remember(dbNotifications) {
        dbNotifications.map { entity ->
            val (icon, tint, bg) = when (entity.category.uppercase()) {
                "SUPPORT" -> Triple(Icons.Outlined.TaskAlt, Color(0xFF059669), Color(0xFFECFDF5))
                "AI" -> Triple(Icons.Outlined.SmartToy, Color(0xFF3B82F6), Color(0xFFEFF6FF))
                "DEVICE" -> Triple(Icons.Outlined.Router, Color(0xFF6366F1), Color(0xFFEEF2FF))
                "THERAPY" -> Triple(Icons.Outlined.Psychology, Color(0xFF10B981), Color(0xFFECFDF5))
                "ALERT" -> Triple(Icons.Outlined.BatteryAlert, Color(0xFFEF4444), Color(0xFFFEF2F2))
                else -> Triple(Icons.Outlined.NotificationsActive, Color(0xFF2563EB), Color(0xFFEFF6FF))
            }
            NotificationItem(
                id = entity.id,
                icon = icon,
                iconTint = tint,
                iconBgColor = bg,
                title = entity.title,
                time = formatAdminTimestamp(entity.timestamp),
                message = entity.message,
                isUnread = entity.isUnread,
                category = entity.category,
                targetUsername = entity.targetUsername
            )
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                TopAppBar(
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    title = {
                        Text(
                            "Notifications & Feedback",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { showFeedbackDialog = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFFECFDF5),
                            contentColor = Color(0xFF047857)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                    ) {
                        Text("Send Feedback", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (mappedItems.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    AppDatabase.getInstance(context).adminDao().markAllNotificationsRead()
                                }
                            },
                            border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                        ) {
                            Text("Mark Read", color = Color(0xFF2563EB), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFFE2E8F0))
            }
        },
        containerColor = Color(0xFFF8FAFC)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            if (mappedItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No notifications", color = Color(0xFF64748B))
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .fillMaxHeight()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(mappedItems, key = { it.id }) { notification ->
                        NotificationCard(notification)
                    }
                }
            }
        }
    }

    if (showFeedbackDialog) {
        var usernameInput by remember { mutableStateOf("patient_demo") }
        var subjectInput by remember { mutableStateOf("") }
        var messageInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            title = {
                Text(
                    "Send App Feedback to Support Desk",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = { usernameInput = it },
                        label = { Text("Your Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = subjectInput,
                        onValueChange = { subjectInput = it },
                        label = { Text("Issue / Feedback Subject") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = messageInput,
                        onValueChange = { messageInput = it },
                        label = { Text("Describe your feedback") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (subjectInput.isNotBlank() && messageInput.isNotBlank()) {
                            val cleanUser = usernameInput.trim().ifBlank { "user" }
                            adminViewModel.submitUserTicket(
                                username = cleanUser,
                                email = "$cleanUser@neurogrip.app",
                                subject = subjectInput.trim(),
                                message = messageInput.trim(),
                                category = "APP_FEEDBACK",
                                priority = "HIGH"
                            )
                            showFeedbackDialog = false
                            Toast.makeText(
                                context,
                                "Feedback submitted! Admin will notify you once resolved.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8)),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Submit Feedback", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showFeedbackDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun NotificationCard(item: NotificationItem) {
    val isSupportSolution = item.category.equals("SUPPORT", ignoreCase = true)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSupportSolution) Color(0xFFECFDF5) else Color.White
        ),
        border = BorderStroke(
            1.dp,
            if (isSupportSolution) Color(0xFF6EE7B7) else Color(0xFFE2E8F0)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(modifier = Modifier.width(10.dp).padding(top = 6.dp)) {
                if (item.isUnread) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFF3B82F6), CircleShape)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(item.iconBgColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = item.iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.time,
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
                if (isSupportSolution) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = Color(0xFFD1FAE5),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "FEEDBACK RESOLVED • FOR @${item.targetUsername}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF065F46),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.message,
                    color = Color(0xFF334155),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}
