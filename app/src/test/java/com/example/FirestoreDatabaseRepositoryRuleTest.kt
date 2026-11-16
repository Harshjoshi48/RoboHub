package com.example

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirestoreDatabaseRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private lateinit var repository: FirestoreDatabaseRepository

    @Before
    fun setUp() {
        repository = FirestoreDatabaseRepository(db)
    }

    @Test
    fun createAndGetUserProfile_asOwner_succeeds() = runFirestoreTest {
        val uid = signInAs("alice@example.com")

        val createResult = repository.createOwnUserProfile(
            uid = uid,
            username = "alice",
            name = "Alice Clinician",
            email = "alice@example.com",
            mobile = "+91 98765 43210",
            metadata = mapOf("department" to "NeuroRehab", "calibration" to 95L),
            tags = listOf("emg", "verified")
        )
        assertTrue(
            "Expected createOwnUserProfile success but got: ${createResult.exceptionOrNull()}",
            createResult.isSuccess
        )

        val getResult = repository.getUserProfileById(uid)
        assertTrue(getResult.isSuccess)
        val loaded = getResult.getOrNull()
        assertNotNull(loaded)
        assertEquals(uid, loaded?.id)
        assertEquals(uid, loaded?.userId)
        assertEquals("alice", loaded?.username)
        assertEquals("Alice Clinician", loaded?.name)
        assertEquals("USER", loaded?.role)
        assertEquals("ACTIVE", loaded?.status)
    }

    @Test
    fun observeOwnUserProfile_asOwner_returnsMatchingProfile() = runFirestoreTest {
        val uid = signInAs("alice@example.com")
        assertTrue(
            repository.createOwnUserProfile(
                uid = uid,
                username = "alice",
                name = "Alice Clinician",
                email = "alice@example.com"
            ).isSuccess
        )

        val profiles = repository.observeOwnUserProfile(uid).first()
        assertEquals(1, profiles.size)
        assertEquals("alice", profiles.first().username)
    }

    @Test
    fun getUserProfileById_asDifferentNonAdminUser_failsWithPermissionDenied() = runFirestoreTest {
        val ownerUid = signInAs("owner@example.com")
        val createResult = repository.createOwnUserProfile(
            uid = ownerUid,
            username = "owner_user",
            name = "Owner Patient",
            email = "owner@example.com"
        )
        assertTrue(createResult.isSuccess)

        // Switch to another non-admin user
        signInAs("intruder@example.com")

        val readResult = repository.getUserProfileById(ownerUid)
        assertTrue("Expected failure when reading another user's profile", readResult.isFailure)
        val ex = readResult.exceptionOrNull() as? FirebaseFirestoreException
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
    }

    @Test
    fun createOwnUserProfile_whenUnauthenticated_failsWithPermissionDenied() = runFirestoreTest {
        auth.signOut()

        val result = repository.createOwnUserProfile(
            uid = "anon_uid_123",
            username = "anon",
            name = "Anonymous User",
            email = "anon@example.com"
        )
        assertTrue("Expected failure when unauthenticated", result.isFailure)
        val ex = result.exceptionOrNull() as? FirebaseFirestoreException
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
    }

    @Test
    fun adminAndUserEmailOtpFlow_enforcesSingleUseRateLimitAndRoleLock() = runFirestoreTest {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val adminRepo = AdminRepository(context)
        adminRepo.ensureSeeded()

        // 1. Secure password setup/reset for Super Admin hj195345@gmail.com via Email OTP
        val resetChallengeRes = adminRepo.issueOrResendEmailOtp(
            "hj195345@gmail.com",
            "PASSWORD_RESET",
            true
        )
        assertTrue("Expected OTP challenge for hj195345@gmail.com", resetChallengeRes is RepoResult.Success)
        val resetChallenge = (resetChallengeRes as RepoResult.Success).data
        assertEquals("hj195345@gmail.com", resetChallenge.email)
        assertEquals(60, resetChallenge.ttlSeconds)
        val resetOtpCode = adminRepo.getLastDispatchedOtpForTest("hj195345@gmail.com")
        assertNotNull(resetOtpCode)

        // Incorrect OTP ("999999") must be rejected
        if (resetOtpCode != "999999") {
            val wrongOtpAttempt = adminRepo.verifyEmailOtpOnly("hj195345@gmail.com", "999999")
            assertTrue("Incorrect OTP 999999 must be rejected", wrongOtpAttempt is RepoResult.Error)
        }

        val verifyResetRes = adminRepo.verifyEmailOtpOnly("hj195345@gmail.com", resetOtpCode!!)
        assertTrue("Expected reset OTP verification to succeed", verifyResetRes is RepoResult.Success)

        val updatePassRes = adminRepo.resetPasswordAfterOtpVerified("hj195345@gmail.com", "Admin#Secure99")
        assertTrue("Expected password reset to succeed", updatePassRes is RepoResult.Success)

        // 2. Super Admin Login Step 1 (Email + Password) -> Step 2 (6-Digit OTP)
        val adminInitRes = adminRepo.initiateEmailOtpLogin(
            "hj195345@gmail.com",
            "Admin#Secure99",
            true
        )
        assertTrue("Expected Admin login init to succeed", adminInitRes is RepoResult.Success)
        val adminOtp = adminRepo.getLastDispatchedOtpForTest("hj195345@gmail.com")!!

        // Verify Admin OTP -> grants SUPER_ADMIN session
        val adminSessionRes = adminRepo.verifyEmailOtpForSession(
            "hj195345@gmail.com",
            adminOtp,
            true
        )
        assertTrue("Expected Admin OTP verification to grant session", adminSessionRes is RepoResult.Success)
        val adminOutcome = (adminSessionRes as RepoResult.Success).data
        assertTrue(adminOutcome.isAdminPortalEligible)
        assertEquals("SUPER_ADMIN", adminOutcome.session.role)
        assertTrue(adminOutcome.session.otpVerified)

        // Reusing the same OTP must fail (single-use enforcement)
        val reuseRes = adminRepo.verifyEmailOtpForSession(
            "hj195345@gmail.com",
            adminOtp,
            true
        )
        assertTrue("Reusing OTP must be rejected", reuseRes is RepoResult.Error)

        // 3. Self-registration role lock & Normal User Email OTP Login + 3-per-10m Rate Limit
        val regRes = adminRepo.registerNewUser(
            fullName = "Patient One",
            email = "normal.patient@example.com",
            username = "normal_patient",
            rawPassword = "User#Pass88",
            mobile = "+91 98765 00001",
            role = AdminRole.SUPER_ADMIN, // Client attempts privilege escalation
            actorUsername = "self_signup",
            actorRole = AdminRole.SUPER_ADMIN
        )
        assertTrue("Expected user registration to succeed", regRes is RepoResult.Success)
        val createdUser = (regRes as RepoResult.Success).data
        assertEquals("Self-signup must lock role to USER", "USER", createdUser.role)

        // Send 1 of 3 in 10-minute rolling window
        val send1 = adminRepo.initiateEmailOtpLogin("normal.patient@example.com", "User#Pass88", false)
        assertTrue(send1 is RepoResult.Success)
        val firstOtp = adminRepo.getLastDispatchedOtpForTest("normal.patient@example.com")!!

        // Send 2 of 3 (Resend invalidates firstOtp)
        val send2 = adminRepo.issueOrResendEmailOtp("normal.patient@example.com", "LOGIN", false)
        assertTrue(send2 is RepoResult.Success)
        val secondOtp = adminRepo.getLastDispatchedOtpForTest("normal.patient@example.com")!!

        // First OTP must now fail because Send 2 invalidated it
        val oldOtpVerify = adminRepo.verifyEmailOtpForSession("normal.patient@example.com", firstOtp, false)
        assertTrue("Previous OTP must be invalidated when new OTP is issued", oldOtpVerify is RepoResult.Error)

        // Second OTP succeeds for normal user login
        val userSessionRes = adminRepo.verifyEmailOtpForSession("normal.patient@example.com", secondOtp, false)
        assertTrue("Second OTP must succeed for normal user", userSessionRes is RepoResult.Success)
        assertEquals("USER", (userSessionRes as RepoResult.Success).data.session.role)

        // Send 3 of 3 in 10-minute rolling window
        val send3 = adminRepo.issueOrResendEmailOtp("normal.patient@example.com", "LOGIN", false)
        assertTrue(send3 is RepoResult.Success)

        // Send 4 in 10-minute rolling window must be blocked by rate limit (max 3 per 600s)
        val send4 = adminRepo.issueOrResendEmailOtp("normal.patient@example.com", "LOGIN", false)
        assertTrue("4th OTP within 10 minutes must be blocked by rate limit", send4 is RepoResult.Error)

        // 4. Google OAuth Login: links existing email or creates verified profile without duplicate accounts
        val googleLinkRes = adminRepo.authenticateWithGoogle(
            email = "normal.patient@example.com",
            displayName = "Patient One Google",
            googleUid = "google_uid_patient_1",
            idToken = "mock_google_id_token_xyz"
        )
        assertTrue("Expected Google OAuth linking to succeed", googleLinkRes is RepoResult.Success)
        val googleOutcome = (googleLinkRes as RepoResult.Success).data
        assertEquals("normal_patient", googleOutcome.session.username)
        assertEquals("USER", googleOutcome.session.role)

        // 5. RBAC Custom Permissions & Last Super Admin Protection
        val permRes = adminRepo.updateUserCustomPermissions(
            actorUsername = "hj123",
            actorRole = AdminRole.SUPER_ADMIN,
            targetUserId = createdUser.id,
            customPermissions = setOf(AdminPermission.VIEW_USERS, AdminPermission.VIEW_ANALYTICS)
        )
        assertTrue("Expected custom permission grant to succeed", permRes is RepoResult.Success)

        // Blocking or demoting the last active SUPER_ADMIN (hj195345@gmail.com) must be rejected
        val allUsers = AppDatabase.getInstance(context).userDao().getAllUsersSnapshot()
        val superAdminUser = allUsers.first { it.email.equals("hj195345@gmail.com", ignoreCase = true) }
        val blockSuperAdminRes = adminRepo.setUserStatus(
            actorUsername = "hj123",
            actorRole = AdminRole.SUPER_ADMIN,
            targetUserId = superAdminUser.id,
            newStatus = AccountStatus.BLOCKED,
            reason = "Attempted block of last Super Admin"
        )
        assertTrue("Blocking the last active Super Admin must be prevented", blockSuperAdminRes is RepoResult.Error)
    }

    @Test
    fun updateOwnProfilePhoto_enforcesOwnershipAndValidHttpsUrl() = runFirestoreTest {
        val ownerUid = signInAs("harsh.joshi@neurogrip.io")
        val createRes = repository.createOwnUserProfile(
            uid = ownerUid,
            username = "harsh_joshi",
            name = "Harsh Joshi",
            email = "harsh.joshi@neurogrip.io"
        )
        assertTrue(
            "Expected createOwnUserProfile success, got: ${createRes.exceptionOrNull()}",
            createRes.isSuccess
        )

        val validHttpsUrl =
            "https://firebasestorage.googleapis.com/v0/b/neurogrip-test/o/profile_photos%2F$ownerUid%2Favatar_1750000000.jpg?alt=media"
        val updateSuccess = repository.updateOwnProfilePhoto(
            uid = ownerUid,
            photoUrl = validHttpsUrl,
            photoUpdatedAt = 1_750_000_000L
        )
        assertTrue(
            "Owner updateOwnProfilePhoto should succeed, got ${updateSuccess.exceptionOrNull()}",
            updateSuccess.isSuccess
        )

        val loaded = repository.getUserProfileById(ownerUid).getOrNull()
        assertEquals(validHttpsUrl, loaded?.photoUrl)
        assertEquals(1_750_000_000L, loaded?.photoUpdatedAt)

        // Removing photo (clearing photoUrl to "") succeeds for owner
        val removeSuccess = repository.updateOwnProfilePhoto(
            uid = ownerUid,
            photoUrl = "",
            photoUpdatedAt = 1_750_000_500L
        )
        assertTrue(
            "Removing photo should succeed, got ${removeSuccess.exceptionOrNull()}",
            removeSuccess.isSuccess
        )
        val afterRemove = repository.getUserProfileById(ownerUid).getOrNull()
        assertEquals("", afterRemove?.photoUrl)

        // Local file:// URL is rejected by repository & Firestore rules
        val invalidUrlRes = repository.updateOwnProfilePhoto(
            uid = ownerUid,
            photoUrl = "file:///data/local/avatar.png",
            photoUpdatedAt = 1_750_000_900L
        )
        assertTrue("Local file:// URL must fail", invalidUrlRes.isFailure)

        // Another user cannot update owner's profile photo
        signInAs("intruder@neurogrip.io")
        val crossUserRes = repository.updateOwnProfilePhoto(
            uid = ownerUid,
            photoUrl = validHttpsUrl,
            photoUpdatedAt = 1_750_001_000L
        )
        assertTrue("Cross-user photo update must be denied", crossUserRes.isFailure)
        val ex = crossUserRes.exceptionOrNull() as? FirebaseFirestoreException
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
    }
}


