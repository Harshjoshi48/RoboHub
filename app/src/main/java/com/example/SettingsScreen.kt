package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: DashboardViewModel = viewModel(),
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    val lightBg = Color(0xFFF8FAFC)
    val darkText = Color(0xFF1E293B)
    val textGray = Color(0xFF64748B)
    val primaryBlue = Color(0xFF0055AA)
    val buttonBlue = Color(0xFF4A7499)

    val isConnected by viewModel.isConnected.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()
    val battery by viewModel.battery.collectAsState()

    var showConnectDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    
    var showUpdateDialog by remember { mutableStateOf(false) }
    
    var showLanguageDialog by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf(sharedPrefs.getString("app_language", "English") ?: "English") }
    
    var showNotificationDialog by remember { mutableStateOf(false) }
    
    var showPasswordDialog by remember { mutableStateOf(false) }
    
    var showSecurityDialog by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            showConnectDialog = true
        } else {
            showPermissionDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Settings", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = darkText)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = primaryBlue)
                    }
                },
                actions = {
                    IconButton(onClick = { /* Search */ }) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = primaryBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = lightBg)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 16.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                    label = { Text("Home", fontSize = 10.sp, fontWeight = FontWeight.Medium) },
                    selected = false,
                    onClick = { onBack() },
                    colors = NavigationBarItemDefaults.colors(
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
                    icon = { Icon(Icons.Outlined.TrendingUp, contentDescription = "Progress") },
                    label = { Text("Progress", fontSize = 10.sp) },
                    selected = false,
                    onClick = { },
                    colors = NavigationBarItemDefaults.colors(unselectedIconColor = textGray, unselectedTextColor = textGray)
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Outlined.Person, contentDescription = "Profile") },
                    label = { Text("Profile", fontSize = 10.sp, fontWeight = FontWeight.Medium) },
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
            }
        },
        containerColor = lightBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Section 1
            SettingsSection {
                SettingsRow(
                    icon = Icons.Outlined.Devices,
                    title = "Connected Devices",
                    onClick = {
                        if (isConnected) {
                            showConnectDialog = true
                        } else {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                showConnectDialog = true
                            } else {
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        }
                    }
                )
                HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(start = 56.dp))
                SettingsRow(
                    icon = Icons.Outlined.SystemUpdateAlt,
                    title = "OTA Firmware Update",
                    hasRedDot = true,
                    onClick = { showUpdateDialog = true }
                )
                HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(start = 56.dp))
                SettingsRow(
                    icon = Icons.Outlined.BatteryFull,
                    title = "Battery Status",
                    trailingText = if (isConnected) "$battery%" else "--",
                    onClick = {} // Just info
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Section 2
            SettingsSection {
                SettingsRow(
                    icon = Icons.Outlined.Language,
                    title = "Language",
                    trailingText = selectedLanguage,
                    onClick = { showLanguageDialog = true }
                )
                HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(start = 56.dp))
                SettingsRow(
                    icon = Icons.Outlined.Notifications,
                    title = "Notifications",
                    onClick = { showNotificationDialog = true }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Section 3
            SettingsSection {
                SettingsRow(
                    icon = Icons.Outlined.VpnKey,
                    title = "Change Password",
                    onClick = { showPasswordDialog = true }
                )
                HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(start = 56.dp))
                SettingsRow(
                    icon = Icons.Outlined.Security,
                    title = "Security",
                    onClick = { showSecurityDialog = true }
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Logout Button
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFB91C1C)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFFB91C1C)
                )
            ) {
                Text("Logout", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }

        // Dialogs
        if (showConnectDialog) {
            AlertDialog(
                onDismissRequest = { showConnectDialog = false },
                title = { Text("Connect to ESP32") },
                text = { Text(if (isConnected) "Are you sure you want to disconnect from ESP32?" else "This will scan and connect to the ESP32 Wi-Fi network. Do you want to proceed?") },
                confirmButton = {
                    TextButton(onClick = {
                        showConnectDialog = false
                        viewModel.toggleConnection(context)
                    }) {
                        Text("Yes")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConnectDialog = false }) {
                        Text("No")
                    }
                }
            )
        }

        if (showPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showPermissionDialog = false },
                title = { Text("Permission Required") },
                text = { Text("Location permission is needed to scan for Wi-Fi networks.") },
                confirmButton = {
                    TextButton(onClick = { showPermissionDialog = false }) { Text("OK") }
                }
            )
        }

        if (showUpdateDialog) {
            AlertDialog(
                onDismissRequest = { showUpdateDialog = false },
                title = { Text("OTA Firmware Update") },
                text = { Text("App update is available. Do you want to download and install the latest firmware on your ESP32 device?") },
                confirmButton = {
                    TextButton(onClick = { showUpdateDialog = false }) { Text("Update Now") }
                },
                dismissButton = {
                    TextButton(onClick = { showUpdateDialog = false }) { Text("Later") }
                }
            )
        }

        if (showLanguageDialog) {
            AlertDialog(
                onDismissRequest = { showLanguageDialog = false },
                title = { Text("Select Language") },
                text = {
                    Column {
                        val languages = listOf("English", "Hindi", "Gujarati")
                        languages.forEach { lang ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedLanguage = lang
                                        sharedPrefs.edit().putString("app_language", lang).apply()
                                        showLanguageDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedLanguage == lang,
                                    onClick = null
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(lang)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLanguageDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showNotificationDialog) {
            var esp32Notif by remember { mutableStateOf(sharedPrefs.getBoolean("notif_esp32", true)) }
            var muscleNotif by remember { mutableStateOf(sharedPrefs.getBoolean("notif_muscle", true)) }
            var neuroGripNotif by remember { mutableStateOf(sharedPrefs.getBoolean("notif_neurogrip", true)) }
            var appNotif by remember { mutableStateOf(sharedPrefs.getBoolean("notif_app", true)) }

            AlertDialog(
                onDismissRequest = { showNotificationDialog = false },
                title = { Text("Notifications") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        NotificationSwitch("ESP32 Notifications", esp32Notif) { 
                            esp32Notif = it; sharedPrefs.edit().putBoolean("notif_esp32", it).apply() 
                        }
                        NotificationSwitch("Muscle Signal Notifications", muscleNotif) { 
                            muscleNotif = it; sharedPrefs.edit().putBoolean("notif_muscle", it).apply() 
                        }
                        NotificationSwitch("NeuroGrip Notifications", neuroGripNotif) { 
                            neuroGripNotif = it; sharedPrefs.edit().putBoolean("notif_neurogrip", it).apply() 
                        }
                        NotificationSwitch("Application Related", appNotif) { 
                            appNotif = it; sharedPrefs.edit().putBoolean("notif_app", it).apply() 
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showNotificationDialog = false }) { Text("Close") }
                }
            )
        }

        if (showPasswordDialog) {
            var oldPass by remember { mutableStateOf("") }
            var newPass by remember { mutableStateOf("") }
            var confirmPass by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showPasswordDialog = false },
                title = { Text("Change Password") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = oldPass,
                            onValueChange = { oldPass = it },
                            label = { Text("Current Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = newPass,
                            onValueChange = { newPass = it },
                            label = { Text("New Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = confirmPass,
                            onValueChange = { confirmPass = it },
                            label = { Text("Confirm Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { 
                        // Mock saving logic
                        showPasswordDialog = false 
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showPasswordDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showSecurityDialog) {
            AlertDialog(
                onDismissRequest = { showSecurityDialog = false },
                title = { Text("Security Overview") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text("Confidentiality", fontWeight = FontWeight.Bold, color = primaryBlue)
                        Text("Your biometric and muscle signal data are encrypted and kept strictly confidential. Only your registered device can decrypt the signal parameters.", fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text("Integrity", fontWeight = FontWeight.Bold, color = primaryBlue)
                        Text("Data sent between your phone and the ESP32 is securely verified. Any tampering with OTA firmware updates or signals is automatically blocked.", fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text("Availability", fontWeight = FontWeight.Bold, color = primaryBlue)
                        Text("Our application architecture ensures that whether you are online or offline, your local connection to the ESP32 remains highly available.", fontSize = 14.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSecurityDialog = false }) { Text("Close") }
                }
            )
        }
    }
}

@Composable
fun NotificationSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsSection(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            content()
        }
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    trailingText: String? = null,
    hasRedDot: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon Container
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = title,
            fontSize = 15.sp,
            color = Color(0xFF1E293B),
            modifier = Modifier.weight(1f)
        )
        
        if (hasRedDot) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(Color(0xFFEF4444), CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        if (trailingText != null) {
            Text(
                text = trailingText,
                fontSize = 13.sp,
                color = Color(0xFF64748B)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFFCBD5E1),
            modifier = Modifier.size(20.dp)
        )
    }
}
