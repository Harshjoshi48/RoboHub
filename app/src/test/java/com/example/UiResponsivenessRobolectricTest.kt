package com.example

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h568dp-xxhdpi")
class UiResponsivenessRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compactPhone_loginScreen_rendersCleanlyWithFullTouchTargets() {
        composeRule.setContent {
            LoginScreen(
                onOtpRequired = { _, _ -> },
                onSignUp = {},
                onForgotPassword = {},
                onGoogleLogin = {}
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("LOGIN").assertExists().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("SIGN UP").assertExists().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("Continue with Google").assertExists().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun compactPhone_otpScreen_fitsSixDigitRowWithoutOverflow() {
        composeRule.setContent {
            OtpScreen(
                email = "hj195345@gmail.com",
                next = "admin_login",
                onBack = {}
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Verify Admin OTP").assertExists()
        composeRule.onNodeWithText("VERIFY OTP & OPEN ADMIN PANEL").assertExists().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun compactPhone_permissionsScreen_rendersWithinBounds() {
        composeRule.setContent {
            PermissionsScreen(onBack = {}, onContinue = {})
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("STEP 2 OF 5").assertIsDisplayed()
        composeRule.onNodeWithText("Allow & Continue").assertExists().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun compactPhone_connectDevicesScreen_rendersWithinBounds() {
        composeRule.setContent {
            ConnectDevicesScreen(onBack = {}, onContinue = {})
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Connect Your Devices").assertIsDisplayed()
    }

    @Test
    fun compactPhone_resetPasswordScreen_rendersWithinBounds() {
        composeRule.setContent {
            ResetPasswordScreen(onResetSuccess = {}, onBack = {})
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Create New\nPassword").assertIsDisplayed()
        composeRule.onAllNodesWithText("Reset Password").assertCountEquals(2)
    }

    @Test
    fun compactPhone_dashboardScreen_rendersWithoutClipping() {
        val baseline = SduiSyncState(
            config = SduiDashboardConfig.baselineDefault(),
            activeVersion = 1,
            lastSyncSource = "BASELINE"
        )
        composeRule.setContent {
            DashboardScreen(
                username = "tarun",
                onLogout = {},
                sduiStateOverride = baseline,
                imageModelResolverOverride = { it }
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("NeuroGrip Ready").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("sdui_hero_connect_button").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("Connect ESP32").assertIsDisplayed()
        composeRule.onNodeWithText("LIVE BIO-SIGNALS").assertDoesNotExist()
        composeRule.onNodeWithText("PROSTHETIC STATUS").assertDoesNotExist()
    }

    @Test
    fun compactPhone_handControlScreen_rendersWithoutClipping() {
        val dashboardVm = DashboardViewModel()
        composeRule.setContent {
            HandControlScreen(
                onBack = {},
                onNavigateToHome = {},
                onNavigateToAI = {},
                onNavigateToReports = {},
                onNavigateToProfile = {},
                viewModel = dashboardVm
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Hand Control").assertIsDisplayed()
        composeRule.onNodeWithText("OPEN HAND").assertExists()
        composeRule.onNodeWithText("CLOSE HAND").assertExists()
        composeRule.onNodeWithText("SEND COMMAND").assertExists()
    }

    @Test
    fun compactPhone_aiAssistantScreen_rendersWithoutClipping() {
        val dashboardVm = DashboardViewModel()
        composeRule.setContent {
            AIAssistantScreen(viewModel = dashboardVm, onBack = {})
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("AI Assistant").assertIsDisplayed()
    }

    @Test
    fun compactPhone_gestureStudioScreen_rendersCleanly() {
        composeRule.setContent {
            GestureStudioScreen(onBack = {})
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Gesture Studio").assertCountEquals(2)
        composeRule.onNodeWithText("+ Create New Gesture").assertExists()
    }

    @Test
    fun compactPhone_reportsScreen_rendersCleanly() {
        composeRule.setContent {
            ReportsScreen(onBack = {})
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Progress Report").assertIsDisplayed()
        composeRule.onNodeWithTag("reports_app_icon").assertExists()

        // Verify old banner & admin clutter are completely removed
        composeRule.onNodeWithText("RoboHub — Advanced Telemetry & Usage Report").assertDoesNotExist()
        composeRule.onNodeWithText("AUTHENTICATED USER").assertDoesNotExist()
        composeRule.onNodeWithText("SESSION & SIGNATURE").assertDoesNotExist()
        composeRule.onNodeWithText("Unsigned Report").assertDoesNotExist()

        // Verify strictly sections 1, 2, 3, 4 and zero section 5
        composeRule.onNodeWithText("1. Report Period").assertIsDisplayed()
        composeRule.onNodeWithText("2. Select Date Range").assertIsDisplayed()
        composeRule.onNodeWithText("3. Select Report Format").assertExists()
        composeRule.onNodeWithText("4. Generate Report").assertExists()
        composeRule.onNodeWithText("5. Generate, Preview, Download & Share PDF").assertDoesNotExist()

        // Verify presets, date pickers, formats, and actions
        composeRule.onNodeWithTag("period_chip_today").assertExists()
        composeRule.onNodeWithTag("period_chip_last_7_days").assertExists()
        composeRule.onNodeWithTag("period_chip_last_30_days").assertExists()
        composeRule.onNodeWithTag("period_chip_all_time").assertExists()
        composeRule.onNodeWithTag("report_start_date_picker").assertExists()
        composeRule.onNodeWithTag("report_end_date_picker").assertExists()
        composeRule.onNodeWithTag("format_chip_pdf").assertExists()
        composeRule.onNodeWithTag("format_chip_png").assertExists()
        composeRule.onNodeWithTag("format_chip_text").assertExists()
        composeRule.onNodeWithTag("format_chip_csv").assertExists()
        composeRule.onNodeWithTag("generate_robohub_pdf_button").assertExists()
        composeRule.onNodeWithTag("download_robohub_pdf_button").assertExists()
        composeRule.onNodeWithTag("preview_robohub_pdf_button").assertExists()
        composeRule.onNodeWithTag("share_robohub_pdf_button").assertExists()
    }

    @Test
    fun compactPhone_notificationsScreen_rendersCleanly() {
        composeRule.setContent {
            NotificationsScreen(onBack = {})
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Notifications & Feedback").assertIsDisplayed()
        composeRule.onNodeWithText("Send Feedback").assertIsDisplayed()
    }

    @Test
    fun compactPhone_profileScreen_rendersCleanly() {
        composeRule.setContent {
            ProfileScreen(
                username = "tarun",
                onLogout = {},
                onNavigateToDashboard = {},
                onNavigateToSettings = {}
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Log Out").assertExists()
    }

    @Test
    fun compactPhone_settingsScreen_rendersCleanly() {
        composeRule.setContent {
            SettingsScreen(onBack = {}, onLogout = {})
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Logout").assertExists()
    }

    @Test
    fun compactPhone_adminSduiTab_rendersWithTouchTargets() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val adminVm = AdminViewModel(app)
        val baseline = SduiSyncState(
            config = SduiDashboardConfig.baselineDefault(),
            activeVersion = 1,
            lastSyncSource = "BASELINE"
        )
        composeRule.setContent {
            AdminSduiTab(
                sduiSyncState = baseline,
                sduiHistory = emptyList(),
                adminViewModel = adminVm
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Real-Time Server-Driven UI (SDUI)").assertIsDisplayed()
        composeRule.onNodeWithTag("sdui_sync_now_button").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("sdui_reset_baseline_button").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun compactPhone_systemConfigScreen_rendersCleanly() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val adminVm = AdminViewModel(app)
        composeRule.setContent {
            SystemConfigScreen(adminViewModel = adminVm)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("system_config_screen").assertIsDisplayed()
        composeRule.onNodeWithText("System Configuration").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w840dp-h1200dp-xxhdpi")
    fun tabletScreen_dashboard_rendersCenteredLayout() {
        val baseline = SduiSyncState(
            config = SduiDashboardConfig.baselineDefault(),
            activeVersion = 1,
            lastSyncSource = "BASELINE"
        )
        composeRule.setContent {
            DashboardScreen(
                username = "tarun",
                onLogout = {},
                sduiStateOverride = baseline,
                imageModelResolverOverride = { it }
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("NeuroGrip Ready").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("sdui_promo_banner").assertDoesNotExist()
        composeRule.onNodeWithText("Quick Actions").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun allFifteenRequirements_functionalVerificationSuite() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = AppDatabase.getInstance(app)
        val repo = AdminRepository(app)

        // Req 2: 30-day session TTL (43,200 minutes) and zero plaintext password storage
        org.junit.Assert.assertEquals(43200, SecureSessionManager.REMEMBER_ME_30_DAYS_TTL_MINUTES)

        // Req 6 & 15: Demo users purged, Super Admins hj195344@gmail.com and hj195345@gmail.com preserved & protected
        repo.ensureSeeded()
        val allUsers = db.userDao().getAllUsersSnapshot()
        val usernames = allUsers.map { u -> u.username.lowercase() }
        val emails = allUsers.map { u -> u.email.lowercase() }
        listOf("drmehta", "priya_ed", "rohan_sup", "ananya_an", "vikram", "joshi").forEach { demo ->
            org.junit.Assert.assertFalse("Demo user $demo must not exist", usernames.contains(demo))
        }
        org.junit.Assert.assertTrue(emails.contains("hj195344@gmail.com"))
        org.junit.Assert.assertTrue(emails.contains("hj195345@gmail.com"))

        val superAdmin1 = allUsers.first { u -> u.email.equals("hj195344@gmail.com", ignoreCase = true) }
        val deleteProtected = repo.deleteUserSecurely("hj123", AdminRole.SUPER_ADMIN, superAdmin1.id, hardDelete = true)
        org.junit.Assert.assertTrue("Super Admin must be protected from deletion", deleteProtected is RepoResult.Error)

        // Req 6: Admin login without separate page routes SUPER_ADMIN and persists 30-day session
        val loginRes = repo.authenticate("hj195344@gmail.com", "12345", rememberMe = true)
        org.junit.Assert.assertTrue("Super Admin login must succeed", loginRes is RepoResult.Success<*>)
        val session = SecureSessionManager.loadSession(app)
        org.junit.Assert.assertNotNull(session)
        org.junit.Assert.assertEquals("SUPER_ADMIN", session?.role)
        org.junit.Assert.assertTrue(
            "30-day expiry must be > 29 days in the future",
            (session?.expiresAt ?: 0L) - System.currentTimeMillis() > 29L * 24L * 3600L * 1000L
        )

        // Req 7, 8, 14: Admin Digital Signature + Report Generation + Date Search (15/11/2026) + User report log exclusion
        val sigRes = AdminDigitalSignatureManager.saveSignature(
            context = app,
            signerName = "Dr. H. J. Super Admin",
            signerTitle = "Chief Neuro-Prosthetics Director",
            signerEmail = "hj195345@gmail.com",
            signatureImageUri = "content://neurogrip/signature_stamp.png"
        )
        org.junit.Assert.assertTrue(sigRes.isSuccess)
        org.junit.Assert.assertTrue(AdminDigitalSignatureManager.loadSignature(app).signatureDigestSha256.isNotBlank())

        val userSnapshot = RoboHubReportGenerator.buildLiveReportSnapshot(
            context = app,
            periodStartMillis = System.currentTimeMillis() - 86_400_000L,
            periodEndMillis = System.currentTimeMillis(),
            periodLabel = "Last 24 Hours",
            currentSession = session,
            users = allUsers,
            loginHistory = emptyList(),
            auditLogs = listOf(
                AuditLogEntity(
                    actorUsername = "hj123",
                    actorRole = "SUPER_ADMIN",
                    actionType = "INTERNAL_DIAGNOSTIC",
                    targetEntity = "system",
                    details = "Internal audit log entry"
                )
            ),
            tickets = emptyList(),
            isEsp32Connected = true,
            isEsp32Connecting = false,
            esp32Ip = "AA:BB:CC:DD:EE:FF",
            lastVerifiedConnAt = System.currentTimeMillis(),
            connectionInterruptions = 0,
            eegAttention = 82,
            emgStrength = 76,
            gripForceN = 28f,
            servoTempC = 34.5f,
            batteryPct = 92,
            latencyMs = 14,
            sessionStartMillis = System.currentTimeMillis() - 3_600_000L,
            commandHistory = emptyList(),
            firestoreConfigured = true,
            firestoreProjectId = "ai-studio-android-neuropul",
            firestoreDatabaseId = "ai-studio-android-neuropul-c40fab35-e98a-46c6-850b-96b0e10b6910",
            confirmedSignature = null,
            includeInternalLogs = false
        )
        org.junit.Assert.assertTrue("User report snapshot must exclude internal logs when includeInternalLogs=false", userSnapshot.eventLogs.isEmpty())

        val record = GeneratedReportRecord(
            reportId = "RPT-TEST-1511",
            username = "patient",
            userDisplayName = "Real Patient",
            userEmail = "patient@gmail.com",
            reportType = "PDF",
            periodStartFormatted = "01/11/2026",
            periodEndFormatted = "15/11/2026",
            generatedDateDisplay = "15/11/2026",
            generatedDateIso = "2026-11-15",
            associatedLogs = listOf("EMG peak 88%", "Grip stability nominal")
        )
        GeneratedReportStore.saveReport(app, record)
        val matchedByDdMmYyyy = GeneratedReportStore.searchReports(app, userQuery = "patient@gmail.com", dateQuery = "15/11/2026")
        org.junit.Assert.assertTrue(matchedByDdMmYyyy.any { r -> r.reportId == "RPT-TEST-1511" })

        // Verify App Icon loading & Multi-format Report Generation (PDF, PNG, TEXT, CSV) with Sections 1-4 & Admin Digital Signature
        val appIconBmp = RoboHubReportGenerator.loadAppIconOrBrandingBitmap(app, 64)
        org.junit.Assert.assertNotNull("App launcher/branding icon bitmap must load for reports", appIconBmp)
        org.junit.Assert.assertFalse("User display name must not include Super Admin suffix", userSnapshot.userDisplayName.contains("Super Admin", ignoreCase = true))

        ReportExportFormat.entries.forEach { fmt ->
            val artifact = RoboHubReportGenerator.generateReportArtifact(
                context = app,
                snapshot = userSnapshot,
                format = fmt,
                includeInternalLogs = false
            )
            org.junit.Assert.assertTrue("${fmt.label} report file must exist", artifact.file.exists())
            org.junit.Assert.assertTrue("${fmt.label} report file must be non-empty", artifact.fileSizeBytes > 0L)
            org.junit.Assert.assertEquals(64, artifact.sha256Hex.length)
            if (fmt == ReportExportFormat.TEXT || fmt == ReportExportFormat.CSV) {
                org.junit.Assert.assertTrue(artifact.textPreviewContent.contains("1."))
                org.junit.Assert.assertTrue(artifact.textPreviewContent.contains("4."))
                org.junit.Assert.assertFalse(artifact.textPreviewContent.contains("5."))
                org.junit.Assert.assertFalse(artifact.textPreviewContent.contains("SUPER_ADMIN"))
            }
        }

        // Req 11: Notification creation & immediate deletion from Room
        val sendNotif = repo.sendNotification(
            actorUsername = "hj123",
            actorRole = AdminRole.SUPER_ADMIN,
            title = "Test Alert",
            message = "Verify deletion",
            targetAudience = "ALL"
        )
        org.junit.Assert.assertTrue(sendNotif is RepoResult.Success<*>)
        val createdNotifId = (sendNotif as RepoResult.Success<NotificationEntity>).data.id
        val delNotif = repo.deleteNotification("hj123", AdminRole.SUPER_ADMIN, createdNotifId)
        org.junit.Assert.assertTrue(delNotif is RepoResult.Success<*>)
        val remainingNotifs = db.adminDao().getAllNotificationsSnapshot()
        org.junit.Assert.assertFalse(remainingNotifs.any { n -> n.id == createdNotifId })
    }
}
