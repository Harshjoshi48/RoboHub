package com.example

import android.content.Context
import android.content.pm.PackageInfo
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PrecisionManufacturing
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.annotation.ExperimentalCoilApi
import coil.compose.SubcomposeAsyncImage
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.ImageRequest
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

data class AppBrandingConfig(
    val configKey: String = BRANDING_DOC_ID,
    val appName: String = DEFAULT_APP_NAME,
    val logoUrl: String = "",
    val logoVersion: Long = 1L,
    val brandingVersion: Long = 1L,
    val updatedBy: String = "system",
    val updatedAt: Long = 0L,
    val localCachedLogoPath: String = "",
    val lastSyncSource: String = "DEFAULT", // DEFAULT, CACHE, FIRESTORE, ADMIN_PUBLISH, RESTORE_DEFAULT
    val isOfflineFallback: Boolean = false
) {
    val hasCustomLogo: Boolean
        get() = AppBrandingValidator.isUsableLogoUrl(logoUrl)

    val isDefaultBranding: Boolean
        get() = appName == DEFAULT_APP_NAME && !hasCustomLogo

    companion object {
        const val COLLECTION_NAME = "app_settings"
        const val BRANDING_DOC_ID = "branding"
        const val DEFAULT_APP_NAME = "NeuroGrip"

        fun default(): AppBrandingConfig = AppBrandingConfig()
    }
}

data class ValidatedBrandingLogo(
    val bytes: ByteArray,
    val mimeType: String,
    val extension: String,
    val fileName: String,
    val sizeBytes: Long,
    val widthPx: Int,
    val heightPx: Int
) {
    val formattedSize: String
        get() = when {
            sizeBytes < 1024L -> "$sizeBytes B"
            sizeBytes < 1024L * 1024L -> "${(sizeBytes / 1024.0).let { "%.1f".format(Locale.US, it) }} KB"
            else -> "${(sizeBytes / (1024.0 * 1024.0)).let { "%.2f".format(Locale.US, it) }} MB"
        }

    val dimensionsLabel: String
        get() = if (widthPx > 0 && heightPx > 0) "${widthPx}×${heightPx} px" else "Verified image"
}

data class LauncherBrandingInfo(
    val packageName: String,
    val installedAppLabel: String,
    val versionName: String,
    val versionCode: Long,
    val dynamicRuntimeSupported: Boolean = true,
    val osLauncherMutationSupported: Boolean = false,
    val platformExplanation: String =
        "Android OS compiles launcher icons (@mipmap/ic_launcher) and home-screen names (@string/app_name) into the signed APK/AAB at build time. Remote branding updates apply live across every in-app screen; updating the home-screen launcher icon or label requires shipping a new APK/AAB build (prompted via Remote Config)."
)

object ApkLauncherBrandingInspector {
    fun inspect(context: Context): LauncherBrandingInfo {
        val pm = context.packageManager
        val appInfo = runCatching { context.applicationInfo }.getOrNull()
        val label = runCatching {
            if (appInfo != null) pm.getApplicationLabel(appInfo).toString()
            else context.getString(R.string.app_name)
        }.getOrElse { "NeuroPulse" }

        val pkgInfo: PackageInfo? = runCatching {
            pm.getPackageInfo(context.packageName, 0)
        }.getOrNull()

        val versionCode = if (pkgInfo != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkgInfo.longVersionCode else @Suppress("DEPRECATION") pkgInfo.versionCode.toLong()
        } else {
            1L
        }

        return LauncherBrandingInfo(
            packageName = context.packageName,
            installedAppLabel = label,
            versionName = pkgInfo?.versionName ?: "1.0",
            versionCode = versionCode
        )
    }
}

object AppBrandingValidator {
    const val MAX_LOGO_BYTES: Long = 5L * 1024L * 1024L // 5 MB
    const val MIN_DIMENSION_PX: Int = 64
    const val MAX_DIMENSION_PX: Int = 2048
    const val MIN_NAME_LENGTH: Int = 1
    const val MAX_NAME_LENGTH: Int = 30

    val ALLOWED_MIME_TYPES = setOf("image/png", "image/jpeg", "image/jpg", "image/webp")
    val ALLOWED_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")

    fun isUsableLogoUrl(url: String?): Boolean {
        val clean = url?.trim().orEmpty()
        if (clean.isEmpty()) return false
        return clean.startsWith("https://", ignoreCase = true) ||
            clean.startsWith("file://", ignoreCase = true)
    }

