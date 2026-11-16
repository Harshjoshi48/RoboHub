package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.Color
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class BannerStylePreset(
    val backgroundHex: String,
    val textHex: String,
    val buttonHex: String,
    val buttonTextHex: String,
    val borderHex: String,
    val iconKey: String
)

enum class BannerType(
    val label: String,
    val badgeText: String,
    val requiresClinicalReview: Boolean,
    val preset: BannerStylePreset
) {
    ANNOUNCEMENT(
        label = "Announcement",
        badgeText = "ANNOUNCEMENT",
        requiresClinicalReview = false,
        preset = BannerStylePreset(
            backgroundHex = "#EFF6FF",
            textHex = "#1E3A8A",
            buttonHex = "#1D4ED8",
            buttonTextHex = "#FFFFFF",
            borderHex = "#93C5FD",
            iconKey = "campaign"
        )
    ),
    CLINICAL_TIP(
        label = "Clinical Tip",
        badgeText = "CLINICAL TIP",
        requiresClinicalReview = true,
        preset = BannerStylePreset(
            backgroundHex = "#ECFDF5",
            textHex = "#065F46",
            buttonHex = "#047857",
            buttonTextHex = "#FFFFFF",
            borderHex = "#6EE7B7",
            iconKey = "medical"
        )
    ),
    PROMOTION(
        label = "Promotion",
        badgeText = "PROMO",
        requiresClinicalReview = false,
        preset = BannerStylePreset(
            backgroundHex = "#4C1D95",
            textHex = "#FFFFFF",
            buttonHex = "#F59E0B",
            buttonTextHex = "#111827",
            borderHex = "#8B5CF6",
            iconKey = "star"
        )
    ),
    WARNING(
        label = "Warning / Safety",
        badgeText = "WARNING",
        requiresClinicalReview = false,
        preset = BannerStylePreset(
            backgroundHex = "#FEF2F2",
            textHex = "#991B1B",
            buttonHex = "#DC2626",
            buttonTextHex = "#FFFFFF",
            borderHex = "#FCA5A5",
            iconKey = "warning"
        )
    );

    companion object {
        fun fromString(raw: String?): BannerType {
            return entries.find { it.name.equals(raw?.trim(), ignoreCase = true) } ?: ANNOUNCEMENT
        }
    }
}

enum class BannerCtaAction(
    val label: String,
    val targetLabel: String,
    val targetHint: String
) {
    NONE("No Action", "Target", ""),
    OPEN_URL("Open External URL", "HTTPS URL", "https://neurogrip.io/updates"),
    OPEN_SCREEN("Open App Screen", "Screen Route", "hand_control"),
    OPEN_CATEGORY("Open Category", "Category Key", "grip_exercises"),
    OPEN_PRODUCT("Open Module / Product", "Module ID", "neurogrip_glove_v2"),
    SHOW_POPUP("Show Info Popup", "Popup Message", "Review updated calibration instructions."),
    CALL("Phone Call", "Phone Number", "+919825011223"),
    WHATSAPP("Open WhatsApp", "WhatsApp Number", "919825011223");

    companion object {
        fun fromString(raw: String?): BannerCtaAction {
            return entries.find { it.name.equals(raw?.trim(), ignoreCase = true) } ?: OPEN_SCREEN
        }
    }
}

enum class BannerPosition(val label: String, val groupLabel: String) {
    HOME_TOP("Home Top", "Dashboard Feed"),
    HOME_SLIDER("Home Slider / Carousel", "Dashboard Feed"),
    HOME_MIDDLE("Home Middle", "Dashboard Feed"),
    HOME_BOTTOM("Home Bottom", "Dashboard Feed"),
    TOP_STICKY("Top Sticky", "Overlay Bar"),
    BOTTOM_STICKY("Bottom Sticky", "Overlay Bar"),
    FLOATING("Floating Pill", "Floating Overlay"),
    POPUP("Popup Modal", "Modal Overlay"),
    CATEGORY("Category Slot", "Studio / Control"),
    PRODUCT_DETAILS("Module Details Slot", "Reports / Details");

