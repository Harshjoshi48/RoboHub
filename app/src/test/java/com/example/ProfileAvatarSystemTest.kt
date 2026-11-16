package com.example

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class ProfileAvatarSystemTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun validSampleWebpBytes(): ByteArray = byteArrayOf(
        'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
        24, 0, 0, 0,
        'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte(),
        'V'.code.toByte(), 'P'.code.toByte(), '8'.code.toByte(), ' '.code.toByte(),
        12, 0, 0, 0,
        0, 0, 0, 0,
        0, 0, 0, 0,
        0, 0, 0, 0
    )

    @Test
    fun `smart avatar resolver enforces exact priority order`() {
        // 1. Uploaded/saved photo URL takes highest priority
        val withPhoto = SmartAvatarResolver.resolve(
            photoUrl = "https://firebasestorage.googleapis.com/v0/b/neurogrip/o/avatar.jpg",
            fullName = "Harsh Joshi",
            email = "harsh@neurogrip.io",
            photoUpdatedAt = 12345L
        )
        assertEquals(AvatarRenderMode.PHOTO, withPhoto.mode)
        assertEquals("H", withPhoto.initialChar)

        // 2. Removed/empty or legacy stock URL falls back to first character of name ("Harsh Joshi" -> "H")
        val withNameOnly = SmartAvatarResolver.resolve(
            photoUrl = "",
            fullName = "Harsh Joshi",
            email = "alpha@neurogrip.io"
        )
        assertEquals(AvatarRenderMode.INITIAL_FROM_NAME, withNameOnly.mode)
        assertEquals("H", withNameOnly.initialChar)

        val ignoringLegacyStock = SmartAvatarResolver.resolve(
            photoUrl = "https://i.pravatar.cc/100?img=11",
            fullName = "Harsh Joshi",
            email = "harsh@neurogrip.io"
        )
        assertEquals(AvatarRenderMode.INITIAL_FROM_NAME, ignoringLegacyStock.mode)
        assertNull(ignoringLegacyStock.photoUrl)
        assertEquals("H", ignoringLegacyStock.initialChar)

        // 3. Unavailable name falls back to first character of email
        val withEmailOnly = SmartAvatarResolver.resolve(
            photoUrl = null,
            fullName = "   ",
            email = "neuro.patient@example.com"
        )
        assertEquals(AvatarRenderMode.INITIAL_FROM_EMAIL, withEmailOnly.mode)
        assertEquals("N", withEmailOnly.initialChar)

        // 4. Both name and email unavailable falls back to default profile icon
        val defaultIcon = SmartAvatarResolver.resolve(
            photoUrl = null,
            fullName = "",
            email = ""
        )
        assertEquals(AvatarRenderMode.DEFAULT_ICON, defaultIcon.mode)
        assertNull(defaultIcon.initialChar)
    }

    @Test
    fun `photo validator accepts JPG JPEG PNG WebP and rejects invalid size or format`() {
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)

        val pngBytes = ByteArrayOutputStream().use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            it.toByteArray()
        }
        val jpgBytes = ByteArrayOutputStream().use {
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, it)
            it.toByteArray()
        }
        val webpBytes = validSampleWebpBytes()

        val pngRes = ProfilePhotoValidator.validateImageBytesAndMetadata(pngBytes, "image/png", "photo.png")
        assertTrue("PNG should validate: ${pngRes.exceptionOrNull()}", pngRes.isSuccess)
        assertEquals("png", pngRes.getOrThrow().extension)

        val jpgRes = ProfilePhotoValidator.validateImageBytesAndMetadata(jpgBytes, "image/jpeg", "photo.jpg")
        assertTrue("JPG should validate: ${jpgRes.exceptionOrNull()}", jpgRes.isSuccess)
        assertEquals("jpg", jpgRes.getOrThrow().extension)

        val jpegExtRes = ProfilePhotoValidator.validateImageBytesAndMetadata(jpgBytes, "image/jpg", "portrait.jpeg")
        assertTrue("JPEG should validate: ${jpegExtRes.exceptionOrNull()}", jpegExtRes.isSuccess)
        assertEquals("jpg", jpegExtRes.getOrThrow().extension)

        val webpRes = ProfilePhotoValidator.validateImageBytesAndMetadata(webpBytes, "image/webp", "avatar.webp")
        assertTrue("WebP should validate: ${webpRes.exceptionOrNull()}", webpRes.isSuccess)
        assertEquals("webp", webpRes.getOrThrow().extension)

        // Reject empty file
        val emptyRes = ProfilePhotoValidator.validateImageBytesAndMetadata(ByteArray(0), "image/png", "empty.png")
        assertTrue(emptyRes.isFailure)

        // Reject >5 MB file
        val oversized = ByteArray(ProfilePhotoValidator.MAX_PROFILE_PHOTO_BYTES.toInt() + 10)
        pngBytes.copyInto(oversized)
        val sizeRes = ProfilePhotoValidator.validateImageBytesAndMetadata(oversized, "image/png", "huge.png")
        assertTrue("Oversized image must fail", sizeRes.isFailure)
        assertTrue(sizeRes.exceptionOrNull()?.message?.contains("5 MB") == true)

        // Reject disallowed MIME or extension
        val gifMimeRes = ProfilePhotoValidator.validateImageBytesAndMetadata(pngBytes, "image/gif", "anim.gif")
        assertTrue(gifMimeRes.isFailure)

        // Reject spoofed file claiming .jpg with plain text bytes
        val spoofedBytes = "NOT_AN_IMAGE_PAYLOAD".toByteArray()
        val spoofRes = ProfilePhotoValidator.validateImageBytesAndMetadata(spoofedBytes, "image/jpeg", "fake.jpg")
        assertTrue("Spoofed file must fail magic-byte validation", spoofRes.isFailure)
    }

    @Test
    fun `smart avatar renders first character of full name when no photo`() {
        composeRule.setContent {
            SmartUserAvatar(
                photoUrl = null,
                fullName = "Harsh Joshi",
                email = "alpha@neurogrip.io",
                testTag = "test_avatar_name"
            )
        }
        composeRule.onNodeWithTag("smart_avatar_initial_text", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("H", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `smart avatar renders first character of email when name is blank`() {
        composeRule.setContent {
            SmartUserAvatar(
                photoUrl = null,
                fullName = "",
                email = "zara.khan@neurogrip.io",
                testTag = "test_avatar_email"
            )
        }
        composeRule.onNodeWithText("Z", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `smart avatar renders default profile icon when both name and email unavailable`() {
        composeRule.setContent {
            SmartUserAvatar(
                photoUrl = null,
                fullName = "",
                email = "",
                testTag = "test_avatar_default"
            )
        }
        composeRule.onNodeWithTag("smart_avatar_default_icon", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `photo editor dialog disables save until staged and shows validation error clearly`() {
        composeRule.setContent {
            ProfilePhotoEditorDialog(
                currentPhotoUrl = null,
                fullName = "Harsh Joshi",
                email = "harsh@neurogrip.io",
                photoUpdatedAt = 0L,
                stagedPreviewUri = null,
                stagedValidatedPhoto = null,
                validationError = "Profile photo exceeds the 5 MB limit.",
                isUploading = false,
                onPickFromGallery = {},
                onSavePhoto = {},
                onRemovePhoto = {},
                onDismiss = {}
            )
        }
        composeRule.onNodeWithTag("profile_photo_validation_error").assertIsDisplayed()
        composeRule.onNodeWithTag("save_profile_photo_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("choose_profile_photo_button").assertIsDisplayed()
        composeRule.onNodeWithTag("remove_profile_photo_button").assertDoesNotExist()
    }

    @Test
    fun `photo editor dialog shows preview before saving and triggers save and remove`() {
        val sampleBmp = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888)
        val samplePng = ByteArrayOutputStream().use {
            sampleBmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            it.toByteArray()
        }
        val validated = ProfilePhotoValidator.validateImageBytesAndMetadata(samplePng, "image/png", "preview.png").getOrThrow()

        var saveClicked = false
        var removeClicked = false

        composeRule.setContent {
            ProfilePhotoEditorDialog(
                currentPhotoUrl = "https://firebasestorage.googleapis.com/v0/b/neurogrip/o/old.jpg",
                fullName = "Harsh Joshi",
                email = "harsh@neurogrip.io",
                photoUpdatedAt = 1000L,
                stagedPreviewUri = Uri.parse("file:///tmp/preview.png"),
                stagedValidatedPhoto = validated,
                validationError = null,
                isUploading = false,
                onPickFromGallery = {},
                onSavePhoto = { saveClicked = true },
                onRemovePhoto = { removeClicked = true },
                onDismiss = {}
            )
        }
        composeRule.onNodeWithTag("profile_photo_preview_avatar").assertIsDisplayed()
        composeRule.onNodeWithTag("save_profile_photo_button").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("remove_profile_photo_button").assertIsDisplayed().performClick()
        assertTrue(saveClicked)
        assertTrue(removeClicked)
    }

    @Test
    fun `room user schema and session manager clear wipe legacy prefs and persist photoUrl`() = runBlocking {
        // Legacy global prefs must be cleared when session clears
        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("profile_image_uri", "content://stale/other_user.jpg")
            .putString("profile_name", "Old User")
            .commit()

        SecureSessionManager.clearSession(context)
        val staleAfterClear = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            .getString("profile_image_uri", null)
        assertNull("Legacy global avatar pref must be wiped on logout", staleAfterClear)

        // Room User persists photoUrl and photoUpdatedAt cleanly
        val userDao = db.userDao()
        val id = userDao.insertUser(
            User(
                username = "harsh_joshi",
                name = "Harsh Joshi",
                email = "harsh@neurogrip.io",
                passwordHash = "hash",
                photoUrl = "https://firebasestorage.googleapis.com/v0/b/neurogrip/o/avatar.webp",
                photoUpdatedAt = 1_750_000_000L
            )
        )
        val loaded = userDao.getUserById(id.toInt())
        assertEquals("https://firebasestorage.googleapis.com/v0/b/neurogrip/o/avatar.webp", loaded?.photoUrl)
        assertEquals(1_750_000_000L, loaded?.photoUpdatedAt)
    }

    @Test
    fun `dashboard top bar renders user initial when no photo is set`() {
        val baseline = SduiSyncState(
            config = SduiDashboardConfig.baselineDefault(),
            activeVersion = 1,
            lastSyncSource = "BASELINE"
        )
        composeRule.setContent {
            DashboardScreen(
                username = "Harsh Joshi",
                onLogout = {},
                sduiStateOverride = baseline
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dashboard_top_bar_avatar", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("H", useUnmergedTree = true).assertExists()
    }
}
