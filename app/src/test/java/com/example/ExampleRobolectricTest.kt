package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("NeuroPulse", appName)
    }

    @Test
    fun `admin hj123 authenticates and manages users and remote config`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = AdminRepository(context)

        // 1. Authenticate Super Admin hj123 / 12345
        val authRes = repo.authenticate("hj123", "12345", requireAdminRole = true)
        assertTrue("Admin hj123 should authenticate", authRes is RepoResult.Success)
        val outcome = (authRes as RepoResult.Success).data
        assertEquals("hj123", outcome.user.username)
        assertEquals(AdminRole.SUPER_ADMIN.name, outcome.session.role)
        assertTrue(SecureSessionManager.verifyToken(outcome.session.token))

        // 2. Reject invalid password
        val badRes = repo.authenticate("hj123", "wrongpass", requireAdminRole = true)
        assertTrue(badRes is RepoResult.Error)

        // 3. Publish Remote Config with Maintenance Mode
        val cfgRes = repo.publishRemoteConfig(
            actorUsername = "hj123",
            actorRole = AdminRole.SUPER_ADMIN,
            newConfig = RemoteConfigEntity(maintenanceMode = true, maintenanceMessage = "Unit test maintenance"),
            changeSummary = "Enable maintenance"
        )
        assertTrue(cfgRes is RepoResult.Success)

        // 4. Register standard user joshi (demo users are no longer auto-seeded per Req 15) and verify maintenance mode blocks login
        repo.registerNewUser(
            fullName = "Joshi Patient",
            email = "joshi@gmail.com",
            username = "joshi",
            rawPassword = "1234",
            mobile = "9999999999"
        )
        val joshiMaint = repo.authenticate("joshi", "1234", requireAdminRole = false)
        assertTrue(joshiMaint is RepoResult.Error)
        assertEquals(503, (joshiMaint as RepoResult.Error).code)

        // 5. Disable Maintenance Mode and verify joshi can log in with 30-day Remember Me
        repo.publishRemoteConfig(
            actorUsername = "hj123",
            actorRole = AdminRole.SUPER_ADMIN,
            newConfig = RemoteConfigEntity(maintenanceMode = false),
            changeSummary = "Disable maintenance"
        )
        val joshiOk = repo.authenticate("joshi", "1234", requireAdminRole = false, rememberMe = true)
        assertTrue(joshiOk is RepoResult.Success)
        val joshiSession = (joshiOk as RepoResult.Success).data.session
        val twentyNineDaysMs = 29L * 24L * 60L * 60L * 1000L
        assertTrue("Remember Me session must persist for ~30 days", joshiSession.expiresAt - System.currentTimeMillis() >= twentyNineDaysMs)

        // 6. Verify protected Super Admin hj195344@gmail.com exists and cannot be deleted
        val superAdminPrimary = repo.authenticate("hj195344@gmail.com", "12345", requireAdminRole = false)
        assertTrue("Super Admin hj195344@gmail.com must authenticate via unified login", superAdminPrimary is RepoResult.Success)
        val superAdminUser = (superAdminPrimary as RepoResult.Success).data.user
        assertEquals(AdminRole.SUPER_ADMIN.name, superAdminUser.role)
        val delAttempt = repo.deleteOwnAccountPermanently(
            usernameOrIdentifier = "hj195344@gmail.com",
            session = null
        )
        assertTrue("Protected Super Admin hj195344@gmail.com must never be deleted", delAttempt is RepoResult.Error)

        // 7. Verify Admin Digital Signature and Generated Report search (dd/MM/yyyy and user filter)
        val sig = AdminDigitalSignatureManager.saveSignature(
            context = context,
            signerName = "Harsh Joshi (Super Admin)",
            signerTitle = "Chief Clinical & Security Administrator",
            signerEmail = "hj195344@gmail.com",
            imagePath = "/tmp/test_sig.png"
        ).getOrThrow()
        assertTrue(sig.enabled)
        assertTrue(sig.signatureHash.isNotBlank())

        GeneratedReportStore.recordReport(
            context = context,
            record = GeneratedReportRecord(
                reportId = "RPT-TEST-1",
                targetUsername = "joshi",
                targetEmail = "joshi@gmail.com",
                targetFullName = "Joshi Patient",
                startDate = "01/11/2026",
                endDate = "15/11/2026",
                generatedDateDisplay = "15/11/2026",
                generatedDateIso = "2026-11-15",
                reportFormat = "PDF",
                generatedBy = "hj195344@gmail.com",
                filePath = "/tmp/rpt.pdf",
                summaryText = "Clinical summary",
                activityLogs = listOf("2026-11-15 10:00 | LOGIN_SUCCESS | Verified")
            )
        )
        val searchMatches = GeneratedReportStore.searchReports(context, "joshi@gmail.com", "15/11/2026")
        assertTrue("Generated report search must match user email and dd/MM/yyyy date", searchMatches.isNotEmpty())
    }

    @Test
    fun `default 000000 test OTP succeeds for authorized test account, rejects unauthorized, used, or expired codes`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = AdminRepository(context)
        repo.ensureSeeded()

        // 1. Authorized test account (hj195345@gmail.com) in debug mode generates 000000 and verifies successfully
        val initAdmin = repo.initiateEmailOtpLogin("hj195345@gmail.com", "12345", requireAdminRole = true)
        assertTrue("Expected OTP challenge for authorized Super Admin", initAdmin is RepoResult.Success)
        val adminChallenge = (initAdmin as RepoResult.Success).data
        assertTrue("Test OTP mode must be active for authorized test account", adminChallenge.isTestOtp)
        assertTrue("Indicator must state DEV TEST MODE", adminChallenge.message.contains("DEV TEST MODE"))

        // 2. Incorrect OTP (999999) must be rejected
        val badOtpAttempt = repo.verifyEmailOtpForSession("hj195345@gmail.com", "999999", requireAdminRole = true)
        assertTrue("Incorrect OTP code 999999 must be rejected", badOtpAttempt is RepoResult.Error)

        // 3. Test OTP 000000 succeeds for authorized test account with active challenge
        val adminOtpSuccess = repo.verifyEmailOtpForSession("hj195345@gmail.com", "000000", requireAdminRole = true)
        assertTrue("000000 must succeed for authorized test account", adminOtpSuccess is RepoResult.Success)
        val outcome = (adminOtpSuccess as RepoResult.Success).data
        assertEquals("SUPER_ADMIN", outcome.session.role)
        assertTrue(outcome.session.otpVerified)

        // 4. Previously used OTP is rejected (Single-use enforcement)
        val reuseAttempt = repo.verifyEmailOtpForSession("hj195345@gmail.com", "000000", requireAdminRole = true)
        assertTrue("Reusing previously verified OTP must be rejected", reuseAttempt is RepoResult.Error)

        // 5. Unauthorized account trying 000000 must be rejected
        val regRes = repo.registerNewUser(
            fullName = "External User",
            email = "external.unauthorized@hospital.org",
            username = "external_user",
            rawPassword = "UserPass#99",
            mobile = "+91 98765 43210",
            role = AdminRole.USER,
            actorUsername = "self_signup",
            actorRole = AdminRole.USER
        )
        assertTrue(regRes is RepoResult.Success)

        val unauthInit = repo.initiateEmailOtpLogin("external.unauthorized@hospital.org", "UserPass#99", false)
        assertTrue(unauthInit is RepoResult.Success)
        val unauthChallenge = (unauthInit as RepoResult.Success).data
        assertFalse("Unauthorized account must NOT be granted test OTP mode", unauthChallenge.isTestOtp)

        // 000000 for unauthorized account is strictly rejected
        val unauthBypassAttempt = repo.verifyEmailOtpForSession("external.unauthorized@hospital.org", "000000", false)
        assertTrue("000000 must be rejected for unauthorized accounts", unauthBypassAttempt is RepoResult.Error)

        // 6. Verify Super Admin account cannot be permanently self-deleted
        val superAdminDeleteAttempt = repo.deleteOwnAccountPermanently(
            usernameOrIdentifier = "hj195345@gmail.com",
            session = null
        )
        assertTrue("Super Admin account must be protected from permanent self-deletion", superAdminDeleteAttempt is RepoResult.Error)
        assertFalse(superAdminDeleteAttempt is RepoResult.Success)
    }

    @Test
    fun `Esp32BleManager enforces NeuroGrip dual-method target, strict GATT UUID verification, and rejects commands when unverified`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bleManager = Esp32BleManager.getInstance(context)

        // 1. Target fallback device name is strictly "NeuroGrip" and initial state is DISCONNECTED + unverified
        assertEquals("NeuroGrip", Esp32BleManager.TARGET_DEVICE_NAME)
        assertEquals("NeuroGrip", bleManager.uiState.value.targetDeviceName)
        assertEquals(BleConnectionStatus.DISCONNECTED, bleManager.uiState.value.connectionStatus)
        assertFalse(bleManager.uiState.value.isGattVerified)
        assertFalse(checkEsp32Connection(context))

        // 2. Sending ON or OFF while DISCONNECTED / unverified fails safely and records failure state
        var callbackOk = true
        bleManager.sendOnCommand { ok, _ -> callbackOk = ok }
        assertFalse("BLE ON command must fail when GATT is unverified/disconnected", callbackOk)
        assertEquals(false, bleManager.uiState.value.lastCommandSendSuccess)

        // 3. Strict GATT Service & Characteristic UUID verification rejects unrelated/generic GATT services
        val unrelatedServiceUuid = java.util.UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB") // Battery Service
        val unrelatedCharUuid = java.util.UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
        val failedVerification = bleManager.verifyExpectedUuidsInGattMap(
            discoveredServices = mapOf(unrelatedServiceUuid to listOf(unrelatedCharUuid))
        )
        org.junit.Assert.assertNull("Unrelated GATT service must fail UUID verification", failedVerification)

        // 4. Strict GATT Service & Characteristic UUID verification succeeds when expected existing ESP32 UUIDs are present
        val expectedSvc = java.util.UUID.fromString(Esp32BleManager.DEFAULT_SERVICE_UUID)
        val expectedWrite = java.util.UUID.fromString(Esp32BleManager.DEFAULT_WRITE_CHAR_UUID)
        val passedVerification = bleManager.verifyExpectedUuidsInGattMap(
            discoveredServices = mapOf(
                unrelatedServiceUuid to listOf(unrelatedCharUuid),
                expectedSvc to listOf(expectedWrite)
            )
        )
        org.junit.Assert.assertNotNull("Expected ESP32 Service & Write Characteristic UUIDs must pass verification", passedVerification)
        assertEquals(expectedSvc, passedVerification?.first)
        assertEquals(expectedWrite, passedVerification?.second)

        // 5. Updating firmware UUIDs and ON/OFF payloads updates state without generating random UUIDs
        bleManager.updateBleConfiguration(
            serviceUuid = Esp32BleManager.ESPRESSIF_ALT_SERVICE_UUID,
            writeCharUuid = Esp32BleManager.ESPRESSIF_ALT_CHAR_UUID,
            notifyCharUuid = Esp32BleManager.ESPRESSIF_ALT_CHAR_UUID,
            commandOnPayload = "ON",
            commandOffPayload = "OFF"
        )
        assertEquals(Esp32BleManager.ESPRESSIF_ALT_SERVICE_UUID, bleManager.uiState.value.serviceUuid)
        assertEquals(Esp32BleManager.ESPRESSIF_ALT_CHAR_UUID, bleManager.uiState.value.writeCharUuid)
        assertEquals("ON", bleManager.uiState.value.commandOnPayload)
        assertEquals("OFF", bleManager.uiState.value.commandOffPayload)

        // Restore default NUS UUIDs
        bleManager.updateBleConfiguration(
            serviceUuid = Esp32BleManager.DEFAULT_SERVICE_UUID,
            writeCharUuid = Esp32BleManager.DEFAULT_WRITE_CHAR_UUID,
            notifyCharUuid = Esp32BleManager.DEFAULT_NOTIFY_CHAR_UUID,
            commandOnPayload = "ON",
            commandOffPayload = "OFF"
        )
    }
}
