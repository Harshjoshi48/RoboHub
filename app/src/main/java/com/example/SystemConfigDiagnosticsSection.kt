package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ConfigSectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = title, tint = accentColor, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF0F172A))
                    Text(subtitle, fontSize = 12.sp, color = Color(0xFF64748B))
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
fun StatusKeyValueRow(label: String, value: String, isLocked: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color(0xFF475569),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (isLocked) {
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Read-only field",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "LOCKED",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Surface(
            color = Color(0xFFF8FAFC),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF0F172A),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun ConfigSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF1E293B))
            Text(subtitle, fontSize = 11.sp, color = Color(0xFF64748B))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
fun SystemConfigDiagnosticsAndHistorySection(
    envDiagnostics: List<EnvVarDiagnosticDto>,
    configHistory: List<ConfigHistoryEntity>,
    auditLogs: List<AuditLogEntity>,
    isSuperAdmin: Boolean,
    onRequestRollback: (ConfigHistoryEntity) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // SECTION 8: Environment & Deployment Diagnostics
        ConfigSectionCard(
            title = "8. Environment & Deployment Diagnostics",
            subtitle = "Classification of all 10 server/client environment variables, secret masking, and rotation rules",
            icon = Icons.Outlined.Terminal,
            accentColor = Color(0xFF2563EB)
        ) {
            Surface(
                color = Color(0xFFFFFBEB),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Server Environment Protection Policy",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF92400E)
                    )
                    Text(
                        "• The Android Admin Panel never writes directly to OS environment variables.\n" +
                            "• HMAC secrets, password hashes, and service-account keys are masked and never stored in Cloud Firestore.\n" +
                            "• Infrastructure variables require AI Studio Secrets Panel update + APK rebuild or Cloud Run redeployment.",
                        fontSize = 11.sp,
                        color = Color(0xFF78350F)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            envDiagnostics.forEach { item ->
                val badgeColor = when (item.classification) {
                    "SAFE_RUNTIME_SETTING" -> Color(0xFF059669)
                    "SERVER_ONLY_SECRET" -> Color(0xFFDC2626)
                    "DEPLOYMENT_INFRASTRUCTURE_SETTING" -> Color(0xFF2563EB)
                    else -> Color(0xFFD97706) // INITIAL_BOOTSTRAP_SETTING
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("env_var_card_${item.name}")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.name,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF0F172A),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )
                            Surface(
                                color = badgeColor.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = item.classification,
                                    color = badgeColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Value: ${item.displayValue}",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = if (item.isSecret) Color(0xFF991B1B) else Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.managementGuidance,
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                        if (item.requiresRedeployOrRestart) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Requires: Backend Restart / Redeployment or Firebase Console Action",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309)
                            )
                        }
                    }
                }
            }
        }

        // SECTION 9: Configuration History & Audit Logs
        ConfigSectionCard(
            title = "9. Configuration History, Rollback & Audit Trail",
            subtitle = "Immutable version snapshots (`config_history`) and security audit events (`audit_logs`)",
            icon = Icons.Outlined.History,
            accentColor = Color(0xFF475569)
        ) {
            Text(
                "Versioned Snapshots (${configHistory.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(6.dp))

            if (configHistory.isEmpty()) {
                Text("No configuration snapshots recorded yet.", fontSize = 12.sp, color = Color(0xFF64748B))
            } else {
                configHistory.take(8).forEach { snap ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    "Version v${snap.configVersion} • by @${snap.publishedBy}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    snap.summary,
                                    fontSize = 11.sp,
                                    color = Color(0xFF475569),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    formatAdminTimestamp(snap.timestamp),
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            OutlinedButton(
                                onClick = { onRequestRollback(snap) },
                                enabled = isSuperAdmin,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("rollback_v${snap.configVersion}_button")
                            ) {
                                Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Rollback", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                "System Configuration Security Audit Trail",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(6.dp))

            val configLogs = auditLogs.filter {
                it.actionType.contains("CONFIG") ||
                    it.actionType.contains("CREDENTIAL") ||
                    it.actionType.contains("OTP") ||
                    it.actionType.contains("SYSTEM")
            }.ifEmpty { auditLogs.take(10) }

            configLogs.take(12).forEach { log ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
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
                            "${log.actionType} • @${log.actorUsername} (${log.actorRole})",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            log.details,
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    Text(
                        formatAdminTimestamp(log.timestamp),
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                HorizontalDivider(color = Color(0xFFF1F5F9))
            }
        }
    }
}
