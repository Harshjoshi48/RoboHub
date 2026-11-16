package com.example

import android.content.Context
import android.util.Base64
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

// --- DTOs for Python Backend REST API ---

data class LoginRequestDto(
    val username: String,
    val password: String,
    val totpCode: String? = null
)

data class AuthSessionDto(
    val token: String,
    val userId: Int,
    val username: String,
    val name: String,
    val email: String,
    val role: String,
    val expiresAt: Long,
    val permissions: List<String>,
    val otpVerified: Boolean = true,
    val photoUrl: String = "",
    val photoUpdatedAt: Long = 0L
)

data class DashboardStatsDto(
    val totalUsers: Int,
    val activeUsers: Int,
    val suspendedUsers: Int,
    val adminCount: Int,
    val openTickets: Int,
    val deliveredNotifications: Int,
    val monthlyRecurringRevenueUsd: Double,
    val activeBanners: Int,
    val maintenanceMode: Boolean,
    val configVersion: Int
)

data class UserCreateUpdateDto(
    val name: String,
    val email: String,
    val username: String,
    val password: String? = null,
    val mobile: String,
    val role: String,
    val status: String,
    val subscriptionPlan: String,
    val notes: String = ""
)

data class StatusUpdateDto(
    val status: String,
    val reason: String = ""
)

data class RoleUpdateDto(
    val role: String
)

data class UserPermissionsUpdateDto(
    val permissions: List<String>
)

data class GoogleAuthRequestDto(
    val idToken: String,
    val email: String,
    val name: String,
    val googleSub: String = "",
    val emailVerified: Boolean = true
)

data class NotificationDispatchDto(
    val title: String,
    val message: String,
    val targetAudience: String,
    val targetUsername: String = "",
    val category: String = "SYSTEM",
    val isTest: Boolean = false,
    val isScheduled: Boolean = false,
    val scheduledFor: Long = System.currentTimeMillis()
)

