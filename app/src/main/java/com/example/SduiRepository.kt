package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class SduiSyncState(
    val config: SduiDashboardConfig = SduiDashboardConfig.baselineDefault(),
    val activeVersion: Int = 1,
    val contentSha256: String = "",
    val isOfflineFallback: Boolean = false,
    val isSyncing: Boolean = false,
    val lastSyncedAt: Long = 0L,
    val lastSyncSource: String = "BASELINE", // BASELINE, CACHE, FIRESTORE, BACKEND, ADMIN_PUBLISH, ROLLBACK
    val lastValidationError: String? = null,
    val cachedImagePaths: Map<String, String> = emptyMap()
)

enum class SduiApplyStatus {
    UPDATED,
    UP_TO_DATE,
    REJECTED_INVALID,
    ABORTED_NETWORK_MID_SYNC
}

data class SduiApplyOutcome(
    val status: SduiApplyStatus,
    val activeVersion: Int,
    val message: String
)

interface SduiAssetFetcher {
    @Throws(IOException::class)
    suspend fun fetchToFile(url: String, destinationFile: File)
}

class OkHttpSduiAssetFetcher : SduiAssetFetcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Throws(IOException::class)
    override suspend fun fetchToFile(url: String, destinationFile: File) {
        if (!url.startsWith("https://")) {
            throw IOException("Refusing non-HTTPS image asset URL: $url")
        }
        val parent = destinationFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val tempFile = File(parent, "${destinationFile.name}.tmp")
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                tempFile.delete()
                throw IOException("HTTP ${response.code} downloading SDUI asset")
            }
            val body = response.body ?: throw IOException("Empty response body for SDUI asset")
            val bytes = body.bytes()
            if (bytes.isEmpty() || bytes.size > 5 * 1024 * 1024) {
                tempFile.delete()
                throw IOException("SDUI asset size invalid (${bytes.size} bytes)")
            }
            tempFile.writeBytes(bytes)
            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            if (!tempFile.renameTo(destinationFile)) {
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
            }
        }
    }
}

fun interface SduiConnectivityChecker {
    fun isOnline(): Boolean
}

