package com.example

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AdminSection(val title: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Outlined.Dashboard),
    SYSTEM_CONFIG("System Configuration", Icons.Outlined.SettingsSuggest),
    BRANDING("App Branding", Icons.Outlined.Palette),
    FIRESTORE_DB("Firestore DB", Icons.Outlined.Storage),
    USERS("Users", Icons.Outlined.People),
    NOTIFICATIONS("Push & FCM", Icons.Outlined.NotificationsActive),
    REMOTE_CONFIG("Remote Config", Icons.Outlined.Tune),
    SDUI_UI("Server UI (SDUI)", Icons.Outlined.ViewQuilt),
    BANNERS("Banners & CMS", Icons.Outlined.Campaign),
    ANALYTICS("Analytics", Icons.Outlined.Analytics),
    SUPPORT("Support Desk", Icons.Outlined.SupportAgent),
    ROLES("Admins & RBAC", Icons.Outlined.AdminPanelSettings),
    SECURITY("Audit & Security", Icons.Outlined.Security),
    SYSTEM_TOOLS("Billing, AI & Backup", Icons.Outlined.Build)
}

fun formatAdminTimestamp(ts: Long): String {
    val sdf = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
    return sdf.format(Date(ts))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdminPanelScreen(
    adminViewModel: AdminViewModel,
    onNavigateToUserAppPreview: (String) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val users by adminViewModel.users.collectAsStateWithLifecycle()
    val loginHistory by adminViewModel.loginHistory.collectAsStateWithLifecycle()
    val notifications by adminViewModel.notifications.collectAsStateWithLifecycle()
    val remoteConfig by adminViewModel.remoteConfig.collectAsStateWithLifecycle()
    val configHistory by adminViewModel.configHistory.collectAsStateWithLifecycle()
    val banners by adminViewModel.banners.collectAsStateWithLifecycle()
    val tickets by adminViewModel.tickets.collectAsStateWithLifecycle()
    val auditLogs by adminViewModel.auditLogs.collectAsStateWithLifecycle()
    val subscriptions by adminViewModel.subscriptions.collectAsStateWithLifecycle()
    val backups by adminViewModel.backups.collectAsStateWithLifecycle()
    val sduiSyncState by adminViewModel.sduiSyncState.collectAsStateWithLifecycle()
    val sduiHistory by adminViewModel.sduiHistory.collectAsStateWithLifecycle()
    val appBranding by adminViewModel.appBranding.collectAsStateWithLifecycle()
    val statusMessage by adminViewModel.statusMessage.collectAsStateWithLifecycle()
    val roleOverride by adminViewModel.activeRoleOverride.collectAsStateWithLifecycle()

    val selectedSection by adminViewModel.selectedAdminSection.collectAsStateWithLifecycle()
    var showRoleSwitcherDialog by remember { mutableStateOf(false) }

    val currentSession by adminViewModel.currentSession.collectAsStateWithLifecycle()
    val currentUserProfile by adminViewModel.currentUserProfile.collectAsStateWithLifecycle()
    val isStrictlyAuthorizedAdmin by adminViewModel.isAuthorizedAdminSession.collectAsStateWithLifecycle()
    val effectiveRole = remember(roleOverride, currentSession) {
        adminViewModel.effectiveRole()
    }
    val effectiveUsername = adminViewModel.effectiveUsername()
    val adminFullName = currentUserProfile?.name?.takeIf { it.isNotBlank() }
        ?: currentSession?.name?.takeIf { it.isNotBlank() }
        ?: effectiveUsername
    val adminEmail = currentUserProfile?.email?.takeIf { it.isNotBlank() }
        ?: currentSession?.email
    val adminPhotoUrl = currentUserProfile?.photoUrl?.takeIf { it.isNotBlank() }
        ?: currentSession?.photoUrl
    val adminPhotoUpdatedAt = maxOf(
        currentUserProfile?.photoUpdatedAt ?: 0L,
        currentSession?.photoUpdatedAt ?: 0L
    )

    val hasValidAdminAccess = SecureSessionManager.isSessionValid(currentSession) &&
        isStrictlyAuthorizedAdmin &&
        effectiveRole != AdminRole.USER &&
        effectiveRole != AdminRole.BLOCKED

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, hasValidAdminAccess, currentSession?.token) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && hasValidAdminAccess) {
                adminViewModel.onEnteredAdminPanel()
                adminViewModel.refreshCloudAdminVerification()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (!hasValidAdminAccess) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = "OTP Verification Required",
                        tint = Color(0xFFF87171),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Super Admin Login",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Restricted Access. Authorized Super Admin Only.",
                        color = Color(0xFFFCA5A5),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "hj195344@gmail.com • hj195345@gmail.com",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sign in with your Super Admin email. Sign in to continue. A 6-digit OTP will be sent to your email.",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            adminViewModel.logoutAdmin()
                            onLogout()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Return to Super Admin Login", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            adminViewModel.clearMessage()
        }
    }

    val darkNavy = Color(0xFF0F172A)
    val accentBlue = Color(0xFF2563EB)
    val surfaceBg = Color(0xFFF1F5F9)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF0F172A), Color(0xFF1E3A8A))
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            SmartUserAvatar(
                                photoUrl = adminPhotoUrl,
                                fullName = adminFullName,
                                email = adminEmail,
                                photoUpdatedAt = adminPhotoUpdatedAt,
                                size = 38.dp,
                                fontSize = 15.sp,
                                borderColor = Color(0xFF93C5FD),
                                borderWidth = 1.5.dp,
                                contentDescription = "Admin Profile Avatar",
                                testTag = "admin_top_bar_avatar"
                            )
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(Color(0xFF1E3A8A), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.AdminPanelSettings,
                                    contentDescription = "Admin Shield",
                                    tint = Color(0xFF93C5FD),
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        AppBrandingLogo(
                            branding = appBranding,
                            size = 30.dp,
                            cornerRadius = 8.dp,
                            showBorder = true,
                            borderColor = Color(0xFF93C5FD),
                            testTag = "admin_top_bar_branding_logo"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${appBranding.appName} Admin Console",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("admin_top_bar_app_name")
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { showRoleSwitcherDialog = true }
                            ) {
                                Text(
                                    text = "$effectiveUsername • ${effectiveRole.displayName}",
                                    color = Color(0xFF93C5FD),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "Switch Role",
                                    tint = Color(0xFF93C5FD),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            onClick = {
                                if (adminViewModel.enterUserAppPreviewMode()) {
                                    onNavigateToUserAppPreview(effectiveUsername)
                                } else {
                                    adminViewModel.logoutAdmin()
                                    onLogout()
                                }
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color.White.copy(alpha = 0.14f),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .testTag("open_user_app_button")
                        ) {
                            Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("User App", fontSize = 12.sp, maxLines = 1)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = {
                                adminViewModel.logoutAdmin()
                                onLogout()
                            },
                            modifier = Modifier.testTag("admin_logout_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Logout Admin",
                                tint = Color(0xFFFCA5A5)
                            )
                        }
                    }
                }

                // Scrollable Navigation Bar for all 10 Admin Modules
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AdminSection.entries.forEach { section ->
                        val selected = selectedSection == section
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (selected) Color(0xFF2563EB) else Color.White.copy(alpha = 0.08f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { adminViewModel.selectAdminSection(section) }
                                .testTag("admin_tab_${section.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = section.icon,
                                    contentDescription = section.title,
                                    tint = if (selected) Color.White else Color(0xFFCBD5E1),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = section.title,
                                    color = if (selected) Color.White else Color(0xFFCBD5E1),
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = surfaceBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedSection) {
                AdminSection.DASHBOARD -> AdminOverviewTab(
                    users = users,
                    tickets = tickets,
                    notifications = notifications,
                    subscriptions = subscriptions,
                    remoteConfig = remoteConfig ?: RemoteConfigEntity(),
                    auditLogs = auditLogs,
                    onJumpToSection = { adminViewModel.selectAdminSection(it) }
                )
                AdminSection.SYSTEM_CONFIG -> SystemConfigScreen(
                    adminViewModel = adminViewModel
                )
                AdminSection.BRANDING -> AdminBrandingTab(
                    adminViewModel = adminViewModel
                )
                AdminSection.FIRESTORE_DB -> AdminFirestoreDatabaseTab(
                    adminViewModel = adminViewModel,
                    auditLogs = auditLogs
                )
                AdminSection.USERS -> AdminUsersTab(
                    users = users,
                    loginHistory = loginHistory,
                    adminViewModel = adminViewModel
                )
                AdminSection.NOTIFICATIONS -> AdminNotificationsTab(
                    notifications = notifications,
                    users = users,
                    adminViewModel = adminViewModel
                )
                AdminSection.REMOTE_CONFIG -> AdminRemoteConfigTab(
                    remoteConfig = remoteConfig ?: RemoteConfigEntity(),
                    configHistory = configHistory,
                    adminViewModel = adminViewModel
                )
                AdminSection.SDUI_UI -> AdminSduiTab(
                    sduiSyncState = sduiSyncState,
                    sduiHistory = sduiHistory,
                    adminViewModel = adminViewModel
                )
                AdminSection.BANNERS -> AdminBannersTab(
                    banners = banners,
                    adminViewModel = adminViewModel
                )
                AdminSection.ANALYTICS -> AdminAnalyticsTab(
                    users = users,
                    subscriptions = subscriptions,
                    notifications = notifications,
                    tickets = tickets,
                    loginHistory = loginHistory,
                    auditLogs = auditLogs,
                    adminViewModel = adminViewModel
                )
                AdminSection.SUPPORT -> AdminSupportTicketsTab(
                    tickets = tickets,
                    users = users,
                    adminViewModel = adminViewModel
                )
                AdminSection.ROLES -> AdminRolesAndStaffTab(
                    users = users,
                    effectiveRole = effectiveRole,
                    adminViewModel = adminViewModel
                )
                AdminSection.SECURITY -> AdminSecurityAuditTab(
                    auditLogs = auditLogs,
                    loginHistory = loginHistory,
                    remoteConfig = remoteConfig ?: RemoteConfigEntity(),
                    adminViewModel = adminViewModel
                )
                AdminSection.SYSTEM_TOOLS -> AdminSystemToolsTab(
                    subscriptions = subscriptions,
                    backups = backups,
                    adminViewModel = adminViewModel
                )
            }
        }
    }

    if (showRoleSwitcherDialog) {
        AlertDialog(
            onDismissRequest = { showRoleSwitcherDialog = false },
            title = { Text("RBAC Role Enforcement Tester", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Test how backend-enforced permissions restrict actions for each administrator role:",
                        fontSize = 13.sp,
                        color = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    listOf(
                        AdminRole.SUPER_ADMIN,
                        AdminRole.ADMIN,
                        AdminRole.EDITOR,
                        AdminRole.SUPPORT_AGENT,
                        AdminRole.ANALYST
                    ).forEach { r ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (effectiveRole == r) Color(0xFFDBEAFE) else Color(0xFFF8FAFC),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    adminViewModel.setRoleSimulationOverride(r)
                                    showRoleSwitcherDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(r.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        "${RolePermissionPolicy.permissionsFor(r).size} permissions granted",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                                RadioButton(selected = effectiveRole == r, onClick = null)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRoleSwitcherDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun AdminOverviewTab(
    users: List<User>,
    tickets: List<SupportTicketEntity>,
    notifications: List<NotificationEntity>,
    subscriptions: List<SubscriptionRecordEntity>,
    remoteConfig: RemoteConfigEntity,
    auditLogs: List<AuditLogEntity>,
    onJumpToSection: (AdminSection) -> Unit
) {
    val activeUsers = users.count { it.status == AccountStatus.ACTIVE.name }
    val loggedInUsers = users.count { it.loginCount > 0 && it.lastLoginAt > 0L }
    val suspendedUsers = users.count {
        it.status in setOf(AccountStatus.SUSPENDED.name, AccountStatus.BLOCKED.name) ||
            it.role == AdminRole.BLOCKED.name
    }
    val inactiveUsers = users.count { it.status == AccountStatus.INACTIVE.name }
    val openTickets = tickets.count { it.status == "OPEN" || it.status == "IN_PROGRESS" }
    val mrr = subscriptions.filter { it.status == "ACTIVE" }.sumOf { it.amountUsd }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // System Status Banner
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (remoteConfig.maintenanceMode) Color(0xFFFEF2F2) else Color(0xFFECFDF5)
                ),
                border = BorderStroke(
                    1.dp,
                    if (remoteConfig.maintenanceMode) Color(0xFFF87171) else Color(0xFF34D399)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (remoteConfig.maintenanceMode) Icons.Filled.Warning else Icons.Filled.Verified,
                            contentDescription = null,
                            tint = if (remoteConfig.maintenanceMode) Color(0xFFDC2626) else Color(0xFF059669),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (remoteConfig.maintenanceMode) "MAINTENANCE MODE ACTIVE" else "ALL SYSTEMS OPERATIONAL",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = if (remoteConfig.maintenanceMode) Color(0xFF991B1B) else Color(0xFF065F46)
                            )
                            Text(
                                text = "Remote Config v${remoteConfig.configVersion} • Min App v${remoteConfig.minSupportedVersionName} • ESP32 Default ${remoteConfig.defaultEsp32Ip}",
                                fontSize = 12.sp,
                                color = Color(0xFF475569)
                            )
                        }
                    }
                    TextButton(onClick = { onJumpToSection(AdminSection.REMOTE_CONFIG) }) {
                        Text("Configure")
                    }
                }
            }
        }

        item {
            Text(
                "Real-Time Platform Metrics",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AdminMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Registered Users",
                    value = "${users.size}",
                    subtitle = "$loggedInUsers logged in • $activeUsers active",
                    icon = Icons.Outlined.People,
                    accent = Color(0xFF2563EB),
                    onClick = { onJumpToSection(AdminSection.USERS) }
                )
                AdminMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Currently Active",
                    value = "$activeUsers",
                    subtitle = "$suspendedUsers blocked • $inactiveUsers inactive",
                    icon = Icons.Outlined.VerifiedUser,
                    accent = Color(0xFF059669),
                    onClick = { onJumpToSection(AdminSection.USERS) }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AdminMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Support Queue",
                    value = "$openTickets Open",
                    subtitle = "${tickets.size} total tickets logged",
                    icon = Icons.Outlined.SupportAgent,
                    accent = Color(0xFFD97706),
                    onClick = { onJumpToSection(AdminSection.SUPPORT) }
                )
                AdminMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "FCM Broadcasts",
                    value = "${notifications.size}",
                    subtitle = "Push notifications sent",
                    icon = Icons.Outlined.NotificationsActive,
                    accent = Color(0xFF7C3AED),
                    onClick = { onJumpToSection(AdminSection.NOTIFICATIONS) }
                )
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Quick Administrative Actions",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { onJumpToSection(AdminSection.USERS) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Manage Users", fontSize = 12.sp)
                        }
                        FilledTonalButton(
                            onClick = { onJumpToSection(AdminSection.NOTIFICATIONS) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send Push", fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { onJumpToSection(AdminSection.BANNERS) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Campaign, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit Banners", fontSize = 12.sp)
                        }
                        FilledTonalButton(
                            onClick = { onJumpToSection(AdminSection.FIRESTORE_DB) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Storage, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Firestore DB", fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = { onJumpToSection(AdminSection.SYSTEM_CONFIG) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_action_system_config")
                    ) {
                        Icon(Icons.Outlined.SettingsSuggest, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Super Admin System Configuration", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = { onJumpToSection(AdminSection.BRANDING) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_action_app_branding")
                    ) {
                        Icon(Icons.Outlined.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("App Branding & Identity Management", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Recent Security Audit Trail",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF0F172A)
                        )
                        TextButton(onClick = { onJumpToSection(AdminSection.SECURITY) }) {
                            Text("View All")
                        }
                    }
                    auditLogs.take(5).forEach { log ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        when (log.severity) {
                                            "CRITICAL" -> Color(0xFFDC2626)
                                            "WARNING" -> Color(0xFFD97706)
                                            else -> Color(0xFF2563EB)
                                        },
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${log.actionType} • ${log.targetEntity}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF1E293B)
                                )
                                Text(
                                    log.details,
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                formatAdminTimestamp(log.timestamp),
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                    }
                }
            }
        }
    }
}

