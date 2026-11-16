package com.example

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class BannerRealtimeSyncStatus(
    val isListening: Boolean = false,
    val lastSyncedAtMillis: Long = 0L,
    val sourceLabel: String = "Offline Room Cache",
    val lastError: String? = null
)

class BannerSyncManager(
    context: Context,
    private val adminDao: AdminDao
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dismissPrefs = appContext.getSharedPreferences("banner_dismiss_prefs", Context.MODE_PRIVATE)
    private val impressionDedupSet = ConcurrentHashMap.newKeySet<String>()

    private val _syncStatus = MutableStateFlow(BannerRealtimeSyncStatus())
    val syncStatus: StateFlow<BannerRealtimeSyncStatus> = _syncStatus.asStateFlow()

    private val _dismissRevision = MutableStateFlow(0L)
    val dismissRevision: StateFlow<Long> = _dismissRevision.asStateFlow()

    @Volatile
    private var bannerListener: ListenerRegistration? = null

    @Volatile
    private var authListenerAttached = false

    @Volatile
    private var isAdminModeHint = false

    private val db: FirebaseFirestore? by lazy {
        try {
            val dbId = appContext.getString(R.string.firestore_database_id)
            FirebaseFirestore.getInstance(dbId)
        } catch (e: Exception) {
            Log.w("BannerSyncManager", "Firestore init unavailable: ${e.message}")
            null
        }
    }

    fun startRealtimeSync(isAdminUser: Boolean = false) {
        isAdminModeHint = isAdminUser
        val auth = try {
            FirebaseAuth.getInstance()
        } catch (_: Exception) {
            null
        }
        if (auth != null && !authListenerAttached) {
            authListenerAttached = true
            auth.addAuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                if (user != null) {
                    attachFirestoreListener(useAdminQuery = isAdminModeHint)
                } else {
                    detachFirestoreListener()
                }
            }
        }
        if (auth?.currentUser != null) {
            attachFirestoreListener(useAdminQuery = isAdminUser)
        } else {
            _syncStatus.value = _syncStatus.value.copy(
                isListening = false,
                sourceLabel = "Offline Room Cache"
            )
        }
    }

    fun detachFirestoreListener() {
        bannerListener?.remove()
        bannerListener = null
        _syncStatus.value = _syncStatus.value.copy(
            isListening = false,
            sourceLabel = "Offline Room Cache"
        )
    }

    private fun attachFirestoreListener(useAdminQuery: Boolean) {
        val firestore = db ?: return
        bannerListener?.remove()

        val query: Query = if (useAdminQuery) {
            firestore.collection("banners_content")
        } else {
            firestore.collection("banners_content")
                .whereEqualTo("isActive", true)
                .whereEqualTo("isPublic", true)
                .whereEqualTo("isPublished", true)
                .whereEqualTo("isDraft", false)
                .whereEqualTo("clinicalApproved", true)
        }

        _syncStatus.value = _syncStatus.value.copy(
            isListening = true,
            sourceLabel = if (useAdminQuery) "Firestore Live (Admin)" else "Firestore Live"
        )

        bannerListener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("BannerSyncManager", "Banner snapshot error (${error.code}): ${error.message}")
                if (useAdminQuery && error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    // Gracefully fall back to the public live banner query
                    attachFirestoreListener(useAdminQuery = false)
                } else {
                    _syncStatus.value = _syncStatus.value.copy(
                        isListening = false,
                        sourceLabel = "Offline Room Cache",
                        lastError = error.message
                    )
                }
                return@addSnapshotListener
            }

            if (snapshot != null) {
                scope.launch {
                    reconcileCloudSnapshotsToRoom(snapshot.documents, replaceAll = useAdminQuery)
                    _syncStatus.value = _syncStatus.value.copy(
                        isListening = true,
                        lastSyncedAtMillis = System.currentTimeMillis(),
                        sourceLabel = if (useAdminQuery) "Firestore Live (Admin)" else "Firestore Live",
                        lastError = null
                    )
                }
            }
        }
    }

    private suspend fun reconcileCloudSnapshotsToRoom(
        docs: List<DocumentSnapshot>,
        replaceAll: Boolean
    ) {
        if (docs.isEmpty() && !replaceAll) return
        val existingList = adminDao.getAllBannersSnapshot()
        val seenKeys = HashSet<String>()

        for (doc in docs) {
            val mapped = snapshotToEntity(doc, existingList) ?: continue
            val key = BannerEligibilityEngine.effectiveBannerKey(mapped)
            seenKeys.add(key)
            val existingMatch = existingList.find {
                (mapped.id > 0 && it.id == mapped.id) ||
                    BannerEligibilityEngine.effectiveBannerKey(it) == key
            }
            if (existingMatch != null) {
                val preservedImpressions = maxOf(existingMatch.impressionsCount, mapped.impressionsCount)
                val preservedUnique = maxOf(existingMatch.uniqueViewsCount, mapped.uniqueViewsCount)
                val preservedClicks = maxOf(existingMatch.clicksCount, mapped.clicksCount)
                adminDao.insertBanner(
                    mapped.copy(
                        id = existingMatch.id,
                        bannerKey = existingMatch.bannerKey.ifBlank { mapped.bannerKey },
                        impressionsCount = preservedImpressions,
                        uniqueViewsCount = preservedUnique,
                        clicksCount = preservedClicks
                    )
                )
            } else {
                adminDao.insertBanner(mapped)
            }
        }

        if (replaceAll && docs.isNotEmpty()) {
            existingList.forEach { local ->
                val localKey = BannerEligibilityEngine.effectiveBannerKey(local)
                if (localKey.startsWith("banner_") && localKey !in seenKeys) {
                    adminDao.deleteBanner(local.id)
                }
            }
        }
    }

    suspend fun syncBannerToCloud(banner: BannerContentEntity, isCreate: Boolean): Result<String> =
        withContext(Dispatchers.IO) {
            val firestore = db ?: return@withContext Result.failure(IllegalStateException("Firestore unavailable"))
            if (FirebaseAuth.getInstance().currentUser == null) {
                return@withContext Result.failure(IllegalStateException("Not authenticated with Firebase"))
            }
            val key = BannerEligibilityEngine.effectiveBannerKey(banner)
            val docRef = firestore.collection("banners_content").document(key)
            try {
                val existingSnap = docRef.get().await()
                val existingCreated = existingSnap.getTimestamp("createdAt")
                val payload = entityToFirestoreMap(banner, key).toMutableMap()
                if (existingSnap.exists() && existingCreated != null && !isCreate) {
                    payload["createdAt"] = existingCreated
                    payload["updatedAt"] = FieldValue.serverTimestamp()
                    docRef.set(payload).await()
                } else {
                    payload["createdAt"] = existingCreated ?: FieldValue.serverTimestamp()
                    payload["updatedAt"] = FieldValue.serverTimestamp()
                    docRef.set(payload).await()
                }
                Result.success(key)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("FirestoreError", """{"operationType":"write","path":"${docRef.path}","error":"${e.message}"}""")
                Result.failure(e)
            }
        }

    suspend fun deleteBannerFromCloud(bannerKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        val firestore = db ?: return@withContext Result.failure(IllegalStateException("Firestore unavailable"))
        if (FirebaseAuth.getInstance().currentUser == null) {
            return@withContext Result.failure(IllegalStateException("Not authenticated"))
        }
        val cleanKey = bannerKey.trim()
        if (cleanKey.isEmpty()) return@withContext Result.success(Unit)
        val docRef = firestore.collection("banners_content").document(cleanKey)
        try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FirestoreError", """{"operationType":"delete","path":"${docRef.path}","error":"${e.message}"}""")
            Result.failure(e)
        }
    }

    suspend fun trackImpression(banner: BannerContentEntity, username: String, screenSlot: String) =
        withContext(Dispatchers.IO) {
            if (banner.id <= 0) return@withContext
            val bannerKey = BannerEligibilityEngine.effectiveBannerKey(banner)
            val dateKey = BannerTimezoneHelper.todayDateKey(banner.timezoneId)
            val sessionDedupKey = "${username.lowercase()}:$bannerKey:$dateKey"
            if (!impressionDedupSet.add(sessionDedupKey)) {
                return@withContext
            }

            val current = adminDao.getBannerById(banner.id) ?: banner
            val existingDaily = adminDao.getBannerDailyStats(current.id, dateKey)
            val uniquePrefKey = "seen_${username.lowercase()}_${bannerKey}_$dateKey"
            val isNewUniqueView = !dismissPrefs.getBoolean(uniquePrefKey, false)
            if (isNewUniqueView) {
                dismissPrefs.edit().putBoolean(uniquePrefKey, true).apply()
            }

            val updatedBanner = current.copy(
                impressionsCount = current.impressionsCount + 1,
                uniqueViewsCount = current.uniqueViewsCount + if (isNewUniqueView) 1 else 0
            )
            adminDao.updateBanner(updatedBanner)
            adminDao.upsertBannerDailyStats(
                (existingDaily ?: BannerAnalyticsEntity(
                    bannerId = current.id,
                    bannerKey = bannerKey,
                    dateKey = dateKey
                )).let {
                    it.copy(
                        impressions = it.impressions + 1,
                        uniqueViews = it.uniqueViews + if (isNewUniqueView) 1 else 0,
                        updatedAt = System.currentTimeMillis()
                    )
                }
            )
            appendCloudAnalyticsEvent(current.id, bannerKey, "IMPRESSION", username, dateKey, screenSlot)
        }

    suspend fun trackClick(banner: BannerContentEntity, username: String, screenSlot: String) =
        withContext(Dispatchers.IO) {
            if (banner.id <= 0) return@withContext
            val bannerKey = BannerEligibilityEngine.effectiveBannerKey(banner)
            val dateKey = BannerTimezoneHelper.todayDateKey(banner.timezoneId)
            val current = adminDao.getBannerById(banner.id) ?: banner
            val existingDaily = adminDao.getBannerDailyStats(current.id, dateKey)

            adminDao.updateBanner(
                current.copy(clicksCount = current.clicksCount + 1)
            )
            adminDao.upsertBannerDailyStats(
                (existingDaily ?: BannerAnalyticsEntity(
                    bannerId = current.id,
                    bannerKey = bannerKey,
                    dateKey = dateKey
                )).let {
                    it.copy(
                        clicks = it.clicks + 1,
                        updatedAt = System.currentTimeMillis()
                    )
                }
            )
            appendCloudAnalyticsEvent(current.id, bannerKey, "CLICK", username, dateKey, screenSlot)
        }

    suspend fun dismissBanner(banner: BannerContentEntity, username: String, screenSlot: String) =
        withContext(Dispatchers.IO) {
            val bannerKey = BannerEligibilityEngine.effectiveBannerKey(banner)
            val now = System.currentTimeMillis()
            val prefKey = "dismiss_${username.lowercase()}_$bannerKey"
            dismissPrefs.edit().putLong(prefKey, now).apply()
            _dismissRevision.value = now

            if (banner.id > 0) {
                val dateKey = BannerTimezoneHelper.todayDateKey(banner.timezoneId)
                val existingDaily = adminDao.getBannerDailyStats(banner.id, dateKey)
                adminDao.upsertBannerDailyStats(
                    (existingDaily ?: BannerAnalyticsEntity(
                        bannerId = banner.id,
                        bannerKey = bannerKey,
                        dateKey = dateKey
                    )).let {
                        it.copy(
                            dismissals = it.dismissals + 1,
                            updatedAt = now
                        )
                    }
                )
                appendCloudAnalyticsEvent(banner.id, bannerKey, "DISMISS", username, dateKey, screenSlot)
            }
        }

    fun clearDismissHistoryForBanner(bannerKey: String) {
        val editors = dismissPrefs.edit()
        dismissPrefs.all.keys
            .filter { it.startsWith("dismiss_") && it.endsWith("_$bannerKey") }
            .forEach { editors.remove(it) }
        editors.apply()
        _dismissRevision.value = System.currentTimeMillis()
    }

    fun getDismissedMapForUser(username: String): Map<String, Long> {
        val prefix = "dismiss_${username.lowercase()}_"
        val map = HashMap<String, Long>()
        dismissPrefs.all.forEach { (k, v) ->
            if (k.startsWith(prefix) && v is Long) {
                map[k.removePrefix(prefix)] = v
            }
        }
        return map
    }

    private suspend fun appendCloudAnalyticsEvent(
        bannerId: Int,
        bannerKey: String,
        eventType: String,
        username: String,
        dateKey: String,
        screenSlot: String
    ) {
        val firestore = db ?: return
        val uid = try {
            FirebaseAuth.getInstance().currentUser?.uid
        } catch (_: Exception) {
            null
        } ?: return

        val evtId = "evt_${bannerKey}_${eventType.lowercase()}_${System.currentTimeMillis()}_${(100..999).random()}"
        val payload = hashMapOf<String, Any>(
            "bannerId" to bannerId.toLong(),
            "bannerKey" to bannerKey,
            "eventType" to eventType,
            "userId" to uid,
            "username" to username.ifBlank { "user" },
            "dateKey" to dateKey,
            "screenSlot" to screenSlot.ifBlank { "HOME_TOP" },
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            firestore.collection("banner_analytics_events").document(evtId).set(payload).await()
        } catch (_: Exception) {
            // Non-blocking best-effort cloud telemetry append; Room already holds authoritative local tally
        }
    }

    fun entityToFirestoreMap(banner: BannerContentEntity, key: String): Map<String, Any> {
        val clinicalApproved = BannerEligibilityEngine.resolveClinicalApproved(banner)
        val type = BannerType.fromString(banner.bannerType)
        val effectiveStatus = if (!type.requiresClinicalReview) {
            "NOT_REQUIRED"
        } else {
            ClinicalReviewStatus.fromString(banner.clinicalReviewStatus).name
        }
        return linkedMapOf(
            "bannerKey" to key,
            "title" to banner.title.trim().take(160),
            "subtitle" to banner.subtitle.trim().take(400),
            "description" to banner.description.trim().take(2000),
            "bannerType" to type.name,
            "iconKey" to banner.iconKey.ifBlank { type.preset.iconKey }.take(64),
            "imageUrl" to banner.imageUrl.trim(),
            "thumbnailUrl" to banner.thumbnailUrl.trim(),
            "ctaEnabled" to banner.ctaEnabled,
            "ctaText" to banner.ctaText.trim().take(60),
            "ctaActionType" to BannerCtaAction.fromString(banner.ctaActionType).name,
            "ctaTargetValue" to banner.ctaTargetValue.trim().take(1024),
            "deepLinkRoute" to banner.targetRoute.ifBlank { banner.ctaTargetValue }.take(256),
            "targetScreen" to BannerPosition.fromString(banner.displayPosition).name,
            "displayPosition" to BannerPosition.fromString(banner.displayPosition).name,
            "backgroundHex" to BannerColorUtils.normalizeHexOrDefault(banner.backgroundColorHex, type.preset.backgroundHex),
            "textHex" to BannerColorUtils.normalizeHexOrDefault(banner.textColorHex, type.preset.textHex),
            "buttonHex" to BannerColorUtils.normalizeHexOrDefault(banner.buttonColorHex, type.preset.buttonHex),
            "buttonTextHex" to BannerColorUtils.normalizeHexOrDefault(banner.buttonTextColorHex, type.preset.buttonTextHex),
            "borderHex" to BannerColorUtils.normalizeHexOrDefault(banner.borderColorHex, type.preset.borderHex),
            "priority" to banner.priority.coerceIn(-100, 1000).toLong(),
            "isActive" to banner.isActive,
            "isPublic" to banner.isPublic,
            "isPublished" to banner.isPublished,
            "isDraft" to banner.isDraft,
            "clinicalReviewStatus" to effectiveStatus,
            "clinicalApproved" to clinicalApproved,
            "reviewedBy" to banner.reviewedBy.take(100),
            "startAtMillis" to banner.startAtMillis.coerceAtLeast(0L),
            "endAtMillis" to banner.endAtMillis.coerceAtLeast(0L),
            "timezoneId" to banner.timezoneId.ifBlank { "Asia/Kolkata" }.take(64),
            "targetAudience" to BannerTargetingMode.fromString(banner.targetAudience).name,
            "targetValuesCsv" to banner.targetValuesCsv.take(1000),
            "dismissible" to banner.dismissible,
            "reShowAfterDismissHours" to banner.reShowAfterDismissHours.coerceIn(0, 8760).toLong(),
            "impressionsCount" to banner.impressionsCount.coerceAtLeast(0).toLong(),
            "uniqueViewsCount" to banner.uniqueViewsCount.coerceAtLeast(0).toLong(),
            "clicksCount" to banner.clicksCount.coerceAtLeast(0).toLong(),
            "createdBy" to banner.createdBy.ifBlank { "hj123" }.take(100),
            "updatedBy" to banner.updatedBy.ifBlank { banner.createdBy.ifBlank { "hj123" } }.take(100)
        )
    }

    private fun snapshotToEntity(
        doc: DocumentSnapshot,
        existingList: List<BannerContentEntity>
    ): BannerContentEntity? {
        val title = doc.getString("title")?.trim().orEmpty()
        val subtitle = doc.getString("subtitle")?.trim().orEmpty()
        if (title.isEmpty() || subtitle.isEmpty()) return null

        val key = doc.getString("bannerKey")?.trim().orEmpty().ifBlank { doc.id }
        val numericId = key.removePrefix("banner_").toIntOrNull()
            ?: doc.id.removePrefix("banner_").toIntOrNull()
            ?: existingList.find { it.bannerKey == key }?.id
            ?: 0

        val bType = BannerType.fromString(doc.getString("bannerType"))
        val reviewStatus = doc.getString("clinicalReviewStatus")
            ?: if (bType == BannerType.CLINICAL_TIP) "APPROVED" else "NOT_REQUIRED"
        val clinicalApproved = doc.getBoolean("clinicalApproved")
            ?: (!bType.requiresClinicalReview || reviewStatus == "APPROVED")

        val createdAtMs = doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
        val updatedAtMs = doc.getTimestamp("updatedAt")?.toDate()?.time ?: createdAtMs
        val targetRouteCompat = doc.getString("deepLinkRoute") ?: doc.getString("ctaTargetValue") ?: "reports"

        return BannerContentEntity(
            id = numericId,
            bannerKey = key,
            title = title,
            subtitle = subtitle,
            description = doc.getString("description") ?: "",
            bannerType = bType.name,
            iconKey = doc.getString("iconKey") ?: bType.preset.iconKey,
            imageUrl = doc.getString("imageUrl") ?: "",
            thumbnailUrl = doc.getString("thumbnailUrl") ?: "",
            ctaEnabled = doc.getBoolean("ctaEnabled") ?: true,
            ctaText = doc.getString("ctaText") ?: "Learn More",
            ctaActionType = BannerCtaAction.fromString(doc.getString("ctaActionType")).name,
            ctaTargetValue = doc.getString("ctaTargetValue") ?: targetRouteCompat,
            targetRoute = targetRouteCompat,
            backgroundColorHex = BannerColorUtils.normalizeHexOrDefault(doc.getString("backgroundHex"), bType.preset.backgroundHex),
            textColorHex = BannerColorUtils.normalizeHexOrDefault(doc.getString("textHex"), bType.preset.textHex),
            buttonColorHex = BannerColorUtils.normalizeHexOrDefault(doc.getString("buttonHex"), bType.preset.buttonHex),
            buttonTextColorHex = BannerColorUtils.normalizeHexOrDefault(doc.getString("buttonTextHex"), bType.preset.buttonTextHex),
            borderColorHex = BannerColorUtils.normalizeHexOrDefault(doc.getString("borderHex"), bType.preset.borderHex),
            isActive = doc.getBoolean("isActive") ?: true,
            isPublic = doc.getBoolean("isPublic") ?: true,
            isPublished = doc.getBoolean("isPublished") ?: true,
            isDraft = doc.getBoolean("isDraft") ?: false,
            clinicalReviewStatus = reviewStatus,
            clinicalApproved = clinicalApproved,
            reviewedBy = doc.getString("reviewedBy") ?: "",
            priority = (doc.getLong("priority") ?: 1L).toInt(),
            displayPosition = BannerPosition.fromString(doc.getString("displayPosition") ?: doc.getString("targetScreen")).name,
            startAtMillis = doc.getLong("startAtMillis") ?: 0L,
            endAtMillis = doc.getLong("endAtMillis") ?: 0L,
            timezoneId = doc.getString("timezoneId") ?: "Asia/Kolkata",
            targetAudience = BannerTargetingMode.fromString(doc.getString("targetAudience")).name,
            targetValuesCsv = doc.getString("targetValuesCsv") ?: "",
            dismissible = doc.getBoolean("dismissible") ?: true,
            reShowAfterDismissHours = (doc.getLong("reShowAfterDismissHours") ?: 24L).toInt(),
            impressionsCount = (doc.getLong("impressionsCount") ?: 0L).toInt(),
            uniqueViewsCount = (doc.getLong("uniqueViewsCount") ?: 0L).toInt(),
            clicksCount = (doc.getLong("clicksCount") ?: 0L).toInt(),
            createdBy = doc.getString("createdBy") ?: "hj123",
            updatedBy = doc.getString("updatedBy") ?: "hj123",
            createdAt = createdAtMs,
            updatedAt = updatedAtMs
        )
    }
}
