package com.example

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

enum class AdminRole(val displayName: String, val level: Int) {
    SUPER_ADMIN("Super Admin", 5),
    ADMIN("Admin", 4),
    DATA_MANAGER("Data Manager", 3),
    EDITOR("Editor", 3),
    SUPPORT_AGENT("Support Agent", 2),
    ANALYST("Analyst", 1),
    VIEWER("Viewer", 1),
    USER("Standard User", 0),
    BLOCKED("Blocked", -1);

    companion object {
        fun fromString(value: String): AdminRole {
            return entries.find { it.name.equals(value, ignoreCase = true) || it.displayName.equals(value, ignoreCase = true) }
                ?: USER
        }
    }
}

enum class AccountStatus(val label: String) {
    ACTIVE("Active"),
    SUSPENDED("Suspended"),
    BLOCKED("Blocked"),
    INACTIVE("Inactive"),
    PENDING("Pending Verification"),
    DELETED("Deleted");

    companion object {
        fun fromString(value: String): AccountStatus {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: ACTIVE
        }
    }
}

object SecurityHasher {
    private const val SALT = "NeuroPulse_v2_SecuritySalt_2026"

    fun hashPassword(rawPassword: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("$SALT:$rawPassword".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPassword(rawPassword: String, storedHash: String): Boolean {
        return MessageDigest.isEqual(
            hashPassword(rawPassword).toByteArray(Charsets.UTF_8),
            storedHash.toByteArray(Charsets.UTF_8)
        )
    }

    fun hashOtp(normalizedEmail: String, otpCode: String, challengeId: String): String {
        val cleanEmail = normalizedEmail.trim().lowercase()
        val cleanOtp = otpCode.trim()
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("$SALT:OTP_V2:$cleanEmail:$challengeId:$cleanOtp".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyOtpHash(normalizedEmail: String, otpCode: String, challengeId: String, expectedHash: String): Boolean {
        val computed = hashOtp(normalizedEmail, otpCode, challengeId)
        return MessageDigest.isEqual(
            computed.toByteArray(Charsets.UTF_8),
            expectedHash.toByteArray(Charsets.UTF_8)
        )
    }
}

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["email"]),
        Index(value = ["username"]),
        Index(value = ["role"]),
        Index(value = ["status"]),
        Index(value = ["lastLoginAt"])
    ]
)
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val email: String,
    val timestamp: Long = System.currentTimeMillis(),
    val username: String = email.substringBefore("@"),
    val passwordHash: String = SecurityHasher.hashPassword("1234"),
    val mobile: String = "+91 98765 43210",
    val role: String = AdminRole.USER.name,
    val status: String = AccountStatus.ACTIVE.name,
    val authProvider: String = "EMAIL", // EMAIL, GOOGLE, EMAIL,GOOGLE
    val customPermissions: String = "", // Comma-separated individual AdminPermission overrides
    val subscriptionPlan: String = "Pro Rehab",
    val twoFactorEnabled: Boolean = false,
    val lastLoginAt: Long = System.currentTimeMillis(),
    val loginCount: Int = 1,
    val deviceId: String = "NG-ESP32-001",
    val notes: String = "",
    val photoUrl: String = "",
    val photoUpdatedAt: Long = 0L
)

@Entity(
    tableName = "login_history",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["username"]),
        Index(value = ["timestamp"])
    ]
)
data class LoginHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val username: String,
    val role: String,
    val success: Boolean,
    val ipAddress: String = "192.168.1.104",
    val deviceModel: String = "Android NeuroGrip Client",
    val failureReason: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val message: String,
    val targetAudience: String = "ALL_USERS", // ALL_USERS, ADMINS, PRO_USERS, SPECIFIC_USER
    val targetUsername: String = "",
    val category: String = "SYSTEM", // SYSTEM, AI, DEVICE, THERAPY, ALERT, PROMO
    val isScheduled: Boolean = false,
    val scheduledFor: Long = System.currentTimeMillis(),
    val deliveryStatus: String = "DELIVERED", // DELIVERED, SCHEDULED, TEST_SENT, FAILED
    val sentBy: String = "hj123",
    val isUnread: Boolean = true,
    val recipientCount: Int = 1,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "remote_config")
