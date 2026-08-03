package com.example

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var currentTab by remember { mutableStateOf("Users") }
    
    val primaryBlue = Color(0xFF2F80ED)
    val bgGray = Color(0xFFF8FAFC)
    val textDark = Color(0xFF1E293B)
    val textGray = Color(0xFF64748B)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Panel", fontWeight = FontWeight.Bold, color = textDark) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Filled.Logout, contentDescription = "Logout", tint = Color(0xFFEF4444))
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 16.dp
            ) {
                val tabs = listOf(
                    "Users" to Icons.Outlined.People,
                    "AI API" to Icons.Outlined.Key,
                    "App Theme" to Icons.Outlined.ColorLens
                )
                
                tabs.forEach { (title, icon) ->
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = title) },
                        label = { Text(title, fontSize = 10.sp) },
                        selected = currentTab == title,
                        onClick = { currentTab = title },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = primaryBlue,
                            selectedTextColor = primaryBlue,
                            indicatorColor = primaryBlue.copy(alpha = 0.15f),
                            unselectedIconColor = textGray,
                            unselectedTextColor = textGray
                        )
                    )
                }
            }
        },
        containerColor = bgGray
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentTab) {
                "Users" -> UsersManagementTab()
                "AI API" -> ApiKeyManagementTab(sharedPrefs, context)
                "App Theme" -> ThemeManagementTab(context)
            }
        }
    }
}

data class UserRecord(val id: String, val name: String, val email: String, var isBlocked: Boolean, val role: String)

@Composable
fun UsersManagementTab() {
    var users by remember {
        mutableStateOf(
            listOf(
                UserRecord("1", "Joshi", "joshi@example.com", false, "User"),
                UserRecord("2", "Test User", "test@example.com", false, "User"),
                UserRecord("3", "Spammer", "spam@example.com", true, "User"),
                UserRecord("4", "Admin", "admin@gmail.com", false, "Admin")
            )
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("User Management", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            Text("Control access and roles.", fontSize = 14.sp, color = Color(0xFF64748B))
            Spacer(modifier = Modifier.height(16.dp))
        }

        items(users) { user ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (user.isBlocked) Color(0xFFFEE2E2) else Color(0xFFE0F2FE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (user.isBlocked) Icons.Filled.Block else Icons.Filled.Person, 
                            contentDescription = null, 
                            tint = if (user.isBlocked) Color(0xFFEF4444) else Color(0xFF0284C7)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(user.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
                        Text(user.email, fontSize = 12.sp, color = Color(0xFF64748B))
                        Text("Role: ${user.role}", fontSize = 12.sp, color = Color(0xFF2F80ED), fontWeight = FontWeight.Medium)
                    }
                    if (user.role != "Admin") {
                        Button(
                            onClick = {
                                users = users.map { if (it.id == user.id) it.copy(isBlocked = !it.isBlocked) else it }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (user.isBlocked) Color(0xFF10B981) else Color(0xFFEF4444)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (user.isBlocked) "Unban" else "Ban", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeyManagementTab(sharedPrefs: android.content.SharedPreferences, context: Context) {
    var apiKey by remember { mutableStateOf(sharedPrefs.getString("gemini_api_key", "") ?: "") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("AI Configuration", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
        Text("Manage API keys for the AI assistant.", fontSize = 14.sp, color = Color(0xFF64748B))
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("Gemini API Key") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = {
                sharedPrefs.edit().putString("gemini_api_key", apiKey).apply()
                Toast.makeText(context, "API Key Saved", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F80ED))
        ) {
            Text("Save API Key", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ThemeManagementTab(context: Context) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("App Content & Theme", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
        Text("Control application colors and text.", fontSize = 14.sp, color = Color(0xFF64748B))
        Spacer(modifier = Modifier.height(24.dp))
        
        AdminActionCard("Change Primary Color", Icons.Outlined.ColorLens, context)
        AdminActionCard("Manage App Texts", Icons.Outlined.TextFields, context)
        AdminActionCard("Manage Hero Images", Icons.Outlined.Image, context)
        AdminActionCard("Global Settings", Icons.Outlined.Settings, context)
    }
}

@Composable
fun AdminActionCard(title: String, icon: ImageVector, context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable {
            Toast.makeText(context, "$title setting clicked", Toast.LENGTH_SHORT).show()
        },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF2F80ED))
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFFCBD5E1))
        }
    }
}
