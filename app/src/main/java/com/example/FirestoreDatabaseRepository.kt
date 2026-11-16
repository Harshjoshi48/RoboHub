package com.example

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import java.util.Date
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

suspend fun requestGoogleIdTokenViaCredentialManager(context: Context): String {
    val serverClientId = context.getString(R.string.default_web_client_id)
    val credentialManager = CredentialManager.create(context)
    return try {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val result = credentialManager.getCredential(request = request, context = context)
        GoogleIdTokenCredential.createFrom(result.credential.data).idToken
    } catch (e: NoCredentialException) {
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId).build()
        val fallbackRequest = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()
        val result = credentialManager.getCredential(request = fallbackRequest, context = context)
        GoogleIdTokenCredential.createFrom(result.credential.data).idToken
    }
}

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String?): String {
    val currentUser = try {
        FirebaseAuth.getInstance().currentUser
    } catch (_: Exception) {
        null
    }

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", SecurityErrorSanitizer.maskEmail(provider.email))
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", SecurityErrorSanitizer.maskUid(currentUser?.uid))
        put("email", SecurityErrorSanitizer.maskEmail(currentUser?.email))
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", SecurityErrorSanitizer.sanitizeLogDetails(exception.message ?: exception.javaClass.simpleName))
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@IgnoreExtraProperties
data class FirestoreUserDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val name: String = "",
    val email: String = "",
    val mobile: String = "+91 98765 43210",
    val role: String = "USER",
    val status: String = "ACTIVE",
    val subscriptionPlan: String = "Free Basic",
    val deviceId: String = "NG-ESP32-001",
    val notes: String = "",
    val photoUrl: String = "",
    val photoUpdatedAt: Long = 0L,
    val twoFactorEnabled: Boolean = false,
    val loginCount: Long = 1L,
    val metadata: Map<String, Any>? = null,
    val tags: List<String>? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreAppBrandingDoc(
    @DocumentId val id: String = "",
    val configKey: String = AppBrandingConfig.BRANDING_DOC_ID,
    val appName: String = AppBrandingConfig.DEFAULT_APP_NAME,
    val logoUrl: String = "",
    val logoVersion: Long = 1L,
    val brandingVersion: Long = 1L,
    val updatedBy: String = "system",
    val metadata: Map<String, Any>? = null,
    val tags: List<String>? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class FirestoreFieldSpec(
    val name: String,
    val type: String, // STRING, INT, DOUBLE, BOOLEAN, MAP, ARRAY, TIMESTAMP
    val required: Boolean = false,
    val editable: Boolean = true,
    val allowedValues: List<String> = emptyList(),
    val defaultHint: String = ""
)

data class FirestoreCollectionMeta(
    val collectionPath: String,
    val displayName: String,
    val description: String,
    val canCreate: Boolean,
    val canUpdate: Boolean,
    val canDelete: Boolean,
    val requiredViewPermission: AdminPermission,
    val requiredWritePermission: AdminPermission?,
    val requiredDeletePermission: AdminPermission?,
    val fields: List<FirestoreFieldSpec>
)

data class FirestoreDocumentRecord(
    val documentId: String,
    val collectionPath: String,
    val data: Map<String, Any?>,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

data class PaginatedDocumentsResult(
    val collectionPath: String,
    val documents: List<FirestoreDocumentRecord>,
    val pageIndex: Int,
    val pageSize: Int,
    val totalMatched: Int,
    val hasNextPage: Boolean,
    val hasPrevPage: Boolean,
    val source: String // "CLOUD_FIRESTORE" or "SYNCED_REPLICA"
)

object FirestoreCollectionAllowlist {
    val PROTECTED_SYSTEM_COLLECTIONS = setOf(
        "admins",
        "otp_challenges",
        "system_credentials",
        "service_accounts",
        "firebase_private_keys",
        "auth_tokens"
    )

    val ALLOWLISTED_COLLECTIONS: List<FirestoreCollectionMeta> = listOf(
        FirestoreCollectionMeta(
            collectionPath = "users",
            displayName = "Users (users)",
            description = "Application users & administrative staff profiles with RBAC roles and status.",
            canCreate = true,
            canUpdate = true,
            canDelete = true,
            requiredViewPermission = AdminPermission.VIEW_USERS,
            requiredWritePermission = AdminPermission.EDIT_USERS,
            requiredDeletePermission = AdminPermission.DELETE_USERS,
            fields = listOf(
                FirestoreFieldSpec("userId", "STRING", required = true, editable = false, defaultHint = "user_uid_1"),
                FirestoreFieldSpec("username", "STRING", required = true, editable = true, defaultHint = "patient_01"),
                FirestoreFieldSpec("name", "STRING", required = true, editable = true, defaultHint = "Dr. Meera Nair"),
                FirestoreFieldSpec("email", "STRING", required = true, editable = true, defaultHint = "meera@neurogrip.io"),
                FirestoreFieldSpec("mobile", "STRING", required = false, editable = true, defaultHint = "+91 98765 43210"),
                FirestoreFieldSpec("role", "STRING", required = true, editable = true, allowedValues = listOf("USER", "ANALYST", "SUPPORT_AGENT", "EDITOR", "ADMIN", "SUPER_ADMIN"), defaultHint = "USER"),
                FirestoreFieldSpec("status", "STRING", required = true, editable = true, allowedValues = listOf("ACTIVE", "SUSPENDED", "DELETED"), defaultHint = "ACTIVE"),
                FirestoreFieldSpec("subscriptionPlan", "STRING", required = false, editable = true, defaultHint = "Pro Rehab"),
                FirestoreFieldSpec("deviceId", "STRING", required = false, editable = true, defaultHint = "NG-ESP32-001"),
                FirestoreFieldSpec("notes", "STRING", required = false, editable = true, defaultHint = "Clinical notes"),
                FirestoreFieldSpec("photoUrl", "STRING", required = false, editable = true, defaultHint = "https://firebasestorage.googleapis.com/..."),
                FirestoreFieldSpec("photoUpdatedAt", "INT", required = false, editable = true, defaultHint = "0"),
                FirestoreFieldSpec("twoFactorEnabled", "BOOLEAN", required = false, editable = true, defaultHint = "false"),
                FirestoreFieldSpec("loginCount", "INT", required = false, editable = true, defaultHint = "1"),
                FirestoreFieldSpec("metadata", "MAP", required = false, editable = true, defaultHint = """{"clinic":"NeuroCenter","calibrated":true}"""),
                FirestoreFieldSpec("tags", "ARRAY", required = false, editable = true, defaultHint = """["emg-verified","rehab"]""")
            )
        ),
        FirestoreCollectionMeta(
            collectionPath = "system_config",
            displayName = "System Config (system_config)",
            description = "Super Admin dynamic runtime settings (OTP TTL/Max Sends/Window, Session TTL, Rate Limits, FCM, and Maintenance Gate).",
            canCreate = true,
            canUpdate = true,
            canDelete = false,
            requiredViewPermission = AdminPermission.VIEW_DASHBOARD,
            requiredWritePermission = AdminPermission.MANAGE_SYSTEM_CONFIG,
            requiredDeletePermission = null,
            fields = listOf(
                FirestoreFieldSpec("configKey", "STRING", required = true, editable = false, defaultHint = "runtime_settings"),
                FirestoreFieldSpec("otpTtlSeconds", "INT", required = true, editable = true, defaultHint = "60"),
                FirestoreFieldSpec("otpMaxSends", "INT", required = true, editable = true, defaultHint = "3"),
                FirestoreFieldSpec("otpWindowSeconds", "INT", required = true, editable = true, defaultHint = "600"),
                FirestoreFieldSpec("sessionTtlMinutes", "INT", required = true, editable = true, defaultHint = "30"),
                FirestoreFieldSpec("rateLimitPerMinute", "INT", required = true, editable = true, defaultHint = "60"),
                FirestoreFieldSpec("requireTwoFactorForAdmins", "BOOLEAN", required = false, editable = true, defaultHint = "false"),
                FirestoreFieldSpec("idleLockoutMinutes", "INT", required = false, editable = true, defaultHint = "15"),
                FirestoreFieldSpec("fcmMasterEnabled", "BOOLEAN", required = false, editable = true, defaultHint = "true"),
                FirestoreFieldSpec("fcmDefaultTopic", "STRING", required = false, editable = true, allowedValues = listOf("ALL_USERS", "PRO_USERS", "ADMINS"), defaultHint = "ALL_USERS"),
                FirestoreFieldSpec("highPriorityClinicalAlerts", "BOOLEAN", required = false, editable = true, defaultHint = "true"),
                FirestoreFieldSpec("preserveDefaultScreenLayout", "BOOLEAN", required = false, editable = true, defaultHint = "true"),
                FirestoreFieldSpec("maintenanceMode", "BOOLEAN", required = false, editable = true, defaultHint = "false"),
                FirestoreFieldSpec("maintenanceMessage", "STRING", required = false, editable = true, defaultHint = "Scheduled maintenance in progress."),
                FirestoreFieldSpec("minSupportedVersionName", "STRING", required = false, editable = true, defaultHint = "1.0.0"),
                FirestoreFieldSpec("recommendedVersionName", "STRING", required = false, editable = true, defaultHint = "1.2.0"),
                FirestoreFieldSpec("forceUpdate", "BOOLEAN", required = false, editable = true, defaultHint = "false"),
                FirestoreFieldSpec("configVersion", "INT", required = true, editable = true, defaultHint = "1"),
                FirestoreFieldSpec("updatedBy", "STRING", required = true, editable = false, defaultHint = "hj123"),
                FirestoreFieldSpec("metadata", "MAP", required = false, editable = true, defaultHint = """{"multiWorkerConsistent":true}"""),
                FirestoreFieldSpec("tags", "ARRAY", required = false, editable = true, defaultHint = """["system_config","super_admin"]""")
            )
        ),
        FirestoreCollectionMeta(
            collectionPath = "remote_config",
            displayName = "Remote Config (remote_config)",
            description = "Live feature flags, ESP32 defaults, maintenance gates, and minimum version policies.",
            canCreate = true,
            canUpdate = true,
            canDelete = false,
            requiredViewPermission = AdminPermission.VIEW_DASHBOARD,
            requiredWritePermission = AdminPermission.MANAGE_REMOTE_CONFIG,
            requiredDeletePermission = null,
            fields = listOf(
                FirestoreFieldSpec("configKey", "STRING", required = true, editable = false, defaultHint = "current"),
                FirestoreFieldSpec("maintenanceMode", "BOOLEAN", required = true, editable = true, defaultHint = "false"),
                FirestoreFieldSpec("maintenanceMessage", "STRING", required = true, editable = true, defaultHint = "Scheduled maintenance in progress."),
                FirestoreFieldSpec("minSupportedVersionCode", "INT", required = true, editable = true, defaultHint = "1"),
                FirestoreFieldSpec("minSupportedVersionName", "STRING", required = true, editable = true, defaultHint = "1.0.0"),
                FirestoreFieldSpec("recommendedVersionName", "STRING", required = true, editable = true, defaultHint = "1.2.0"),
                FirestoreFieldSpec("forceUpdate", "BOOLEAN", required = false, editable = true, defaultHint = "false"),
                FirestoreFieldSpec("featureAiAssistant", "BOOLEAN", required = false, editable = true, defaultHint = "true"),
                FirestoreFieldSpec("featureHandControl", "BOOLEAN", required = false, editable = true, defaultHint = "true"),
                FirestoreFieldSpec("featureGestureStudio", "BOOLEAN", required = false, editable = true, defaultHint = "true"),
                FirestoreFieldSpec("featureTelemetryStream", "BOOLEAN", required = false, editable = true, defaultHint = "true"),
                FirestoreFieldSpec("featureOtaUpdates", "BOOLEAN", required = false, editable = true, defaultHint = "true"),
                FirestoreFieldSpec("primaryThemeHex", "STRING", required = false, editable = true, defaultHint = "#2F80ED"),
                FirestoreFieldSpec("maxGripForceLimitN", "DOUBLE", required = false, editable = true, defaultHint = "15.0"),
                FirestoreFieldSpec("defaultEsp32Ip", "STRING", required = false, editable = true, defaultHint = "192.168.4.1"),
                FirestoreFieldSpec("configVersion", "INT", required = true, editable = true, defaultHint = "1"),
                FirestoreFieldSpec("updatedBy", "STRING", required = true, editable = true, defaultHint = "hj123"),
                FirestoreFieldSpec("metadata", "MAP", required = false, editable = true, defaultHint = """{"environment":"production"}"""),
                FirestoreFieldSpec("tags", "ARRAY", required = false, editable = true, defaultHint = """["live","config"]""")
            )
        ),
        FirestoreCollectionMeta(
            collectionPath = "notifications",
            displayName = "Push Broadcasts (notifications)",
            description = "FCM push notification dispatches, audience targeting, and delivery status records.",
            canCreate = true,
            canUpdate = true,
            canDelete = true,
            requiredViewPermission = AdminPermission.VIEW_DASHBOARD,
            requiredWritePermission = AdminPermission.SEND_NOTIFICATIONS,
            requiredDeletePermission = AdminPermission.SEND_NOTIFICATIONS,
            fields = listOf(
                FirestoreFieldSpec("title", "STRING", required = true, editable = true, defaultHint = "Firmware v2.4 Update"),
                FirestoreFieldSpec("message", "STRING", required = true, editable = true, defaultHint = "Improved EMG latency is now available."),
                FirestoreFieldSpec("targetAudience", "STRING", required = true, editable = true, allowedValues = listOf("ALL_USERS", "ADMINS", "PRO_USERS", "SPECIFIC_USER"), defaultHint = "ALL_USERS"),
                FirestoreFieldSpec("targetUsername", "STRING", required = false, editable = true, defaultHint = ""),
                FirestoreFieldSpec("category", "STRING", required = false, editable = true, defaultHint = "SYSTEM"),
                FirestoreFieldSpec("deliveryStatus", "STRING", required = false, editable = true, allowedValues = listOf("DELIVERED", "SCHEDULED", "TEST_SENT", "FAILED"), defaultHint = "DELIVERED"),
                FirestoreFieldSpec("sentBy", "STRING", required = true, editable = false, defaultHint = "hj123"),
                FirestoreFieldSpec("recipientCount", "INT", required = false, editable = true, defaultHint = "1"),
                FirestoreFieldSpec("isScheduled", "BOOLEAN", required = false, editable = true, defaultHint = "false"),
                FirestoreFieldSpec("metadata", "MAP", required = false, editable = true, defaultHint = """{"fcmTopic":"all_users"}"""),
                FirestoreFieldSpec("tags", "ARRAY", required = false, editable = true, defaultHint = """["fcm","broadcast"]""")
            )
        ),
        FirestoreCollectionMeta(
            collectionPath = "banners_content",
            displayName = "CMS Banners (banners_content)",
            description = "In-app announcements, clinical rehabilitation tips, promotions, CTAs, and scheduled banners.",
            canCreate = true,
            canUpdate = true,
            canDelete = true,
            requiredViewPermission = AdminPermission.VIEW_DASHBOARD,
            requiredWritePermission = AdminPermission.MANAGE_BANNERS,
            requiredDeletePermission = AdminPermission.MANAGE_BANNERS,
            fields = listOf(
                FirestoreFieldSpec("title", "STRING", required = true, editable = true, defaultHint = "AI Gesture Studio 2.0"),
                FirestoreFieldSpec("subtitle", "STRING", required = true, editable = true, defaultHint = "Train custom finger grip patterns with 96% neural accuracy."),
                FirestoreFieldSpec("description", "STRING", required = false, editable = true, defaultHint = "Detailed clinical instructions or promotion details."),
                FirestoreFieldSpec("bannerType", "STRING", required = false, editable = true, allowedValues = listOf("ANNOUNCEMENT", "CLINICAL_TIP", "PROMOTION", "WARNING"), defaultHint = "ANNOUNCEMENT"),
                FirestoreFieldSpec("iconKey", "STRING", required = false, editable = true, defaultHint = "campaign"),
                FirestoreFieldSpec("imageUrl", "STRING", required = false, editable = true, defaultHint = ""),
                FirestoreFieldSpec("ctaEnabled", "BOOLEAN", required = false, editable = true, defaultHint = "true"),
                FirestoreFieldSpec("ctaText", "STRING", required = false, editable = true, defaultHint = "Open Studio"),
                FirestoreFieldSpec("ctaActionType", "STRING", required = false, editable = true, allowedValues = listOf("NONE", "OPEN_URL", "OPEN_SCREEN", "OPEN_CATEGORY", "OPEN_PRODUCT", "SHOW_POPUP", "CALL", "WHATSAPP"), defaultHint = "OPEN_SCREEN"),
                FirestoreFieldSpec("ctaTargetValue", "STRING", required = false, editable = true, defaultHint = "gesture_studio"),
                FirestoreFieldSpec("deepLinkRoute", "STRING", required = false, editable = true, defaultHint = "gesture_studio"),
                FirestoreFieldSpec("backgroundHex", "STRING", required = false, editable = true, defaultHint = "#EFF6FF"),
                FirestoreFieldSpec("textHex", "STRING", required = false, editable = true, defaultHint = "#1E3A8A"),
                FirestoreFieldSpec("buttonHex", "STRING", required = false, editable = true, defaultHint = "#1D4ED8"),
                FirestoreFieldSpec("buttonTextHex", "STRING", required = false, editable = true, defaultHint = "#FFFFFF"),
                FirestoreFieldSpec("borderHex", "STRING", required = false, editable = true, defaultHint = "#93C5FD"),
                FirestoreFieldSpec("priority", "INT", required = false, editable = true, defaultHint = "1"),
                FirestoreFieldSpec("displayPosition", "STRING", required = false, editable = true, allowedValues = listOf("HOME_TOP", "HOME_SLIDER", "HOME_MIDDLE", "HOME_BOTTOM", "TOP_STICKY", "BOTTOM_STICKY", "FLOATING", "POPUP", "CATEGORY", "PRODUCT_DETAILS"), defaultHint = "HOME_TOP"),
                FirestoreFieldSpec("isActive", "BOOLEAN", required = true, editable = true, defaultHint = "true"),
                FirestoreFieldSpec("isPublic", "BOOLEAN", required = false, editable = true, defaultHint = "true"),
                FirestoreFieldSpec("isPublished", "BOOLEAN", required = false, editable = true, defaultHint = "true"),
                FirestoreFieldSpec("isDraft", "BOOLEAN", required = false, editable = true, defaultHint = "false"),
                FirestoreFieldSpec("clinicalReviewStatus", "STRING", required = false, editable = true, allowedValues = listOf("NOT_REQUIRED", "PENDING_REVIEW", "APPROVED", "REJECTED"), defaultHint = "APPROVED"),
                FirestoreFieldSpec("clinicalApproved", "BOOLEAN", required = false, editable = true, defaultHint = "true"),
                FirestoreFieldSpec("targetAudience", "STRING", required = false, editable = true, allowedValues = listOf("PUBLIC_ALL", "SPECIFIC_USERS", "USER_ROLES", "NEW_USERS", "EXISTING_USERS"), defaultHint = "PUBLIC_ALL"),
                FirestoreFieldSpec("targetValuesCsv", "STRING", required = false, editable = true, defaultHint = ""),
                FirestoreFieldSpec("targetScreen", "STRING", required = false, editable = true, defaultHint = "HOME_TOP"),
                FirestoreFieldSpec("metadata", "MAP", required = false, editable = true, defaultHint = """{"campaign":"Q4_Rehab"}"""),
                FirestoreFieldSpec("tags", "ARRAY", required = false, editable = true, defaultHint = """["dashboard","promo"]""")
            )
        ),
        FirestoreCollectionMeta(
            collectionPath = "banner_analytics_events",
            displayName = "Banner Analytics (banner_analytics_events) [Append-Only]",
            description = "Immutable impression, click, and dismissal events tracked across app users.",
            canCreate = true,
            canUpdate = false,
            canDelete = false,
            requiredViewPermission = AdminPermission.VIEW_ANALYTICS,
            requiredWritePermission = AdminPermission.MANAGE_BANNERS,
            requiredDeletePermission = null,
            fields = listOf(
                FirestoreFieldSpec("bannerId", "INT", required = true, editable = false, defaultHint = "1"),
                FirestoreFieldSpec("bannerKey", "STRING", required = true, editable = false, defaultHint = "banner_1"),
                FirestoreFieldSpec("eventType", "STRING", required = true, editable = false, allowedValues = listOf("IMPRESSION", "CLICK", "DISMISS"), defaultHint = "IMPRESSION"),
                FirestoreFieldSpec("userId", "STRING", required = true, editable = false, defaultHint = "uid_1"),
                FirestoreFieldSpec("username", "STRING", required = true, editable = false, defaultHint = "joshi"),
                FirestoreFieldSpec("dateKey", "STRING", required = true, editable = false, defaultHint = "2026-11-15"),
                FirestoreFieldSpec("screenSlot", "STRING", required = false, editable = false, defaultHint = "HOME_TOP")
            )
        ),
        FirestoreCollectionMeta(
            collectionPath = "support_tickets",
            displayName = "Support Tickets (support_tickets)",
            description = "Patient and clinician hardware support requests, priority triage, and replies.",
            canCreate = true,
            canUpdate = true,
            canDelete = true,
            requiredViewPermission = AdminPermission.VIEW_DASHBOARD,
            requiredWritePermission = AdminPermission.MANAGE_SUPPORT_TICKETS,
            requiredDeletePermission = AdminPermission.DELETE_USERS,
            fields = listOf(
                FirestoreFieldSpec("userId", "STRING", required = true, editable = false, defaultHint = "user_joshi"),
                FirestoreFieldSpec("username", "STRING", required = true, editable = false, defaultHint = "joshi"),
                FirestoreFieldSpec("userEmail", "STRING", required = true, editable = true, defaultHint = "joshi@neurogrip.io"),
                FirestoreFieldSpec("subject", "STRING", required = true, editable = true, defaultHint = "Index finger servo jitter"),
                FirestoreFieldSpec("message", "STRING", required = true, editable = true, defaultHint = "Servo vibrates above 85% EMG gain."),
                FirestoreFieldSpec("category", "STRING", required = false, editable = true, defaultHint = "HARDWARE"),
                FirestoreFieldSpec("priority", "STRING", required = false, editable = true, allowedValues = listOf("LOW", "MEDIUM", "HIGH", "CRITICAL"), defaultHint = "HIGH"),
                FirestoreFieldSpec("status", "STRING", required = true, editable = true, allowedValues = listOf("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED"), defaultHint = "OPEN"),
                FirestoreFieldSpec("adminReply", "STRING", required = false, editable = true, defaultHint = "Lower max grip force to 12.5N in Remote Config."),
                FirestoreFieldSpec("assignedTo", "STRING", required = false, editable = true, defaultHint = "hj123"),
                FirestoreFieldSpec("metadata", "MAP", required = false, editable = true, defaultHint = """{"firmware":"v2.4"}"""),
                FirestoreFieldSpec("tags", "ARRAY", required = false, editable = true, defaultHint = """["hardware","servo"]""")
            )
        ),
        FirestoreCollectionMeta(
            collectionPath = "subscriptions",
            displayName = "Subscriptions (subscriptions)",
            description = "User subscription plans, billing cycles, and revenue status records.",
            canCreate = true,
            canUpdate = true,
            canDelete = true,
            requiredViewPermission = AdminPermission.VIEW_DASHBOARD,
            requiredWritePermission = AdminPermission.MANAGE_SUBSCRIPTIONS,
            requiredDeletePermission = AdminPermission.MANAGE_SUBSCRIPTIONS,
            fields = listOf(
                FirestoreFieldSpec("userId", "STRING", required = true, editable = false, defaultHint = "user_joshi"),
                FirestoreFieldSpec("username", "STRING", required = true, editable = true, defaultHint = "joshi"),
                FirestoreFieldSpec("planName", "STRING", required = true, editable = true, defaultHint = "Pro Rehab"),
                FirestoreFieldSpec("status", "STRING", required = true, editable = true, allowedValues = listOf("ACTIVE", "PAST_DUE", "CANCELED", "TRIAL"), defaultHint = "ACTIVE"),
                FirestoreFieldSpec("amountUsd", "DOUBLE", required = true, editable = true, defaultHint = "29.0"),
                FirestoreFieldSpec("billingCycle", "STRING", required = false, editable = true, defaultHint = "MONTHLY"),
                FirestoreFieldSpec("paymentMethodLast4", "STRING", required = false, editable = true, defaultHint = "4242"),
                FirestoreFieldSpec("autoRenew", "BOOLEAN", required = false, editable = true, defaultHint = "true"),
                FirestoreFieldSpec("metadata", "MAP", required = false, editable = true, defaultHint = """{"invoicePrefix":"NG-INV"}"""),
                FirestoreFieldSpec("tags", "ARRAY", required = false, editable = true, defaultHint = """["billing","active"]""")
            )
        ),
        FirestoreCollectionMeta(
            collectionPath = "config_history",
            displayName = "Config History (config_history)",
            description = "Versioned remote configuration snapshots for auditing and rollback.",
            canCreate = true,
            canUpdate = false,
            canDelete = true,
            requiredViewPermission = AdminPermission.VIEW_DASHBOARD,
            requiredWritePermission = AdminPermission.MANAGE_REMOTE_CONFIG,
            requiredDeletePermission = AdminPermission.MANAGE_REMOTE_CONFIG,
            fields = listOf(
                FirestoreFieldSpec("configVersion", "INT", required = true, editable = false, defaultHint = "1"),
                FirestoreFieldSpec("summary", "STRING", required = true, editable = true, defaultHint = "Initial baseline configuration"),
                FirestoreFieldSpec("publishedBy", "STRING", required = true, editable = false, defaultHint = "hj123"),
                FirestoreFieldSpec("snapshotMap", "MAP", required = false, editable = true, defaultHint = """{"maintenanceMode":false,"maxGripForceLimitN":15.0}"""),
                FirestoreFieldSpec("metadata", "MAP", required = false, editable = true, defaultHint = """{"verified":true}"""),
                FirestoreFieldSpec("tags", "ARRAY", required = false, editable = true, defaultHint = """["rollback-ready"]""")
            )
        ),
        FirestoreCollectionMeta(
            collectionPath = "backups",
            displayName = "Backups (backups)",
            description = "Authorized Firestore collection export and recovery snapshots.",
            canCreate = true,
            canUpdate = true,
            canDelete = true,
            requiredViewPermission = AdminPermission.VIEW_DASHBOARD,
            requiredWritePermission = AdminPermission.MANAGE_BACKUPS,
            requiredDeletePermission = AdminPermission.MANAGE_BACKUPS,
            fields = listOf(
                FirestoreFieldSpec("label", "STRING", required = true, editable = true, defaultHint = "Production Snapshot v1"),
                FirestoreFieldSpec("createdBy", "STRING", required = true, editable = false, defaultHint = "hj123"),
                FirestoreFieldSpec("collectionName", "STRING", required = true, editable = false, defaultHint = "users"),
                FirestoreFieldSpec("recordCount", "INT", required = true, editable = false, defaultHint = "5"),
                FirestoreFieldSpec("sizeBytes", "INT", required = true, editable = false, defaultHint = "1024"),
                FirestoreFieldSpec("metadataMap", "MAP", required = false, editable = true, defaultHint = """{"format":"json_v1"}"""),
                FirestoreFieldSpec("tags", "ARRAY", required = false, editable = true, defaultHint = """["snapshot","verified"]""")
            )
        ),
        FirestoreCollectionMeta(
            collectionPath = "audit_logs",
            displayName = "Audit Logs (audit_logs) [Append-Only]",
            description = "Immutable security & database administration audit trail. Update and delete are forbidden by Firestore rules.",
            canCreate = true,
            canUpdate = false,
            canDelete = false,
            requiredViewPermission = AdminPermission.VIEW_AUDIT_LOGS,
            requiredWritePermission = AdminPermission.VIEW_AUDIT_LOGS,
            requiredDeletePermission = null,
            fields = listOf(
                FirestoreFieldSpec("actorUid", "STRING", required = true, editable = false, defaultHint = "admin_hj123"),
                FirestoreFieldSpec("actorUsername", "STRING", required = true, editable = false, defaultHint = "hj123"),
                FirestoreFieldSpec("actorRole", "STRING", required = true, editable = false, defaultHint = "SUPER_ADMIN"),
                FirestoreFieldSpec("actionType", "STRING", required = true, editable = false, defaultHint = "DB_EXPORT"),
                FirestoreFieldSpec("targetEntity", "STRING", required = true, editable = false, defaultHint = "users"),
                FirestoreFieldSpec("details", "STRING", required = true, editable = false, defaultHint = "Exported collection snapshot"),
                FirestoreFieldSpec("severity", "STRING", required = true, editable = false, allowedValues = listOf("INFO", "WARNING", "CRITICAL"), defaultHint = "INFO"),
                FirestoreFieldSpec("metadata", "MAP", required = false, editable = false, defaultHint = """{"ip":"127.0.0.1"}"""),
                FirestoreFieldSpec("tags", "ARRAY", required = false, editable = false, defaultHint = """["security","audit"]""")
            )
        ),
        FirestoreCollectionMeta(
            collectionPath = "sdui_configs",
            displayName = "Server-Driven UI (sdui_configs)",
            description = "Live Server-Driven UI schema, theme colors, section order, and media URLs synchronized to Android users.",
            canCreate = true,
            canUpdate = true,
            canDelete = false,
            requiredViewPermission = AdminPermission.VIEW_DASHBOARD,
            requiredWritePermission = AdminPermission.MANAGE_REMOTE_CONFIG,
            requiredDeletePermission = null,
            fields = listOf(
                FirestoreFieldSpec("configKey", "STRING", required = true, editable = false, defaultHint = "dashboard_active"),
                FirestoreFieldSpec("schemaVersion", "INT", required = true, editable = true, defaultHint = "1"),
                FirestoreFieldSpec("configVersion", "INT", required = true, editable = true, defaultHint = "1"),
                FirestoreFieldSpec("updatedBy", "STRING", required = true, editable = true, defaultHint = "hj123"),
                FirestoreFieldSpec("changeSummary", "STRING", required = false, editable = true, defaultHint = "Updated UI theme"),
                FirestoreFieldSpec("payloadJson", "STRING", required = true, editable = true, defaultHint = "{}"),
                FirestoreFieldSpec("contentSha256", "STRING", required = true, editable = false, defaultHint = ""),
                FirestoreFieldSpec("themePrimaryHex", "STRING", required = false, editable = true, defaultHint = "#4A7499"),
                FirestoreFieldSpec("themeBackgroundHex", "STRING", required = false, editable = true, defaultHint = "#F5F7FA"),
                FirestoreFieldSpec("heroTitle", "STRING", required = false, editable = true, defaultHint = "NeuroGrip Ready"),
                FirestoreFieldSpec("heroImageUrl", "STRING", required = false, editable = true, defaultHint = "https://images.unsplash.com/photo-1589254065878-42c9da997008?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80"),
                FirestoreFieldSpec("promoEnabled", "BOOLEAN", required = false, editable = true, defaultHint = "false"),
                FirestoreFieldSpec("sectionsOrder", "ARRAY", required = false, editable = true, defaultHint = """["promo_banner","hero","ai_assistant","permissions","control_modes","bio_signals","prosthetic_status","quick_actions"]"""),
                FirestoreFieldSpec("metadata", "MAP", required = false, editable = true, defaultHint = """{"sdui":"v1"}"""),
                FirestoreFieldSpec("tags", "ARRAY", required = false, editable = true, defaultHint = """["sdui","live"]""")
            )
        ),
        FirestoreCollectionMeta(
            collectionPath = "sdui_history",
            displayName = "SDUI History (sdui_history) [Append-Only]",
            description = "Immutable versioned snapshots of Server-Driven UI changes for auditing and rollback.",
            canCreate = true,
            canUpdate = false,
            canDelete = false,
            requiredViewPermission = AdminPermission.VIEW_DASHBOARD,
            requiredWritePermission = AdminPermission.MANAGE_REMOTE_CONFIG,
            requiredDeletePermission = null,
            fields = listOf(
                FirestoreFieldSpec("configVersion", "INT", required = true, editable = false, defaultHint = "1"),
                FirestoreFieldSpec("schemaVersion", "INT", required = true, editable = false, defaultHint = "1"),
                FirestoreFieldSpec("summary", "STRING", required = true, editable = false, defaultHint = "Baseline v1"),
                FirestoreFieldSpec("publishedBy", "STRING", required = true, editable = false, defaultHint = "hj123"),
                FirestoreFieldSpec("payloadJson", "STRING", required = true, editable = false, defaultHint = "{}"),
                FirestoreFieldSpec("contentSha256", "STRING", required = true, editable = false, defaultHint = ""),
                FirestoreFieldSpec("metadata", "MAP", required = false, editable = false, defaultHint = """{"rollbackReady":true}"""),
                FirestoreFieldSpec("tags", "ARRAY", required = false, editable = false, defaultHint = """["sdui-history"]""")
            )
        ),
        FirestoreCollectionMeta(
            collectionPath = "app_settings",
            displayName = "App Branding (app_settings)",
            description = "Super-Admin-managed live application name, Firebase Storage logo URL, and version counters.",
            canCreate = true,
            canUpdate = true,
            canDelete = false,
            requiredViewPermission = AdminPermission.VIEW_DASHBOARD,
            requiredWritePermission = AdminPermission.MANAGE_SYSTEM_CONFIG,
            requiredDeletePermission = null,
            fields = listOf(
                FirestoreFieldSpec("configKey", "STRING", required = true, editable = false, defaultHint = "branding"),
                FirestoreFieldSpec("appName", "STRING", required = true, editable = true, defaultHint = "NeuroGrip"),
                FirestoreFieldSpec("logoUrl", "STRING", required = true, editable = true, defaultHint = ""),
                FirestoreFieldSpec("logoVersion", "INT", required = true, editable = true, defaultHint = "1"),
                FirestoreFieldSpec("brandingVersion", "INT", required = true, editable = true, defaultHint = "1"),
                FirestoreFieldSpec("updatedBy", "STRING", required = true, editable = true, defaultHint = "hj195345@gmail.com"),
                FirestoreFieldSpec("metadata", "MAP", required = false, editable = true, defaultHint = """{"source":"admin_branding"}"""),
                FirestoreFieldSpec("tags", "ARRAY", required = false, editable = true, defaultHint = """["branding","identity"]""")
            )
        )
    )

    fun getMeta(collectionPath: String): FirestoreCollectionMeta? {
        return ALLOWLISTED_COLLECTIONS.find { it.collectionPath == collectionPath }
    }

    fun isAllowlisted(collectionPath: String): Boolean {
        if (collectionPath in PROTECTED_SYSTEM_COLLECTIONS) return false
        return getMeta(collectionPath) != null
    }
}

/**
 * Primary Cloud Firestore Repository using the provisioned named database ID (`R.string.firestore_database_id`).
 */
class FirestoreDatabaseRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val documentIdRegex = Regex("^[a-zA-Z0-9_\\-]+$")

    fun isValidDocumentId(docId: String): Boolean {
        return docId.isNotBlank() && docId.length <= 128 && documentIdRegex.matches(docId)
    }

    fun getFirestoreInstance(): FirebaseFirestore = db

    suspend fun publishSduiConfigDocument(config: SduiDashboardConfig): Result<Unit> {
        val docRef = db.collection("sdui_configs").document(SduiAllowlists.ACTIVE_CONFIG_KEY)
        return try {
            val existingSnap = docRef.get().await()
            val payload = SduiJsonCodec.toFirestoreMap(config).toMutableMap()
            val existingCreatedAt = existingSnap.getTimestamp("createdAt")
            if (existingSnap.exists() && existingCreatedAt != null) {
                payload["createdAt"] = existingCreatedAt
                payload["updatedAt"] = FieldValue.serverTimestamp()
                docRef.set(payload, SetOptions.merge()).await()
            } else {
                payload["createdAt"] = FieldValue.serverTimestamp()
                payload["updatedAt"] = FieldValue.serverTimestamp()
                docRef.set(payload).await()
            }

            val historyDocId = "sdui_v${config.configVersion}_${System.currentTimeMillis()}"
            val encodedJson = SduiJsonCodec.encode(config)
            val historyPayload = linkedMapOf<String, Any?>(
                "configVersion" to config.configVersion.toLong(),
                "schemaVersion" to config.schemaVersion.toLong(),
                "summary" to config.changeSummary.ifBlank { "Published SDUI v${config.configVersion}" },
                "publishedBy" to config.updatedBy,
                "payloadJson" to encodedJson,
                "contentSha256" to SduiJsonCodec.sha256(encodedJson),
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("sdui_history").document(historyDocId).set(historyPayload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * Verifies a signed-in Firebase user's admin membership from `/admins/{uid}` and `/users/{uid}`.
     * Returns an AdminRole if found in Firestore, `AdminRole.BLOCKED` if account is deactivated,
     * or `null` if no cloud record exists yet.
     */
    suspend fun verifyCloudAdminRole(uid: String): Result<AdminRole?> {
        if (!isValidDocumentId(uid)) {
            return Result.success(null)
        }
        return try {
            val adminSnap = db.collection("admins").document(uid).get().await()
            if (adminSnap.exists()) {
                val roleStr = adminSnap.getString("role") ?: AdminRole.USER.name
                val parsed = AdminRole.fromString(roleStr)
                if (SecureSessionManager.isAuthorizedAdminRole(parsed)) {
                    return Result.success(parsed)
                }
            }

            val userSnap = db.collection("users").document(uid).get().await()
            if (userSnap.exists()) {
                val statusStr = userSnap.getString("status") ?: AccountStatus.ACTIVE.name
                if (!statusStr.equals(AccountStatus.ACTIVE.name, ignoreCase = true)) {
                    return Result.success(AdminRole.BLOCKED)
                }
                val roleStr = userSnap.getString("role") ?: AdminRole.USER.name
                return Result.success(AdminRole.fromString(roleStr))
            }

            Result.success(null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Typed User Profile Methods (Used by App & Robolectric Rule Tests) ---

    suspend fun createOwnUserProfile(
        uid: String,
        username: String,
        name: String,
        email: String,
        mobile: String = "+91 98765 43210",
        photoUrl: String? = null,
        photoUpdatedAt: Long? = null,
        metadata: Map<String, Any>? = null,
        tags: List<String>? = null
    ): Result<String> {
        val docRef = db.collection("users").document(uid)
        val safePhotoUrl = photoUrl?.trim()?.takeIf { it.isEmpty() || it.startsWith("https://", ignoreCase = true) }
        val payload = mutableMapOf<String, Any?>(
            "userId" to uid,
            "username" to username.trim(),
            "name" to name.trim(),
            "email" to email.trim(),
            "mobile" to mobile.trim(),
            "role" to "USER",
            "status" to "ACTIVE",
            "subscriptionPlan" to "Free Basic",
            "deviceId" to "NG-ESP32-001",
            "notes" to "",
            "photoUrl" to safePhotoUrl,
            "photoUpdatedAt" to photoUpdatedAt?.takeIf { it >= 0L },
            "twoFactorEnabled" to false,
            "loginCount" to 1L,
            "metadata" to metadata,
            "tags" to tags,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }
        return try {
            docRef.set(payload).await()
            Result.success(uid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getUserProfileById(uid: String): Result<FirestoreUserDoc?> {
        val docRef = db.collection("users").document(uid)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                Result.success(null)
            } else {
                Result.success(
                    snap.toObject(
                        FirestoreUserDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    fun observeOwnUserProfile(uid: String): Flow<List<FirestoreUserDoc>> {
        return db.collection("users")
            .whereEqualTo("userId", uid)
            .snapshots()
            .map { querySnapshot ->
                querySnapshot.toObjects(
                    FirestoreUserDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
    }

    suspend fun updateOwnUserProfile(
        uid: String,
        name: String,
        mobile: String,
        notes: String,
        metadata: Map<String, Any>? = null,
        tags: List<String>? = null
    ): Result<Unit> {
        val docRef = db.collection("users").document(uid)
        val updates = mutableMapOf<String, Any?>(
            "name" to name.trim(),
            "mobile" to mobile.trim(),
            "notes" to notes.trim(),
            "metadata" to metadata,
            "tags" to tags,
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateOwnProfilePhoto(
        uid: String,
        photoUrl: String,
        photoUpdatedAt: Long = System.currentTimeMillis()
    ): Result<Unit> {
        val cleanUrl = photoUrl.trim()
        if (cleanUrl.isNotEmpty() && !cleanUrl.startsWith("https://", ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("Profile photo URL must be empty or HTTPS."))
        }
        val docRef = db.collection("users").document(uid)
        val updates = mapOf<String, Any>(
            "photoUrl" to cleanUrl,
            "photoUpdatedAt" to photoUpdatedAt.coerceAtLeast(0L),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun uploadProfilePhotoToStorage(
        uid: String,
        validated: ValidatedProfilePhoto,
        previousPhotoUrl: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): Result<String> {
        val safeUid = uid.trim()
        if (safeUid.isEmpty()) {
            return Result.failure(IllegalStateException("Authenticated Firebase UID is required to upload profile photo."))
        }
        return try {
            val storage = FirebaseStorage.getInstance(db.app)
            val objectPath = "profile_photos/$safeUid/avatar_${timestamp}.${validated.extension}"
            val ref = storage.reference.child(objectPath)
            val metadata = StorageMetadata.Builder()
                .setContentType(validated.mimeType)
                .setCacheControl("public,max-age=3600")
                .build()
            ref.putBytes(validated.bytes, metadata).await()
            val downloadUri = ref.downloadUrl.await().toString()
            if (!previousPhotoUrl.isNullOrBlank() && previousPhotoUrl != downloadUri) {
                deleteProfilePhotoFromStorage(previousPhotoUrl)
            }
            Result.success(downloadUri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteProfilePhotoFromStorage(photoUrl: String?) {
        val clean = photoUrl?.trim().orEmpty()
        if (!clean.contains("firebasestorage", ignoreCase = true) && !clean.startsWith("gs://")) {
            return
        }
        runCatching {
            val storage = FirebaseStorage.getInstance(db.app)
            storage.getReferenceFromUrl(clean).delete().await()
        }
    }

    // --- App Branding & Identity (app_settings/branding + Storage app_branding/) ---

    suspend fun fetchAppBranding(): Result<FirestoreAppBrandingDoc?> {
        val docRef = db.collection(AppBrandingConfig.COLLECTION_NAME)
            .document(AppBrandingConfig.BRANDING_DOC_ID)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                Result.success(null)
            } else {
                Result.success(
                    snap.toObject(
                        FirestoreAppBrandingDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    fun observeAppBranding(): Flow<FirestoreAppBrandingDoc?> {
        val docRef = db.collection(AppBrandingConfig.COLLECTION_NAME)
            .document(AppBrandingConfig.BRANDING_DOC_ID)
        return docRef.snapshots().map { snap ->
            if (!snap.exists()) null
            else snap.toObject(
                FirestoreAppBrandingDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        }
    }

    suspend fun publishAppBranding(
        config: AppBrandingConfig,
        metadata: Map<String, Any>? = null,
        tags: List<String>? = null
    ): Result<Unit> {
        val docRef = db.collection(AppBrandingConfig.COLLECTION_NAME)
            .document(AppBrandingConfig.BRANDING_DOC_ID)
        val nameErr = AppBrandingValidator.validateAppName(config.appName)
        if (nameErr != null) {
            return Result.failure(IllegalArgumentException(nameErr))
        }
        val cleanLogoUrl = config.logoUrl.trim()
        val urlErr = AppBrandingValidator.validateLogoUrlForCloud(cleanLogoUrl)
        if (urlErr != null) {
            return Result.failure(IllegalArgumentException(urlErr))
        }

        val payload = linkedMapOf<String, Any>(
            "configKey" to AppBrandingConfig.BRANDING_DOC_ID,
            "appName" to config.appName.trim(),
            "logoUrl" to cleanLogoUrl,
            "logoVersion" to config.logoVersion.coerceAtLeast(1L),
            "brandingVersion" to config.brandingVersion.coerceAtLeast(1L),
            "updatedBy" to config.updatedBy.trim().ifBlank { "super_admin" }.take(100)
        )
        if (metadata != null) payload["metadata"] = metadata
        if (tags != null) payload["tags"] = tags

        return try {
            val existingSnap = docRef.get().await()
            val existingCreatedAt = existingSnap.getTimestamp("createdAt")
            if (existingSnap.exists() && existingCreatedAt != null) {
                payload["createdAt"] = existingCreatedAt
                payload["updatedAt"] = FieldValue.serverTimestamp()
                docRef.set(payload, SetOptions.merge()).await()
            } else {
                payload["createdAt"] = FieldValue.serverTimestamp()
                payload["updatedAt"] = FieldValue.serverTimestamp()
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun uploadBrandingLogoToStorage(
        validated: ValidatedBrandingLogo,
        logoVersion: Long,
        previousLogoUrl: String? = null,
        timestamp: Long = System.currentTimeMillis(),
        onProgress: (Float) -> Unit = {}
    ): Result<String> {
        return try {
            val storage = FirebaseStorage.getInstance(db.app)
            val safeVer = logoVersion.coerceAtLeast(1L)
            val objectPath = "app_branding/logo_v${safeVer}_${timestamp}.${validated.extension}"
            val ref = storage.reference.child(objectPath)
            val metadata = StorageMetadata.Builder()
                .setContentType(validated.mimeType)
                .setCacheControl("public,max-age=3600")
                .build()
            val uploadTask = ref.putBytes(validated.bytes, metadata)
            uploadTask.addOnProgressListener { snap ->
                if (snap.totalByteCount > 0) {
                    onProgress((snap.bytesTransferred.toFloat() / snap.totalByteCount.toFloat()).coerceIn(0f, 1f))
                }
            }
            uploadTask.await()
            onProgress(1f)
            val downloadUri = ref.downloadUrl.await().toString()
            if (!previousLogoUrl.isNullOrBlank() && previousLogoUrl != downloadUri) {
                deleteBrandingLogoFromStorage(previousLogoUrl)
            }
            Result.success(downloadUri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteBrandingLogoFromStorage(logoUrl: String?) {
        val clean = logoUrl?.trim().orEmpty()
        if (!clean.contains("firebasestorage", ignoreCase = true) && !clean.startsWith("gs://")) {
            return
        }
        runCatching {
            val storage = FirebaseStorage.getInstance(db.app)
            storage.getReferenceFromUrl(clean).delete().await()
        }
    }

    // --- Collection & Document Management Operations ---

    suspend fun fetchCollectionDocumentsFromCloud(
        collectionPath: String,
        limitCount: Long = 100L
    ): Result<List<FirestoreDocumentRecord>> {
        if (!FirestoreCollectionAllowlist.isAllowlisted(collectionPath)) {
            return Result.failure(
                SecurityException("Collection '$collectionPath' is protected or not in the administrator allowlist.")
            )
        }
        val currentUser = try {
            FirebaseAuth.getInstance().currentUser
        } catch (_: Exception) {
            null
        } ?: return Result.success(emptyList())

        val email = currentUser.email?.trim()?.lowercase().orEmpty()
        val isBootstrappedAdmin = currentUser.isEmailVerified &&
            (email == "hj195344@gmail.com" || email == "hj195345@gmail.com")

        return try {
            val baseCollection = db.collection(collectionPath)
            val query = when {
                isBootstrappedAdmin -> baseCollection.limit(limitCount.coerceIn(1L, 200L))
                collectionPath in setOf("users", "support_tickets", "subscriptions", "banner_analytics_events") ->
                    baseCollection.whereEqualTo("userId", currentUser.uid).limit(limitCount.coerceIn(1L, 200L))
                collectionPath == "notifications" ->
                    baseCollection.whereEqualTo("targetAudience", "ALL_USERS").limit(limitCount.coerceIn(1L, 200L))
                collectionPath == "remote_config" ->
                    baseCollection.whereEqualTo("configKey", "current").limit(limitCount.coerceIn(1L, 200L))
                collectionPath == "sdui_configs" ->
                    baseCollection.whereEqualTo("configKey", "dashboard_active").limit(limitCount.coerceIn(1L, 200L))
                collectionPath == "app_settings" ->
                    baseCollection.whereEqualTo("configKey", AppBrandingConfig.BRANDING_DOC_ID).limit(1L)
                else -> return Result.success(emptyList())
            }
            val snapshot = query.get().await()
            val records = snapshot.documents.map { doc ->
                snapshotToRecord(collectionPath, doc)
            }
            Result.success(records)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, collectionPath)
            Result.failure(e)
        }
    }

    suspend fun createOrUpdateCloudDocument(
        collectionPath: String,
        documentId: String,
        fields: Map<String, Any?>,
        isCreate: Boolean
    ): Result<FirestoreDocumentRecord> {
        if (!FirestoreCollectionAllowlist.isAllowlisted(collectionPath)) {
            return Result.failure(
                SecurityException("Collection '$collectionPath' is protected or not allowlisted.")
            )
        }
        val meta = FirestoreCollectionAllowlist.getMeta(collectionPath)!!
        if (isCreate && !meta.canCreate) {
            return Result.failure(SecurityException("Creation is disabled for collection '$collectionPath'."))
        }
        if (!isCreate && !meta.canUpdate) {
            return Result.failure(SecurityException("Updates are forbidden on append-only collection '$collectionPath'."))
        }
        if (!isValidDocumentId(documentId)) {
            return Result.failure(
                IllegalArgumentException("Invalid document ID '$documentId'. Use 1-128 alphanumeric, '-', or '_' characters.")
            )
        }

        val docRef = db.collection(collectionPath).document(documentId)
        val sanitized = HashMap<String, Any>()
        val allowedNames = meta.fields.map { it.name }.toSet()

        for ((k, v) in fields) {
            if (k in allowedNames && v != null) {
                sanitized[k] = v
            }
        }

        val nowMs = System.currentTimeMillis()
        return try {
            if (isCreate) {
                sanitized["createdAt"] = FieldValue.serverTimestamp()
                if (collectionPath != "audit_logs") {
                    sanitized["updatedAt"] = FieldValue.serverTimestamp()
                }
                docRef.set(sanitized).await()
            } else {
                val existingSnap = docRef.get().await()
                val existingCreatedAt = existingSnap.getTimestamp("createdAt") ?: Timestamp.now()
                sanitized["createdAt"] = existingCreatedAt
                if (collectionPath != "audit_logs") {
                    sanitized["updatedAt"] = FieldValue.serverTimestamp()
                }
                docRef.set(sanitized, SetOptions.merge()).await()
            }
            Result.success(
                FirestoreDocumentRecord(
                    documentId = documentId,
                    collectionPath = collectionPath,
                    data = sanitized.filterValues { it !is FieldValue },
                    createdAtMillis = nowMs,
                    updatedAtMillis = nowMs
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (isCreate) OperationType.CREATE else OperationType.UPDATE,
                docRef.path
            )
            Result.failure(e)
        }
    }

    suspend fun deleteCloudDocument(
        collectionPath: String,
        documentId: String
    ): Result<Unit> {
        if (!FirestoreCollectionAllowlist.isAllowlisted(collectionPath)) {
            return Result.failure(SecurityException("Collection '$collectionPath' is not allowlisted."))
        }
        val meta = FirestoreCollectionAllowlist.getMeta(collectionPath)!!
        if (!meta.canDelete) {
            return Result.failure(SecurityException("Deletion is forbidden on collection '$collectionPath'."))
        }
        if (!isValidDocumentId(documentId)) {
            return Result.failure(IllegalArgumentException("Invalid document ID '$documentId'."))
        }
        val docRef = db.collection(collectionPath).document(documentId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    private fun snapshotToRecord(
        collectionPath: String,
        doc: DocumentSnapshot
    ): FirestoreDocumentRecord {
        val rawMap = doc.data ?: emptyMap()
        val createdTs = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        val updatedTs = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: createdTs
        val normalized = LinkedHashMap<String, Any?>()
        for ((k, v) in rawMap) {
            normalized[k] = when (v) {
                is Timestamp -> v.toDate().time
                else -> v
            }
        }
        return FirestoreDocumentRecord(
            documentId = doc.id,
            collectionPath = collectionPath,
            data = normalized,
            createdAtMillis = createdTs?.toDate()?.time ?: System.currentTimeMillis(),
            updatedAtMillis = updatedTs?.toDate()?.time ?: System.currentTimeMillis()
        )
    }

    companion object {
        /**
         * Parses and validates raw string inputs from the Android Database Editor into typed Firestore values,
         * including nested JSON Maps (`Map<String, Any>`) and bounded String Arrays (`List<String>`).
         */
        fun parseAndValidateFieldValue(spec: FirestoreFieldSpec, rawInput: String): Result<Any?> {
            val trimmed = rawInput.trim()
            if (trimmed.isEmpty()) {
                return if (spec.required) {
                    Result.failure(IllegalArgumentException("Field '${spec.name}' is required."))
                } else {
                    Result.success(null)
                }
            }
            return try {
                when (spec.type) {
                    "STRING" -> {
                        if (spec.allowedValues.isNotEmpty() && trimmed !in spec.allowedValues) {
                            return Result.failure(
                                IllegalArgumentException(
                                    "Field '${spec.name}' must be one of: ${spec.allowedValues.joinToString(", ")}"
                                )
                            )
                        }
                        if (trimmed.length > 2000) {
                            return Result.failure(IllegalArgumentException("Field '${spec.name}' exceeds max length (2000 chars)."))
                        }
                        Result.success(trimmed)
                    }
                    "INT" -> {
                        val v = trimmed.toLongOrNull()
                            ?: return Result.failure(IllegalArgumentException("Field '${spec.name}' must be a valid integer."))
                        Result.success(v)
                    }
                    "DOUBLE" -> {
                        val v = trimmed.toDoubleOrNull()
                            ?: return Result.failure(IllegalArgumentException("Field '${spec.name}' must be a valid decimal number."))
                        Result.success(v)
                    }
                    "BOOLEAN" -> {
                        val lower = trimmed.lowercase()
                        if (lower != "true" && lower != "false") {
                            return Result.failure(IllegalArgumentException("Field '${spec.name}' must be 'true' or 'false'."))
                        }
                        Result.success(lower == "true")
                    }
                    "MAP" -> {
                        val json = JSONObject(trimmed)
                        val map = LinkedHashMap<String, Any>()
                        val keys = json.keys()
                        var count = 0
                        while (keys.hasNext()) {
                            count++
                            if (count > 25) {
                                return Result.failure(IllegalArgumentException("Map '${spec.name}' exceeds maximum 25 keys."))
                            }
                            val k = keys.next()
                            val value = json.get(k)
                            map[k] = when (value) {
                                is Int -> value.toLong()
                                is Long, is Double, is Boolean, is String -> value
                                else -> value.toString()
                            }
                        }
                        Result.success(map)
                    }
                    "ARRAY" -> {
                        val list = mutableListOf<String>()
                        if (trimmed.startsWith("[")) {
                            val arr = JSONArray(trimmed)
                            if (arr.length() > 20) {
                                return Result.failure(IllegalArgumentException("Array '${spec.name}' exceeds maximum 20 items."))
                            }
                            for (i in 0 until arr.length()) {
                                list.add(arr.get(i).toString())
                            }
                        } else {
                            val parts = trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            if (parts.size > 20) {
                                return Result.failure(IllegalArgumentException("Array '${spec.name}' exceeds maximum 20 items."))
                            }
                            list.addAll(parts)
                        }
                        Result.success(list)
                    }
                    else -> Result.success(trimmed)
                }
            } catch (e: Exception) {
                Result.failure(
                    IllegalArgumentException(
                        "Invalid ${spec.type} format for '${spec.name}'."
                    )
                )
            }
        }

        fun formatFieldValueForEditor(value: Any?): String {
            return when (value) {
                null -> ""
                is Map<*, *> -> JSONObject(value).toString()
                is List<*> -> JSONArray(value).toString()
                is Timestamp -> value.toDate().time.toString()
                is Date -> value.time.toString()
                else -> value.toString()
            }
        }
    }
}