data class RemoteConfigEntity(
    @PrimaryKey val id: Int = 1,
    val maintenanceMode: Boolean = false,
    val maintenanceMessage: String = "NeuroGrip servers are undergoing scheduled maintenance. Please check back shortly.",
    val minSupportedVersionCode: Int = 1,
    val minSupportedVersionName: String = "1.0.0",
    val recommendedVersionName: String = "1.2.0",
    val forceUpdate: Boolean = false,
    val releaseNotes: String = "Improved EEG signal filtering, lower ESP32 servo latency, and enhanced safety cutoffs.",
    val featureAiAssistant: Boolean = true,
    val featureHandControl: Boolean = true,
    val featureGestureStudio: Boolean = true,
    val featureTelemetryStream: Boolean = true,
    val featureOtaUpdates: Boolean = true,
    val featureBiometricAuth: Boolean = false,
    val primaryThemeHex: String = "#2F80ED",
    val darkModeDefault: Boolean = false,
    val maxGripForceLimitN: Float = 15.0f,
    val telemetryIntervalMs: Int = 150,
    val defaultEsp32Ip: String = "192.168.4.1",
    val sessionTimeoutMinutes: Int = 30,
    val rateLimitRequestsPerMin: Int = 120,
    val enforceTwoFactorForAdmins: Boolean = false,
    val updatedBy: String = "hj123",
    val updatedAt: Long = System.currentTimeMillis(),
    val configVersion: Int = 1
)

@Entity(tableName = "config_history")
data class ConfigHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val configVersion: Int,
    val summary: String,
    val snapshotJson: String,
    val publishedBy: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "banners_content",
    indices = [
        Index(value = ["bannerKey"]),
        Index(value = ["displayPosition"]),
        Index(value = ["isActive", "isPublic", "isPublished", "isDraft"])
    ]
)
data class BannerContentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bannerKey: String = "",
    val title: String,
    val subtitle: String,
    val description: String = "",
    val bannerType: String = "ANNOUNCEMENT", // ANNOUNCEMENT, CLINICAL_TIP, PROMOTION, WARNING
    val iconKey: String = "campaign",
    val imageUrl: String = "",
    val thumbnailUrl: String = "",
    val ctaEnabled: Boolean = true,
    val ctaText: String = "Learn More",
    val ctaActionType: String = "OPEN_SCREEN", // NONE, OPEN_URL, OPEN_SCREEN, OPEN_CATEGORY, OPEN_PRODUCT, SHOW_POPUP, CALL, WHATSAPP
    val ctaTargetValue: String = "reports",
    val targetRoute: String = "reports",
    val backgroundColorHex: String = "#EFF6FF",
    val textColorHex: String = "#1E3A8A",
    val buttonColorHex: String = "#1D4ED8",
    val buttonTextColorHex: String = "#FFFFFF",
    val borderColorHex: String = "#93C5FD",
    val isActive: Boolean = true,
    val isPublic: Boolean = true,
    val isPublished: Boolean = true,
    val isDraft: Boolean = false,
    val clinicalReviewStatus: String = "APPROVED", // NOT_REQUIRED, PENDING_REVIEW, APPROVED, REJECTED
    val clinicalApproved: Boolean = true,
    val reviewedBy: String = "hj123",
    val priority: Int = 1,
    val displayPosition: String = "HOME_TOP", // HOME_TOP, HOME_SLIDER, HOME_MIDDLE, HOME_BOTTOM, TOP_STICKY, BOTTOM_STICKY, FLOATING, POPUP, CATEGORY, PRODUCT_DETAILS
    val startAtMillis: Long = 0L,
    val endAtMillis: Long = 0L,
    val timezoneId: String = "Asia/Kolkata",
    val targetAudience: String = "PUBLIC_ALL", // PUBLIC_ALL, SPECIFIC_USERS, USER_ROLES, NEW_USERS, EXISTING_USERS
    val targetValuesCsv: String = "",
    val dismissible: Boolean = true,
    val reShowAfterDismissHours: Int = 24,
    val impressionsCount: Int = 0,
    val uniqueViewsCount: Int = 0,
    val clicksCount: Int = 0,
    val createdBy: String = "hj123",
    val updatedBy: String = "hj123",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "banner_analytics_daily",
    indices = [
        Index(value = ["bannerId", "dateKey"], unique = true),
        Index(value = ["dateKey"])
    ]
)
data class BannerAnalyticsEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bannerId: Int,
    val bannerKey: String,
    val dateKey: String, // yyyy-MM-dd
    val impressions: Int = 0,
    val uniqueViews: Int = 0,
    val clicks: Int = 0,
    val dismissals: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "support_tickets")
