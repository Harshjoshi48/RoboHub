package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BannerRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun markCurrentUserEmailVerifiedInEmulator(uid: String) {
        val client = okhttp3.OkHttpClient()
        val mediaType = "application/json".toMediaType()
        val body = """{"localId":"$uid","emailVerified":true}""".toRequestBody(mediaType)
        val req = okhttp3.Request.Builder()
            .url("http://$AUTH_HOST:$AUTH_PORT/identitytoolkit.googleapis.com/v1/projects/$PROJECT_ID/accounts:update")
            .addHeader("Authorization", "Bearer owner")
            .post(body)
            .build()
        client.newCall(req).execute().use { resp ->
            check(resp.isSuccessful) {
                "Failed to verify email in Auth emulator: HTTP ${resp.code}"
            }
        }
    }

    private fun validBannerMap(
        syncManager: BannerSyncManager,
        bannerKey: String,
        title: String,
        bannerType: BannerType = BannerType.ANNOUNCEMENT,
        isPublic: Boolean = true,
        isPublished: Boolean = true,
        isDraft: Boolean = false,
        clinicalApproved: Boolean = true
    ): MutableMap<String, Any> {
        val entity = BannerContentEntity(
            id = 50,
            bannerKey = bannerKey,
            title = title,
            subtitle = "Emulator integration test banner",
            description = "Detailed instructions",
            bannerType = bannerType.name,
            iconKey = bannerType.preset.iconKey,
            ctaEnabled = true,
            ctaText = "Open Screen",
            ctaActionType = BannerCtaAction.OPEN_SCREEN.name,
            ctaTargetValue = "hand_control",
            targetRoute = "hand_control",
            backgroundColorHex = bannerType.preset.backgroundHex,
            textColorHex = bannerType.preset.textHex,
            buttonColorHex = bannerType.preset.buttonHex,
            buttonTextColorHex = bannerType.preset.buttonTextHex,
            borderColorHex = bannerType.preset.borderHex,
            isActive = true,
            isPublic = isPublic,
            isPublished = isPublished,
            isDraft = isDraft,
            clinicalReviewStatus = if (clinicalApproved) "APPROVED" else "PENDING_REVIEW",
            clinicalApproved = clinicalApproved,
            priority = 10,
            displayPosition = BannerPosition.HOME_TOP.name,
            targetAudience = BannerTargetingMode.PUBLIC_ALL.name
        )
        val map = syncManager.entityToFirestoreMap(entity, bannerKey).toMutableMap()
        val now = Timestamp.now()
        map["createdAt"] = now
        map["updatedAt"] = now
        return map
    }

    @Test
    fun bannersContent_and_analyticsEvents_enforceAdminRbacPublicLiveFilterAndImmutability() = runFirestoreTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val memDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val syncManager = BannerSyncManager(context, memDb.adminDao())

        try {
            val liveRef = db.collection("banners_content").document("banner_live_rule_01")
            val draftRef = db.collection("banners_content").document("banner_draft_rule_02")
            val tipRef = db.collection("banners_content").document("banner_tip_unapproved_03")

            // 1. Unauthenticated caller cannot read or write banners
            auth.signOut()
            val unauthRead = runCatching { liveRef.get().await() }
            assertTrue(unauthRead.isFailure)
            assertEquals(
                FirebaseFirestoreException.Code.PERMISSION_DENIED,
                (unauthRead.exceptionOrNull() as? FirebaseFirestoreException)?.code
            )

            // 2. Bootstrapped Super Admin signs in with verified email and seeds live, draft, and unapproved tip
            val adminUid = signInAs("hj195345@gmail.com")
            markCurrentUserEmailVerifiedInEmulator(adminUid)
            auth.currentUser?.getIdToken(true)?.await()

            val livePayload = validBannerMap(syncManager, "banner_live_rule_01", "Live Public Notice")
            val draftPayload = validBannerMap(
                syncManager,
                "banner_draft_rule_02",
                "Secret Draft",
                isPublic = false,
                isPublished = false,
                isDraft = true
            )
            val tipPayload = validBannerMap(
                syncManager,
                "banner_tip_unapproved_03",
                "Pending Clinical Tip",
                bannerType = BannerType.CLINICAL_TIP,
                clinicalApproved = false
            )

            assertTrue(runCatching { liveRef.set(livePayload).await() }.isSuccess)
            assertTrue(runCatching { draftRef.set(draftPayload).await() }.isSuccess)
            assertTrue(runCatching { tipRef.set(tipPayload).await() }.isSuccess)

            // Malformed HEX color or invalid CTA action rejected even for Admin
            val invalidHexPayload = livePayload.toMutableMap().apply { put("backgroundHex", "not_hex") }
            val badHexRes = runCatching {
                db.collection("banners_content").document("banner_bad_hex").set(invalidHexPayload).await()
            }
            assertTrue(badHexRes.isFailure)
            assertEquals(
                FirebaseFirestoreException.Code.PERMISSION_DENIED,
                (badHexRes.exceptionOrNull() as? FirebaseFirestoreException)?.code
            )

            // 3. Standard non-admin user:
            //    - Cannot write/update/delete banners
            //    - Cannot run unfiltered list or read draft / unapproved tip
            //    - CAN run matching public live query and sees ONLY the approved live banner
            val patientUid = signInAs("patient_banner_reader@example.com")

            val userWrite = runCatching { liveRef.set(livePayload).await() }
            assertTrue(userWrite.isFailure)
            assertEquals(
                FirebaseFirestoreException.Code.PERMISSION_DENIED,
                (userWrite.exceptionOrNull() as? FirebaseFirestoreException)?.code
            )

            val draftRead = runCatching { draftRef.get().await() }
            assertTrue(draftRead.isFailure)
            assertEquals(
                FirebaseFirestoreException.Code.PERMISSION_DENIED,
                (draftRead.exceptionOrNull() as? FirebaseFirestoreException)?.code
            )

            val tipRead = runCatching { tipRef.get().await() }
            assertTrue(tipRead.isFailure)
            assertEquals(
                FirebaseFirestoreException.Code.PERMISSION_DENIED,
                (tipRead.exceptionOrNull() as? FirebaseFirestoreException)?.code
            )

            val unfilteredList = runCatching { db.collection("banners_content").get().await() }
            assertTrue(unfilteredList.isFailure)
            assertEquals(
                FirebaseFirestoreException.Code.PERMISSION_DENIED,
                (unfilteredList.exceptionOrNull() as? FirebaseFirestoreException)?.code
            )

            val matchingQuery = runCatching {
                db.collection("banners_content")
                    .whereEqualTo("isActive", true)
                    .whereEqualTo("isPublic", true)
                    .whereEqualTo("isPublished", true)
                    .whereEqualTo("isDraft", false)
                    .whereEqualTo("clinicalApproved", true)
                    .get()
                    .await()
            }
            assertTrue(
                "Matching public live banner query failed: ${matchingQuery.exceptionOrNull()}",
                matchingQuery.isSuccess
            )
            val docs = matchingQuery.getOrThrow().documents
            assertEquals(1, docs.size)
            assertEquals("banner_live_rule_01", docs.first().id)

            // 4. User analytics event append-only enforcement
            val evtRef = db.collection("banner_analytics_events").document("evt_test_${System.nanoTime()}")
            val validEvent = mapOf(
                "bannerId" to 50L,
                "bannerKey" to "banner_live_rule_01",
                "eventType" to "CLICK",
                "userId" to patientUid,
                "username" to "patient_reader",
                "dateKey" to "2026-11-15",
                "screenSlot" to "HOME_TOP",
                "createdAt" to Timestamp.now()
            )
            assertTrue(runCatching { evtRef.set(validEvent).await() }.isSuccess)

            // Updating or deleting analytics events is forbidden (append-only)
            val updateAttempt = runCatching { evtRef.update("eventType", "IMPRESSION").await() }
            assertTrue(updateAttempt.isFailure)
            val deleteAttempt = runCatching { evtRef.delete().await() }
            assertTrue(deleteAttempt.isFailure)
        } finally {
            memDb.close()
        }
    }
}
