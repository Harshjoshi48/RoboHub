package com.example

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class SduiSystemRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var imageDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SduiRepository.resetSingletonForTest()
        context.getSharedPreferences("sdui_atomic_cache_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        imageDir = File(context.filesDir, "sdui_test_images_${System.nanoTime()}")
        imageDir.mkdirs()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        imageDir.deleteRecursively()
        SduiRepository.resetSingletonForTest()
    }

    private fun buildRemoteConfig(
        version: Int,
        heroTitle: String,
        promoEnabled: Boolean = false,
        promoTitle: String = "",
        heroImageUrl: String = SduiDashboardConfig.baselineDefault().hero.imageUrl
    ): String {
        val base = SduiDashboardConfig.baselineDefault()
        val updated = base.copy(
            configVersion = version,
            updatedBy = "hj123",
            updatedAt = 1_750_000_000_000L + version * 1000L,
            changeSummary = "Remote UI v$version",
            promoBanner = if (promoEnabled) {
                base.promoBanner.copy(
                    enabled = true,
                    title = promoTitle,
                    subtitle = "Server-driven live campaign",
                    ctaText = "Launch",
                    ctaRoute = "hand_control"
                )
            } else {
                base.promoBanner
            },
            hero = base.hero.copy(
                title = heroTitle,
                imageUrl = heroImageUrl
            )
        )
        return SduiJsonCodec.encode(updated)
    }

    @Test
    fun `baseline default config renders exact existing Dashboard UI with promo banner hidden`() {
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

        composeRule.onNodeWithText("NeuroGrip Ready").assertIsDisplayed()
        composeRule.onNodeWithText("Connect ESP32").assertIsDisplayed()
        composeRule.onNodeWithTag("sdui_hero_connect_button").assertIsDisplayed()
        composeRule.onNodeWithText("AI ASSISTANT").assertExists()
        composeRule.onNodeWithText("CONTROL MODES").assertExists()
        composeRule.onNodeWithText("Live Bio-Signals").assertDoesNotExist()
        composeRule.onNodeWithText("Prosthetic Status").assertDoesNotExist()
        composeRule.onNodeWithText("Quick Actions").assertExists()
        composeRule.onNodeWithTag("sdui_promo_banner").assertDoesNotExist()
    }

    @Test
    fun `scenarios 1 through 8 complete online offline reconnect live update and atomic safety lifecycle`() = runBlocking {
        val onlineFlag = AtomicBoolean(true)
        val failNextImageFetch = AtomicBoolean(false)
        val remoteServerJson = arrayOf(
            buildRemoteConfig(
                version = 2,
                heroTitle = "NeuroGrip Live Hub v2",
                promoEnabled = true,
                promoTitle = "Initial Cloud Banner v2"
            )
        )
        val fetchedImageUrls = mutableListOf<String>()

        val mockFetcher = object : SduiAssetFetcher {
            override suspend fun fetchToFile(url: String, destinationFile: File) {
                if (failNextImageFetch.get()) {
                    throw IOException("Simulated network drop mid-download")
                }
                fetchedImageUrls.add(url)
                destinationFile.parentFile?.mkdirs()
                destinationFile.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3, 4))
            }
        }

        // ---------------------------------------------------------------------
        // SCENARIO 1: First-time installation with internet
        // ---------------------------------------------------------------------
        val repoInstall = SduiRepository(
            context = context,
            sduiDao = db.sduiDao(),
            assetFetcher = mockFetcher,
            connectivityChecker = { onlineFlag.get() },
            imageDir = imageDir
        )
        repoInstall.startRealtimeSync(
            firestore = null,
            backendJsonProvider = { Result.success(remoteServerJson[0]) }
        )
        val firstSync = repoInstall.syncLatestFromRemote()
        assertTrue(
            "Expected UPDATED or UP_TO_DATE after initial sync but was ${firstSync.status}",
            firstSync.status == SduiApplyStatus.UPDATED || firstSync.status == SduiApplyStatus.UP_TO_DATE
        )
        assertEquals(2, repoInstall.syncState.value.activeVersion)
        assertEquals("NeuroGrip Live Hub v2", repoInstall.syncState.value.config.hero.title)
        assertTrue(fetchedImageUrls.isNotEmpty())

        // Verify Room cache & local image files are populated
        val cachedInRoom = db.sduiDao().getActiveConfigSnapshot()
        assertNotNull(cachedInRoom)
        assertEquals(2, cachedInRoom?.configVersion)
        val resolvedLocalImg = repoInstall.resolveImageModel(repoInstall.syncState.value.config.hero.imageUrl)
        assertTrue("Expected cached local File model", resolvedLocalImg is File && resolvedLocalImg.exists())
        repoInstall.stopRealtimeSync()

        // ---------------------------------------------------------------------
        // SCENARIO 2: App opening without internet (recreate repo offline)
        // ---------------------------------------------------------------------
        onlineFlag.set(false)
        val repoOfflineBoot = SduiRepository(
            context = context,
            sduiDao = db.sduiDao(),
            assetFetcher = mockFetcher,
            connectivityChecker = { onlineFlag.get() },
            imageDir = imageDir
        )
        // Synchronously warmed from atomic prefs BEFORE any coroutine runs — zero blank/default flash
        assertEquals(2, repoOfflineBoot.syncState.value.activeVersion)
        assertEquals("NeuroGrip Live Hub v2", repoOfflineBoot.syncState.value.config.hero.title)
        assertTrue(repoOfflineBoot.syncState.value.isOfflineFallback)
        assertTrue(repoOfflineBoot.resolveImageModel(repoOfflineBoot.syncState.value.config.hero.imageUrl) is File)

        // Mount Compose UI while offline; v2 renders immediately with zero errors or blanks
        composeRule.setContent {
            val state by repoOfflineBoot.syncState.collectAsState()
            DashboardScreen(
                username = "tarun",
                onLogout = {},
                sduiStateOverride = state,
                imageModelResolverOverride = { repoOfflineBoot.resolveImageModel(it) }
            )
        }
        composeRule.onNodeWithText("NeuroGrip Live Hub v2").assertIsDisplayed()
        composeRule.onNodeWithText("Initial Cloud Banner v2").assertIsDisplayed()

        // ---------------------------------------------------------------------
        // SCENARIO 3: Admin changes UI while user is offline -> app stays on v2 cleanly
        // ---------------------------------------------------------------------
        remoteServerJson[0] = buildRemoteConfig(
            version = 3,
            heroTitle = "NeuroGrip Reconnected Hub v3",
            promoEnabled = true,
            promoTitle = "Reconnected Flash Alert v3"
        )
        repoOfflineBoot.startRealtimeSync(
            firestore = null,
            backendJsonProvider = { Result.success(remoteServerJson[0]) }
        )
        val offlineSyncAttempt = repoOfflineBoot.syncLatestFromRemote()
        assertEquals(2, offlineSyncAttempt.activeVersion)
        assertEquals("NeuroGrip Live Hub v2", repoOfflineBoot.syncState.value.config.hero.title)

        // ---------------------------------------------------------------------
        // SCENARIO 4 & 5: User reconnects -> new UI v3 downloads & applies LIVE without restart
        // ---------------------------------------------------------------------
        onlineFlag.set(true)
        val reconnectOutcome = repoOfflineBoot.onConnectivityChanged(isOnline = true)
        assertEquals(SduiApplyStatus.UPDATED, reconnectOutcome?.status)
        assertEquals(3, repoOfflineBoot.syncState.value.activeVersion)
        assertFalse(repoOfflineBoot.syncState.value.isOfflineFallback)

        // Compose UI updates immediately in-place without recreating Activity/Composable
        composeRule.waitForIdle()
        composeRule.onNodeWithText("NeuroGrip Reconnected Hub v3").assertIsDisplayed()
        composeRule.onNodeWithText("Reconnected Flash Alert v3").assertIsDisplayed()
        repoOfflineBoot.stopRealtimeSync()

        // ---------------------------------------------------------------------
        // SCENARIO 6: App closes and reopens offline -> retains v3
        // ---------------------------------------------------------------------
        onlineFlag.set(false)
        val repoReopenedOffline = SduiRepository(
            context = context,
            sduiDao = db.sduiDao(),
            assetFetcher = mockFetcher,
            connectivityChecker = { onlineFlag.get() },
            imageDir = imageDir
        )
        repoReopenedOffline.hydrateFromRoomCache()
        assertEquals(3, repoReopenedOffline.syncState.value.activeVersion)
        assertEquals("NeuroGrip Reconnected Hub v3", repoReopenedOffline.syncState.value.config.hero.title)
        assertTrue(repoReopenedOffline.syncState.value.isOfflineFallback)

        // ---------------------------------------------------------------------
        // SCENARIO 7: Firebase returns invalid / malicious configuration -> rejected; v3 preserved
        // ---------------------------------------------------------------------
        val invalidPayloads = listOf(
            "{ malformed json !!!",
            """{"schemaVersion":99,"configVersion":4,"sectionsOrder":["hero"]}""",
            """{"schemaVersion":1,"configVersion":4,"sectionsOrder":["hero"],"theme":{"buttonBlueHex":"red"},"hero":{"title":"<script>alert(1)</script>","imageUrl":"http://evil.example/x.png"}}"""
        )
        invalidPayloads.forEach { badJson ->
            val rej = repoReopenedOffline.applyIncomingConfigJson(
                rawJson = badJson,
                source = "FIRESTORE",
                requireAssetDownloadOnNewImage = true
            )
            assertEquals("Expected REJECTED_INVALID for: $badJson", SduiApplyStatus.REJECTED_INVALID, rej.status)
            assertEquals(3, repoReopenedOffline.syncState.value.activeVersion)
            assertEquals("NeuroGrip Reconnected Hub v3", repoReopenedOffline.syncState.value.config.hero.title)
            assertNotNull(repoReopenedOffline.syncState.value.lastValidationError)
        }
        assertEquals(3, db.sduiDao().getActiveConfigSnapshot()?.configVersion)

        // ---------------------------------------------------------------------
        // SCENARIO 8: Internet disconnects mid-sync during image download -> atomic rollback keeps v3
        // ---------------------------------------------------------------------
        failNextImageFetch.set(true)
        val v4WithNewAsset = buildRemoteConfig(
            version = 4,
            heroTitle = "Should Never Appear Partial v4",
            promoEnabled = true,
            promoTitle = "Partial Banner v4",
            heroImageUrl = "https://images.unsplash.com/photo-1589254065878-mid-sync-drop-v4"
        )
        val midDropOutcome = repoReopenedOffline.applyIncomingConfigJson(
            rawJson = v4WithNewAsset,
            source = "FIRESTORE",
            requireAssetDownloadOnNewImage = true
        )
        assertEquals(SduiApplyStatus.ABORTED_NETWORK_MID_SYNC, midDropOutcome.status)
        assertEquals(3, repoReopenedOffline.syncState.value.activeVersion)
        assertEquals("NeuroGrip Reconnected Hub v3", repoReopenedOffline.syncState.value.config.hero.title)
        assertEquals(3, db.sduiDao().getActiveConfigSnapshot()?.configVersion)
    }
}
