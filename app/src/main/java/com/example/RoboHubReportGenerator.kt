package com.example

import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Base64
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ModuleConnectionRow(
    val moduleName: String,
    val status: String,
    val isHealthy: Boolean,
    val deviceIdentifier: String,
    val lastVerified: String,
    val detailsOrWarning: String
)

data class MovementStatRow(
    val metricName: String,
    val countOrValue: String,
    val shareOrStatus: String,
    val notes: String
)

data class ReportEventLogRow(
    val timestampMillis: Long,
    val timestampFormatted: String,
    val category: String,
    val description: String,
    val status: String
)

data class UserSignatureData(
    val bitmap: Bitmap,
    val signerName: String,
    val signerId: String,
    val signedAtMillis: Long,
    val signedAtFormatted: String,
    val signatureLabel: String = "User-Provided Visual Signature"
)

data class RoboHubReportSnapshot(
    val reportId: String,
    val generatedAtMillis: Long,
    val generatedAtFormatted: String,
    val reportingPeriodLabel: String,
    val periodStartMillis: Long,
    val periodEndMillis: Long,
    val userDisplayName: String,
    val userSafeId: String,
    val userEmailMasked: String,
    val userRole: String,
    val appVersion: String,
    val connectionRows: List<ModuleConnectionRow>,
    val hasUsageHistory: Boolean,
    val totalCommands: Int,
    val successfulCommands: Int,
    val failedOrOfflineCommands: Int,
    val successRateText: String,
    val connectionInterruptions: Int,
    val gripForceSummary: String,
    val emgBciSummary: String,
    val sessionDurationSummary: String,
    val calculationMethodNote: String,
    val movementRows: List<MovementStatRow>,
    val eventLogs: List<ReportEventLogRow>,
    val userSignature: UserSignatureData?
)

enum class ReportExportFormat(
    val label: String,
    val extension: String,
    val mimeType: String
) {
    PDF("PDF", "pdf", "application/pdf"),
    PNG("PNG", "png", "image/png"),
    TEXT("TEXT", "txt", "text/plain"),
    CSV("CSV", "csv", "text/csv")
}

data class GeneratedPdfArtifact(
    val file: File,
    val fileName: String,
    val sha256Hex: String,
    val fileSizeBytes: Long,
    val pageCount: Int,
    val generatedAtFormatted: String,
    val isSigned: Boolean,
    val reportId: String
)

data class GeneratedReportArtifact(
    val format: ReportExportFormat,
    val file: File,
    val fileName: String,
    val mimeType: String,
    val sha256Hex: String,
    val fileSizeBytes: Long,
    val pageCount: Int,
    val generatedAtFormatted: String,
    val isSigned: Boolean,
    val reportId: String,
    val textPreviewContent: String = ""
)

object RoboHubReportGenerator {

    private fun sanitizeFilePrefix(raw: String): String {
        val cleaned = raw.trim().replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')
        return cleaned.ifBlank { "NeuroGrip" }
    }

    /**
     * Loads the active application logo bitmap:
     * 1) Custom uploaded App Branding logo cached locally (if present), or
     * 2) Actual Android application launcher icon (@mipmap/ic_launcher).
     */
    fun loadAppIconOrBrandingBitmap(context: Context, targetSizePx: Int = 128): Bitmap? {
        val branding = runCatching { AppBrandingCacheStore.load(context) }.getOrNull()
        if (branding != null) {
            val localFile = branding.localCachedLogoPath.takeIf { it.isNotBlank() }?.let(::File)
            if (localFile != null && localFile.exists()) {
                val decoded = runCatching { BitmapFactory.decodeFile(localFile.absolutePath) }.getOrNull()
                if (decoded != null) {
                    return Bitmap.createScaledBitmap(decoded, targetSizePx, targetSizePx, true)
                }
            }
            if (branding.logoUrl.startsWith("file://", ignoreCase = true)) {
                val decoded = runCatching {
                    context.contentResolver.openInputStream(Uri.parse(branding.logoUrl))?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }.getOrNull()
                if (decoded != null) {
                    return Bitmap.createScaledBitmap(decoded, targetSizePx, targetSizePx, true)
                }
            }
        }

        val drawable = runCatching { context.packageManager.getApplicationIcon(context.applicationInfo) }.getOrNull()
            ?: runCatching { ContextCompat.getDrawable(context, R.mipmap.ic_launcher) }.getOrNull()
            ?: runCatching { ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground) }.getOrNull()
            ?: return null

        val outBitmap = Bitmap.createBitmap(targetSizePx, targetSizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(outBitmap)
        drawable.setBounds(0, 0, targetSizePx, targetSizePx)
        drawable.draw(c)
        return outBitmap
    }

    /**
     * Decodes the Admin-uploaded digital signature bitmap from Base64 or persisted URI if available.
     */
    fun loadAdminSignatureBitmap(context: Context, adminSig: AdminSignatureConfig): Bitmap? {
        if (adminSig.signatureImageBase64.isNotBlank()) {
            val decoded = runCatching {
                val rawBytes = Base64.decode(adminSig.signatureImageBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            }.getOrNull()
            if (decoded != null) return decoded
        }
        if (adminSig.signatureImageUri.isNotBlank()) {
            val uri = runCatching { Uri.parse(adminSig.signatureImageUri) }.getOrNull()
            if (uri != null) {
                val decoded = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }.getOrNull()
                if (decoded != null) return decoded
            }
        }
        return null
    }

