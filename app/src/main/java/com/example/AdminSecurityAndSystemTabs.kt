package com.example

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.ByteArrayOutputStream

@Composable
fun AdminRolesAndStaffTab(
    users: List<User>,
    effectiveRole: AdminRole,
    adminViewModel: AdminViewModel
) {
    val staffList = remember(users) {
        users.filter { AdminRole.fromString(it.role) != AdminRole.USER }
    }
    var userForRoleEdit by remember { mutableStateOf<User?>(null) }

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
                        "Role-Based Access Control (RBAC) Matrix",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        "Your active session role is ${effectiveRole.displayName}. Tap any role chip below to test live backend permission enforcement:",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            AdminRole.SUPER_ADMIN,
                            AdminRole.ADMIN,
                            AdminRole.EDITOR,
                            AdminRole.SUPPORT_AGENT,
                            AdminRole.ANALYST
                        ).forEach { r ->
                            FilterChip(
                                selected = effectiveRole == r,
                                onClick = { adminViewModel.setRoleSimulationOverride(r) },
                                label = { Text(r.displayName, fontSize = 12.sp) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Granted Permissions for ${effectiveRole.displayName}:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF1E293B)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        RolePermissionPolicy.permissionsFor(effectiveRole).joinToString(" • ") { it.name },
                        fontSize = 11.sp,
                        color = Color(0xFF2563EB)
                    )
                }
            }
        }

        item {
            Text(
                "Administrator & Staff Accounts (${staffList.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color(0xFF0F172A)
            )
        }

        items(staffList, key = { it.id }) { staff ->
            val roleEnum = AdminRole.fromString(staff.role)
            Card(
                shape = RoundedCornerShape(16.dp),
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
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(staff.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                        Text("@${staff.username} • ${staff.email}", fontSize = 12.sp, color = Color(0xFF64748B))
                        Text(
                            "Role: ${roleEnum.displayName} • 2FA: ${if (staff.twoFactorEnabled) "Enabled" else "Disabled"}",
                            fontSize = 11.sp,
                            color = Color(0xFF2563EB),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    OutlinedButton(onClick = { userForRoleEdit = staff }) {
                        Text("Change Role", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    userForRoleEdit?.let { target ->
        var chosenRole by remember(target) { mutableStateOf(AdminRole.fromString(target.role)) }
        AlertDialog(
            onDismissRequest = { userForRoleEdit = null },
            title = { Text("Assign Role for @${target.username}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AdminRole.entries.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = chosenRole == r, onClick = { chosenRole = r })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(r.displayName)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.changeUserRole(target.id, chosenRole)
                        userForRoleEdit = null
                    }
                ) {
                    Text("Update Role")
                }
            },
            dismissButton = {
                TextButton(onClick = { userForRoleEdit = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminSecurityAuditTab(
    auditLogs: List<AuditLogEntity>,
    loginHistory: List<LoginHistoryEntity>,
    remoteConfig: RemoteConfigEntity,
    adminViewModel: AdminViewModel
) {
    val context = LocalContext.current
    var backendUrl by remember { mutableStateOf(SecureSessionManager.getBackendUrl(context)) }
    var sessionTimeout by remember(remoteConfig) { mutableStateOf(remoteConfig.sessionTimeoutMinutes.toString()) }
    var rateLimit by remember(remoteConfig) { mutableStateOf(remoteConfig.rateLimitRequestsPerMin.toString()) }
    var enforce2Fa by remember(remoteConfig) { mutableStateOf(remoteConfig.enforceTwoFactorForAdmins) }
    var severityFilter by remember { mutableStateOf("ALL") }

    // Requirement 14B: Admin Digital Signature state & Photo Picker upload
    val signatureConfig by adminViewModel.adminSignatureConfig.collectAsStateWithLifecycle()
    val effectiveRole = adminViewModel.effectiveRole()
    val canManageSignature = effectiveRole in setOf(AdminRole.SUPER_ADMIN, AdminRole.ADMIN)

    var signerName by remember(signatureConfig) { mutableStateOf(signatureConfig.signerName) }
    var signerTitle by remember(signatureConfig) { mutableStateOf(signatureConfig.signerTitle) }
    var signatureScript by remember(signatureConfig) { mutableStateOf(signatureConfig.signatureScript) }
    var signatureImageBase64 by remember(signatureConfig) { mutableStateOf(signatureConfig.signatureImageBase64) }

    val decodedSignatureBitmap = remember(signatureImageBase64) {
        if (signatureImageBase64.isBlank()) null
        else try {
            val bytes = Base64.decode(signatureImageBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    val signaturePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && canManageSignature) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val rawBmp = BitmapFactory.decodeStream(stream)
                    if (rawBmp != null) {
                        val scaled = Bitmap.createScaledBitmap(rawBmp, 360, 120, true)
                        val baos = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.PNG, 90, baos)
                        signatureImageBase64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    val filteredLogs = remember(auditLogs, severityFilter) {
        if (severityFilter == "ALL") auditLogs else auditLogs.filter { it.severity == severityFilter }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // REQUIREMENT 14B: ADMIN DIGITAL SIGNATURE MANAGEMENT CARD
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.5.dp, Color(0xFF93C5FD)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_digital_signature_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Admin Digital Signature & Report Stamp",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                "Upload, preview, replace, and save the official Admin Digital Signature automatically embedded into generated PDF & CSV reports.",
                                fontSize = 11.sp,
                                color = Color(0xFF475569)
                            )
                        }
                        Surface(
                            color = if (canManageSignature) Color(0xFFECFDF5) else Color(0xFFFEF2F2),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                if (canManageSignature) "AUTHORIZED" else "READ-ONLY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (canManageSignature) Color(0xFF059669) else Color(0xFFDC2626),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Live Preview Box of Current Admin Signature
                    Surface(
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_signature_preview_box")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "LIVE SIGNATURE PREVIEW (APPLIED TO GENERATED REPORTS)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF1D4ED8)
                            )
                            if (decodedSignatureBitmap != null) {
                                Surface(
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(84.dp)
                                ) {
                                    Image(
                                        bitmap = decodedSignatureBitmap.asImageBitmap(),
                                        contentDescription = "Uploaded Admin Digital Signature",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(8.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = signatureScript.ifBlank { "H. J. SuperAdmin" },
                                    fontSize = 20.sp,
                                    fontStyle = FontStyle.Italic,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif,
                                    color = Color(0xFF1E3A8A)
                                )
                            }
                            Text(
                                "${signerName.ifBlank { "Super Admin" }} • ${signerTitle.ifBlank { "Chief Systems & Clinical Administrator" }}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                "Verified Admin Email: ${signatureConfig.signerEmail} • HMAC-SHA256: ${signatureConfig.hmacSha256.take(24)}...",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF047857)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = signerName,
                            onValueChange = { signerName = it },
                            enabled = canManageSignature,
                            label = { Text("Signer Name", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("admin_sig_signer_name_input")
                        )
                        OutlinedTextField(
                            value = signerTitle,
                            onValueChange = { signerTitle = it },
                            enabled = canManageSignature,
                            label = { Text("Signer Title", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("admin_sig_signer_title_input")
                        )
                    }

                    OutlinedTextField(
                        value = signatureScript,
                        onValueChange = { signatureScript = it },
                        enabled = canManageSignature,
                        label = { Text("Digital Signature Script / Text Stamp", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_sig_script_input")
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                signaturePickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = canManageSignature,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .testTag("upload_admin_signature_image_button")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (signatureImageBase64.isBlank()) "Upload Signature Image" else "Replace Signature Image",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (signatureImageBase64.isNotBlank()) {
                            OutlinedButton(
                                onClick = { signatureImageBase64 = "" },
                                enabled = canManageSignature,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.heightIn(min = 44.dp)
                            ) {
                                Text("Clear Image", fontSize = 12.sp)
                            }
                        }

                        Button(
                            onClick = {
                                adminViewModel.saveAdminDigitalSignature(
                                    signerName = signerName,
                                    signerTitle = signerTitle,
                                    signatureScript = signatureScript,
                                    signatureImageBase64 = signatureImageBase64
                                )
                            },
                            enabled = canManageSignature,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .testTag("save_admin_signature_button")
                        ) {
                            Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Digital Signature", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Security Policies & Python FastAPI Endpoint",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = backendUrl,
                        onValueChange = { backendUrl = it },
                        label = { Text("Python FastAPI Base URL (HTTPS/HTTP)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = sessionTimeout,
                            onValueChange = { sessionTimeout = it.filter { c -> c.isDigit() } },
                            label = { Text("Session TTL (mins)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = rateLimit,
                            onValueChange = { rateLimit = it.filter { c -> c.isDigit() } },
                            label = { Text("Rate Limit (req/min)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    ConfigToggleRow("Enforce 2FA for All Admin Accounts", enforce2Fa) {
                        enforce2Fa = it
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            SecureSessionManager.setBackendUrl(context, backendUrl)
                            adminViewModel.publishRemoteConfig(
                                remoteConfig.copy(
                                    sessionTimeoutMinutes = sessionTimeout.toIntOrNull() ?: 30,
                                    rateLimitRequestsPerMin = rateLimit.toIntOrNull() ?: 120,
                                    enforceTwoFactorForAdmins = enforce2Fa
                                ),
                                "Updated security policy & session TTL"
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Security & Backend Settings")
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Immutable Audit Logs (${filteredLogs.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF0F172A)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("ALL", "INFO", "WARNING", "CRITICAL").forEach { sev ->
                        FilterChip(
                            selected = severityFilter == sev,
                            onClick = { severityFilter = sev },
                            label = { Text(sev, fontSize = 10.sp) }
                        )
                    }
                }
            }
        }

        items(filteredLogs, key = { it.id }) { log ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${log.actionType} → ${log.targetEntity}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF0F172A),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        Text(
                            log.severity,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (log.severity) {
                                "CRITICAL" -> Color(0xFFDC2626)
                                "WARNING" -> Color(0xFFD97706)
                                else -> Color(0xFF2563EB)
                            }
                        )
                    }
                    Text(log.details, fontSize = 12.sp, color = Color(0xFF475569))
                    Text(
                        "Actor: @${log.actorUsername} (${log.actorRole}) • IP: ${log.ipAddress} • ${formatAdminTimestamp(log.timestamp)}",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

@Composable
fun AdminSystemToolsTab(
    subscriptions: List<SubscriptionRecordEntity>,
    backups: List<BackupSnapshotEntity>,
    adminViewModel: AdminViewModel
) {
    val aiOutput by adminViewModel.aiGeneratedOutput.collectAsStateWithLifecycle()
    var backupLabel by remember { mutableStateOf("") }
    var subToEdit by remember { mutableStateOf<SubscriptionRecordEntity?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // AI-Assisted Admin Co-Pilot
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "AI-Assisted Operations Co-Pilot",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        "Generate live security audits, support ticket triage responses, and broadcast copy.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(onClick = { adminViewModel.runAiAdminTool("EXECUTIVE_SUMMARY") }) {
                            Text("Executive Summary", fontSize = 12.sp)
                        }
                        FilledTonalButton(onClick = { adminViewModel.runAiAdminTool("SECURITY_AUDIT") }) {
                            Text("Security Audit", fontSize = 12.sp)
                        }
                        FilledTonalButton(onClick = { adminViewModel.runAiAdminTool("TICKET_TRIAGE") }) {
                            Text("Ticket Triage", fontSize = 12.sp)
                        }
                        FilledTonalButton(onClick = { adminViewModel.runAiAdminTool("NOTIFICATION_DRAFT") }) {
                            Text("Draft Broadcast", fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = aiOutput,
                            fontSize = 12.sp,
                            color = Color(0xFF1E293B),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // Subscription & Payment Management
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Subscription & Payment Management (${subscriptions.size})",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    subscriptions.forEach { sub ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "@${sub.username} • ${sub.planName}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    "$${"%.2f".format(sub.amountUsd)}/${sub.billingCycle} • ${sub.paymentMethod} • ${sub.status}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                            TextButton(onClick = { subToEdit = sub }) {
                                Text("Manage")
                            }
                        }
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                    }
                }
            }
        }

        // Backup & Recovery Controls
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Database Backup & Point-in-Time Recovery",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = backupLabel,
                            onValueChange = { backupLabel = it },
                            label = { Text("Snapshot Label") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                adminViewModel.createBackup(backupLabel)
                                backupLabel = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .heightIn(min = 52.dp)
                                .testTag("create_backup_button")
                        ) {
                            Text("Backup Now", maxLines = 1)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    backups.forEach { b ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(b.label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    "${b.userCount} users • ${b.sizeBytes} bytes • By @${b.createdBy} • ${formatAdminTimestamp(b.timestamp)}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                            OutlinedButton(onClick = { adminViewModel.restoreBackup(b) }) {
                                Text("Restore", fontSize = 12.sp)
                            }
                        }
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                    }
                }
            }
        }
    }

    subToEdit?.let { s ->
        var plan by remember(s) { mutableStateOf(s.planName) }
        var amount by remember(s) { mutableStateOf(s.amountUsd.toString()) }
        var status by remember(s) { mutableStateOf(s.status) }

        AlertDialog(
            onDismissRequest = { subToEdit = null },
            title = { Text("Manage Subscription: @${s.username}", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = plan,
                        onValueChange = { plan = it },
                        label = { Text("Plan Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount (USD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("ACTIVE", "PAST_DUE", "CANCELED", "REFUNDED").forEach { st ->
                            FilterChip(
                                selected = status == st,
                                onClick = { status = st },
                                label = { Text(st, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.updateSubscription(s, plan, amount.toDoubleOrNull() ?: 0.0, status)
                        subToEdit = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { subToEdit = null }) { Text("Cancel") }
            }
        )
    }
}
