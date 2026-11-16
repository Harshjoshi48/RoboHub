package com.example

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AdminBrandingTab(
    adminViewModel: AdminViewModel,
    brandingOverride: AppBrandingConfig? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val liveBranding by adminViewModel.appBranding.collectAsStateWithLifecycle()
    val branding = brandingOverride ?: liveBranding
    val uploadProgress by adminViewModel.brandingUploadProgress.collectAsStateWithLifecycle()
    val isLoading by adminViewModel.isLoading.collectAsStateWithLifecycle()
    val remoteConfig by adminViewModel.remoteConfig.collectAsStateWithLifecycle()
    val activeRoleOverride by adminViewModel.activeRoleOverride.collectAsStateWithLifecycle()
    val currentSession by adminViewModel.currentSession.collectAsStateWithLifecycle()

    val isSuperAdmin = remember(activeRoleOverride, currentSession) {
        adminViewModel.isSuperAdminAccount()
    }
    val launcherInfo = adminViewModel.launcherBrandingInfo

    var draftAppName by remember(branding.brandingVersion, branding.appName) {
        mutableStateOf(branding.appName)
    }
    var stagedLogoUri by remember { mutableStateOf<Uri?>(null) }
    var stagedValidatedLogo by remember { mutableStateOf<ValidatedBrandingLogo?>(null) }
    var pendingRemoveLogo by remember(branding.brandingVersion, branding.logoVersion) {
        mutableStateOf(false)
    }
    var logoValidationError by remember { mutableStateOf<String?>(null) }
    var notifyUsers by remember { mutableStateOf(true) }
    var summaryNote by remember { mutableStateOf("") }
    var inlineFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    var showRemoveLogoConfirm by remember { mutableStateOf(false) }
    var showRestoreLogoConfirm by remember { mutableStateOf(false) }
    var showRestoreAllConfirm by remember { mutableStateOf(false) }

    var launcherRecommendedVersion by remember(remoteConfig?.recommendedVersionName) {
        mutableStateOf(remoteConfig?.recommendedVersionName?.ifBlank { "1.1" } ?: "1.1")
    }
    var launcherForceUpdate by remember(remoteConfig?.forceUpdate) {
        mutableStateOf(remoteConfig?.forceUpdate ?: false)
    }
    var launcherReleaseSummary by remember {
        mutableStateOf("Install updated APK/AAB with refreshed launcher icon and home-screen name.")
    }

    val nameValidationError = remember(draftAppName) {
        AppBrandingValidator.validateAppName(draftAppName)
    }
    val isBusy = isLoading || uploadProgress != null

    val previewBranding = remember(branding, draftAppName, stagedLogoUri, pendingRemoveLogo) {
        val cleanName = draftAppName.trim().ifBlank { AppBrandingConfig.DEFAULT_APP_NAME }
        when {
            stagedLogoUri != null -> branding.copy(appName = cleanName, logoUrl = stagedLogoUri.toString())
            pendingRemoveLogo -> branding.copy(appName = cleanName, logoUrl = "", localCachedLogoPath = "")
            else -> branding.copy(appName = cleanName)
        }
    }
    val previewLogoModel: Any? = remember(stagedLogoUri, pendingRemoveLogo, branding) {
        when {
            stagedLogoUri != null -> stagedLogoUri
            pendingRemoveLogo -> null
            else -> null
        }
    }

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val res = withContext(Dispatchers.IO) {
                    AppBrandingValidator.readAndValidateUri(context, uri)
                }
                res.onSuccess { validated ->
                    stagedLogoUri = uri
                    stagedValidatedLogo = validated
                    pendingRemoveLogo = false
                    logoValidationError = null
                    inlineFeedback = true to "Logo staged (${validated.dimensionsLabel}, ${validated.formattedSize}). Tap Save & Publish to apply live."
                }.onFailure { err ->
                    stagedLogoUri = null
                    stagedValidatedLogo = null
                    logoValidationError = err.message ?: "Selected image failed validation."
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("admin_branding_tab"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Super Admin Gate / Authorization Notice
        if (!isSuperAdmin) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("branding_super_admin_restricted_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = "Super Admin Only",
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "Restricted: Super Admin Only",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF991B1B)
                            )
                            Text(
                                "Only verified Super Admin accounts can upload logos, rename the app, or publish branding updates.",
                                fontSize = 12.sp,
                                color = Color(0xFF7F1D1D)
                            )
                        }
                    }
                }
            }
        }

        // Header & Sync Status Card
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
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                "App Branding & Identity Management",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Branding v${branding.brandingVersion} • Logo v${branding.logoVersion} • Source: ${branding.lastSyncSource}",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                "Last updated by ${branding.updatedBy}",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (branding.isOfflineFallback) Color(0xFFFEF3C7) else Color(0xFFDBEAFE)
                        ) {
                            Text(
                                text = if (isSuperAdmin) "SUPER ADMIN" else "READ ONLY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSuperAdmin) Color(0xFF1D4ED8) else Color(0xFF92400E),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            adminViewModel.refreshAppBrandingNow { ok, msg ->
                                inlineFeedback = ok to msg
                            }
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("branding_refresh_button")
                    ) {
                        Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sync Active Branding From Cloud", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    if (inlineFeedback != null) {
                        val (ok, msg) = inlineFeedback!!
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (ok) Color(0xFFECFDF5) else Color(0xFFFEF2F2),
                            border = BorderStroke(
                                1.dp,
                                if (ok) Color(0xFF6EE7B7) else Color(0xFFFCA5A5)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("branding_status_banner")
                        ) {
                            Text(
                                text = msg,
                                fontSize = 12.sp,
                                color = if (ok) Color(0xFF065F46) else Color(0xFF991B1B),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // Section A: Application Logo Management
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "A. Application Logo Management",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Supported formats: PNG, JPG/JPEG, WebP • Max 5 MB • 64×64 to 2048×2048 px.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AppBrandingLogo(
                            branding = previewBranding,
                            overrideLogoModel = if (pendingRemoveLogo) null else previewLogoModel,
                            size = 68.dp,
                            cornerRadius = 16.dp,
                            showBorder = true,
                            testTag = "branding_editor_logo_preview"
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val logoStateLabel = when {
                                stagedValidatedLogo != null ->
                                    "Staged Upload: ${stagedValidatedLogo!!.fileName} (${stagedValidatedLogo!!.dimensionsLabel}, ${stagedValidatedLogo!!.formattedSize})"
                                pendingRemoveLogo ->
                                    "Logo marked for removal (reverts to default emblem on publish)"
                                branding.hasCustomLogo ->
                                    "Active Custom Logo (v${branding.logoVersion})"
                                else ->
                                    "Default NeuroGrip Emblem Active"
                            }
                            Text(
                                text = logoStateLabel,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = Color(0xFF1E293B)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when {
                                    stagedValidatedLogo != null -> "Verified & ready to upload to Firebase Storage."
                                    branding.hasCustomLogo && !pendingRemoveLogo -> "Synced via Firebase Storage CDN & local offline cache."
                                    else -> "Built-in adaptive vector emblem."
                                },
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    if (logoValidationError != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("branding_logo_error")
                        ) {
                            Text(
                                text = logoValidationError!!,
                                color = Color(0xFFB91C1C),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    if (uploadProgress != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Uploading logo to Firebase Storage (${(uploadProgress!! * 100).toInt()}%)...",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1D4ED8)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { uploadProgress!!.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .testTag("branding_upload_progress")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            logoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = isSuperAdmin && !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("branding_pick_logo_button")
                    ) {
                        Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (branding.hasCustomLogo || stagedLogoUri != null) "Replace Application Logo" else "Upload Application Logo",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showRemoveLogoConfirm = true },
                            enabled = isSuperAdmin && !isBusy && ((branding.hasCustomLogo && !pendingRemoveLogo) || stagedLogoUri != null),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("branding_remove_logo_button")
                        ) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remove Logo", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        OutlinedButton(
                            onClick = { showRestoreLogoConfirm = true },
                            enabled = isSuperAdmin && !isBusy && (branding.hasCustomLogo || stagedLogoUri != null || pendingRemoveLogo),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("branding_restore_default_logo_button")
                        ) {
                            Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Default Logo", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        // Section B: Application Name Management
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "B. Application Name Management",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Updates the in-app application title across headers, splash, login, and user navigation (1–30 characters).",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = draftAppName,
                        onValueChange = { if (it.length <= 40) draftAppName = it },
                        label = { Text("Application Name") },
                        placeholder = { Text(AppBrandingConfig.DEFAULT_APP_NAME) },
                        singleLine = true,
                        enabled = isSuperAdmin && !isBusy,
                        isError = nameValidationError != null,
                        supportingText = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = nameValidationError ?: "Live across Dashboard, Profile, Splash & Auth",
                                    color = if (nameValidationError != null) Color(0xFFDC2626) else Color(0xFF64748B),
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "${draftAppName.trim().length}/${AppBrandingValidator.MAX_NAME_LENGTH}",
                                    color = if (nameValidationError != null) Color(0xFFDC2626) else Color(0xFF64748B),
                                    fontSize = 11.sp
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("branding_name_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            draftAppName = AppBrandingConfig.DEFAULT_APP_NAME
                        },
                        enabled = isSuperAdmin && !isBusy && draftAppName != AppBrandingConfig.DEFAULT_APP_NAME,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("branding_restore_default_name_button")
                    ) {
                        Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore Default Name (${AppBrandingConfig.DEFAULT_APP_NAME})", fontSize = 13.sp)
                    }
                }
            }
        }

        // Section C: Live Branding Preview
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("branding_preview_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "C. Live Branding Preview",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // In-App Top Bar & Splash Preview
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "IN-APP HEADER & SPLASH PREVIEW",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AppBrandingLogo(
                                        branding = previewBranding,
                                        overrideLogoModel = if (pendingRemoveLogo) null else previewLogoModel,
                                        size = 34.dp,
                                        cornerRadius = 9.dp,
                                        testTag = "branding_preview_header_logo"
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = previewBranding.appName,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 16.sp,
                                            color = Color(0xFF1E3A8A),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.testTag("branding_preview_app_name")
                                        )
                                        Text(
                                            text = "HAND CONNECT • LIVE",
                                            fontSize = 10.sp,
                                            color = Color(0xFF64748B),
                                            letterSpacing = 1.sp
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFDBEAFE)
                                ) {
                                    Text(
                                        "ACTIVE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1D4ED8),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Home Screen Launcher Mockup Preview
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF0F172A),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "HOME SCREEN LAUNCHER MOCKUP",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8),
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                shadowElevation = 4.dp,
                                modifier = Modifier.size(62.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    AppBrandingLogo(
                                        branding = previewBranding,
                                        overrideLogoModel = if (pendingRemoveLogo) null else previewLogoModel,
                                        size = 54.dp,
                                        cornerRadius = 14.dp,
                                        testTag = "branding_preview_launcher_logo"
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = previewBranding.appName,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Installed APK drawer label: ${launcherInfo.installedAppLabel}",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // Section D: Publish Changes
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "D. Publish Branding Changes",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = summaryNote,
                        onValueChange = { summaryNote = it },
                        label = { Text("Publish Change Note (optional)") },
                        placeholder = { Text("e.g., Updated Q4 brand emblem & app name") },
                        singleLine = true,
                        enabled = isSuperAdmin && !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                "Notify Connected Users",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                "Broadcasts branding update notification to users alongside automatic live UI refresh.",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        Switch(
                            checked = notifyUsers,
                            onCheckedChange = { notifyUsers = it },
                            enabled = isSuperAdmin && !isBusy
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            adminViewModel.publishAppBranding(
                                newAppName = draftAppName,
                                stagedLogo = stagedValidatedLogo,
                                removeLogo = pendingRemoveLogo,
                                notifyUsers = notifyUsers,
                                summaryNote = summaryNote
                            ) { ok, msg ->
                                inlineFeedback = ok to msg
                                if (ok) {
                                    stagedLogoUri = null
                                    stagedValidatedLogo = null
                                    pendingRemoveLogo = false
                                    logoValidationError = null
                                    summaryNote = ""
                                }
                            }
                        },
                        enabled = isSuperAdmin && !isBusy && nameValidationError == null,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("branding_publish_button")
                    ) {
                        Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save & Publish", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showRestoreAllConfirm = true },
                        enabled = isSuperAdmin && !isBusy,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB45309)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("branding_restore_all_button")
                    ) {
                        Icon(Icons.Outlined.SettingsBackupRestore, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore All Defaults (Name & Logo)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }

        // Section E: Android Launcher Icon & App Drawer Name Advisory
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "E. Android Launcher Icon & App Drawer Release",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Package: ${launcherInfo.packageName}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                "Compiled APK Drawer Label: ${launcherInfo.installedAppLabel} (v${launcherInfo.versionName} • code ${launcherInfo.versionCode})",
                                fontSize = 12.sp,
                                color = Color(0xFF475569)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = launcherInfo.platformExplanation,
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = launcherRecommendedVersion,
                        onValueChange = { launcherRecommendedVersion = it },
                        label = { Text("Recommended APK/AAB Version (with new launcher assets)") },
                        singleLine = true,
                        enabled = isSuperAdmin && !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = launcherReleaseSummary,
                        onValueChange = { launcherReleaseSummary = it },
                        label = { Text("Update Prompt Message") },
                        singleLine = false,
                        maxLines = 2,
                        enabled = isSuperAdmin && !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Require Update Before Next Session",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1E293B)
                        )
                        Switch(
                            checked = launcherForceUpdate,
                            onCheckedChange = { launcherForceUpdate = it },
                            enabled = isSuperAdmin && !isBusy
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = {
                            adminViewModel.publishLauncherBrandingReleaseAdvisory(
                                recommendedVersionName = launcherRecommendedVersion,
                                releaseSummary = launcherReleaseSummary,
                                forceUpdate = launcherForceUpdate
                            ) { ok, msg ->
                                inlineFeedback = ok to msg
                            }
                        },
                        enabled = isSuperAdmin && !isBusy && launcherRecommendedVersion.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("branding_launcher_advisory_button")
                    ) {
                        Icon(Icons.Outlined.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Publish APK Update Prompt via Remote Config", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (showRemoveLogoConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveLogoConfirm = false },
            title = { Text("Remove Custom Logo?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (stagedLogoUri != null) {
                        "Discard the staged logo upload? Your currently published logo will remain active until you remove it."
                    } else {
                        "This marks the custom application logo for removal and reverts all apps to the built-in default emblem once published."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRemoveLogoConfirm = false
                        if (stagedLogoUri != null) {
                            stagedLogoUri = null
                            stagedValidatedLogo = null
                            logoValidationError = null
                            inlineFeedback = true to "Staged logo selection cleared."
                        } else {
                            pendingRemoveLogo = true
                            inlineFeedback = true to "Logo marked for removal. Tap Save & Publish to revert all users."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("branding_confirm_remove_logo_button")
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveLogoConfirm = false }) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("branding_confirm_remove_logo_dialog")
        )
    }

    if (showRestoreLogoConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreLogoConfirm = false },
            title = { Text("Restore Default Logo?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Immediately restore the built-in default application emblem for all connected users while preserving your current application name?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreLogoConfirm = false
                        stagedLogoUri = null
                        stagedValidatedLogo = null
                        pendingRemoveLogo = false
                        logoValidationError = null
                        adminViewModel.publishAppBranding(
                            newAppName = draftAppName,
                            stagedLogo = null,
                            removeLogo = true,
                            notifyUsers = notifyUsers,
                            summaryNote = "Restored default application logo"
                        ) { ok, msg ->
                            inlineFeedback = ok to msg
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    modifier = Modifier.testTag("branding_confirm_restore_logo_button")
                ) {
                    Text("Restore Default Logo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreLogoConfirm = false }) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("branding_confirm_restore_logo_dialog")
        )
    }

    if (showRestoreAllConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreAllConfirm = false },
            title = { Text("Restore Default Branding?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Reset BOTH the Application Name to \"${AppBrandingConfig.DEFAULT_APP_NAME}\" and the Application Logo back to the built-in default across Firebase and all connected users?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreAllConfirm = false
                        stagedLogoUri = null
                        stagedValidatedLogo = null
                        pendingRemoveLogo = false
                        logoValidationError = null
                        adminViewModel.restoreDefaultAppBranding(notifyUsers = notifyUsers) { ok, msg ->
                            inlineFeedback = ok to msg
                            if (ok) {
                                draftAppName = AppBrandingConfig.DEFAULT_APP_NAME
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309)),
                    modifier = Modifier.testTag("branding_confirm_restore_all_button")
                ) {
                    Text("Restore Defaults")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreAllConfirm = false }) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("branding_confirm_restore_all_dialog")
        )
    }
}
