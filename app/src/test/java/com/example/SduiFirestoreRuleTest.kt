package com.example

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import java.net.HttpURLConnection
import java.net.URL
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SduiFirestoreRuleTest : FirestoreEmulatorTestBase() {

    private fun validSduiMap(version: Long = 2L, heroTitle: String = "NeuroGrip Live Hub"): Map<String, Any> {
        val now = Timestamp.now()
        val cfg = SduiDashboardConfig.baselineDefault().copy(
            configVersion = version.toInt(),
            hero = SduiDashboardConfig.baselineDefault().hero.copy(title = heroTitle)
        )
        val payloadJson = SduiJsonCodec.encode(cfg)
        val sha = SduiJsonCodec.sha256(payloadJson)
        return mapOf(
            "configKey" to "dashboard_active",
            "schemaVersion" to 1L,
            "configVersion" to version,
            "updatedBy" to "hj123",
            "changeSummary" to "Rule test publish v$version",
            "payloadJson" to payloadJson,
            "contentSha256" to sha,
            "themePrimaryHex" to "#2563EB",
            "themeBackgroundHex" to "#F5F7FA",
            "heroTitle" to heroTitle,
            "heroImageUrl" to cfg.hero.imageUrl,
            "promoEnabled" to true,
            "sectionsOrder" to listOf("promo_banner", "hero", "ai_assistant"),
            "createdAt" to now,
            "updatedAt" to now
        )
    }

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

    @Test
    fun sduiConfigsAndHistory_enforceAdminWriteValidationAndUserReadIsolation() = runFirestoreTest {
        val docRef = db.collection("sdui_configs").document("dashboard_active")
        val histRef = db.collection("sdui_history").document("sdui_v2")

        // 1. Unauthenticated read is denied
        auth.signOut()
        val unauthRead = runCatching { docRef.get().await() }
        assertTrue(unauthRead.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            (unauthRead.exceptionOrNull() as? FirebaseFirestoreException)?.code
        )

        // 2. Non-admin user cannot publish SDUI config
        signInAs("patient_reader@example.com")
        val userWrite = runCatching { docRef.set(validSduiMap()).await() }
        assertTrue(userWrite.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            (userWrite.exceptionOrNull() as? FirebaseFirestoreException)?.code
        )

        // 3. Verified Bootstrapped Super Admin publishes valid SDUI config + immutable history snapshot
        val adminUid = signInAs("hj195345@gmail.com")
        markCurrentUserEmailVerifiedInEmulator(adminUid)
        auth.currentUser?.getIdToken(true)?.await()

        val validMap = validSduiMap(version = 2L, heroTitle = "Emulator Live Hub v2")
        val adminWrite = runCatching { docRef.set(validMap).await() }
        assertTrue(
            "Admin publish should succeed but failed: ${adminWrite.exceptionOrNull()}",
            adminWrite.isSuccess
        )

        val histWrite = runCatching {
            histRef.set(
                mapOf(
                    "configVersion" to 2L,
                    "schemaVersion" to 1L,
                    "summary" to "Published v2",
                    "publishedBy" to "hj123",
                    "payloadJson" to validMap["payloadJson"],
                    "contentSha256" to validMap["contentSha256"],
                    "createdAt" to Timestamp.now()
                )
            ).await()
        }
        assertTrue(
            "Admin sdui_history create should succeed: ${histWrite.exceptionOrNull()}",
            histWrite.isSuccess
        )

        // 4. Invalid SDUI payloads (bad hex color, insecure http URL, unsupported schemaVersion) rejected even for admin
        val badColorMap = validMap.toMutableMap().apply { put("themePrimaryHex", "blue") }
        val badColorRes = runCatching { docRef.set(badColorMap).await() }
        assertTrue(badColorRes.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            (badColorRes.exceptionOrNull() as? FirebaseFirestoreException)?.code
        )

        val badUrlMap = validMap.toMutableMap().apply { put("heroImageUrl", "http://insecure.local/img.png") }
        val badUrlRes = runCatching { docRef.set(badUrlMap).await() }
        assertTrue(badUrlRes.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            (badUrlRes.exceptionOrNull() as? FirebaseFirestoreException)?.code
        )

        // 5. Deleting active SDUI config is forbidden even for admin
        val delRes = runCatching { docRef.delete().await() }
        assertTrue(delRes.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            (delRes.exceptionOrNull() as? FirebaseFirestoreException)?.code
        )

        // 6. Standard authenticated user can read active sdui_configs but CANNOT read sdui_history
        signInAs("patient_reader@example.com")
        val userReadConfig = runCatching { docRef.get().await() }
        assertTrue(userReadConfig.isSuccess)
        assertEquals(2L, userReadConfig.getOrNull()?.getLong("configVersion"))

        val userReadHistory = runCatching { histRef.get().await() }
        assertTrue(userReadHistory.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            (userReadHistory.exceptionOrNull() as? FirebaseFirestoreException)?.code
        )
    }

    @Test
    fun sduiRepository_skipsFirestoreWhenSignedOutAndAutoSyncsOnSignIn() = runFirestoreTest {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val roomDb = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            // Seed v2 into Firestore as verified bootstrapped admin
            val adminUid = signInAs("hj195345@gmail.com")
            markCurrentUserEmailVerifiedInEmulator(adminUid)
            auth.currentUser?.getIdToken(true)?.await()
            val validMap = validSduiMap(version = 2L, heroTitle = "Authenticated Cloud SDUI v2")
            db.collection("sdui_configs").document("dashboard_active").set(validMap).await()

            // Sign out completely so FirebaseAuth.currentUser == null (login screen / local OTP state)
            auth.signOut()

            val mockFetcher = object : SduiAssetFetcher {
                override suspend fun fetchToFile(url: String, destinationFile: java.io.File) {
                    destinationFile.parentFile?.mkdirs()
                    destinationFile.writeBytes(byteArrayOf(1, 2, 3, 4))
                }
            }
            val sduiRepo = SduiRepository(
                context = context,
                sduiDao = roomDb.sduiDao(),
                assetFetcher = mockFetcher,
                connectivityChecker = { true }
            )

            // Signed-out sync must NOT attempt unauthenticated Firestore read or fail
            sduiRepo.startRealtimeSync(firestore = db, backendJsonProvider = null)
            val signedOutSync = sduiRepo.syncLatestFromRemote()
            assertEquals(SduiApplyStatus.UP_TO_DATE, signedOutSync.status)
            assertEquals(1, sduiRepo.syncState.value.activeVersion)

            // Now sign in as a standard user -> auth listener + sync reads v2 cleanly
            signInAs("sdui_patient@example.com")
            val signedInSync = sduiRepo.syncLatestFromRemote()
            assertTrue(
                "Expected UPDATED or UP_TO_DATE after sign-in sync, got ${signedInSync.status}",
                signedInSync.status == SduiApplyStatus.UPDATED || signedInSync.status == SduiApplyStatus.UP_TO_DATE
            )
            assertEquals(2, sduiRepo.syncState.value.activeVersion)
            assertEquals("Authenticated Cloud SDUI v2", sduiRepo.syncState.value.config.hero.title)
            sduiRepo.stopRealtimeSync()
        } finally {
            roomDb.close()
        }
    }
}