data class SupportTicketEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val username: String,
    val userEmail: String,
    val subject: String,
    val message: String,
    val category: String = "HARDWARE", // HARDWARE, CALIBRATION, ACCOUNT, BILLING, BUG
    val priority: String = "HIGH", // LOW, MEDIUM, HIGH, CRITICAL
    val status: String = "OPEN", // OPEN, IN_PROGRESS, RESOLVED, CLOSED
    val assignedTo: String = "hj123",
    val adminReply: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "audit_logs",
    indices = [
        Index(value = ["actorUsername"]),
        Index(value = ["actionType"]),
        Index(value = ["timestamp"])
    ]
)
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val actorUsername: String,
    val actorRole: String,
    val actionType: String, // LOGIN, USER_UPDATE, USER_SUSPEND, USER_DELETE, ROLE_CHANGE, CONFIG_PUBLISH, NOTIFICATION_SEND, BACKUP_RESTORE, SECURITY_POLICY
    val targetEntity: String,
    val details: String,
    val severity: String = "INFO", // INFO, WARNING, CRITICAL
    val ipAddress: String = "10.0.2.15",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "subscriptions")
data class SubscriptionRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val username: String,
    val planName: String, // Free Basic, Pro Rehab, Clinical Enterprise
    val amountUsd: Double,
    val billingCycle: String = "MONTHLY", // MONTHLY, ANNUAL
    val status: String = "ACTIVE", // ACTIVE, PAST_DUE, CANCELED, REFUNDED
    val paymentMethod: String = "Visa •••• 4242",
    val nextBillingDate: Long = System.currentTimeMillis() + 30L * 24 * 3600 * 1000,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "backups")
data class BackupSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    val createdBy: String,
    val userCount: Int,
    val ticketCount: Int,
    val configVersion: Int,
    val payloadJson: String,
    val sizeBytes: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "sdui_config_cache")
data class SduiConfigCacheEntity(
    @PrimaryKey val configKey: String = SduiAllowlists.ACTIVE_CONFIG_KEY,
    val schemaVersion: Int = 1,
    val configVersion: Int = 1,
    val payloadJson: String,
    val contentSha256: String,
    val requiredImageUrlsJson: String = "[]",
    val updatedBy: String = "system",
    val updatedAt: Long = 0L,
    val syncedAt: Long = System.currentTimeMillis(),
    val changeSummary: String = ""
)

@Entity(tableName = "sdui_image_cache")
data class SduiImageCacheEntity(
    @PrimaryKey val urlSha256: String,
    val originalUrl: String,
    val localFilePath: String,
    val byteSize: Long,
    val downloadedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sdui_config_history")
data class SduiConfigHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val configVersion: Int,
    val schemaVersion: Int,
    val summary: String,
    val payloadJson: String,
    val contentSha256: String,
    val publishedBy: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE status != 'DELETED' ORDER BY timestamp DESC")
    fun getAllUsers(): Flow<List<User>>

    @Query("SELECT * FROM users ORDER BY timestamp DESC")
    fun getAllUsersIncludingDeleted(): Flow<List<User>>

    @Query("SELECT * FROM users ORDER BY timestamp DESC")
    suspend fun getAllUsersSnapshot(): List<User>

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: Int): User?

    @Query("SELECT * FROM users WHERE LOWER(username) = LOWER(:identifier) OR LOWER(email) = LOWER(:identifier) LIMIT 1")
    suspend fun getUserByIdentifier(identifier: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Update
    suspend fun updateUser(user: User)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteUserById(id: Int)
}

@Dao
interface AdminDao {
    // Login History
    @Query("SELECT * FROM login_history ORDER BY timestamp DESC LIMIT 100")
    fun getAllLoginHistory(): Flow<List<LoginHistoryEntity>>

    @Query("SELECT * FROM login_history WHERE userId = :userId ORDER BY timestamp DESC LIMIT 50")
    fun getLoginHistoryForUser(userId: Int): Flow<List<LoginHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoginHistory(entry: LoginHistoryEntity)