    fun validateAppName(rawName: String): String? {
        val trimmed = rawName.trim()
        if (trimmed.isEmpty()) {
            return "Application name cannot be empty."
        }
        if (trimmed.length > MAX_NAME_LENGTH) {
            return "Application name must be at most $MAX_NAME_LENGTH characters (currently ${trimmed.length})."
        }
        if (trimmed.any { it.isISOControl() }) {
            return "Application name contains invalid control characters or line breaks."
        }
        return null
    }

    fun validateLogoUrlForCloud(url: String): String? {
        val clean = url.trim()
        if (clean.isEmpty()) return null
        if (clean.length > 2048) return "Logo URL exceeds maximum length (2048 chars)."
        if (!clean.startsWith("https://", ignoreCase = true)) {
            return "Cloud branding logoUrl must use HTTPS."
        }
        return null
    }

    private fun parseImageDimensionsFallback(bytes: ByteArray, ext: String): Pair<Int, Int>? {
        return when (ext) {
            "png" -> {
                if (bytes.size >= 24 &&
                    bytes[12] == 'I'.code.toByte() &&
                    bytes[13] == 'H'.code.toByte() &&
                    bytes[14] == 'D'.code.toByte() &&
                    bytes[15] == 'R'.code.toByte()
                ) {
                    val w = ((bytes[16].toInt() and 0xFF) shl 24) or
                        ((bytes[17].toInt() and 0xFF) shl 16) or
                        ((bytes[18].toInt() and 0xFF) shl 8) or
                        (bytes[19].toInt() and 0xFF)
                    val h = ((bytes[20].toInt() and 0xFF) shl 24) or
                        ((bytes[21].toInt() and 0xFF) shl 16) or
                        ((bytes[22].toInt() and 0xFF) shl 8) or
                        (bytes[23].toInt() and 0xFF)
                    if (w > 0 && h > 0) w to h else null
                } else null
            }
            "jpg", "jpeg" -> {
                var idx = 2
                while (idx + 9 < bytes.size) {
                    if (bytes[idx] != 0xFF.toByte()) {
                        idx++
                        continue
                    }
                    val marker = bytes[idx + 1].toInt() and 0xFF
                    if (marker == 0xD8 || marker == 0xD9 || marker == 0x00 || marker == 0x01 || (marker in 0xD0..0xD7)) {
                        idx += 2
                        continue
                    }
                    val segLen = ((bytes[idx + 2].toInt() and 0xFF) shl 8) or (bytes[idx + 3].toInt() and 0xFF)
                    if (segLen < 2 || idx + 2 + segLen > bytes.size) break
                    if (marker == 0xC0 || marker == 0xC1 || marker == 0xC2 || marker == 0xC3) {
                        val h = ((bytes[idx + 5].toInt() and 0xFF) shl 8) or (bytes[idx + 6].toInt() and 0xFF)
                        val w = ((bytes[idx + 7].toInt() and 0xFF) shl 8) or (bytes[idx + 8].toInt() and 0xFF)
                        return if (w > 0 && h > 0) w to h else null
                    }
                    idx += 2 + segLen
                }
                null
            }
            "webp" -> {
                if (bytes.size >= 30 &&
                    bytes[12] == 'V'.code.toByte() &&
                    bytes[13] == 'P'.code.toByte() &&
                    bytes[14] == '8'.code.toByte()
                ) {
                    when (bytes[15].toInt().toChar()) {
                        ' ' -> {
                            val w = (((bytes[27].toInt() and 0xFF) shl 8) or (bytes[26].toInt() and 0xFF)) and 0x3FFF
                            val h = (((bytes[29].toInt() and 0xFF) shl 8) or (bytes[28].toInt() and 0xFF)) and 0x3FFF
                            if (w > 0 && h > 0) w to h else null
                        }
                        'L' -> {
                            val b0 = bytes[21].toInt() and 0xFF
                            val b1 = bytes[22].toInt() and 0xFF
                            val b2 = bytes[23].toInt() and 0xFF
                            val b3 = bytes[24].toInt() and 0xFF
                            val bits = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
                            val w = (bits and 0x3FFF) + 1
                            val h = ((bits shr 14) and 0x3FFF) + 1
                            if (w > 0 && h > 0) w to h else null
                        }
                        'X' -> {
                            val w = 1 + ((bytes[24].toInt() and 0xFF) or ((bytes[25].toInt() and 0xFF) shl 8) or ((bytes[26].toInt() and 0xFF) shl 16))
                            val h = 1 + ((bytes[27].toInt() and 0xFF) or ((bytes[28].toInt() and 0xFF) shl 8) or ((bytes[29].toInt() and 0xFF) shl 16))
                            if (w > 0 && h > 0) w to h else null
                        }
                        else -> null
                    }
                } else null
            }
            else -> null
        }
    }

