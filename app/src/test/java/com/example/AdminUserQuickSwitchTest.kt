package com.example

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class AdminUserQuickSwitchTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var context: Context

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        context = app.applicationContext
        SecureSessionManager.clearSession(context)
    }

    @After
    fun tearDown() {
        SecureSessionManager.clearSession(context)
    }

    private fun issueAndPersistSession(
        userId: Int,
        username: String,
        email: String,
        role: AdminRole,
        ttlMinutes: Int = 30
    ): AuthSessionDto {
        val session = SecureSessionManager.issueSignedToken(
            userId = userId,
            username = username,
            role = role,
            ttlMinutes = ttlMinutes,
            email = email,
            displayName = username,
            otpVerified = true
        )
        SecureSessionManager.saveSession(context, session, rememberMe = false)
        return session
    }

    @Test
    fun `super admin opens user app preserves selected tab and returns cleanly via floating icon across all 5 screens`() {
        issueAndPersistSession(
            userId = 1,
            username = "hj195344",
            email = "hj195344@gmail.com",
            role = AdminRole.SUPER_ADMIN
        )
        val adminVm = AdminViewModel(app)
        adminVm.refreshSessionFromStorage()
        val dashVm = DashboardViewModel()

        assertTrue(adminVm.isAuthorizedAdminSession.value)

        composeRule.setContent {
            val navController = rememberNavController()
            val showFloating by adminVm.showFloatingAdminReturnButton.collectAsStateWithLifecycle()
            val isAuthorized by adminVm.isAuthorizedAdminSession.collectAsStateWithLifecycle()
            val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

            Box(modifier = Modifier.fillMaxSize()) {
                NavHost(navController = navController, startDestination = "admin_panel") {
                    composable("admin_panel") {
                        AdminPanelScreen(
                            adminViewModel = adminVm,
                            onNavigateToUserAppPreview = { uname ->
                                if (adminVm.enterUserAppPreviewMode()) {
                                    navController.navigate("dashboard/$uname") { launchSingleTop = true }
                                }
                            },
                            onLogout = { adminVm.logoutAdmin() }
                        )
                    }
                    composable("dashboard/{username}") { entry ->
                        val uname = entry.arguments?.getString("username") ?: "User"
                        DashboardScreen(
                            username = uname,
                            viewModel = dashVm,
                            adminViewModel = adminVm,
                            onLogout = { adminVm.logoutAdmin() },
                            onNavigateToHandControl = { navController.navigate("hand_control") },
                            onNavigateToAIAssistant = { navController.navigate("ai_assistant") },
                            onNavigateToReports = { navController.navigate("reports") },
                            onNavigateToProfile = { navController.navigate("profile/$uname") }
                        )
                    }
                    composable("hand_control") {
                        HandControlScreen(
                            onBack = { navController.popBackStack() },
                            onNavigateToHome = { navController.navigate("dashboard/hj195344") },
                            onNavigateToAI = { navController.navigate("ai_assistant") },
                            onNavigateToReports = { navController.navigate("reports") },
                            onNavigateToProfile = { navController.navigate("profile/hj195344") },
                            viewModel = dashVm,
                            adminViewModel = adminVm
                        )
                    }
                    composable("ai_assistant") {
                        AIAssistantScreen(
                            viewModel = dashVm,
                            onBack = { navController.popBackStack() },
                            onNavigateToHome = { navController.navigate("dashboard/hj195344") },
                            onNavigateToControl = { navController.navigate("hand_control") },
                            onNavigateToReports = { navController.navigate("reports") },
                            onNavigateToProfile = { navController.navigate("profile/hj195344") }
                        )
                    }
                    composable("reports") {
                        ReportsScreen(
                            onBack = { navController.popBackStack() },
                            onNavigateToHome = { navController.navigate("dashboard/hj195344") },
                            onNavigateToControl = { navController.navigate("hand_control") },
                            onNavigateToAI = { navController.navigate("ai_assistant") },
                            onNavigateToProfile = { navController.navigate("profile/hj195344") },
                            dashboardViewModel = dashVm,
                            adminViewModel = adminVm
                        )
                    }
                    composable("profile/{username}") { entry ->
                        val uname = entry.arguments?.getString("username") ?: "User"
                        ProfileScreen(
                            username = uname,
                            onLogout = { adminVm.logoutAdmin() },
                            onNavigateToDashboard = { navController.navigate("dashboard/$uname") },
                            onNavigateToSettings = {},
                            onNavigateToHandControl = { navController.navigate("hand_control") },
                            onNavigateToAIAssistant = { navController.navigate("ai_assistant") },
                            onNavigateToReports = { navController.navigate("reports") },
                            adminViewModel = adminVm
                        )
                    }
                }

                FloatingAdminReturnButton(
                    visible = showFloating && isAuthorized && currentRoute in USER_APP_PREVIEW_ROUTES,
                    onClick = {
                        if (adminVm.returnToAdminPanel()) {
                            navController.popBackStack("admin_panel", inclusive = false)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 14.dp,
                            bottom = if (currentRoute == "ai_assistant") 166.dp else 94.dp
                        )
                )
            }
        }

        // Select a non-default tab (BANNERS) before switching to User App
        composeRule.runOnUiThread {
            adminVm.selectAdminSection(AdminSection.BANNERS)
        }
        composeRule.waitForIdle()
        assertEquals(AdminSection.BANNERS, adminVm.selectedAdminSection.value)

        // Click "User App" inside Admin Panel
        composeRule.onNodeWithTag("open_user_app_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        // Verify Admin Preview Mode active, session intact, and floating return icon visible on Home
        assertTrue(adminVm.isAdminPreviewMode.value)
        assertEquals(AdminRole.SUPER_ADMIN, adminVm.verifiedSessionAdminRole())
        composeRule.onNodeWithTag("floating_admin_return_button")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Return to Admin Panel").assertIsDisplayed()

        // Navigate through remaining 4 User App screens: Control -> AI -> Progress -> Profile
        composeRule.onNodeWithText("Control").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("floating_admin_return_button").assertIsDisplayed()

        composeRule.onNodeWithText("AI").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("floating_admin_return_button").assertIsDisplayed()

        composeRule.onNodeWithText("Progress").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("floating_admin_return_button").assertIsDisplayed()

        composeRule.onNodeWithText("Profile").performClick()
        composeRule.waitForIdle()
        assertTrue("Session should remain valid on Profile", SecureSessionManager.isSessionValid(adminVm.currentSession.value))
        assertTrue("Admin session should remain authorized on Profile", adminVm.isAuthorizedAdminSession.value)
        assertTrue("Preview mode should remain true on Profile", adminVm.isAdminPreviewMode.value)
        assertTrue("showFloatingAdminReturnButton should remain true on Profile", adminVm.showFloatingAdminReturnButton.value)
        composeRule.onNodeWithTag("floating_admin_return_button").assertIsDisplayed()

        // Click Floating Admin Return Icon to jump straight back to Admin Panel preserving selected tab
        composeRule.onNodeWithTag("floating_admin_return_button").performClick()
        composeRule.waitForIdle()

        assertFalse(adminVm.isAdminPreviewMode.value)
        assertEquals(AdminSection.BANNERS, adminVm.selectedAdminSection.value)
        composeRule.onNodeWithTag("open_user_app_button").assertIsDisplayed()
        composeRule.onAllNodesWithTag("floating_admin_return_button").assertCountEquals(0)
    }

    @Test
    fun `other authorized admin roles can quick switch while normal users never see icon or access admin panel`() {
        val db = AppDatabase.getInstance(context)
        val createdAdminId = runBlocking {
            AppDatabase.seedDatabaseIfNeeded(db)
            val existing: User? = db.userDao().getUserByIdentifier("ops.admin@neurogrip.io")
            if (existing != null) {
                db.userDao().updateUser(existing.copy(role = AdminRole.ADMIN.name, status = AccountStatus.ACTIVE.name))
                existing.id
            } else {
                db.userDao().insertUser(
                    User(
                        name = "Operations Admin",
                        email = "ops.admin@neurogrip.io",
                        username = "ops_admin",
                        passwordHash = SecurityHasher.hashPassword("AdminPass123!"),
                        role = AdminRole.ADMIN.name,
                        status = AccountStatus.ACTIVE.name
                    )
                ).toInt()
            }
        }

        // 1. Authorized ADMIN account opens User App
        issueAndPersistSession(
            userId = createdAdminId,
            username = "ops_admin",
            email = "ops.admin@neurogrip.io",
            role = AdminRole.ADMIN
        )
        val adminVm = AdminViewModel(app)
        adminVm.refreshSessionFromStorage()

        assertTrue(adminVm.isAuthorizedAdminSession.value)
        assertTrue(adminVm.enterUserAppPreviewMode())
        assertTrue(adminVm.showFloatingAdminReturnButton.value)
        assertTrue(adminVm.returnToAdminPanel())

        // 2. Normal USER account even if DashboardScreen username route param tries "hj123"
        val normalUserId = runBlocking {
            val existing: User? = db.userDao().getUserByIdentifier("patient.normal@example.com")
            if (existing != null) {
                db.userDao().updateUser(existing.copy(role = AdminRole.USER.name, status = AccountStatus.ACTIVE.name))
                existing.id
            } else {
                db.userDao().insertUser(
                    User(
                        name = "Normal Patient",
                        email = "patient.normal@example.com",
                        username = "patient_normal",
                        passwordHash = SecurityHasher.hashPassword("UserPass123!"),
                        role = AdminRole.USER.name,
                        status = AccountStatus.ACTIVE.name
                    )
                ).toInt()
            }
        }
        issueAndPersistSession(
            userId = normalUserId,
            username = "patient_normal",
            email = "patient.normal@example.com",
            role = AdminRole.USER
        )
        val userVm = AdminViewModel(app)
        userVm.refreshSessionFromStorage()
        val dashVm = DashboardViewModel()

        assertFalse("USER role must not pass admin verification", userVm.isAuthorizedAdminSession.value)
        assertFalse("USER role must never enter Admin Preview Mode", userVm.enterUserAppPreviewMode())
        assertFalse(userVm.showFloatingAdminReturnButton.value)
        assertFalse(userVm.returnToAdminPanel())

        composeRule.setContent {
            val showFloating by userVm.showFloatingAdminReturnButton.collectAsStateWithLifecycle()
            Box(modifier = Modifier.fillMaxSize()) {
                DashboardScreen(
                    username = "hj123",
                    onLogout = {},
                    onNavigateToAdminPanel = {},
                    viewModel = dashVm,
                    adminViewModel = userVm
                )
                FloatingAdminReturnButton(
                    visible = showFloating,
                    onClick = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Must NOT show hardcoded username admin shortcut or floating return icon
        composeRule.onAllNodesWithTag("dashboard_admin_console_button").assertCountEquals(0)
        composeRule.onAllNodesWithTag("floating_admin_return_button").assertCountEquals(0)
    }

    @Test
    fun `normal user attempting direct navigation to admin panel sees restricted gate`() {
        issueAndPersistSession(
            userId = 55,
            username = "regular_user",
            email = "regular@example.com",
            role = AdminRole.USER
        )
        val userVm = AdminViewModel(app)
        userVm.refreshSessionFromStorage()

        composeRule.setContent {
            AdminPanelScreen(
                adminViewModel = userVm,
                onNavigateToUserAppPreview = {},
                onLogout = {}
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Restricted Access. Authorized Super Admin Only.").assertIsDisplayed()
        composeRule.onAllNodesWithTag("open_user_app_button").assertCountEquals(0)
    }

    @Test
    fun `admin logout immediately revokes preview mode and hides floating icon`() {
        issueAndPersistSession(
            userId = 1,
            username = "hj195345",
            email = "hj195345@gmail.com",
            role = AdminRole.SUPER_ADMIN
        )
        val adminVm = AdminViewModel(app)
        adminVm.refreshSessionFromStorage()
        adminVm.selectAdminSection(AdminSection.SDUI_UI)
        assertTrue(adminVm.enterUserAppPreviewMode())
        assertTrue(adminVm.showFloatingAdminReturnButton.value)

        adminVm.logoutAdmin()

        assertNull(adminVm.currentSession.value)
        assertFalse(adminVm.isAuthorizedAdminSession.value)
        assertFalse(adminVm.isAdminPreviewMode.value)
        assertFalse(adminVm.showFloatingAdminReturnButton.value)
        assertEquals(AdminSection.DASHBOARD, adminVm.selectedAdminSection.value)
        assertFalse(adminVm.enterUserAppPreviewMode())
        assertFalse(adminVm.returnToAdminPanel())
    }

    @Test
    fun `expired or tampered admin session cannot use quick switch or return to admin panel`() = runBlocking {
        // 1. Expired admin session
        val expiredSession = SecureSessionManager.issueSignedToken(
            userId = 1,
            username = "hj195344",
            role = AdminRole.SUPER_ADMIN,
            ttlMinutes = -5,
            email = "hj195344@gmail.com",
            displayName = "Super Admin",
            otpVerified = true
        )
        SecureSessionManager.saveSession(context, expiredSession, rememberMe = false)
        assertFalse(SecureSessionManager.isSessionValid(expiredSession))

        val expiredVm = AdminViewModel(app)
        assertFalse(expiredVm.isAuthorizedAdminSession.value)
        assertFalse(expiredVm.enterUserAppPreviewMode())
        assertFalse(expiredVm.returnToAdminPanel())
        assertFalse(expiredVm.showFloatingAdminReturnButton.value)

        // 2. Tampered in-memory role escalation without valid matching HMAC signature
        val validUserSession = SecureSessionManager.issueSignedToken(
            userId = 77,
            username = "spoof_attempt",
            role = AdminRole.USER,
            ttlMinutes = 30,
            email = "spoof@example.com",
            displayName = "Spoof Attempt",
            otpVerified = true
        )
        val tampered = validUserSession.copy(role = AdminRole.SUPER_ADMIN.name)
        assertFalse("Tampered role claim must fail HMAC signature validation", SecureSessionManager.isSessionValid(tampered))
        assertFalse(expiredVm.isSessionStrictlyAuthorizedAdmin(session = tampered))
    }
}