class SduiRepository(
    private val context: Context,
    private val sduiDao: SduiDao,
    private val assetFetcher: SduiAssetFetcher = OkHttpSduiAssetFetcher(),
    private val connectivityChecker: SduiConnectivityChecker = SduiConnectivityChecker {
        defaultIsNetworkConnected(context)
    },
    private val imageDir: File = File(context.filesDir, "sdui_images"),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val mirrorPrefs = context.applicationContext.getSharedPreferences(
        "sdui_atomic_cache_prefs",
        Context.MODE_PRIVATE
    )
    private val syncMutex = Mutex()
    private var firestoreListener: ListenerRegistration? = null
    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    private var attachedAuth: FirebaseAuth? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var attachedFirestore: FirebaseFirestore? = null
    private var backendFetcher: (suspend () -> Result<String>)? = null

    private val _syncState = MutableStateFlow(loadInitialSynchronousState())
    val syncState: StateFlow<SduiSyncState> = _syncState.asStateFlow()

    init {
        if (!imageDir.exists()) {
            imageDir.mkdirs()
        }
        // Hydrate asynchronously from Room (canonical transactional store) if Room has newer valid snapshot
        scope.launch {
            hydrateFromRoomCache()
        }
    }

    private fun loadInitialSynchronousState(): SduiSyncState {
        val cachedJson = mirrorPrefs.getString(KEY_PAYLOAD_JSON, null)
        val cachedHash = mirrorPrefs.getString(KEY_CONTENT_SHA256, null)
        val lastSynced = mirrorPrefs.getLong(KEY_LAST_SYNCED_AT, 0L)
        val imagePaths = loadMirrorImagePaths()

        if (!cachedJson.isNullOrBlank()) {
            val validation = SduiSchemaValidator.validateJson(cachedJson)
            if (validation is SduiValidationResult.Valid &&
                (cachedHash.isNullOrBlank() || cachedHash == validation.sha256Hash)
            ) {
                return SduiSyncState(
                    config = validation.config,
                    activeVersion = validation.config.configVersion,
                    contentSha256 = validation.sha256Hash,
                    isOfflineFallback = !isCurrentlyOnline(),
                    isSyncing = false,
                    lastSyncedAt = lastSynced,
                    lastSyncSource = "CACHE",
                    lastValidationError = null,
                    cachedImagePaths = imagePaths
                )
            }
        }

        val baseline = SduiDashboardConfig.baselineDefault()
        val baselineJson = SduiJsonCodec.encode(baseline)
        return SduiSyncState(
            config = baseline,
            activeVersion = baseline.configVersion,
            contentSha256 = SduiJsonCodec.sha256(baselineJson),
            isOfflineFallback = !isCurrentlyOnline(),
            isSyncing = false,
            lastSyncedAt = 0L,
            lastSyncSource = "BASELINE",
            lastValidationError = null,
            cachedImagePaths = imagePaths
        )
    }

    private fun isCurrentlyOnline(): Boolean {
        return try {
            connectivityChecker.isOnline()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun hydrateFromRoomCache() {
        syncMutex.withLock {
            val cached = sduiDao.getActiveConfigSnapshot() ?: return@withLock
            val validation = SduiSchemaValidator.validateJson(cached.payloadJson)
            if (validation is SduiValidationResult.Valid &&
                (cached.contentSha256.isBlank() || cached.contentSha256 == validation.sha256Hash)
            ) {
                val roomImages = buildImagePathMapFromDao()
                // Only update if current state is still BASELINE or Room has strictly newer version
                val cur = _syncState.value
                if (cur.lastSyncSource == "BASELINE" ||
                    validation.config.configVersion > cur.activeVersion ||
                    (validation.config.configVersion == cur.activeVersion &&
                        validation.sha256Hash != cur.contentSha256)
                ) {
                    writeMirrorPrefsAtomically(validation.normalizedJson, validation.sha256Hash, cached.syncedAt, roomImages)
                    _syncState.value = SduiSyncState(
                        config = validation.config,
                        activeVersion = validation.config.configVersion,
                        contentSha256 = validation.sha256Hash,
                        isOfflineFallback = !isCurrentlyOnline(),
                        isSyncing = false,
                        lastSyncedAt = cached.syncedAt,
                        lastSyncSource = "CACHE",
                        lastValidationError = null,
                        cachedImagePaths = roomImages
                    )
                }
            }
        }
    }

    private fun isFirebaseAuthenticated(firestore: FirebaseFirestore?): Boolean {
        if (firestore == null) return false
        return try {
            FirebaseAuth.getInstance(firestore.app).currentUser != null
        } catch (_: Exception) {
            false
        }
    }

    private fun attachFirestoreListenerIfSignedIn(firestore: FirebaseFirestore) {
        if (firestoreListener != null || !isFirebaseAuthenticated(firestore)) return
        try {
            firestoreListener = firestore.collection("sdui_configs")
                .document(SduiAllowlists.ACTIVE_CONFIG_KEY)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        handleFirestoreError(
                            error,
                            OperationType.GET,
                            "sdui_configs/${SduiAllowlists.ACTIVE_CONFIG_KEY}"
                        )
                        _syncState.value = _syncState.value.copy(
                            isOfflineFallback = !isCurrentlyOnline(),
                            isSyncing = false
                        )
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        scope.launch {
                            processFirestoreSnapshot(snapshot)
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w("SduiRepository", "Could not attach Firestore SDUI listener: ${e.message}")
        }
    }

    private fun detachFirestoreListener() {
        firestoreListener?.remove()
        firestoreListener = null
    }

    fun startRealtimeSync(
        firestore: FirebaseFirestore?,
        backendJsonProvider: (suspend () -> Result<String>)? = null
    ) {
        attachedFirestore = firestore
        backendFetcher = backendJsonProvider

        if (firestore != null) {
            attachFirestoreListenerIfSignedIn(firestore)
            if (authStateListener == null) {
                try {
                    val fbAuth = FirebaseAuth.getInstance(firestore.app)
                    val listener = FirebaseAuth.AuthStateListener { auth ->
                        if (auth.currentUser != null) {
                            attachFirestoreListenerIfSignedIn(firestore)
                            if (isCurrentlyOnline()) {
                                scope.launch {
                                    syncLatestFromRemote()
                                }
                            }
                        } else {
                            detachFirestoreListener()
                        }
                    }
                    fbAuth.addAuthStateListener(listener)
                    attachedAuth = fbAuth
                    authStateListener = listener
                } catch (_: Exception) {
                    // Safe fallback if FirebaseAuth is unavailable in isolated tests
                }
            }
        }

        registerConnectivityObserver()

        if (isCurrentlyOnline()) {
            scope.launch {
                syncLatestFromRemote()
            }
        }
    }

    fun stopRealtimeSync() {
        detachFirestoreListener()
        authStateListener?.let { listener ->
            try {
                attachedAuth?.removeAuthStateListener(listener)
            } catch (_: Exception) {}
        }
        authStateListener = null
        attachedAuth = null
        networkCallback?.let { cb ->
            try {
                val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                cm?.unregisterNetworkCallback(cb)
            } catch (_: Exception) {}
        }
        networkCallback = null
    }

    private fun registerConnectivityObserver() {
        if (networkCallback != null) return
        try {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch {
                        onConnectivityChanged(isOnline = true)
                    }
                }

                override fun onLost(network: Network) {
                    scope.launch {
                        onConnectivityChanged(isOnline = isCurrentlyOnline())
                    }
                }
            }
            cm.registerDefaultNetworkCallback(cb)
            networkCallback = cb
        } catch (_: Exception) {
            // Safe fallback on test runners or restricted environments
        }
    }

    suspend fun onConnectivityChanged(isOnline: Boolean): SduiApplyOutcome? {
        _syncState.value = _syncState.value.copy(isOfflineFallback = !isOnline)
        return if (isOnline) {
            syncLatestFromRemote()
        } else {
            null
        }
    }

    suspend fun syncLatestFromRemote(): SduiApplyOutcome {
        if (!isCurrentlyOnline()) {
            _syncState.value = _syncState.value.copy(isOfflineFallback = true, isSyncing = false)
            return SduiApplyOutcome(
                status = SduiApplyStatus.UP_TO_DATE,
                activeVersion = _syncState.value.activeVersion,
                message = "Offline mode: using cached UI v${_syncState.value.activeVersion}"
            )
        }

        _syncState.value = _syncState.value.copy(isSyncing = true, isOfflineFallback = false)

        // 1. Try Firestore document first only when FirebaseAuth session is active (rules require isSignedIn())
        val db = attachedFirestore
        if (db != null && isFirebaseAuthenticated(db)) {
            try {
                val snap = db.collection("sdui_configs")
                    .document(SduiAllowlists.ACTIVE_CONFIG_KEY)
                    .get()
                    .await()
                if (snap.exists()) {
                    val outcome = processFirestoreSnapshot(snap)
                    _syncState.value = _syncState.value.copy(isSyncing = false)
                    return outcome
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.GET, "sdui_configs/${SduiAllowlists.ACTIVE_CONFIG_KEY}")
            }
        }

        // 2. Fallback to Python backend HTTP endpoint if configured
        val fetcher = backendFetcher
        if (fetcher != null) {
            val res = fetcher()
            if (res.isSuccess) {
                val outcome = applyIncomingConfigJson(
                    rawJson = res.getOrThrow(),
                    source = "BACKEND",
                    forceApplySameOrLowerVersion = false,
                    requireAssetDownloadOnNewImage = true
                )
                _syncState.value = _syncState.value.copy(isSyncing = false)
                return outcome
            }
        }

        _syncState.value = _syncState.value.copy(isSyncing = false)
        return SduiApplyOutcome(
            status = SduiApplyStatus.UP_TO_DATE,
            activeVersion = _syncState.value.activeVersion,
            message = "Retained active UI v${_syncState.value.activeVersion}"
        )
    }

    suspend fun processFirestoreSnapshot(snapshot: DocumentSnapshot): SduiApplyOutcome {
        val rawPayload = snapshot.getString("payloadJson")
        if (!rawPayload.isNullOrBlank()) {
            return applyIncomingConfigJson(
                rawJson = rawPayload,
                source = "FIRESTORE",
                forceApplySameOrLowerVersion = false,
                requireAssetDownloadOnNewImage = true
            )
        }

        // Support field-by-field document updates written from Admin DB Explorer
        val version = snapshot.getLong("configVersion")?.toInt()
            ?: return SduiApplyOutcome(
                status = SduiApplyStatus.REJECTED_INVALID,
                activeVersion = _syncState.value.activeVersion,
                message = "Firestore SDUI document missing payloadJson and configVersion."
            )
        val merged = _syncState.value.config.copy(
            configVersion = version,
            updatedBy = snapshot.getString("updatedBy") ?: "admin",
            updatedAt = System.currentTimeMillis(),
            changeSummary = snapshot.getString("changeSummary") ?: "Updated via Firestore DB",
            theme = _syncState.value.config.theme.copy(
                buttonBlueHex = snapshot.getString("themePrimaryHex") ?: _syncState.value.config.theme.buttonBlueHex,
                lightBgHex = snapshot.getString("themeBackgroundHex") ?: _syncState.value.config.theme.lightBgHex
            ),
            hero = _syncState.value.config.hero.copy(
                title = snapshot.getString("heroTitle") ?: _syncState.value.config.hero.title,
                imageUrl = snapshot.getString("heroImageUrl") ?: _syncState.value.config.hero.imageUrl
            )
        )
        return applyIncomingConfigJson(
            rawJson = SduiJsonCodec.encode(merged),
            source = "FIRESTORE",
            forceApplySameOrLowerVersion = false,
            requireAssetDownloadOnNewImage = true
        )
    }

    suspend fun applyIncomingConfigJson(
        rawJson: String,
        source: String,
        forceApplySameOrLowerVersion: Boolean = false,
        requireAssetDownloadOnNewImage: Boolean = false
    ): SduiApplyOutcome = syncMutex.withLock {
        val validation = SduiSchemaValidator.validateJson(rawJson)
        if (validation is SduiValidationResult.Invalid) {
            val reason = validation.formattedReason()
            Log.w("SduiRepository", "Rejected invalid SDUI config from $source: $reason")
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                lastValidationError = "Rejected invalid UI config ($source): $reason"
            )
            return@withLock SduiApplyOutcome(
                status = SduiApplyStatus.REJECTED_INVALID,
                activeVersion = _syncState.value.activeVersion,
                message = reason
            )
        }

        val valid = validation as SduiValidationResult.Valid
        val current = _syncState.value

        if (!forceApplySameOrLowerVersion &&
            valid.config.configVersion < current.activeVersion
        ) {
            return@withLock SduiApplyOutcome(
                status = SduiApplyStatus.UP_TO_DATE,
                activeVersion = current.activeVersion,
                message = "Ignored stale SDUI v${valid.config.configVersion} (active v${current.activeVersion})"
            )
        }

        if (!forceApplySameOrLowerVersion &&
            valid.config.configVersion == current.activeVersion &&
            valid.sha256Hash == current.contentSha256 &&
            current.lastSyncSource != "BASELINE"
        ) {
            return@withLock SduiApplyOutcome(
                status = SduiApplyStatus.UP_TO_DATE,
                activeVersion = current.activeVersion,
                message = "SDUI v${current.activeVersion} is already active"
            )
        }

        // Prefetch required images before committing to cache; abort cleanly if required remote fetch fails mid-sync
        val updatedImageMap = current.cachedImagePaths.toMutableMap()
        val urls = valid.config.requiredImageUrls()
        for (url in urls) {
            val urlHash = SduiJsonCodec.sha256(url)
            val existingEntity = sduiDao.getImageCacheByHash(urlHash)
            val existingFile = existingEntity?.localFilePath?.let { File(it) }
            if (existingFile != null && existingFile.exists() && existingFile.length() > 0L) {
                updatedImageMap[url] = existingFile.absolutePath
                continue
            }

            val destFile = File(imageDir, "$urlHash.img")
            if (destFile.exists() && destFile.length() > 0L) {
                sduiDao.upsertImageCache(
                    SduiImageCacheEntity(
                        urlSha256 = urlHash,
                        originalUrl = url,
                        localFilePath = destFile.absolutePath,
                        byteSize = destFile.length()
                    )
                )
                updatedImageMap[url] = destFile.absolutePath
                continue
            }

            if (requireAssetDownloadOnNewImage) {
                try {
                    assetFetcher.fetchToFile(url, destFile)
                    sduiDao.upsertImageCache(
                        SduiImageCacheEntity(
                            urlSha256 = urlHash,
                            originalUrl = url,
                            localFilePath = destFile.absolutePath,
                            byteSize = destFile.length()
                        )
                    )
                    updatedImageMap[url] = destFile.absolutePath
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val err = "Asset sync interrupted for '$url': ${e.message ?: "Network error"}"
                    Log.w("SduiRepository", "Aborting atomic SDUI update: $err")
                    _syncState.value = _syncState.value.copy(
                        isSyncing = false,
                        lastValidationError = err
                    )
                    return@withLock SduiApplyOutcome(
                        status = SduiApplyStatus.ABORTED_NETWORK_MID_SYNC,
                        activeVersion = current.activeVersion,
                        message = err
                    )
                }
            }
        }

        // Atomic commit into Room + Mirror SharedPreferences + StateFlow
        val now = System.currentTimeMillis()
        val cacheEntity = SduiConfigCacheEntity(
            configKey = SduiAllowlists.ACTIVE_CONFIG_KEY,
            schemaVersion = valid.config.schemaVersion,
            configVersion = valid.config.configVersion,
            payloadJson = valid.normalizedJson,
            contentSha256 = valid.sha256Hash,
            requiredImageUrlsJson = JSONArray(urls).toString(),
            updatedBy = valid.config.updatedBy,
            updatedAt = valid.config.updatedAt.takeIf { it > 0L } ?: now,
            syncedAt = now,
            changeSummary = valid.config.changeSummary
        )
        val historyEntity = SduiConfigHistoryEntity(
            configVersion = valid.config.configVersion,
            schemaVersion = valid.config.schemaVersion,
            summary = valid.config.changeSummary.ifBlank { "Updated UI v${valid.config.configVersion}" },
            payloadJson = valid.normalizedJson,
            contentSha256 = valid.sha256Hash,
            publishedBy = valid.config.updatedBy,
            timestamp = now
        )

        sduiDao.saveValidatedConfigAtomically(cacheEntity, historyEntity)
        writeMirrorPrefsAtomically(valid.normalizedJson, valid.sha256Hash, now, updatedImageMap)

        _syncState.value = SduiSyncState(
            config = valid.config,
            activeVersion = valid.config.configVersion,
            contentSha256 = valid.sha256Hash,
            isOfflineFallback = !isCurrentlyOnline(),
            isSyncing = false,
            lastSyncedAt = now,
            lastSyncSource = source,
            lastValidationError = null,
            cachedImagePaths = updatedImageMap.toMap()
        )

        SduiApplyOutcome(
            status = SduiApplyStatus.UPDATED,
            activeVersion = valid.config.configVersion,
            message = "Applied SDUI v${valid.config.configVersion} from $source"
        )
    }

    fun resolveImageModel(url: String): Any {
        val clean = url.trim()
        val cachedPath = _syncState.value.cachedImagePaths[clean]
        if (!cachedPath.isNullOrBlank()) {
            val file = File(cachedPath)
            if (file.exists() && file.length() > 0L) {
                return file
            }
        }
        if (clean.isNotBlank()) {
            val fallbackFile = File(imageDir, "${SduiJsonCodec.sha256(clean)}.img")
            if (fallbackFile.exists() && fallbackFile.length() > 0L) {
                return fallbackFile
            }
        }
        return clean
    }

    private suspend fun buildImagePathMapFromDao(): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        sduiDao.getAllImageCacheSnapshots().forEach { item ->
            val f = File(item.localFilePath)
            if (f.exists() && f.length() > 0L) {
                map[item.originalUrl] = item.localFilePath
            }
        }
        return map
    }

    private fun writeMirrorPrefsAtomically(
        normalizedJson: String,
        sha256Hash: String,
        syncedAt: Long,
        imagePaths: Map<String, String>
    ) {
        val imgJson = JSONObject()
        imagePaths.forEach { (url, path) -> imgJson.put(url, path) }
        mirrorPrefs.edit()
            .putString(KEY_PAYLOAD_JSON, normalizedJson)
            .putString(KEY_CONTENT_SHA256, sha256Hash)
            .putLong(KEY_LAST_SYNCED_AT, syncedAt)
            .putString(KEY_IMAGE_PATHS_JSON, imgJson.toString())
            .apply()
    }

    private fun loadMirrorImagePaths(): Map<String, String> {
        val raw = mirrorPrefs.getString(KEY_IMAGE_PATHS_JSON, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val map = LinkedHashMap<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val url = keys.next()
                val path = obj.optString(url, "")
                if (path.isNotBlank() && File(path).exists()) {
                    map[url] = path
                }
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    companion object {
        private const val KEY_PAYLOAD_JSON = "sdui_active_payload_json"
        private const val KEY_CONTENT_SHA256 = "sdui_active_content_sha256"
        private const val KEY_LAST_SYNCED_AT = "sdui_last_synced_at"
        private const val KEY_IMAGE_PATHS_JSON = "sdui_image_paths_json"

        @Volatile
        private var INSTANCE: SduiRepository? = null

        fun getInstance(context: Context): SduiRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SduiRepository(
                    context = context.applicationContext,
                    sduiDao = AppDatabase.getInstance(context.applicationContext).sduiDao()
                ).also { INSTANCE = it }
            }
        }

        internal fun resetSingletonForTest() {
            INSTANCE?.stopRealtimeSync()
            INSTANCE = null
        }

        fun defaultIsNetworkConnected(context: Context): Boolean {
            return try {
                val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as? ConnectivityManager ?: return false
                val network = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(network) ?: return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } catch (_: Exception) {
                false
            }
        }
    }
}