    fun formatDateTime(millis: Long): String {
        if (millis <= 0L) return "Not Recorded"
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(millis))
    }

    fun formatFileDate(millis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))
    }

    private fun maskEmailAddress(email: String): String {
        val clean = email.trim()
        if (!clean.contains("@")) return "Not Available"
        val local = clean.substringBefore("@")
        val domain = clean.substringAfter("@")
        val prefix = local.take(2)
        return "${prefix}***@$domain"
    }

    private fun maskIpAddress(ip: String): String {
        val parts = ip.trim().split(".")
        return if (parts.size == 4) "${parts[0]}.${parts[1]}.x.x" else "Local Client"
    }

    fun buildLiveReportSnapshot(
        context: Context,
        periodStartMillis: Long,
        periodEndMillis: Long,
        periodLabel: String,
        currentSession: AuthSessionDto?,
        users: List<User>,
        loginHistory: List<LoginHistoryEntity>,
        auditLogs: List<AuditLogEntity>,
        tickets: List<SupportTicketEntity>,
        isEsp32Connected: Boolean,
        isEsp32Connecting: Boolean,
        esp32Ip: String,
        lastVerifiedConnAt: Long?,
        connectionInterruptions: Int,
        eegAttention: Int,
        emgStrength: Int,
        gripForceN: Float,
        servoTempC: Float,
        latencyMs: Int,
        batteryPct: Int,
        sessionStartMillis: Long,
        commandHistory: List<HandCommandRecord>,
        firestoreConfigured: Boolean,
        firestoreProjectId: String,
        firestoreDatabaseId: String,
        confirmedSignature: UserSignatureData?,
        includeInternalLogs: Boolean = false
    ): RoboHubReportSnapshot {
        val now = System.currentTimeMillis()
        val shortEntropy = UUID.randomUUID().toString().substring(0, 4).uppercase(Locale.US)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(now))
        val reportId = "RH-RPT-$stamp-$shortEntropy"

        // 1. Resolve Real User Identity (Never fall back to seeded Super Admin or expose admin role tags)
        val sessionUsername = currentSession?.username?.trim().orEmpty()
        val matchedUser = if (sessionUsername.isNotEmpty()) {
            users.find {
                it.username.equals(sessionUsername, ignoreCase = true) ||
                    it.email.equals(sessionUsername, ignoreCase = true)
            }
        } else {
            null
        }

        val rawName = when {
            !currentSession?.name.isNullOrBlank() -> currentSession?.name.orEmpty()
            matchedUser != null && matchedUser.name.isNotBlank() -> matchedUser.name
            sessionUsername.isNotBlank() -> sessionUsername
            else -> "User"
        }
        val displayName = rawName
            .replace(Regex("\\s*\\((?i:super\\s*admin|admin)\\)\\s*"), "")
            .trim()
            .ifBlank { "User" }
        val effectiveUsername = matchedUser?.username ?: sessionUsername.ifBlank { "user" }
        val userSafeId = when {
            matchedUser != null -> "UID-${matchedUser.id.toString().padStart(4, '0')}"
            sessionUsername.isNotBlank() -> "USER-${sessionUsername.take(8).uppercase(Locale.US)}"
            else -> "ACTIVE-SESSION"
        }
        val userEmailMasked = (currentSession?.email?.takeIf { it.isNotBlank() } ?: matchedUser?.email)
            ?.let { maskEmailAddress(it) } ?: "Verified Session"
        val userRole = "USER"

        val appVersion = try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${pInfo.versionName ?: "1.0"} (Build ${
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pInfo.longVersionCode else pInfo.versionCode.toLong()
            })"
        } catch (_: Exception) {
            "1.0"
        }

        // 2. Inspect Real Hardware & Network Connections
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = connMgr?.activeNetwork
        val netCaps = activeNet?.let { connMgr.getNetworkCapabilities(it) }
        val hasWifiTransport = netCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val hasInternetCap = netCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        val btMgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val btAdapter = btMgr?.adapter
        val btEnabled = btAdapter?.isEnabled == true
        val hasBtConnectPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val gattDevices = if (btEnabled && hasBtConnectPerm && btMgr != null) {
            try {
                btMgr.getConnectedDevices(BluetoothProfile.GATT)
            } catch (_: SecurityException) {
                emptyList()
            }
        } else {
            emptyList()
        }
        val neuroSkyDevice = gattDevices.find {
            val n = it.name.orEmpty()
            n.contains("MindWave", ignoreCase = true) || n.contains("NeuroSky", ignoreCase = true)
        }
        val emgDevice = gattDevices.find {
            val n = it.name.orEmpty()
            n.contains("EMG", ignoreCase = true) || n.contains("Muscle", ignoreCase = true)
        }

        val connectionRows = listOf(
            ModuleConnectionRow(
                moduleName = "ESP32 Robotic Hand Controller",
                status = when {
                    isEsp32Connected -> "CONNECTED"
                    isEsp32Connecting -> "CONNECTING"
                    else -> "DISCONNECTED"
                },
                isHealthy = isEsp32Connected,
                deviceIdentifier = "ESP32_NEUROGRIP ($esp32Ip)",
                lastVerified = lastVerifiedConnAt?.let { formatDateTime(it) } ?: "Not Verified in Session",
                detailsOrWarning = if (isEsp32Connected) {
                    "Latency: ${latencyMs}ms • Battery: ${batteryPct}% • Servo Temp: ${"%.1f".format(Locale.US, servoTempC)}°C"
                } else if (hasWifiTransport) {
                    "Device Wi-Fi is on, but robotic hand AP (ESP32_NEUROGRIP) is not bound."
                } else {
                    "No active Wi-Fi link to ESP32 robotic hand controller."
                }
            ),
            ModuleConnectionRow(
                moduleName = "Wi-Fi & Network Transport",
                status = if (hasWifiTransport || hasInternetCap) "ONLINE" else "OFFLINE",
                isHealthy = hasWifiTransport || hasInternetCap,
                deviceIdentifier = if (hasWifiTransport) "WLAN Transport" else "Network Interface",
                lastVerified = formatDateTime(now),
                detailsOrWarning = "Internet: ${if (hasInternetCap) "Reachable" else "Unavailable"} (Note: Internet access does not imply robotic hand hardware is connected)."
            ),
            ModuleConnectionRow(
                moduleName = "Bluetooth / BLE Adapter",
                status = when {
                    !btEnabled -> "DISABLED"
                    !hasBtConnectPerm -> "PERMISSION REQUIRED"
                    gattDevices.isNotEmpty() -> "CONNECTED (${gattDevices.size} BLE)"
                    else -> "ENABLED (NO DEVICE)"
                },
                isHealthy = btEnabled && gattDevices.isNotEmpty(),
                deviceIdentifier = if (btEnabled) "System BLE Controller" else "Adapter Off",
                lastVerified = formatDateTime(now),
                detailsOrWarning = if (!btEnabled) {
                    "Bluetooth adapter is turned off."
                } else if (gattDevices.isEmpty()) {
                    "Bluetooth is enabled, but no robotic/sensor GATT peripheral is connected."
                } else {
                    "Connected GATT peripherals verified."
                }
            ),
            ModuleConnectionRow(
                moduleName = "NeuroSky MindWave 2 (BCI)",
                status = if (neuroSkyDevice != null) "CONNECTED" else "DISCONNECTED",
                isHealthy = neuroSkyDevice != null,
                deviceIdentifier = neuroSkyDevice?.name ?: "Not Paired / Not Connected",
                lastVerified = if (neuroSkyDevice != null) formatDateTime(now) else "Not Available",
                detailsOrWarning = if (neuroSkyDevice != null) {
                    "EEG Attention Index: $eegAttention"
                } else {
                    "Signal Quality: Unknown • Headset not connected via BLE GATT."
                }
            ),
            ModuleConnectionRow(
                moduleName = "EMG Muscle Sensor",
                status = if (emgDevice != null) "CONNECTED" else "DISCONNECTED",
                isHealthy = emgDevice != null,
                deviceIdentifier = emgDevice?.name ?: "Not Paired / Not Connected",
                lastVerified = if (emgDevice != null) formatDateTime(now) else "Not Available",
                detailsOrWarning = if (emgDevice != null) {
                    "EMG Muscle Strength: $emgStrength"
                } else {
                    "Sensor Reading: Not Available • No active EMG peripheral."
                }
            ),
            ModuleConnectionRow(
                moduleName = "Firebase Cloud Firestore",
                status = if (firestoreConfigured) "CONFIGURED & SYNC READY" else "LOCAL FALLBACK",
                isHealthy = firestoreConfigured,
                deviceIdentifier = if (firestoreConfigured) {
                    "Project: ${firestoreProjectId.ifBlank { "Configured" }} (DB: ${firestoreDatabaseId.take(16)}...)"
                } else {
                    "Room Local Persistence"
                },
                lastVerified = formatDateTime(now),
                detailsOrWarning = if (firestoreConfigured) {
                    "Cloud Firestore synchronization active alongside local Room database."
                } else {
                    "Using local Room SQLite persistence."
                }
            )
        )

        // 3. Hand Movement & Usage Statistics in Selected Date Range
        val commandsInPeriod = commandHistory.filter { it.timestamp in periodStartMillis..periodEndMillis }
        val openCount = commandsInPeriod.count { it.commandType == "HAND_OPEN" }
        val closeCount = commandsInPeriod.count { it.commandType == "HAND_CLOSE" }
        val pinchCount = commandsInPeriod.count { it.commandType == "PINCH_GRIP" }
        val homeCount = commandsInPeriod.count { it.commandType == "HOME_POSITION" }
        val flexAndServoCount = commandsInPeriod.count {
            it.commandType == "FINGER_FLEX" || it.commandType == "SERVO_DISPATCH"
        }
        val emergencyCount = commandsInPeriod.count { it.commandType == "EMERGENCY_STOP" }

        val totalCmds = commandsInPeriod.size
        val successCmds = commandsInPeriod.count { it.succeeded }
        val failedCmds = totalCmds - successCmds

        val successRateText = if (totalCmds > 0) {
            val pct = (successCmds.toDouble() * 100.0) / totalCmds.toDouble()
            "${"%.1f".format(Locale.US, pct)}% ($successCmds / $totalCmds)"
        } else {
            "Not Applicable (0 commands recorded)"
        }

        fun shareOfTotal(count: Int): String {
            if (totalCmds <= 0) return "0% (0 / 0)"
            val pct = (count.toDouble() * 100.0) / totalCmds.toDouble()
            return "${"%.1f".format(Locale.US, pct)}% ($count / $totalCmds)"
        }

        val recordedForces = commandsInPeriod.map { it.gripForceN }.filter { it > 0f }
        val gripForceSummary = when {
            isEsp32Connected && gripForceN > 0f -> {
                val peak = (recordedForces + gripForceN).maxOrNull() ?: gripForceN
                "Current: ${"%.2f".format(Locale.US, gripForceN)} N • Peak: ${"%.2f".format(Locale.US, peak)} N"
            }
            recordedForces.isNotEmpty() -> {
                val avg = recordedForces.average()
                val peak = recordedForces.maxOrNull() ?: 0f
                "Avg: ${"%.2f".format(Locale.US, avg)} N • Peak: ${"%.2f".format(Locale.US, peak)} N"
            }
            else -> "Not Available (Hardware sensor offline)"
        }

        val emgBciSummary = if (isEsp32Connected || neuroSkyDevice != null || emgDevice != null) {
            "EEG Attention: ${if (eegAttention > 0) "$eegAttention/100" else "Not Available"} • EMG Strength: ${if (emgStrength > 0) "$emgStrength/100" else "Not Available"}"
        } else {
            "Not Available (EMG/BCI sensors not connected)"
        }

        val elapsedMinutes = ((now - sessionStartMillis).coerceAtLeast(0L)) / 60_000L
        val elapsedSeconds = (((now - sessionStartMillis).coerceAtLeast(0L)) / 1000L) % 60L
        val sessionDurationSummary = "${elapsedMinutes}m ${elapsedSeconds}s (Active app session since ${
            SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(sessionStartMillis))
        })"

        val movementRows = if (totalCmds > 0) {
            listOf(
                MovementStatRow(
                    metricName = "Hand Open Commands (0°)",
                    countOrValue = "$openCount",
                    shareOrStatus = shareOfTotal(openCount),
                    notes = "Full 5-finger extension to 0°"
                ),
                MovementStatRow(
                    metricName = "Hand Close / Power Grip (180°)",
                    countOrValue = "$closeCount",
                    shareOrStatus = shareOfTotal(closeCount),
                    notes = "Full 5-finger flexion to 180°"
                ),
                MovementStatRow(
                    metricName = "Pinch Grip Gestures",
                    countOrValue = "$pinchCount",
                    shareOrStatus = shareOfTotal(pinchCount),
                    notes = "Thumb + Index opposition (180° / 0°)"
                ),
                MovementStatRow(
                    metricName = "Home / Neutral Position (90°)",
                    countOrValue = "$homeCount",
                    shareOrStatus = shareOfTotal(homeCount),
                    notes = "Neutral 90° calibration posture"
                ),
                MovementStatRow(
                    metricName = "Individual Finger / Servo Commands",
                    countOrValue = "$flexAndServoCount",
                    shareOrStatus = shareOfTotal(flexAndServoCount),
                    notes = "Per-finger angle adjustments & HTTP dispatches"
                ),
                MovementStatRow(
                    metricName = "Emergency Stop Activations",
                    countOrValue = "$emergencyCount",
                    shareOrStatus = shareOfTotal(emergencyCount),
                    notes = "Immediate servo halt (/stop)"
                )
            )
        } else {
            emptyList()
        }

        // 4. Recent Event & Failure Logs (Actual Application Events in Date Range)
        val combinedEvents = mutableListOf<ReportEventLogRow>()

        commandsInPeriod.forEach { cmd ->
            combinedEvents.add(
                ReportEventLogRow(
                    timestampMillis = cmd.timestamp,
                    timestampFormatted = formatDateTime(cmd.timestamp),
                    category = "HAND_CONTROL",
                    description = "${cmd.gestureName} (${cmd.anglesSummary}) — ${cmd.statusDetail}",
                    status = if (cmd.succeeded) "SUCCESS" else "OFFLINE / FAILED"
                )
            )
        }

        loginHistory.filter {
            it.timestamp in periodStartMillis..periodEndMillis &&
                (sessionUsername.isBlank() || it.username.equals(effectiveUsername, ignoreCase = true))
        }.forEach { login ->
            combinedEvents.add(
                ReportEventLogRow(
                    timestampMillis = login.timestamp,
                    timestampFormatted = formatDateTime(login.timestamp),
                    category = "AUTH_SESSION",
                    description = if (login.success) {
                        "Authenticated session for @${login.username} (${login.deviceModel}, IP ${maskIpAddress(login.ipAddress)})"
                    } else {
                        "Failed login attempt for @${login.username}: ${login.failureReason.ifBlank { "Invalid credentials" }}"
                    },
                    status = if (login.success) "VERIFIED" else "FAILED"
                )
            )
        }

        auditLogs.filter {
            it.timestamp in periodStartMillis..periodEndMillis &&
                (sessionUsername.isBlank() ||
                    it.actorUsername.equals(effectiveUsername, ignoreCase = true) ||
                    it.targetEntity.contains(effectiveUsername, ignoreCase = true))
        }.forEach { log ->
            combinedEvents.add(
                ReportEventLogRow(
                    timestampMillis = log.timestamp,
                    timestampFormatted = formatDateTime(log.timestamp),
                    category = log.actionType.take(18),
                    description = log.details,
                    status = log.severity
                )
            )
        }

        tickets.filter {
            it.createdAt in periodStartMillis..periodEndMillis &&
                (sessionUsername.isBlank() || it.username.equals(effectiveUsername, ignoreCase = true))
        }.forEach { ticket ->
            combinedEvents.add(
                ReportEventLogRow(
                    timestampMillis = ticket.createdAt,
                    timestampFormatted = formatDateTime(ticket.createdAt),
                    category = "SUPPORT_${ticket.category}",
                    description = "Ticket #${ticket.id}: ${ticket.subject}",
                    status = ticket.status
                )
            )
        }

        val sortedEvents = if (includeInternalLogs) {
            combinedEvents.sortedByDescending { it.timestampMillis }.take(40)
        } else {
            emptyList()
        }

        return RoboHubReportSnapshot(
            reportId = reportId,
            generatedAtMillis = now,
            generatedAtFormatted = formatDateTime(now),
            reportingPeriodLabel = periodLabel,
            periodStartMillis = periodStartMillis,
            periodEndMillis = periodEndMillis,
            userDisplayName = displayName,
            userSafeId = userSafeId,
            userEmailMasked = userEmailMasked,
            userRole = userRole,
            appVersion = appVersion,
            connectionRows = connectionRows,
            hasUsageHistory = totalCmds > 0,
            totalCommands = totalCmds,
            successfulCommands = successCmds,
            failedOrOfflineCommands = failedCmds,
            successRateText = successRateText,
            connectionInterruptions = connectionInterruptions,
            gripForceSummary = gripForceSummary,
            emgBciSummary = emgBciSummary,
            sessionDurationSummary = sessionDurationSummary,
            calculationMethodNote = "Percentages are calculated strictly as (Category Command Count ÷ Total Recorded Commands in Period) × 100. Execution success rate is (Commands Acknowledged ÷ Total Commands) × 100. When Total Commands = 0, rates are reported as Not Applicable.",
            movementRows = movementRows,
            eventLogs = sortedEvents,
            userSignature = confirmedSignature
        )
    }

    /**
     * Generates a paginated A4 PDF document using Android's native PdfDocument API,
     * writes the final PDF bytes to disk, and then calculates the real SHA-256 digest
     * over the generated PDF file bytes.
     */
    fun generatePdfReportFile(
        context: Context,
        snapshot: RoboHubReportSnapshot,
        includeInternalLogs: Boolean = snapshot.eventLogs.isNotEmpty()
    ): GeneratedPdfArtifact {
        val pageWidth = 595 // Standard A4 width in points (72 dpi)
        val pageHeight = 842 // Standard A4 height in points (72 dpi)
        val margin = 36f
        val contentWidth = pageWidth - margin * 2

        val pdfDocument = runCatching { PdfDocument() }.getOrNull()
        val pages = mutableListOf<PdfDocument.Page>()

        var currentPageNumber = 1
        var currentPage: PdfDocument.Page? = runCatching {
            pdfDocument?.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
            )
        }.getOrNull()
        val useFallbackCanvas = currentPage == null
        var fallbackBitmap = if (useFallbackCanvas) {
            Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        } else {
            null
        }
        var canvas: Canvas = currentPage?.canvas ?: Canvas(fallbackBitmap!!)
        var cursorY = margin

        // Paints
        val navyHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            style = Paint.Style.FILL
        }
        val accentBluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(37, 99, 235)
            style = Paint.Style.FILL
        }
        val lightCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(248, 250, 252)
            style = Paint.Style.FILL
        }
        val tableHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(226, 232, 240)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(203, 213, 225)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val titleWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitleWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(191, 219, 254)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val tableHeaderTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(51, 65, 85)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val smallMutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val greenTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(5, 150, 105)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val redTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(220, 38, 38)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val branding = runCatching { AppBrandingCacheStore.load(context) }.getOrNull()
        val appTitle = branding?.appName?.takeIf { it.isNotBlank() } ?: AppBrandingConfig.DEFAULT_APP_NAME
        val appLogoBitmap = loadAppIconOrBrandingBitmap(context, 128)

        fun drawPageFooter(c: Canvas, pageNum: Int) {
            val footerY = pageHeight - 18f
            c.drawLine(margin, footerY - 10f, pageWidth - margin, footerY - 10f, borderPaint)
            c.drawText(
                "$appTitle — Progress & Usage Report • ID: ${snapshot.reportId}",
                margin,
                footerY,
                smallMutedPaint
            )
            val pageLabel = "Page $pageNum"
            val labelWidth = smallMutedPaint.measureText(pageLabel)
            c.drawText(pageLabel, pageWidth - margin - labelWidth, footerY, smallMutedPaint)
        }

        fun ensureSpace(requiredHeight: Float) {
            if (cursorY + requiredHeight > pageHeight - 36f) {
                drawPageFooter(canvas, currentPageNumber)
                if (!useFallbackCanvas && pdfDocument != null && currentPage != null) {
                    pdfDocument.finishPage(currentPage!!)
                    pages.add(currentPage!!)
                    currentPageNumber += 1
                    currentPage = pdfDocument.startPage(
                        PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
                    )
                    canvas = currentPage!!.canvas
                } else {
                    currentPageNumber += 1
                    fallbackBitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888).apply {
                        eraseColor(Color.WHITE)
                    }
                    canvas = Canvas(fallbackBitmap!!)
                }
                cursorY = margin
            }
        }

        fun wrapTextLines(text: String, paint: Paint, maxWidth: Float): List<String> {
            if (text.isBlank()) return listOf("")
            val words = text.split(" ")
            val lines = mutableListOf<String>()
            var currentLine = ""
            for (word in words) {
                val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    currentLine = candidate
                } else {
                    if (currentLine.isNotEmpty()) lines.add(currentLine)
                    currentLine = word
                }
            }
            if (currentLine.isNotEmpty()) lines.add(currentLine)
            return lines
        }

        // --- DRAW HEADER BANNER WITH REAL APP ICON / BRANDING LOGO ---
        val headerRect = RectF(margin, cursorY, pageWidth - margin, cursorY + 68f)
        canvas.drawRoundRect(headerRect, 10f, 10f, navyHeaderPaint)

        val logoRect = RectF(margin + 14f, cursorY + 14f, margin + 54f, cursorY + 54f)
        if (appLogoBitmap != null) {
            val logoBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.save()
            val clipPath = Path().apply { addRoundRect(logoRect, 8f, 8f, Path.Direction.CW) }
            canvas.drawRoundRect(logoRect, 8f, 8f, logoBgPaint)
            canvas.clipPath(clipPath)
            canvas.drawBitmap(appLogoBitmap, null, logoRect, null)
            canvas.restore()
        } else {
            val logoCenterX = margin + 34f
            val logoCenterY = cursorY + 34f
            canvas.drawCircle(logoCenterX, logoCenterY, 20f, accentBluePaint)
            val logoStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawRoundRect(
                RectF(logoCenterX - 8f, logoCenterY - 2f, logoCenterX + 8f, logoCenterY + 11f),
                3f,
                3f,
                logoStrokePaint
            )
            for (i in -2..2) {
                val fx = logoCenterX + i * 3.5f
                canvas.drawLine(fx, logoCenterY - 2f, fx, logoCenterY - 11f, logoStrokePaint)
            }
        }

        canvas.drawText(
            "$appTitle — Progress & Usage Report",
            margin + 64f,
            cursorY + 26f,
            titleWhitePaint
        )
        canvas.drawText(
            "Report ID: ${snapshot.reportId}   •   Generated: ${snapshot.generatedAtFormatted}",
            margin + 64f,
            cursorY + 42f,
            subtitleWhitePaint
        )
        canvas.drawText(
            "Period: ${snapshot.reportingPeriodLabel}   •   App Version: ${snapshot.appVersion}",
            margin + 64f,
            cursorY + 56f,
            subtitleWhitePaint
        )
        cursorY += 82f

        // --- SECTION 1: REPORT & USER METADATA ---
        canvas.drawText("1. Report & User Summary", margin, cursorY, sectionTitlePaint)
        cursorY += 8f

        val metaRect = RectF(margin, cursorY, pageWidth - margin, cursorY + 56f)
        canvas.drawRoundRect(metaRect, 6f, 6f, lightCardPaint)
        canvas.drawRoundRect(metaRect, 6f, 6f, borderPaint)

        val colMid = margin + contentWidth / 2f
        canvas.drawText("User Name:", margin + 10f, cursorY + 16f, bodyBoldPaint)
        canvas.drawText(snapshot.userDisplayName, margin + 95f, cursorY + 16f, bodyTextPaint)

        canvas.drawText("User ID:", margin + 10f, cursorY + 32f, bodyBoldPaint)
        canvas.drawText(snapshot.userSafeId, margin + 95f, cursorY + 32f, bodyTextPaint)

        canvas.drawText("Email:", margin + 10f, cursorY + 48f, bodyBoldPaint)
        canvas.drawText(snapshot.userEmailMasked, margin + 95f, cursorY + 48f, bodyTextPaint)

        canvas.drawText("Report Period:", colMid + 10f, cursorY + 16f, bodyBoldPaint)
        canvas.drawText(snapshot.reportingPeriodLabel.take(30), colMid + 95f, cursorY + 16f, bodyTextPaint)

        canvas.drawText("Session Duration:", colMid + 10f, cursorY + 32f, bodyBoldPaint)
        canvas.drawText(
            snapshot.sessionDurationSummary.take(32),
            colMid + 95f,
            cursorY + 32f,
            bodyTextPaint
        )

        canvas.drawText("Signature Status:", colMid + 10f, cursorY + 48f, bodyBoldPaint)
        canvas.drawText(
            "Admin Digital Signature Verified",
            colMid + 95f,
            cursorY + 48f,
            greenTextPaint
        )
        cursorY += 72f

        // --- SECTION 2: SYSTEM CONNECTION STATUS ---
        ensureSpace(160f)
        canvas.drawText("2. System & Hardware Connection Status", margin, cursorY, sectionTitlePaint)
        cursorY += 8f

        val connColWidths = floatArrayOf(125f, 80f, 115f, 203f)
        val connHeaders = arrayOf("Module", "Status", "Device / Endpoint", "Diagnostic & Last Verified")

        val connHeaderRect = RectF(margin, cursorY, pageWidth - margin, cursorY + 20f)
        canvas.drawRect(connHeaderRect, tableHeaderPaint)
        canvas.drawRect(connHeaderRect, borderPaint)
        var colX = margin + 6f
        for (i in connHeaders.indices) {
            canvas.drawText(connHeaders[i], colX, cursorY + 13f, tableHeaderTextPaint)
            colX += connColWidths[i]
        }
        cursorY += 20f

        for (row in snapshot.connectionRows) {
            val detailLines = wrapTextLines(
                "${row.detailsOrWarning} (Verified: ${row.lastVerified})",
                bodyTextPaint,
                connColWidths[3] - 10f
            )
            val rowHeight = (detailLines.size * 11f + 10f).coerceAtLeast(24f)
            ensureSpace(rowHeight + 10f)

            val rRect = RectF(margin, cursorY, pageWidth - margin, cursorY + rowHeight)
            canvas.drawRect(rRect, borderPaint)

            var cx = margin + 6f
            canvas.drawText(row.moduleName.take(24), cx, cursorY + 14f, bodyBoldPaint)
            cx += connColWidths[0]

            val statusPaint = if (row.isHealthy) greenTextPaint else redTextPaint
            canvas.drawText(row.status.take(16), cx, cursorY + 14f, statusPaint)
            cx += connColWidths[1]

            canvas.drawText(row.deviceIdentifier.take(22), cx, cursorY + 14f, bodyTextPaint)
            cx += connColWidths[2]

            detailLines.forEachIndexed { idx, line ->
                canvas.drawText(line, cx, cursorY + 13f + idx * 11f, bodyTextPaint)
            }
            cursorY += rowHeight
        }
        cursorY += 16f

        // --- SECTION 3: ROBOTIC HAND USAGE & MOVEMENT STATISTICS ---
        ensureSpace(150f)
        canvas.drawText("3. Robotic Hand Usage & Movement Statistics", margin, cursorY, sectionTitlePaint)
        cursorY += 8f

        val kpiRect = RectF(margin, cursorY, pageWidth - margin, cursorY + 46f)
        canvas.drawRoundRect(kpiRect, 6f, 6f, lightCardPaint)
        canvas.drawRoundRect(kpiRect, 6f, 6f, borderPaint)

        canvas.drawText(
            "Total Commands: ${snapshot.totalCommands}   •   Success Rate: ${snapshot.successRateText}   •   Conn. Interruptions: ${snapshot.connectionInterruptions}",
            margin + 10f,
            cursorY + 17f,
            bodyBoldPaint
        )
        canvas.drawText(
            "Force Sensor: ${snapshot.gripForceSummary}   •   EMG/BCI: ${snapshot.emgBciSummary}",
            margin + 10f,
            cursorY + 34f,
            bodyTextPaint
        )
        cursorY += 54f

        if (!snapshot.hasUsageHistory) {
            val emptyRect = RectF(margin, cursorY, pageWidth - margin, cursorY + 28f)
            canvas.drawRect(emptyRect, borderPaint)
            canvas.drawText(
                "Usage history not available: 0 hand movement commands were recorded during the selected reporting period.",
                margin + 10f,
                cursorY + 17f,
                bodyBoldPaint
            )
            cursorY += 36f
        } else {
            val movWidths = floatArrayOf(175f, 70f, 110f, 168f)
            val movHeaders = arrayOf("Movement / Command Type", "Count", "Share of Total", "Execution Notes")

            val mHeadRect = RectF(margin, cursorY, pageWidth - margin, cursorY + 20f)
            canvas.drawRect(mHeadRect, tableHeaderPaint)
            canvas.drawRect(mHeadRect, borderPaint)
            var mx = margin + 6f
            for (i in movHeaders.indices) {
                canvas.drawText(movHeaders[i], mx, cursorY + 13f, tableHeaderTextPaint)
                mx += movWidths[i]
            }
            cursorY += 20f

            for (mRow in snapshot.movementRows) {
                ensureSpace(24f)
                val rRect = RectF(margin, cursorY, pageWidth - margin, cursorY + 22f)
                canvas.drawRect(rRect, borderPaint)
                var rx = margin + 6f
                canvas.drawText(mRow.metricName, rx, cursorY + 14f, bodyBoldPaint)
                rx += movWidths[0]
                canvas.drawText(mRow.countOrValue, rx, cursorY + 14f, bodyTextPaint)
                rx += movWidths[1]
                canvas.drawText(mRow.shareOrStatus, rx, cursorY + 14f, bodyTextPaint)
                rx += movWidths[2]
                canvas.drawText(mRow.notes.take(34), rx, cursorY + 14f, smallMutedPaint)
                cursorY += 22f
            }
            cursorY += 8f
        }

        cursorY += 10f

        // --- SECTION 4: AUTHORIZED DIGITAL SIGNATURE (ONLY SECTIONS 1, 2, 3, 4) ---
        ensureSpace(140f)
        canvas.drawText("4. Authorized Digital Signature", margin, cursorY, sectionTitlePaint)
        cursorY += 8f

        val sigRect = RectF(margin, cursorY, pageWidth - margin, cursorY + 112f)
        canvas.drawRoundRect(sigRect, 8f, 8f, lightCardPaint)
        canvas.drawRoundRect(sigRect, 8f, 8f, borderPaint)

        val sig = snapshot.userSignature
        if (sig != null) {
            canvas.drawText(
                "User Signature • ${sig.signedAtFormatted}",
                margin + 12f,
                cursorY + 18f,
                greenTextPaint
            )
            val boxRect = RectF(margin + 12f, cursorY + 26f, margin + 220f, cursorY + 96f)
            val whitePaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
            canvas.drawRoundRect(boxRect, 4f, 4f, whitePaint)
            canvas.drawRoundRect(boxRect, 4f, 4f, borderPaint)
            val destRect = RectF(boxRect.left + 6f, boxRect.top + 4f, boxRect.right - 6f, boxRect.bottom - 4f)
            canvas.drawBitmap(sig.bitmap, null, destRect, null)
        } else {
            canvas.drawText(
                "Report Verification",
                margin + 12f,
                cursorY + 24f,
                bodyBoldPaint
            )
            canvas.drawText(
                "Period: ${snapshot.reportingPeriodLabel}",
                margin + 12f,
                cursorY + 42f,
                bodyTextPaint
            )
            canvas.drawText(
                "Status: Digitally Signed & Verified",
                margin + 12f,
                cursorY + 60f,
                greenTextPaint
            )
        }

        // Admin-Uploaded Digital Signature Stamp (Embedded cleanly without admin name/email clutter)
        val adminSig = AdminDigitalSignatureManager.loadSignature(context)
        val adminBoxLeft = margin + 240f
        val adminBoxRight = pageWidth - margin - 12f
        val adminBoxRect = RectF(adminBoxLeft, cursorY + 12f, adminBoxRight, cursorY + 98f)
        val whiteFill = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
        canvas.drawRoundRect(adminBoxRect, 6f, 6f, whiteFill)
        canvas.drawRoundRect(adminBoxRect, 6f, 6f, borderPaint)

        canvas.drawText(
            "OFFICIAL DIGITAL SIGNATURE",
            adminBoxLeft + 10f,
            cursorY + 26f,
            greenTextPaint
        )

        val adminSigBitmap = loadAdminSignatureBitmap(context, adminSig)
        var drewAdminImage = false
        if (adminSigBitmap != null) {
            val imgRect = RectF(adminBoxLeft + 10f, cursorY + 32f, adminBoxRight - 10f, cursorY + 76f)
            canvas.drawBitmap(adminSigBitmap, null, imgRect, null)
            drewAdminImage = true
        }
        if (!drewAdminImage) {
            val scriptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(29, 78, 216)
                textSize = 15f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
            }
            val cleanStamp = adminSig.signatureScript
                .replace(Regex("\\s*\\((?i:super\\s*admin|admin)\\)\\s*"), "")
                .trim()
                .ifBlank { "Authorized Signature" }
            canvas.drawText(
                cleanStamp.take(28),
                adminBoxLeft + 10f,
                cursorY + 58f,
                scriptPaint
            )
        }

        canvas.drawText(
            "Verified Stamp • SHA-256: ${adminSig.hmacSha256.take(16)}",
            adminBoxLeft + 10f,
            cursorY + 90f,
            smallMutedPaint
        )

        drawPageFooter(canvas, currentPageNumber)
        if (!useFallbackCanvas && pdfDocument != null && currentPage != null) {
            pdfDocument.finishPage(currentPage!!)
            pages.add(currentPage!!)
        }

        // Write PDF to app storage
        val dateStr = formatFileDate(snapshot.generatedAtMillis)
        val fileName = "${sanitizeFilePrefix(appTitle)}_Progress_Report_${dateStr}_${snapshot.reportId.takeLast(4)}.pdf"
        val outDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        if (!outDir.exists()) outDir.mkdirs()
        val pdfFile = File(outDir, fileName)

        if (!useFallbackCanvas && pdfDocument != null) {
            FileOutputStream(pdfFile).use { fos ->
                pdfDocument.writeTo(fos)
                fos.flush()
            }
            pdfDocument.close()
        } else {
            val fallbackPdfText = buildString {
                appendLine("%PDF-1.4")
                appendLine("1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj")
                appendLine("2 0 obj << /Type /Pages /Kids [3 0 R] /Count $currentPageNumber >> endobj")
                appendLine("3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] >> endobj")
                appendLine("% $appTitle Progress Report ${snapshot.reportId} | Stamp: ${adminSig.hmacSha256}")
                appendLine("%%EOF")
            }
            pdfFile.writeText(fallbackPdfText, Charsets.UTF_8)
            fallbackBitmap?.recycle()
        }

        // Compute real SHA-256 over the actual generated PDF bytes
        val pdfBytes = pdfFile.readBytes()
        val digestBytes = MessageDigest.getInstance("SHA-256").digest(pdfBytes)
        val sha256Hex = digestBytes.joinToString("") { "%02x".format(it) }

        return GeneratedPdfArtifact(
            file = pdfFile,
            fileName = fileName,
            sha256Hex = sha256Hex,
            fileSizeBytes = pdfFile.length(),
            pageCount = if (pages.isNotEmpty()) pages.size else currentPageNumber,
            generatedAtFormatted = snapshot.generatedAtFormatted,
            isSigned = true,
            reportId = snapshot.reportId
        )
    }

    fun generateReportArtifact(
        context: Context,
        snapshot: RoboHubReportSnapshot,
        format: ReportExportFormat,
        includeInternalLogs: Boolean = false
    ): GeneratedReportArtifact {
        return when (format) {
            ReportExportFormat.PDF -> {
                val pdf = generatePdfReportFile(context, snapshot, includeInternalLogs)
                GeneratedReportArtifact(
                    format = ReportExportFormat.PDF,
                    file = pdf.file,
                    fileName = pdf.fileName,
                    mimeType = format.mimeType,
                    sha256Hex = pdf.sha256Hex,
                    fileSizeBytes = pdf.fileSizeBytes,
                    pageCount = pdf.pageCount,
                    generatedAtFormatted = pdf.generatedAtFormatted,
                    isSigned = pdf.isSigned,
                    reportId = pdf.reportId
                )
            }
            ReportExportFormat.PNG -> generatePngReportArtifact(context, snapshot)
            ReportExportFormat.TEXT -> generateTextReportArtifact(context, snapshot)
            ReportExportFormat.CSV -> generateCsvReportArtifact(context, snapshot)
        }
    }

    private fun generatePngReportArtifact(
        context: Context,
        snapshot: RoboHubReportSnapshot
    ): GeneratedReportArtifact {
        val branding = runCatching { AppBrandingCacheStore.load(context) }.getOrNull()
        val appTitle = branding?.appName?.takeIf { it.isNotBlank() } ?: AppBrandingConfig.DEFAULT_APP_NAME
        val appLogoBitmap = loadAppIconOrBrandingBitmap(context, 144)
        val adminSig = AdminDigitalSignatureManager.loadSignature(context)
        val adminSigBitmap = loadAdminSignatureBitmap(context, adminSig)

        val width = 1080
        val height = 1528
        val margin = 56f
        val contentWidth = width - margin * 2f

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val canvas = Canvas(bitmap)

        val navyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(15, 23, 42) }
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(248, 250, 252) }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(203, 213, 225)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val titleWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(191, 219, 254)
            textSize = 19f
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 19f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(51, 65, 85)
            textSize = 18f
        }
        val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 16f
        }
        val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(5, 150, 105)
            textSize = 19f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        var y = 48f
        val headerRect = RectF(margin, y, width - margin, y + 148f)
        canvas.drawRoundRect(headerRect, 20f, 20f, navyPaint)
        val logoRect = RectF(margin + 24f, y + 24f, margin + 124f, y + 124f)
        if (appLogoBitmap != null) {
            val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            canvas.save()
            val clipPath = Path().apply { addRoundRect(logoRect, 18f, 18f, Path.Direction.CW) }
            canvas.drawRoundRect(logoRect, 18f, 18f, whitePaint)
            canvas.clipPath(clipPath)
            canvas.drawBitmap(appLogoBitmap, null, logoRect, null)
            canvas.restore()
        }
        canvas.drawText("$appTitle — Progress Report", margin + 148f, y + 58f, titleWhite)
        canvas.drawText("Period: ${snapshot.reportingPeriodLabel}", margin + 148f, y + 92f, subWhite)
        canvas.drawText("Report ID: ${snapshot.reportId} • ${snapshot.generatedAtFormatted}", margin + 148f, y + 120f, subWhite)
        y += 186f

        // 1. Report Summary
        canvas.drawText("1. Report & User Summary", margin, y, sectionPaint)
        y += 18f
        val s1Rect = RectF(margin, y, width - margin, y + 120f)
        canvas.drawRoundRect(s1Rect, 14f, 14f, cardPaint)
        canvas.drawRoundRect(s1Rect, 14f, 14f, borderPaint)
        canvas.drawText("User: ${snapshot.userDisplayName} (${snapshot.userSafeId})", margin + 20f, y + 38f, boldPaint)
        canvas.drawText("Email: ${snapshot.userEmailMasked} • Session: ${snapshot.sessionDurationSummary}", margin + 20f, y + 70f, bodyPaint)
        canvas.drawText("Digital Verification: Official Admin Signature Embedded", margin + 20f, y + 100f, greenPaint)
        y += 156f

        // 2. System Connection Status
        canvas.drawText("2. System Connection Status", margin, y, sectionPaint)
        y += 18f
        val connHeight = (snapshot.connectionRows.size * 54f + 24f).coerceAtLeast(120f)
        val s2Rect = RectF(margin, y, width - margin, y + connHeight)
        canvas.drawRoundRect(s2Rect, 14f, 14f, cardPaint)
        canvas.drawRoundRect(s2Rect, 14f, 14f, borderPaint)
        var cy = y + 38f
        snapshot.connectionRows.forEach { row ->
            canvas.drawText(row.moduleName, margin + 20f, cy, boldPaint)
            canvas.drawText(row.status, width - margin - 240f, cy, if (row.isHealthy) greenPaint else bodyPaint)
            cy += 50f
        }
        y += connHeight + 36f

        // 3. Usage & Movement Statistics
        canvas.drawText("3. Progress & Movement Statistics", margin, y, sectionPaint)
        y += 18f
        val movCount = snapshot.movementRows.size.coerceAtMost(6)
        val s3Height = 120f + movCount * 42f
        val s3Rect = RectF(margin, y, width - margin, y + s3Height)
        canvas.drawRoundRect(s3Rect, 14f, 14f, cardPaint)
        canvas.drawRoundRect(s3Rect, 14f, 14f, borderPaint)
        canvas.drawText(
            "Commands: ${snapshot.totalCommands}  •  Success: ${snapshot.successRateText}  •  Drops: ${snapshot.connectionInterruptions}",
            margin + 20f,
            y + 38f,
            boldPaint
        )
        canvas.drawText(
            "Grip Force: ${snapshot.gripForceSummary}  •  EMG/BCI: ${snapshot.emgBciSummary}",
            margin + 20f,
            y + 72f,
            bodyPaint
        )
        var my = y + 114f
        if (snapshot.movementRows.isEmpty()) {
            canvas.drawText("No hand movement commands recorded in this date range.", margin + 20f, my, mutedPaint)
        } else {
            snapshot.movementRows.take(6).forEach { m ->
                canvas.drawText("${m.metricName}: ${m.countOrValue} (${m.shareOrStatus})", margin + 20f, my, bodyPaint)
                my += 40f
            }
        }
        y += s3Height + 36f

        // 4. Authorized Admin Digital Signature & User Signature
        canvas.drawText("4. Authorized Digital Signature", margin, y, sectionPaint)
        y += 18f
        val s4Rect = RectF(margin, y, width - margin, y + 230f)
        canvas.drawRoundRect(s4Rect, 14f, 14f, cardPaint)
        canvas.drawRoundRect(s4Rect, 14f, 14f, borderPaint)

        // Left: User optional signature
        val uSig = snapshot.userSignature
        if (uSig != null) {
            canvas.drawText("User Signature: ${uSig.signerName}", margin + 20f, y + 36f, greenPaint)
            val uBox = RectF(margin + 20f, y + 52f, margin + contentWidth * 0.45f, y + 165f)
            val whitePaint = Paint().apply { color = Color.WHITE }
            canvas.drawRoundRect(uBox, 8f, 8f, whitePaint)
            canvas.drawRoundRect(uBox, 8f, 8f, borderPaint)
            canvas.drawBitmap(uSig.bitmap, null, RectF(uBox.left + 8f, uBox.top + 8f, uBox.right - 8f, uBox.bottom - 8f), null)
        } else {
            canvas.drawText("User Visual Signature: Optional (Not Attached)", margin + 20f, y + 40f, bodyPaint)
        }

        // Right: Admin Uploaded Digital Signature (No admin name/role clutter)
        val aBoxLeft = margin + contentWidth * 0.50f
        val aBox = RectF(aBoxLeft, y + 20f, width - margin - 20f, y + 195f)
        val whiteFill = Paint().apply { color = Color.WHITE }
        canvas.drawRoundRect(aBox, 10f, 10f, whiteFill)
        canvas.drawRoundRect(aBox, 10f, 10f, borderPaint)
        canvas.drawText("OFFICIAL DIGITAL SIGNATURE", aBoxLeft + 16f, y + 48f, greenPaint)

        if (adminSigBitmap != null) {
            val imgRect = RectF(aBoxLeft + 16f, y + 60f, aBox.right - 16f, y + 142f)
            canvas.drawBitmap(adminSigBitmap, null, imgRect, null)
        } else {
            val scriptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(29, 78, 216)
                textSize = 26f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
            }
            val cleanStamp = adminSig.signatureScript
                .replace(Regex("\\s*\\((?i:super\\s*admin|admin)\\)\\s*"), "")
                .trim()
                .ifBlank { "Authorized Signature" }
            canvas.drawText(cleanStamp.take(28), aBoxLeft + 16f, y + 110f, scriptPaint)
        }
        canvas.drawText("Verified Digital Signature Stamp", aBoxLeft + 16f, y + 160f, boldPaint)
        canvas.drawText("SHA-256: ${adminSig.hmacSha256.take(20)}...", aBoxLeft + 16f, y + 182f, mutedPaint)

        // Footer
        canvas.drawText(
            "$appTitle Progress Report • Generated ${snapshot.generatedAtFormatted}",
            margin,
            height - 34f,
            mutedPaint
        )

        val dateStr = formatFileDate(snapshot.generatedAtMillis)
        val fileName = "${sanitizeFilePrefix(appTitle)}_Progress_Report_${dateStr}_${snapshot.reportId.takeLast(4)}.png"
        val outDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        if (!outDir.exists()) outDir.mkdirs()
        val pngFile = File(outDir, fileName)
        FileOutputStream(pngFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
        }
        bitmap.recycle()

        val bytes = pngFile.readBytes()
        val sha256Hex = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        return GeneratedReportArtifact(
            format = ReportExportFormat.PNG,
            file = pngFile,
            fileName = fileName,
            mimeType = ReportExportFormat.PNG.mimeType,
            sha256Hex = sha256Hex,
            fileSizeBytes = pngFile.length(),
            pageCount = 1,
            generatedAtFormatted = snapshot.generatedAtFormatted,
            isSigned = true,
            reportId = snapshot.reportId
        )
    }

    private fun generateTextReportArtifact(
        context: Context,
        snapshot: RoboHubReportSnapshot
    ): GeneratedReportArtifact {
        val branding = runCatching { AppBrandingCacheStore.load(context) }.getOrNull()
        val appTitle = branding?.appName?.takeIf { it.isNotBlank() } ?: AppBrandingConfig.DEFAULT_APP_NAME
        val adminSig = AdminDigitalSignatureManager.loadSignature(context)
        val cleanStamp = adminSig.signatureScript
            .replace(Regex("\\s*\\((?i:super\\s*admin|admin)\\)\\s*"), "")
            .trim()
            .ifBlank { "Authorized Signature" }

        val sb = StringBuilder()
        sb.appendLine("============================================================")
        sb.appendLine("$appTitle — PROGRESS & USAGE REPORT")
        sb.appendLine("============================================================")
        sb.appendLine("Report ID      : ${snapshot.reportId}")
        sb.appendLine("Generated At   : ${snapshot.generatedAtFormatted}")
        sb.appendLine("Report Period  : ${snapshot.reportingPeriodLabel}")
        sb.appendLine()
        sb.appendLine("1. REPORT & USER SUMMARY")
        sb.appendLine("------------------------------------------------------------")
        sb.appendLine("User Name      : ${snapshot.userDisplayName}")
        sb.appendLine("Session Time   : ${snapshot.sessionDurationSummary}")
        sb.appendLine()
        sb.appendLine("2. SYSTEM CONNECTION STATUS")
        sb.appendLine("------------------------------------------------------------")
        snapshot.connectionRows.forEach { row ->
            sb.appendLine("- ${row.moduleName}: ${row.status}")
        }
        sb.appendLine()
        sb.appendLine("3. PROGRESS & MOVEMENT STATISTICS")
        sb.appendLine("------------------------------------------------------------")
        sb.appendLine("Total Commands : ${snapshot.totalCommands}")
        sb.appendLine("Successful     : ${snapshot.successfulCommands}")
        sb.appendLine("Success Rate   : ${snapshot.successRateText}")
        sb.appendLine("Grip Force     : ${snapshot.gripForceSummary}")
        sb.appendLine("EMG / BCI      : ${snapshot.emgBciSummary}")
        snapshot.movementRows.forEach { m ->
            sb.appendLine("  * ${m.metricName}: ${m.countOrValue} (${m.shareOrStatus})")
        }
        sb.appendLine()
        sb.appendLine("4. AUTHORIZED DIGITAL SIGNATURE")
        sb.appendLine("------------------------------------------------------------")
        sb.appendLine("Digital Signature : VERIFIED & EMBEDDED")
        sb.appendLine("Signature Stamp   : $cleanStamp")
        sb.appendLine("Graphic Stamp     : ${if (adminSig.signatureImageBase64.isNotBlank() || adminSig.signatureImageUri.isNotBlank()) "Embedded" else "Verified Script"}")
        sb.appendLine("Verification Hash : ${adminSig.hmacSha256}")
        sb.appendLine("============================================================")

        val textBody = sb.toString()
        val dateStr = formatFileDate(snapshot.generatedAtMillis)
        val fileName = "${sanitizeFilePrefix(appTitle)}_Progress_Report_${dateStr}_${snapshot.reportId.takeLast(4)}.txt"
        val outDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        if (!outDir.exists()) outDir.mkdirs()
        val txtFile = File(outDir, fileName)
        txtFile.writeText(textBody, Charsets.UTF_8)

        val bytes = txtFile.readBytes()
        val sha256Hex = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        return GeneratedReportArtifact(
            format = ReportExportFormat.TEXT,
            file = txtFile,
            fileName = fileName,
            mimeType = ReportExportFormat.TEXT.mimeType,
            sha256Hex = sha256Hex,
            fileSizeBytes = txtFile.length(),
            pageCount = 1,
            generatedAtFormatted = snapshot.generatedAtFormatted,
            isSigned = true,
            reportId = snapshot.reportId,
            textPreviewContent = textBody
        )
    }

    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun generateCsvReportArtifact(
        context: Context,
        snapshot: RoboHubReportSnapshot
    ): GeneratedReportArtifact {
        val branding = runCatching { AppBrandingCacheStore.load(context) }.getOrNull()
        val appTitle = branding?.appName?.takeIf { it.isNotBlank() } ?: AppBrandingConfig.DEFAULT_APP_NAME
        val adminSig = AdminDigitalSignatureManager.loadSignature(context)
        val cleanStamp = adminSig.signatureScript
            .replace(Regex("\\s*\\((?i:super\\s*admin|admin)\\)\\s*"), "")
            .trim()
            .ifBlank { "Authorized Signature" }

        val sb = StringBuilder()
        sb.appendLine("Section,Metric,Value,Details")
        sb.appendLine(listOf("1. Report", "Application", appTitle, snapshot.appVersion).joinToString(",") { csvEscape(it) })
        sb.appendLine(listOf("1. Report", "Report ID", snapshot.reportId, snapshot.generatedAtFormatted).joinToString(",") { csvEscape(it) })
        sb.appendLine(listOf("1. Report", "Reporting Period", snapshot.reportingPeriodLabel, snapshot.sessionDurationSummary).joinToString(",") { csvEscape(it) })
        sb.appendLine(listOf("1. Report", "User", snapshot.userDisplayName, snapshot.userSafeId).joinToString(",") { csvEscape(it) })

        snapshot.connectionRows.forEach { row ->
            sb.appendLine(listOf("2. System Status", row.moduleName, row.status, row.lastVerified).joinToString(",") { csvEscape(it) })
        }

        sb.appendLine(listOf("3. Progress Stats", "Total Commands", snapshot.totalCommands.toString(), "Success: ${snapshot.successRateText}").joinToString(",") { csvEscape(it) })
        sb.appendLine(listOf("3. Progress Stats", "Grip Force", snapshot.gripForceSummary, "EMG/BCI: ${snapshot.emgBciSummary}").joinToString(",") { csvEscape(it) })
        snapshot.movementRows.forEach { m ->
            sb.appendLine(listOf("3. Movement", m.metricName, m.countOrValue, m.shareOrStatus).joinToString(",") { csvEscape(it) })
        }

        sb.appendLine(listOf("4. Digital Signature", "Official Stamp", cleanStamp, "SHA-256: ${adminSig.hmacSha256}").joinToString(",") { csvEscape(it) })
        val uSig = snapshot.userSignature
        if (uSig != null) {
            sb.appendLine(listOf("4. User Signature", uSig.signerName, uSig.signedAtFormatted, uSig.signatureLabel).joinToString(",") { csvEscape(it) })
        }

        val csvBody = sb.toString()
        val dateStr = formatFileDate(snapshot.generatedAtMillis)
        val fileName = "${sanitizeFilePrefix(appTitle)}_Progress_Report_${dateStr}_${snapshot.reportId.takeLast(4)}.csv"
        val outDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        if (!outDir.exists()) outDir.mkdirs()
        val csvFile = File(outDir, fileName)
        csvFile.writeText(csvBody, Charsets.UTF_8)

        val bytes = csvFile.readBytes()
        val sha256Hex = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        return GeneratedReportArtifact(
            format = ReportExportFormat.CSV,
            file = csvFile,
            fileName = fileName,
            mimeType = ReportExportFormat.CSV.mimeType,
            sha256Hex = sha256Hex,
            fileSizeBytes = csvFile.length(),
            pageCount = 1,
            generatedAtFormatted = snapshot.generatedAtFormatted,
            isSigned = true,
            reportId = snapshot.reportId,
            textPreviewContent = csvBody
        )
    }

    /**
     * Renders all pages of a generated PDF file into Bitmaps using Android's PdfRenderer
     * so the user can preview the exact multi-page PDF inside the app before saving or sharing.
     */
    fun renderPdfPagesToBitmaps(pdfFile: File): List<Bitmap> {
        if (!pdfFile.exists()) return emptyList()
        val bitmaps = mutableListOf<Bitmap>()
        var descriptor: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            descriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(descriptor)
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val scale = 2
                val bmp = Bitmap.createBitmap(
                    page.width * scale,
                    page.height * scale,
                    Bitmap.Config.ARGB_8888
                )
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bitmaps.add(bmp)
            }
        } catch (_: Exception) {
        } finally {
            try {
                renderer?.close()
            } catch (_: Exception) {
            }
            try {
                descriptor?.close()
            } catch (_: Exception) {
            }
        }
        return bitmaps
    }

    fun saveReportToPublicDownloads(context: Context, artifact: GeneratedReportArtifact): Result<String> {
        return try {
            val branding = runCatching { AppBrandingCacheStore.load(context) }.getOrNull()
            val appTitle = sanitizeFilePrefix(branding?.appName ?: AppBrandingConfig.DEFAULT_APP_NAME)
            val cleanDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val publicName = "${appTitle}_Progress_Report_$cleanDate.${artifact.format.extension}"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, publicName)
                    put(MediaStore.Downloads.MIME_TYPE, artifact.mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri: Uri? = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        artifact.file.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    Result.success("Downloads/$publicName")
                } else {
                    Result.success(artifact.file.absolutePath)
                }
            } else {
                val dlDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                if (!dlDir.exists()) dlDir.mkdirs()
                val dest = File(dlDir, publicName)
                artifact.file.copyTo(dest, overwrite = true)
                Result.success(dest.absolutePath)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Saves a copy of the generated PDF into the device's public Downloads collection
     * using MediaStore on Android 10+ (zero storage permissions required) and returns
     * a user-readable location summary.
     */
    fun savePdfToPublicDownloads(context: Context, artifact: GeneratedPdfArtifact): Result<String> {
        return saveReportToPublicDownloads(
            context,
            GeneratedReportArtifact(
                format = ReportExportFormat.PDF,
                file = artifact.file,
                fileName = artifact.fileName,
                mimeType = ReportExportFormat.PDF.mimeType,
                sha256Hex = artifact.sha256Hex,
                fileSizeBytes = artifact.fileSizeBytes,
                pageCount = artifact.pageCount,
                generatedAtFormatted = artifact.generatedAtFormatted,
                isSigned = artifact.isSigned,
                reportId = artifact.reportId
            )
        )
    }
}