    companion object {
        fun fromString(raw: String?): BannerPosition {
            return entries.find { it.name.equals(raw?.trim(), ignoreCase = true) } ?: HOME_TOP
        }
    }
}

enum class BannerTargetingMode(val label: String, val hint: String) {
    PUBLIC_ALL("Public / All Users", ""),
    SPECIFIC_USERS("Specific Users", "joshi,hj123"),
    USER_ROLES("User Roles", "USER,ADMIN,EDITOR"),
    NEW_USERS("New Users (<= 2 logins)", ""),
    EXISTING_USERS("Existing Users (>= 3 logins)", "");

    companion object {
        fun fromString(raw: String?): BannerTargetingMode {
            return entries.find { it.name.equals(raw?.trim(), ignoreCase = true) } ?: PUBLIC_ALL
        }
    }
}

enum class ClinicalReviewStatus(val label: String) {
    NOT_REQUIRED("Not Required"),
    PENDING_REVIEW("Pending Clinical Review"),
    APPROVED("Clinically Approved"),
    REJECTED("Clinical Review Rejected");

    companion object {
        fun fromString(raw: String?): ClinicalReviewStatus {
            return entries.find { it.name.equals(raw?.trim(), ignoreCase = true) } ?: APPROVED
        }
    }
}

enum class BannerLifecycleState(val label: String) {
    LIVE("LIVE"),
    DRAFT("DRAFT"),
    SCHEDULED("SCHEDULED"),
    EXPIRED("EXPIRED"),
    PENDING_REVIEW("REVIEW PENDING"),
    INACTIVE("INACTIVE"),
    PRIVATE("PRIVATE")
}

data class BannerUserContext(
    val userId: String = "",
    val username: String = "",
    val email: String = "",
    val role: String = "USER",
    val loginCount: Int = 1,
    val accountCreatedAtMillis: Long = 0L,
    val dismissedAtMillisByBannerKey: Map<String, Long> = emptyMap(),
    val nowMillis: Long = System.currentTimeMillis()
)

data class BannerDashboardMetrics(
    val totalBanners: Int = 0,
    val activeBanners: Int = 0,
    val inactiveBanners: Int = 0,
    val publishedBanners: Int = 0,
    val draftBanners: Int = 0,
    val scheduledBanners: Int = 0,
    val totalImpressions: Int = 0,
    val totalClicks: Int = 0
)

data class BannerDailyStat(
    val dateKey: String,
    val impressions: Int,
    val uniqueViews: Int,
    val clicks: Int
) {
    val ctrPercent: Float
        get() = if (impressions > 0) (clicks.toFloat() / impressions.toFloat()) * 100f else 0f
}

data class BannerAnalyticsSummary(
    val totalImpressions: Int = 0,
    val totalClicks: Int = 0,
    val totalUniqueViews: Int = 0,
    val totalDismissals: Int = 0,
    val overallCtrPercent: Float = 0f,
    val dailyStats: List<BannerDailyStat> = emptyList(),
    val perBannerStats: List<BannerContentEntity> = emptyList()
)

object BannerAppScreens {
    val routes: List<Pair<String, String>> = listOf(
        "hand_control" to "Robotic Hand Control",
        "gesture_studio" to "Gesture Studio",
        "ai_assistant" to "AI Rehab Assistant",
        "reports" to "Progress & Reports",
        "connect_devices" to "Connect Devices (BLE)",
        "notifications" to "Notifications Center",
        "profile" to "User Profile",
        "settings" to "Settings",
        "permissions" to "Device Permissions"
    )
}

object BannerColorUtils {
    private val HEX_REGEX = Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")

    fun isValidHex(hex: String?): Boolean {
        return !hex.isNullOrBlank() && HEX_REGEX.matches(hex.trim())
    }

    fun normalizeHexOrDefault(raw: String?, fallback: String): String {
        val candidate = raw?.trim().orEmpty().let {
            if (it.isNotEmpty() && !it.startsWith("#")) "#$it" else it
        }
        return if (isValidHex(candidate)) candidate.uppercase(Locale.US) else fallback
    }

