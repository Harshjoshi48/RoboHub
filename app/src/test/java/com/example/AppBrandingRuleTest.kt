package com.example

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppBrandingRuleTest : FirestoreEmulatorTestBase() {

    private fun validBrandingMap(
        appName: String = "PulseNeuro",
        logoUrl: String = "https://firebasestorage.googleapis.com/v0/b/test-bucket/o/app_branding%2Flogo_v2_1700000000.png?alt=media",
        logoVersion: Long = 2L,
        brandingVersion: Long = 2L
    ): Map<String, Any> {
        val now = Timestamp.now()
        return mapOf(
            "configKey" to "branding",
            "appName" to appName,
            "logoUrl" to logoUrl,
            "logoVersion" to logoVersion,
            "brandingVersion" to brandingVersion,
            "updatedBy" to "hj123",
            "metadata" to mapOf("summary" to "Updated branding"),
            "tags" to listOf("branding", "v$brandingVersion"),
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
    fun appBrandingDoc_enforcesSuperAdminOnlyWriteValidationDeleteDenialAndSignedInRead() = runFirestoreTest {
        val docRef = db.collection("app_settings").document("branding")

        // 1. Unauthenticated read & write denied
        auth.signOut()
        val unauthRead = runCatching { docRef.get().await() }
        assertTrue(unauthRead.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            (unauthRead.exceptionOrNull() as? FirebaseFirestoreException)?.code
        )

        // 2. Standard signed-in user cannot write app_settings/branding
        signInAs("patient_branding@example.com")
        val userWrite = runCatching { docRef.set(validBrandingMap()).await() }
        assertTrue(userWrite.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            (userWrite.exceptionOrNull() as? FirebaseFirestoreException)?.code
        )

        // 3. Unverified Super Admin email is rejected until emailVerified == true
        val adminUid = signInAs("hj195345@gmail.com")
        val unverifiedWrite = runCatching { docRef.set(validBrandingMap()).await() }
        assertTrue(unverifiedWrite.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            (unverifiedWrite.exceptionOrNull() as? FirebaseFirestoreException)?.code
        )

        // Verify Super Admin email & refresh ID token
        markCurrentUserEmailVerifiedInEmulator(adminUid)
        auth.currentUser?.getIdToken(true)?.await()

        // 4. Verified Super Admin publishes valid branding config
        val validMap = validBrandingMap(appName = "PulseCare Hand", brandingVersion = 2L)
        val adminWrite = runCatching { docRef.set(validMap).await() }
        assertTrue(
            "Verified Super Admin write should succeed: ${adminWrite.exceptionOrNull()}",
            adminWrite.isSuccess
        )

        // 5. Invalid payloads rejected even for verified Super Admin
        val emptyNameRes = runCatching { docRef.set(validBrandingMap(appName = "")).await() }
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            (emptyNameRes.exceptionOrNull() as? FirebaseFirestoreException)?.code
        )

        val longNameRes = runCatching { docRef.set(validBrandingMap(appName = "A".repeat(31))).await() }
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            (longNameRes.exceptionOrNull() as? FirebaseFirestoreException)?.code
        )

        val httpUrlRes = runCatching {
            docRef.set(validBrandingMap(logoUrl = "http://insecure.example.com/logo.png")).await()
        }
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            (httpUrlRes.exceptionOrNull() as? FirebaseFirestoreException)?.code
        )

        val wrongDocRes = runCatching {
            db.collection("app_settings").document("other_doc").set(validMap).await()
        }
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            (wrongDocRes.exceptionOrNull() as? FirebaseFirestoreException)?.code
        )

        // 6. Deleting app_settings/branding is forbidden even for verified Super Admin
        val delRes = runCatching { docRef.delete().await() }
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            (delRes.exceptionOrNull() as? FirebaseFirestoreException)?.code
        )

        // 7. Standard authenticated user can read live branding doc & fetchAppBranding() returns it
        signInAs("patient_branding@example.com")
        val repo = FirestoreDatabaseRepository(db)
        val fetchedRes = repo.fetchAppBranding()
        assertTrue("Expected fetchAppBranding success: ${fetchedRes.exceptionOrNull()}", fetchedRes.isSuccess)
        val fetched = fetchedRes.getOrNull()
        assertNotNull(fetched)
        assertEquals("PulseCare Hand", fetched?.appName)
        assertEquals(2L, fetched?.brandingVersion)
    }
}
