package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun AdminRemoteConfigTab(
    remoteConfig: RemoteConfigEntity,
    configHistory: List<ConfigHistoryEntity>,
    adminViewModel: AdminViewModel
) {
    var draft by remember(remoteConfig) { mutableStateOf(remoteConfig) }
    var changeSummary by remember { mutableStateOf("") }

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Remote App Control & Feature Flags",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                "Active Version: v${remoteConfig.configVersion} • Updated by @${remoteConfig.updatedBy}",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Maintenance Mode & Availability", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Maintenance Mode", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Blocks standard user logins while allowing admins", fontSize = 11.sp, color = Color(0xFF64748B))
                        }
                        Switch(
                            checked = draft.maintenanceMode,
                            onCheckedChange = { draft = draft.copy(maintenanceMode = it) },
                            modifier = Modifier.testTag("switch_maintenance_mode")
                        )
                    }
                    OutlinedTextField(
                        value = draft.maintenanceMessage,
                        onValueChange = { draft = draft.copy(maintenanceMessage = it) },
                        label = { Text("Maintenance Banner / Blocking Message") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Native Feature Toggles", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
                    ConfigToggleRow("AI Rehab Assistant Module", draft.featureAiAssistant) {
                        draft = draft.copy(featureAiAssistant = it)
                    }
                    ConfigToggleRow("Robotic Hand Servo Control", draft.featureHandControl) {
                        draft = draft.copy(featureHandControl = it)
                    }
                    ConfigToggleRow("Gesture Studio Customizer", draft.featureGestureStudio) {
                        draft = draft.copy(featureGestureStudio = it)
                    }
                    ConfigToggleRow("Live EEG/EMG Telemetry Stream", draft.featureTelemetryStream) {
                        draft = draft.copy(featureTelemetryStream = it)
                    }
                    ConfigToggleRow("ESP32 OTA Firmware Updates", draft.featureOtaUpdates) {
                        draft = draft.copy(featureOtaUpdates = it)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("App Version & Force Update Gate", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = draft.minSupportedVersionName,
                            onValueChange = { draft = draft.copy(minSupportedVersionName = it) },
                            label = { Text("Min Supported Ver") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = draft.recommendedVersionName,
                            onValueChange = { draft = draft.copy(recommendedVersionName = it) },
                            label = { Text("Recommended Ver") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    ConfigToggleRow("Force Immediate App Update Gate", draft.forceUpdate) {
                        draft = draft.copy(forceUpdate = it)
                    }
                    OutlinedTextField(
                        value = draft.releaseNotes,
                        onValueChange = { draft = draft.copy(releaseNotes = it) },
                        label = { Text("Release Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Hardware & Theme Parameters", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = draft.defaultEsp32Ip,
                            onValueChange = { draft = draft.copy(defaultEsp32Ip = it) },
                            label = { Text("Default ESP32 IP") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = draft.primaryThemeHex,
                            onValueChange = { draft = draft.copy(primaryThemeHex = it) },
                            label = { Text("Primary Accent Hex") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = changeSummary,
                        onValueChange = { changeSummary = it },
                        label = { Text("Commit / Change Summary") },
                        placeholder = { Text("e.g., Enabled maintenance window or toggled AI module") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            adminViewModel.publishRemoteConfig(
                                draft,
                                changeSummary.ifBlank { "Updated remote configuration via Admin Console" }
                            )
                            changeSummary = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("publish_remote_config_button")
                    ) {
                        Icon(Icons.Outlined.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Publish Remote Configuration", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        item {
            Text(
                "Configuration Version History & Rollback (${configHistory.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color(0xFF0F172A)
            )
        }

        items(configHistory, key = { it.id }) { item ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Version v${item.configVersion} • ${item.summary}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            "Published by @${item.publishedBy} • ${formatAdminTimestamp(item.timestamp)}",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    OutlinedButton(
                        onClick = { adminViewModel.rollbackConfig(item) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Restore", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ConfigToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = Color(0xFF1E293B), modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun AdminBannersTab(
    banners: List<BannerContentEntity>,
    adminViewModel: AdminViewModel
) {
    AdminBannerManagerScreen(
        banners = banners,
        adminViewModel = adminViewModel
    )
}

private data class AnalyticsOnlineSessionItem(
    val timestamp: Long,
    val dateString: String,
    val username: String,
    val fullName: String,
    val email: String,
    val role: String,
    val deviceInfo: String,
    val ipAddress: String,
    val statusLabel: String
)

private data class AnalyticsUserActionItem(
    val timestamp: Long,
    val dateString: String,
    val username: String,
    val fullName: String,
    val actionType: String,
    val details: String
)

private fun parseDateInputBounds(dateStr: String, isEndOfDay: Boolean): Long? {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val parsed = sdf.parse(dateStr.trim()) ?: return null
        val cal = Calendar.getInstance().apply {
            time = parsed
            if (isEndOfDay) {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            } else {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }
        cal.timeInMillis
    } catch (_: Exception) {
        null
    }
}

private fun formatDateYmd(millis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))
}

private fun formatDateTimeReadable(millis: Long): String {
    if (millis <= 0L) return "N/A"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(millis))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminAnalyticsTab(
    users: List<User>,
    subscriptions: List<SubscriptionRecordEntity>,
    notifications: List<NotificationEntity>,
    tickets: List<SupportTicketEntity>,
    loginHistory: List<LoginHistoryEntity>,
    auditLogs: List<AuditLogEntity> = emptyList(),
    adminViewModel: AdminViewModel? = null
) {
    val context = LocalContext.current
    val now = remember { System.currentTimeMillis() }
    var selectedPreset by remember { mutableStateOf("LAST_30_DAYS") }
    var fromDateInput by remember { mutableStateOf(formatDateYmd(now - 30L * 24 * 60 * 60 * 1000)) }
    var toDateInput by remember { mutableStateOf(formatDateYmd(now)) }
    var filterUserName by remember { mutableStateOf("") }
    var filterUserEmail by remember { mutableStateOf("") }
    var selectedReportType by remember { mutableStateOf("PDF") }
    var lastDownloadedFileInfo by remember { mutableStateOf<String?>(null) }
    var showReportPreviewDialog by remember { mutableStateOf(false) }

    // Requirement 8: Search Generated User Reports state (logs shown ONLY after searching & selecting a report)
    val persistedReportsState = adminViewModel?.generatedReports?.collectAsState()
    val allGeneratedReports = persistedReportsState?.value ?: remember { GeneratedReportStore.loadAllReports(context) }
    var generatedReportUserQuery by remember { mutableStateOf("") }
    var generatedReportDateQuery by remember { mutableStateOf("") }
    var hasSearchedGeneratedReports by remember { mutableStateOf(false) }
    var selectedGeneratedReport by remember { mutableStateOf<GeneratedReportRecord?>(null) }

    // Requirement 10: Dedicated User Login & Activity Search state
    var userLoginSearchQuery by remember { mutableStateOf("") }

    val startMillis = remember(fromDateInput, selectedPreset) {
        if (selectedPreset == "ALL_TIME") 0L
        else parseDateInputBounds(fromDateInput, isEndOfDay = false) ?: (now - 30L * 24 * 60 * 60 * 1000)
    }
    val endMillis = remember(toDateInput, selectedPreset) {
        if (selectedPreset == "ALL_TIME") Long.MAX_VALUE
        else parseDateInputBounds(toDateInput, isEndOfDay = true) ?: Long.MAX_VALUE
    }

    val userByUsername = remember(users) {
        users.associateBy { it.username.lowercase(Locale.US) }
    }

    fun matchesSelectedUserFilter(username: String, fullName: String, email: String): Boolean {
        val nameQ = filterUserName.trim().lowercase(Locale.US)
        val emailQ = filterUserEmail.trim().lowercase(Locale.US)
        val nameOk = nameQ.isBlank() ||
            username.lowercase(Locale.US).contains(nameQ) ||
            fullName.lowercase(Locale.US).contains(nameQ)
        val emailOk = emailQ.isBlank() || email.lowercase(Locale.US).contains(emailQ)
        return nameOk && emailOk
    }

    // 1. Online Users in Date Range (filtered by selected user name / email if specified)
    val onlineSessionsInRange = remember(users, loginHistory, startMillis, endMillis, filterUserName, filterUserEmail) {
        val list = mutableListOf<AnalyticsOnlineSessionItem>()
        loginHistory.filter { it.timestamp in startMillis..endMillis }.forEach { entry ->
            val u = userByUsername[entry.username.lowercase(Locale.US)]
            val fullName = u?.name?.ifBlank { entry.username } ?: entry.username
            val email = u?.email ?: "N/A"
            if (matchesSelectedUserFilter(entry.username, fullName, email)) {
                list.add(
                    AnalyticsOnlineSessionItem(
                        timestamp = entry.timestamp,
                        dateString = formatDateTimeReadable(entry.timestamp),
                        username = entry.username,
                        fullName = fullName,
                        email = email,
                        role = u?.role ?: entry.role,
                        deviceInfo = entry.deviceModel,
                        ipAddress = entry.ipAddress,
                        statusLabel = if (entry.success) "ONLINE / LOGIN SUCCESS" else "FAILED LOGIN ATTEMPT"
                    )
                )
            }
        }
        users.forEach { u ->
            val lastActive = if (u.lastLoginAt > 0L) u.lastLoginAt else u.timestamp
            val fullName = u.name.ifBlank { u.username }
            if (lastActive in startMillis..endMillis &&
                matchesSelectedUserFilter(u.username, fullName, u.email) &&
                list.none {
                    it.username.equals(u.username, ignoreCase = true) &&
                        kotlin.math.abs(it.timestamp - lastActive) < 60_000L
                }
            ) {
                list.add(
                    AnalyticsOnlineSessionItem(
                        timestamp = lastActive,
                        dateString = formatDateTimeReadable(lastActive),
                        username = u.username,
                        fullName = fullName,
                        email = u.email,
                        role = u.role,
                        deviceInfo = "Android App (${u.deviceId})",
                        ipAddress = "Verified Client",
                        statusLabel = "ACTIVE SESSION"
                    )
                )
            }
        }
        list.sortedByDescending { it.timestamp }
    }

    val uniqueOnlineUsernames = remember(onlineSessionsInRange) {
        onlineSessionsInRange.map { it.username.lowercase(Locale.US) }.distinct()
    }

    // 2. What Users Did in the Application (filtered by selected user name / email & date range)
    val userActionsInRange = remember(auditLogs, tickets, loginHistory, users, startMillis, endMillis, filterUserName, filterUserEmail) {
        val actions = mutableListOf<AnalyticsUserActionItem>()
        auditLogs.filter { it.timestamp in startMillis..endMillis }.forEach { log ->
            val actor = log.actorUsername.ifBlank { "system" }
            val u = userByUsername[actor.lowercase(Locale.US)]
            val fullName = u?.name?.ifBlank { actor } ?: actor
            val email = u?.email ?: ""
            if (matchesSelectedUserFilter(actor, fullName, email)) {
                actions.add(
                    AnalyticsUserActionItem(
                        timestamp = log.timestamp,
                        dateString = formatDateTimeReadable(log.timestamp),
                        username = actor,
                        fullName = fullName,
                        actionType = log.actionType,
                        details = "${log.details} (Target: ${log.targetEntity})"
                    )
                )
            }
        }
        tickets.filter { it.createdAt in startMillis..endMillis }.forEach { ticket ->
            val u = userByUsername[ticket.username.lowercase(Locale.US)]
            val fullName = u?.name?.ifBlank { ticket.username } ?: ticket.username
            val email = u?.email ?: ""
            if (matchesSelectedUserFilter(ticket.username, fullName, email)) {
                actions.add(
                    AnalyticsUserActionItem(
                        timestamp = ticket.createdAt,
                        dateString = formatDateTimeReadable(ticket.createdAt),
                        username = ticket.username,
                        fullName = fullName,
                        actionType = "FEEDBACK_SUBMITTED",
                        details = "[${ticket.category}] ${ticket.subject}: ${ticket.message}"
                    )
                )
            }
        }
        loginHistory.filter { it.timestamp in startMillis..endMillis }.forEach { login ->
            val u = userByUsername[login.username.lowercase(Locale.US)]
            val fullName = u?.name?.ifBlank { login.username } ?: login.username
            val email = u?.email ?: ""
            if (matchesSelectedUserFilter(login.username, fullName, email)) {
                actions.add(
                    AnalyticsUserActionItem(
                        timestamp = login.timestamp,
                        dateString = formatDateTimeReadable(login.timestamp),
                        username = login.username,
                        fullName = fullName,
                        actionType = if (login.success) "USER_LOGIN" else "LOGIN_FAILED",
                        details = "${login.failureReason.ifBlank { "Authenticated in app" }} • Device: ${login.deviceModel}"
                    )
                )
            }
        }
        actions.sortedByDescending { it.timestamp }
    }

    // 3. New Users Registered in Date Range
    val newUsersInRange = remember(users, startMillis, endMillis, filterUserName, filterUserEmail) {
        users.filter {
            it.timestamp in startMillis..endMillis &&
                matchesSelectedUserFilter(it.username, it.name.ifBlank { it.username }, it.email)
        }.sortedByDescending { it.timestamp }
    }

    // Requirement 9: Dynamic User Tier & Account Distribution counts from database/Firebase
    val totalRegisteredUsers = users.size
    val activeUsers = users.count { it.status == AccountStatus.ACTIVE.name }
    val suspendedUsers = users.count {
        it.status in setOf(AccountStatus.SUSPENDED.name, AccountStatus.BLOCKED.name) ||
            it.role == AdminRole.BLOCKED.name
    }
    val proCount = users.count { it.subscriptionPlan.contains("Pro", ignoreCase = true) }
    val enterpriseCount = users.count { it.subscriptionPlan.contains("Enterprise", ignoreCase = true) }
    val freeCount = users.count {
        !it.subscriptionPlan.contains("Pro", ignoreCase = true) &&
            !it.subscriptionPlan.contains("Enterprise", ignoreCase = true)
    }
    val successLogins = loginHistory.count { it.success }
    val failedLogins = loginHistory.count { !it.success }

    fun buildTextReport(): String {
        val adminSig = AdminDigitalSignatureManager.loadSignature(context)
        val sb = StringBuilder()
        sb.appendLine("==================================================================")
        sb.appendLine("      NEUROGRIP ADMIN REMOTE ANALYTICS & USER ACTIVITY REPORT     ")
        sb.appendLine("==================================================================")
        sb.appendLine("Generated At : ${formatDateTimeReadable(System.currentTimeMillis())}")
        sb.appendLine("Date Range   : ${if (selectedPreset == "ALL_TIME") "ALL TIME" else "$fromDateInput to $toDateInput"}")
        sb.appendLine("User Filter  : Name='${filterUserName.ifBlank { "ALL" }}' | Email='${filterUserEmail.ifBlank { "ALL" }}'")
        sb.appendLine("Report Type  : $selectedReportType")
        sb.appendLine("Admin Signer : ${adminSig.signerName} (${adminSig.signerTitle}) <${adminSig.signerEmail}>")
        sb.appendLine("Signature ID : HMAC-SHA256 ${adminSig.hmacSha256.take(24)}...")
        sb.appendLine("------------------------------------------------------------------")
        sb.appendLine("EXECUTIVE SUMMARY (SELECTED FILTERS):")
        sb.appendLine("• Unique Online Users       : ${uniqueOnlineUsernames.size} users (${onlineSessionsInRange.size} sessions)")
        sb.appendLine("• Total User Actions Logged : ${userActionsInRange.size} actions")
        sb.appendLine("• New Users Joined          : ${newUsersInRange.size} new registrations")
        sb.appendLine("• Total Registered Users    : $totalRegisteredUsers (Active: $activeUsers, Suspended: $suspendedUsers)")
        sb.appendLine("• Subscription Tiers        : Pro: $proCount | Free: $freeCount | Enterprise: $enterpriseCount")
        sb.appendLine("------------------------------------------------------------------")
        sb.appendLine()
        sb.appendLine("1) NEW USERS JOINED IN DATE RANGE (${newUsersInRange.size}):")
        if (newUsersInRange.isEmpty()) {
            sb.appendLine("   No new user registrations recorded for these filters.")
        } else {
            newUsersInRange.forEachIndexed { idx, u ->
                sb.appendLine("   ${idx + 1}. [${formatDateTimeReadable(u.timestamp)}] ${u.name.ifBlank { u.username }} (@${u.username}) | Email: ${u.email} | Role: ${u.role} | Plan: ${u.subscriptionPlan}")
            }
        }
        sb.appendLine()
        sb.appendLine("2) WHICH USERS WERE ONLINE BY DATE (${onlineSessionsInRange.size} SESSIONS):")
        if (onlineSessionsInRange.isEmpty()) {
            sb.appendLine("   No online sessions recorded for these filters.")
        } else {
            onlineSessionsInRange.forEachIndexed { idx, s ->
                sb.appendLine("   ${idx + 1}. [${s.dateString}] ${s.fullName} (@${s.username}) | Email: ${s.email} | Status: ${s.statusLabel} | Device: ${s.deviceInfo} | IP: ${s.ipAddress}")
            }
        }
        sb.appendLine()
        sb.appendLine("3) WHAT EACH USER DID IN THE APPLICATION (${userActionsInRange.size} EVENTS):")
        if (userActionsInRange.isEmpty()) {
            sb.appendLine("   No user activity events recorded for these filters.")
        } else {
            userActionsInRange.forEachIndexed { idx, act ->
                sb.appendLine("   ${idx + 1}. [${act.dateString}] @${act.username} (${act.fullName}) -> ${act.actionType}: ${act.details}")
            }
        }
        sb.appendLine("==================================================================")
        return sb.toString()
    }

    fun buildCsvReport(): String {
        val adminSig = AdminDigitalSignatureManager.loadSignature(context)
        fun esc(v: String): String = "\"${v.replace("\"", "\"\"")}\""
        val sb = StringBuilder()
        sb.appendLine("Section,Date_Time,Username,Full_Name,Email,Event_Or_Status,Details,Admin_Signature")
        newUsersInRange.forEach { u ->
            sb.appendLine(
                listOf(
                    esc("NEW_USER_JOINED"),
                    esc(formatDateTimeReadable(u.timestamp)),
                    esc(u.username),
                    esc(u.name.ifBlank { u.username }),
                    esc(u.email),
                    esc("REGISTERED_${u.role}"),
                    esc("Plan: ${u.subscriptionPlan} | Status: ${u.status}"),
                    esc("${adminSig.signerName} (${adminSig.hmacSha256.take(12)})")
                ).joinToString(",")
            )
        }
        onlineSessionsInRange.forEach { s ->
            sb.appendLine(
                listOf(
                    esc("ONLINE_USER_SESSION"),
                    esc(s.dateString),
                    esc(s.username),
                    esc(s.fullName),
                    esc(s.email),
                    esc(s.statusLabel),
                    esc("Device: ${s.deviceInfo} | IP: ${s.ipAddress}"),
                    esc("${adminSig.signerName} (${adminSig.hmacSha256.take(12)})")
                ).joinToString(",")
            )
        }
        userActionsInRange.forEach { a ->
            val email = userByUsername[a.username.lowercase(Locale.US)]?.email ?: ""
            sb.appendLine(
                listOf(
                    esc("USER_APP_ACTION"),
                    esc(a.dateString),
                    esc(a.username),
                    esc(a.fullName),
                    esc(email),
                    esc(a.actionType),
                    esc(a.details),
                    esc("${adminSig.signerName} (${adminSig.hmacSha256.take(12)})")
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    fun downloadRemoteReport() {
        try {
            val nowGen = System.currentTimeMillis()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(nowGen))
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            if (!dir.exists()) dir.mkdirs()

            val targetUser = users.find { u ->
                (filterUserName.isNotBlank() && (u.username.contains(filterUserName.trim(), true) || u.name.contains(filterUserName.trim(), true))) ||
                    (filterUserEmail.isNotBlank() && u.email.contains(filterUserEmail.trim(), true))
            }
            val reportUserUsername = targetUser?.username ?: filterUserName.trim().ifBlank { "all_users" }
            val reportUserDisplayName = targetUser?.name?.ifBlank { targetUser.username }
                ?: filterUserName.trim().ifBlank { "All Filtered Users (${uniqueOnlineUsernames.size})" }
            val reportUserEmail = targetUser?.email ?: filterUserEmail.trim().ifBlank { "all@neurogrip.io" }
            val adminSig = AdminDigitalSignatureManager.loadSignature(context)

            val savedFileName: String
            val savedFilePath: String
            val savedSha256: String
            val savedReportId: String

            if (selectedReportType == "PDF") {
                val snap = RoboHubReportGenerator.buildLiveReportSnapshot(
                    context = context,
                    periodStartMillis = startMillis,
                    periodEndMillis = endMillis,
                    periodLabel = "$fromDateInput to $toDateInput",
                    currentSession = AuthSessionDto(
                        token = "",
                        userId = targetUser?.id ?: 0,
                        username = reportUserUsername,
                        name = reportUserDisplayName,
                        email = reportUserEmail,
                        role = targetUser?.role ?: "USER",
                        expiresAt = nowGen + 60_000L,
                        permissions = emptyList(),
                        otpVerified = true
                    ),
                    users = if (targetUser != null) listOf(targetUser) else users,
                    loginHistory = loginHistory.filter {
                        targetUser == null || it.username.equals(targetUser.username, ignoreCase = true)
                    },
                    auditLogs = auditLogs.filter {
                        targetUser == null || it.actorUsername.equals(targetUser.username, ignoreCase = true)
                    },
                    tickets = tickets.filter {
                        targetUser == null || it.username.equals(targetUser.username, ignoreCase = true)
                    },
                    isEsp32Connected = false,
                    isEsp32Connecting = false,
                    esp32Ip = "192.168.4.1",
                    lastVerifiedConnAt = nowGen,
                    connectionInterruptions = 0,
                    eegAttention = 0,
                    emgStrength = 0,
                    gripForceN = 0f,
                    servoTempC = 32.0f,
                    latencyMs = 18,
                    batteryPct = 95,
                    sessionStartMillis = nowGen - 600_000L,
                    commandHistory = emptyList(),
                    firestoreConfigured = true,
                    firestoreProjectId = "neurogrip-cloud",
                    firestoreDatabaseId = "ai-studio-android-neuropul",
                    confirmedSignature = null,
                    includeInternalLogs = true
                )
                val pdfArtifact = RoboHubReportGenerator.generatePdfReportFile(
                    context = context,
                    snapshot = snap,
                    includeInternalLogs = true
                )
                RoboHubReportGenerator.savePdfToPublicDownloads(context, pdfArtifact)
                savedFileName = pdfArtifact.fileName
                savedFilePath = pdfArtifact.file.absolutePath
                savedSha256 = pdfArtifact.sha256Hex
                savedReportId = pdfArtifact.reportId
            } else {
                val csvFile = File(dir, "NeuroGrip_Admin_Report_$stamp.csv")
                val txtFile = File(dir, "NeuroGrip_Admin_Report_$stamp.txt")
                val csvText = buildCsvReport()
                csvFile.writeText(csvText)
                txtFile.writeText(buildTextReport())
                savedFileName = csvFile.name
                savedFilePath = csvFile.absolutePath
                savedSha256 = SecureSessionManager.computeHmacSha256(csvText, "REPORT_CSV_DIGEST")
                savedReportId = "ADM-CSV-$stamp"
            }

            val logEntries = userActionsInRange.take(25).map { act ->
                "${act.dateString} | @${act.username} (${act.fullName}) | ${act.actionType}: ${act.details}"
            }.ifEmpty {
                onlineSessionsInRange.take(15).map { s ->
                    "${s.dateString} | @${s.username} (${s.fullName}) | ${s.statusLabel} (${s.deviceInfo})"
                }
            }

            val record = GeneratedReportRecord(
                reportId = savedReportId,
                username = reportUserUsername,
                userDisplayName = reportUserDisplayName,
                userEmail = reportUserEmail,
                reportType = selectedReportType,
                periodStartFormatted = fromDateInput,
                periodEndFormatted = toDateInput,
                generatedAtMillis = nowGen,
                generatedDateIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowGen)),
                generatedDateDisplay = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(nowGen)),
                filePath = savedFilePath,
                fileName = savedFileName,
                sha256Hex = savedSha256,
                adminSignedBy = adminSig.signerName,
                adminSignatureDigest = adminSig.hmacSha256,
                totalCommands = userActionsInRange.size,
                successRateText = "${onlineSessionsInRange.size} sessions / ${userActionsInRange.size} actions",
                activitySummary = "Sessions: ${onlineSessionsInRange.size} | Actions: ${userActionsInRange.size} | New Users: ${newUsersInRange.size} | Signed by: ${adminSig.signerName}",
                associatedLogs = logEntries
            )

            if (adminViewModel != null) {
                adminViewModel.recordGeneratedReport(record)
            } else {
                GeneratedReportStore.saveReport(context, record)
            }
            selectedGeneratedReport = record
            hasSearchedGeneratedReports = true

            lastDownloadedFileInfo =
                "Saved $savedFileName [$selectedReportType] • Signed by ${adminSig.signerName} (${onlineSessionsInRange.size} sessions, ${userActionsInRange.size} actions)"
            adminViewModel?.recordActivity(
                actionType = "REMOTE_REPORT_DOWNLOADED",
                targetEntity = savedFileName,
                details = "Generated $selectedReportType report for '$reportUserDisplayName' ($fromDateInput to $toDateInput)"
            )
            Toast.makeText(
                context,
                "Report generated & downloaded: $savedFileName",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Failed to save report: ${e.localizedMessage}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Platform Analytics & Advanced Report Generation",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = Color(0xFF0F172A)
            )
            Text(
                "Filter by user name, email, date range, and report format (PDF/CSV), search generated reports, and inspect real-time user tier distribution.",
                fontSize = 12.sp,
                color = Color(0xFF475569)
            )
        }

        // REQUIREMENT 9: DYNAMIC USER TIER & ACCOUNT DISTRIBUTION CARD
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("user_tier_distribution_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "User Tier & Account Distribution (Live Firebase / DB)",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        "Real-time counts calculated dynamically from registered user accounts",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DistributionMetricTile(
                            label = "Total Registered",
                            count = totalRegisteredUsers,
                            accent = Color(0xFF1E3A8A),
                            bg = Color(0xFFEFF6FF),
                            modifier = Modifier.weight(1f).testTag("tier_total_registered")
                        )
                        DistributionMetricTile(
                            label = "Currently Active",
                            count = activeUsers,
                            accent = Color(0xFF059669),
                            bg = Color(0xFFECFDF5),
                            modifier = Modifier.weight(1f).testTag("tier_currently_active")
                        )
                        DistributionMetricTile(
                            label = "Suspended",
                            count = suspendedUsers,
                            accent = Color(0xFFDC2626),
                            bg = Color(0xFFFEF2F2),
                            modifier = Modifier.weight(1f).testTag("tier_suspended_users")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DistributionMetricTile(
                            label = "Pro Users",
                            count = proCount,
                            accent = Color(0xFF2563EB),
                            bg = Color(0xFFDBEAFE),
                            modifier = Modifier.weight(1f).testTag("tier_pro_users")
                        )
                        DistributionMetricTile(
                            label = "Free Users",
                            count = freeCount,
                            accent = Color(0xFF475569),
                            bg = Color(0xFFF1F5F9),
                            modifier = Modifier.weight(1f).testTag("tier_free_users")
                        )
                        DistributionMetricTile(
                            label = "Enterprise Users",
                            count = enterpriseCount,
                            accent = Color(0xFF7C3AED),
                            bg = Color(0xFFF5F3FF),
                            modifier = Modifier.weight(1f).testTag("tier_enterprise_users")
                        )
                    }

                    val values = listOf(
                        "Total ($totalRegisteredUsers)" to totalRegisteredUsers.toFloat(),
                        "Active ($activeUsers)" to activeUsers.toFloat(),
                        "Suspended ($suspendedUsers)" to suspendedUsers.toFloat(),
                        "Pro ($proCount)" to proCount.toFloat(),
                        "Free ($freeCount)" to freeCount.toFloat(),
                        "Enterprise ($enterpriseCount)" to enterpriseCount.toFloat()
                    )
                    val maxVal = (values.maxOfOrNull { it.second } ?: 1f).coerceAtLeast(1f)

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                    ) {
                        val barWidth = size.width / (values.size * 2f)
                        values.forEachIndexed { idx, pair ->
                            val ratio = (pair.second / maxVal).coerceIn(0.08f, 1f)
                            val barHeight = size.height * ratio
                            val x = idx * (barWidth * 2f) + barWidth * 0.5f
                            val y = size.height - barHeight
                            drawRoundRect(
                                color = when (idx) {
                                    0 -> Color(0xFF1E3A8A)
                                    1 -> Color(0xFF10B981)
                                    2 -> Color(0xFFEF4444)
                                    3 -> Color(0xFF3B82F6)
                                    4 -> Color(0xFF64748B)
                                    else -> Color(0xFF8B5CF6)
                                },
                                topLeft = Offset(x, y),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(10f, 10f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        values.forEach { (label, _) ->
                            Text(
                                label,
                                fontSize = 9.sp,
                                color = Color(0xFF475569),
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // REQUIREMENT 7: ADVANCED ADMIN ANALYSIS & REPORT GENERATION CARD
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.5.dp, Color(0xFF93C5FD)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = Color(0xFFDBEAFE),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.CloudDownload,
                                        contentDescription = "Remote Report Download",
                                        tint = Color(0xFF1D4ED8)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Advanced User Activity & Report Generator",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    "Filter by User Name • Email • Start/End Date • Report Type (PDF/CSV)",
                                    fontSize = 11.sp,
                                    color = Color(0xFF475569)
                                )
                            }
                        }
                    }

                    // User Name & User Email Filters
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = filterUserName,
                            onValueChange = { filterUserName = it },
                            label = { Text("User Name Filter", fontSize = 11.sp) },
                            placeholder = { Text("e.g., hj123 or name", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("analytics_filter_user_name")
                        )
                        OutlinedTextField(
                            value = filterUserEmail,
                            onValueChange = { filterUserEmail = it },
                            label = { Text("User Email Filter", fontSize = 11.sp) },
                            placeholder = { Text("e.g., user@gmail.com", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("analytics_filter_user_email")
                        )
                    }

                    // Date Range Presets
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val presets = listOf(
                            "TODAY" to "Today",
                            "LAST_7_DAYS" to "Last 7 Days",
                            "LAST_30_DAYS" to "Last 30 Days",
                            "ALL_TIME" to "All Time"
                        )
                        presets.forEach { (key, label) ->
                            FilterChip(
                                selected = selectedPreset == key,
                                onClick = {
                                    selectedPreset = key
                                    val current = System.currentTimeMillis()
                                    toDateInput = formatDateYmd(current)
                                    fromDateInput = when (key) {
                                        "TODAY" -> formatDateYmd(current)
                                        "LAST_7_DAYS" -> formatDateYmd(current - 7L * 24 * 60 * 60 * 1000)
                                        "LAST_30_DAYS" -> formatDateYmd(current - 30L * 24 * 60 * 60 * 1000)
                                        else -> "2020-01-01"
                                    }
                                },
                                label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }

                    // Custom Start Date / End Date Inputs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = fromDateInput,
                            onValueChange = {
                                fromDateInput = it
                                selectedPreset = "CUSTOM"
                            },
                            label = { Text("Start Date (YYYY-MM-DD)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("report_from_date_input")
                        )
                        OutlinedTextField(
                            value = toDateInput,
                            onValueChange = {
                                toDateInput = it
                                selectedPreset = "CUSTOM"
                            },
                            label = { Text("End Date (YYYY-MM-DD)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("report_to_date_input")
                        )
                    }

                    // Report Type Selector (PDF / CSV)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Report Type:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        FilterChip(
                            selected = selectedReportType == "PDF",
                            onClick = { selectedReportType = "PDF" },
                            label = { Text("PDF (Signed Report)", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.testTag("analytics_report_type_pdf")
                        )
                        FilterChip(
                            selected = selectedReportType == "CSV",
                            onClick = { selectedReportType = "CSV" },
                            label = { Text("CSV / TXT Export", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.testTag("analytics_report_type_csv")
                        )
                    }

                    // 3 Key Metrics for the Selected Filters
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = Color(0xFFEFF6FF),
                            border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("ONLINE USERS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8))
                                Text("${uniqueOnlineUsernames.size}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
                                Text("${onlineSessionsInRange.size} sessions", fontSize = 10.sp, color = Color(0xFF475569))
                            }
                        }
                        Surface(
                            color = Color(0xFFF5F3FF),
                            border = BorderStroke(1.dp, Color(0xFFDDD6FE)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("USER ACTIONS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6D28D9))
                                Text("${userActionsInRange.size}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
                                Text("in-app events", fontSize = 10.sp, color = Color(0xFF475569))
                            }
                        }
                        Surface(
                            color = Color(0xFFECFDF5),
                            border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("NEW USERS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                                Text("${newUsersInRange.size}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
                                Text("joined in range", fontSize = 10.sp, color = Color(0xFF475569))
                            }
                        }
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { downloadRemoteReport() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1D4ED8),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("download_remote_report_button")
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Generate & Download Report",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Generate & Download ($selectedReportType)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        OutlinedButton(
                            onClick = { showReportPreviewDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF1D4ED8)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("preview_remote_report_button")
                        ) {
                            Icon(
                                Icons.Default.Visibility,
                                contentDescription = "Preview Report",
                                tint = Color(0xFF1D4ED8),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Preview / Share", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1D4ED8))
                        }
                    }

                    lastDownloadedFileInfo?.let { info ->
                        Surface(
                            color = Color(0xFFECFDF5),
                            border = BorderStroke(1.dp, Color(0xFF6EE7B7)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Downloaded",
                                    tint = Color(0xFF059669),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    info,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF065F46),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // REQUIREMENT 8: SEARCH GENERATED USER REPORTS (Logs shown ONLY after searching & selecting a matching report)
        item {
            val matchingReports = remember(allGeneratedReports, generatedReportUserQuery, generatedReportDateQuery, hasSearchedGeneratedReports) {
                if (!hasSearchedGeneratedReports && generatedReportUserQuery.isBlank() && generatedReportDateQuery.isBlank()) {
                    emptyList()
                } else {
                    val qUser = generatedReportUserQuery.trim().lowercase(Locale.US)
                    val qDate = generatedReportDateQuery.trim()
                    allGeneratedReports.filter { r ->
                        val userMatches = qUser.isEmpty() ||
                            r.username.lowercase(Locale.US).contains(qUser) ||
                            r.userDisplayName.lowercase(Locale.US).contains(qUser) ||
                            r.userEmail.lowercase(Locale.US).contains(qUser)
                        val dateMatches = qDate.isEmpty() ||
                            r.generatedDateDisplay.equals(qDate, ignoreCase = true) ||
                            r.generatedDateIso.equals(qDate, ignoreCase = true) ||
                            r.generatedDateDisplay.contains(qDate, ignoreCase = true) ||
                            r.generatedDateIso.contains(qDate, ignoreCase = true)
                        userMatches && dateMatches
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.5.dp, Color(0xFFCBD5E1)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("analytics_search_generated_reports_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Search Generated User Reports",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        "Search by user name/email and generation date (e.g., 15/11/2026) to inspect a generated report and its associated logs.",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = generatedReportUserQuery,
                            onValueChange = {
                                generatedReportUserQuery = it
                                selectedGeneratedReport = null
                            },
                            label = { Text("User Name or Email", fontSize = 11.sp) },
                            placeholder = { Text("Search user...", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("search_generated_report_user_input")
                        )
                        OutlinedTextField(
                            value = generatedReportDateQuery,
                            onValueChange = {
                                generatedReportDateQuery = it
                                selectedGeneratedReport = null
                            },
                            label = { Text("Date (DD/MM/YYYY)", fontSize = 11.sp) },
                            placeholder = { Text("e.g., 15/11/2026", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("search_generated_report_date_input")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                hasSearchedGeneratedReports = true
                                selectedGeneratedReport = matchingReports.firstOrNull()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("search_generated_reports_button")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Search Generated Reports", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        if (hasSearchedGeneratedReports || generatedReportUserQuery.isNotBlank() || generatedReportDateQuery.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    generatedReportUserQuery = ""
                                    generatedReportDateQuery = ""
                                    hasSearchedGeneratedReports = false
                                    selectedGeneratedReport = null
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Clear", fontSize = 12.sp)
                            }
                        }
                    }

                    if (!hasSearchedGeneratedReports && generatedReportUserQuery.isBlank() && generatedReportDateQuery.isBlank()) {
                        Surface(
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Enter a user name, email, or generation date (such as 15/11/2026) and tap 'Search Generated Reports' to view matching reports and their associated logs.",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else if (matchingReports.isEmpty()) {
                        Surface(
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, Color(0xFFFECACA)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "No generated user reports matched your search criteria.",
                                fontSize = 12.sp,
                                color = Color(0xFF991B1B),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        Text(
                            "Matching Generated Reports (${matchingReports.size}):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        matchingReports.take(8).forEach { rpt ->
                            val isSelected = selectedGeneratedReport?.reportId == rpt.reportId
                            Surface(
                                color = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF2563EB) else Color(0xFFE2E8F0)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "${rpt.userDisplayName} (@${rpt.username}) • ${rpt.reportType}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color(0xFF0F172A)
                                            )
                                            Text(
                                                "Email: ${rpt.userEmail} • Generated: ${rpt.generatedDateDisplay} (${rpt.generatedDateIso})",
                                                fontSize = 11.sp,
                                                color = Color(0xFF475569)
                                            )
                                            Text(
                                                "Report ID: ${rpt.reportId} • Signed by: ${rpt.adminSignedBy}",
                                                fontSize = 10.sp,
                                                color = Color(0xFF1D4ED8),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                selectedGeneratedReport = if (isSelected) null else rpt
                                            },
                                            modifier = Modifier
                                                .heightIn(min = 40.dp)
                                                .testTag("select_generated_report_${rpt.reportId}")
                                        ) {
                                            Text(if (isSelected) "Hide Logs" else "View Report & Logs", fontSize = 11.sp)
                                        }
                                    }

                                    if (isSelected) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        HorizontalDivider(color = Color(0xFFBFDBFE))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Report Summary: ${rpt.activitySummary}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E293B)
                                        )
                                        Text(
                                            "Period: ${rpt.periodStartFormatted} → ${rpt.periodEndFormatted} • File: ${rpt.fileName}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF475569)
                                        )
                                        Text(
                                            "SHA-256: ${rpt.sha256Hex.take(24)}... • Admin HMAC: ${rpt.adminSignatureDigest.take(20)}...",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFF047857)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            "Associated User Activity Logs (${rpt.associatedLogs.size}):",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF0F172A)
                                        )
                                        if (rpt.associatedLogs.isEmpty()) {
                                            Text(
                                                "No activity log entries recorded in this report.",
                                                fontSize = 11.sp,
                                                color = Color(0xFF64748B)
                                            )
                                        } else {
                                            rpt.associatedLogs.forEach { logLine ->
                                                Text(
                                                    "• $logLine",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF334155),
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // REQUIREMENT 10: DEDICATED USER LOGIN & ACTIVITY SEARCH CARD
        item {
            val matchedUsersForLoginSearch = remember(users, userLoginSearchQuery) {
                val q = userLoginSearchQuery.trim().lowercase(Locale.US)
                if (q.isEmpty()) emptyList()
                else users.filter {
                    it.username.lowercase(Locale.US).contains(q) ||
                        it.name.lowercase(Locale.US).contains(q) ||
                        it.email.lowercase(Locale.US).contains(q)
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("user_login_activity_search_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "User Login & Activity Search",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        "Search by user name or email to inspect login date/time, last login, login history, and active/suspended status.",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )

                    OutlinedTextField(
                        value = userLoginSearchQuery,
                        onValueChange = { userLoginSearchQuery = it },
                        label = { Text("Search User by Name or Email") },
                        placeholder = { Text("Enter name, username, or email...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("user_login_activity_search_input")
                    )

                    if (userLoginSearchQuery.isBlank()) {
                        Text(
                            "Type a user name or email above to inspect their login timestamps, login history, and account status.",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    } else if (matchedUsersForLoginSearch.isEmpty()) {
                        Text(
                            "No registered user found matching '${userLoginSearchQuery.trim()}'.",
                            fontSize = 12.sp,
                            color = Color(0xFFDC2626)
                        )
                    } else {
                        matchedUsersForLoginSearch.forEach { u ->
                            val userLogins = loginHistory.filter {
                                it.username.equals(u.username, ignoreCase = true)
                            }.sortedByDescending { it.timestamp }
                            val isSusp = u.status in setOf(AccountStatus.SUSPENDED.name, AccountStatus.BLOCKED.name) ||
                                u.role == AdminRole.BLOCKED.name

                            Surface(
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${u.name.ifBlank { u.username }} (@${u.username})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF0F172A),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Surface(
                                            color = if (isSusp) Color(0xFFFEF2F2) else Color(0xFFECFDF5),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                if (isSusp) "SUSPENDED" else "ACTIVE",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (isSusp) Color(0xFFDC2626) else Color(0xFF059669),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        "Email: ${u.email} • Role: ${u.role} • Plan: ${u.subscriptionPlan}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF475569)
                                    )
                                    Text(
                                        "Last Login Timestamp: ${if (u.lastLoginAt > 0L) formatDateTimeReadable(u.lastLoginAt) else "Never"} • Total Logins: ${u.loginCount}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF1D4ED8)
                                    )
                                    if (userLogins.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Login History (${userLogins.size} records):",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                        userLogins.take(6).forEach { entry ->
                                            Text(
                                                "• ${formatDateTimeReadable(entry.timestamp)} — ${if (entry.success) "LOGIN SUCCESS" else "FAILED (${entry.failureReason})"} • ${entry.deviceModel} [${entry.ipAddress}]",
                                                fontSize = 11.sp,
                                                color = if (entry.success) Color(0xFF047857) else Color(0xFFDC2626)
                                            )
                                        }
                                    } else {
                                        Text(
                                            "Login History: No individual login events recorded yet.",
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                            }
                        }
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
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Operational Health & Security Summary",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                    Text("• Authentication Success Rate: $successLogins succeeded / $failedLogins failed attempts", fontSize = 13.sp, color = Color(0xFF334155))
                    Text("• Push Broadcasts Delivered: ${notifications.count { it.deliveryStatus == "DELIVERED" }} campaigns", fontSize = 13.sp, color = Color(0xFF334155))
                    Text("• Support Ticket Resolution: ${tickets.count { it.status == "RESOLVED" || it.status == "CLOSED" }} resolved / ${tickets.size} total", fontSize = 13.sp, color = Color(0xFF334155))
                    Text("• Active Monthly Revenue (MRR): $${"%.2f".format(subscriptions.filter { it.status == "ACTIVE" }.sumOf { it.amountUsd })} USD", fontSize = 13.sp, color = Color(0xFF334155))
                }
            }
        }
    }

    if (showReportPreviewDialog) {
        val fullTextReport = remember(fromDateInput, toDateInput, selectedPreset, filterUserName, filterUserEmail, selectedReportType, users, loginHistory, auditLogs, tickets) {
            buildTextReport()
        }
        AlertDialog(
            onDismissRequest = { showReportPreviewDialog = false },
            title = {
                Text(
                    "Generated Admin Report ($fromDateInput → $toDateInput)",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Surface(
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 380.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = fullTextReport,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF0F172A)
                        )
                    }
                }
            },
            confirmButton = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("NeuroGrip Admin Report", fullTextReport))
                            Toast.makeText(context, "Report copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) {
                        Text("Copy")
                    }
                    Button(
                        onClick = {
                            downloadRemoteReport()
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "NeuroGrip Admin Report ($fromDateInput to $toDateInput)")
                                putExtra(Intent.EXTRA_TEXT, fullTextReport)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Admin Report"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8)),
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) {
                        Text("Download & Share", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showReportPreviewDialog = false },
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun DistributionMetricTile(
    label: String,
    count: Int,
    accent: Color,
    bg: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = bg,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "$count",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accent
            )
            Text(
                label,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF334155),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminSupportTicketsTab(
    tickets: List<SupportTicketEntity>,
    users: List<User> = emptyList(),
    adminViewModel: AdminViewModel
) {
    val context = LocalContext.current
    var selectedTicket by remember { mutableStateOf<SupportTicketEntity?>(null) }
    var selectedStatusFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var showAddFeedbackDialog by remember { mutableStateOf(false) }

    val userByUsername = remember(users) {
        users.associateBy { it.username.lowercase(Locale.US) }
    }
    val userById = remember(users) {
        users.associateBy { it.id }
    }

    val filteredTickets = remember(tickets, selectedStatusFilter, searchQuery) {
        tickets.filter { t ->
            val matchesStatus = selectedStatusFilter == "ALL" || t.status.equals(selectedStatusFilter, ignoreCase = true)
            val matchesQuery = searchQuery.isBlank() ||
                t.username.contains(searchQuery, ignoreCase = true) ||
                t.subject.contains(searchQuery, ignoreCase = true) ||
                t.message.contains(searchQuery, ignoreCase = true) ||
                t.category.contains(searchQuery, ignoreCase = true)
            matchesStatus && matchesQuery
        }
    }

    val openCount = tickets.count { it.status == "OPEN" }
    val inProgressCount = tickets.count { it.status == "IN_PROGRESS" }
    val resolvedCount = tickets.count { it.status == "RESOLVED" || it.status == "CLOSED" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Card with KPIs & Add Feedback Button
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "User Feedback & Support Desk (${tickets.size})",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "View full user details for every feedback and send instant app resolution notifications.",
                                fontSize = 12.sp,
                                color = Color(0xFF475569)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = { showAddFeedbackDialog = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFFEFF6FF),
                                contentColor = Color(0xFF1D4ED8)
                            ),
                            modifier = Modifier.testTag("add_user_feedback_button")
                        ) {
                            Icon(Icons.Default.AddComment, contentDescription = "Log Feedback", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Feedback", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    // Status Summary Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, Color(0xFFFECACA)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("OPEN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                                Text("$openCount", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF991B1B))
                            }
                        }
                        Surface(
                            color = Color(0xFFFFFBEB),
                            border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("IN PROGRESS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                                Text("$inProgressCount", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF92400E))
                            }
                        }
                        Surface(
                            color = Color(0xFFECFDF5),
                            border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("RESOLVED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                                Text("$resolvedCount", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF065F46))
                            }
                        }
                    }

                    // Search & Filter Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search by username, subject, or feedback message", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("support_ticket_search_input")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("ALL", "OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED").forEach { st ->
                            FilterChip(
                                selected = selectedStatusFilter == st,
                                onClick = { selectedStatusFilter = st },
                                label = { Text(st.replace("_", " "), fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }
                }
            }
        }

        if (filteredTickets.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Forum,
                            contentDescription = "No Feedback",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No feedback tickets match the current filter.",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF0F172A)
                        )
                    }
                }
            }
        }

        items(filteredTickets, key = { it.id }) { ticket ->
            val submitterUser = userByUsername[ticket.username.lowercase(Locale.US)] ?: userById[ticket.userId]
            val submitterFullName = submitterUser?.name?.ifBlank { ticket.username } ?: ticket.username
            val submitterEmail = submitterUser?.email?.ifBlank { ticket.userEmail.ifBlank { "Registered App User" } }
                ?: ticket.userEmail.ifBlank { "Registered App User" }
            val submitterRole = submitterUser?.role ?: "USER"
            val submitterPlan = submitterUser?.subscriptionPlan ?: "Standard"

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Ticket Title + Status/Priority Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ticket #${ticket.id} • ${ticket.subject}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Category: ${ticket.category} • Submitted: ${formatDateTimeReadable(ticket.createdAt)}",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = when (ticket.status) {
                                "OPEN" -> Color(0xFFFEF2F2)
                                "IN_PROGRESS" -> Color(0xFFFFFBEB)
                                else -> Color(0xFFECFDF5)
                            },
                            border = BorderStroke(
                                1.dp,
                                when (ticket.status) {
                                    "OPEN" -> Color(0xFFFECACA)
                                    "IN_PROGRESS" -> Color(0xFFFDE68A)
                                    else -> Color(0xFFA7F3D0)
                                }
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "${ticket.priority} • ${ticket.status}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = when (ticket.status) {
                                    "OPEN" -> Color(0xFFDC2626)
                                    "IN_PROGRESS" -> Color(0xFFD97706)
                                    else -> Color(0xFF059669)
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // User Who Submitted Feedback Box
                    Surface(
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = Color(0xFFDBEAFE),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = "Submitted By User",
                                        tint = Color(0xFF1D4ED8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Feedback By: $submitterFullName (@${ticket.username})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    "Email: $submitterEmail • User ID: #${ticket.userId}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF475569)
                                )
                                Text(
                                    "Role: $submitterRole • Plan: $submitterPlan • Assigned To: @${ticket.assignedTo}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }

                    // User Feedback Message Box
                    Surface(
                        color = Color(0xFFFFFBEB),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "USER FEEDBACK / ISSUE DESCRIPTION:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF92400E)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                ticket.message,
                                fontSize = 13.sp,
                                color = Color(0xFF0F172A),
                                lineHeight = 18.sp
                            )
                        }
                    }

                    // Official Admin Reply Box (if replied)
                    if (ticket.adminReply.isNotBlank()) {
                        Surface(
                            color = Color(0xFFECFDF5),
                            border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "OFFICIAL SOLUTION / ADMIN REPLY SENT TO @${ticket.username}:",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF065F46)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    ticket.adminReply,
                                    fontSize = 12.sp,
                                    color = Color(0xFF064E3B),
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }

                    // Action Buttons Row: 1-Tap Resolve & Notify User + Full Reply Modal
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val autoReply = if (ticket.adminReply.isNotBlank()) {
                                    ticket.adminReply
                                } else {
                                    "તમારા ફીડબેક પરથી એપમાં સોલ્યુશન થઈ ગયું છે! Your feedback regarding '${ticket.subject}' has been resolved in the app."
                                }
                                adminViewModel.updateTicket(
                                    ticket = ticket,
                                    newStatus = "RESOLVED",
                                    reply = autoReply,
                                    assignedTo = ticket.assignedTo.ifBlank { "hj123" },
                                    sendResolutionNotification = true,
                                    customNotificationMessage = autoReply
                                )
                                Toast.makeText(
                                    context,
                                    "Solution notification sent to @${ticket.username}!",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF059669),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("notify_solution_ticket_${ticket.id}")
                        ) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = "Notify User Solution",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Resolve & Send Notification",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                textAlign = TextAlign.Center
                            )
                        }

                        OutlinedButton(
                            onClick = { selectedTicket = ticket },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF1D4ED8)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("respond_ticket_${ticket.id}")
                        ) {
                            Icon(
                                Icons.Default.EditNote,
                                contentDescription = "Respond / Update",
                                tint = Color(0xFF1D4ED8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Reply / Edit",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D4ED8),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    // Add / Log New Feedback Dialog
    if (showAddFeedbackDialog) {
        var usernameInput by remember {
            mutableStateOf(
                users.firstOrNull { it.role != AdminRole.SUPER_ADMIN.name && it.role != AdminRole.ADMIN.name }?.username
                    ?: "patient_demo"
            )
        }
        var subjectInput by remember { mutableStateOf("") }
        var messageInput by remember { mutableStateOf("") }
        var categoryInput by remember { mutableStateOf("APP_FEEDBACK") }
        var priorityInput by remember { mutableStateOf("HIGH") }

        AlertDialog(
            onDismissRequest = { showAddFeedbackDialog = false },
            title = {
                Text(
                    "Log User Feedback to Database",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = { usernameInput = it },
                        label = { Text("Submitted By Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = subjectInput,
                        onValueChange = { subjectInput = it },
                        label = { Text("Feedback Subject") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = messageInput,
                        onValueChange = { messageInput = it },
                        label = { Text("Detailed Feedback / Issue") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Category:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("APP_FEEDBACK", "CALIBRATION", "FIRMWARE", "ACCOUNT").forEach { cat ->
                            FilterChip(
                                selected = categoryInput == cat,
                                onClick = { categoryInput = cat },
                                label = { Text(cat, fontSize = 11.sp) }
                            )
                        }
                    }
                    Text("Priority:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("LOW", "MEDIUM", "HIGH", "CRITICAL").forEach { pr ->
                            FilterChip(
                                selected = priorityInput == pr,
                                onClick = { priorityInput = pr },
                                label = { Text(pr, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (subjectInput.isNotBlank() && messageInput.isNotBlank()) {
                            val matchedUser = userByUsername[usernameInput.trim().lowercase(Locale.US)]
                            val cleanUsername = usernameInput.trim().ifBlank { "user" }
                            adminViewModel.submitUserTicket(
                                username = cleanUsername,
                                email = matchedUser?.email ?: "$cleanUsername@neurogrip.app",
                                subject = subjectInput.trim(),
                                message = messageInput.trim(),
                                category = categoryInput,
                                priority = priorityInput
                            )
                            showAddFeedbackDialog = false
                            Toast.makeText(context, "Feedback saved to database!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8))
                ) {
                    Text("Save Feedback", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFeedbackDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Respond / Update & Send Solution Notification Modal
    selectedTicket?.let { t ->
        val defaultSolutionText =
            "તમારા ફીડબેક પરથી એપમાં સોલ્યુશન થઈ ગયું છે! Your feedback regarding '${t.subject}' has been resolved in the app."
        var reply by remember(t) {
            mutableStateOf(t.adminReply.ifBlank { defaultSolutionText })
        }
        var status by remember(t) { mutableStateOf(if (t.status == "OPEN") "RESOLVED" else t.status) }
        var assigned by remember(t) { mutableStateOf(t.assignedTo.ifBlank { "hj123" }) }
        var sendUserNotification by remember(t) { mutableStateOf(true) }
        var notificationBody by remember(t) {
            mutableStateOf(defaultSolutionText)
        }

        AlertDialog(
            onDismissRequest = { selectedTicket = null },
            title = {
                Column {
                    Text(
                        "Manage Feedback #${t.id} (@${t.username})",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        t.subject,
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Surface(
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                "User Feedback from @${t.username}:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D4ED8)
                            )
                            Text(
                                t.message,
                                fontSize = 12.sp,
                                color = Color(0xFF0F172A)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = reply,
                        onValueChange = {
                            reply = it
                            if (sendUserNotification) {
                                notificationBody = it
                            }
                        },
                        label = { Text("Official Solution / Admin Response") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = assigned,
                        onValueChange = { assigned = it },
                        label = { Text("Assigned Admin Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        "Ticket Status:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED").forEach { st ->
                            FilterChip(
                                selected = status == st,
                                onClick = { status = st },
                                label = { Text(st, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }

                    Surface(
                        color = Color(0xFFECFDF5),
                        border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Send Solution Notification to @${t.username}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF065F46)
                                    )
                                    Text(
                                        "Notifies user in-app & push that their feedback is resolved",
                                        fontSize = 10.sp,
                                        color = Color(0xFF047857)
                                    )
                                }
                                Switch(
                                    checked = sendUserNotification,
                                    onCheckedChange = { sendUserNotification = it }
                                )
                            }
                            if (sendUserNotification) {
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = notificationBody,
                                    onValueChange = { notificationBody = it },
                                    label = { Text("Notification Message to @${t.username}", fontSize = 11.sp) },
                                    minLines = 2,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.updateTicket(
                            ticket = t,
                            newStatus = status,
                            reply = reply,
                            assignedTo = assigned,
                            sendResolutionNotification = sendUserNotification,
                            customNotificationMessage = notificationBody
                        )
                        selectedTicket = null
                        Toast.makeText(
                            context,
                            if (sendUserNotification) "Ticket updated & notification sent to @${t.username}!"
                            else "Ticket updated in database!",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                ) {
                    Text(
                        if (sendUserNotification) "Save & Notify User" else "Save Ticket",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedTicket = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