    fun parseColorOrDefault(raw: String?, fallback: Color): Color {
        return try {
            val clean = raw?.trim().orEmpty()
            if (!isValidHex(clean)) return fallback
            Color(android.graphics.Color.parseColor(clean))
        } catch (_: Exception) {
            fallback
        }
    }

    val curatedSwatches: List<String> = listOf(
        "#EFF6FF", "#1E40AF", "#0F172A", "#ECFDF5", "#047857",
        "#4C1D95", "#F59E0B", "#FEF2F2", "#DC2626", "#FFFFFF"
    )
}

object BannerTimezoneHelper {
    val commonTimezones: List<String> = listOf(
        "Asia/Kolkata",
        "UTC",
        "America/New_York",
        "America/Los_Angeles",
        "Europe/London",
        "Asia/Singapore",
        TimeZone.getDefault().id
    ).distinct()

    fun todayDateKey(timezoneId: String = TimeZone.getDefault().id, nowMillis: Long = System.currentTimeMillis()): String {
        return try {
            val zone = safeZoneId(timezoneId)
            Instant.ofEpochMilli(nowMillis).atZone(zone).format(DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: Exception) {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMillis))
        }
    }

    fun safeZoneId(timezoneId: String?): ZoneId {
        return try {
            if (timezoneId.isNullOrBlank()) ZoneId.systemDefault() else ZoneId.of(timezoneId.trim())
        } catch (_: Exception) {
            ZoneId.systemDefault()
        }
    }

    fun formatMillis(millis: Long, timezoneId: String): String {
        if (millis <= 0L) return "Immediate / No Limit"
        return try {
            val zone = safeZoneId(timezoneId)
            val zdt = Instant.ofEpochMilli(millis).atZone(zone)
            zdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US))
        } catch (_: Exception) {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(millis))
        }
    }

    fun parseDateTimeToMillis(dateTimeText: String, timezoneId: String): Long? {
        val clean = dateTimeText.trim()
        if (clean.isEmpty()) return 0L
        return try {
            val zone = safeZoneId(timezoneId)
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)
            val local = java.time.LocalDateTime.parse(clean, formatter)
            ZonedDateTime.of(local, zone).toInstant().toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }
}

object BannerEligibilityEngine {

    fun effectiveBannerKey(banner: BannerContentEntity): String {
        return banner.bannerKey.ifBlank {
            if (banner.id > 0) "banner_${banner.id}" else "banner_${banner.title.hashCode()}"
        }
    }

    fun resolveClinicalApproved(banner: BannerContentEntity): Boolean {
        val type = BannerType.fromString(banner.bannerType)
        if (!type.requiresClinicalReview) return true
        return ClinicalReviewStatus.fromString(banner.clinicalReviewStatus) == ClinicalReviewStatus.APPROVED &&
            banner.clinicalApproved
    }

    fun classifyLifecycleState(
        banner: BannerContentEntity,
        nowMillis: Long = System.currentTimeMillis()
    ): BannerLifecycleState {
        if (banner.isDraft) return BannerLifecycleState.DRAFT
        if (!banner.isActive) return BannerLifecycleState.INACTIVE
        val type = BannerType.fromString(banner.bannerType)
        if (type.requiresClinicalReview && !resolveClinicalApproved(banner)) {
            return BannerLifecycleState.PENDING_REVIEW
        }
        if (banner.endAtMillis > 0L && nowMillis > banner.endAtMillis) {
            return BannerLifecycleState.EXPIRED
        }
        if (banner.startAtMillis > 0L && nowMillis < banner.startAtMillis) {
            return BannerLifecycleState.SCHEDULED
        }
        if (!banner.isPublished || !banner.isPublic) return BannerLifecycleState.PRIVATE
        return BannerLifecycleState.LIVE
    }

