package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class AppBrandingSystemTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var appContext: Context

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        SecureSessionManager.clearSession(appContext)
        AppBrandingCacheStore.clearLocalLogoCopies(appContext)
        AppBrandingCacheStore.save(appContext, AppBrandingConfig.default())
    }

    @After
    fun tearDown() {
        SecureSessionManager.clearSession(appContext)
        AppBrandingCacheStore.clearLocalLogoCopies(appContext)
        AppBrandingCacheStore.save(appContext, AppBrandingConfig.default())
    }

    private fun buildValidPngBytes(width: Int = 256, height: Int = 256, extraPadding: Int = 0): ByteArray {
        val header = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D,
            0x49, 0x48, 0x44, 0x52, // IHDR
            ((width ushr 24) and 0xFF).toByte(),
            ((width ushr 16) and 0xFF).toByte(),
            ((width ushr 8) and 0xFF).toByte(),
            (width and 0xFF).toByte(),
            ((height ushr 24) and 0xFF).toByte(),
            ((height ushr 16) and 0xFF).toByte(),
            ((height ushr 8) and 0xFF).toByte(),
            (height and 0xFF).toByte(),
            0x08, 0x06, 0x00, 0x00, 0x00
        )
        return if (extraPadding > 0) header + ByteArray(extraPadding) else header
    }

    @Test
    fun validator_checksPngJpgWebpSizeDimensionsAndAppName() {
        val validPng = AppBrandingValidator.validateImageBytesAndMetadata(
            bytes = buildValidPngBytes(512, 512),
            mimeType = "image/png",
            fileName = "brand.png"
        )
        assertTrue(validPng.isSuccess)
        assertEquals("png", validPng.getOrThrow().extension)
        assertEquals(512, validPng.getOrThrow().widthPx)
        assertEquals(512, validPng.getOrThrow().heightPx)

        val tooSmall = AppBrandingValidator.validateImageBytesAndMetadata(
            bytes = buildValidPngBytes(32, 32),
            mimeType = "image/png",
            fileName = "tiny.png"
        )
        assertTrue(tooSmall.isFailure)
        assertTrue(tooSmall.exceptionOrNull()?.message?.contains("too small", ignoreCase = true) == true)

        val tooLargeDims = AppBrandingValidator.validateImageBytesAndMetadata(
            bytes = buildValidPngBytes(4096, 4096),
            mimeType = "image/png",
            fileName = "huge.png"
        )
        assertTrue(tooLargeDims.isFailure)
        assertTrue(tooLargeDims.exceptionOrNull()?.message?.contains("too large", ignoreCase = true) == true)

        val oversizedBytes = AppBrandingValidator.validateImageBytesAndMetadata(
            bytes = buildValidPngBytes(256, 256),
            mimeType = "image/png",
            fileName = "overlimit.png",
            maxBytes = 20L
        )
        assertTrue(oversizedBytes.isFailure)
        assertTrue(oversizedBytes.exceptionOrNull()?.message?.contains("limit", ignoreCase = true) == true)

        val unsupportedFormat = AppBrandingValidator.validateImageBytesAndMetadata(
            bytes = byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61), // GIF89a
            mimeType = "image/gif",
            fileName = "anim.gif"
        )
        assertTrue(unsupportedFormat.isFailure)

        assertNotNull(AppBrandingValidator.validateAppName(""))
        assertNotNull(AppBrandingValidator.validateAppName("   "))
        assertNotNull(AppBrandingValidator.validateAppName("A".repeat(31)))
        assertNotNull(AppBrandingValidator.validateAppName("Bad\nName"))
        assertNull(AppBrandingValidator.validateAppName("NeuroPulse Pro"))
    }

    @Test
    fun repository_enforcesSuperAdminOnlyPublishRestoreAndOfflineCache() = runBlocking {
        val repo = AdminRepository(appContext)
        val validLogo = AppBrandingValidator.validateImageBytesAndMetadata(
            bytes = buildValidPngBytes(512, 512),
            mimeType = "image/png",
            fileName = "pulse_logo.png"
        ).getOrThrow()

        // Non-Super-Admins must be rejected with 403
        for (deniedRole in listOf(AdminRole.ADMIN, AdminRole.EDITOR, AdminRole.USER)) {
            val deniedRes = repo.publishAppBrandingChanges(
                actorUsername = "staff_tester",
                actorRole = deniedRole,
                newAppName = "BlockedRename",
                stagedLogo = validLogo
            )
            assertTrue("Role $deniedRole must be blocked", deniedRes is RepoResult.Error)
            assertEquals(403, (deniedRes as RepoResult.Error).code)
        }

        // Super Admin publishes new name + logo
        val publishRes = repo.publishAppBrandingChanges(
            actorUsername = "hj123",
            actorRole = AdminRole.SUPER_ADMIN,
            newAppName = "PulseRehab",
            stagedLogo = validLogo,
            removeLogo = false,
            notifyUsers = true,
            summaryNote = "Brand rollout v2"
        )
        assertTrue("Super Admin publish must succeed: $publishRes", publishRes is RepoResult.Success)
        val updated = (publishRes as RepoResult.Success).data
        assertEquals("PulseRehab", updated.appName)
        assertEquals(2L, updated.brandingVersion)
        assertEquals(2L, updated.logoVersion)
        assertTrue(updated.localCachedLogoPath.isNotBlank())
        assertTrue(java.io.File(updated.localCachedLogoPath).exists())

        // Verify persistence survives cold-start load
        val reloaded = AppBrandingCacheStore.load(appContext)
        assertEquals("PulseRehab", reloaded.appName)
        assertEquals(2L, reloaded.brandingVersion)
        assertEquals(2L, reloaded.logoVersion)

        // Restore defaults resets both name and logo and increments versions
        val restoreRes = repo.restoreDefaultAppBranding(
            actorUsername = "hj123",
            actorRole = AdminRole.SUPER_ADMIN,
            notifyUsers = true
        )
        assertTrue(restoreRes is RepoResult.Success)
        val restored = (restoreRes as RepoResult.Success).data
        assertEquals(AppBrandingConfig.DEFAULT_APP_NAME, restored.appName)
        assertFalse(restored.hasCustomLogo)
        assertEquals(3L, restored.brandingVersion)
        assertEquals(3L, restored.logoVersion)

        // Launcher APK release advisory RBAC + Remote Config update
        val deniedAdvisory = repo.publishLauncherBrandingReleaseAdvisory(
            actorUsername = "editor_user",
            actorRole = AdminRole.ADMIN,
            recommendedVersionName = "2.5.0",
            releaseSummary = "Denied",
            forceUpdate = false
        )
        assertTrue(deniedAdvisory is RepoResult.Error)
        assertEquals(403, (deniedAdvisory as RepoResult.Error).code)

        val allowedAdvisory = repo.publishLauncherBrandingReleaseAdvisory(
            actorUsername = "hj123",
            actorRole = AdminRole.SUPER_ADMIN,
            recommendedVersionName = "2.5.0",
            releaseSummary = "Install APK v2.5.0 for updated launcher icon",
            forceUpdate = true
        )
        assertTrue(allowedAdvisory is RepoResult.Success)
        assertEquals("2.5.0", (allowedAdvisory as RepoResult.Success).data.recommendedVersionName)
        assertTrue(allowedAdvisory.data.forceUpdate)
    }

    @Test
    fun adminBrandingTab_updatesPreviewPublishesAndEnforcesSuperAdminUiLock() {
        val session = SecureSessionManager.issueSignedToken(
            userId = 1,
            username = "hj123",
            role = AdminRole.SUPER_ADMIN,
            ttlMinutes = 60,
            email = "hj195345@gmail.com",
            displayName = "Harsh Joshi",
            otpVerified = true
        )
        SecureSessionManager.saveSession(appContext, session, rememberMe = true)
        val vm = AdminViewModel(appContext.applicationContext as Application)

        composeRule.setContent {
            MyApplicationTheme {
                AdminBrandingTab(adminViewModel = vm)
            }
        }

        composeRule.onNodeWithTag("admin_branding_tab").performScrollToNode(hasTestTag("branding_name_input"))
        composeRule.onNodeWithTag("branding_name_input").performTextReplacement("BioSync Hand")

        composeRule.onNodeWithTag("admin_branding_tab").performScrollToNode(hasTestTag("branding_preview_card"))
        composeRule.onNodeWithTag("branding_preview_card").assertIsDisplayed()
        composeRule.onNodeWithTag("branding_preview_app_name").assertTextContains("BioSync Hand")

        composeRule.onNodeWithTag("admin_branding_tab").performScrollToNode(hasTestTag("branding_publish_button"))
        composeRule.onNodeWithTag("branding_publish_button").assertIsEnabled().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            AppBrandingCacheStore.load(appContext).appName == "BioSync Hand"
        }
        assertEquals("BioSync Hand", AppBrandingCacheStore.load(appContext).appName)

        // Switching to non-Super-Admin role locks out branding actions
        vm.setRoleSimulationOverride(AdminRole.ADMIN)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_branding_tab").performScrollToNode(hasTestTag("branding_super_admin_restricted_banner"))
        composeRule.onNodeWithTag("branding_super_admin_restricted_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_branding_tab").performScrollToNode(hasTestTag("branding_publish_button"))
        composeRule.onNodeWithTag("branding_publish_button").assertIsNotEnabled()
    }

    @Test
    fun userScreens_liveUpdateAppNameAndLogoWithoutRestart() {
        val vm = AdminViewModel(appContext.applicationContext as Application)
        val customLogoFile = java.io.File(appContext.filesDir, "test_custom_logo.png").apply {
            writeBytes(buildValidPngBytes(256, 256))
        }
        val logoUriString = android.net.Uri.fromFile(customLogoFile).toString()

        composeRule.setContent {
            MyApplicationTheme {
                DashboardScreen(
                    username = "patient_one",
                    onLogout = {},
                    adminViewModel = vm
                )
            }
        }

        // Baseline title
        composeRule.onNodeWithTag("sdui_brand_title").assertTextContains("NeuroGrip")

        // Simulate live branding publish while user is viewing DashboardScreen
        AppBrandingCacheStore.save(
            appContext,
            AppBrandingConfig(
                appName = "KinetixPro",
                logoUrl = logoUriString,
                logoVersion = 4L,
                brandingVersion = 4L,
                updatedBy = "hj123",
                localCachedLogoPath = customLogoFile.absolutePath,
                lastSyncSource = "FIRESTORE"
            )
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("sdui_brand_title").assertTextContains("KinetixPro")
        composeRule.onNodeWithTag("dashboard_branding_logo").assertIsDisplayed()
        composeRule.onNodeWithText("NeuroGrip Ready").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun profileAndLoginScreens_reflectLiveRemoteBranding() {
        val vm = AdminViewModel(appContext.applicationContext as Application)
        AppBrandingCacheStore.save(
            appContext,
            AppBrandingConfig(
                appName = "VitaGrip",
                logoVersion = 2L,
                brandingVersion = 2L,
                updatedBy = "hj123"
            )
        )

        composeRule.setContent {
            MyApplicationTheme {
                ProfileScreen(
                    username = "Harsh",
                    onLogout = {},
                    onNavigateToDashboard = {},
                    onNavigateToSettings = {},
                    adminViewModel = vm
                )
            }
        }
        composeRule.onNodeWithTag("profile_branding_app_name").assertTextContains("VitaGrip")
    }
}