    // Notifications
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    suspend fun getAllNotificationsSnapshot(): List<NotificationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity): Long

    @Query("UPDATE notifications SET isUnread = 0")
    suspend fun markAllNotificationsRead()

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteNotificationById(id: Int)

    @Query("DELETE FROM notifications")
    suspend fun clearAllNotifications()

    // Remote Config
    @Query("SELECT * FROM remote_config WHERE id = 1 LIMIT 1")
    fun getRemoteConfig(): Flow<RemoteConfigEntity?>

    @Query("SELECT * FROM remote_config WHERE id = 1 LIMIT 1")
    suspend fun getRemoteConfigSnapshot(): RemoteConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRemoteConfig(config: RemoteConfigEntity)

    @Query("SELECT * FROM config_history ORDER BY timestamp DESC")
    fun getConfigHistory(): Flow<List<ConfigHistoryEntity>>

    @Query("SELECT * FROM config_history ORDER BY timestamp DESC")
    suspend fun getConfigHistorySnapshot(): List<ConfigHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfigHistory(history: ConfigHistoryEntity)

    // Banners & Content
    @Query("SELECT * FROM banners_content ORDER BY priority DESC, updatedAt DESC")
    fun getAllBanners(): Flow<List<BannerContentEntity>>

    @Query("SELECT * FROM banners_content WHERE isActive = 1 AND isPublic = 1 AND isPublished = 1 AND isDraft = 0 AND clinicalApproved = 1 ORDER BY priority DESC, updatedAt DESC")
    fun getActiveBanners(): Flow<List<BannerContentEntity>>

    @Query("SELECT * FROM banners_content ORDER BY priority DESC, updatedAt DESC")
    suspend fun getAllBannersSnapshot(): List<BannerContentEntity>

    @Query("SELECT * FROM banners_content WHERE id = :id LIMIT 1")
    suspend fun getBannerById(id: Int): BannerContentEntity?

    @Query("SELECT * FROM banners_content WHERE bannerKey = :bannerKey LIMIT 1")
    suspend fun getBannerByKey(bannerKey: String): BannerContentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBanner(banner: BannerContentEntity): Long

    @Update
    suspend fun updateBanner(banner: BannerContentEntity)

    @Query("DELETE FROM banners_content WHERE id = :id")
    suspend fun deleteBanner(id: Int)

    @Query("DELETE FROM banners_content WHERE bannerKey = :bannerKey")
    suspend fun deleteBannerByKey(bannerKey: String)

    // Banner Analytics
    @Query("SELECT * FROM banner_analytics_daily ORDER BY dateKey DESC, impressions DESC")
    fun observeAllBannerAnalytics(): Flow<List<BannerAnalyticsEntity>>

    @Query("SELECT * FROM banner_analytics_daily ORDER BY dateKey DESC")
    suspend fun getAllBannerAnalyticsSnapshot(): List<BannerAnalyticsEntity>

    @Query("SELECT * FROM banner_analytics_daily WHERE bannerId = :bannerId AND dateKey = :dateKey LIMIT 1")
    suspend fun getBannerDailyStats(bannerId: Int, dateKey: String): BannerAnalyticsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBannerDailyStats(entry: BannerAnalyticsEntity): Long

    @Query("DELETE FROM banner_analytics_daily WHERE bannerId = :bannerId")
    suspend fun deleteAnalyticsForBanner(bannerId: Int)

    // Support Tickets
    @Query("SELECT * FROM support_tickets ORDER BY updatedAt DESC")
    fun getAllTickets(): Flow<List<SupportTicketEntity>>

    @Query("SELECT * FROM support_tickets ORDER BY updatedAt DESC")
    suspend fun getAllTicketsSnapshot(): List<SupportTicketEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTicket(ticket: SupportTicketEntity): Long

    @Update
    suspend fun updateTicket(ticket: SupportTicketEntity)

    @Query("DELETE FROM support_tickets WHERE id = :id")
    suspend fun deleteTicket(id: Int)

    // Audit Logs
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 250")
    fun getAllAuditLogs(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    suspend fun getAllAuditLogsSnapshot(): List<AuditLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity)

    // Subscriptions
    @Query("SELECT * FROM subscriptions ORDER BY updatedAt DESC")
    fun getAllSubscriptions(): Flow<List<SubscriptionRecordEntity>>

    @Query("SELECT * FROM subscriptions ORDER BY updatedAt DESC")
    suspend fun getAllSubscriptionsSnapshot(): List<SubscriptionRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(sub: SubscriptionRecordEntity): Long

    @Update
    suspend fun updateSubscription(sub: SubscriptionRecordEntity)

    // Backups
    @Query("SELECT * FROM backups ORDER BY timestamp DESC")
    fun getAllBackups(): Flow<List<BackupSnapshotEntity>>

    @Query("SELECT * FROM backups ORDER BY timestamp DESC")
    suspend fun getAllBackupsSnapshot(): List<BackupSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackup(backup: BackupSnapshotEntity): Long

    @Query("DELETE FROM backups WHERE id = :id")
    suspend fun deleteBackup(id: Int)
}