    fun validateImageBytesAndMetadata(
        bytes: ByteArray,
        mimeType: String?,
        fileName: String?,
        maxBytes: Long = MAX_LOGO_BYTES
    ): Result<ValidatedBrandingLogo> {
        if (bytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("Selected logo file is empty. Please choose another image."))
        }
        if (bytes.size.toLong() > maxBytes) {
            val maxMb = maxBytes / (1024L * 1024L)
            return Result.failure(IllegalArgumentException("Logo file exceeds the ${maxMb} MB limit. Please select a smaller image."))
        }

        val normalizedMime = mimeType?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotEmpty() }
        if (normalizedMime != null && normalizedMime !in ALLOWED_MIME_TYPES) {
            return Result.failure(IllegalArgumentException("Unsupported format ($normalizedMime). Allowed formats: PNG, JPG, JPEG, WebP."))
        }

        val cleanName = fileName?.trim().orEmpty().ifBlank { "app_logo" }
        val ext = cleanName.substringAfterLast('.', "").lowercase(Locale.US)
        if (cleanName.contains('.') && ext.isNotEmpty() && ext !in ALLOWED_EXTENSIONS) {
            return Result.failure(IllegalArgumentException("Unsupported file extension '.$ext'. Only PNG, JPG, and WebP are allowed."))
        }

        val detected = ProfilePhotoValidator.detectFormatFromBytes(bytes)
            ?: return Result.failure(IllegalArgumentException("Invalid or corrupted image file. Only PNG, JPG, and WebP logos are supported."))

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        }
        var widthPx = bounds.outWidth
        var heightPx = bounds.outHeight
        if (widthPx <= 0 || heightPx <= 0) {
            val parsed = parseImageDimensionsFallback(bytes, detected.second)
            if (parsed != null) {
                widthPx = parsed.first
                heightPx = parsed.second
            }
        }
        if (widthPx <= 0 || heightPx <= 0) {
            return Result.failure(IllegalArgumentException("Unable to read image dimensions. Please choose a valid PNG, JPG, or WebP logo."))
        }
        if (widthPx < MIN_DIMENSION_PX || heightPx < MIN_DIMENSION_PX) {
            return Result.failure(
                IllegalArgumentException(
                    "Logo resolution (${widthPx}×${heightPx} px) is too small. Minimum required is ${MIN_DIMENSION_PX}×${MIN_DIMENSION_PX} px."
                )
            )
        }
        if (widthPx > MAX_DIMENSION_PX || heightPx > MAX_DIMENSION_PX) {
            return Result.failure(
                IllegalArgumentException(
                    "Logo resolution (${widthPx}×${heightPx} px) is too large. Maximum allowed is ${MAX_DIMENSION_PX}×${MAX_DIMENSION_PX} px."
                )
            )
        }

        return Result.success(
            ValidatedBrandingLogo(
                bytes = bytes,
                mimeType = detected.first,
                extension = detected.second,
                fileName = cleanName,
                sizeBytes = bytes.size.toLong(),
                widthPx = widthPx,
                heightPx = heightPx
            )
        )
    }

    fun readAndValidateUri(
        context: Context,
        uri: Uri,
        maxBytes: Long = MAX_LOGO_BYTES
    ): Result<ValidatedBrandingLogo> {
        return runCatching {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri)
            var displayName: String? = null
            var reportedSize: Long = -1L

            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) displayName = cursor.getString(nameIdx)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) reportedSize = cursor.getLong(sizeIdx)
                }
            }

            if (reportedSize > maxBytes) {
                val maxMb = maxBytes / (1024L * 1024L)
                return Result.failure(IllegalArgumentException("Logo file exceeds the ${maxMb} MB limit. Please select a smaller image."))
            }

            val bytes = resolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) {
                        val maxMb = maxBytes / (1024L * 1024L)
                        return Result.failure(IllegalArgumentException("Logo file exceeds the ${maxMb} MB limit. Please select a smaller image."))
                    }
                    out.write(buf, 0, read)
                }
                out.toByteArray()
            } ?: return Result.failure(IllegalArgumentException("Unable to read the selected logo from device storage."))

            validateImageBytesAndMetadata(
                bytes = bytes,
                mimeType = mimeType,
                fileName = displayName ?: uri.lastPathSegment,
                maxBytes = maxBytes
            ).getOrThrow()
        }
    }
}

