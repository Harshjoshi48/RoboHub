package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemConfigScreen(
    adminViewModel: AdminViewModel
) {
    val savedConfig by adminViewModel.systemConfig.collectAsStateWithLifecycle()
    val configHistory by adminViewModel.configHistory.collectAsStateWithLifecycle()
    val auditLogs by adminViewModel.auditLogs.collectAsStateWithLifecycle()
    val users by adminViewModel.users.collectAsStateWithLifecycle()
    val isLoading by adminViewModel.isLoading.collectAsStateWithLifecycle()
    val errorMsg by adminViewModel.systemConfigError.collectAsStateWithLifecycle()

    val effectiveRole = adminViewModel.effectiveRole()
    val effectiveUsername = adminViewModel.effectiveUsername()
    val isSuperAdmin = RolePermissionPolicy.hasPermission(effectiveRole, AdminPermission.MANAGE_SYSTEM_CONFIG)

    // Editable draft state initialized from savedConfig
    var otpTtlStr by remember(savedConfig) { mutableStateOf(savedConfig.otpTtlSeconds.toString()) }
    var otpMaxSendsStr by remember(savedConfig) { mutableStateOf(savedConfig.otpMaxSends.toString()) }
    var otpWindowStr by remember(savedConfig) { mutableStateOf(savedConfig.otpWindowSeconds.toString()) }
    var sessionTtlStr by remember(savedConfig) { mutableStateOf(savedConfig.sessionTtlMinutes.toString()) }
    var rateLimitStr by remember(savedConfig) { mutableStateOf(savedConfig.rateLimitPerMinute.toString()) }
    var idleLockoutStr by remember(savedConfig) { mutableStateOf(savedConfig.idleLockoutMinutes.toString()) }
    var require2fa by remember(savedConfig) { mutableStateOf(savedConfig.requireTwoFactorForAdmins) }
    var fcmEnabled by remember(savedConfig) { mutableStateOf(savedConfig.fcmMasterEnabled) }
    var fcmTopic by remember(savedConfig) { mutableStateOf(savedConfig.fcmDefaultTopic) }
    var highPriorityAlerts by remember(savedConfig) { mutableStateOf(savedConfig.highPriorityClinicalAlerts) }
    var preserveDefaultLayout by remember(savedConfig) { mutableStateOf(savedConfig.preserveDefaultScreenLayout) }
    var maintenanceMode by remember(savedConfig) { mutableStateOf(savedConfig.maintenanceMode) }
    var maintenanceMessage by remember(savedConfig) { mutableStateOf(savedConfig.maintenanceMessage) }
    var minVersion by remember(savedConfig) { mutableStateOf(savedConfig.minSupportedVersionName) }
    var latestVersion by remember(savedConfig) { mutableStateOf(savedConfig.recommendedVersionName) }
    var forceUpdate by remember(savedConfig) { mutableStateOf(savedConfig.forceUpdate) }
    var changeNote by remember { mutableStateOf("") }

    var showSaveConfirmDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showCredentialRotationModal by remember { mutableStateOf(false) }
    var rollbackTarget by remember { mutableStateOf<ConfigHistoryEntity?>(null) }

    val draftCandidate = remember(
        otpTtlStr, otpMaxSendsStr, otpWindowStr, sessionTtlStr, rateLimitStr,
        idleLockoutStr, require2fa, fcmEnabled, fcmTopic, highPriorityAlerts,
        preserveDefaultLayout, maintenanceMode, maintenanceMessage, minVersion, latestVersion, forceUpdate
    ) {
        SystemConfigRuntimeDto(
            configKey = "runtime_settings",
            otpTtlSeconds = otpTtlStr.toIntOrNull() ?: -1,
            otpMaxSends = otpMaxSendsStr.toIntOrNull() ?: -1,
            otpWindowSeconds = otpWindowStr.toIntOrNull() ?: -1,
            sessionTtlMinutes = sessionTtlStr.toIntOrNull() ?: -1,
            rateLimitPerMinute = rateLimitStr.toIntOrNull() ?: -1,
            requireTwoFactorForAdmins = require2fa,
            idleLockoutMinutes = idleLockoutStr.toIntOrNull() ?: -1,
            fcmMasterEnabled = fcmEnabled,
            fcmDefaultTopic = fcmTopic,
            highPriorityClinicalAlerts = highPriorityAlerts,
            preserveDefaultScreenLayout = preserveDefaultLayout,
            maintenanceMode = maintenanceMode,
            maintenanceMessage = maintenanceMessage,
            minSupportedVersionName = minVersion,
            recommendedVersionName = latestVersion,
            forceUpdate = forceUpdate,
            configVersion = savedConfig.configVersion,
            updatedBy = effectiveUsername
        )
    }

    val liveValidationError = remember(draftCandidate) {
        adminViewModel.validateDraftSystemConfig(draftCandidate)
    }

    val envDiagnostics = remember(savedConfig) {
        adminViewModel.getEnvironmentDiagnostics()
    }

    val superAdminAccount = remember(users, effectiveUsername) {
        users.find { it.username.equals(effectiveUsername, ignoreCase = true) }
            ?: users.find { it.username.equals("hj123", ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("system_config_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header & Role Enforcement Banner (Clean Light High-Contrast Card)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSuperAdmin) Color(0xFFEFF6FF) else Color(0xFFFEF2F2)
                ),
                border = BorderStroke(
                    1.5.dp,
                    if (isSuperAdmin) Color(0xFF93C5FD) else Color(0xFFFCA5A5)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp)
                        ) {
                            Surface(
                                color = if (isSuperAdmin) Color(0xFFDBEAFE) else Color(0xFFFEE2E2),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isSuperAdmin) Icons.Filled.AdminPanelSettings else Icons.Filled.Lock,
                                        contentDescription = "Super Admin Security",
                                        tint = if (isSuperAdmin) Color(0xFF1D4ED8) else Color(0xFFDC2626),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "System Configuration",
                                    color = Color(0xFF0F172A),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Active Version v${savedConfig.configVersion} • Updated by @${savedConfig.updatedBy}",
                                    color = Color(0xFF475569),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Surface(
                            color = if (isSuperAdmin) Color(0xFF2563EB) else Color(0xFFDC2626),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (isSuperAdmin) "SUPER ADMIN" else "READ-ONLY",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = if (isSuperAdmin) Color(0xFFBFDBFE) else Color(0xFFFECACA))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isSuperAdmin) {
                            "Default screen layout remains unchanged until you explicitly save a modification. Non-secret runtime parameters are synced to Cloud Firestore (system_config/runtime_settings)."
                        } else {
                            "RBAC Protection Active: Only SUPER_ADMIN (permission MANAGE_SYSTEM_CONFIG) can modify system configuration. Fields are locked in read-only mode."
                        },
                        color = if (isSuperAdmin) Color(0xFF1E3A8A) else Color(0xFF991B1B),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        // Loading & Error / Validation Banner
        if (isLoading) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        if (errorMsg != null || liveValidationError != null) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    border = BorderStroke(1.dp, Color(0xFFF87171)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = Color(0xFFDC2626))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = liveValidationError ?: errorMsg.orEmpty(),
                            color = Color(0xFF991B1B),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        if (errorMsg != null) {
                            IconButton(onClick = { adminViewModel.clearSystemConfigError() }) {
                                Icon(Icons.Filled.Close, contentDescription = "Dismiss error", tint = Color(0xFF991B1B))
                            }
                        }
                    }
                }
            }
        }

        // SECTION 1: Firebase & Database Status
        item {
            ConfigSectionCard(
                title = "1. Firebase & Cloud Firestore Status",
                subtitle = "Live provisioning status, named database binding, and collection security posture",
                icon = Icons.Outlined.CloudDone,
                accentColor = Color(0xFF059669)
            ) {
                StatusKeyValueRow("Firebase Project ID", "gen-lang-client-0929832946", isLocked = true)
                StatusKeyValueRow(
                    "Named Firestore Database ID",
                    "ai-studio-android-neuropul-c40fab35-e98a-46c6-850b-96b0e10b6910",
                    isLocked = true
                )
                StatusKeyValueRow("Runtime Config Document", "system_config/runtime_settings (Synced)", isLocked = false)
                StatusKeyValueRow(
                    "Collection Security Allowlist",
                    "${FirestoreCollectionAllowlist.ALLOWLISTED_COLLECTIONS.size} Allowlisted • ${FirestoreCollectionAllowlist.PROTECTED_SYSTEM_COLLECTIONS.size} Protected System Collections",
                    isLocked = false
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Infrastructure Guard: Firebase Project IDs, Database IDs, and Backend Base URLs are bound at build/provisioning time and cannot be altered through generic runtime forms.",
                        fontSize = 11.sp,
                        color = Color(0xFF475569),
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }

        // SECTION 2: Admin Account Settings
        item {
            ConfigSectionCard(
                title = "2. Super Admin Account Settings",
                subtitle = "Authenticated administrator identity & zero-plaintext credential rotation",
                icon = Icons.Outlined.ManageAccounts,
                accentColor = Color(0xFF2563EB)
            ) {
                StatusKeyValueRow("Primary Super Admin Username", "@${superAdminAccount?.username ?: "hj123"}", isLocked = true)
                StatusKeyValueRow("Display Name", superAdminAccount?.name ?: "Harsh Joshi (Super Admin)", isLocked = false)
                StatusKeyValueRow("Verified Admin Email", superAdminAccount?.email ?: "hj195344@gmail.com", isLocked = false)
                StatusKeyValueRow("Stored Password Hash", "•••••••••••••••••••••••••••••••• (SHA-256 Salted)", isLocked = true)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { showCredentialRotationModal = true },
                    enabled = isSuperAdmin,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("rotate_admin_credentials_button")
                ) {
                    Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Rotate Super Admin Password / Update Profile", textAlign = TextAlign.Center)
                }
            }
        }

        // SECTION 3: Session & Security Settings
        item {
            ConfigSectionCard(
                title = "3. Session & Security Settings",
                subtitle = "Server-enforced session token TTL, idle lockout, and staff 2FA enforcement",
                icon = Icons.Outlined.Security,
                accentColor = Color(0xFF7C3AED)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = sessionTtlStr,
                        onValueChange = { sessionTtlStr = it.filter { ch -> ch.isDigit() } },
                        enabled = isSuperAdmin,
                        label = { Text("SESSION_TTL_MINUTES") },
                        supportingText = { Text("Default: 30 (5–1440m)") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_session_ttl_minutes")
                    )
                    OutlinedTextField(
                        value = idleLockoutStr,
                        onValueChange = { idleLockoutStr = it.filter { ch -> ch.isDigit() } },
                        enabled = isSuperAdmin,
                        label = { Text("Idle Lockout (Mins)") },
                        supportingText = { Text("Default: 15 (1–240m)") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_idle_lockout_minutes")
                    )
                }
                ConfigSwitchRow(
                    title = "Require 2FA for All Admin Roles",
                    subtitle = "Mandates second-factor verification for ADMIN, EDITOR, SUPPORT, and ANALYST accounts",
                    checked = require2fa,
                    enabled = isSuperAdmin,
                    onCheckedChange = { require2fa = it }
                )
            }
        }

        // SECTION 4: OTP Configuration
        item {
            ConfigSectionCard(
                title = "4. Dynamic OTP Configuration",
                subtitle = "Server-enforced One-Time Password expiration, max send attempts, and sliding window",
                icon = Icons.Outlined.Pin,
                accentColor = Color(0xFFD97706)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = otpTtlStr,
                        onValueChange = { otpTtlStr = it.filter { ch -> ch.isDigit() } },
                        enabled = isSuperAdmin,
                        label = { Text("OTP_TTL_SECONDS") },
                        supportingText = { Text("Def: 60 (15–600s)") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_otp_ttl_seconds")
                    )
                    OutlinedTextField(
                        value = otpMaxSendsStr,
                        onValueChange = { otpMaxSendsStr = it.filter { ch -> ch.isDigit() } },
                        enabled = isSuperAdmin,
                        label = { Text("OTP_MAX_SENDS") },
                        supportingText = { Text("Def: 3 (1–10)") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_otp_max_sends")
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = otpWindowStr,
                    onValueChange = { otpWindowStr = it.filter { ch -> ch.isDigit() } },
                    enabled = isSuperAdmin,
                    label = { Text("OTP_WINDOW_SECONDS (Sliding Rate Window)") },
                    supportingText = { Text("Default: 600 seconds (valid range: 60–3600s, must be >= OTP_TTL_SECONDS)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_otp_window_seconds")
                )
                Spacer(modifier = Modifier.height(6.dp))
                FilledTonalButton(
                    onClick = { adminViewModel.testOtpDispatch(effectiveUsername) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("test_otp_policy_button")
                ) {
                    Icon(Icons.Outlined.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Test Active OTP Window Enforcement (${savedConfig.otpMaxSends} sends / ${savedConfig.otpWindowSeconds}s, TTL ${savedConfig.otpTtlSeconds}s)",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // SECTION 5: API Rate Limits
        item {
            ConfigSectionCard(
                title = "5. API Rate Limits",
                subtitle = "Per-minute request throttling enforced across backend workers and repository calls",
                icon = Icons.Outlined.Speed,
                accentColor = Color(0xFF0284C7)
            ) {
                OutlinedTextField(
                    value = rateLimitStr,
                    onValueChange = { rateLimitStr = it.filter { ch -> ch.isDigit() } },
                    enabled = isSuperAdmin,
                    label = { Text("RATE_LIMIT_PER_MINUTE") },
                    supportingText = {
                        Text("Default: 60 req/min (valid range: 10–1000). Current 60s window usage: ${SecureSessionManager.currentRateWindowCount()} reqs.")
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_rate_limit_per_minute")
                )
            }
        }

        // SECTION 6: Notification Settings
        item {
            ConfigSectionCard(
                title = "6. Push Notification & FCM Settings",
                subtitle = "Firebase Cloud Messaging dispatch controls and default broadcast audience",
                icon = Icons.Outlined.NotificationsActive,
                accentColor = Color(0xFF4F46E5)
            ) {
                ConfigSwitchRow(
                    title = "Enable Firebase Cloud Messaging (FCM) Dispatch",
                    subtitle = "Master switch for outbound push broadcasts and scheduled alerts",
                    checked = fcmEnabled,
                    enabled = isSuperAdmin,
                    onCheckedChange = { fcmEnabled = it }
                )
                ConfigSwitchRow(
                    title = "High-Priority Clinical Telemetry Alerts",
                    subtitle = "Elevates robotic hand thermal/grip cutoff alerts to high-importance channel",
                    checked = highPriorityAlerts,
                    enabled = isSuperAdmin,
                    onCheckedChange = { highPriorityAlerts = it }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text("Default Broadcast Topic:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("ALL_USERS", "PRO_USERS", "ADMINS").forEach { topic ->
                        FilterChip(
                            selected = fcmTopic == topic,
                            onClick = { if (isSuperAdmin) fcmTopic = topic },
                            enabled = isSuperAdmin,
                            label = { Text(topic, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        // SECTION 7: App Maintenance & Remote Configuration
        item {
            ConfigSectionCard(
                title = "7. App Maintenance & Default Layout Lock",
                subtitle = "Default screen layout preservation, maintenance gate, and minimum version policy",
                icon = Icons.Outlined.BuildCircle,
                accentColor = Color(0xFFDC2626)
            ) {
                ConfigSwitchRow(
                    title = "Preserve Default Screen Layout Until Admin Changes",
                    subtitle = "Keeps existing default app layout and navigation unchanged unless explicitly modified by Admin",
                    checked = preserveDefaultLayout,
                    enabled = isSuperAdmin,
                    onCheckedChange = { preserveDefaultLayout = it }
                )
                ConfigSwitchRow(
                    title = "Maintenance Mode Gate",
                    subtitle = "Blocks standard USER logins with HTTP 503 while allowing SUPER_ADMIN access",
                    checked = maintenanceMode,
                    enabled = isSuperAdmin,
                    onCheckedChange = { maintenanceMode = it }
                )
                ConfigSwitchRow(
                    title = "Force Minimum Version Upgrade",
                    subtitle = "Requires clients below Min Supported Version to update immediately",
                    checked = forceUpdate,
                    enabled = isSuperAdmin,
                    onCheckedChange = { forceUpdate = it }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = minVersion,
                        onValueChange = { minVersion = it },
                        enabled = isSuperAdmin,
                        label = { Text("Min Supported Version") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = latestVersion,
                        onValueChange = { latestVersion = it },
                        enabled = isSuperAdmin,
                        label = { Text("Recommended Version") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = maintenanceMessage,
                    onValueChange = { maintenanceMessage = it },
                    enabled = isSuperAdmin,
                    label = { Text("Maintenance Banner Message") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Save & Reset Action Bar
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = changeNote,
                        onValueChange = { changeNote = it },
                        enabled = isSuperAdmin,
                        label = { Text("Audit Change Summary (Optional)") },
                        placeholder = { Text("e.g., Tightened OTP window and rate limit for production") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_system_config_change_note")
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showResetConfirmDialog = true },
                            enabled = isSuperAdmin && !isLoading,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("reset_system_config_defaults_button")
                        ) {
                            Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset Defaults", textAlign = TextAlign.Center)
                        }
                        Button(
                            onClick = { showSaveConfirmDialog = true },
                            enabled = isSuperAdmin && !isLoading && liveValidationError == null,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("save_system_config_button")
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save & Apply", textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }

        // SECTION 8 & 9: Environment Diagnostics & Configuration History / Audit Logs
        item {
            SystemConfigDiagnosticsAndHistorySection(
                envDiagnostics = envDiagnostics,
                configHistory = configHistory,
                auditLogs = auditLogs,
                isSuperAdmin = isSuperAdmin,
                onRequestRollback = { historyItem -> rollbackTarget = historyItem }
            )
        }
    }

    // Confirmation Dialog: Save & Apply
    if (showSaveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSaveConfirmDialog = false },
            title = { Text("Apply System Configuration v${savedConfig.configVersion + 1}?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "The Python backend and Cloud Firestore (`system_config/runtime_settings`) will validate and enforce these settings immediately on all subsequent requests:",
                        fontSize = 13.sp
                    )
                    Text("• OTP_TTL_SECONDS = ${draftCandidate.otpTtlSeconds}s | OTP_MAX_SENDS = ${draftCandidate.otpMaxSends} | OTP_WINDOW_SECONDS = ${draftCandidate.otpWindowSeconds}s", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Text("• SESSION_TTL_MINUTES = ${draftCandidate.sessionTtlMinutes}m | RATE_LIMIT_PER_MINUTE = ${draftCandidate.rateLimitPerMinute}/m", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Text("• Maintenance Mode = ${draftCandidate.maintenanceMode} | Preserve Default Layout = ${draftCandidate.preserveDefaultScreenLayout}", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveConfirmDialog = false
                        adminViewModel.saveSystemConfiguration(draftCandidate, changeNote) { ok ->
                            if (ok) changeNote = ""
                        }
                    },
                    modifier = Modifier.testTag("confirm_save_system_config_button")
                ) {
                    Text("Confirm & Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Confirmation Dialog: Reset to Validated Defaults
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Restore Validated Defaults?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This will reset OTP_TTL_SECONDS=60, OTP_MAX_SENDS=3, OTP_WINDOW_SECONDS=600, SESSION_TTL_MINUTES=30, RATE_LIMIT_PER_MINUTE=60, and MaintenanceMode=false.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        val defaults = SystemConfigRuntimeDto(
                            configVersion = savedConfig.configVersion,
                            updatedBy = effectiveUsername
                        )
                        adminViewModel.saveSystemConfiguration(defaults, "Restored validated baseline defaults")
                    }
                ) {
                    Text("Restore Defaults")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Confirmation Dialog: Rollback Version
    rollbackTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { rollbackTarget = null },
            title = { Text("Rollback to Snapshot v${target.configVersion}?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This will restore all runtime settings from v${target.configVersion} (${target.summary}) and record an immutable rollback event in audit_logs.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val item = target
                        rollbackTarget = null
                        adminViewModel.rollbackSystemConfiguration(item)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    modifier = Modifier.testTag("confirm_rollback_button")
                ) {
                    Text("Confirm Rollback")
                }
            },
            dismissButton = {
                TextButton(onClick = { rollbackTarget = null }) { Text("Cancel") }
            }
        )
    }

    // Modal: Safe Super Admin Credential Rotation
    if (showCredentialRotationModal) {
        var currentPass by remember { mutableStateOf("") }
        var newPass by remember { mutableStateOf("") }
        var editDisplayName by remember { mutableStateOf(superAdminAccount?.name ?: "Harsh Joshi (Super Admin)") }
        var editEmail by remember { mutableStateOf(superAdminAccount?.email ?: "hj195344@gmail.com") }

        AlertDialog(
            onDismissRequest = { showCredentialRotationModal = false },
            title = { Text("Super Admin Credential Rotation", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Passwords are salted and SHA-256 hashed on the backend. Plaintext credentials are never stored in Firestore or exposed in logs.",
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )
                    OutlinedTextField(
                        value = currentPass,
                        onValueChange = { currentPass = it },
                        label = { Text("Current Super Admin Password *") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it },
                        label = { Text("New Password (leave blank to keep current)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editDisplayName,
                        onValueChange = { editDisplayName = it },
                        label = { Text("Admin Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editEmail,
                        onValueChange = { editEmail = it },
                        label = { Text("Admin Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.rotateSuperAdminCredentials(
                            currentPassword = currentPass,
                            newPassword = newPass,
                            displayName = editDisplayName,
                            email = editEmail
                        ) { ok ->
                            if (ok) showCredentialRotationModal = false
                        }
                    },
                    enabled = currentPass.isNotBlank()
                ) {
                    Text("Verify & Rotate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCredentialRotationModal = false }) { Text("Cancel") }
            }
        )
    }
}