@Composable
fun AdminMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 12.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(accent.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Color(0xFF0F172A))
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, fontSize = 11.sp, color = Color(0xFF64748B), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdminUsersTab(
    users: List<User>,
    loginHistory: List<LoginHistoryEntity>,
    adminViewModel: AdminViewModel
) {
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("ALL") }
    var sortBy by remember { mutableStateOf("NEWEST") }
    var pageIndex by remember { mutableIntStateOf(0) }
    val pageSize = 5
    var selectedUserForDetail by remember { mutableStateOf<User?>(null) }
    var showCreateUserDialog by remember { mutableStateOf(false) }
    var userToDelete by remember { mutableStateOf<User?>(null) }
    var userToSuspend by remember { mutableStateOf<User?>(null) }
    var userForPermissions by remember { mutableStateOf<User?>(null) }

    val totalRegistered = users.size
    val totalLoggedIn = users.count { it.loginCount > 0 && it.lastLoginAt > 0L }
    val currentlyActive = users.count { it.status == AccountStatus.ACTIVE.name }
    val blockedCount = users.count {
        it.status in setOf(AccountStatus.SUSPENDED.name, AccountStatus.BLOCKED.name) ||
            it.role == AdminRole.BLOCKED.name
    }

    val filteredUsers = remember(users, searchQuery, statusFilter, sortBy) {
        val base = users.filter { u ->
            val matchesQuery = searchQuery.isBlank() ||
                u.name.contains(searchQuery, ignoreCase = true) ||
                u.username.contains(searchQuery, ignoreCase = true) ||
                u.email.contains(searchQuery, ignoreCase = true) ||
                u.role.contains(searchQuery, ignoreCase = true) ||
                u.authProvider.contains(searchQuery, ignoreCase = true)
            val matchesStatus = when (statusFilter) {
                "ALL" -> true
                "ADMINS" -> u.role != AdminRole.USER.name && u.role != AdminRole.BLOCKED.name
                "ACTIVE" -> u.status == AccountStatus.ACTIVE.name
                "BLOCKED", "SUSPENDED" -> u.status in setOf(AccountStatus.SUSPENDED.name, AccountStatus.BLOCKED.name) || u.role == AdminRole.BLOCKED.name
                "INACTIVE" -> u.status == AccountStatus.INACTIVE.name
                "GOOGLE" -> u.authProvider.contains("GOOGLE", ignoreCase = true)
                else -> u.status == statusFilter || u.role == statusFilter
            }
            matchesQuery && matchesStatus
        }
        when (sortBy) {
            "LAST_LOGIN" -> base.sortedByDescending { it.lastLoginAt }
            "MOST_LOGINS" -> base.sortedByDescending { it.loginCount }
            "NAME" -> base.sortedBy { it.name.lowercase() }
            else -> base.sortedByDescending { it.timestamp }
        }
    }

    val totalPages = ((filteredUsers.size + pageSize - 1) / pageSize).coerceAtLeast(1)
    val safePage = pageIndex.coerceIn(0, totalPages - 1)
    val pagedUsers = remember(filteredUsers, safePage) {
        val from = (safePage * pageSize).coerceAtMost(filteredUsers.size)
        val to = (from + pageSize).coerceAtMost(filteredUsers.size)
        filteredUsers.subList(from, to)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // User KPI Summary Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("$totalRegistered", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF1E3A8A))
                    Text("Registered", fontSize = 10.sp, color = Color(0xFF64748B))
                }
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("$totalLoggedIn", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF0284C7))
                    Text("Logged In", fontSize = 10.sp, color = Color(0xFF64748B))
                }
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("$currentlyActive", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF059669))
                    Text("Active Now", fontSize = 10.sp, color = Color(0xFF64748B))
                }
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("$blockedCount", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFFDC2626))
                    Text("Blocked", fontSize = 10.sp, color = Color(0xFF64748B))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "User Management (${filteredUsers.size})",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp,
                color = Color(0xFF0F172A),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { adminViewModel.syncRealUsersFromFirebase() },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .testTag("admin_sync_firebase_users_button")
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sync Firebase", fontSize = 12.sp, maxLines = 1)
                }
                Button(
                    onClick = { showCreateUserDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .testTag("admin_add_user_button")
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add User", fontSize = 13.sp, maxLines = 1)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                pageIndex = 0
            },
            placeholder = { Text("Search by username, name, email, role, or provider...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_user_search_input")
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("ALL", "ADMINS", "ACTIVE", "SUSPENDED", "BLOCKED", "INACTIVE", "GOOGLE").forEach { filter ->
                FilterChip(
                    selected = statusFilter == filter,
                    onClick = {
                        statusFilter = filter
                        pageIndex = 0
                    },
                    label = { Text(filter, fontSize = 11.sp) }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Sort:", fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold)
            listOf(
                "NEWEST" to "Registered Date",
                "LAST_LOGIN" to "Last Login",
                "MOST_LOGINS" to "Most Logins",
                "NAME" to "Name A-Z"
            ).forEach { (key, label) ->
                AssistChip(
                    onClick = { sortBy = key; pageIndex = 0 },
                    label = {
                        Text(
                            label,
                            fontSize = 11.sp,
                            fontWeight = if (sortBy == key) FontWeight.Bold else FontWeight.Normal,
                            color = if (sortBy == key) Color(0xFF2563EB) else Color(0xFF475569)
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (filteredUsers.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No matching users found.", color = Color(0xFF64748B))
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(pagedUsers, key = { it.id }) { user ->
                    val isSuspendedOrBlocked = user.status in setOf(AccountStatus.SUSPENDED.name, AccountStatus.BLOCKED.name) ||
                        user.role == AdminRole.BLOCKED.name
                    val isInactive = user.status == AccountStatus.INACTIVE.name
                    val isProtectedSuperAdmin = user.username.equals("hj123", ignoreCase = true) ||
                        user.username.equals("hj195344", ignoreCase = true) ||
                        user.email.equals("hj195344@gmail.com", ignoreCase = true) ||
                        user.email.equals("hj195345@gmail.com", ignoreCase = true)
                    val roleEnum = AdminRole.fromString(user.role)
                    val customPerms = RolePermissionPolicy.parseCustomPermissions(user.customPermissions)

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedUserForDetail = user }
                            .testTag("user_card_${user.username}")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                SmartUserAvatar(
                                    photoUrl = user.photoUrl,
                                    fullName = user.name,
                                    email = user.email,
                                    photoUpdatedAt = user.photoUpdatedAt,
                                    size = 42.dp,
                                    fontSize = 16.sp,
                                    borderColor = Color(0xFFE2E8F0),
                                    borderWidth = 1.dp,
                                    contentDescription = "${user.name} avatar",
                                    testTag = "admin_user_avatar_${user.username}"
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            user.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Color(0xFF0F172A)
                                        )
                                        Surface(
                                            color = if (roleEnum != AdminRole.USER) Color(0xFFDBEAFE) else Color(0xFFF1F5F9),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                roleEnum.displayName,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (roleEnum != AdminRole.USER) Color(0xFF1D4ED8) else Color(0xFF475569),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Surface(
                                            color = Color(0xFFF3E8FF),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                user.authProvider,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF6B21A8),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        "@${user.username} • ${user.email}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B)
                                    )
                                    Text(
                                        "Registered: ${formatAdminTimestamp(user.timestamp)} • Last Login: ${if (user.loginCount > 0 && user.lastLoginAt > 0L) formatAdminTimestamp(user.lastLoginAt) else "Never"} (${user.loginCount}x)",
                                        fontSize = 11.sp,
                                        color = Color(0xFF475569)
                                    )
                                    Text(
                                        "Plan: ${user.subscriptionPlan} • Device: ${user.deviceId}" +
                                            if (customPerms.isNotEmpty()) " • +${customPerms.size} Custom Perms" else "",
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }

                                Surface(
                                    color = when {
                                        isSuspendedOrBlocked -> Color(0xFFFEF2F2)
                                        isInactive -> Color(0xFFFFFBEB)
                                        else -> Color(0xFFECFDF5)
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        user.status,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            isSuspendedOrBlocked -> Color(0xFFDC2626)
                                            isInactive -> Color(0xFFD97706)
                                            else -> Color(0xFF059669)
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { selectedUserForDetail = user },
                                    modifier = Modifier.heightIn(min = 40.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Edit", fontSize = 11.sp, maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = { userForPermissions = user },
                                    modifier = Modifier
                                        .heightIn(min = 40.dp)
                                        .testTag("permissions_${user.username}"),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Permissions", fontSize = 11.sp, maxLines = 1)
                                }
                                if (isSuspendedOrBlocked) {
                                    Button(
                                        onClick = {
                                            adminViewModel.setUserStatus(
                                                user.id,
                                                AccountStatus.ACTIVE,
                                                "Reactivated/unblocked by administrator"
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                        modifier = Modifier
                                            .heightIn(min = 40.dp)
                                            .testTag("reactivate_${user.username}"),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Unblock", fontSize = 11.sp, maxLines = 1)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { userToSuspend = user },
                                        enabled = !isProtectedSuperAdmin,
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD97706)),
                                        modifier = Modifier
                                            .heightIn(min = 40.dp)
                                            .testTag("suspend_${user.username}"),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Block", fontSize = 11.sp, maxLines = 1)
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        val next = if (isInactive) AccountStatus.ACTIVE else AccountStatus.INACTIVE
                                        adminViewModel.setUserStatus(
                                            user.id,
                                            next,
                                            if (next == AccountStatus.INACTIVE) "Deactivated by administrator" else "Activated by administrator"
                                        )
                                    },
                                    enabled = !isProtectedSuperAdmin,
                                    modifier = Modifier
                                        .heightIn(min = 40.dp)
                                        .testTag("deactivate_${user.username}"),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(if (isInactive) "Activate" else "Deactivate", fontSize = 11.sp, maxLines = 1)
                                }
                                IconButton(
                                    onClick = { userToDelete = user },
                                    enabled = !isProtectedSuperAdmin,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .testTag("delete_${user.username}")
                                ) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Color(0xFFDC2626))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Page ${safePage + 1} of $totalPages (${filteredUsers.size} users)",
                    fontSize = 12.sp,
                    color = Color(0xFF475569),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { if (safePage > 0) pageIndex = safePage - 1 },
                        enabled = safePage > 0,
                        modifier = Modifier.heightIn(min = 40.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Prev", fontSize = 12.sp, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { if (safePage < totalPages - 1) pageIndex = safePage + 1 },
                        enabled = safePage < totalPages - 1,
                        modifier = Modifier.heightIn(min = 40.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Next", fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
    }

    // Individual Permissions Grant / Revoke Dialog
    userForPermissions?.let { target ->
        val roleEnum = AdminRole.fromString(target.role)
        val roleDefaults = RolePermissionPolicy.permissionsFor(roleEnum)
        var selectedCustom by remember(target) {
            mutableStateOf(RolePermissionPolicy.parseCustomPermissions(target.customPermissions))
        }
        var selectedRole by remember(target) { mutableStateOf(roleEnum) }
        val isProtectedRoot = target.username.equals("hj123", ignoreCase = true) ||
            target.username.equals("hj195344", ignoreCase = true) ||
            target.email.equals("hj195344@gmail.com", ignoreCase = true) ||
            target.email.equals("hj195345@gmail.com", ignoreCase = true)

        AlertDialog(
            onDismissRequest = { userForPermissions = null },
            title = { Text("Role & Permissions: @${target.username}", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Assigned Role:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AdminRole.entries.forEach { r ->
                            FilterChip(
                                selected = selectedRole == r,
                                enabled = !isProtectedRoot,
                                onClick = { selectedRole = r },
                                label = { Text(r.displayName, fontSize = 11.sp) }
                            )
                        }
                    }
                    HorizontalDivider()
                    Text(
                        "Grant or revoke individual permissions:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    val activeDefaults = RolePermissionPolicy.permissionsFor(selectedRole)
                    AdminPermission.entries.forEach { perm ->
                        val isInherited = activeDefaults.contains(perm)
                        val isCustom = selectedCustom.contains(perm)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isInherited) {
                                    selectedCustom = if (isCustom) selectedCustom - perm else selectedCustom + perm
                                }
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(perm.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (isInherited) "Included in ${selectedRole.displayName}"
                                    else if (isCustom) "Custom Permission Granted"
                                    else "Not Granted",
                                    fontSize = 10.sp,
                                    color = if (isInherited || isCustom) Color(0xFF059669) else Color(0xFF64748B)
                                )
                            }
                            Checkbox(
                                checked = isInherited || isCustom,
                                enabled = !isInherited,
                                onCheckedChange = { checked ->
                                    if (!isInherited) {
                                        selectedCustom = if (checked) selectedCustom + perm else selectedCustom - perm
                                    }
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedRole != roleEnum && !isProtectedRoot) {
                            adminViewModel.changeUserRole(target.id, selectedRole)
                        }
                        adminViewModel.updateUserCustomPermissions(target.id, selectedCustom)
                        userForPermissions = null
                    }
                ) {
                    Text("Save Role & Permissions")
                }
            },
            dismissButton = {
                TextButton(onClick = { userForPermissions = null }) { Text("Cancel") }
            }
        )
    }

    // Suspend / Block Reason Dialog
    userToSuspend?.let { target ->
        var suspendReason by remember { mutableStateOf("Security policy or telemetry violation") }
        AlertDialog(
            onDismissRequest = { userToSuspend = null },
            title = { Text("Block / Suspend @${target.username}?") },
            text = {
                Column {
                    Text("Blocked or suspended users are immediately prevented from logging into the NeuroGrip app.", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = suspendReason,
                        onValueChange = { suspendReason = it },
                        label = { Text("Reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.setUserStatus(target.id, AccountStatus.SUSPENDED, suspendReason)
                        userToSuspend = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Confirm Block")
                }
            },
            dismissButton = {
                TextButton(onClick = { userToSuspend = null }) { Text("Cancel") }
            }
        )
    }

    // Delete Confirmation Dialog
    userToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = { Text("Delete Account @${target.username}?") },
            text = {
                Text("This action permanently revokes access for ${target.name} (${target.email}) and logs a CRITICAL security audit event.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.deleteUser(target.id, hardDelete = true)
                        userToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Delete Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // User Detail & Edit Modal
    selectedUserForDetail?.let { u ->
        var editName by remember(u) { mutableStateOf(u.name) }
        var editEmail by remember(u) { mutableStateOf(u.email) }
        var editMobile by remember(u) { mutableStateOf(u.mobile) }
        var editPlan by remember(u) { mutableStateOf(u.subscriptionPlan) }
        var editNotes by remember(u) { mutableStateOf(u.notes) }
        var newPassword by remember(u) { mutableStateOf("") }
        val userLogins = loginHistory.filter { it.username.equals(u.username, ignoreCase = true) }

        AlertDialog(
            onDismissRequest = { selectedUserForDetail = null },
            title = { Text("User Profile: @${u.username}", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editEmail,
                        onValueChange = { editEmail = it },
                        label = { Text("Email Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editMobile,
                        onValueChange = { editMobile = it },
                        label = { Text("Mobile") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editPlan,
                        onValueChange = { editPlan = it },
                        label = { Text("Subscription Plan") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Reset Password (leave blank to keep)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editNotes,
                        onValueChange = { editNotes = it },
                        label = { Text("Admin Clinical/Security Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Recent Login History (${userLogins.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    userLogins.take(5).forEach { entry ->
                        Text(
                            "• ${formatAdminTimestamp(entry.timestamp)} - ${if (entry.success) "SUCCESS" else "FAILED (${entry.failureReason})"} [${entry.ipAddress}]",
                            fontSize = 11.sp,
                            color = if (entry.success) Color(0xFF059669) else Color(0xFFDC2626)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.updateUserProfile(
                            targetUserId = u.id,
                            name = editName,
                            email = editEmail,
                            mobile = editMobile,
                            subscriptionPlan = editPlan,
                            notes = editNotes,
                            newPassword = newPassword.ifBlank { null }
                        )
                        selectedUserForDetail = null
                    }
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedUserForDetail = null }) { Text("Close") }
            }
        )
    }

    // Create User Dialog
    if (showCreateUserDialog) {
        var newName by remember { mutableStateOf("") }
        var newUsername by remember { mutableStateOf("") }
        var newEmail by remember { mutableStateOf("") }
        var newPassword by remember { mutableStateOf("12345") }
        var newMobile by remember { mutableStateOf("+91 98000 00000") }
        var selectedRole by remember { mutableStateOf(AdminRole.USER) }

        AlertDialog(
            onDismissRequest = { showCreateUserDialog = false },
            title = { Text("Create User or Admin Account", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newUsername,
                        onValueChange = { newUsername = it },
                        label = { Text("Username (for login)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newEmail,
                        onValueChange = { newEmail = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Initial Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newMobile,
                        onValueChange = { newMobile = it },
                        label = { Text("Mobile") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Role:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AdminRole.entries.forEach { role ->
                            FilterChip(
                                selected = selectedRole == role,
                                onClick = { selectedRole = role },
                                label = { Text(role.displayName, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.createUser(
                            fullName = newName,
                            email = newEmail,
                            username = newUsername,
                            password = newPassword,
                            mobile = newMobile,
                            role = selectedRole
                        ) { success, _ ->
                            if (success) showCreateUserDialog = false
                        }
                    }
                ) {
                    Text("Create Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateUserDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun AdminNotificationsTab(
    notifications: List<NotificationEntity>,
    users: List<User>,
    adminViewModel: AdminViewModel
) {
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var audience by remember { mutableStateOf("ALL_USERS") }
    var category by remember { mutableStateOf("SYSTEM") }
    var scheduleMinutes by remember { mutableStateOf("0") }
    var notificationToDelete by remember { mutableStateOf<NotificationEntity?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Push Notification & FCM Broadcast Composer",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        "Dispatches through the backend FCM service and delivers live to the Android Notifications screen.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Notification Title") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("notif_title_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("Notification Body Message") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("notif_message_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Target Audience Topic:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("ALL_USERS", "PRO_USERS", "ADMINS").forEach { target ->
                            FilterChip(
                                selected = audience == target,
                                onClick = { audience = target },
                                label = { Text(target, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Category:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("SYSTEM", "AI", "DEVICE", "THERAPY", "ALERT").forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = scheduleMinutes,
                        onValueChange = { scheduleMinutes = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Schedule Delay (Minutes, 0 = Immediate)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Live Preview Card
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Live Android Handset Preview:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = Color(0xFF2563EB))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    title.ifBlank { "Your Notification Title" },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    message.ifBlank { "Your notification message body will appear here..." },
                                    fontSize = 12.sp,
                                    color = Color(0xFF475569)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                adminViewModel.dispatchNotification(
                                    title = title,
                                    message = message,
                                    audience = audience,
                                    category = category,
                                    isTest = true
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("send_test_notif_button")
                        ) {
                            Text("Send Test")
                        }
                        Button(
                            onClick = {
                                val delayMins = scheduleMinutes.toIntOrNull() ?: 0
                                adminViewModel.dispatchNotification(
                                    title = title,
                                    message = message,
                                    audience = audience,
                                    category = category,
                                    isTest = false,
                                    isScheduled = delayMins > 0,
                                    scheduledOffsetMins = delayMins
                                )
                                title = ""
                                message = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("broadcast_notif_button")
                        ) {
                            Text(if ((scheduleMinutes.toIntOrNull() ?: 0) > 0) "Schedule" else "Broadcast Now")
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Previously Sent Notifications (${notifications.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color(0xFF0F172A)
            )
        }

        items(notifications, key = { it.id }) { item ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("notif_card_${item.id}")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            item.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF0F172A),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = Color(0xFFECFDF5),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    item.deliveryStatus,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF059669),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            OutlinedButton(
                                onClick = { notificationToDelete = item },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                border = BorderStroke(1.dp, Color(0xFFFECACA)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .heightIn(min = 36.dp)
                                    .testTag("delete_notif_${item.id}")
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Delete Notification",
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(item.message, fontSize = 13.sp, color = Color(0xFF475569))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Audience: ${item.targetAudience} (${item.recipientCount} recipients) • By @${item.sentBy} • ${formatAdminTimestamp(item.timestamp)}",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }

    // Requirement 11: Confirmation Dialog before deleting notification from UI/Room & Firebase
    notificationToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { notificationToDelete = null },
            title = { Text("Delete Notification?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to permanently delete '${target.title}'? This will immediately remove the notification from the UI, local database, and Firebase Firestore."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.deleteNotification(target.id)
                        notificationToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("confirm_delete_notif_button")
                ) {
                    Text("Delete Permanently", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { notificationToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
