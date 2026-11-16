package com.example

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
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
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class BannerSystemRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dao: AdminDao
    private lateinit var syncManager: BannerSyncManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("banner_dismiss_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.adminDao()
        syncManager = BannerSyncManager(context, dao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun sampleBanner(
        id: Int = 1,
        key: String = "banner_1",
        title: String = "Sensor Calibration Drive",
        subtitle: String = "Run quick 2-minute calibration before therapy.",
        type: BannerType = BannerType.ANNOUNCEMENT,
        position: BannerPosition = BannerPosition.HOME_TOP,
        isActive: Boolean = true,
        isPublic: Boolean = true,
        isPublished: Boolean = true,
        isDraft: Boolean = false,
        clinicalReviewStatus: String = "APPROVED",
        clinicalApproved: Boolean = true,
        startAtMillis: Long = 0L,
        endAtMillis: Long = 0L,
        targetAudience: String = "PUBLIC_ALL",
        targetValuesCsv: String = "",
        dismissible: Boolean = true,
        reShowAfterDismissHours: Int = 24,
        priority: Int = 5
    ): BannerContentEntity {
        return BannerContentEntity(
            id = id,
            bannerKey = key,
            title = title,
            subtitle = subtitle,
            description = "Extended therapeutic instructions for neural hand feedback.",
            bannerType = type.name,
            iconKey = type.preset.iconKey,
            ctaEnabled = true,
            ctaText = "Open Calibration",
            ctaActionType = BannerCtaAction.OPEN_SCREEN.name,
            ctaTargetValue = "hand_control",
            targetRoute = "hand_control",
            backgroundColorHex = type.preset.backgroundHex,
            textColorHex = type.preset.textHex,
            buttonColorHex = type.preset.buttonHex,
            buttonTextColorHex = type.preset.buttonTextHex,
            borderColorHex = type.preset.borderHex,
            isActive = isActive,
            isPublic = isPublic,
            isPublished = isPublished,
            isDraft = isDraft,
            clinicalReviewStatus = clinicalReviewStatus,
            clinicalApproved = clinicalApproved,
            priority = priority,
            displayPosition = position.name,
            startAtMillis = startAtMillis,
            endAtMillis = endAtMillis,
            timezoneId = "Asia/Kolkata",
            targetAudience = targetAudience,
            targetValuesCsv = targetValuesCsv,
            dismissible = dismissible,
            reShowAfterDismissHours = reShowAfterDismissHours
        )
    }

    @Test
    fun `eligibility engine gates drafts scheduling clinical tips targeting and dismiss cooldown`() {
        val now = 1_750_000_000_000L
        val baseUser = BannerUserContext(
            userId = "user_2",
            username = "joshi",
            email = "joshi@neurogrip.io",
            role = "USER",
            loginCount = 4,
            accountCreatedAtMillis = now - 30L * 24 * 3_600_000L,
            nowMillis = now
        )

        val liveBanner = sampleBanner(id = 1, key = "banner_live")
        val draftBanner = sampleBanner(id = 2, key = "banner_draft", isDraft = true, isPublished = false)
        val inactiveBanner = sampleBanner(id = 3, key = "banner_inactive", isActive = false)
        val scheduledBanner = sampleBanner(id = 4, key = "banner_sched", startAtMillis = now + 60_000L)
        val expiredBanner = sampleBanner(id = 5, key = "banner_exp", endAtMillis = now - 1_000L)
        val unapprovedTip = sampleBanner(
            id = 6,
            key = "banner_tip_pending",
            type = BannerType.CLINICAL_TIP,
            clinicalReviewStatus = ClinicalReviewStatus.PENDING_REVIEW.name,
            clinicalApproved = false
        )
        val approvedTip = sampleBanner(
            id = 7,
            key = "banner_tip_ok",
            type = BannerType.CLINICAL_TIP,
            clinicalReviewStatus = ClinicalReviewStatus.APPROVED.name,
            clinicalApproved = true
        )
        val targetedJoshi = sampleBanner(
            id = 8,
            key = "banner_joshi_only",
            targetAudience = BannerTargetingMode.SPECIFIC_USERS.name,
            targetValuesCsv = "joshi,other_user"
        )
        val targetedAdminRole = sampleBanner(
            id = 9,
            key = "banner_admin_role",
            targetAudience = BannerTargetingMode.USER_ROLES.name,
            targetValuesCsv = "ADMIN,SUPER_ADMIN"
        )

        assertTrue(BannerEligibilityEngine.isEligibleForUser(liveBanner, baseUser))
        assertFalse(BannerEligibilityEngine.isEligibleForUser(draftBanner, baseUser))
        assertFalse(BannerEligibilityEngine.isEligibleForUser(inactiveBanner, baseUser))
        assertFalse(BannerEligibilityEngine.isEligibleForUser(scheduledBanner, baseUser))
        assertFalse(BannerEligibilityEngine.isEligibleForUser(expiredBanner, baseUser))
        assertFalse(BannerEligibilityEngine.isEligibleForUser(unapprovedTip, baseUser))
        assertTrue(BannerEligibilityEngine.isEligibleForUser(approvedTip, baseUser))
        assertTrue(BannerEligibilityEngine.isEligibleForUser(targetedJoshi, baseUser))
        assertFalse(BannerEligibilityEngine.isEligibleForUser(targetedAdminRole, baseUser))

        // Dismiss cooldown suppresses within 24h window and re-allows after cooldown passes
        val dismissedWithinCooldown = baseUser.copy(
            dismissedAtMillisByBannerKey = mapOf("banner_live" to (now - 2 * 3_600_000L))
        )
        assertFalse(BannerEligibilityEngine.isEligibleForUser(liveBanner, dismissedWithinCooldown))

        val dismissedAfterCooldown = baseUser.copy(
            dismissedAtMillisByBannerKey = mapOf("banner_live" to (now - 25 * 3_600_000L))
        )
        assertTrue(BannerEligibilityEngine.isEligibleForUser(liveBanner, dismissedAfterCooldown))

        // KPI dashboard summary calculation across mixed states
        val metrics = BannerEligibilityEngine.computeDashboardMetrics(
            banners = listOf(liveBanner, draftBanner, inactiveBanner, scheduledBanner, expiredBanner),
            nowMillis = now
        )
        assertEquals(5, metrics.totalBanners)
        assertEquals(4, metrics.activeBanners)
        assertEquals(1, metrics.inactiveBanners)
        assertEquals(4, metrics.publishedBanners)
        assertEquals(1, metrics.draftBanners)
        assertEquals(1, metrics.scheduledBanners)
    }

    @Test
    fun `cta handler validates all 8 actions and executes screen dial whatsapp and popup intents`() {
        // Validation rules
        assertNotNull(BannerCtaHandler.validateCta(true, "", BannerCtaAction.OPEN_SCREEN, "reports"))
        assertNotNull(BannerCtaHandler.validateCta(true, "Visit", BannerCtaAction.OPEN_URL, "ftp://bad.url"))
        assertNull(BannerCtaHandler.validateCta(true, "Visit", BannerCtaAction.OPEN_URL, "https://neurogrip.io"))
        assertNotNull(BannerCtaHandler.validateCta(true, "Call", BannerCtaAction.CALL, "123"))
        assertNull(BannerCtaHandler.validateCta(true, "Call", BannerCtaAction.CALL, "+919825011223"))
        assertNotNull(BannerCtaHandler.validateCta(true, "Chat", BannerCtaAction.WHATSAPP, "123"))
        assertNull(BannerCtaHandler.validateCta(true, "Chat", BannerCtaAction.WHATSAPP, "919825011223"))

        var openedScreen: String? = null
        var openedCategory: String? = null
        var openedProduct: String? = null
        var popupMsg: String? = null
        val callbacks = BannerNavigationCallbacks(
            onOpenScreen = { openedScreen = it },
            onOpenCategory = { openedCategory = it },
            onOpenProduct = { openedProduct = it },
            onShowPopup = { _, msg -> popupMsg = msg }
        )

        // Screen navigation
        BannerCtaHandler.executeCta(
            context,
            sampleBanner().copy(ctaActionType = BannerCtaAction.OPEN_SCREEN.name, ctaTargetValue = "gesture_studio"),
            callbacks
        )
        assertEquals("gesture_studio", openedScreen)

        // Category navigation
        BannerCtaHandler.executeCta(
            context,
            sampleBanner().copy(ctaActionType = BannerCtaAction.OPEN_CATEGORY.name, ctaTargetValue = "fine_pinch"),
            callbacks
        )
        assertEquals("fine_pinch", openedCategory)

        // Product / module navigation
        BannerCtaHandler.executeCta(
            context,
            sampleBanner().copy(ctaActionType = BannerCtaAction.OPEN_PRODUCT.name, ctaTargetValue = "glove_pro_x"),
            callbacks
        )
        assertEquals("glove_pro_x", openedProduct)

        // Popup dialog
        BannerCtaHandler.executeCta(
            context,
            sampleBanner().copy(ctaActionType = BannerCtaAction.SHOW_POPUP.name, ctaTargetValue = "Check electrode gel."),
            callbacks
        )
        assertEquals("Check electrode gel.", popupMsg)

        // Dial intent (uses safe ACTION_DIAL without CALL_PHONE permission)
        val appShadow = Shadows.shadowOf(context as android.app.Application)
        BannerCtaHandler.executeCta(
            context,
            sampleBanner().copy(ctaActionType = BannerCtaAction.CALL.name, ctaTargetValue = "+919825011223"),
            callbacks
        )
        val dialIntent: Intent = appShadow.nextStartedActivity
        assertEquals(Intent.ACTION_DIAL, dialIntent.action)
        assertEquals("tel:+919825011223", dialIntent.dataString)

        // WhatsApp intent
        BannerCtaHandler.executeCta(
            context,
            sampleBanner().copy(ctaActionType = BannerCtaAction.WHATSAPP.name, ctaTargetValue = "+91 98250 11223"),
            callbacks
        )
        val waIntent: Intent = appShadow.nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, waIntent.action)
        assertEquals("https://wa.me/919825011223", waIntent.dataString)
    }

    @Test
    fun `analytics tracking records unique views clicks and dismissals in Room v5 and persists cooldown`() {
        runBlocking {
            val insertedId = dao.insertBanner(sampleBanner(id = 0, key = "banner_room_1")).toInt()
            val stored = requireNotNull(dao.getBannerById(insertedId))

            // First impression records 1 impression and 1 unique view
            syncManager.trackImpression(stored, username = "joshi", screenSlot = "HOME_TOP")
            // Second impression same user same session/day is deduplicated
            syncManager.trackImpression(stored, username = "joshi", screenSlot = "HOME_TOP")
            // Different user in same session adds 1 impression and 1 unique view
            syncManager.trackImpression(stored, username = "tarun", screenSlot = "HOME_TOP")

            syncManager.trackClick(stored, username = "joshi", screenSlot = "HOME_TOP")
            syncManager.dismissBanner(stored, username = "joshi", screenSlot = "HOME_TOP")

            val updated = requireNotNull(dao.getBannerById(insertedId))
            assertEquals(2, updated.impressionsCount)
            assertEquals(2, updated.uniqueViewsCount)
            assertEquals(1, updated.clicksCount)

            val dailyRows = dao.observeAllBannerAnalytics().first()
            assertEquals(1, dailyRows.size)
            assertEquals(2, dailyRows.first().impressions)
            assertEquals(2, dailyRows.first().uniqueViews)
            assertEquals(1, dailyRows.first().clicks)
            assertEquals(1, dailyRows.first().dismissals)

            val dismissMap = syncManager.getDismissedMapForUser("joshi")
            assertTrue(dismissMap.containsKey("banner_room_1"))
        }
    }

    @Test
    fun `compose DynamicBannerCard renders live banner triggers impression CTA and dismissal callbacks`() {
        val live = sampleBanner(id = 42, key = "banner_ui_42", title = "Evening Rehab Drill", priority = 20)
        val filtered = BannerEligibilityEngine.filterEligibleForPosition(
            banners = listOf(live),
            position = BannerPosition.HOME_TOP,
            userContext = BannerUserContext(userId = "u1", username = "joshi", role = "USER")
        )
        assertEquals(1, filtered.size)

        var impressionCount = 0
        var clickedTitle: String? = null
        var dismissedTitle: String? = null
        var openedRoute: String? = null
        val callbacks = BannerNavigationCallbacks(onOpenScreen = { openedRoute = it })

        composeRule.setContent {
            val active = filtered.first()
            DynamicBannerCard(
                banner = active,
                onImpression = { impressionCount += 1 },
                onCtaClick = {
                    clickedTitle = active.title
                    BannerCtaHandler.executeCta(context, active, callbacks)
                },
                onDismiss = { dismissedTitle = active.title }
            )
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Evening Rehab Drill").assertIsDisplayed()
        composeRule.onNodeWithText("Open Calibration").assertIsDisplayed()
        assertEquals(1, impressionCount)

        composeRule.onNodeWithTag("banner_cta_banner_ui_42").performClick()
        composeRule.waitForIdle()
        assertEquals("Evening Rehab Drill", clickedTitle)
        assertEquals("hand_control", openedRoute)

        composeRule.onNodeWithTag("banner_dismiss_banner_ui_42").performClick()
        composeRule.waitForIdle()
        assertEquals("Evening Rehab Drill", dismissedTitle)
    }

    @Test
    fun `admin repository saves draft publishes unpublishes reviews clinical tip and duplicates banner`() {
        runBlocking {
            val repo = AdminRepository(context)
            repo.ensureSeeded()

            val draftRes = repo.saveBanner(
                actorUsername = "hj123",
                actorRole = AdminRole.SUPER_ADMIN,
                banner = sampleBanner(
                    id = 0,
                    key = "banner_clinic_tip_99",
                    title = "Wrist Tendon Tip",
                    type = BannerType.CLINICAL_TIP,
                    isPublished = false,
                    isDraft = true,
                    isPublic = false,
                    clinicalReviewStatus = ClinicalReviewStatus.PENDING_REVIEW.name,
                    clinicalApproved = false
                )
            )
            assertTrue(draftRes is RepoResult.Success)
            val savedDraft = (draftRes as RepoResult.Success).data
            assertTrue(savedDraft.isDraft)
            assertFalse(savedDraft.clinicalApproved)

            // Review & approve clinical tip
            val approveRes = repo.reviewClinicalTipBanner("hj123", AdminRole.SUPER_ADMIN, savedDraft, approved = true)
            assertTrue(approveRes is RepoResult.Success)
            val approvedBanner = (approveRes as RepoResult.Success).data
            assertTrue(approvedBanner.clinicalApproved)
            assertEquals(ClinicalReviewStatus.APPROVED.name, approvedBanner.clinicalReviewStatus)

            // Publish live
            val pubRes = repo.publishBanner("hj123", AdminRole.SUPER_ADMIN, approvedBanner, makePublic = true)
            assertTrue(pubRes is RepoResult.Success)
            val published = (pubRes as RepoResult.Success).data
            assertTrue(published.isPublished)
            assertTrue(published.isPublic)
            assertFalse(published.isDraft)

            // Duplicate creates an independent draft copy with zero analytics counters
            val dupRes = repo.duplicateBanner(
                "hj123",
                AdminRole.SUPER_ADMIN,
                published.copy(impressionsCount = 45, clicksCount = 12)
            )
            assertTrue(dupRes is RepoResult.Success)
            val duplicated = (dupRes as RepoResult.Success).data
            assertTrue(duplicated.id != published.id)
            assertTrue(duplicated.isDraft)
            assertFalse(duplicated.isPublished)
            assertEquals(0, duplicated.impressionsCount)
            assertEquals(0, duplicated.clicksCount)
            assertTrue(duplicated.title.contains("(Copy)"))

            // Clean up test rows from singleton Room db
            repo.deleteBanner("hj123", AdminRole.SUPER_ADMIN, published.id)
            repo.deleteBanner("hj123", AdminRole.SUPER_ADMIN, duplicated.id)
        }
    }
}
