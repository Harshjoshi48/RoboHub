package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    username: String,
    onLogout: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHandControl: () -> Unit = {},
    onNavigateToAIAssistant: () -> Unit = {},
    onNavigateToReports: () -> Unit = {}
) {
    val primaryBlue = Color(0xFF0055AA)
    val buttonBlue = Color(0xFF4A7499)
    val textGray = Color(0xFF64748B)
    val darkText = Color(0xFF1E293B)
    val lightBg = Color(0xFFF5F7FA)
    val cardBg = Color.White
    
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    
    var profileImageUri by remember { mutableStateOf(sharedPrefs.getString("profile_image_uri", null)) }
    var isEditing by remember { mutableStateOf(false) }
    
    var editName by remember { mutableStateOf(sharedPrefs.getString("profile_name", username) ?: username) }
    var editEmail by remember { mutableStateOf(sharedPrefs.getString("profile_email", "${username.lowercase().replace(" ", ".")}@example.com") ?: "${username.lowercase().replace(" ", ".")}@example.com") }
    var editMobile by remember { mutableStateOf(sharedPrefs.getString("profile_mobile", "+91 98765 43210") ?: "+91 98765 43210") }
    
    // Dynamic Network Check
    var isOnline by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        isOnline = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            profileImageUri = it.toString()
            sharedPrefs.edit().putString("profile_image_uri", it.toString()).apply()
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
                    selected = false,
                    onClick = onNavigateToDashboard,
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor = textGray,
                        unselectedTextColor = textGray
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Outlined.PanTool, contentDescription = "Control") },
                    label = { Text("Control", fontSize = 10.sp) },
                    selected = false,
                    onClick = onNavigateToHandControl,
                    colors = NavigationBarItemDefaults.colors(unselectedIconColor = textGray, unselectedTextColor = textGray)
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Outlined.Psychology, contentDescription = "AI") },
                    label = { Text("AI", fontSize = 10.sp) },
                    selected = false,
                    onClick = onNavigateToAIAssistant,
                    colors = NavigationBarItemDefaults.colors(unselectedIconColor = textGray, unselectedTextColor = textGray)
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Outlined.Timeline, contentDescription = "Progress") },
                    label = { Text("Progress", fontSize = 10.sp) },
                    selected = false,
                    onClick = onNavigateToReports,
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateToDashboard) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = darkText)
                }
                Text("NeuroGrip", color = primaryBlue, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Row {
                    IconButton(onClick = { isEditing = !isEditing }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit Profile", tint = if (isEditing) primaryBlue else textGray)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = textGray)
                    }
                }
            }
            
            Text(
                "Profile",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = darkText,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).align(Alignment.CenterHorizontally)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Profile Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        AsyncImage(
                            model = profileImageUri ?: "https://i.pravatar.cc/100?img=11",
                            contentDescription = "Profile Photo",
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(Color.LightGray)
                                .clickable { launcher.launch("image/*") },
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.White, CircleShape)
                                .padding(2.dp)
                                .background(Color(0xFFEFF6FF), CircleShape)
                                .clickable { launcher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit Photo", tint = primaryBlue, modifier = Modifier.size(16.dp))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        editName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = darkText
                    )
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "NG-2026-001",
                            style = MaterialTheme.typography.bodyMedium,
                            color = textGray
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Outlined.VerifiedUser, contentDescription = "Verified", tint = primaryBlue, modifier = Modifier.size(16.dp))
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Box(
                        modifier = Modifier
                            .background(if (isOnline) Color(0xFFECFDF5) else Color(0xFFFEF2F2), RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(if (isOnline) Color(0xFF10B981) else Color(0xFFEF4444), CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (isOnline) "Device Online" else "Device Offline", 
                                color = if (isOnline) Color(0xFF047857) else Color(0xFFB91C1C), 
                                fontSize = 12.sp, 
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text("Member Since: 2026", style = MaterialTheme.typography.bodySmall, color = textGray)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Personal Information
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Personal Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = darkText)
                        if (isEditing) {
                            TextButton(onClick = { 
                                isEditing = false
                                sharedPrefs.edit()
                                    .putString("profile_name", editName)
                                    .putString("profile_email", editEmail)
                                    .putString("profile_mobile", editMobile)
                                    .apply()
                            }) {
                                Text("Save", fontWeight = FontWeight.Bold, color = primaryBlue)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    ProfileInfoRow(
                        icon = Icons.Outlined.Person, 
                        label = "Full Name", 
                        value = editName,
                        isEditing = isEditing,
                        onValueChange = { editName = it }
                    )
                    Divider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(vertical = 12.dp))
                    ProfileInfoRow(
                        icon = Icons.Outlined.Email, 
                        label = "Email Address", 
                        value = editEmail,
                        isEditing = isEditing,
                        onValueChange = { editEmail = it }
                    )
                    Divider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(vertical = 12.dp))
                    ProfileInfoRow(
                        icon = Icons.Outlined.Phone, 
                        label = "Mobile Number", 
                        value = editMobile,
                        isEditing = isEditing,
                        onValueChange = { editMobile = it }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Log Out Button
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
            ) {
                Text("Log Out", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun ProfileInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector, 
    label: String, 
    value: String,
    isEditing: Boolean = false,
    onValueChange: (String) -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, color = Color(0xFF64748B))
            Spacer(modifier = Modifier.height(2.dp))
            if (isEditing) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Color(0xFF1E293B))
                )
            } else {
                Text(value, fontSize = 14.sp, color = Color(0xFF1E293B), fontWeight = FontWeight.Medium)
            }
        }
        if (!isEditing) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Color(0xFFCBD5E1))
        }
    }
}
