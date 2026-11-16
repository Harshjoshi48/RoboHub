package com.example

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    onNavigateToHome: () -> Unit = onBack,
    onNavigateToControl: () -> Unit = {},
    onNavigateToAI: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    dashboardViewModel: DashboardViewModel = viewModel(),
    adminViewModel: AdminViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val primaryBlue = Color(0xFF1D4ED8)
    val darkNavy = Color(0xFF0F172A)
    val slateText = Color(0xFF334155)
    val mutedGray = Color(0xFF64748B)
    val lightBg = Color(0xFFF8FAFC)
    val successGreen = Color(0xFF059669)

    // Application Icon / Branding Logo
    val branding by AppBrandingCacheStore.stateFlow(context).collectAsStateWithLifecycle()
    val appIconBitmap = remember(context, branding.logoUrl, branding.localCachedLogoPath, branding.brandingVersion) {
        RoboHubReportGenerator.loadAppIconOrBrandingBitmap(context, 128)
    }

    // Live Telemetry & Session Data (used when building the report)
    val isEsp32Connected by dashboardViewModel.isConnected.collectAsStateWithLifecycle()
    val isEsp32Connecting by dashboardViewModel.isConnecting.collectAsStateWithLifecycle()
    val esp32Ip by dashboardViewModel.esp32Ip.collectAsStateWithLifecycle()
    val lastVerifiedConnAt by dashboardViewModel.lastVerifiedConnectionAt.collectAsStateWithLifecycle()
    val connectionInterruptions by dashboardViewModel.connectionInterruptions.collectAsStateWithLifecycle()
    val eegAttention by dashboardViewModel.eegAttention.collectAsStateWithLifecycle()
    val emgStrength by dashboardViewModel.emgStrength.collectAsStateWithLifecycle()
    val gripForceN by dashboardViewModel.gripForce.collectAsStateWithLifecycle()
    val servoTempC by dashboardViewModel.servoTemp.collectAsStateWithLifecycle()
    val latencyMs by dashboardViewModel.latency.collectAsStateWithLifecycle()
    val batteryPct by dashboardViewModel.battery.collectAsStateWithLifecycle()
    val commandHistory by dashboardViewModel.commandHistory.collectAsStateWithLifecycle()

    val currentSession by adminViewModel.currentSession.collectAsStateWithLifecycle()
    val currentUserProfile by adminViewModel.currentUserProfile.collectAsStateWithLifecycle()
    val users by adminViewModel.users.collectAsStateWithLifecycle()
    val loginHistory by adminViewModel.loginHistory.collectAsStateWithLifecycle()
    val auditLogs by adminViewModel.auditLogs.collectAsStateWithLifecycle()
    val tickets by adminViewModel.tickets.collectAsStateWithLifecycle()

    // 1. Report Period & 2. Custom Date Range State
    val nowMillis = remember { System.currentTimeMillis() }
    var selectedRangePreset by remember { mutableStateOf("LAST_30_DAYS") }
    var periodStartMillis by remember { mutableStateOf(nowMillis - 30L * 24 * 60 * 60 * 1000) }
    var periodEndMillis by remember { mutableStateOf(nowMillis) }

    // 3. Report Format State
    var selectedFormat by remember { mutableStateOf(ReportExportFormat.PDF) }

    // 4. Generate Report (1..100 Animation) & Actions State
    var latestArtifact by remember { mutableStateOf<GeneratedReportArtifact?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var generationPercent by remember { mutableIntStateOf(0) }
    var showPreviewModal by remember { mutableStateOf(false) }
    var downloadStatusBanner by remember { mutableStateOf<String?>(null) }

    val animatedProgress by animateFloatAsState(
        targetValue = (generationPercent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 60),
        label = "report_generation_progress"
    )

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }
    val periodLabel = remember(periodStartMillis, periodEndMillis, selectedRangePreset) {
        if (selectedRangePreset == "ALL_TIME") {
            "All Time — ${dateFormatter.format(Date(periodEndMillis))}"
        } else {
            "${dateFormatter.format(Date(periodStartMillis))} — ${dateFormatter.format(Date(periodEndMillis))}"
        }
    }

    val firestoreRepo = adminViewModel.repository.firestoreRepo
    val firestoreProjectId = remember(context) {
        runCatching { context.getString(R.string.project_id) }.getOrElse { "not-configured" }
    }
    val firestoreDatabaseId = remember(context) {
        runCatching { context.getString(R.string.firestore_database_id) }.getOrElse { "(default)" }
    }
    val isFirestoreConfigured = firestoreRepo != null &&
        firestoreProjectId.isNotBlank() &&
        !firestoreProjectId.contains("placeholder", ignoreCase = true)

    val snapshot = remember(
        periodStartMillis,
        periodEndMillis,
        periodLabel,
        currentSession,
        users,
        loginHistory,
        auditLogs,
        tickets,
        isEsp32Connected,
        isEsp32Connecting,
        esp32Ip,
        lastVerifiedConnAt,
        connectionInterruptions,
        eegAttention,
        emgStrength,
        gripForceN,
        servoTempC,
        latencyMs,
        batteryPct,
        commandHistory
    ) {
        RoboHubReportGenerator.buildLiveReportSnapshot(
            context = context,
            periodStartMillis = periodStartMillis,
            periodEndMillis = periodEndMillis,
            periodLabel = periodLabel,
            currentSession = currentSession,
            users = users,
            loginHistory = loginHistory,
            auditLogs = auditLogs,
            tickets = tickets,
            isEsp32Connected = isEsp32Connected,
            isEsp32Connecting = isEsp32Connecting,
            esp32Ip = esp32Ip,
            lastVerifiedConnAt = lastVerifiedConnAt,
            connectionInterruptions = connectionInterruptions,
            eegAttention = eegAttention,
            emgStrength = emgStrength,
            gripForceN = gripForceN,
            servoTempC = servoTempC,
            latencyMs = latencyMs,
            batteryPct = batteryPct,
            sessionStartMillis = dashboardViewModel.sessionStartTimestamp,
            commandHistory = commandHistory,
            firestoreConfigured = isFirestoreConfigured,
            firestoreProjectId = firestoreProjectId,
            firestoreDatabaseId = firestoreDatabaseId,
            confirmedSignature = null,
            includeInternalLogs = false
        )
    }

    fun pickStartDate() {
        val cal = Calendar.getInstance().apply { timeInMillis = periodStartMillis }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                periodStartMillis = picked
                if (periodEndMillis < picked) {
                    periodEndMillis = picked + 24L * 60 * 60 * 1000 - 1L
                }
                selectedRangePreset = "CUSTOM"
                latestArtifact = null
                generationPercent = 0
                downloadStatusBanner = null
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun pickEndDate() {
        val cal = Calendar.getInstance().apply { timeInMillis = periodEndMillis }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply {
                    set(year, month, day, 23, 59, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                periodEndMillis = picked
                if (periodStartMillis > picked) {
                    periodStartMillis = picked - 24L * 60 * 60 * 1000 + 1L
                }
                selectedRangePreset = "CUSTOM"
                latestArtifact = null
                generationPercent = 0
                downloadStatusBanner = null
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun generateReportNow(
        targetFormat: ReportExportFormat = selectedFormat,
        openPreviewWhenReady: Boolean = false,
        downloadWhenReady: Boolean = false,
        shareWhenReady: Boolean = false
    ) {
        if (isGenerating) return
        isGenerating = true
        generationPercent = 1
        downloadStatusBanner = null
        coroutineScope.launch {
            try {
                // Build actual report artifact with Admin-uploaded Digital Signature & App Icon
                val artifactDeferred = withContext(Dispatchers.IO) {
                    RoboHubReportGenerator.generateReportArtifact(
                        context = context,
                        snapshot = snapshot,
                        format = targetFormat,
                        includeInternalLogs = false
                    )
                }

                // Animate 1% -> 100% progress smoothly
                for (pct in listOf(10, 22, 36, 50, 64, 78, 90, 100)) {
                    generationPercent = pct
                    delay(22L)
                }

                latestArtifact = artifactDeferred
                isGenerating = false

                val matchedUser = users.find {
                    it.username.equals(currentSession?.username ?: "", ignoreCase = true)
                }
                val adminSig = AdminDigitalSignatureManager.loadSignature(context)
                adminViewModel.recordGeneratedReport(
                    GeneratedReportRecord(
                        reportId = artifactDeferred.reportId,
                        username = matchedUser?.username ?: currentSession?.username ?: snapshot.userDisplayName,
                        userDisplayName = snapshot.userDisplayName,
                        userEmail = matchedUser?.email ?: snapshot.userEmailMasked,
                        reportType = targetFormat.label,
                        periodStartFormatted = dateFormatter.format(Date(periodStartMillis)),
                        periodEndFormatted = dateFormatter.format(Date(periodEndMillis)),
                        generatedAtMillis = snapshot.generatedAtMillis,
                        generatedDateIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(snapshot.generatedAtMillis)),
                        generatedDateDisplay = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(snapshot.generatedAtMillis)),
                        filePath = artifactDeferred.file.absolutePath,
                        fileName = artifactDeferred.fileName,
                        sha256Hex = artifactDeferred.sha256Hex,
                        adminSignedBy = adminSig.signerName,
                        adminSignatureDigest = adminSig.hmacSha256,
                        totalCommands = snapshot.totalCommands,
                        successRateText = snapshot.successRateText,
                        activitySummary = "Commands: ${snapshot.totalCommands} (${snapshot.successRateText}) | Session: ${snapshot.sessionDurationSummary}",
                        associatedLogs = commandHistory.take(15).map { cmd ->
                            "${RoboHubReportGenerator.formatDateTime(cmd.timestamp)} | ${cmd.gestureName} (${cmd.anglesSummary}) | ${if (cmd.succeeded) "SUCCESS" else "OFFLINE"}"
                        }
                    )
                )

                if (openPreviewWhenReady) {
                    showPreviewModal = true
                }
                if (downloadWhenReady) {
                    val savedRes = withContext(Dispatchers.IO) {
                        RoboHubReportGenerator.saveReportToPublicDownloads(context, artifactDeferred)
                    }
                    savedRes.onSuccess { path ->
                        downloadStatusBanner = "${targetFormat.label} saved to $path"
                        Toast.makeText(context, "Saved to $path", Toast.LENGTH_LONG).show()
                    }.onFailure { err ->
                        Toast.makeText(
                            context,
                            SecurityErrorSanitizer.toUserSafeMessage(err, "Unable to save report"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                if (shareWhenReady) {
                    shareReportFile(context, branding.appName, artifactDeferred)
                }
            } catch (e: Exception) {
                isGenerating = false
                generationPercent = 0
                Toast.makeText(
                    context,
                    SecurityErrorSanitizer.toUserSafeMessage(e, "Unable to generate report"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (branding.hasCustomLogo) {
                            AppBrandingLogo(
                                branding = branding,
                                size = 34.dp,
                                cornerRadius = 10.dp,
                                showBorder = true,
                                testTag = "reports_app_icon"
                            )
                        } else if (appIconBitmap != null) {
                            Image(
                                bitmap = appIconBitmap.asImageBitmap(),
                                contentDescription = "${branding.appName} Icon",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .testTag("reports_app_icon")
                            )
                        } else {
                            AppBrandingLogo(
                                branding = branding,
                                size = 34.dp,
                                cornerRadius = 10.dp,
                                testTag = "reports_app_icon"
                            )
                        }
                        Text(
                            "Progress Report",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = darkNavy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("reports_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = primaryBlue)
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToProfile,
                        modifier = Modifier.testTag("reports_top_bar_avatar_button")
                    ) {
                        SmartUserAvatar(
                            photoUrl = currentUserProfile?.photoUrl ?: currentSession?.photoUrl,
                            fullName = currentUserProfile?.name ?: currentSession?.name ?: currentSession?.username,
                            email = currentUserProfile?.email ?: currentSession?.email,
                            photoUpdatedAt = maxOf(
                                currentUserProfile?.photoUpdatedAt ?: 0L,
                                currentSession?.photoUpdatedAt ?: 0L
                            ),
                            size = 32.dp,
                            fontSize = 13.sp,
                            borderColor = primaryBlue.copy(alpha = 0.25f),
                            borderWidth = 1.dp,
                            contentDescription = "Open Profile",
                            testTag = "reports_top_bar_avatar"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            val buttonBlue = Color(0xFF4A7499)
            NavigationBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = Color.White,
                tonalElevation = 16.dp,
                modifier = Modifier.testTag("reports_bottom_nav_bar")
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Outlined.Home, contentDescription = "Home") },
                    label = { Text("Home", fontSize = 10.sp) },
                    selected = false,
                    onClick = onNavigateToHome,
                    colors = NavigationBarItemDefaults.colors(unselectedIconColor = mutedGray, unselectedTextColor = mutedGray)
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Outlined.PanTool, contentDescription = "Control") },
                    label = { Text("Control", fontSize = 10.sp) },
                    selected = false,
                    onClick = onNavigateToControl,
                    colors = NavigationBarItemDefaults.colors(unselectedIconColor = mutedGray, unselectedTextColor = mutedGray)
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Outlined.Psychology, contentDescription = "AI") },
                    label = { Text("AI", fontSize = 10.sp) },
                    selected = false,
                    onClick = onNavigateToAI,
                    colors = NavigationBarItemDefaults.colors(unselectedIconColor = mutedGray, unselectedTextColor = mutedGray)
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Timeline, contentDescription = "Progress") },
                    label = { Text("Progress", fontSize = 10.sp, fontWeight = FontWeight.Medium) },
                    selected = true,
                    onClick = { },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = buttonBlue,
                        selectedTextColor = buttonBlue,
                        indicatorColor = buttonBlue.copy(alpha = 0.15f),
                        unselectedIconColor = mutedGray,
                        unselectedTextColor = mutedGray
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Outlined.Person, contentDescription = "Profile") },
                    label = { Text("Profile", fontSize = 10.sp) },
                    selected = false,
                    onClick = onNavigateToProfile,
                    colors = NavigationBarItemDefaults.colors(unselectedIconColor = mutedGray, unselectedTextColor = mutedGray)
                )
            }
        },
        containerColor = lightBg
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. REPORT PERIOD (Today, Last 7 Days, Last 30 Days, All Time)
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "1. Report Period",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = darkNavy
                        )

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
                                    selected = selectedRangePreset == key,
                                    onClick = {
                                        selectedRangePreset = key
                                        val now = System.currentTimeMillis()
                                        periodEndMillis = now
                                        periodStartMillis = when (key) {
                                            "TODAY" -> Calendar.getInstance().apply {
                                                timeInMillis = now
                                                set(Calendar.HOUR_OF_DAY, 0)
                                                set(Calendar.MINUTE, 0)
                                                set(Calendar.SECOND, 0)
                                                set(Calendar.MILLISECOND, 0)
                                            }.timeInMillis
                                            "LAST_7_DAYS" -> now - 7L * 24 * 60 * 60 * 1000
                                            "LAST_30_DAYS" -> now - 30L * 24 * 60 * 60 * 1000
                                            else -> 0L
                                        }
                                        latestArtifact = null
                                        generationPercent = 0
                                        downloadStatusBanner = null
                                    },
                                    label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                    modifier = Modifier.testTag("period_chip_${key.lowercase(Locale.US)}")
                                )
                            }
                        }
                    }
                }

                // 2. CUSTOM DATE RANGE (From Date -> To Date)
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "2. Select Date Range",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = darkNavy
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = lightBg,
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp)
                                    .clickable { pickStartDate() }
                                    .testTag("report_start_date_picker")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                                        Text("From Date", fontSize = 11.sp, color = mutedGray)
                                        Text(
                                            if (selectedRangePreset == "ALL_TIME") "All Time" else dateFormatter.format(Date(periodStartMillis)),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = darkNavy,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(Icons.Default.CalendarToday, contentDescription = "Pick From Date", tint = primaryBlue, modifier = Modifier.size(18.dp))
                                }
                            }

                            Surface(
                                color = lightBg,
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp)
                                    .clickable { pickEndDate() }
                                    .testTag("report_end_date_picker")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                                        Text("To Date", fontSize = 11.sp, color = mutedGray)
                                        Text(
                                            dateFormatter.format(Date(periodEndMillis)),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = darkNavy,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(Icons.Default.CalendarToday, contentDescription = "Pick To Date", tint = primaryBlue, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                // 3. REPORT FORMAT SELECTION (PDF, PNG, TEXT, CSV)
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "3. Select Report Format",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = darkNavy
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val formatOptions = listOf(
                                Triple(ReportExportFormat.PDF, Icons.Outlined.PictureAsPdf, "PDF"),
                                Triple(ReportExportFormat.PNG, Icons.Outlined.Image, "PNG"),
                                Triple(ReportExportFormat.TEXT, Icons.Outlined.Description, "TEXT"),
                                Triple(ReportExportFormat.CSV, Icons.Outlined.TableChart, "CSV")
                            )
                            formatOptions.forEach { (fmt, icon, title) ->
                                val isSelected = selectedFormat == fmt
                                Surface(
                                    color = if (isSelected) Color(0xFFEFF6FF) else lightBg,
                                    border = BorderStroke(
                                        if (isSelected) 1.5.dp else 1.dp,
                                        if (isSelected) primaryBlue else Color(0xFFCBD5E1)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 60.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            selectedFormat = fmt
                                            if (latestArtifact?.format != fmt) {
                                                latestArtifact = null
                                                generationPercent = 0
                                                downloadStatusBanner = null
                                            }
                                        }
                                        .testTag("format_chip_${fmt.name.lowercase(Locale.US)}")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            icon,
                                            contentDescription = title,
                                            tint = if (isSelected) primaryBlue else slateText,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            title,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            color = if (isSelected) primaryBlue else darkNavy,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. GENERATE REPORT (1 to 100 Animation) + DOWNLOAD, PREVIEW & SHARE
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "4. Generate Report",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = darkNavy
                        )

                        Button(
                            onClick = { generateReportNow(targetFormat = selectedFormat) },
                            enabled = !isGenerating,
                            colors = ButtonDefaults.buttonColors(containerColor = primaryBlue),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("generate_robohub_pdf_button")
                        ) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isGenerating) {
                                    "Generating Report... $generationPercent%"
                                } else {
                                    "Generate Report"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }

                        AnimatedVisibility(visible = isGenerating || generationPercent > 0) {
                            Surface(
                                color = if (generationPercent == 100 && !isGenerating) Color(0xFFECFDF5) else Color(0xFFEFF6FF),
                                border = BorderStroke(
                                    1.dp,
                                    if (generationPercent == 100 && !isGenerating) Color(0xFF6EE7B7) else Color(0xFFBFDBFE)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("report_generation_progress")
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            if (generationPercent == 100 && !isGenerating) {
                                                "${selectedFormat.label} Report Ready"
                                            } else {
                                                "Generating ${selectedFormat.label} Report..."
                                            },
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (generationPercent == 100 && !isGenerating) Color(0xFF065F46) else darkNavy
                                        )
                                        Text(
                                            "$generationPercent%",
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (generationPercent == 100 && !isGenerating) successGreen else primaryBlue
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { animatedProgress },
                                        color = if (generationPercent == 100 && !isGenerating) successGreen else primaryBlue,
                                        trackColor = Color.White,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                }
                            }
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val existing = latestArtifact?.takeIf { it.format == selectedFormat }
                                    if (existing != null) {
                                        coroutineScope.launch {
                                            val res = withContext(Dispatchers.IO) {
                                                RoboHubReportGenerator.saveReportToPublicDownloads(context, existing)
                                            }
                                            res.onSuccess { path ->
                                                downloadStatusBanner = "${existing.format.label} saved to $path"
                                                Toast.makeText(context, "Saved to $path", Toast.LENGTH_LONG).show()
                                            }.onFailure { err ->
                                                Toast.makeText(
                                                    context,
                                                    SecurityErrorSanitizer.toUserSafeMessage(err, "Unable to save report"),
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    } else {
                                        generateReportNow(targetFormat = selectedFormat, downloadWhenReady = true)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, successGreen),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("download_robohub_pdf_button")
                            ) {
                                Icon(Icons.Outlined.Download, contentDescription = "Download", tint = successGreen, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Download", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = successGreen, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    val existing = latestArtifact?.takeIf { it.format == selectedFormat }
                                    if (existing != null) {
                                        showPreviewModal = true
                                    } else {
                                        generateReportNow(targetFormat = selectedFormat, openPreviewWhenReady = true)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, primaryBlue),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("preview_robohub_pdf_button")
                            ) {
                                Icon(Icons.Outlined.Visibility, contentDescription = "Preview", tint = primaryBlue, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Preview", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = primaryBlue, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    val existing = latestArtifact?.takeIf { it.format == selectedFormat }
                                    if (existing != null) {
                                        shareReportFile(context, branding.appName, existing)
                                    } else {
                                        generateReportNow(targetFormat = selectedFormat, shareWhenReady = true)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, darkNavy),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("share_robohub_pdf_button")
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = "Share", tint = darkNavy, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = darkNavy, maxLines = 1)
                            }
                        }

                        downloadStatusBanner?.let { msg ->
                            Surface(
                                color = Color(0xFFECFDF5),
                                border = BorderStroke(1.dp, Color(0xFF6EE7B7)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = successGreen, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(msg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF065F46))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showPreviewModal && latestArtifact != null) {
        val artifact = latestArtifact!!
        MultiFormatReportPreviewDialog(
            artifact = artifact,
            onDismiss = { showPreviewModal = false },
            onDownload = {
                coroutineScope.launch {
                    val res = withContext(Dispatchers.IO) {
                        RoboHubReportGenerator.saveReportToPublicDownloads(context, artifact)
                    }
                    res.onSuccess { path ->
                        downloadStatusBanner = "${artifact.format.label} saved to $path"
                        Toast.makeText(context, "Saved to $path", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onShare = { shareReportFile(context, branding.appName, artifact) },
            onOpenExternal = { openReportExternally(context, artifact) }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiFormatReportPreviewDialog(
    artifact: GeneratedReportArtifact,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onOpenExternal: () -> Unit
) {
    var renderedBitmaps by remember(artifact) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoadingPreview by remember(artifact) { mutableStateOf(true) }

    LaunchedEffect(artifact) {
        isLoadingPreview = true
        renderedBitmaps = withContext(Dispatchers.IO) {
            when (artifact.format) {
                ReportExportFormat.PDF -> RoboHubReportGenerator.renderPdfPagesToBitmaps(artifact.file)
                ReportExportFormat.PNG -> listOfNotNull(runCatching { BitmapFactory.decodeFile(artifact.file.absolutePath) }.getOrNull())
                ReportExportFormat.TEXT, ReportExportFormat.CSV -> emptyList()
            }
        }
        isLoadingPreview = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    "${artifact.format.label} Preview",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = Color(0xFF0F172A)
                )
                Text(
                    artifact.fileName,
                    fontSize = 11.sp,
                    color = Color(0xFF475569),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp, max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isLoadingPreview) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else when (artifact.format) {
                    ReportExportFormat.TEXT, ReportExportFormat.CSV -> {
                        val contentText = remember(artifact) {
                            artifact.textPreviewContent.ifBlank {
                                runCatching { artifact.file.readText(Charsets.UTF_8) }.getOrDefault("")
                            }
                        }
                        Surface(
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = contentText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFF0F172A),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    ReportExportFormat.PDF, ReportExportFormat.PNG -> {
                        if (renderedBitmaps.isEmpty()) {
                            Text(
                                "${artifact.format.label} report generated (${artifact.fileName}). Use Download or Share below.",
                                fontSize = 12.sp,
                                color = Color(0xFF334155)
                            )
                        } else {
                            renderedBitmaps.forEachIndexed { index, bmp ->
                                if (renderedBitmaps.size > 1) {
                                    Text(
                                        "Page ${index + 1} of ${renderedBitmaps.size}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1D4ED8)
                                    )
                                }
                                Surface(
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "${artifact.format.label} Preview ${index + 1}",
                                        contentScale = ContentScale.FillWidth,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(onClick = onOpenExternal, modifier = Modifier.heightIn(min = 44.dp)) {
                    Text("Open", fontSize = 11.sp)
                }
                OutlinedButton(onClick = onShare, modifier = Modifier.heightIn(min = 44.dp)) {
                    Text("Share", fontSize = 11.sp)
                }
                Button(
                    onClick = onDownload,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Text("Download", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 44.dp)) {
                Text("Close")
            }
        }
    )
}

private fun shareReportFile(context: Context, appName: String, artifact: GeneratedReportArtifact) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            artifact.file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = artifact.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "$appName Progress Report (${artifact.reportId})")
            putExtra(
                Intent.EXTRA_TEXT,
                "Attached is the $appName Progress Report (${artifact.fileName})."
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share ${artifact.format.label} Report"))
    } catch (e: Exception) {
        Toast.makeText(
            context,
            SecurityErrorSanitizer.toUserSafeMessage(e, "Unable to share report"),
            Toast.LENGTH_LONG
        ).show()
    }
}

private fun openReportExternally(context: Context, artifact: GeneratedReportArtifact) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            artifact.file
        )
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, artifact.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(viewIntent, "Open ${artifact.format.label} Report"))
    } catch (_: Exception) {
        Toast.makeText(context, "No external viewer installed; use Preview.", Toast.LENGTH_LONG).show()
    }
}
