package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun DashboardScreen(username: String, onLogout: () -> Unit, onNavigateToNotifications: () -> Unit = {}, onNavigateToProfile: () -> Unit = {}, onNavigateToSettings: () -> Unit = {}, onNavigateToReports: () -> Unit = {}, viewModel: DashboardViewModel = viewModel()) {
    val primaryBlue = Color(0xFF5A81A5)
    val buttonBlue = Color(0xFF4A7499)
    val textGray = Color(0xFF64748B)
    val darkText = Color(0xFF1E293B)
    val lightBg = Color(0xFFF5F7FA)
    val cardBg = Color.White
    val greenColor = Color(0xFF10B981)
    val borderLight = Color(0xFFE2E8F0)

    val isConnecting by viewModel.isConnecting.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val eegAttention by viewModel.eegAttention.collectAsState()
    val emgStrength by viewModel.emgStrength.collectAsState()
    val latency by viewModel.latency.collectAsState()
    val battery by viewModel.battery.collectAsState()
    val eegDataPoints by viewModel.eegDataPoints.collectAsState()
    val emgDataPoints by viewModel.emgDataPoints.collectAsState()

    val responseTime by viewModel.responseTime.collectAsState()
    val servoTemp by viewModel.servoTemp.collectAsState()
    val gripForce by viewModel.gripForce.collectAsState()
    val therapyProgress by viewModel.therapyProgress.collectAsState()

    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)
    val profileImageUri = sharedPrefs.getString("profile_image_uri", null)
    val connectionMessage by viewModel.connectionMessage.collectAsState()

    LaunchedEffect(connectionMessage) {
        connectionMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }


    var showPermissionDialog by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            showConfirmDialog = true
        } else {
            showPermissionDialog = true
        }
    }



    
    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 16.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                    label = { Text("Home", fontSize = 10.sp, fontWeight = FontWeight.Medium) },
                    selected = true,
                    onClick = { },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = buttonBlue,
                        selectedTextColor = buttonBlue,
                        indicatorColor = buttonBlue.copy(alpha = 0.15f),
                        unselectedIconColor = textGray,
                        unselectedTextColor = textGray
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Outlined.PanTool, contentDescription = "Control") },
                    label = { Text("Control", fontSize = 10.sp) },
                    selected = false,
                    onClick = { },
                    colors = NavigationBarItemDefaults.colors(unselectedIconColor = textGray, unselectedTextColor = textGray)
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Outlined.Psychology, contentDescription = "AI") },
                    label = { Text("AI", fontSize = 10.sp) },
                    selected = false,
                    onClick = { },
                    colors = NavigationBarItemDefaults.colors(unselectedIconColor = textGray, unselectedTextColor = textGray)
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Outlined.Timeline, contentDescription = "Progress") },
                    label = { Text("Progress", fontSize = 10.sp) },
                    selected = false,
                    onClick = { },
                    colors = NavigationBarItemDefaults.colors(unselectedIconColor = textGray, unselectedTextColor = textGray)
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Outlined.Person, contentDescription = "Profile") },
                    label = { Text("Profile", fontSize = 10.sp) },
                    selected = false,
                    onClick = { onNavigateToProfile() },
                    colors = NavigationBarItemDefaults.colors(unselectedIconColor = textGray, unselectedTextColor = textGray)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(lightBg)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = profileImageUri ?: "https://i.pravatar.cc/100?img=11",
                        contentDescription = "Profile",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { onNavigateToProfile() },
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        val displayUser = if (username.length > 10) username.take(10) else username
                        Text(displayUser, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = darkText)
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).background(if (isConnected) greenColor else Color.Red, CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isConnected) "System Online" else "System Offline", style = MaterialTheme.typography.labelSmall, color = textGray, fontSize = 9.sp)
                        }
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🧠", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("NeuroGrip", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = buttonBlue)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateToNotifications) {
                        Icon(imageVector = Icons.Outlined.Notifications, contentDescription = "Notifications", tint = textGray)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(imageVector = Icons.Outlined.Settings, contentDescription = "Settings", tint = textGray)
                    }
                }
            }

            // Hero Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFFF8FAFC), Color(0xFFE2E8F0))
                        )
                    )
                ) {
                    Column(modifier = Modifier.padding(24.dp).fillMaxWidth(0.6f)) {
Text("NeuroGrip Ready", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = darkText)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Your interface is calibrated and ready for seamless neural communication.",
                            style = MaterialTheme.typography.bodySmall,
                            color = textGray,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { 
                                if (isConnecting) return@Button
                                if (isConnected) {
                                    viewModel.toggleConnection(context)
                                } else {
                                    val isGranted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                    
                                    if (isGranted) {
                                        showConfirmDialog = true
                                    } else {
                                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isConnected) Color(0xFFEF4444) else buttonBlue)
                        ) {
                            if (isConnecting) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connecting...", color = Color.White, fontWeight = FontWeight.Medium)
                            } else {
                                Text(if (isConnected) "Disconnect" else "Connect ESP32", color = Color.White, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    
                    AsyncImage(
                        model = "https://images.unsplash.com/photo-1589254065878-42c9da997008?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80",
                        contentDescription = "Hand",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 16.dp)
                            .width(130.dp)
                            .height(90.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // AI Assistant
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("AI ASSISTANT", style = MaterialTheme.typography.labelSmall, color = textGray, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Outlined.Psychology, contentDescription = "AI", tint = buttonBlue, modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("AI Assistant", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = darkText)
                            Text("I am here to optimize your experience.", style = MaterialTheme.typography.bodySmall, color = textGray)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, buttonBlue.copy(alpha = 0.3f))
                    ) {
                        Text("Engage AI Assistant", color = buttonBlue, fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatItem(Icons.Outlined.MonitorHeart, "Hardware", if (isConnected) "Stable" else "Wait", darkText, textGray)
                        StatItem(Icons.Outlined.Psychology, "AI Engine", "Active", darkText, textGray)
                        StatItem(Icons.Outlined.Wifi, "Latency", if (isConnected) "${latency}ms" else "--", darkText, textGray)
                        StatItem(Icons.Outlined.BatteryFull, "Battery", if (isConnected) "${battery}%" else "--", darkText, textGray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Control Modes
            Text(
                "CONTROL MODES",
                style = MaterialTheme.typography.labelSmall,
                color = textGray,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                ModeItem(Icons.Outlined.Psychology, "Brain Control (BCI)", cardBg, darkText, borderLight)
                Spacer(modifier = Modifier.height(12.dp))
                ModeItem(Icons.Outlined.SettingsInputAntenna, "Muscle Control (EMG)", cardBg, darkText, borderLight)
                Spacer(modifier = Modifier.height(12.dp))
                ModeItem(Icons.Outlined.PhoneAndroid, "App Control", cardBg, darkText, borderLight)
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // Live Bio Signals
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Live Bio-Signals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = darkText)
                Text("View Analysis", style = MaterialTheme.typography.labelSmall, color = textGray)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // EEG
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.width(90.dp)) {
                            Text("EEG Attention", style = MaterialTheme.typography.labelSmall, color = textGray, fontSize = 10.sp)
                            Text(if (isConnected) "${eegAttention}%" else "--", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = darkText)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        // Mock Chart
                        Box(modifier = Modifier.weight(1f).height(40.dp)) {
                            // Draw background rectangles for the "bands"
                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(1f).fillMaxHeight())
                                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFFE2E8F0).copy(alpha = 0.5f)))
                                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF10B981).copy(alpha = 0.2f)))
                                Box(modifier = Modifier.weight(0.5f).fillMaxHeight())
                            }
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val width = size.width
                                val height = size.height
                                val path = Path()
                                val stepX = width / (eegDataPoints.size - 1)
                                path.moveTo(0f, height / 2)
                                eegDataPoints.forEachIndexed { index, value ->
                                    val x = index * stepX
                                    val y = (height / 2) - value
                                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                drawPath(path, color = Color(0xFF64748B), style = Stroke(width = 2f))
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(imageVector = Icons.Outlined.ShowChart, contentDescription = null, tint = textGray, modifier = Modifier.size(20.dp))
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // EMG
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.width(90.dp)) {
                            Text("EMG Strength", style = MaterialTheme.typography.labelSmall, color = textGray, fontSize = 10.sp)
                            Text(if (isConnected) "${emgStrength}%" else "--", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = darkText)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        // Mock Chart
                        Box(modifier = Modifier.weight(1f).height(40.dp)) {
                             // Draw background rectangles for the "bands"
                             Row(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(1.5f).fillMaxHeight())
                                Box(modifier = Modifier.weight(0.5f).fillMaxHeight().background(Color(0xFFE2E8F0).copy(alpha = 0.5f)))
                                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF10B981).copy(alpha = 0.2f)))
                                Box(modifier = Modifier.weight(0.5f).fillMaxHeight())
                            }
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val width = size.width
                                val height = size.height
                                val path = Path()
                                val stepX = width / (emgDataPoints.size - 1)
                                path.moveTo(0f, height / 2)
                                emgDataPoints.forEachIndexed { index, value ->
                                    val x = index * stepX
                                    val y = (height / 2) - value
                                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                drawPath(path, color = Color(0xFF64748B), style = Stroke(width = 2f))
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(imageVector = Icons.Outlined.Compress, contentDescription = null, tint = textGray, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // Prosthetic Status
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column {
                    AsyncImage(
                        model = "https://images.unsplash.com/photo-1535378917042-10a22c95931a?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80",
                        contentDescription = "Hand",
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentScale = ContentScale.Crop
                    )
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Prosthetic Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = darkText)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1.2f)) {
                                Text("Therapy Goal", style = MaterialTheme.typography.bodyMedium, color = darkText, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(progress = { 1f }, modifier = Modifier.size(56.dp), color = Color(0xFFF1F5F9), strokeWidth = 5.dp)
                                        CircularProgressIndicator(progress = { therapyProgress }, modifier = Modifier.size(56.dp), color = buttonBlue, strokeWidth = 5.dp)
                                        Text("${(therapyProgress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = darkText)
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text("${(therapyProgress * 20).toInt()} / 20 mins", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = darkText)
                                        Text("Today's Progress", style = MaterialTheme.typography.labelSmall, color = textGray)
                                    }
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                StatusLine("Response Time", if(isConnected) "${responseTime}ms" else "--", darkText, textGray)
                                Spacer(modifier = Modifier.height(8.dp))
                                StatusLine("Servo Temperature", if(isConnected) String.format("%.1f°C", servoTemp) else "--", darkText, textGray)
                                Spacer(modifier = Modifier.height(8.dp))
                                StatusLine("Grip Force", if(isConnected) String.format("%.1f N/m", gripForce) else "--", darkText, textGray)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                "\"Ready to beat yesterday's score, Tarun?\nStart a 5-minute gesture drill.\"",
                                style = MaterialTheme.typography.labelMedium,
                                color = textGray,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Quick Actions
            Text(
                "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp),
                color = darkText
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QuickActionCard(Icons.Outlined.PanTool, "Hand Control", Modifier.weight(1f), cardBg, darkText)
                Spacer(modifier = Modifier.width(12.dp))
                QuickActionCard(Icons.Outlined.BarChart, "Reports", Modifier.weight(1f), cardBg, darkText, onClick = onNavigateToReports)
                Spacer(modifier = Modifier.width(12.dp))
                QuickActionCard(Icons.Outlined.Gesture, "Gesture Studio", Modifier.weight(1f), cardBg, darkText)
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }




        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = { Text("Connect ESP32") },
                text = { Text("Search and connect to the ESP32 network via Wi-Fi?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showConfirmDialog = false
                            viewModel.toggleConnection(context)
                        }
                    ) {
                        Text("Yes")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) {
                        Text("No")
                    }
                }
            )
        }

        if (showPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showPermissionDialog = false },
                title = { Text("Permission Required") },
                text = { Text("Location permission is required to connect to the ESP32 via Wi-Fi. Please enable it in the app settings.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showPermissionDialog = false
                            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    ) {
                        Text("Open Settings")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
}

@Composable
fun StatItem(icon: ImageVector, label: String, value: String, darkText: Color, textGray: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = label, tint = textGray, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = textGray)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = darkText)
    }



}

@Composable
fun ModeItem(icon: ImageVector, title: String, cardBg: Color, darkText: Color, borderLight: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, borderLight)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).background(Color(0xFFF1F5F9), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = darkText)
            }
            ContainerReadyBadge()
        }
    }



}

@Composable
fun ContainerReadyBadge() {
    Box(
        modifier = Modifier
            .background(Color(0xFF10B981).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text("READY", style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 9.sp)
    }



}

@Composable
fun StatusLine(label: String, value: String, darkText: Color, textGray: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = textGray, fontSize = 10.sp)
        Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = darkText)
    }



}

@Composable
fun QuickActionCard(icon: ImageVector, label: String, modifier: Modifier = Modifier, cardBg: Color, darkText: Color, onClick: () -> Unit = {}) {
    Card(
        modifier = modifier.height(100.dp).clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = darkText)
        }
    }



}