object AppBrandingCacheStore {
    private const val PREFS_NAME = "app_branding_cache_prefs"
    private const val KEY_APP_NAME = "app_name"
    private const val KEY_LOGO_URL = "logo_url"
    private const val KEY_LOGO_VERSION = "logo_version"
    private const val KEY_BRANDING_VERSION = "branding_version"
    private const val KEY_UPDATED_BY = "updated_by"
    private const val KEY_UPDATED_AT = "updated_at"
    private const val KEY_LOCAL_LOGO_PATH = "local_logo_path"
    private const val KEY_LAST_SOURCE = "last_sync_source"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var sharedBrandingFlow: kotlinx.coroutines.flow.MutableStateFlow<AppBrandingConfig>? = null

    fun stateFlow(context: Context): kotlinx.coroutines.flow.StateFlow<AppBrandingConfig> {
        val existing = sharedBrandingFlow
        if (existing != null) return existing
        return synchronized(this) {
            sharedBrandingFlow ?: kotlinx.coroutines.flow.MutableStateFlow(load(context)).also {
                sharedBrandingFlow = it
            }
        }
    }

    fun reloadFromPrefs(context: Context): AppBrandingConfig {
        val loaded = load(context)
        sharedBrandingFlow?.value = loaded
        return loaded
    }

    fun load(context: Context): AppBrandingConfig {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedName = prefs.getString(KEY_APP_NAME, null)?.trim()?.takeIf { it.isNotEmpty() }
            ?: AppBrandingConfig.DEFAULT_APP_NAME
        val storedUrl = prefs.getString(KEY_LOGO_URL, "")?.trim().orEmpty()
        val storedLocalPath = prefs.getString(KEY_LOCAL_LOGO_PATH, "")?.trim().orEmpty()
        val verifiedLocal = if (storedLocalPath.isNotEmpty() && File(storedLocalPath).exists()) storedLocalPath else ""
        val logoVer = prefs.getLong(KEY_LOGO_VERSION, 1L).coerceAtLeast(1L)
        val brandingVer = prefs.getLong(KEY_BRANDING_VERSION, 1L).coerceAtLeast(1L)
        val updatedBy = prefs.getString(KEY_UPDATED_BY, "system") ?: "system"
        val updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L)
        val source = prefs.getString(KEY_LAST_SOURCE, if (prefs.contains(KEY_APP_NAME)) "CACHE" else "DEFAULT")
            ?: "DEFAULT"

        return AppBrandingConfig(
            configKey = AppBrandingConfig.BRANDING_DOC_ID,
            appName = storedName,
            logoUrl = storedUrl,
            logoVersion = logoVer,
            brandingVersion = brandingVer,
            updatedBy = updatedBy,
            updatedAt = updatedAt,
            localCachedLogoPath = verifiedLocal,
            lastSyncSource = source,
            isOfflineFallback = false
        )
    }

    fun save(context: Context, config: AppBrandingConfig) {
        val normalized = config.copy(
            appName = config.appName.trim().ifBlank { AppBrandingConfig.DEFAULT_APP_NAME },
            logoUrl = config.logoUrl.trim(),
            logoVersion = config.logoVersion.coerceAtLeast(1L),
            brandingVersion = config.brandingVersion.coerceAtLeast(1L)
        )
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_APP_NAME, normalized.appName)
            .putString(KEY_LOGO_URL, normalized.logoUrl)
            .putLong(KEY_LOGO_VERSION, normalized.logoVersion)
            .putLong(KEY_BRANDING_VERSION, normalized.brandingVersion)
            .putString(KEY_UPDATED_BY, normalized.updatedBy)
            .putLong(KEY_UPDATED_AT, normalized.updatedAt)
            .putString(KEY_LOCAL_LOGO_PATH, normalized.localCachedLogoPath)
            .putString(KEY_LAST_SOURCE, normalized.lastSyncSource)
            .apply()
        sharedBrandingFlow?.value = normalized
    }

    fun saveLocalLogoCopy(
        context: Context,
        bytes: ByteArray,
        extension: String,
        logoVersion: Long
    ): String {
        val dir = File(context.applicationContext.filesDir, "app_branding")
        if (!dir.exists()) {
            dir.mkdirs()
        } else {
            dir.listFiles()?.forEach { runCatching { it.delete() } }
        }
        val cleanExt = extension.lowercase(Locale.US).ifBlank { "png" }
        val file = File(dir, "logo_v${logoVersion}_${System.currentTimeMillis()}.$cleanExt")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    fun clearLocalLogoCopies(context: Context) {
        val dir = File(context.applicationContext.filesDir, "app_branding")
        if (dir.exists()) {
            dir.listFiles()?.forEach { runCatching { it.delete() } }
        }
    }

    fun downloadRemoteLogoToCache(
        context: Context,
        url: String,
        logoVersion: Long
    ): String? {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("https://", ignoreCase = true)) return null
        return runCatching {
            val req = Request.Builder().url(cleanUrl).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val bytes = resp.body?.bytes() ?: return@use null
                if (bytes.isEmpty() || bytes.size.toLong() > AppBrandingValidator.MAX_LOGO_BYTES) return@use null
                val detectedExt = ProfilePhotoValidator.detectFormatFromBytes(bytes)?.second ?: "png"
                saveLocalLogoCopy(context, bytes, detectedExt, logoVersion)
            }
        }.getOrNull()
    }

    @OptIn(ExperimentalCoilApi::class)
    fun evictLogoFromCoil(
        context: Context,
        url: String?,
        brandingVersion: Long = 1L,
        logoVersion: Long = 1L
    ) {
        val clean = url?.trim().orEmpty()
        if (clean.isEmpty()) return
        runCatching {
            val loader = context.applicationContext.imageLoader
            val versionedKey = "$clean#bv=$brandingVersion&lv=$logoVersion"
            loader.memoryCache?.remove(MemoryCache.Key(clean))
            loader.memoryCache?.remove(MemoryCache.Key(versionedKey))
            loader.diskCache?.remove(clean)
            loader.diskCache?.remove(versionedKey)
        }
    }

    fun resolveDisplayLogoModel(context: Context, config: AppBrandingConfig): Any? {
        val localFile = config.localCachedLogoPath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() }
        val cleanUrl = config.logoUrl.trim()
        if (cleanUrl.isEmpty()) return null
        if (cleanUrl.startsWith("file://", ignoreCase = true)) {
            return localFile ?: Uri.parse(cleanUrl)
        }
        return if (config.isOfflineFallback && localFile != null) localFile else cleanUrl
    }
}

