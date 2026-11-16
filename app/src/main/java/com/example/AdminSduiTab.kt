package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AdminSduiTab(
    sduiSyncState: SduiSyncState,
    sduiHistory: List<SduiConfigHistoryEntity>,
    adminViewModel: AdminViewModel
) {
    var draft by remember(sduiSyncState.activeVersion, sduiSyncState.contentSha256) {
        mutableStateOf(sduiSyncState.config)
    }
    var changeSummary by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val draftValidation = remember(draft) { SduiSchemaValidator.validateConfig(draft) }
    val canManage = adminViewModel.hasPermission(AdminPermission.MANAGE_REMOTE_CONFIG)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Status & Live Sync Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                "Real-Time Server-Driven UI (SDUI)",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                "Live Version: v${sduiSyncState.activeVersion} • Schema v${sduiSyncState.config.schemaVersion} • Source: ${sduiSyncState.lastSyncSource}",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (sduiSyncState.isOfflineFallback) Color(0xFFFEF3C7) else Color(0xFFDCFCE7)
                        ) {
                            Text(
                                text = if (sduiSyncState.isOfflineFallback) "OFFLINE CACHE" else "LIVE SYNC",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (sduiSyncState.isOfflineFallback) Color(0xFF92400E) else Color(0xFF166534),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (!sduiSyncState.lastValidationError.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                        ) {
                            Text(
                                "Last rejected payload: ${sduiSyncState.lastValidationError}",
                                fontSize = 11.sp,
                                color = Color(0xFF991B1B),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { adminViewModel.refreshSduiFromServer() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("sdui_sync_now_button")
                        ) {
                            Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync Now", fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                        OutlinedButton(
                            enabled = canManage,
                            onClick = {
                                adminViewModel.resetSduiToBaseline()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("sdui_reset_baseline_button")
                        ) {
                            Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset Baseline", fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }

        // Quick Theme Presets
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Quick Theme & Banner Presets",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF1E293B)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { draft = SduiDashboardConfig.baselineDefault() },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Text("Default UI", fontSize = 11.sp, textAlign = TextAlign.Center)
                        }
                        FilledTonalButton(
                            onClick = {
                                draft = draft.copy(
                                    theme = draft.theme.copy(
                                        buttonBlueHex = "#1D4ED8",
                                        lightBgHex = "#EFF6FF",
                                        primaryBlueHex = "#2563EB"
                                    ),
                                    promoBanner = draft.promoBanner.copy(
                                        enabled = true,
                                        title = "Clinical Rehab Update",
                                        subtitle = "Try the updated 5-minute EMG calibration workflow.",
                                        ctaText = "Open Control",
                                        ctaRoute = "hand_control"
                                    )
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Text("Clinical Blue", fontSize = 11.sp, textAlign = TextAlign.Center)
                        }
                        FilledTonalButton(
                            onClick = {
                                draft = draft.copy(
                                    theme = draft.theme.copy(
                                        buttonBlueHex = "#047857",
                                        lightBgHex = "#ECFDF5",
                                        primaryBlueHex = "#059669"
                                    ),
                                    promoBanner = draft.promoBanner.copy(
                                        enabled = true,
                                        title = "Recovery Milestone Mode",
                                        subtitle = "Focus on daily gesture accuracy & bio-signal stability.",
                                        ctaText = "Studio",
                                        ctaRoute = "gesture_studio",
                                        backgroundHex = "#ECFDF5",
                                        borderHex = "#A7F3D0",
                                        textColorHex = "#065F46"
                                    )
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Text("Emerald Focus", fontSize = 11.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }

        // Theme & Colors Editor
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Theme Colors & Brand Header",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = draft.topBar.brandTitle,
                            onValueChange = { draft = draft.copy(topBar = draft.topBar.copy(brandTitle = it)) },
                            label = { Text("Top Brand Title") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = draft.theme.buttonBlueHex,
                            onValueChange = { draft = draft.copy(theme = draft.theme.copy(buttonBlueHex = it)) },
                            label = { Text("Primary Accent Hex") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("sdui_primary_hex_input")
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = draft.theme.lightBgHex,
                            onValueChange = { draft = draft.copy(theme = draft.theme.copy(lightBgHex = it)) },
                            label = { Text("Background Hex") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = draft.theme.darkTextHex,
                            onValueChange = { draft = draft.copy(theme = draft.theme.copy(darkTextHex = it)) },
                            label = { Text("Dark Text Hex") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Dynamic Promotional Banner Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Server-Driven Promo Banner", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Disabled in baseline so default layout stays untouched", fontSize = 11.sp, color = Color(0xFF64748B))
                        }
                        Switch(
                            checked = draft.promoBanner.enabled,
                            onCheckedChange = { enabled ->
                                draft = draft.copy(promoBanner = draft.promoBanner.copy(enabled = enabled))
                            },
                            modifier = Modifier.testTag("sdui_promo_enabled_switch")
                        )
                    }
                    if (draft.promoBanner.enabled) {
                        OutlinedTextField(
                            value = draft.promoBanner.title,
                            onValueChange = { draft = draft.copy(promoBanner = draft.promoBanner.copy(title = it)) },
                            label = { Text("Banner Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("sdui_promo_title_input")
                        )
                        OutlinedTextField(
                            value = draft.promoBanner.subtitle,
                            onValueChange = { draft = draft.copy(promoBanner = draft.promoBanner.copy(subtitle = it)) },
                            label = { Text("Banner Subtitle") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = draft.promoBanner.ctaText,
                                onValueChange = { draft = draft.copy(promoBanner = draft.promoBanner.copy(ctaText = it)) },
                                label = { Text("CTA Label") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = draft.promoBanner.ctaRoute,
                                onValueChange = { draft = draft.copy(promoBanner = draft.promoBanner.copy(ctaRoute = it)) },
                                label = { Text("Target Route") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = draft.promoBanner.imageUrl,
                            onValueChange = { draft = draft.copy(promoBanner = draft.promoBanner.copy(imageUrl = it)) },
                            label = { Text("Optional HTTPS Banner Image URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Hero Section & Section Ordering
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Hero Banner & Sections Copy",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                    OutlinedTextField(
                        value = draft.hero.title,
                        onValueChange = { draft = draft.copy(hero = draft.hero.copy(title = it)) },
                        label = { Text("Hero Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("sdui_hero_title_input")
                    )
                    OutlinedTextField(
                        value = draft.hero.subtitle,
                        onValueChange = { draft = draft.copy(hero = draft.hero.copy(subtitle = it)) },
                        label = { Text("Hero Subtitle") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = draft.hero.connectButtonText,
                            onValueChange = { draft = draft.copy(hero = draft.hero.copy(connectButtonText = it)) },
                            label = { Text("Connect Button Label") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = draft.aiAssistant.ctaText,
                            onValueChange = { draft = draft.copy(aiAssistant = draft.aiAssistant.copy(ctaText = it)) },
                            label = { Text("AI Button Label") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = draft.hero.imageUrl,
                        onValueChange = { draft = draft.copy(hero = draft.hero.copy(imageUrl = it)) },
                        label = { Text("Hero HTTPS Image URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = draft.prostheticStatus.quoteText,
                        onValueChange = { draft = draft.copy(prostheticStatus = draft.prostheticStatus.copy(quoteText = it)) },
                        label = { Text("Prosthetic Status Motivation Quote") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Section Visibility & Ordering", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    ConfigToggleRow("Show Hero Card", draft.hero.visible) {
                        draft = draft.copy(hero = draft.hero.copy(visible = it))
                    }
                    ConfigToggleRow("Show AI Assistant Card", draft.aiAssistant.visible) {
                        draft = draft.copy(aiAssistant = draft.aiAssistant.copy(visible = it))
                    }
                    ConfigToggleRow("Show Permissions Card", draft.permissions.visible) {
                        draft = draft.copy(permissions = draft.permissions.copy(visible = it))
                    }
                    ConfigToggleRow("Show Control Modes", draft.controlModes.visible) {
                        draft = draft.copy(controlModes = draft.controlModes.copy(visible = it))
                    }
                    ConfigToggleRow("Show Live Bio-Signals", draft.bioSignals.visible) {
                        draft = draft.copy(bioSignals = draft.bioSignals.copy(visible = it))
                    }
                    ConfigToggleRow("Show Prosthetic Status", draft.prostheticStatus.visible) {
                        draft = draft.copy(prostheticStatus = draft.prostheticStatus.copy(visible = it))
                    }
                    ConfigToggleRow("Show Quick Actions", draft.quickActions.visible) {
                        draft = draft.copy(quickActions = draft.quickActions.copy(visible = it))
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Section Render Order", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF475569))
                    draft.sectionsOrder.forEachIndexed { idx, secId ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${idx + 1}. $secId",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )
                            Row {
                                IconButton(
                                    enabled = idx > 0,
                                    onClick = {
                                        val mutable = draft.sectionsOrder.toMutableList()
                                        val tmp = mutable[idx - 1]
                                        mutable[idx - 1] = mutable[idx]
                                        mutable[idx] = tmp
                                        draft = draft.copy(sectionsOrder = mutable)
                                    },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move Up")
                                }
                                IconButton(
                                    enabled = idx < draft.sectionsOrder.lastIndex,
                                    onClick = {
                                        val mutable = draft.sectionsOrder.toMutableList()
                                        val tmp = mutable[idx + 1]
                                        mutable[idx + 1] = mutable[idx]
                                        mutable[idx] = tmp
                                        draft = draft.copy(sectionsOrder = mutable)
                                    },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move Down")
                                }
                            }
                        }
                    }

                    // Real-time Schema Validator feedback
                    if (draftValidation is SduiValidationResult.Invalid) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, Color(0xFFEF4444))
                        ) {
                            Text(
                                "Schema Validation Error: ${draftValidation.formattedReason()}",
                                fontSize = 11.sp,
                                color = Color(0xFFB91C1C),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else if (localError != null) {
                        Text(localError ?: "", fontSize = 11.sp, color = Color(0xFFB91C1C))
                    }

                    OutlinedTextField(
                        value = changeSummary,
                        onValueChange = { changeSummary = it },
                        label = { Text("Release / UI Update Note") },
                        placeholder = { Text("e.g., Updated hero headline & enabled rehab promo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        enabled = canManage && draftValidation is SduiValidationResult.Valid,
                        onClick = {
                            localError = null
                            adminViewModel.publishSduiConfig(
                                draft = draft,
                                summary = changeSummary.ifBlank { "Updated live SDUI layout" }
                            ) { ok, msg ->
                                if (ok) changeSummary = "" else localError = msg
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("publish_sdui_button")
                    ) {
                        Icon(Icons.Outlined.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Publish Live SDUI Update", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        // Version History & Rollback
        item {
            Text(
                "SDUI Version History & Rollback (${sduiHistory.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color(0xFF0F172A)
            )
        }

        items(sduiHistory, key = { "${it.configVersion}_${it.id}" }) { hist ->
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
                            "SDUI v${hist.configVersion} • ${hist.summary}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            "By @${hist.publishedBy} • ${formatAdminTimestamp(hist.timestamp)}",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    OutlinedButton(
                        enabled = canManage,
                        onClick = { adminViewModel.rollbackSduiConfig(hist) }
                    ) {
                        Text("Rollback", fontSize = 12.sp)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}