@Dao
interface SduiDao {
    @Query("SELECT * FROM sdui_config_cache WHERE configKey = :key LIMIT 1")
    fun observeActiveConfig(key: String = SduiAllowlists.ACTIVE_CONFIG_KEY): Flow<SduiConfigCacheEntity?>

    @Query("SELECT * FROM sdui_config_cache WHERE configKey = :key LIMIT 1")
    suspend fun getActiveConfigSnapshot(key: String = SduiAllowlists.ACTIVE_CONFIG_KEY): SduiConfigCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActiveConfig(entity: SduiConfigCacheEntity)

    @Query("SELECT * FROM sdui_image_cache")
    suspend fun getAllImageCacheSnapshots(): List<SduiImageCacheEntity>

    @Query("SELECT * FROM sdui_image_cache WHERE urlSha256 = :urlSha256 LIMIT 1")
    suspend fun getImageCacheByHash(urlSha256: String): SduiImageCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertImageCache(entity: SduiImageCacheEntity)

    @Query("SELECT * FROM sdui_config_history ORDER BY configVersion DESC, timestamp DESC LIMIT 40")
    fun observeSduiHistory(): Flow<List<SduiConfigHistoryEntity>>

    @Query("SELECT * FROM sdui_config_history ORDER BY configVersion DESC, timestamp DESC LIMIT 40")
    suspend fun getSduiHistorySnapshot(): List<SduiConfigHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSduiHistory(entity: SduiConfigHistoryEntity)

    @Transaction
    suspend fun saveValidatedConfigAtomically(
        configEntity: SduiConfigCacheEntity,
        historyEntity: SduiConfigHistoryEntity?
    ) {
        upsertActiveConfig(configEntity)
        if (historyEntity != null) {
            insertSduiHistory(historyEntity)
        }
    }
}