@Composable
fun AppBrandingLogo(
    branding: AppBrandingConfig,
    size: Dp = 32.dp,
    cornerRadius: Dp = 10.dp,
    showBorder: Boolean = false,
    borderColor: Color = Color(0xFFDBEAFE),
    backgroundColor: Color = Color(0xFF0055AA),
    accentEndColor: Color = Color(0xFF2563EB),
    overrideLogoModel: Any? = null,
    contentDescription: String = "${branding.appName} Logo",
    modifier: Modifier = Modifier,
    testTag: String = "app_branding_logo"
) {
    val context = LocalContext.current
    val shape: Shape = RoundedCornerShape(cornerRadius)
    val resolvedModel = overrideLogoModel ?: AppBrandingCacheStore.resolveDisplayLogoModel(context, branding)
    val cacheKey = "${branding.logoUrl}#bv=${branding.brandingVersion}&lv=${branding.logoVersion}"

    val boxModifier = modifier
        .size(size)
        .clip(shape)
        .then(
            if (showBorder) Modifier.border(BorderStroke(1.5.dp, borderColor), shape) else Modifier
        )
        .semantics { this.contentDescription = contentDescription }
        .testTag(testTag)

    Box(
        modifier = boxModifier,
        contentAlignment = Alignment.Center
    ) {
        if (resolvedModel != null) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(resolvedModel)
                    .memoryCacheKey(cacheKey)
                    .diskCacheKey(cacheKey)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    DefaultBrandingEmblem(size = size, bgStart = backgroundColor, bgEnd = accentEndColor)
                },
                error = {
                    val localFallback = branding.localCachedLogoPath.takeIf { it.isNotBlank() }
                        ?.let(::File)?.takeIf { it.exists() }
                    if (localFallback != null && resolvedModel != localFallback) {
                        SubcomposeAsyncImage(
                            model = localFallback,
                            contentDescription = contentDescription,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            error = {
                                DefaultBrandingEmblem(size = size, bgStart = backgroundColor, bgEnd = accentEndColor)
                            }
                        )
                    } else {
                        DefaultBrandingEmblem(size = size, bgStart = backgroundColor, bgEnd = accentEndColor)
                    }
                }
            )
        } else {
            DefaultBrandingEmblem(size = size, bgStart = backgroundColor, bgEnd = accentEndColor)
        }
    }
}

@Composable
private fun DefaultBrandingEmblem(
    size: Dp,
    bgStart: Color,
    bgEnd: Color
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(bgStart, bgEnd)))
            .testTag("app_branding_default_emblem"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.PrecisionManufacturing,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.58f)
        )
    }
}
