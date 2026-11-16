package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DashboardScreen(
    username: String,
    onLogout: () -> Unit,
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToReports: () -> Unit = {},
    onNavigateToGestureStudio: () -> Unit = {},
    onNavigateToAIAssistant: () -> Unit = {},
    onNavigateToPermissions: () -> Unit = {},
    onNavigateToHandControl: () -> Unit = {},
    onNavigateToConnectDevices: () -> Unit = {},
    onNavigateToAdminPanel: (() -> Unit)? = null,
    viewModel: DashboardViewModel = viewModel(),
    adminViewModel: AdminViewModel = viewModel(),
    sduiStateOverride: SduiSyncState? = null,
    imageModelResolverOverride: ((String) -> Any)? = null,
    appBrandingOverride: AppBrandingConfig? = null
) {
    val liveSduiState by adminViewModel.sduiSyncState.collectAsStateWithLifecycle()
    val effectiveSduiState = sduiStateOverride ?: liveSduiState
    val sdui = effectiveSduiState.config
    val resolveSduiImage: (String) -> Any = { url ->
        imageModelResolverOverride?.invoke(url) ?: adminViewModel.resolveSduiImageModel(url)
    }

    val primaryBlue = SduiColorParser.parseOrDefault(sdui.theme.primaryBlueHex, Color(0xFF5A81A5))
    val buttonBlue = SduiColorParser.parseOrDefault(sdui.theme.buttonBlueHex, Color(0xFF4A7499))
    val textGray = SduiColorParser.parseOrDefault(sdui.theme.textGrayHex, Color(0xFF64748B))
    val darkText = SduiColorParser.parseOrDefault(sdui.theme.darkTextHex, Color(0xFF1E293B))
    val lightBg = SduiColorParser.parseOrDefault(sdui.theme.lightBgHex, Color(0xFFF5F7FA))
    val cardBg = SduiColorParser.parseOrDefault(sdui.theme.cardBgHex, Color.White)
    val greenColor = SduiColorParser.parseOrDefault(sdui.theme.greenColorHex, Color(0xFF10B981))
    val borderLight = SduiColorParser.parseOrDefault(sdui.theme.borderLightHex, Color(0xFFE2E8F0))
    val sectionCorner = sdui.theme.cornerRadiusDp.dp
    val sectionGap = sdui.theme.sectionSpacingDp.dp

    val isConnecting by viewModel.isConnecting.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val eegAttention by viewModel.eegAttention.collectAsState()
    val emgStrength by viewModel.emgStrength.collectAsState()
    val latency by viewModel.latency.collectAsState()
    val battery by viewModel.battery.collectAsState()
    val eegDataPoints by viewModel.eegDataPoints.collectAsState()
    val emgDataPoints by viewModel.emgDataPoints.collectAsState()

    val responseTime by viewModel.responseTime.collectAsState()
    val servoTemp by viewModel.servoTemp.collectAsState()
    val gripForce by viewModel.gripForce.collectAsState()
    val therapyProgress by viewModel.therapyProgress.collectAsState()

    val remoteConfig by adminViewModel.remoteConfig.collectAsState()
    val allBanners by adminViewModel.banners.collectAsStateWithLifecycle()
    val activeBanners by adminViewModel.activeBanners.collectAsState()
    val bannerPool = if (allBanners.isNotEmpty()) allBanners else activeBanners
    var showSupportTicketDialog by remember { mutableStateOf(false) }
    var bannerActionModal by remember { mutableStateOf<Pair<String, String>?>(null) }

    val context = LocalContext.current
    val liveAppBranding by AppBrandingCacheStore.stateFlow(context).collectAsStateWithLifecycle()
    val appBranding = appBrandingOverride ?: liveAppBranding
    val currentUserProfile by adminViewModel.currentUserProfile.collectAsStateWithLifecycle()
    val currentSession by adminViewModel.currentSession.collectAsStateWithLifecycle()
    val isAuthorizedAdmin by adminViewModel.isAuthorizedAdminSession.collectAsStateWithLifecycle()
    val allUsers by adminViewModel.users.collectAsStateWithLifecycle()
    val matchedDashUser = remember(currentUserProfile, currentSession, allUsers, username) {
        currentUserProfile ?: allUsers.firstOrNull { u ->
            (username.isNotBlank() && (u.username.equals(username, ignoreCase = true) || u.email.equals(username, ignoreCase = true))) ||
                (currentSession?.email?.isNotBlank() == true && u.email.equals(currentSession?.email, ignoreCase = true))
        }
    }
    val activeFullName = matchedDashUser?.name?.takeIf { it.isNotBlank() }
        ?: currentSession?.name?.takeIf { it.isNotBlank() }
        ?: username
    val activeEmail = matchedDashUser?.email?.takeIf { it.isNotBlank() }
        ?: currentSession?.email?.takeIf { it.isNotBlank() }
    val activePhotoUrl = matchedDashUser?.photoUrl?.takeIf { it.isNotBlank() }
        ?: currentSession?.photoUrl?.takeIf { it.isNotBlank() }
    val activePhotoUpdatedAt = maxOf(matchedDashUser?.photoUpdatedAt ?: 0L, currentSession?.photoUpdatedAt ?: 0L)
    val connectionMessage by viewModel.connectionMessage.collectAsState()

    val navigateBySduiRoute: (String) -> Unit = { route ->
        when (route) {
            "hand_control" -> {
                if (remoteConfig?.featureHandControl != false) {
                    onNavigateToHandControl()
                } else {
                    Toast.makeText(context, "Hand Control disabled by Administrator Remote Config", Toast.LENGTH_SHORT).show()
                }
            }
            "ai_assistant" -> {
                if (remoteConfig?.featureAiAssistant != false) {
                    onNavigateToAIAssistant()
                } else {
                    Toast.makeText(context, "AI Assistant disabled by Administrator Remote Config", Toast.LENGTH_SHORT).show()
                }
            }
            "reports" -> onNavigateToReports()
            "gesture_studio" -> onNavigateToGestureStudio()
            "permissions" -> onNavigateToPermissions()
            "connect_devices" -> onNavigateToConnectDevices()
            "settings" -> onNavigateToSettings()
            "notifications" -> onNavigateToNotifications()
            "profile" -> onNavigateToProfile()
            else -> {}
        }
    }

    val bannerCallbacks = remember(remoteConfig, username) {
        BannerNavigationCallbacks(
            onOpenScreen = { route -> navigateBySduiRoute(route) },
            onOpenCategory = { cat ->
                bannerActionModal = "Rehab Category" to "Explore exercises and calibration routines for '$cat' inside Gesture Studio."
            },
            onOpenProduct = { prod ->
                bannerActionModal = "NeuroGrip Module" to "Inspect device calibration & telemetry details for module '$prod'."
            }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.bindBleManager(context)
    }

    LaunchedEffect(connectionMessage) {
        connectionMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }


    var showPermissionDialog by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            showConfirmDialog = true
        } else {
            showPermissionDialog = true
        }
    }



    
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = Color.White,
                tonalElevation = 16.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                    label = { Text("Home", fontSize = 10.sp, fontWeight = FontWeight.Medium) },
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
                NavigationBarItem(
                    icon = { Icon(Icons.Outlined.PanTool, contentDescription = "Control") },
                    label = { Text("Control", fontSize = 10.sp) },
                    selected = false,
                    onClick = {
                        if (remoteConfig?.featureHandControl != false) {
                            onNavigateToHandControl()
                        } else {
                            Toast.makeText(context, "Hand Control disabled by Administrator Remote Config", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(unselectedIconColor = textGray, unselectedTextColor = textGray)
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Outlined.Psychology, contentDescription = "AI") },
                    label = { Text("AI", fontSize = 10.sp) },
                    selected = false,
                    onClick = {
                        if (remoteConfig?.featureAiAssistant != false) {
                            onNavigateToAIAssistant()
                        } else {
                            Toast.makeText(context, "AI Assistant disabled by Administrator Remote Config", Toast.LENGTH_SHORT).show()
                        }
                    },
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
                    label = { Text("Profile", fontSize = 10.sp) },
                    selected = false,
                    onClick = { onNavigateToProfile() },
                    colors = NavigationBarItemDefaults.colors(unselectedIconColor = textGray, unselectedTextColor = textGray)
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(lightBg)
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false).padding(end = 6.dp)
                ) {
                    SmartUserAvatar(
                        photoUrl = activePhotoUrl,
                        fullName = activeFullName,
                        email = activeEmail,
                        photoUpdatedAt = activePhotoUpdatedAt,
                        size = 40.dp,
                        fontSize = 16.sp,
                        borderColor = primaryBlue.copy(alpha = 0.25f),
                        borderWidth = 1.5.dp,
                        onClick = onNavigateToProfile,
                        contentDescription = "Profile",
                        testTag = "dashboard_top_bar_avatar"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        val displayUser = activeFullName.ifBlank { username }
                        Text(
                            displayUser,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = darkText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).background(if (isConnected) greenColor else Color.Red, CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (isConnected) sdui.topBar.onlineLabel else sdui.topBar.offlineLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = textGray,
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (appBranding.hasCustomLogo) {
                        AppBrandingLogo(
                            branding = appBranding,
                            size = 24.dp,
                            cornerRadius = 6.dp,
                            testTag = "dashboard_branding_logo"
                        )
                    } else {
                        Text(sdui.topBar.brandEmoji, fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    val effectiveBrandTitle = if (sdui.topBar.brandTitle == AppBrandingConfig.DEFAULT_APP_NAME) {
                        appBranding.appName
                    } else {
                        sdui.topBar.brandTitle
                    }
                    Text(
                        effectiveBrandTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = buttonBlue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("sdui_brand_title")
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isAuthorizedAdmin && onNavigateToAdminPanel != null) {
                        IconButton(
                            onClick = {
                                if (adminViewModel.returnToAdminPanel()) {
                                    onNavigateToAdminPanel()
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("dashboard_admin_console_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AdminPanelSettings,
                                contentDescription = "Return to Admin Panel",
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    IconButton(onClick = { showSupportTicketDialog = true }, modifier = Modifier.size(40.dp)) {
                        Icon(imageVector = Icons.Outlined.SupportAgent, contentDescription = "Support Desk", tint = textGray, modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = onNavigateToNotifications, modifier = Modifier.size(40.dp)) {
                        Icon(imageVector = Icons.Outlined.Notifications, contentDescription = "Notifications", tint = textGray, modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = onNavigateToSettings, modifier = Modifier.size(40.dp)) {
                        Icon(imageVector = Icons.Outlined.Settings, contentDescription = "Settings", tint = textGray, modifier = Modifier.size(22.dp))
                    }
                }
            }

            // Live Remote Config Maintenance / Force Update / Banner Feed
            remoteConfig?.let { cfg ->
                if (cfg.maintenanceMode) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                        border = BorderStroke(1.dp, Color(0xFFEF4444))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("MAINTENANCE MODE ACTIVE", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Color(0xFFB91C1C))
                            Text(cfg.maintenanceMessage, fontSize = 12.sp, color = Color(0xFF7F1D1D))
                        }
                    }
                }
                if (cfg.forceUpdate) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Recommended App Update v${cfg.recommendedVersionName}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF92400E))
                            Text(cfg.releaseNotes, fontSize = 12.sp, color = Color(0xFF78350F))
                        }
                    }
                }
            }

            DynamicBannerSlotHost(
                banners = bannerPool,
                position = BannerPosition.TOP_STICKY,
                username = username,
                adminViewModel = adminViewModel,
                callbacks = bannerCallbacks
            )

            DynamicBannerSlotHost(
                banners = bannerPool,
                position = BannerPosition.HOME_TOP,
                username = username,
                adminViewModel = adminViewModel,
                callbacks = bannerCallbacks
            )

            Spacer(modifier = Modifier.height(6.dp))

            DynamicBannerSlotHost(
                banners = bannerPool,
                position = BannerPosition.HOME_SLIDER,
                username = username,
                adminViewModel = adminViewModel,
                callbacks = bannerCallbacks
            )

            DynamicBannerSlotHost(
                banners = bannerPool,
                position = BannerPosition.POPUP,
                username = username,
                adminViewModel = adminViewModel,
                callbacks = bannerCallbacks
            )

            if (showSupportTicketDialog) {
                var subj by remember { mutableStateOf("") }
                var msg by remember { mutableStateOf("") }
                var cat by remember { mutableStateOf("HARDWARE") }
                AlertDialog(
                    onDismissRequest = { showSupportTicketDialog = false },
                    title = { Text("Submit Support Ticket", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = subj,
                                onValueChange = { subj = it },
                                label = { Text("Issue Subject") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = msg,
                                onValueChange = { msg = it },
                                label = { Text("Describe hardware or app issue") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                adminViewModel.submitUserTicket(username, "$username@neurogrip.io", subj, msg, cat)
                                showSupportTicketDialog = false
                                Toast.makeText(context, "Support ticket sent to Admin Console!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Submit")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showSupportTicketDialog = false },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) { Text("Cancel") }
                    }
                )
            }

            // Render Server-Driven Dashboard Sections dynamically while preserving exact baseline default order and layout
            var renderedAnySection = false
            sdui.sectionsOrder.forEach { sectionId ->
                when (sectionId) {
                    "promo_banner" -> {
                        if (sdui.promoBanner.enabled) {
                            if (renderedAnySection) Spacer(modifier = Modifier.height(sectionGap))
                            renderedAnySection = true
                            val promoBg = SduiColorParser.parseOrDefault(sdui.promoBanner.backgroundHex, Color(0xFFEFF6FF))
                            val promoBorder = SduiColorParser.parseOrDefault(sdui.promoBanner.borderHex, Color(0xFFBFDBFE))
                            val promoText = SduiColorParser.parseOrDefault(sdui.promoBanner.textColorHex, Color(0xFF1E3A8A))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .testTag("sdui_promo_banner"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = promoBg),
                                border = BorderStroke(1.dp, promoBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    if (sdui.promoBanner.imageUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = resolveSduiImage(sdui.promoBanner.imageUrl),
                                            contentDescription = sdui.promoBanner.title,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(10.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(sdui.promoBanner.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = promoText)
                                        Text(sdui.promoBanner.subtitle, fontSize = 12.sp, color = promoText.copy(alpha = 0.85f))
                                    }
                                    TextButton(onClick = { navigateBySduiRoute(sdui.promoBanner.ctaRoute) }) {
                                        Text(sdui.promoBanner.ctaText, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = promoText)
                                    }
                                }
                            }
                        }
                    }

                    "hero" -> {
                        if (sdui.hero.visible) {
                            if (renderedAnySection) Spacer(modifier = Modifier.height(sectionGap))
                            renderedAnySection = true
                            val heroStart = SduiColorParser.parseOrDefault(sdui.hero.gradientStartHex, Color(0xFFF8FAFC))
                            val heroEnd = SduiColorParser.parseOrDefault(sdui.hero.gradientEndHex, Color(0xFFE2E8F0))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp),
                                shape = RoundedCornerShape(sectionCorner),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 196.dp)
                                        .background(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(heroStart, heroEnd)
                                            )
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 12.dp)
                                        ) {
                                            Text(
                                                sdui.hero.title,
                                                style = MaterialTheme.typography.headlineSmall.copy(fontSize = sdui.hero.titleFontSizeSp.sp),
                                                fontWeight = FontWeight.Bold,
                                                color = darkText,
                                                modifier = Modifier.testTag("sdui_hero_title")
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                sdui.hero.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = textGray,
                                                lineHeight = 16.sp,
                                                modifier = Modifier.testTag("sdui_hero_subtitle")
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            val connectLabel = sdui.hero.connectButtonText.trim().ifBlank { "Connect ESP32" }
                                            val disconnectLabel = sdui.hero.disconnectButtonText.trim().ifBlank { "Disconnect ESP32" }
                                            val connectingLabel = sdui.hero.connectingText.trim().ifBlank { "Connecting..." }
                                            Button(
                                                onClick = {
                                                    if (isConnecting) return@Button
                                                    if (isConnected) {
                                                        viewModel.toggleConnection(context)
                                                    } else {
                                                        val isGranted = ContextCompat.checkSelfPermission(
                                                            context,
                                                            Manifest.permission.ACCESS_FINE_LOCATION
                                                        ) == PackageManager.PERMISSION_GRANTED
                                                        if (isGranted) {
                                                            showConfirmDialog = true
                                                        } else {
                                                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                                        }
                                                    }
                                                },
                                                shape = RoundedCornerShape(20.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isConnected) Color(0xFFEF4444) else buttonBlue,
                                                    contentColor = Color.White
                                                ),
                                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .testTag("sdui_hero_connect_button")
                                            ) {
                                                if (isConnecting) {
                                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(connectingLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                } else {
                                                    Icon(
                                                        imageVector = if (isConnected) Icons.Filled.BluetoothConnected else Icons.Filled.BluetoothSearching,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = if (isConnected) disconnectLabel else connectLabel,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                        }

                                        AsyncImage(
                                            model = resolveSduiImage(sdui.hero.imageUrl),
                                            contentDescription = "Hand",
                                            modifier = Modifier
                                                .width(104.dp)
                                                .height(100.dp)
                                                .clip(RoundedCornerShape(14.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        }
                    }

                    "ai_assistant" -> {
                        if (sdui.aiAssistant.visible) {
                            if (renderedAnySection) Spacer(modifier = Modifier.height(sectionGap))
                            renderedAnySection = true
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp),
                                shape = RoundedCornerShape(sectionCorner),
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text(sdui.aiAssistant.headerLabel, style = MaterialTheme.typography.labelSmall, color = textGray, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(imageVector = Icons.Outlined.Psychology, contentDescription = "AI", tint = buttonBlue, modifier = Modifier.size(28.dp))
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(sdui.aiAssistant.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = darkText)
                                            Text(sdui.aiAssistant.subtitle, style = MaterialTheme.typography.bodySmall, color = textGray)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    OutlinedButton(
                                        onClick = { navigateBySduiRoute(sdui.aiAssistant.ctaRoute) },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                        shape = RoundedCornerShape(20.dp),
                                        border = BorderStroke(1.dp, buttonBlue.copy(alpha = 0.3f))
                                    ) {
                                        Text(sdui.aiAssistant.ctaText, color = buttonBlue, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                                    }
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        StatItem(Icons.Outlined.MonitorHeart, "Hardware", if (isConnected) "Stable" else "Wait", darkText, textGray, Modifier.weight(1f))
                                        StatItem(Icons.Outlined.Psychology, "AI Engine", "Active", darkText, textGray, Modifier.weight(1f))
                                        StatItem(Icons.Outlined.Wifi, "Latency", if (isConnected) "${latency}ms" else "--", darkText, textGray, Modifier.weight(1f))
                                        StatItem(Icons.Outlined.BatteryFull, "Battery", if (isConnected) "${battery}%" else "--", darkText, textGray, Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    "permissions" -> {
                        if (sdui.permissions.visible) {
                            if (renderedAnySection) Spacer(modifier = Modifier.height(sectionGap))
                            renderedAnySection = true
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .clickable { navigateBySduiRoute(sdui.permissions.actionRoute) },
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                                border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(buttonBlue, RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Shield,
                                            contentDescription = "Permissions",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            sdui.permissions.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = darkText
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            sdui.permissions.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = textGray
                                        )
                                    }
                                    Text(
                                        sdui.permissions.actionText,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = buttonBlue
                                    )
                                }
                            }
                        }
                    }

                    "control_modes" -> {
                        if (sdui.controlModes.visible) {
                            if (renderedAnySection) Spacer(modifier = Modifier.height(20.dp))
                            renderedAnySection = true
                            Text(
                                sdui.controlModes.headerLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = textGray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                                sdui.controlModes.items.forEachIndexed { idx, mode ->
                                    if (idx > 0) Spacer(modifier = Modifier.height(12.dp))
                                    ModeItem(
                                        icon = sduiIconVector(mode.iconKey),
                                        title = mode.title,
                                        cardBg = cardBg,
                                        darkText = darkText,
                                        borderLight = borderLight,
                                        badgeText = mode.badgeText,
                                        onClick = { navigateBySduiRoute(mode.route) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            DynamicBannerSlotHost(
                                banners = bannerPool,
                                position = BannerPosition.HOME_MIDDLE,
                                username = username,
                                adminViewModel = adminViewModel,
                                callbacks = bannerCallbacks
                            )
                        }
                    }

                    "bio_signals", "prosthetic_status" -> {
                        // Requirement 12: Live Bio-Signals and Prosthetic Status sections removed from User Home Page
                    }

                    "quick_actions" -> {
                        if (sdui.quickActions.visible) {
                            if (renderedAnySection) Spacer(modifier = Modifier.height(sectionGap))
                            renderedAnySection = true
                            Text(
                                sdui.quickActions.headerLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 24.dp),
                                color = darkText
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                sdui.quickActions.actions.forEach { action ->
                                    QuickActionCard(
                                        icon = sduiIconVector(action.iconKey),
                                        label = action.label,
                                        modifier = Modifier.weight(1f),
                                        cardBg = cardBg,
                                        darkText = darkText,
                                        onClick = { navigateBySduiRoute(action.route) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            DynamicBannerSlotHost(
                banners = bannerPool,
                position = BannerPosition.HOME_BOTTOM,
                username = username,
                adminViewModel = adminViewModel,
                callbacks = bannerCallbacks
            )

            Spacer(modifier = Modifier.height(64.dp))
        }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DynamicBannerSlotHost(
                banners = bannerPool,
                position = BannerPosition.FLOATING,
                username = username,
                adminViewModel = adminViewModel,
                callbacks = bannerCallbacks
            )
            DynamicBannerSlotHost(
                banners = bannerPool,
                position = BannerPosition.BOTTOM_STICKY,
                username = username,
                adminViewModel = adminViewModel,
                callbacks = bannerCallbacks
            )
        }

        bannerActionModal?.let { (modalTitle, modalMsg) ->
            AlertDialog(
                onDismissRequest = { bannerActionModal = null },
                title = { Text(modalTitle, fontWeight = FontWeight.Bold) },
                text = { Text(modalMsg, fontSize = 14.sp) },
                confirmButton = {
                    Button(
                        onClick = {
                            val openStudio = modalTitle.contains("Category", ignoreCase = true)
                            bannerActionModal = null
                            if (openStudio) onNavigateToGestureStudio() else onNavigateToHandControl()
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(if (modalTitle.contains("Category", ignoreCase = true)) "Open Studio" else "Open Control")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { bannerActionModal = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Close")
                    }
                }
            )
        }
        }
    }




        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = { Text("Connect ESP32") },
                text = { Text("Search and connect to the ESP32 network via Wi-Fi?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showConfirmDialog = false
                            viewModel.toggleConnection(context)
                        }
                    ) {
                        Text("Yes")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) {
                        Text("No")
                    }
                }
            )
        }

        if (showPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showPermissionDialog = false },
                title = { Text("Permission Required") },
                text = { Text("Location permission is required to connect to the ESP32 via Wi-Fi. Please enable it in the app settings.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showPermissionDialog = false
                            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    ) {
                        Text("Open Settings")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
}

@Composable
fun StatItem(icon: ImageVector, label: String, value: String, darkText: Color, textGray: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = textGray, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = textGray, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = darkText, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ModeItem(
    icon: ImageVector,
    title: String,
    cardBg: Color,
    darkText: Color,
    borderLight: Color,
    badgeText: String = "READY",
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, borderLight)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).padding(end = 10.dp)
            ) {
                Box(
                    modifier = Modifier.size(40.dp).background(Color(0xFFF1F5F9), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = darkText)
            }
            ContainerReadyBadge(badgeText)
        }
    }
}

@Composable
fun ContainerReadyBadge(badgeText: String = "READY") {
    Box(
        modifier = Modifier
            .background(Color(0xFF10B981).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(badgeText, style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 9.sp)
    }
}

private fun sduiIconVector(iconKey: String): ImageVector {
    return when (iconKey) {
        "psychology" -> Icons.Outlined.Psychology
        "antenna" -> Icons.Outlined.SettingsInputAntenna
        "phone" -> Icons.Outlined.PhoneAndroid
        "hand" -> Icons.Outlined.PanTool
        "chart" -> Icons.Outlined.BarChart
        "gesture" -> Icons.Outlined.Gesture
        "shield" -> Icons.Outlined.Shield
        "sensors" -> Icons.Outlined.Sensors
        "settings" -> Icons.Outlined.Settings
        "notifications" -> Icons.Outlined.Notifications
        else -> Icons.Outlined.Widgets
    }
}

@Composable
fun StatusLine(label: String, value: String, darkText: Color, textGray: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = textGray,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 4.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = darkText,
            maxLines = 1
        )
    }
}

@Composable
fun QuickActionCard(icon: ImageVector, label: String, modifier: Modifier = Modifier, cardBg: Color, darkText: Color, onClick: () -> Unit = {}) {
    Card(
        modifier = modifier.heightIn(min = 104.dp).clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = darkText,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