    fun computeDashboardMetrics(
        banners: List<BannerContentEntity>,
        analyticsRows: List<BannerAnalyticsEntity> = emptyList(),
        nowMillis: Long = System.currentTimeMillis()
    ): BannerDashboardMetrics {
        val scheduledCount = banners.count { b ->
            !b.isDraft && b.isActive && b.startAtMillis > nowMillis && (b.endAtMillis <= 0L || b.endAtMillis > nowMillis)
        }
        val rowImpressions = analyticsRows.sumOf { it.impressions }
        val rowClicks = analyticsRows.sumOf { it.clicks }
        val entityImpressions = banners.sumOf { it.impressionsCount }
        val entityClicks = banners.sumOf { it.clicksCount }

        return BannerDashboardMetrics(
            totalBanners = banners.size,
            activeBanners = banners.count { it.isActive },
            inactiveBanners = banners.count { !it.isActive },
            publishedBanners = banners.count { it.isPublished && !it.isDraft },
            draftBanners = banners.count { it.isDraft },
            scheduledBanners = scheduledCount,
            totalImpressions = maxOf(rowImpressions, entityImpressions),
            totalClicks = maxOf(rowClicks, entityClicks)
        )
    }

    fun isEligibleForUser(
        banner: BannerContentEntity,
        userContext: BannerUserContext
    ): Boolean {
        if (!banner.isActive || !banner.isPublic || !banner.isPublished || banner.isDraft) {
            return false
        }
        if (!resolveClinicalApproved(banner)) {
            return false
        }
        val now = userContext.nowMillis
        if (banner.startAtMillis > 0L && now < banner.startAtMillis) {
            return false
        }
        if (banner.endAtMillis > 0L && now > banner.endAtMillis) {
            return false
        }

        // Dismiss cooldown gate
        if (banner.dismissible) {
            val key = effectiveBannerKey(banner)
            val dismissedAt = userContext.dismissedAtMillisByBannerKey[key]
            if (dismissedAt != null) {
                val cooldownHours = banner.reShowAfterDismissHours
                if (cooldownHours <= 0) {
                    return false
                }
                val cooldownMs = cooldownHours * 3_600_000L
                if (now - dismissedAt < cooldownMs) {
                    return false
                }
            }
        }

        // Targeting evaluation
        val mode = BannerTargetingMode.fromString(banner.targetAudience)
        val targets = banner.targetValuesCsv
            .split(",")
            .map { it.trim().lowercase(Locale.US) }
            .filter { it.isNotEmpty() }

        return when (mode) {
            BannerTargetingMode.PUBLIC_ALL -> true
            BannerTargetingMode.SPECIFIC_USERS -> {
                if (targets.isEmpty()) true else {
                    val uName = userContext.username.trim().lowercase(Locale.US)
                    val uEmail = userContext.email.trim().lowercase(Locale.US)
                    val uId = userContext.userId.trim().lowercase(Locale.US)
                    targets.any { t -> t == uName || t == uEmail || t == uId }
                }
            }
            BannerTargetingMode.USER_ROLES -> {
                if (targets.isEmpty()) true else {
                    val roleLower = userContext.role.trim().lowercase(Locale.US)
                    targets.any { it == roleLower }
                }
            }
            BannerTargetingMode.NEW_USERS -> {
                val within7Days = userContext.accountCreatedAtMillis > 0L &&
                    (now - userContext.accountCreatedAtMillis) <= 7L * 24 * 3_600_000L
                userContext.loginCount <= 2 || within7Days
            }
            BannerTargetingMode.EXISTING_USERS -> {
                val olderThan7Days = userContext.accountCreatedAtMillis > 0L &&
                    (now - userContext.accountCreatedAtMillis) > 7L * 24 * 3_600_000L
                userContext.loginCount > 2 || olderThan7Days
            }
        }
    }

    fun filterEligibleForPosition(
        banners: List<BannerContentEntity>,
        position: BannerPosition,
        userContext: BannerUserContext
    ): List<BannerContentEntity> {
        return banners
            .filter { BannerPosition.fromString(it.displayPosition) == position }
            .filter { isEligibleForUser(it, userContext) }
            .sortedWith(
                compareByDescending<BannerContentEntity> { it.priority }
                    .thenByDescending { it.updatedAt }
            )
    }