@Database(
    entities = [
        User::class,
        LoginHistoryEntity::class,
        NotificationEntity::class,
        RemoteConfigEntity::class,
        ConfigHistoryEntity::class,
        BannerContentEntity::class,
        SupportTicketEntity::class,
        AuditLogEntity::class,
        SubscriptionRecordEntity::class,
        BackupSnapshotEntity::class,
        SduiConfigCacheEntity::class,
        SduiImageCacheEntity::class,
        SduiConfigHistoryEntity::class,
        BannerAnalyticsEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun adminDao(): AdminDao
    abstract fun sduiDao(): SduiDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN photoUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE users ADD COLUMN photoUpdatedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "neurogrip_unified.db"
                )
                    .addMigrations(MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                CoroutineScope(Dispatchers.IO).launch {
                    seedDatabaseIfNeeded(instance)
                }
                instance
            }
        }

        suspend fun seedDatabaseIfNeeded(db: AppDatabase) {
            val userDao = db.userDao()
            val adminDao = db.adminDao()

            // Ensure protected Super Admin hj195344@gmail.com exists and is active
            val now = System.currentTimeMillis()
            val primarySuperAdmin = userDao.getUserByIdentifier("hj195344@gmail.com")
                ?: userDao.getUserByIdentifier("hj195344")
            if (primarySuperAdmin == null) {
                val superAdminId = userDao.insertUser(
                    User(
                        name = "Harsh Joshi (Super Admin)",
                        email = "hj195344@gmail.com",
                        username = "hj195344",
                        passwordHash = SecurityHasher.hashPassword("12345"),
                        mobile = "+91 98250 11223",
                        role = AdminRole.SUPER_ADMIN.name,
                        status = AccountStatus.ACTIVE.name,
                        subscriptionPlan = "Clinical Enterprise",
                        twoFactorEnabled = true,
                        lastLoginAt = now - 300_000L,
                        loginCount = 1,
                        deviceId = "NG-SUPER-ADMIN-PRIMARY",
                        notes = "Protected Super Administrator account (hj195344@gmail.com)."
                    )
                ).toInt()
                adminDao.insertLoginHistory(
                    LoginHistoryEntity(
                        userId = superAdminId,
                        username = "hj195344",
                        role = AdminRole.SUPER_ADMIN.name,
                        success = true,
                        ipAddress = "10.0.2.2",
                        deviceModel = "NeuroPulse Admin Console",
                        timestamp = now - 300_000L
                    )
                )
            } else if (
                !primarySuperAdmin.email.equals("hj195344@gmail.com", ignoreCase = true) ||
                primarySuperAdmin.role != AdminRole.SUPER_ADMIN.name ||
                primarySuperAdmin.status != AccountStatus.ACTIVE.name
            ) {
                userDao.updateUser(
                    primarySuperAdmin.copy(
                        email = "hj195344@gmail.com",
                        role = AdminRole.SUPER_ADMIN.name,
                        status = AccountStatus.ACTIVE.name,
                        twoFactorEnabled = true
                    )
                )
            }

            // Ensure Super Admin alias (hj195345@gmail.com / hj123) exists and is updated in-place without duplicates
            val existingAdmin = userDao.getUserByIdentifier("hj195345@gmail.com")
                ?: userDao.getUserByIdentifier("hj123")
            if (existingAdmin == null) {
                val adminId = userDao.insertUser(
                    User(
                        name = "Harsh Joshi (Super Admin)",
                        email = "hj195345@gmail.com",
                        username = "hj123",
                        passwordHash = SecurityHasher.hashPassword("12345"),
                        mobile = "+91 98250 11223",
                        role = AdminRole.SUPER_ADMIN.name,
                        status = AccountStatus.ACTIVE.name,
                        subscriptionPlan = "Clinical Enterprise",
                        twoFactorEnabled = true,
                        lastLoginAt = now - 600_000L,
                        loginCount = 42,
                        deviceId = "NG-ADMIN-MASTER",
                        notes = "Primary platform owner and super administrator (hj195345@gmail.com)."
                    )
                ).toInt()

                adminDao.insertLoginHistory(
                    LoginHistoryEntity(
                        userId = adminId,
                        username = "hj123",
                        role = AdminRole.SUPER_ADMIN.name,
                        success = true,
                        ipAddress = "10.0.2.2",
                        deviceModel = "Pixel 9 Pro (Admin Console)",
                        timestamp = now - 600_000L
                    )
                )
            } else if (!existingAdmin.email.equals("hj195345@gmail.com", ignoreCase = true) ||
                existingAdmin.role != AdminRole.SUPER_ADMIN.name ||
                !existingAdmin.twoFactorEnabled
            ) {
                userDao.updateUser(
                    existingAdmin.copy(
                        email = "hj195345@gmail.com",
                        role = AdminRole.SUPER_ADMIN.name,
                        status = AccountStatus.ACTIVE.name,
                        twoFactorEnabled = true
                    )
                )
            }

            // Remove any legacy hardcoded demo accounts if present (strictly preserving hj195344@gmail.com and hj195345@gmail.com / hj123)
            val legacyDemoUsernames = setOf("drmehta", "priya_ed", "rohan_sup", "ananya_an", "vikram")
            val legacyDemoEmails = setOf(
                "a.mehta@neurogrip.io",
                "priya.editor@neurogrip.io",
                "rohan.support@neurogrip.io",
                "ananya.analyst@neurogrip.io",
                "vikram.desai@gmail.com"
            )
            userDao.getAllUsersSnapshot().forEach { u ->
                val isProtectedSuperAdmin =
                    u.email.equals("hj195344@gmail.com", ignoreCase = true) ||
                        u.email.equals("hj195345@gmail.com", ignoreCase = true) ||
                        u.username.equals("hj195344", ignoreCase = true) ||
                        u.username.equals("hj123", ignoreCase = true)
                if (!isProtectedSuperAdmin &&
                    (u.username.lowercase() in legacyDemoUsernames || u.email.lowercase() in legacyDemoEmails)
                ) {
                    userDao.deleteUserById(u.id)
                }
            }

            // Seed Remote Config
            if (adminDao.getRemoteConfigSnapshot() == null) {
                val initialConfig = RemoteConfigEntity()
                adminDao.saveRemoteConfig(initialConfig)
                adminDao.insertConfigHistory(
                    ConfigHistoryEntity(
                        configVersion = 1,
                        summary = "Initial production baseline configuration",
                        snapshotJson = """{"version":1,"maintenanceMode":false,"minSupportedVersionCode":1,"featureAiAssistant":true,"featureHandControl":true}""",
                        publishedBy = "hj123"
                    )
                )
            }

            // Seed Server-Driven UI Baseline (v1) if not yet cached
            val sduiDao = db.sduiDao()
            if (sduiDao.getActiveConfigSnapshot() == null) {
                val baseline = SduiDashboardConfig.baselineDefault()
                val json = SduiJsonCodec.encode(baseline)
                val hash = SduiJsonCodec.sha256(json)
                val urlsJson = org.json.JSONArray(baseline.requiredImageUrls()).toString()
                sduiDao.saveValidatedConfigAtomically(
                    configEntity = SduiConfigCacheEntity(
                        configKey = SduiAllowlists.ACTIVE_CONFIG_KEY,
                        schemaVersion = baseline.schemaVersion,
                        configVersion = baseline.configVersion,
                        payloadJson = json,
                        contentSha256 = hash,
                        requiredImageUrlsJson = urlsJson,
                        updatedBy = baseline.updatedBy,
                        updatedAt = baseline.updatedAt,
                        syncedAt = System.currentTimeMillis(),
                        changeSummary = baseline.changeSummary
                    ),
                    historyEntity = SduiConfigHistoryEntity(
                        configVersion = baseline.configVersion,
                        schemaVersion = baseline.schemaVersion,
                        summary = baseline.changeSummary,
                        payloadJson = json,
                        contentSha256 = hash,
                        publishedBy = baseline.updatedBy
                    )
                )
            }

            // Seed Banners
            if (adminDao.getAllBannersSnapshot().isEmpty()) {
                val now = System.currentTimeMillis()
                val tz = java.util.TimeZone.getDefault().id
                val b1Id = adminDao.insertBanner(
                    BannerContentEntity(
                        bannerKey = "banner_1",
                        title = "NeuroGrip Firmware v2.4 Ready",
                        subtitle = "Enhanced EEG attention lock & 30% faster servo response.",
                        description = "Connect your NeuroGrip prosthetic over BLE or local Wi-Fi and run a quick 5-finger sweep calibration in Hand Control.",
                        bannerType = "ANNOUNCEMENT",
                        iconKey = "campaign",
                        ctaEnabled = true,
                        ctaText = "Calibrate Now",
                        ctaActionType = "OPEN_SCREEN",
                        ctaTargetValue = "hand_control",
                        targetRoute = "hand_control",
                        backgroundColorHex = "#EFF6FF",
                        textColorHex = "#1E3A8A",
                        buttonColorHex = "#1D4ED8",
                        buttonTextColorHex = "#FFFFFF",
                        borderColorHex = "#93C5FD",
                        isActive = true,
                        isPublic = true,
                        isPublished = true,
                        isDraft = false,
                        clinicalReviewStatus = "NOT_REQUIRED",
                        clinicalApproved = true,
                        priority = 10,
                        displayPosition = "HOME_TOP",
                        timezoneId = tz,
                        targetAudience = "PUBLIC_ALL",
                        dismissible = true,
                        reShowAfterDismissHours = 24,
                        createdBy = "hj123",
                        updatedBy = "hj123",
                        createdAt = now - 3_600_000L,
                        updatedAt = now - 3_600_000L
                    )
                ).toInt()
                if (b1Id > 0) {
                    adminDao.updateBanner(
                        adminDao.getBannerById(b1Id)!!.copy(bannerKey = "banner_$b1Id")
                    )
                }

                val b2Id = adminDao.insertBanner(
                    BannerContentEntity(
                        bannerKey = "banner_2",
                        title = "Daily Rehab Clinical Tip",
                        subtitle = "Complete 15 mins of Gesture Studio grip exercises today.",
                        description = "Consistent pinch-and-release repetition helps stabilize surface EMG baselines and accelerates neuro-motor adaptation.",
                        bannerType = "CLINICAL_TIP",
                        iconKey = "medical",
                        ctaEnabled = true,
                        ctaText = "Open Studio",
                        ctaActionType = "OPEN_SCREEN",
                        ctaTargetValue = "gesture_studio",
                        targetRoute = "gesture_studio",
                        backgroundColorHex = "#ECFDF5",
                        textColorHex = "#065F46",
                        buttonColorHex = "#047857",
                        buttonTextColorHex = "#FFFFFF",
                        borderColorHex = "#6EE7B7",
                        isActive = true,
                        isPublic = true,
                        isPublished = true,
                        isDraft = false,
                        clinicalReviewStatus = "APPROVED",
                        clinicalApproved = true,
                        reviewedBy = "hj123",
                        priority = 5,
                        displayPosition = "HOME_SLIDER",
                        timezoneId = tz,
                        targetAudience = "PUBLIC_ALL",
                        dismissible = true,
                        reShowAfterDismissHours = 12,
                        createdBy = "priya_ed",
                        updatedBy = "hj123",
                        createdAt = now - 1_800_000L,
                        updatedAt = now - 1_800_000L
                    )
                ).toInt()
                if (b2Id > 0) {
                    adminDao.updateBanner(
                        adminDao.getBannerById(b2Id)!!.copy(bannerKey = "banner_$b2Id")
                    )
                }
            }

            // Seed Notifications
            if (adminDao.getAllNotificationsSnapshot().isEmpty()) {
                val now = System.currentTimeMillis()
                adminDao.insertNotification(
                    NotificationEntity(
                        title = "AI Assistant",
                        message = "New rehabilitation recommendation available for your grip profile.",
                        targetAudience = "ALL_USERS",
                        category = "AI",
                        deliveryStatus = "DELIVERED",
                        sentBy = "hj123",
                        isUnread = true,
                        recipientCount = 6,
                        timestamp = now - 120_000L
                    )
                )
                adminDao.insertNotification(
                    NotificationEntity(
                        title = "Device Connected",
                        message = "ESP32 NeuroGrip controller verified on local Wi-Fi.",
                        targetAudience = "ALL_USERS",
                        category = "DEVICE",
                        deliveryStatus = "DELIVERED",
                        sentBy = "system",
                        isUnread = false,
                        recipientCount = 6,
                        timestamp = now - 600_000L
                    )
                )
                adminDao.insertNotification(
                    NotificationEntity(
                        title = "Brain Calibration",
                        message = "EEG baseline calibration completed with 94% signal stability.",
                        targetAudience = "PRO_USERS",
                        category = "THERAPY",
                        deliveryStatus = "DELIVERED",
                        sentBy = "hj123",
                        isUnread = false,
                        recipientCount = 4,
                        timestamp = now - 3_600_000L
                    )
                )
            }

            // Seed Audit Logs
            if (adminDao.getAllAuditLogsSnapshot().isEmpty()) {
                val now = System.currentTimeMillis()
                adminDao.insertAuditLog(
                    AuditLogEntity(
                        actorUsername = "hj123",
                        actorRole = AdminRole.SUPER_ADMIN.name,
                        actionType = "SYSTEM_INIT",
                        targetEntity = "NeuroGrip Unified Database",
                        details = "Initialized RBAC roles, default remote config v1, and security policies.",
                        severity = "INFO",
                        timestamp = now - 3_600_000L
                    )
                )
                adminDao.insertAuditLog(
                    AuditLogEntity(
                        actorUsername = "hj123",
                        actorRole = AdminRole.SUPER_ADMIN.name,
                        actionType = "USER_SUSPEND",
                        targetEntity = "user:vikram",
                        details = "Suspended account due to abnormal ESP32 packet rate.",
                        severity = "WARNING",
                        timestamp = now - 1_800_000L
                    )
                )
            }
        }
    }
}