data class ApiActionResponse(
    val success: Boolean,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class FirestoreDocumentDto(
    val documentId: String,
    val collectionPath: String,
    val fields: Map<String, Any?>,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class FirestoreCollectionPageDto(
    val collectionPath: String,
    val documents: List<FirestoreDocumentDto>,
    val totalCount: Int,
    val page: Int,
    val pageSize: Int
)

data class FirestoreUpsertRequestDto(
    val documentId: String,
    val fields: Map<String, Any?>,
    val isCreate: Boolean = false
)

data class FirestoreImportRequestDto(
    val collectionPath: String,
    val documents: List<FirestoreUpsertRequestDto>
)

data class SystemConfigRuntimeDto(
    val configKey: String = "runtime_settings",
    val otpTtlSeconds: Int = 60,
    val otpMaxSends: Int = 3,
    val otpWindowSeconds: Int = 600,
    val sessionTtlMinutes: Int = 30,
    val rateLimitPerMinute: Int = 60,
    val requireTwoFactorForAdmins: Boolean = false,
    val idleLockoutMinutes: Int = 15,
    val fcmMasterEnabled: Boolean = true,
    val fcmDefaultTopic: String = "ALL_USERS",
    val highPriorityClinicalAlerts: Boolean = true,
    val preserveDefaultScreenLayout: Boolean = true,
    val maintenanceMode: Boolean = false,
    val maintenanceMessage: String = "NeuroGrip servers are undergoing scheduled maintenance. Please check back shortly.",
    val minSupportedVersionName: String = "1.0.0",
    val recommendedVersionName: String = "1.2.0",
    val forceUpdate: Boolean = false,
    val configVersion: Int = 1,
    val updatedBy: String = "hj123",
    val updatedAt: Long = System.currentTimeMillis()
)

data class EnvVarDiagnosticDto(
    val name: String,
    val classification: String, // SAFE_RUNTIME_SETTING, SERVER_ONLY_SECRET, DEPLOYMENT_INFRASTRUCTURE_SETTING, INITIAL_BOOTSTRAP_SETTING
    val displayValue: String,
    val isSecret: Boolean,
    val editableInSystemConfig: Boolean,
    val requiresRedeployOrRestart: Boolean,
    val managementGuidance: String
)

data class SystemConfigRollbackDto(
    val targetVersion: Int
)

data class SduiPublishRequestDto(
    val payloadJson: String,
    val changeSummary: String = ""
)

data class AdminAccountRotationDto(
    val currentPassword: String,
    val newPassword: String = "",
    val displayName: String = "",
    val email: String = ""
)

data class OtpSendRequestDto(
    val identifier: String,
    val purpose: String = "LOGIN",
    val requireAdmin: Boolean = false
)

data class OtpVerifyRequestDto(
    val identifier: String,
    val otpCode: String,
    val challengeId: String? = null,
    val issueSession: Boolean = false,
    val requireAdmin: Boolean = false
)

data class LoginInitRequestDto(
    val identifier: String,
    val password: String,
    val requireAdmin: Boolean = false
)

/**
 * Represents an active Email OTP challenge.
 * SECURITY POLICY: Never contains or exposes plaintext OTP codes (`devPreviewCode` removed).
 */
data class OtpChallengeState(
    val challengeId: String,
    val email: String,
    val maskedEmail: String,
    val username: String,
    val displayName: String,
    val role: String,
    val purpose: String, // USER_LOGIN, ADMIN_LOGIN, SIGNUP_VERIFY, PASSWORD_RESET
    val requireAdmin: Boolean,
    val ttlSeconds: Int = 60,
    val expiresAtMillis: Long,
    val remainingSendsInWindow: Int = 2,
    val otpMaxSends: Int = 3,
    val otpWindowSeconds: Int = 600,
    val resendAvailableAtMillis: Long = System.currentTimeMillis() + 15_000L,
    val smtpDelivered: Boolean = false,
    val message: String = "OTP sent to your registered email.",
    val isTestOtp: Boolean = false,
    val testIndicator: String = ""
)

data class PasswordResetRequestDto(
    val email: String
)

data class PasswordResetConfirmDto(
    val email: String,
    val otpCode: String,
    val newPassword: String
)

data class EmailDispatchRequestDto(
    val recipientEmail: String,
    val recipientName: String = "",
    val subject: String,
    val bodyText: String,
    val category: String = "POST_LOGIN_SECURITY"
)

interface AdminBackendApi {
    @POST("/api/v1/auth/login")
    suspend fun login(@Body request: LoginRequestDto): Response<AuthSessionDto>

    @POST("/api/v1/auth/google")
    suspend fun authenticateGoogle(@Body request: GoogleAuthRequestDto): Response<Map<String, Any?>>

    @POST("/api/v1/auth/login-init")
    suspend fun initiateEmailOtpLogin(@Body request: LoginInitRequestDto): Response<Map<String, Any?>>

    @POST("/api/v1/auth/verify-otp")
    suspend fun verifyLoginOtp(@Body request: OtpVerifyRequestDto): Response<AuthSessionDto>

    @POST("/api/v1/auth/otp/send")
    suspend fun sendOtp(@Body request: OtpSendRequestDto): Response<Map<String, Any?>>

    @POST("/api/v1/auth/otp/verify")
    suspend fun verifyOtp(@Body request: OtpVerifyRequestDto): Response<ApiActionResponse>

    @POST("/api/v1/auth/password-reset/request")
    suspend fun requestPasswordResetOtp(@Body request: PasswordResetRequestDto): Response<Map<String, Any?>>

    @POST("/api/v1/auth/password-reset/confirm")
    suspend fun confirmPasswordResetWithOtp(@Body request: PasswordResetConfirmDto): Response<ApiActionResponse>

    @POST("/api/v1/email/send")
    suspend fun sendEmail(@Body request: EmailDispatchRequestDto): Response<ApiActionResponse>

    @GET("/api/v1/dashboard/stats")
    suspend fun getDashboardStats(): Response<DashboardStatsDto>

    @GET("/api/v1/users")
    suspend fun getUsers(): Response<List<User>>

    @POST("/api/v1/users")
    suspend fun createUser(@Body dto: UserCreateUpdateDto): Response<ApiActionResponse>

    @PUT("/api/v1/users/{id}/status")
    suspend fun updateUserStatus(
        @Path("id") userId: Int,
        @Body dto: StatusUpdateDto
    ): Response<ApiActionResponse>

    @PUT("/api/v1/users/{id}/role")
    suspend fun updateUserRole(
        @Path("id") userId: Int,
        @Body dto: RoleUpdateDto
    ): Response<ApiActionResponse>

    @PUT("/api/v1/users/{id}/permissions")
    suspend fun updateUserPermissions(
        @Path("id") userId: Int,
        @Body dto: UserPermissionsUpdateDto
    ): Response<ApiActionResponse>

    @DELETE("/api/v1/users/{id}")
    suspend fun deleteUser(@Path("id") userId: Int): Response<ApiActionResponse>

    @POST("/api/v1/notifications/send")
    suspend fun dispatchNotification(@Body dto: NotificationDispatchDto): Response<ApiActionResponse>

    @GET("/api/v1/config")
    suspend fun getRemoteConfig(): Response<RemoteConfigEntity>

    @PUT("/api/v1/config")
    suspend fun publishRemoteConfig(@Body config: RemoteConfigEntity): Response<ApiActionResponse>

    @GET("/api/v1/sdui/config")
    suspend fun getSduiConfig(): Response<Map<String, Any?>>

    @PUT("/api/v1/sdui/config")
    suspend fun publishSduiConfig(@Body request: SduiPublishRequestDto): Response<Map<String, Any?>>

    @POST("/api/v1/sdui/rollback")
    suspend fun rollbackSduiConfig(@Body request: SystemConfigRollbackDto): Response<Map<String, Any?>>

    @GET("/api/v1/sdui/history")
    suspend fun getSduiHistory(): Response<List<Map<String, Any?>>>

    @GET("/api/v1/system-config")
    suspend fun getSystemConfig(): Response<Map<String, Any?>>

    @PUT("/api/v1/system-config")
    suspend fun updateSystemConfig(@Body config: SystemConfigRuntimeDto): Response<ApiActionResponse>

    @POST("/api/v1/system-config/rollback")
    suspend fun rollbackSystemConfig(@Body request: SystemConfigRollbackDto): Response<ApiActionResponse>

    @POST("/api/v1/system-config/admin-account")
    suspend fun rotateAdminCredentials(@Body request: AdminAccountRotationDto): Response<ApiActionResponse>

    @GET("/api/v1/audit-logs")
    suspend fun getAuditLogs(): Response<List<AuditLogEntity>>

    // --- Cloud Firestore Database Management Endpoints ---
    @GET("/api/v1/firestore/{collection}")
    suspend fun listFirestoreCollection(
        @Path("collection") collection: String
    ): Response<FirestoreCollectionPageDto>

    @POST("/api/v1/firestore/{collection}")
    suspend fun upsertFirestoreDocument(
        @Path("collection") collection: String,
        @Body request: FirestoreUpsertRequestDto
    ): Response<ApiActionResponse>

    @DELETE("/api/v1/firestore/{collection}/{docId}")
    suspend fun deleteFirestoreDocument(
        @Path("collection") collection: String,
        @Path("docId") docId: String
    ): Response<ApiActionResponse>

    @POST("/api/v1/firestore/{collection}/import")
    suspend fun importFirestoreCollection(
        @Path("collection") collection: String,
        @Body request: FirestoreImportRequestDto
    ): Response<ApiActionResponse>
}

/**
 * RBAC Permission Matrix enforced both in the Python backend and Android repository layer.
 */
enum class AdminPermission {
    VIEW_DASHBOARD,
    VIEW_USERS,
    EDIT_USERS,
    SUSPEND_USERS,
    DELETE_USERS,
    MANAGE_ROLES,
    SEND_NOTIFICATIONS,
    MANAGE_REMOTE_CONFIG,
    MANAGE_BANNERS,
    VIEW_ANALYTICS,
    MANAGE_MAINTENANCE,
    MANAGE_SUPPORT_TICKETS,
    VIEW_AUDIT_LOGS,
    MANAGE_SECURITY_POLICY,
    MANAGE_SUBSCRIPTIONS,
    USE_AI_TOOLS,
    MANAGE_BACKUPS,
    MANAGE_DATABASE,
    MANAGE_SYSTEM_CONFIG
}

object RolePermissionPolicy {
    fun permissionsFor(role: AdminRole): Set<AdminPermission> {
        return when (role) {
            AdminRole.SUPER_ADMIN -> AdminPermission.entries.toSet()
            AdminRole.ADMIN -> AdminPermission.entries.toSet() - setOf(
                AdminPermission.MANAGE_ROLES,
                AdminPermission.MANAGE_BACKUPS,
                AdminPermission.MANAGE_SECURITY_POLICY,
                AdminPermission.MANAGE_SYSTEM_CONFIG
            )
            AdminRole.DATA_MANAGER -> setOf(
                AdminPermission.VIEW_DASHBOARD,
                AdminPermission.VIEW_USERS,
                AdminPermission.EDIT_USERS,
                AdminPermission.MANAGE_BANNERS,
                AdminPermission.VIEW_ANALYTICS,
                AdminPermission.MANAGE_BACKUPS,
                AdminPermission.MANAGE_DATABASE,
                AdminPermission.USE_AI_TOOLS
            )
            AdminRole.EDITOR -> setOf(
                AdminPermission.VIEW_DASHBOARD,
                AdminPermission.MANAGE_BANNERS,
                AdminPermission.SEND_NOTIFICATIONS,
                AdminPermission.VIEW_ANALYTICS,
                AdminPermission.USE_AI_TOOLS
            )
            AdminRole.SUPPORT_AGENT -> setOf(
                AdminPermission.VIEW_DASHBOARD,
                AdminPermission.VIEW_USERS,
                AdminPermission.EDIT_USERS,
                AdminPermission.SUSPEND_USERS,
                AdminPermission.MANAGE_SUPPORT_TICKETS,
                AdminPermission.USE_AI_TOOLS
            )
            AdminRole.ANALYST -> setOf(
                AdminPermission.VIEW_DASHBOARD,
                AdminPermission.VIEW_USERS,
                AdminPermission.VIEW_ANALYTICS,
                AdminPermission.VIEW_AUDIT_LOGS,
                AdminPermission.USE_AI_TOOLS
            )
            AdminRole.VIEWER -> setOf(
                AdminPermission.VIEW_DASHBOARD,
                AdminPermission.VIEW_USERS,
                AdminPermission.VIEW_ANALYTICS,
                AdminPermission.VIEW_AUDIT_LOGS
            )
            AdminRole.USER, AdminRole.BLOCKED -> emptySet()
        }
    }

    fun parseCustomPermissions(csv: String): Set<AdminPermission> {
        if (csv.isBlank()) return emptySet()
        return csv.split(",")
            .mapNotNull { token ->
                val clean = token.trim()
                AdminPermission.entries.firstOrNull { it.name.equals(clean, ignoreCase = true) }
            }
            .toSet()
    }

    fun effectivePermissions(role: AdminRole, customPermissionsCsv: String = ""): Set<AdminPermission> {
        if (role == AdminRole.BLOCKED) return emptySet()
        return permissionsFor(role) + parseCustomPermissions(customPermissionsCsv)
    }

    fun hasPermission(role: AdminRole, permission: AdminPermission, customPermissionsCsv: String = ""): Boolean {
        return effectivePermissions(role, customPermissionsCsv).contains(permission)
    }
}

/**
 * Handles signed token generation, validation, session expiration (including 30-day Remember Me),
 * and rate limiting. Never stores plain-text passwords.
 */
object SecureSessionManager {
    private const val PREFS_NAME = "neurogrip_secure_admin_session"
    private const val HMAC_SECRET = "NeuroPulse_Admin_HMAC_Key_2026_v2"
    const val REMEMBER_ME_30_DAYS_TTL_MINUTES: Int = 30 * 24 * 60 // 43,200 minutes (30 days)
    private val requestTimestamps = mutableListOf<Long>()

    @Volatile
    var activeRateLimitPerMinute: Int = 60

    @Volatile
    var activeSessionTtlMinutes: Int = 30

    fun issueSignedToken(
        userId: Int,
        username: String,
        role: AdminRole,
        ttlMinutes: Int = activeSessionTtlMinutes,
        email: String = "$username@neurogrip.io",
        displayName: String = username,
        otpVerified: Boolean = true,
        photoUrl: String = "",
        photoUpdatedAt: Long = 0L
    ): AuthSessionDto {
        val expiresAt = System.currentTimeMillis() + ttlMinutes.toLong() * 60_000L
        val payload = "$userId:$username:${role.name}:$expiresAt"
        val signature = hmacSha256(payload)
        val encoded = Base64.encodeToString("$payload:$signature".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return AuthSessionDto(
            token = encoded,
            userId = userId,
            username = username,
            name = displayName,
            email = email,
            role = role.name,
            expiresAt = expiresAt,
            permissions = RolePermissionPolicy.permissionsFor(role).map { it.name },
            otpVerified = otpVerified,
            photoUrl = photoUrl,
            photoUpdatedAt = photoUpdatedAt
        )
    }

    data class VerifiedTokenClaims(
        val userId: Int,
        val username: String,
        val role: AdminRole,
        val expiresAt: Long
    )

    fun extractVerifiedTokenClaims(token: String?): VerifiedTokenClaims? {
        if (token.isNullOrBlank()) return null
        return try {
            val decoded = String(Base64.decode(token, Base64.NO_WRAP), Charsets.UTF_8)
            val parts = decoded.split(":")
            if (parts.size != 5) return null
            val payload = "${parts[0]}:${parts[1]}:${parts[2]}:${parts[3]}"
            val expectedSig = hmacSha256(payload)
            if (expectedSig != parts[4]) return null
            val expiresAt = parts[3].toLongOrNull() ?: return null
            if (System.currentTimeMillis() >= expiresAt) return null
            val userId = parts[0].toIntOrNull() ?: return null
            VerifiedTokenClaims(
                userId = userId,
                username = parts[1],
                role = AdminRole.fromString(parts[2]),
                expiresAt = expiresAt
            )
        } catch (_: Exception) {
            null
        }
    }

    fun verifyToken(token: String?): Boolean {
        return extractVerifiedTokenClaims(token) != null
    }

    fun isSessionValid(session: AuthSessionDto?): Boolean {
        if (session == null) return false
        if (!session.otpVerified) return false
        if (System.currentTimeMillis() >= session.expiresAt) return false
        val claims = extractVerifiedTokenClaims(session.token) ?: return false
        return claims.userId == session.userId &&
            claims.username.equals(session.username, ignoreCase = true) &&
            claims.role == AdminRole.fromString(session.role) &&
            claims.expiresAt == session.expiresAt
    }

    fun isAuthorizedAdminRole(role: AdminRole): Boolean {
        return role != AdminRole.USER &&
            role != AdminRole.BLOCKED &&
            RolePermissionPolicy.hasPermission(role, AdminPermission.VIEW_DASHBOARD)
    }

    fun checkRateLimit(maxPerMinute: Int = activeRateLimitPerMinute): Boolean {
        val now = System.currentTimeMillis()
        synchronized(requestTimestamps) {
            requestTimestamps.removeAll { now - it > 60_000L }
            if (requestTimestamps.size >= maxPerMinute) {
                return false
            }
            requestTimestamps.add(now)
            return true
        }
    }

    fun currentRateWindowCount(): Int {
        val now = System.currentTimeMillis()
        synchronized(requestTimestamps) {
            requestTimestamps.removeAll { now - it > 60_000L }
            return requestTimestamps.size
        }
    }

    fun saveSession(
        context: Context,
        session: AuthSessionDto,
        backendUrl: String = "http://10.0.2.2:8000",
        rememberMe: Boolean = isRememberMeEnabled(context)
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("token", session.token)
            .putInt("user_id", session.userId)
            .putString("username", session.username)
            .putString("name", session.name)
            .putString("email", session.email)
            .putString("role", session.role)
            .putLong("expires_at", session.expiresAt)
            .putString("photo_url", session.photoUrl)
            .putLong("photo_updated_at", session.photoUpdatedAt)
            .putBoolean("remember_me_30d", rememberMe)
            .putString("remembered_identifier", if (rememberMe) session.email.ifBlank { session.username } else "")
            .putString("backend_url", backendUrl)
            .apply()
    }

    fun setRememberMePreference(context: Context, rememberMe: Boolean, identifier: String = "") {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit().putBoolean("remember_me_30d", rememberMe)
        if (rememberMe && identifier.isNotBlank()) {
            editor.putString("remembered_identifier", identifier.trim())
        } else if (!rememberMe) {
            editor.remove("remembered_identifier")
        }
        editor.apply()
    }

    fun isRememberMeEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("remember_me_30d", true)
    }

    fun getRememberedIdentifier(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("remembered_identifier", "") ?: ""
    }

    fun loadSession(context: Context): AuthSessionDto? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString("token", null) ?: return null
        if (!verifyToken(token)) {
            clearSession(context)
            return null
        }
        val role = AdminRole.fromString(prefs.getString("role", AdminRole.USER.name) ?: AdminRole.USER.name)
        return AuthSessionDto(
            token = token,
            userId = prefs.getInt("user_id", 0),
            username = prefs.getString("username", "") ?: "",
            name = prefs.getString("name", "") ?: "",
            email = prefs.getString("email", "") ?: "",
            role = role.name,
            expiresAt = prefs.getLong("expires_at", 0L),
            permissions = RolePermissionPolicy.permissionsFor(role).map { it.name },
            photoUrl = prefs.getString("photo_url", "") ?: "",
            photoUpdatedAt = prefs.getLong("photo_updated_at", 0L)
        )
    }

    fun getBackendUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("backend_url", "http://10.0.2.2:8000") ?: "http://10.0.2.2:8000"
    }

    fun setBackendUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("backend_url", url.trim().removeSuffix("/")).apply()
    }

    fun clearSession(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rememberedId = prefs.getString("remembered_identifier", "") ?: ""
        val rememberFlag = prefs.getBoolean("remember_me_30d", false)
        prefs.edit().clear()
            .putBoolean("remember_me_30d", rememberFlag)
            .putString("remembered_identifier", rememberedId)
            .apply()
        ProfileAvatarCacheHelper.clearAllLegacyGlobalAvatarPrefs(context)
    }

    fun computeHmacSha256(data: String, purpose: String = ""): String =
        hmacSha256(if (purpose.isBlank()) data else "$purpose:$data")

    private fun hmacSha256(data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(HMAC_SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

/**
 * Stores and verifies the Admin Digital Signature (Req 14B) used to sign all generated PDF/CSV reports.
 * Only authorized administrators (SUPER_ADMIN / ADMIN) may upload, replace, or modify the signature.
 */
data class AdminSignatureConfig(
    val signerName: String = "Harsh Joshi (Super Admin)",
    val signerEmail: String = "hj195344@gmail.com",
    val signerTitle: String = "Chief Platform & Clinical Security Administrator",
    val signatureText: String = "Harsh Joshi",
    val signatureImageBase64: String = "",
    val signatureImageUri: String = "",
    val signatureDigestSha256: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val enabled: Boolean = true
) {
    val signatureScript: String get() = signatureText
    val hmacSha256: String get() = signatureDigestSha256
    val signatureHash: String get() = signatureDigestSha256
}

object AdminDigitalSignatureManager {
    private const val PREFS_NAME = "neurogrip_admin_digital_signature"

    fun loadSignature(context: Context): AdminSignatureConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val signerName = prefs.getString("signer_name", "Harsh Joshi (Super Admin)") ?: "Harsh Joshi (Super Admin)"
        val signerEmail = prefs.getString("signer_email", "hj195344@gmail.com") ?: "hj195344@gmail.com"
        val signerTitle = prefs.getString("signer_title", "Chief Platform & Clinical Security Administrator")
            ?: "Chief Platform & Clinical Security Administrator"
        val signatureText = prefs.getString("signature_text", "Harsh Joshi") ?: "Harsh Joshi"
        val imageBase64 = prefs.getString("signature_image_base64", "") ?: ""
        val imageUri = prefs.getString("signature_image_uri", "") ?: ""
        val updatedAt = prefs.getLong("updated_at", 1_750_000_000_000L)
        val storedDigest = prefs.getString("signature_digest", "") ?: ""
        val computedDigest = storedDigest.ifBlank {
            SecureSessionManager.computeHmacSha256("$signerName|$signerEmail|$signatureText|$updatedAt").take(24).uppercase()
        }
        return AdminSignatureConfig(
            signerName = signerName,
            signerEmail = signerEmail,
            signerTitle = signerTitle,
            signatureText = signatureText,
            signatureImageBase64 = imageBase64,
            signatureImageUri = imageUri,
            signatureDigestSha256 = computedDigest,
            updatedAtMillis = updatedAt,
            enabled = true
        )
    }

    fun saveSignature(
        context: Context,
        actorRole: AdminRole = AdminRole.SUPER_ADMIN,
        signerName: String = "Harsh Joshi (Super Admin)",
        signerEmail: String = "hj195344@gmail.com",
        signerTitle: String = "Chief Platform & Clinical Security Administrator",
        signatureText: String = "",
        signatureScript: String = "",
        signatureImageBase64: String = "",
        signatureImageUri: String = "",
        imagePath: String = ""
    ): Result<AdminSignatureConfig> {
        if (actorRole != AdminRole.SUPER_ADMIN && actorRole != AdminRole.ADMIN) {
            return Result.failure(SecurityException("Only authorized administrators can upload or modify the Admin Digital Signature."))
        }
        val cleanName = signerName.trim().ifBlank { "Harsh Joshi (Super Admin)" }
        val cleanEmail = signerEmail.trim().ifBlank { "hj195344@gmail.com" }
        val cleanTitle = signerTitle.trim().ifBlank { "Chief Platform & Clinical Security Administrator" }
        val resolvedText = signatureText.ifBlank { signatureScript }.trim()
        val cleanScript = resolvedText.ifBlank { cleanName.substringBefore("(").trim() }
        val resolvedUri = signatureImageUri.ifBlank { imagePath }
        val now = System.currentTimeMillis()
        val digest = SecureSessionManager.computeHmacSha256(
            "$cleanName|$cleanEmail|$cleanScript|${signatureImageBase64.take(64)}|$resolvedUri|$now"
        ).take(24).uppercase()

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("signer_name", cleanName)
            .putString("signer_email", cleanEmail)
            .putString("signer_title", cleanTitle)
            .putString("signature_text", cleanScript)
            .putString("signature_image_base64", signatureImageBase64)
            .putString("signature_image_uri", resolvedUri)
            .putString("signature_digest", digest)
            .putLong("updated_at", now)
            .apply()

        return Result.success(
            AdminSignatureConfig(
                signerName = cleanName,
                signerEmail = cleanEmail,
                signerTitle = cleanTitle,
                signatureText = cleanScript,
                signatureImageBase64 = signatureImageBase64,
                signatureImageUri = resolvedUri,
                signatureDigestSha256 = digest,
                updatedAtMillis = now,
                enabled = true
            )
        )
    }
}

/**
 * Persists and searches Admin-generated User Activity Reports (Reqs 7 & 8).
 */
data class GeneratedReportRecord(
    val reportId: String = "RPT-0",
    val username: String = "",
    val userDisplayName: String = "",
    val userEmail: String = "",
    val targetUsername: String = username,
    val targetUserFullName: String = userDisplayName.ifBlank { targetUsername },
    val targetFullName: String = targetUserFullName,
    val targetUserEmail: String = userEmail,
    val targetEmail: String = targetUserEmail,
    val periodStartFormatted: String = "",
    val periodEndFormatted: String = "",
    val startDate: String = periodStartFormatted,
    val endDate: String = periodEndFormatted,
    val reportType: String = "PDF",
    val reportFormat: String = reportType,
    val generatedDateDisplay: String = "", // dd/MM/yyyy e.g. 15/11/2026
    val generatedDateIso: String = "",     // yyyy-MM-dd e.g. 2026-11-15
    val generatedAtMillis: Long = System.currentTimeMillis(),
    val adminSignedBy: String = "Harsh Joshi (Super Admin)",
    val generatedByAdmin: String = adminSignedBy,
    val generatedBy: String = generatedByAdmin,
    val filePath: String = "",
    val fileName: String = "",
    val sha256Hex: String = "",
    val adminSignatureDigest: String = "",
    val totalCommands: Int = 0,
    val successRateText: String = "",
    val activitySummary: String = "",
    val summaryText: String = activitySummary,
    val associatedLogs: List<String> = emptyList(),
    val activityLogsSummary: List<String> = associatedLogs,
    val activityLogs: List<String> = activityLogsSummary
) {
    val effectiveUsername: String get() = username.ifBlank { targetUsername }
    val effectiveFullName: String get() = userDisplayName.ifBlank { targetUserFullName.ifBlank { targetFullName.ifBlank { effectiveUsername } } }
    val effectiveEmail: String get() = userEmail.ifBlank { targetUserEmail.ifBlank { targetEmail } }
    val effectiveStartDate: String get() = periodStartFormatted.ifBlank { startDate }
    val effectiveEndDate: String get() = periodEndFormatted.ifBlank { endDate }
    val effectiveSummary: String get() = activitySummary.ifBlank { summaryText }
    val effectiveLogs: List<String> get() = associatedLogs.ifEmpty { activityLogsSummary.ifEmpty { activityLogs } }
}

object GeneratedReportStore {
    private const val PREFS_NAME = "neurogrip_generated_reports_store"
    private const val KEY_REPORTS_JSON = "generated_reports_json"

    fun recordReport(context: Context, record: GeneratedReportRecord) = saveReport(context, record)

    fun saveReport(context: Context, record: GeneratedReportRecord) {
        val normalized = record.copy(
            username = record.effectiveUsername,
            targetUsername = record.effectiveUsername,
            userDisplayName = record.effectiveFullName,
            targetUserFullName = record.effectiveFullName,
            targetFullName = record.effectiveFullName,
            userEmail = record.effectiveEmail,
            targetUserEmail = record.effectiveEmail,
            targetEmail = record.effectiveEmail,
            periodStartFormatted = record.effectiveStartDate,
            startDate = record.effectiveStartDate,
            periodEndFormatted = record.effectiveEndDate,
            endDate = record.effectiveEndDate,
            reportType = record.reportType.ifBlank { record.reportFormat.ifBlank { "PDF" } },
            activitySummary = record.effectiveSummary,
            summaryText = record.effectiveSummary,
            associatedLogs = record.effectiveLogs,
            activityLogsSummary = record.effectiveLogs,
            activityLogs = record.effectiveLogs
        )
        val existing = loadAllReports(context).toMutableList()
        existing.removeAll { it.reportId == normalized.reportId }
        existing.add(0, normalized)
        val trimmed = existing.take(100)
        val arr = org.json.JSONArray()
        trimmed.forEach { item ->
            val obj = org.json.JSONObject().apply {
                put("reportId", item.reportId)
                put("targetUsername", item.effectiveUsername)
                put("targetUserFullName", item.effectiveFullName)
                put("targetUserEmail", item.effectiveEmail)
                put("startDate", item.effectiveStartDate)
                put("endDate", item.effectiveEndDate)
                put("reportType", item.reportType)
                put("generatedDateDisplay", item.generatedDateDisplay)
                put("generatedDateIso", item.generatedDateIso)
                put("generatedAtMillis", item.generatedAtMillis)
                put("generatedByAdmin", item.adminSignedBy.ifBlank { item.generatedByAdmin })
                put("filePath", item.filePath)
                put("fileName", item.fileName)
                put("sha256Hex", item.sha256Hex)
                put("summaryText", item.effectiveSummary)
                put("adminSignatureDigest", item.adminSignatureDigest)
                put("totalCommands", item.totalCommands)
                put("successRateText", item.successRateText)
                val logsArr = org.json.JSONArray()
                item.effectiveLogs.forEach { logsArr.put(it) }
                put("activityLogsSummary", logsArr)
            }
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_REPORTS_JSON, arr.toString())
            .apply()
    }

    fun loadAllReports(context: Context): List<GeneratedReportRecord> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_REPORTS_JSON, null) ?: return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val logsArr = obj.optJSONArray("activityLogsSummary")
                    val logs = buildList {
                        if (logsArr != null) {
                            for (j in 0 until logsArr.length()) {
                                add(logsArr.optString(j))
                            }
                        }
                    }
                    val uname = obj.optString("targetUsername")
                    val fname = obj.optString("targetUserFullName", uname)
                    val uemail = obj.optString("targetUserEmail")
                    val sDate = obj.optString("startDate")
                    val eDate = obj.optString("endDate")
                    val rType = obj.optString("reportType", "PDF")
                    val signedBy = obj.optString("generatedByAdmin", "Harsh Joshi (Super Admin)")
                    val summary = obj.optString("summaryText", "")
                    add(
                        GeneratedReportRecord(
                            reportId = obj.optString("reportId"),
                            username = uname,
                            userDisplayName = fname,
                            userEmail = uemail,
                            targetUsername = uname,
                            targetUserFullName = fname,
                            targetFullName = fname,
                            targetUserEmail = uemail,
                            targetEmail = uemail,
                            periodStartFormatted = sDate,
                            periodEndFormatted = eDate,
                            startDate = sDate,
                            endDate = eDate,
                            reportType = rType,
                            reportFormat = rType,
                            generatedDateDisplay = obj.optString("generatedDateDisplay"),
                            generatedDateIso = obj.optString("generatedDateIso"),
                            generatedAtMillis = obj.optLong("generatedAtMillis", 0L),
                            adminSignedBy = signedBy,
                            generatedByAdmin = signedBy,
                            generatedBy = signedBy,
                            filePath = obj.optString("filePath", ""),
                            fileName = obj.optString("fileName", ""),
                            sha256Hex = obj.optString("sha256Hex", ""),
                            adminSignatureDigest = obj.optString("adminSignatureDigest", ""),
                            totalCommands = obj.optInt("totalCommands", 0),
                            successRateText = obj.optString("successRateText", ""),
                            activitySummary = summary,
                            summaryText = summary,
                            associatedLogs = logs,
                            activityLogsSummary = logs,
                            activityLogs = logs
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun searchReports(
        context: Context,
        userQuery: String,
        dateQuery: String
    ): List<GeneratedReportRecord> {
        val cleanUser = userQuery.trim().lowercase()
        val cleanDate = dateQuery.trim().lowercase()
        if (cleanUser.isBlank() && cleanDate.isBlank()) return emptyList()
        return loadAllReports(context).filter { rec ->
            val matchesUser = cleanUser.isBlank() ||
                rec.effectiveUsername.lowercase().contains(cleanUser) ||
                rec.effectiveFullName.lowercase().contains(cleanUser) ||
                rec.effectiveEmail.lowercase().contains(cleanUser)
            val matchesDate = cleanDate.isBlank() ||
                rec.generatedDateDisplay.lowercase().contains(cleanDate) ||
                rec.generatedDateIso.lowercase().contains(cleanDate) ||
                rec.effectiveStartDate.lowercase().contains(cleanDate) ||
                rec.effectiveEndDate.lowercase().contains(cleanDate)
            matchesUser && matchesDate
        }
    }
}

object AdminNetworkClient {
    @Volatile
    private var currentBaseUrl: String = "http://10.0.2.2:8000/"
    @Volatile
    private var apiInstance: AdminBackendApi? = null

    fun getApi(context: Context): AdminBackendApi {
        val configuredUrl = SecureSessionManager.getBackendUrl(context).let {
            if (it.endsWith("/")) it else "$it/"
        }
        if (apiInstance != null && currentBaseUrl == configuredUrl) {
            return apiInstance!!
        }
        synchronized(this) {
            val authInterceptor = Interceptor { chain ->
                val session = SecureSessionManager.loadSession(context)
                val builder = chain.request().newBuilder()
                    .header("Accept", "application/json")
                if (session != null) {
                    builder.header("Authorization", "Bearer ${session.token}")
                    builder.header("X-Admin-Username", session.username)
                    builder.header("X-Admin-Role", session.role)
                }
                chain.proceed(builder.build())
            }

            val isDebugBuild = runCatching {
                Class.forName("com.example.BuildConfig").getDeclaredField("DEBUG").getBoolean(null)
            }.getOrDefault(false)

            val logging = HttpLoggingInterceptor().apply {
                level = if (isDebugBuild) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(logging)
                .connectTimeout(1500, TimeUnit.MILLISECONDS)
                .readTimeout(2000, TimeUnit.MILLISECONDS)
                .build()

            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()

            val safeUrl = if (configuredUrl.startsWith("http://") || configuredUrl.startsWith("https://")) {
                configuredUrl
            } else {
                "http://10.0.2.2:8000/"
            }

            val retrofit = Retrofit.Builder()
                .baseUrl(safeUrl)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()

            currentBaseUrl = safeUrl
            apiInstance = retrofit.create(AdminBackendApi::class.java)
            return apiInstance!!
        }
    }
}

/**
 * Central error and PII sanitizer ensuring ordinary users never see internal stack traces,
 * file system paths, database schema internals, raw Firebase exception classes, or tokens.
 * Standardizes user-facing error codes: AUTH_401, AUTH_403, DB_403, REQ_400, NET_503, SYS_500.
 */
object SecurityErrorSanitizer {

    private val FILE_PATH_REGEX = Regex("""(?:/data/[^\s,;]+|/storage/[^\s,;]+|/app/[^\s,;]+|[A-Za-z]:\\[^\s,;]+)""")
    private val STACK_TRACE_REGEX = Regex("""\bat\s+[a-zA-Z0-9_.$]+\([^\)]*\)""")
    private val BEARER_TOKEN_REGEX = Regex("""(?i)Bearer\s+[A-Za-z0-9._\-~+/]+=*""")
    private val EXCEPTION_CLASS_REGEX = Regex("""[a-zA-Z0-9_.]*Exception:\s*""")

    fun maskEmail(email: String?): String {
        val clean = email?.trim().orEmpty()
        if (clean.isEmpty() || !clean.contains("@")) return "***"
        val local = clean.substringBefore("@")
        val domain = clean.substringAfter("@")
        val safeLocal = when {
            local.length <= 2 -> "${local.take(1)}***"
            else -> "${local.take(2)}***${local.takeLast(1)}"
        }
        return "$safeLocal@$domain"
    }

    fun maskUid(uid: String?): String {
        val clean = uid?.trim().orEmpty()
        if (clean.length <= 6) return "***"
        return "${clean.take(3)}***${clean.takeLast(3)}"
    }

    fun sanitizeLogDetails(raw: String?): String {
        if (raw.isNullOrBlank()) return "Unspecified error"
        return raw
            .replace(BEARER_TOKEN_REGEX, "Bearer [REDACTED]")
            .replace(FILE_PATH_REGEX, "[REDACTED_PATH]")
            .replace(STACK_TRACE_REGEX, "[REDACTED_TRACE]")
            .lineSequence()
            .firstOrNull()
            ?.take(220)
            ?.trim()
            ?: "Sanitized error"
    }

    fun toUserSafeMessage(
        throwable: Throwable?,
        fallbackAction: String = "Unable to complete request"
    ): String {
        val rawMsg = throwable?.message.orEmpty()
        val className = throwable?.javaClass?.simpleName.orEmpty()
        val combined = "$className $rawMsg".lowercase()

        return when {
            combined.contains("permission_denied") ||
                combined.contains("missing or insufficient permissions") ||
                combined.contains("403") ||
                combined.contains("access denied") ->
                "Access denied: You do not have permission to access this resource. [Code: DB_403]"

            combined.contains("unauthenticated") ||
                combined.contains("401") ||
                combined.contains("invalid credentials") ||
                combined.contains("session expired") ->
                "Authentication required: Please sign in again to continue. [Code: AUTH_401]"

            combined.contains("rate limit") ||
                combined.contains("429") ||
                combined.contains("too many requests") ->
                "Too many attempts detected. Please wait before trying again. [Code: AUTH_429]"

            combined.contains("invalid") ||
                combined.contains("illegalargument") ||
                combined.contains("400") -> {
                val cleaned = rawMsg
                    .replace(EXCEPTION_CLASS_REGEX, "")
                    .replace(FILE_PATH_REGEX, "[path]")
                    .replace(BEARER_TOKEN_REGEX, "[redacted]")
                    .lineSequence()
                    .firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (cleaned.isNotEmpty() && cleaned.length <= 140 && !cleaned.contains("com.google.") && !cleaned.contains("java.")) {
                    "$cleaned [Code: REQ_400]"
                } else {
                    "Invalid input provided. Please check your entries and try again. [Code: REQ_400]"
                }
            }

            combined.contains("timeout") ||
                combined.contains("unable to resolve host") ||
                combined.contains("connectexception") ||
                combined.contains("network") ||
                combined.contains("unavailable") ->
                "Service temporarily unreachable. Please check your connection and try again. [Code: NET_503]"

            else -> "$fallbackAction. Please try again. [Code: SYS_500]"
        }
    }
}