    fun buildAnalyticsSummary(
        banners: List<BannerContentEntity>,
        rows: List<BannerAnalyticsEntity>
    ): BannerAnalyticsSummary {
        val totalImpressions = maxOf(rows.sumOf { it.impressions }, banners.sumOf { it.impressionsCount })
        val totalClicks = maxOf(rows.sumOf { it.clicks }, banners.sumOf { it.clicksCount })
        val totalUniqueViews = maxOf(rows.sumOf { it.uniqueViews }, banners.sumOf { it.uniqueViewsCount })
        val totalDismissals = rows.sumOf { it.dismissals }
        val ctr = if (totalImpressions > 0) (totalClicks.toFloat() / totalImpressions.toFloat()) * 100f else 0f

        val groupedByDate = rows
            .groupBy { it.dateKey }
            .entries
            .sortedByDescending { it.key }
            .take(14)
            .map { (dateKey, entries) ->
                BannerDailyStat(
                    dateKey = dateKey,
                    impressions = entries.sumOf { it.impressions },
                    uniqueViews = entries.sumOf { it.uniqueViews },
                    clicks = entries.sumOf { it.clicks }
                )
            }

        val perBanner = banners.sortedByDescending { it.impressionsCount * 2 + it.clicksCount * 5 }

        return BannerAnalyticsSummary(
            totalImpressions = totalImpressions,
            totalClicks = totalClicks,
            totalUniqueViews = totalUniqueViews,
            totalDismissals = totalDismissals,
            overallCtrPercent = ctr,
            dailyStats = groupedByDate,
            perBannerStats = perBanner
        )
    }
}

object BannerImageHelper {
    private const val MAX_DIMENSION_PX = 720
    private const val MAX_RAW_IMAGE_BYTES = 72_000

    fun processPickedImageToDataUriAndCache(context: Context, uri: Uri, bannerKeyHint: String): String? {
        return try {
            val appCtx = context.applicationContext
            val input = appCtx.contentResolver.openInputStream(uri) ?: return null
            val rawBytes = input.use { it.readBytes() }
            if (rawBytes.isEmpty()) return null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
            var sampleSize = 1
            while (bounds.outWidth / sampleSize > MAX_DIMENSION_PX ||
                bounds.outHeight / sampleSize > MAX_DIMENSION_PX
            ) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts) ?: return null

            val ratio = minOf(
                MAX_DIMENSION_PX.toFloat() / decoded.width.coerceAtLeast(1),
                MAX_DIMENSION_PX.toFloat() / decoded.height.coerceAtLeast(1),
                1.0f
            )
            val scaled = if (ratio < 1.0f) {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * ratio).toInt().coerceAtLeast(1),
                    (decoded.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                decoded
            }

            var quality = 82
            val outStream = ByteArrayOutputStream()
            do {
                outStream.reset()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, outStream)
                quality -= 10
            } while (outStream.size() > MAX_RAW_IMAGE_BYTES && quality >= 30)

            val compressedBytes = outStream.toByteArray()
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()

            val dir = File(appCtx.filesDir, "banner_images").apply { if (!exists()) mkdirs() }
            val safeName = bannerKeyHint.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "banner" }
            val localFile = File(dir, "${safeName}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(localFile).use { it.write(compressedBytes) }

            val base64 = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64"
        } catch (_: Exception) {
            null
        }
    }

    fun resolveCoilModel(imageUrl: String?): Any? {
        val raw = imageUrl?.trim().orEmpty()
        if (raw.isEmpty()) return null
        if (raw.startsWith("data:image/")) {
            val commaIdx = raw.indexOf(',')
            if (commaIdx > 0 && commaIdx < raw.length - 1) {
                return try {
                    Base64.decode(raw.substring(commaIdx + 1), Base64.DEFAULT)
                } catch (_: Exception) {
                    null
                }
            }
            return null
        }
        if (raw.startsWith("/") && File(raw).exists()) {
            return File(raw)
        }
        return raw
    }
}
